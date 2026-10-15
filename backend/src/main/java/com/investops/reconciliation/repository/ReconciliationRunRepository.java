package com.investops.reconciliation.repository;

import com.investops.reconciliation.entity.ReconciliationRun;
import com.investops.reconciliation.entity.RunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

    Page<ReconciliationRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<ReconciliationRun> findByStatus(RunStatus status);
}
