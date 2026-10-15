package com.investops.reconciliation.entity;

import com.investops.trade.entity.SourceSystem;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_run", indexes = {
        @Index(name = "idx_recon_run_status", columnList = "status"),
        @Index(name = "idx_recon_run_dates", columnList = "trade_date_from, trade_date_to"),
        @Index(name = "idx_recon_run_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "run_date", nullable = false, updatable = false)
    private Instant runDate;

    @Column(name = "trade_date_from", nullable = false)
    private LocalDate tradeDateFrom;

    @Column(name = "trade_date_to", nullable = false)
    private LocalDate tradeDateTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_a", nullable = false, length = 20)
    private SourceSystem sourceA;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_b", nullable = false, length = 20)
    private SourceSystem sourceB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RunStatus status = RunStatus.RUNNING;

    @Column(name = "total_compared", nullable = false)
    @Builder.Default
    private int totalCompared = 0;

    @Column(name = "matched_count", nullable = false)
    @Builder.Default
    private int matchedCount = 0;

    @Column(name = "break_count", nullable = false)
    @Builder.Default
    private int breakCount = 0;

    @Column(name = "price_tolerance", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal priceTolerance = new BigDecimal("0.01000000");

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
