package com.investops.trade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investops.common.exception.BusinessValidationException;
import com.investops.common.exception.DuplicateResourceException;
import com.investops.trade.dto.TradeImportResponse;
import com.investops.trade.entity.BatchStatus;
import com.investops.trade.entity.ImportBatch;
import com.investops.trade.entity.SourceSystem;
import com.investops.trade.repository.ImportBatchRepository;
import com.investops.trade.repository.TradeRecordRepository;
import com.investops.trade.validation.CsvRowValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeImportServiceTest {

    @Mock
    private ImportBatchRepository importBatchRepository;

    @Mock
    private TradeRecordRepository tradeRecordRepository;

    private TradeImportService tradeImportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CsvRowValidator csvRowValidator = new CsvRowValidator();
        tradeImportService = new TradeImportService(
                importBatchRepository,
                tradeRecordRepository,
                csvRowValidator,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should successfully import valid trade CSV")
    void shouldImportValidCsv() {
        String csvContent = "trade_id,trade_date,settlement_date,symbol,side,quantity,price,counterparty,status\n" +
                "TRD-001,2026-10-15,2026-10-17,AAPL,BUY,100,178.50,GOLDMAN,CONFIRMED\n" +
                "TRD-002,2026-10-15,2026-10-17,MSFT,SELL,250,412.30,JPMORGAN,CONFIRMED\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "valid_trades.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        when(importBatchRepository.findByFileHash(any())).thenReturn(Optional.empty());
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
            ImportBatch b = invocation.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            return b;
        });
        when(tradeRecordRepository.existsBySourceSystemAndExternalTradeId(any(), any())).thenReturn(false);

        TradeImportResponse response = tradeImportService.importTrades(file, "SYSTEM_A", null);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(response.getTotalRows()).isEqualTo(2);
        assertThat(response.getValidRows()).isEqualTo(2);
        assertThat(response.getFailedRows()).isEqualTo(0);
        assertThat(response.getSkippedRows()).isEqualTo(0);
        assertThat(response.getErrors()).isEmpty();

        verify(tradeRecordRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Should reject upload when identical file hash was already completed")
    void shouldRejectDuplicateFileHash() {
        String csvContent = "trade_id,trade_date,settlement_date,symbol,side,quantity,price,counterparty,status\n" +
                "TRD-001,2026-10-15,2026-10-17,AAPL,BUY,100,178.50,GOLDMAN,CONFIRMED\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "trades.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        ImportBatch existingBatch = ImportBatch.builder()
                .id(UUID.randomUUID())
                .status(BatchStatus.COMPLETED)
                .build();

        when(importBatchRepository.findByFileHash(any())).thenReturn(Optional.of(existingBatch));

        assertThatThrownBy(() -> tradeImportService.importTrades(file, "SYSTEM_A", null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("File with identical contents has already been imported");

        verify(tradeRecordRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should reject CSV with missing required headers and mark batch failed")
    void shouldRejectMissingHeaders() {
        String csvContent = "trade_id,trade_date,symbol\nTRD-001,2026-10-15,AAPL\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad_header.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        when(importBatchRepository.findByFileHash(any())).thenReturn(Optional.empty());
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
            ImportBatch b = invocation.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            return b;
        });

        assertThatThrownBy(() -> tradeImportService.importTrades(file, "SYSTEM_A", null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("CSV missing required headers");
    }

    @Test
    @DisplayName("Should detect invalid rows and update failedRows count")
    void shouldDetectInvalidRows() {
        String csvContent = "trade_id,trade_date,settlement_date,symbol,side,quantity,price,counterparty,status\n" +
                "TRD-001,2026-10-15,2026-10-14,AAPL,BUY,100,178.50,GOLDMAN,CONFIRMED\n" + // settlement before trade
                "TRD-002,2026-10-15,2026-10-17,MSFT,BUY,-50,412.30,JPMORGAN,CONFIRMED\n" +  // negative quantity
                "TRD-003,2026-10-15,2026-10-17,NVDA,BUY,100,0.00,MORGAN,CONFIRMED\n";       // zero price

        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid_trades.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        when(importBatchRepository.findByFileHash(any())).thenReturn(Optional.empty());
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
            ImportBatch b = invocation.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            return b;
        });

        TradeImportResponse response = tradeImportService.importTrades(file, "SYSTEM_A", null);

        assertThat(response.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(response.getTotalRows()).isEqualTo(3);
        assertThat(response.getValidRows()).isEqualTo(0);
        assertThat(response.getFailedRows()).isEqualTo(3);
        assertThat(response.getErrors()).hasSize(3);

        verify(tradeRecordRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should flag duplicate external_trade_id within the same file")
    void shouldFlagDuplicateTradeIdInFile() {
        String csvContent = "trade_id,trade_date,settlement_date,symbol,side,quantity,price,counterparty,status\n" +
                "TRD-001,2026-10-15,2026-10-17,AAPL,BUY,100,178.50,GOLDMAN,CONFIRMED\n" +
                "TRD-001,2026-10-15,2026-10-17,AAPL,BUY,100,178.50,GOLDMAN,CONFIRMED\n"; // duplicate ID

        MockMultipartFile file = new MockMultipartFile(
                "file", "dup_trades.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        when(importBatchRepository.findByFileHash(any())).thenReturn(Optional.empty());
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
            ImportBatch b = invocation.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            return b;
        });
        when(tradeRecordRepository.existsBySourceSystemAndExternalTradeId(any(), any())).thenReturn(false);

        TradeImportResponse response = tradeImportService.importTrades(file, "SYSTEM_A", null);

        assertThat(response.getTotalRows()).isEqualTo(2);
        assertThat(response.getValidRows()).isEqualTo(1);
        assertThat(response.getFailedRows()).isEqualTo(1);
        assertThat(response.getErrors().get(0).getMessage()).contains("Duplicate trade_id detected within this batch");
    }

    @Test
    @DisplayName("Should skip trades that already exist in database for that source system")
    void shouldSkipAlreadyExistingTradeInDatabase() {
        String csvContent = "trade_id,trade_date,settlement_date,symbol,side,quantity,price,counterparty,status\n" +
                "TRD-001,2026-10-15,2026-10-17,AAPL,BUY,100,178.50,GOLDMAN,CONFIRMED\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "existing.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        when(importBatchRepository.findByFileHash(any())).thenReturn(Optional.empty());
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
            ImportBatch b = invocation.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            return b;
        });
        when(tradeRecordRepository.existsBySourceSystemAndExternalTradeId(SourceSystem.SYSTEM_A, "TRD-001")).thenReturn(true);

        TradeImportResponse response = tradeImportService.importTrades(file, "SYSTEM_A", null);

        assertThat(response.getTotalRows()).isEqualTo(1);
        assertThat(response.getValidRows()).isEqualTo(0);
        assertThat(response.getSkippedRows()).isEqualTo(1);
        assertThat(response.getFailedRows()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when file is empty")
    void shouldThrowWhenEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> tradeImportService.importTrades(emptyFile, "SYSTEM_A", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CSV file cannot be empty");
    }
}
