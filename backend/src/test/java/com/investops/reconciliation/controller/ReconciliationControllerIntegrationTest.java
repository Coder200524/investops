package com.investops.reconciliation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investops.reconciliation.dto.ReconciliationRequest;
import com.investops.reconciliation.entity.MatchStatus;
import com.investops.reconciliation.repository.ReconciliationResultRepository;
import com.investops.reconciliation.repository.ReconciliationRunRepository;
import com.investops.reconciliation.service.ReconciliationService;
import com.investops.trade.entity.ImportBatch;
import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeRecord;
import com.investops.trade.entity.TradeSide;
import com.investops.trade.repository.ImportBatchRepository;
import com.investops.trade.repository.TradeRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ReconciliationController Integration Tests")
class ReconciliationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TradeRecordRepository tradeRecordRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private ReconciliationResultRepository resultRepository;

    @Autowired
    private ReconciliationRunRepository runRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    private ImportBatch batchA;
    private ImportBatch batchB;

    @BeforeEach
    void setUp() {
        resultRepository.deleteAll();
        runRepository.deleteAll();
        tradeRecordRepository.deleteAll();
        importBatchRepository.deleteAll();

        batchA = importBatchRepository.save(ImportBatch.builder()
                .filename("trades_a.csv")
                .sourceSystem(SourceSystem.SYSTEM_A)
                .fileHash("hash-a-" + UUID.randomUUID())
                .totalRows(10)
                .validRows(10)
                .build());

        batchB = importBatchRepository.save(ImportBatch.builder()
                .filename("trades_b.csv")
                .sourceSystem(SourceSystem.SYSTEM_B)
                .fileHash("hash-b-" + UUID.randomUUID())
                .totalRows(10)
                .validRows(10)
                .build());
    }

    private TradeRecord createTrade(SourceSystem source, String externalId, String symbol,
                                    TradeSide side, String qty, String price,
                                    LocalDate tradeDate, LocalDate settlDate) {
        return tradeRecordRepository.save(TradeRecord.builder()
                .externalTradeId(externalId)
                .sourceSystem(source)
                .symbol(symbol)
                .side(side)
                .quantity(new BigDecimal(qty))
                .price(new BigDecimal(price))
                .counterparty("JPMORGAN")
                .tradeDate(tradeDate)
                .settlementDate(settlDate)
                .importBatch(source == SourceSystem.SYSTEM_A ? batchA : batchB)
                .build());
    }

    @Test
    @DisplayName("POST /api/v1/reconciliation/runs returns 202 Accepted and completes asynchronously")
    void shouldInitiateRunAndCompleteAsynchronously() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 5);

        // Trade 1: Exact Match (TRD-001 vs TRD-1)
        createTrade(SourceSystem.SYSTEM_A, "TRD-001", "AAPL", TradeSide.BUY, "100.00", "150.00", date, date.plusDays(2));
        createTrade(SourceSystem.SYSTEM_B, "TRD-1", "AAPL", TradeSide.BUY, "100.00", "150.00", date, date.plusDays(2));

        // Trade 2: Quantity Break
        createTrade(SourceSystem.SYSTEM_A, "TRD-002", "MSFT", TradeSide.SELL, "200.00", "310.00", date, date.plusDays(2));
        createTrade(SourceSystem.SYSTEM_B, "TRD-2", "MSFT", TradeSide.SELL, "180.00", "310.00", date, date.plusDays(2));

        // Trade 3: Missing in Source B
        createTrade(SourceSystem.SYSTEM_A, "TRD-003", "GOOGL", TradeSide.BUY, "50.00", "140.00", date, date.plusDays(2));

        ReconciliationRequest request = ReconciliationRequest.builder()
                .tradeDateFrom(LocalDate.of(2026, 10, 1))
                .tradeDateTo(LocalDate.of(2026, 10, 10))
                .sourceA(SourceSystem.SYSTEM_A)
                .sourceB(SourceSystem.SYSTEM_B)
                .priceTolerance(new BigDecimal("0.01000000"))
                .build();

        String responseBody = mockMvc.perform(post("/api/v1/reconciliation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.runId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andReturn().getResponse().getContentAsString();

        String runIdStr = objectMapper.readTree(responseBody).get("data").get("runId").asText();
        UUID runId = UUID.fromString(runIdStr);

        // Wait for async execution to complete
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            var run = runRepository.findById(runId).orElse(null);
            return run != null && "COMPLETED".equals(run.getStatus().name());
        });

        // Verify GET /api/v1/reconciliation/runs/{id}
        mockMvc.perform(get("/api/v1/reconciliation/runs/{id}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(runIdStr))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalCompared").value(3))
                .andExpect(jsonPath("$.data.matchedCount").value(1))
                .andExpect(jsonPath("$.data.breakCount").value(2))
                .andExpect(jsonPath("$.data.matchRate").value(33.33));

        // Verify GET /api/v1/reconciliation/runs/{id}/results
        mockMvc.perform(get("/api/v1/reconciliation/runs/{id}/results", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content", hasSize(3)));

        // Verify GET /api/v1/reconciliation/runs/{id}/results with MATCHED filter
        mockMvc.perform(get("/api/v1/reconciliation/runs/{id}/results", runId)
                        .param("matchStatus", "MATCHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].matchStatus").value("MATCHED"))
                .andExpect(jsonPath("$.data.content[0].businessKey").value("TRD-001"));

        // Verify GET /api/v1/reconciliation/runs/{id}/results with MISMATCHED filter
        mockMvc.perform(get("/api/v1/reconciliation/runs/{id}/results", runId)
                        .param("matchStatus", "MISMATCHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].discrepancyType").value("QUANTITY_MISMATCH"));

        // Verify GET /api/v1/reconciliation/runs/{id}/results with MISSING filter
        mockMvc.perform(get("/api/v1/reconciliation/runs/{id}/results", runId)
                        .param("matchStatus", "MISSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].discrepancyType").value("MISSING_IN_SOURCE_B"));
    }

    @Test
    @DisplayName("GET /api/v1/reconciliation/runs returns paginated runs")
    void shouldReturnPaginatedRuns() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 5);
        ReconciliationRequest request = ReconciliationRequest.builder()
                .tradeDateFrom(date)
                .tradeDateTo(date)
                .sourceA(SourceSystem.SYSTEM_A)
                .sourceB(SourceSystem.SYSTEM_B)
                .build();

        reconciliationService.executeRunSync(request, null);

        mockMvc.perform(get("/api/v1/reconciliation/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/v1/reconciliation/runs with invalid date range returns 422 Unprocessable Entity")
    void shouldReturnBadRequestOnInvertedDates() throws Exception {
        ReconciliationRequest request = ReconciliationRequest.builder()
                .tradeDateFrom(LocalDate.of(2026, 10, 15))
                .tradeDateTo(LocalDate.of(2026, 10, 10)) // tradeDateTo is before tradeDateFrom
                .sourceA(SourceSystem.SYSTEM_A)
                .sourceB(SourceSystem.SYSTEM_B)
                .build();

        mockMvc.perform(post("/api/v1/reconciliation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.title").value("Business Validation Failed"))
                .andExpect(jsonPath("$.error.detail").value("tradeDateTo cannot precede tradeDateFrom"));
    }

    @Test
    @DisplayName("POST /api/v1/reconciliation/runs with same source system returns 422 Unprocessable Entity")
    void shouldReturnBadRequestOnSameSourceSystems() throws Exception {
        ReconciliationRequest request = ReconciliationRequest.builder()
                .tradeDateFrom(LocalDate.of(2026, 10, 1))
                .tradeDateTo(LocalDate.of(2026, 10, 10))
                .sourceA(SourceSystem.SYSTEM_A)
                .sourceB(SourceSystem.SYSTEM_A)
                .build();

        mockMvc.perform(post("/api/v1/reconciliation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.title").value("Business Validation Failed"))
                .andExpect(jsonPath("$.error.detail").value("Source systems must be different"));
    }

    @Test
    @DisplayName("GET /api/v1/reconciliation/runs/{id} for non-existent run returns 404")
    void shouldReturnNotFoundForNonExistentRun() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/reconciliation/runs/{id}", randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.title").value("Resource Not Found"));
    }
}
