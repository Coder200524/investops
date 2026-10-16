package com.investops.exceptionmgmt.service;

import com.investops.auth.entity.AppUser;
import com.investops.exceptionmgmt.dto.AuditLogDto;
import com.investops.exceptionmgmt.entity.ExceptionAuditLog;
import com.investops.exceptionmgmt.entity.TradeException;
import com.investops.exceptionmgmt.repository.ExceptionAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final ExceptionAuditLogRepository auditLogRepository;

    /**
     * Appends an immutable audit log entry.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ExceptionAuditLog logEvent(
            TradeException exception,
            AppUser actor,
            String action,
            String fieldChanged,
            String oldValue,
            String newValue) {

        ExceptionAuditLog logEntry = ExceptionAuditLog.builder()
                .exception(exception)
                .actor(actor)
                .action(action)
                .fieldChanged(fieldChanged)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();

        ExceptionAuditLog saved = auditLogRepository.save(logEntry);
        log.info("Audit event logged: exceptionId={}, action={}, field={}, old={}, new={}, actor={}",
                exception.getId(), action, fieldChanged, oldValue, newValue,
                actor != null ? actor.getUsername() : "System");
        return saved;
    }

    /**
     * Retrieves chronological audit history for an exception.
     */
    @Transactional(readOnly = true)
    public List<AuditLogDto> getAuditLogs(UUID exceptionId) {
        return auditLogRepository.findByExceptionIdOrderByCreatedAtAsc(exceptionId).stream()
                .map(AuditLogDto::from)
                .collect(Collectors.toList());
    }
}
