package com.investops.trade.service;

import com.investops.common.dto.PagedResponse;
import com.investops.common.exception.ResourceNotFoundException;
import com.investops.trade.dto.ImportBatchDto;
import com.investops.trade.dto.TradeRecordDto;
import com.investops.trade.entity.ImportBatch;
import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeRecord;
import com.investops.trade.entity.TradeSide;
import com.investops.trade.repository.ImportBatchRepository;
import com.investops.trade.repository.TradeRecordRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeQueryService {

    private final TradeRecordRepository tradeRecordRepository;
    private final ImportBatchRepository importBatchRepository;
    private final TradeImportService tradeImportService;

    public PagedResponse<TradeRecordDto> findTrades(
            SourceSystem sourceSystem,
            LocalDate tradeDateFrom,
            LocalDate tradeDateTo,
            String symbol,
            TradeSide side,
            Pageable pageable) {

        Specification<TradeRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (sourceSystem != null) {
                predicates.add(cb.equal(root.get("sourceSystem"), sourceSystem));
            }
            if (tradeDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("tradeDate"), tradeDateFrom));
            }
            if (tradeDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("tradeDate"), tradeDateTo));
            }
            if (symbol != null && !symbol.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("symbol")), symbol.trim().toUpperCase()));
            }
            if (side != null) {
                predicates.add(cb.equal(root.get("side"), side));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<TradeRecord> page = tradeRecordRepository.findAll(spec, pageable);
        return PagedResponse.from(page.map(TradeRecordDto::from));
    }

    public TradeRecordDto getTradeById(UUID id) {
        TradeRecord trade = tradeRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trade not found with ID: " + id));
        return TradeRecordDto.from(trade);
    }

    public ImportBatchDto getBatchById(UUID batchId) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found with ID: " + batchId));
        return ImportBatchDto.from(batch, tradeImportService.deserializeErrors(batch.getErrorDetails()));
    }
}
