package com.investops.trade.dto;

import com.investops.trade.entity.BatchStatus;
import com.investops.trade.entity.ImportBatch;
import com.investops.trade.entity.SourceSystem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatchDto {
    private UUID id;
    private String filename;
    private SourceSystem sourceSystem;
    private UUID uploadedBy;
    private int totalRows;
    private int validRows;
    private int skippedRows;
    private int failedRows;
    private BatchStatus status;
    private String fileHash;
    private List<RowErrorDto> errors;
    private Instant createdAt;
    private Instant updatedAt;

    public static ImportBatchDto from(ImportBatch batch, List<RowErrorDto> parsedErrors) {
        return ImportBatchDto.builder()
                .id(batch.getId())
                .filename(batch.getFilename())
                .sourceSystem(batch.getSourceSystem())
                .uploadedBy(batch.getUploadedBy())
                .totalRows(batch.getTotalRows())
                .validRows(batch.getValidRows())
                .skippedRows(batch.getSkippedRows())
                .failedRows(batch.getFailedRows())
                .status(batch.getStatus())
                .fileHash(batch.getFileHash())
                .errors(parsedErrors)
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }
}
