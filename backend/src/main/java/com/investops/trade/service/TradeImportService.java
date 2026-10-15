package com.investops.trade.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investops.common.exception.BusinessValidationException;
import com.investops.common.exception.DuplicateResourceException;
import com.investops.common.util.HashUtil;
import com.investops.trade.dto.RowErrorDto;
import com.investops.trade.dto.TradeImportResponse;
import com.investops.trade.entity.*;
import com.investops.trade.repository.ImportBatchRepository;
import com.investops.trade.repository.TradeRecordRepository;
import com.investops.trade.validation.CsvRowValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeImportService {

    private final ImportBatchRepository importBatchRepository;
    private final TradeRecordRepository tradeRecordRepository;
    private final CsvRowValidator csvRowValidator;
    private final ObjectMapper objectMapper;

    @Transactional
    public TradeImportResponse importTrades(MultipartFile file, String sourceSystemStr, UUID uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file cannot be empty");
        }

        SourceSystem sourceSystem = SourceSystem.fromString(sourceSystemStr);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "trades.csv";

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessValidationException("Failed to read uploaded file: " + e.getMessage());
        }

        // 1. Calculate SHA-256 Checksum
        String fileHash = HashUtil.calculateSha256(fileBytes);
        Optional<ImportBatch> existingBatchOpt = importBatchRepository.findByFileHash(fileHash);
        if (existingBatchOpt.isPresent()) {
            ImportBatch existing = existingBatchOpt.get();
            if (existing.getStatus() == BatchStatus.COMPLETED) {
                throw new DuplicateResourceException(
                        "File with identical contents has already been imported under batch ID: " + existing.getId());
            }
        }

        // 2. Create Initial Batch in PROCESSING state
        ImportBatch batch = ImportBatch.builder()
                .filename(filename)
                .sourceSystem(sourceSystem)
                .uploadedBy(uploadedBy)
                .fileHash(fileHash)
                .status(BatchStatus.PROCESSING)
                .build();
        batch = importBatchRepository.save(batch);

        List<RowErrorDto> allErrors = new ArrayList<>();
        List<TradeRecord> tradesToPersist = new ArrayList<>();
        Set<String> seenTradeIdsInBatch = new HashSet<>();
        int totalRows = 0;
        int skippedRows = 0;

        // 3. Parse CSV with RFC-4180 format
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            // Validate header presence
            List<String> headers = csvParser.getHeaderNames();
            validateHeaders(headers);

            for (CSVRecord record : csvParser) {
                totalRows++;
                int rowNumber = (int) record.getRecordNumber() + 1; // 1-indexed including header

                CsvRowValidator.ValidationResult result = csvRowValidator.validateRow(record, rowNumber);
                if (!result.isValid()) {
                    allErrors.addAll(result.getErrors());
                    continue;
                }

                CsvRowValidator.ValidatedTradeRow row = result.getRow();

                // Intra-batch duplicate check
                if (!seenTradeIdsInBatch.add(row.getExternalTradeId())) {
                    allErrors.add(new RowErrorDto(
                            rowNumber,
                            "trade_id",
                            "Duplicate trade_id detected within this batch: " + row.getExternalTradeId(),
                            row.getExternalTradeId()));
                    continue;
                }

                // Database idempotency check (pre-existing trade in DB for this source system)
                if (tradeRecordRepository.existsBySourceSystemAndExternalTradeId(sourceSystem, row.getExternalTradeId())) {
                    skippedRows++;
                    continue;
                }

                TradeRecord tradeRecord = TradeRecord.builder()
                        .externalTradeId(row.getExternalTradeId())
                        .sourceSystem(sourceSystem)
                        .tradeDate(row.getTradeDate())
                        .settlementDate(row.getSettlementDate())
                        .symbol(row.getSymbol())
                        .side(row.getSide())
                        .quantity(row.getQuantity())
                        .price(row.getPrice())
                        .counterparty(row.getCounterparty())
                        .status(row.getStatus())
                        .importBatch(batch)
                        .build();

                tradesToPersist.add(tradeRecord);
            }

        } catch (BusinessValidationException e) {
            batch.setStatus(BatchStatus.FAILED);
            batch.setErrorDetails(serializeErrors(List.of(new RowErrorDto(1, "header", e.getMessage(), ""))));
            importBatchRepository.save(batch);
            throw e;
        } catch (Exception e) {
            batch.setStatus(BatchStatus.FAILED);
            batch.setErrorDetails(serializeErrors(List.of(new RowErrorDto(0, "file", "CSV parsing failed: " + e.getMessage(), ""))));
            importBatchRepository.save(batch);
            throw new BusinessValidationException("Failed to parse CSV file: " + e.getMessage());
        }

        // 4. Persist Valid Trades
        if (!tradesToPersist.isEmpty()) {
            tradeRecordRepository.saveAll(tradesToPersist);
        }

        int failedRows = allErrors.size();
        int validRows = tradesToPersist.size();

        // 5. Update Batch Status and Metrics
        BatchStatus finalStatus;
        if (totalRows == 0) {
            finalStatus = BatchStatus.COMPLETED;
        } else if (validRows == 0 && failedRows > 0) {
            finalStatus = BatchStatus.FAILED;
        } else {
            finalStatus = BatchStatus.COMPLETED;
        }

        batch.setTotalRows(totalRows);
        batch.setValidRows(validRows);
        batch.setSkippedRows(skippedRows);
        batch.setFailedRows(failedRows);
        batch.setStatus(finalStatus);
        batch.setErrorDetails(serializeErrors(allErrors));

        batch = importBatchRepository.save(batch);

        return TradeImportResponse.builder()
                .batchId(batch.getId())
                .filename(filename)
                .sourceSystem(sourceSystem)
                .totalRows(totalRows)
                .validRows(validRows)
                .skippedRows(skippedRows)
                .failedRows(failedRows)
                .status(finalStatus)
                .errors(allErrors)
                .build();
    }

    private void validateHeaders(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new BusinessValidationException("CSV file contains no headers");
        }

        Set<String> normalizedHeaders = new HashSet<>();
        for (String h : headers) {
            normalizedHeaders.add(h.trim().toLowerCase());
        }

        List<String> missingHeaders = new ArrayList<>();
        for (String expected : CsvRowValidator.EXPECTED_HEADERS) {
            if (!normalizedHeaders.contains(expected.toLowerCase())) {
                missingHeaders.add(expected);
            }
        }

        if (!missingHeaders.isEmpty()) {
            throw new BusinessValidationException("CSV missing required headers: " + String.join(", ", missingHeaders));
        }
    }

    private String serializeErrors(List<RowErrorDto> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize batch errors to JSON", e);
            return "[]";
        }
    }

    public List<RowErrorDto> deserializeErrors(String errorDetails) {
        if (errorDetails == null || errorDetails.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(errorDetails, new TypeReference<List<RowErrorDto>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize batch errors from JSON", e);
            return Collections.emptyList();
        }
    }
}
