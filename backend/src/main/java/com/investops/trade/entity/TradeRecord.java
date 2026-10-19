package com.investops.trade.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trade_record", uniqueConstraints = {
                @UniqueConstraint(name = "uq_trade_source_external", columnNames = { "source_system",
                                "external_trade_id" })
}, indexes = {
                @Index(name = "idx_trade_source_date", columnList = "source_system, trade_date"),
                @Index(name = "idx_trade_external_id", columnList = "external_trade_id"),
                @Index(name = "idx_trade_batch_id", columnList = "import_batch_id"),
                @Index(name = "idx_trade_symbol", columnList = "symbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeRecord {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(name = "external_trade_id", nullable = false, length = 50)
        private String externalTradeId;

        @Enumerated(EnumType.STRING)
        @Column(name = "source_system", nullable = false, length = 20)
        private SourceSystem sourceSystem;

        @Column(name = "trade_date", nullable = false)
        private LocalDate tradeDate;

        @Column(name = "settlement_date", nullable = false)
        private LocalDate settlementDate;

        @Column(nullable = false, length = 20)
        private String symbol;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 10)
        private TradeSide side;

        @Column(nullable = false, precision = 18, scale = 8)
        private BigDecimal quantity;

        @Column(nullable = false, precision = 18, scale = 8)
        private BigDecimal price;

        @Column(nullable = false, length = 100)
        private String counterparty;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        @Builder.Default
        private TradeStatus status = TradeStatus.VALIDATED;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "import_batch_id", nullable = false)
        private ImportBatch importBatch;

        @CreationTimestamp
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
}
