package com.investops.reconciliation.service;

import com.investops.common.dto.PagedResponse;
import com.investops.common.exception.BusinessValidationException;
import com.investops.common.exception.ResourceNotFoundException;
import com.investops.exceptionmgmt.service.ExceptionService;
import com.investops.reconciliation.dto.ReconciliationRequest;
import com.investops.reconciliation.dto.ReconciliationResultDto;
import com.investops.reconciliation.dto.ReconciliationRunDto;
import com.investops.reconciliation.dto.ReconciliationRunResponse;
import com.investops.reconciliation.entity.MatchStatus;
import com.investops.reconciliation.entity.ReconciliationResult;
import com.investops.reconciliation.entity.ReconciliationRun;
import com.investops.reconciliation.entity.RunStatus;
import com.investops.reconciliation.repository.ReconciliationResultRepository;
import com.investops.reconciliation.repository.ReconciliationRunRepository;
import com.investops.trade.entity.TradeRecord;
import com.investops.trade.repository.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationResultRepository resultRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final MatchingEngine matchingEngine;
    private final ExceptionService exceptionService;
    private final PlatformTransactionManager transactionManager;

    /**
     * Initiates an asynchronous reconciliation run and returns HTTP 202 response immediately.
     */
    public ReconciliationRunResponse initiateRun(ReconciliationRequest request, UUID initiatedBy) {
        validateRequest(request);

        BigDecimal priceTolerance = request.getPriceTolerance() != null
                ? request.getPriceTolerance()
                : new BigDecimal("0.01000000");

        ReconciliationRun run = ReconciliationRun.builder()
                .tradeDateFrom(request.getTradeDateFrom())
                .tradeDateTo(request.getTradeDateTo())
                .sourceA(request.getSourceA())
                .sourceB(request.getSourceB())
                .priceTolerance(priceTolerance)
                .status(RunStatus.RUNNING)
                .initiatedBy(initiatedBy)
                .startedAt(Instant.now())
                .build();

        ReconciliationRun savedRun = runRepository.save(run);
        UUID runId = savedRun.getId();

        // Launch asynchronous processing
        CompletableFuture.runAsync(() -> executeRun(runId));

        return ReconciliationRunResponse.builder()
                .runId(runId)
                .status(RunStatus.RUNNING)
                .message(String.format("Reconciliation initiated for %s to %s between %s and %s",
                        request.getTradeDateFrom(), request.getTradeDateTo(), request.getSourceA(), request.getSourceB()))
                .build();
    }

    /**
     * Executes reconciliation synchronously. Useful for testing and immediate validation.
     */
    public ReconciliationRunDto executeRunSync(ReconciliationRequest request, UUID initiatedBy) {
        validateRequest(request);

        BigDecimal priceTolerance = request.getPriceTolerance() != null
                ? request.getPriceTolerance()
                : new BigDecimal("0.01000000");

        ReconciliationRun run = ReconciliationRun.builder()
                .tradeDateFrom(request.getTradeDateFrom())
                .tradeDateTo(request.getTradeDateTo())
                .sourceA(request.getSourceA())
                .sourceB(request.getSourceB())
                .priceTolerance(priceTolerance)
                .status(RunStatus.RUNNING)
                .initiatedBy(initiatedBy)
                .startedAt(Instant.now())
                .build();

        ReconciliationRun savedRun = runRepository.save(run);
        executeRun(savedRun.getId());

        ReconciliationRun completedRun = runRepository.findById(savedRun.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Run not found: " + savedRun.getId()));
        return ReconciliationRunDto.from(completedRun);
    }

    /**
     * Internal execution unit wrapped in a database transaction.
     */
    public void executeRun(UUID runId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            ReconciliationRun run = runRepository.findById(runId).orElse(null);
            if (run == null) {
                log.error("Reconciliation run {} not found during execution", runId);
                return null;
            }

            try {
                // Fetch candidate trades for both systems
                List<TradeRecord> tradesA = tradeRecordRepository.findBySourceSystemAndTradeDateBetween(
                        run.getSourceA(), run.getTradeDateFrom(), run.getTradeDateTo());
                List<TradeRecord> tradesB = tradeRecordRepository.findBySourceSystemAndTradeDateBetween(
                        run.getSourceB(), run.getTradeDateFrom(), run.getTradeDateTo());

                // Execute deterministic two-pass matching engine
                List<ReconciliationResult> results = matchingEngine.reconcile(
                        run, tradesA, tradesB, run.getPriceTolerance());

                // Batch persist results
                List<ReconciliationResult> savedResults = resultRepository.saveAll(results);

                // Convert eligible reconciliation results (breaks, missing, duplicate trades) into exceptions
                exceptionService.createExceptionsForResults(savedResults);

                // Compute counts
                int totalCompared = results.size();
                int matchedCount = (int) results.stream()
                        .filter(r -> r.getMatchStatus() == MatchStatus.MATCHED)
                        .count();
                int breakCount = totalCompared - matchedCount;

                run.setTotalCompared(totalCompared);
                run.setMatchedCount(matchedCount);
                run.setBreakCount(breakCount);
                run.setStatus(RunStatus.COMPLETED);
                run.setCompletedAt(Instant.now());
                runRepository.save(run);

                log.info("Reconciliation run {} completed successfully: {} total compared, {} matched, {} breaks",
                        runId, totalCompared, matchedCount, breakCount);
            } catch (Exception e) {
                log.error("Reconciliation run {} failed: {}", runId, e.getMessage(), e);
                run.setStatus(RunStatus.FAILED);
                run.setCompletedAt(Instant.now());
                runRepository.save(run);
            }
            return null;
        });
    }

    /**
     * Retrieves run details by ID.
     */
    public ReconciliationRunDto getRunById(UUID id) {
        ReconciliationRun run = runRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found with id: " + id));
        return ReconciliationRunDto.from(run);
    }

    /**
     * Lists all past reconciliation runs with pagination.
     */
    public PagedResponse<ReconciliationRunDto> listRuns(Pageable pageable) {
        Page<ReconciliationRunDto> page = runRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(ReconciliationRunDto::from);
        return PagedResponse.from(page);
    }

    /**
     * Retrieves paginated results for a specific reconciliation run.
     */
    public PagedResponse<ReconciliationResultDto> getRunResults(UUID runId, MatchStatus matchStatus, Pageable pageable) {
        if (!runRepository.existsById(runId)) {
            throw new ResourceNotFoundException("Reconciliation run not found with id: " + runId);
        }

        Page<ReconciliationResult> page;
        if (matchStatus != null) {
            page = resultRepository.findByRunIdAndMatchStatus(runId, matchStatus, pageable);
        } else {
            page = resultRepository.findByRunId(runId, pageable);
        }

        Page<ReconciliationResultDto> dtoPage = page.map(ReconciliationResultDto::from);
        return PagedResponse.from(dtoPage);
    }

    private void validateRequest(ReconciliationRequest request) {
        if (request.getTradeDateFrom() == null || request.getTradeDateTo() == null) {
            throw new BusinessValidationException("Trade date range must not be null");
        }
        if (request.getTradeDateTo().isBefore(request.getTradeDateFrom())) {
            throw new BusinessValidationException("tradeDateTo cannot precede tradeDateFrom");
        }
        if (request.getSourceA() == null || request.getSourceB() == null) {
            throw new BusinessValidationException("Both source systems must be specified");
        }
        if (request.getSourceA() == request.getSourceB()) {
            throw new BusinessValidationException("Source systems must be different");
        }
        if (request.getPriceTolerance() != null && request.getPriceTolerance().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessValidationException("priceTolerance must be non-negative");
        }
    }
}
