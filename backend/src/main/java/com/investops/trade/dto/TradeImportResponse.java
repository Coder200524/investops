package com.investops.trade.dto;

import com.investops.trade.entity.BatchStatus;
import com.investops.trade.entity.SourceSystem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeImportResponse {
    private UUID batchId;
    private String filename;
    private SourceSystem sourceSystem;
    private int totalRows;
    private int validRows;
    private int skippedRows;
    private int failedRows;
    private BatchStatus status;
    private List<RowErrorDto> errors;
}
