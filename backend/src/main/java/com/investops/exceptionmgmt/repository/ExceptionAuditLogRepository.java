package com.investops.exceptionmgmt.repository;

import com.investops.exceptionmgmt.entity.ExceptionAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExceptionAuditLogRepository extends JpaRepository<ExceptionAuditLog, UUID> {

    @EntityGraph(attributePaths = {"actor"})
    List<ExceptionAuditLog> findByExceptionIdOrderByCreatedAtAsc(UUID exceptionId);

    @EntityGraph(attributePaths = {"actor"})
    Page<ExceptionAuditLog> findByExceptionId(UUID exceptionId, Pageable pageable);
}
