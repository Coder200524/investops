package com.investops.exceptionmgmt.entity;

import com.investops.auth.entity.AppUser;
import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import com.investops.reconciliation.entity.ReconciliationResult;
import com.investops.reconciliation.entity.ReconciliationRun;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_exception", indexes = {
        @Index(name = "idx_exception_status_severity", columnList = "status, severity"),
        @Index(name = "idx_exception_run", columnList = "run_id"),
        @Index(name = "idx_exception_result", columnList = "result_id"),
        @Index(name = "idx_exception_assigned_to", columnList = "assigned_to"),
        @Index(name = "idx_exception_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeException {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false, unique = true)
    private ReconciliationResult result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 50)
    private DiscrepancyType exceptionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscrepancySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExceptionStatus status = ExceptionStatus.OPEN;

    @Column(name = "field_name", length = 50)
    private String fieldName;

    @Column(name = "expected_value")
    private String expectedValue;

    @Column(name = "actual_value")
    private String actualValue;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private AppUser assignedTo;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private int version = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
