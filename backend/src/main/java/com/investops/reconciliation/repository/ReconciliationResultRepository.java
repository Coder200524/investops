package com.investops.reconciliation.repository;

import com.investops.reconciliation.entity.MatchStatus;
import com.investops.reconciliation.entity.ReconciliationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, UUID>, JpaSpecificationExecutor<ReconciliationResult> {

    @EntityGraph(attributePaths = {"tradeA", "tradeB"})
    Page<ReconciliationResult> findByRunId(UUID runId, Pageable pageable);

    @EntityGraph(attributePaths = {"tradeA", "tradeB"})
    Page<ReconciliationResult> findByRunIdAndMatchStatus(UUID runId, MatchStatus matchStatus, Pageable pageable);

    @EntityGraph(attributePaths = {"tradeA", "tradeB"})
    List<ReconciliationResult> findByRunId(UUID runId);

    long countByRunIdAndMatchStatus(UUID runId, MatchStatus matchStatus);
}
