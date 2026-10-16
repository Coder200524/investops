package com.investops.exceptionmgmt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investops.auth.entity.AppUser;
import com.investops.auth.entity.UserRole;
import com.investops.auth.repository.UserRepository;
import com.investops.exceptionmgmt.dto.CommentRequest;
import com.investops.exceptionmgmt.dto.ExceptionUpdateRequest;
import com.investops.exceptionmgmt.entity.ExceptionStatus;
import com.investops.exceptionmgmt.entity.TradeException;
import com.investops.exceptionmgmt.repository.ExceptionAuditLogRepository;
import com.investops.exceptionmgmt.repository.ExceptionCommentRepository;
import com.investops.exceptionmgmt.repository.ExceptionRepository;
import com.investops.exceptionmgmt.service.ExceptionService;
import com.investops.reconciliation.entity.*;
import com.investops.reconciliation.repository.ReconciliationResultRepository;
import com.investops.reconciliation.repository.ReconciliationRunRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ExceptionController Integration Tests")
class ExceptionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExceptionRepository exceptionRepository;

    @Autowired
    private ExceptionCommentRepository commentRepository;

    @Autowired
    private ExceptionAuditLogRepository auditLogRepository;

    @Autowired
    private ReconciliationResultRepository resultRepository;

    @Autowired
    private ReconciliationRunRepository runRepository;

    @Autowired
    private TradeRecordRepository tradeRecordRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExceptionService exceptionService;

    private AppUser analystUser;
    private TradeException testException;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        commentRepository.deleteAll();
        exceptionRepository.deleteAll();
        resultRepository.deleteAll();
        runRepository.deleteAll();
        tradeRecordRepository.deleteAll();
        importBatchRepository.deleteAll();
        userRepository.deleteAll();

        analystUser = userRepository.save(AppUser.builder()
                .username("analyst1")
                .email("analyst1@investops.com")
                .passwordHash("hashed-pw")
                .role(UserRole.ANALYST)
                .build());

        ImportBatch batch = importBatchRepository.save(ImportBatch.builder()
                .filename("trades.csv")
                .sourceSystem(SourceSystem.SYSTEM_A)
                .fileHash("hash-" + UUID.randomUUID())
                .totalRows(2)
                .validRows(2)
                .build());

        TradeRecord tradeA = tradeRecordRepository.save(TradeRecord.builder()
                .externalTradeId("TRD-999")
                .sourceSystem(SourceSystem.SYSTEM_A)
                .symbol("AAPL")
                .side(TradeSide.BUY)
                .quantity(new BigDecimal("100.00000000"))
                .price(new BigDecimal("150.00000000"))
                .counterparty("JPMORGAN")
                .tradeDate(LocalDate.of(2026, 10, 5))
                .settlementDate(LocalDate.of(2026, 10, 7))
                .importBatch(batch)
                .build());

        TradeRecord tradeB = tradeRecordRepository.save(TradeRecord.builder()
                .externalTradeId("TRD-999")
                .sourceSystem(SourceSystem.SYSTEM_B)
                .symbol("AAPL")
                .side(TradeSide.BUY)
                .quantity(new BigDecimal("80.00000000"))
                .price(new BigDecimal("150.00000000"))
                .counterparty("JPMORGAN")
                .tradeDate(LocalDate.of(2026, 10, 5))
                .settlementDate(LocalDate.of(2026, 10, 7))
                .importBatch(batch)
                .build());

        ReconciliationRun run = runRepository.save(ReconciliationRun.builder()
                .tradeDateFrom(LocalDate.of(2026, 10, 1))
                .tradeDateTo(LocalDate.of(2026, 10, 10))
                .sourceA(SourceSystem.SYSTEM_A)
                .sourceB(SourceSystem.SYSTEM_B)
                .status(RunStatus.COMPLETED)
                .build());

        ReconciliationResult result = resultRepository.save(ReconciliationResult.builder()
                .run(run)
                .tradeA(tradeA)
                .tradeB(tradeB)
                .matchStatus(MatchStatus.MISMATCHED)
                .discrepancyType(DiscrepancyType.QUANTITY_MISMATCH)
                .severity(DiscrepancySeverity.CRITICAL)
                .differenceDetails("Quantity mismatch: 100 vs 80")
                .build());

        List<TradeException> exceptions = exceptionService.createExceptionsForResults(List.of(result));
        testException = exceptions.get(0);
    }

    @Test
    @DisplayName("Unauthenticated request to /api/v1/exceptions returns 401 Unauthorized")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/exceptions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("Request with unauthorized role returns 403 Forbidden")
    void shouldReturn403WhenUnauthorizedRole() throws Exception {
        mockMvc.perform(get("/api/v1/exceptions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    @DisplayName("GET /api/v1/exceptions returns paginated list of exceptions for ANALYST")
    void shouldListExceptions() throws Exception {
        mockMvc.perform(get("/api/v1/exceptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].businessKey").value("TRD-999"))
                .andExpect(jsonPath("$.data.content[0].exceptionType").value("QUANTITY_MISMATCH"))
                .andExpect(jsonPath("$.data.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.content[0].severity").value("CRITICAL"));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    @DisplayName("GET /api/v1/exceptions/{id} returns full detail with comments and audit log")
    void shouldGetExceptionDetail() throws Exception {
        mockMvc.perform(get("/api/v1/exceptions/{id}", testException.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exception.id").value(testException.getId().toString()))
                .andExpect(jsonPath("$.data.result").exists())
                .andExpect(jsonPath("$.data.auditLogs", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.auditLogs[0].action").value("CREATED"));
    }

    @Test
    @WithMockUser(username = "analyst1", roles = "ANALYST")
    @DisplayName("PATCH /api/v1/exceptions/{id} updates status, assignee, and appends audit log")
    void shouldUpdateExceptionStatusAndAssignee() throws Exception {
        ExceptionUpdateRequest updateRequest = ExceptionUpdateRequest.builder()
                .status(ExceptionStatus.ACKNOWLEDGED)
                .assignedTo(analystUser.getId())
                .version(testException.getVersion())
                .build();

        mockMvc.perform(patch("/api/v1/exceptions/{id}", testException.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.data.assignedToId").value(analystUser.getId().toString()))
                .andExpect(jsonPath("$.data.assignedToUsername").value("analyst1"));

        // Verify audit log has new entries
        mockMvc.perform(get("/api/v1/exceptions/{id}/audit-log", testException.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3))) // CREATED, STATUS_CHANGED, ASSIGNMENT_CHANGED
                .andExpect(jsonPath("$.data[1].action").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.data[2].action").value("ASSIGNMENT_CHANGED"));
    }

    @Test
    @WithMockUser(username = "analyst1", roles = "ANALYST")
    @DisplayName("PATCH with stale version returns 409 Conflict")
    void shouldReturn409OnVersionMismatch() throws Exception {
        ExceptionUpdateRequest updateRequest = ExceptionUpdateRequest.builder()
                .status(ExceptionStatus.ACKNOWLEDGED)
                .version(999) // Wrong version
                .build();

        mockMvc.perform(patch("/api/v1/exceptions/{id}", testException.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.title").value("Conflict"));
    }

    @Test
    @WithMockUser(username = "analyst1", roles = "ANALYST")
    @DisplayName("PATCH with invalid state transition returns 422 Unprocessable Entity")
    void shouldReturn422OnInvalidTransition() throws Exception {
        ExceptionUpdateRequest updateRequest = ExceptionUpdateRequest.builder()
                .status(ExceptionStatus.CLOSED) // OPEN -> CLOSED is invalid
                .version(testException.getVersion())
                .build();

        mockMvc.perform(patch("/api/v1/exceptions/{id}", testException.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.title").value("Business Validation Failed"))
                .andExpect(jsonPath("$.error.detail").value(containsString("Invalid status transition")));
    }

    @Test
    @WithMockUser(username = "analyst1", roles = "ANALYST")
    @DisplayName("POST /api/v1/exceptions/{id}/comments adds investigation comment")
    void shouldAddCommentToException() throws Exception {
        CommentRequest commentRequest = CommentRequest.builder()
                .content("Escalated to trader desk for quantity verification")
                .build();

        mockMvc.perform(post("/api/v1/exceptions/{id}/comments", testException.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("Escalated to trader desk for quantity verification"))
                .andExpect(jsonPath("$.data.authorUsername").value("analyst1"));

        // Verify comment appears in GET /api/v1/exceptions/{id}/comments
        mockMvc.perform(get("/api/v1/exceptions/{id}/comments", testException.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].content").value("Escalated to trader desk for quantity verification"));
    }
}
