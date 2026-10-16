package com.investops.exceptionmgmt.service;

import com.investops.auth.entity.AppUser;
import com.investops.auth.entity.UserRole;
import com.investops.auth.repository.UserRepository;
import com.investops.common.exception.BusinessValidationException;
import com.investops.common.exception.ConflictException;
import com.investops.common.exception.ResourceNotFoundException;
import com.investops.exceptionmgmt.dto.CommentRequest;
import com.investops.exceptionmgmt.dto.ExceptionDetailDto;
import com.investops.exceptionmgmt.dto.ExceptionDto;
import com.investops.exceptionmgmt.dto.ExceptionUpdateRequest;
import com.investops.exceptionmgmt.entity.ExceptionAuditLog;
import com.investops.exceptionmgmt.entity.ExceptionComment;
import com.investops.exceptionmgmt.entity.ExceptionStatus;
import com.investops.exceptionmgmt.entity.TradeException;
import com.investops.exceptionmgmt.repository.ExceptionAuditLogRepository;
import com.investops.exceptionmgmt.repository.ExceptionCommentRepository;
import com.investops.exceptionmgmt.repository.ExceptionRepository;
import com.investops.reconciliation.entity.*;
import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeRecord;
import com.investops.trade.entity.TradeSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExceptionService Unit Tests")
class ExceptionServiceTest {

    @Mock
    private ExceptionRepository exceptionRepository;

    @Mock
    private ExceptionCommentRepository commentRepository;

    @Mock
    private ExceptionAuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ExceptionService exceptionService;

    private ReconciliationRun run;
    private ReconciliationResult breakResult;
    private AppUser analyst;

    @BeforeEach
    void setUp() {
        run = ReconciliationRun.builder()
                .id(UUID.randomUUID())
                .tradeDateFrom(LocalDate.of(2026, 10, 1))
                .tradeDateTo(LocalDate.of(2026, 10, 10))
                .sourceA(SourceSystem.SYSTEM_A)
                .sourceB(SourceSystem.SYSTEM_B)
                .build();

        TradeRecord tradeA = TradeRecord.builder()
                .id(UUID.randomUUID())
                .externalTradeId("TRD-100")
                .quantity(new BigDecimal("100.00"))
                .price(new BigDecimal("50.00"))
                .settlementDate(LocalDate.of(2026, 10, 7))
                .build();

        TradeRecord tradeB = TradeRecord.builder()
                .id(UUID.randomUUID())
                .externalTradeId("TRD-100")
                .quantity(new BigDecimal("90.00"))
                .price(new BigDecimal("50.00"))
                .settlementDate(LocalDate.of(2026, 10, 7))
                .build();

        breakResult = ReconciliationResult.builder()
                .id(UUID.randomUUID())
                .run(run)
                .tradeA(tradeA)
                .tradeB(tradeB)
                .matchStatus(MatchStatus.MISMATCHED)
                .discrepancyType(DiscrepancyType.QUANTITY_MISMATCH)
                .severity(DiscrepancySeverity.HIGH)
                .differenceDetails("Quantity mismatch: 100 vs 90 (diff: 10)")
                .build();

        analyst = AppUser.builder()
                .id(UUID.randomUUID())
                .username("analyst1")
                .email("analyst1@investops.com")
                .role(UserRole.ANALYST)
                .build();
    }

    @Nested
    @DisplayName("Exception Creation and Dedup Tests")
    class CreationAndDedupTests {

        @Test
        @DisplayName("Should convert eligible break results into exceptions and record audit log")
        void shouldCreateExceptionForBreakResult() {
            when(exceptionRepository.existsByResultId(breakResult.getId())).thenReturn(false);
            when(exceptionRepository.save(any(TradeException.class))).thenAnswer(invocation -> {
                TradeException ex = invocation.getArgument(0);
                ex.setId(UUID.randomUUID());
                return ex;
            });

            List<TradeException> created = exceptionService.createExceptionsForResults(List.of(breakResult));

            assertThat(created).hasSize(1);
            TradeException ex = created.get(0);
            assertThat(ex.getResult()).isEqualTo(breakResult);
            assertThat(ex.getExceptionType()).isEqualTo(DiscrepancyType.QUANTITY_MISMATCH);
            assertThat(ex.getSeverity()).isEqualTo(DiscrepancySeverity.HIGH);
            assertThat(ex.getStatus()).isEqualTo(ExceptionStatus.OPEN);
            assertThat(ex.getFieldName()).isEqualTo("quantity");
            assertThat(ex.getExpectedValue()).isEqualTo("100");
            assertThat(ex.getActualValue()).isEqualTo("90");

            verify(auditService, times(1)).logEvent(
                    eq(ex), isNull(), eq("CREATED"), eq("status"), isNull(), eq("OPEN"));
        }

        @Test
        @DisplayName("Should prevent duplicate exception creation for already converted reconciliation results")
        void shouldPreventDuplicateExceptionCreation() {
            when(exceptionRepository.existsByResultId(breakResult.getId())).thenReturn(true);

            List<TradeException> created = exceptionService.createExceptionsForResults(List.of(breakResult));

            assertThat(created).isEmpty();
            verify(exceptionRepository, never()).save(any());
            verify(auditService, never()).logEvent(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should ignore MATCHED reconciliation results")
        void shouldIgnoreMatchedResults() {
            ReconciliationResult matchedResult = ReconciliationResult.builder()
                    .id(UUID.randomUUID())
                    .matchStatus(MatchStatus.MATCHED)
                    .build();

            List<TradeException> created = exceptionService.createExceptionsForResults(List.of(matchedResult));

            assertThat(created).isEmpty();
            verify(exceptionRepository, never()).existsByResultId(any());
            verify(exceptionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Lifecycle State Transitions Tests")
    class StateTransitionTests {

        private TradeException exception;

        @BeforeEach
        void initException() {
            exception = TradeException.builder()
                    .id(UUID.randomUUID())
                    .result(breakResult)
                    .run(run)
                    .exceptionType(DiscrepancyType.QUANTITY_MISMATCH)
                    .severity(DiscrepancySeverity.HIGH)
                    .status(ExceptionStatus.OPEN)
                    .version(0)
                    .build();
        }

        @Test
        @DisplayName("Valid transition: OPEN -> ACKNOWLEDGED")
        void testTransitionOpenToAcknowledged() {
            when(exceptionRepository.findWithDetailsById(exception.getId())).thenReturn(Optional.of(exception));
            when(exceptionRepository.save(any(TradeException.class))).thenAnswer(i -> i.getArgument(0));

            ExceptionUpdateRequest req = ExceptionUpdateRequest.builder()
                    .status(ExceptionStatus.ACKNOWLEDGED)
                    .version(0)
                    .build();

            ExceptionDto dto = exceptionService.updateException(exception.getId(), req, analyst);

            assertThat(dto.getStatus()).isEqualTo(ExceptionStatus.ACKNOWLEDGED);
            verify(auditService).logEvent(exception, analyst, "STATUS_CHANGED", "status", "OPEN", "ACKNOWLEDGED");
        }

        @Test
        @DisplayName("Valid transition: ACKNOWLEDGED -> IN_REVIEW")
        void testTransitionAcknowledgedToInReview() {
            exception.setStatus(ExceptionStatus.ACKNOWLEDGED);
            when(exceptionRepository.findWithDetailsById(exception.getId())).thenReturn(Optional.of(exception));
            when(exceptionRepository.save(any(TradeException.class))).thenAnswer(i -> i.getArgument(0));

            ExceptionUpdateRequest req = ExceptionUpdateRequest.builder()
                    .status(ExceptionStatus.IN_REVIEW)
                    .version(0)
                    .build();

            ExceptionDto dto = exceptionService.updateException(exception.getId(), req, analyst);

            assertThat(dto.getStatus()).isEqualTo(ExceptionStatus.IN_REVIEW);
            verify(auditService).logEvent(exception, analyst, "STATUS_CHANGED", "status", "ACKNOWLEDGED", "IN_REVIEW");
        }

        @Test
        @DisplayName("Valid transition: IN_REVIEW -> ESCALATED and ESCALATED -> IN_REVIEW")
        void testTransitionInReviewAndEscalated() {
            exception.setStatus(ExceptionStatus.IN_REVIEW);
            when(exceptionRepository.findWithDetailsById(exception.getId())).thenReturn(Optional.of(exception));
            when(exceptionRepository.save(any(TradeException.class))).thenAnswer(i -> i.getArgument(0));

            // IN_REVIEW -> ESCALATED
            ExceptionUpdateRequest req = ExceptionUpdateRequest.builder()
                    .status(ExceptionStatus.ESCALATED)
                    .version(0)
                    .build();
            ExceptionDto dto = exceptionService.updateException(exception.getId(), req, analyst);
            assertThat(dto.getStatus()).isEqualTo(ExceptionStatus.ESCALATED);

            // ESCALATED -> IN_REVIEW
            req = ExceptionUpdateRequest.builder()
                    .status(ExceptionStatus.IN_REVIEW)
                    .version(0)
                    .build();
            dto = exceptionService.updateException(exception.getId(), req, analyst);
            assertThat(dto.getStatus()).isEqualTo(ExceptionStatus.IN_REVIEW);
        }

        @Test
        @DisplayName("Valid transition: IN_REVIEW -> RESOLVED")
        void testTransitionInReviewToResolved() {
            exception.setStatus(ExceptionStatus.IN_REVIEW);
            when(exceptionRepository.findWithDetailsById(exception.getId())).thenReturn(Optional.of(exception));
            when(exceptionRepository.save(any(TradeException.class))).thenAnswer(i -> i.getArgument(0));

            ExceptionUpdateRequest req = ExceptionUpdateRequest.builder()
                    .status(ExceptionStatus.RESOLVED)
                    .resolutionNotes("Counterparty confirmed trade amendment")
                    .version(0)
                    .build();

            ExceptionDto dto = exceptionService.updateException(exception.getId(), req, analyst);

            assertThat(dto.getStatus()).isEqualTo(ExceptionStatus.RESOLVED);
            assertThat(dto.getResolutionNotes()).isEqualTo("Counterparty confirmed trade amendment");
            verify(auditService).logEvent(exception, analyst, "STATUS_CHANGED", "status", "IN_REVIEW", "RESOLVED");
        }

        @Test
        @DisplayName("Invalid transition: OPEN -> CLOSED should throw BusinessValidationException")
        void testInvalidTransitionOpenToClosed() {
            when(exceptionRepository.findWithDetailsById(exception.getId())).thenReturn(Optional.of(exception));

            ExceptionUpdateRequest req = ExceptionUpdateRequest.builder()
                    .status(ExceptionStatus.CLOSED)
                    .version(0)
                    .build();

            assertThatThrownBy(() -> exceptionService.updateException(exception.getId(), req, analyst))
                    .isInstanceOf(BusinessValidationException.class)
                    .hasMessageContaining("Invalid status transition from OPEN to CLOSED");
        }

        @Test
        @DisplayName("Resolved exceptions cannot be modified or transitioned back to OPEN")
        void testResolvedExceptionCannotBeModified() {
            exception.setStatus(ExceptionStatus.RESOLVED);
            when(exceptionRepository.findWithDetailsById(exception.getId())).thenReturn(Optional.of(exception));

            ExceptionUpdateRequest req = ExceptionUpdateRequest.builder()
                    .status(ExceptionStatus.OPEN)
                    .version(0)
                    .build();

            assertThatThrownBy(() -> exceptionService.updateException(exception.getId(), req, analyst))
                    .isInstanceOf(BusinessValidationException.class)
                    .hasMessageContaining("Cannot modify an already resolved exception");
        }
    }

    @Nested
    @DisplayName("Optimistic Locking and Assignment Tests")
    class LockingAndAssignmentTests {

        private TradeException exception;

        @BeforeEach
        void initException() {
            exception = TradeException.builder()
                    .id(UUID.randomUUID())
                    .result(breakResult)
                    .run(run)
                    .exceptionType(DiscrepancyType.QUANTITY_MISMATCH)
                    .severity(DiscrepancySeverity.HIGH)
                    .status(ExceptionStatus.OPEN)
                    .version(1) // Current version is 1
                    .build();
        }

        @Test
        @DisplayName("Mismatched version throws ConflictException (409)")
        void testOptimisticLockFailure() {
            when(exceptionRepository.findWithDetailsById(exception.getId())).thenReturn(Optional.of(exception));

            ExceptionUpdateRequest req = ExceptionUpdateRequest.builder()
                    .version(0) // Passing stale version 0
                    .status(ExceptionStatus.ACKNOWLEDGED)
                    .build();

            assertThatThrownBy(() -> exceptionService.updateException(exception.getId(), req, analyst))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Conflict: Exception has been modified by another transaction");
        }

        @Test
        @DisplayName("Assigning exception to analyst updates assignee and creates audit event")
        void testAssignToAnalyst() {
            when(exceptionRepository.findWithDetailsById(exception.getId())).thenReturn(Optional.of(exception));
            when(userRepository.findById(analyst.getId())).thenReturn(Optional.of(analyst));
            when(exceptionRepository.save(any(TradeException.class))).thenAnswer(i -> i.getArgument(0));

            ExceptionUpdateRequest req = ExceptionUpdateRequest.builder()
                    .assignedTo(analyst.getId())
                    .version(1)
                    .build();

            ExceptionDto dto = exceptionService.updateException(exception.getId(), req, analyst);

            assertThat(dto.getAssignedToId()).isEqualTo(analyst.getId());
            assertThat(dto.getAssignedToUsername()).isEqualTo("analyst1");
            verify(auditService).logEvent(exception, analyst, "ASSIGNMENT_CHANGED", "assignedTo", "UNASSIGNED", "analyst1");
        }
    }

    @Nested
    @DisplayName("Comments and Audit Tests")
    class CommentsAndAuditTests {

        @Test
        @DisplayName("Adding comment creates record and audit log")
        void testAddComment() {
            TradeException exception = TradeException.builder()
                    .id(UUID.randomUUID())
                    .status(ExceptionStatus.IN_REVIEW)
                    .build();

            when(exceptionRepository.findById(exception.getId())).thenReturn(Optional.of(exception));
            when(commentRepository.save(any(ExceptionComment.class))).thenAnswer(invocation -> {
                ExceptionComment c = invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            CommentRequest req = CommentRequest.builder().content("Checking with trading desk").build();
            var commentDto = exceptionService.addComment(exception.getId(), req, analyst);

            assertThat(commentDto.getContent()).isEqualTo("Checking with trading desk");
            assertThat(commentDto.getAuthorUsername()).isEqualTo("analyst1");
            verify(auditService).logEvent(eq(exception), eq(analyst), eq("COMMENT_ADDED"), eq("comment"), isNull(), eq("Checking with trading desk"));
        }

        @Test
        @DisplayName("Adding comment to CLOSED exception throws BusinessValidationException")
        void testCannotAddCommentToClosedException() {
            TradeException exception = TradeException.builder()
                    .id(UUID.randomUUID())
                    .status(ExceptionStatus.CLOSED)
                    .build();

            when(exceptionRepository.findById(exception.getId())).thenReturn(Optional.of(exception));

            CommentRequest req = CommentRequest.builder().content("Trying to comment").build();

            assertThatThrownBy(() -> exceptionService.addComment(exception.getId(), req, analyst))
                    .isInstanceOf(BusinessValidationException.class)
                    .hasMessageContaining("Cannot add comments to a closed exception");
        }
    }
}
