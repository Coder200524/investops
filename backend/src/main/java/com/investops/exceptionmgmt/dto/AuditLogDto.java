package com.investops.exceptionmgmt.dto;

import com.investops.exceptionmgmt.entity.ExceptionAuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {

    private UUID id;
    private UUID exceptionId;
    private UUID actorId;
    private String actorUsername;
    private String action;
    private String fieldChanged;
    private String oldValue;
    private String newValue;
    private Instant createdAt;

    public static AuditLogDto from(ExceptionAuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .exceptionId(log.getException().getId())
                .actorId(log.getActor() != null ? log.getActor().getId() : null)
                .actorUsername(log.getActor() != null ? log.getActor().getUsername() : "System")
                .action(log.getAction())
                .fieldChanged(log.getFieldChanged())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
