package com.investops.exceptionmgmt.repository;

import com.investops.exceptionmgmt.entity.ExceptionStatus;
import com.investops.exceptionmgmt.entity.TradeException;
import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExceptionRepository extends JpaRepository<TradeException, UUID>, JpaSpecificationExecutor<TradeException> {

    boolean existsByResultId(UUID resultId);

    Optional<TradeException> findByResultId(UUID resultId);

    List<TradeException> findByRunId(UUID runId);

    @EntityGraph(attributePaths = {"result", "result.tradeA", "result.tradeB", "run", "assignedTo"})
    Optional<TradeException> findWithDetailsById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"result", "result.tradeA", "result.tradeB", "run", "assignedTo"})
    Page<TradeException> findAll(Specification<TradeException> spec, Pageable pageable);

    long countByStatus(ExceptionStatus status);

    long countBySeverity(DiscrepancySeverity severity);

    long countByExceptionType(DiscrepancyType exceptionType);

    @EntityGraph(attributePaths = {"result", "result.tradeA", "result.tradeB", "run", "assignedTo"})
    List<TradeException> findTop10BySeverityInOrderByCreatedAtDesc(List<DiscrepancySeverity> severities);
}
