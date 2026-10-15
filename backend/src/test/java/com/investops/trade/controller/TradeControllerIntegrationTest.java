package com.investops.trade.controller;

import com.investops.trade.entity.*;
import com.investops.trade.repository.ImportBatchRepository;
import com.investops.trade.repository.TradeRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TradeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TradeRecordRepository tradeRecordRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @BeforeEach
    void setUp() {
        tradeRecordRepository.deleteAll();
        importBatchRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/trades/import should import valid CSV and return 201")
    void shouldImportValidCsvViaApi() throws Exception {
        String csvContent = "trade_id,trade_date,settlement_date,symbol,side,quantity,price,counterparty,status\n" +
                "API-TRD-01,2026-10-15,2026-10-17,AAPL,BUY,100,178.50,GOLDMAN,CONFIRMED\n" +
                "API-TRD-02,2026-10-15,2026-10-17,MSFT,SELL,250,412.30,JPMORGAN,CONFIRMED\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "system_a_trades.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/trades/import")
                        .file(file)
                        .param("sourceSystem", "SYSTEM_A"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.validRows").value(2))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.batchId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/trades/import should return 409 Conflict on identical re-upload")
    void shouldReturnConflictOnIdenticalReupload() throws Exception {
        String csvContent = "trade_id,trade_date,settlement_date,symbol,side,quantity,price,counterparty,status\n" +
                "HASH-01,2026-10-15,2026-10-17,AAPL,BUY,100,178.50,GOLDMAN,CONFIRMED\n";

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "trades_hash.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "trades_hash.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        // First upload succeeds
        mockMvc.perform(multipart("/api/v1/trades/import")
                        .file(file1)
                        .param("sourceSystem", "SYSTEM_A"))
                .andExpect(status().isCreated());

        // Duplicate upload fails with 409
        mockMvc.perform(multipart("/api/v1/trades/import")
                        .file(file2)
                        .param("sourceSystem", "SYSTEM_A"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.title").value("Duplicate Resource"));
    }

    @Test
    @DisplayName("GET /api/v1/trades should support pagination and filtering")
    void shouldFilterAndPaginateTrades() throws Exception {
        ImportBatch batch = importBatchRepository.save(ImportBatch.builder()
                .filename("test.csv")
                .sourceSystem(SourceSystem.SYSTEM_A)
                .fileHash("fakehash123")
                .status(BatchStatus.COMPLETED)
                .build());

        tradeRecordRepository.save(TradeRecord.builder()
                .externalTradeId("AAPL-1")
                .sourceSystem(SourceSystem.SYSTEM_A)
                .tradeDate(LocalDate.of(2026, 10, 10))
                .settlementDate(LocalDate.of(2026, 10, 12))
                .symbol("AAPL")
                .side(TradeSide.BUY)
                .quantity(new BigDecimal("100.00000000"))
                .price(new BigDecimal("150.00000000"))
                .counterparty("GOLDMAN")
                .importBatch(batch)
                .build());

        tradeRecordRepository.save(TradeRecord.builder()
                .externalTradeId("MSFT-1")
                .sourceSystem(SourceSystem.SYSTEM_B)
                .tradeDate(LocalDate.of(2026, 10, 12))
                .settlementDate(LocalDate.of(2026, 10, 14))
                .symbol("MSFT")
                .side(TradeSide.SELL)
                .quantity(new BigDecimal("200.00000000"))
                .price(new BigDecimal("300.00000000"))
                .counterparty("CITI")
                .importBatch(batch)
                .build());

        // Test filter by symbol and sourceSystem
        mockMvc.perform(get("/api/v1/trades")
                        .param("symbol", "AAPL")
                        .param("sourceSystem", "SYSTEM_A")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].externalTradeId").value("AAPL-1"))
                .andExpect(jsonPath("$.data.content[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/trades/{id} should return 404 for unknown trade")
    void shouldReturn404ForUnknownTrade() throws Exception {
        mockMvc.perform(get("/api/v1/trades/" + UUID.randomUUID())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.title").value("Resource Not Found"));
    }

    @Test
    @DisplayName("GET /api/v1/trades/batches/{batchId} should return batch details")
    void shouldReturnBatchDetails() throws Exception {
        ImportBatch batch = importBatchRepository.save(ImportBatch.builder()
                .filename("batch_test.csv")
                .sourceSystem(SourceSystem.SYSTEM_B)
                .fileHash("hash-batch-999")
                .totalRows(10)
                .validRows(8)
                .failedRows(2)
                .errorDetails("[{\"row\":5,\"field\":\"price\",\"message\":\"negative price\",\"rawValue\":\"-10\"}]")
                .status(BatchStatus.COMPLETED)
                .build());

        mockMvc.perform(get("/api/v1/trades/batches/" + batch.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(batch.getId().toString()))
                .andExpect(jsonPath("$.data.validRows").value(8))
                .andExpect(jsonPath("$.data.failedRows").value(2))
                .andExpect(jsonPath("$.data.errors", hasSize(1)))
                .andExpect(jsonPath("$.data.errors[0].field").value("price"));
    }
}
