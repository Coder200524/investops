package com.investops.trade.dto;

import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeRecord;
import com.investops.trade.entity.TradeSide;
import com.investops.trade.entity.TradeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeRecordDto {
    private UUID id;
    private String externalTradeId;
    private SourceSystem sourceSystem;
    private LocalDate tradeDate;
    private LocalDate settlementDate;
    private String symbol;
    private TradeSide side;
    private BigDecimal quantity;
    private BigDecimal price;
    private String counterparty;
    private TradeStatus status;
    private UUID importBatchId;
    private Instant createdAt;

    public static TradeRecordDto from(TradeRecord record) {
        return TradeRecordDto.builder()
                .id(record.getId())
                .externalTradeId(record.getExternalTradeId())
                .sourceSystem(record.getSourceSystem())
                .tradeDate(record.getTradeDate())
                .settlementDate(record.getSettlementDate())
                .symbol(record.getSymbol())
                .side(record.getSide())
                .quantity(record.getQuantity())
                .price(record.getPrice())
                .counterparty(record.getCounterparty())
                .status(record.getStatus())
                .importBatchId(record.getImportBatch() != null ? record.getImportBatch().getId() : null)
                .createdAt(record.getCreatedAt())
                .build();
    }
}
