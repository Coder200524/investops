package com.investops.reconciliation.entity;

import com.investops.trade.entity.TradeRecord;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_result", indexes = {
        @Index(name = "idx_recon_result_run_id", columnList = "run_id"),
        @Index(name = "idx_recon_result_match_status", columnList = "match_status"),
        @Index(name = "idx_recon_result_discrepancy", columnList = "discrepancy_type"),
        @Index(name = "idx_recon_result_trade_a", columnList = "trade_a_id"),
        @Index(name = "idx_recon_result_trade_b", columnList = "trade_b_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_a_id")
    private TradeRecord tradeA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_b_id")
    private TradeRecord tradeB;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private MatchStatus matchStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, length = 50)
    @Builder.Default
    private DiscrepancyType discrepancyType = DiscrepancyType.NONE;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DiscrepancySeverity severity;

    @Column(name = "difference_details", columnDefinition = "TEXT")
    private String differenceDetails;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
