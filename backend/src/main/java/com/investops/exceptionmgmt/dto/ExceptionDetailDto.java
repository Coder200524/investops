package com.investops.exceptionmgmt.dto;

import com.investops.exceptionmgmt.entity.TradeException;
import com.investops.reconciliation.dto.ReconciliationResultDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionDetailDto {

    private ExceptionDto exception;
    private ReconciliationResultDto result;
    private List<CommentDto> comments;
    private List<AuditLogDto> auditLogs;

    public static ExceptionDetailDto of(
            TradeException tradeException,
            List<CommentDto> comments,
            List<AuditLogDto> auditLogs) {

        return ExceptionDetailDto.builder()
                .exception(ExceptionDto.from(tradeException))
                .result(tradeException.getResult() != null
                        ? ReconciliationResultDto.from(tradeException.getResult())
                        : null)
                .comments(comments)
                .auditLogs(auditLogs)
                .build();
    }
}
