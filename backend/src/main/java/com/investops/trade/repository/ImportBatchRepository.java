package com.investops.trade.repository;

import com.investops.trade.entity.BatchStatus;
import com.investops.trade.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
    Optional<ImportBatch> findByFileHash(String fileHash);
    boolean existsByFileHashAndStatus(String fileHash, BatchStatus status);
}
