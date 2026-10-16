package com.investops.exceptionmgmt.service;

import com.investops.auth.entity.AppUser;
import com.investops.auth.repository.UserRepository;
import com.investops.common.dto.PagedResponse;
import com.investops.common.exception.BusinessValidationException;
import com.investops.common.exception.ConflictException;
import com.investops.common.exception.ResourceNotFoundException;
import com.investops.exceptionmgmt.dto.*;
import com.investops.exceptionmgmt.entity.ExceptionComment;
import com.investops.exceptionmgmt.entity.ExceptionStatus;
import com.investops.exceptionmgmt.entity.TradeException;
import com.investops.exceptionmgmt.repository.ExceptionCommentRepository;
import com.investops.exceptionmgmt.repository.ExceptionRepository;
import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import com.investops.reconciliation.entity.MatchStatus;
import com.investops.reconciliation.entity.ReconciliationResult;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExceptionService {

    private final ExceptionRepository exceptionRepository;
    private final ExceptionCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Converts eligible reconciliation results (MISMATCHED, MISSING, duplicate trades)
     * into trackable exceptions, idempotently preventing duplicate creation.
     */
    @Transactional
    public List<TradeException> createExceptionsForResults(List<ReconciliationResult> results) {
        List<TradeException> createdExceptions = new ArrayList<>();

        for (ReconciliationResult result : results) {
            if (result.getMatchStatus() == MatchStatus.MATCHED) {
                continue;
            }

            if (exceptionRepository.existsByResultId(result.getId())) {
                log.debug("Exception already exists for resultId={}, skipping", result.getId());
                continue;
            }

            String fieldName = deriveFieldName(result);
            String expectedValue = truncate(deriveExpectedValue(result), 255);
            String actualValue = truncate(deriveActualValue(result), 255);

            TradeException exception = TradeException.builder()
                    .result(result)
                    .run(result.getRun())
                    .exceptionType(result.getDiscrepancyType())
                    .severity(result.getSeverity() != null ? result.getSeverity() : DiscrepancySeverity.MEDIUM)
                    .status(ExceptionStatus.OPEN)
                    .fieldName(fieldName)
                    .expectedValue(expectedValue)
                    .actualValue(actualValue)
                    .version(0)
                    .build();

            TradeException saved = exceptionRepository.save(exception);
            auditService.logEvent(saved, null, "CREATED", "status", null, ExceptionStatus.OPEN.name());
            createdExceptions.add(saved);
        }

        log.info("Created {} trade exceptions for reconciliation run", createdExceptions.size());
        return createdExceptions;
    }

    /**
     * Retrieves an exception with its full detail, comments, and audit trail.
     */
    @Transactional(readOnly = true)
    public ExceptionDetailDto getExceptionById(UUID id) {
        TradeException exception = exceptionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exception not found with id: " + id));

        List<CommentDto> comments = commentRepository.findByExceptionIdOrderByCreatedAtAsc(id).stream()
                .map(CommentDto::from)
                .collect(Collectors.toList());

        List<AuditLogDto> auditLogs = auditService.getAuditLogs(id);

        return ExceptionDetailDto.of(exception, comments, auditLogs);
    }

    /**
     * Lists exceptions with dynamic filtering and pagination.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ExceptionDto> listExceptions(
            List<ExceptionStatus> statuses,
            List<DiscrepancySeverity> severities,
            DiscrepancyType type,
            UUID assignedTo,
            UUID runId,
            Pageable pageable) {

        Specification<TradeException> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (severities != null && !severities.isEmpty()) {
                predicates.add(root.get("severity").in(severities));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("exceptionType"), type));
            }
            if (assignedTo != null) {
                predicates.add(cb.equal(root.get("assignedTo").get("id"), assignedTo));
            }
            if (runId != null) {
                predicates.add(cb.equal(root.get("run").get("id"), runId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ExceptionDto> page = exceptionRepository.findAll(spec, pageable).map(ExceptionDto::from);
        return PagedResponse.from(page);
    }

    /**
     * Updates an exception's status, assignee, severity, and resolution notes.
     * Enforces valid state machine transitions, optimistic locking, and audit logging.
     */
    @Transactional
    public ExceptionDto updateException(UUID id, ExceptionUpdateRequest request, AppUser actor) {
        TradeException exception = exceptionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exception not found with id: " + id));

        // 1. Optimistic Locking Check
        if (request.getVersion() != null && request.getVersion() != exception.getVersion()) {
            throw new ConflictException(String.format(
                    "Conflict: Exception has been modified by another transaction. Expected version %d but found %d",
                    request.getVersion(), exception.getVersion()));
        }

        // 2. Immutability check on terminal states
        if (exception.getStatus().isTerminal()) {
            boolean allowedToClose = (exception.getStatus() == ExceptionStatus.RESOLVED && request.getStatus() == ExceptionStatus.CLOSED);
            if (!allowedToClose) {
                throw new BusinessValidationException("Cannot modify an already resolved exception");
            }
        }

        // 3. State Transition Validation
        if (request.getStatus() != null && request.getStatus() != exception.getStatus()) {
            ExceptionStatus currentStatus = exception.getStatus();
            ExceptionStatus targetStatus = request.getStatus();

            if (!currentStatus.canTransitionTo(targetStatus)) {
                throw new BusinessValidationException(String.format(
                        "Invalid status transition from %s to %s", currentStatus, targetStatus));
            }

            exception.setStatus(targetStatus);
            auditService.logEvent(exception, actor, "STATUS_CHANGED", "status", currentStatus.name(), targetStatus.name());
        }

        // 4. Assignee Update
        if (request.getAssignedTo() != null) {
            UUID currentAssigneeId = exception.getAssignedTo() != null ? exception.getAssignedTo().getId() : null;
            if (!Objects.equals(currentAssigneeId, request.getAssignedTo())) {
                AppUser newAssignee = userRepository.findById(request.getAssignedTo())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getAssignedTo()));

                String oldVal = exception.getAssignedTo() != null ? exception.getAssignedTo().getUsername() : "UNASSIGNED";
                String newVal = newAssignee.getUsername();

                exception.setAssignedTo(newAssignee);
                auditService.logEvent(exception, actor, "ASSIGNMENT_CHANGED", "assignedTo", oldVal, newVal);
            }
        }

        // 5. Severity Update
        if (request.getSeverity() != null && request.getSeverity() != exception.getSeverity()) {
            DiscrepancySeverity oldSeverity = exception.getSeverity();
            exception.setSeverity(request.getSeverity());
            auditService.logEvent(exception, actor, "SEVERITY_CHANGED", "severity", oldSeverity.name(), request.getSeverity().name());
        }

        // 6. Resolution Notes
        if (request.getResolutionNotes() != null && !request.getResolutionNotes().isBlank()) {
            String oldNotes = exception.getResolutionNotes();
            exception.setResolutionNotes(request.getResolutionNotes());
            auditService.logEvent(exception, actor, "RESOLUTION_NOTES_UPDATED", "resolutionNotes", oldNotes, request.getResolutionNotes());
        }

        TradeException saved = exceptionRepository.save(exception);
        return ExceptionDto.from(saved);
    }

    /**
     * Adds an investigation comment to the exception and records an audit log.
     */
    @Transactional
    public CommentDto addComment(UUID exceptionId, CommentRequest request, AppUser author) {
        TradeException exception = exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Exception not found with id: " + exceptionId));

        if (exception.getStatus() == ExceptionStatus.CLOSED) {
            throw new BusinessValidationException("Cannot add comments to a closed exception");
        }

        ExceptionComment comment = ExceptionComment.builder()
                .exception(exception)
                .author(author)
                .content(request.getContent())
                .build();

        ExceptionComment saved = commentRepository.save(comment);

        auditService.logEvent(exception, author, "COMMENT_ADDED", "comment", null,
                request.getContent().length() > 50
                        ? request.getContent().substring(0, 47) + "..."
                        : request.getContent());

        return CommentDto.from(saved);
    }

    /**
     * Lists comments for an exception in chronological order.
     */
    @Transactional(readOnly = true)
    public List<CommentDto> getComments(UUID exceptionId) {
        if (!exceptionRepository.existsById(exceptionId)) {
            throw new ResourceNotFoundException("Exception not found with id: " + exceptionId);
        }
        return commentRepository.findByExceptionIdOrderByCreatedAtAsc(exceptionId).stream()
                .map(CommentDto::from)
                .collect(Collectors.toList());
    }

    /**
     * Helper methods to extract field names and values for exception records.
     */
    private String deriveFieldName(ReconciliationResult result) {
        return switch (result.getDiscrepancyType()) {
            case QUANTITY_MISMATCH -> "quantity";
            case PRICE_DIFFERENCE -> "price";
            case SETTLEMENT_DATE_MISMATCH -> "settlement_date";
            case MISSING_IN_SOURCE_A, MISSING_IN_SOURCE_B -> "trade_record";
            case DUPLICATE_TRADE -> "external_trade_id";
            case MULTI_FIELD_MISMATCH -> "multiple";
            case NONE -> "none";
        };
    }

    private String deriveExpectedValue(ReconciliationResult result) {
        if (result.getTradeA() != null) {
            return switch (result.getDiscrepancyType()) {
                case QUANTITY_MISMATCH -> result.getTradeA().getQuantity().stripTrailingZeros().toPlainString();
                case PRICE_DIFFERENCE -> result.getTradeA().getPrice().stripTrailingZeros().toPlainString();
                case SETTLEMENT_DATE_MISMATCH -> result.getTradeA().getSettlementDate().toString();
                case MISSING_IN_SOURCE_B -> result.getTradeA().getExternalTradeId();
                case DUPLICATE_TRADE -> "1";
                default -> result.getTradeA().getExternalTradeId();
            };
        }
        return "MISSING";
    }

    private String deriveActualValue(ReconciliationResult result) {
        if (result.getTradeB() != null) {
            return switch (result.getDiscrepancyType()) {
                case QUANTITY_MISMATCH -> result.getTradeB().getQuantity().stripTrailingZeros().toPlainString();
                case PRICE_DIFFERENCE -> result.getTradeB().getPrice().stripTrailingZeros().toPlainString();
                case SETTLEMENT_DATE_MISMATCH -> result.getTradeB().getSettlementDate().toString();
                case MISSING_IN_SOURCE_A -> result.getTradeB().getExternalTradeId();
                case DUPLICATE_TRADE -> result.getDifferenceDetails();
                default -> result.getTradeB().getExternalTradeId();
            };
        }
        return "MISSING";
    }

    private String truncate(String val, int maxLen) {
        if (val == null) return null;
        if (val.length() <= maxLen) return val;
        return val.substring(0, maxLen - 3) + "...";
    }
}
