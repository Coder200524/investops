package com.investops.trade.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_batch", indexes = {
        @Index(name = "idx_batch_file_hash", columnList = "file_hash"),
        @Index(name = "idx_batch_status", columnList = "status"),
        @Index(name = "idx_batch_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false, length = 20)
    private SourceSystem sourceSystem;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private int totalRows = 0;

    @Column(name = "valid_rows", nullable = false)
    @Builder.Default
    private int validRows = 0;

    @Column(name = "skipped_rows", nullable = false)
    @Builder.Default
    private int skippedRows = 0;

    @Column(name = "failed_rows", nullable = false)
    @Builder.Default
    private int failedRows = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BatchStatus status = BatchStatus.PROCESSING;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Column(name = "file_hash", nullable = false, unique = true, length = 64)
    private String fileHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
