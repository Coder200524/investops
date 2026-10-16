package com.investops.exceptionmgmt.controller;

import com.investops.auth.entity.AppUser;
import com.investops.auth.repository.UserRepository;
import com.investops.common.dto.ApiResponse;
import com.investops.common.dto.PagedResponse;
import com.investops.exceptionmgmt.dto.*;
import com.investops.exceptionmgmt.entity.ExceptionStatus;
import com.investops.exceptionmgmt.service.AuditService;
import com.investops.exceptionmgmt.service.ExceptionService;
import com.investops.reconciliation.entity.DiscrepancySeverity;
import com.investops.reconciliation.entity.DiscrepancyType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exceptions")
@RequiredArgsConstructor
public class ExceptionController {

    private final ExceptionService exceptionService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    /**
     * GET /api/v1/exceptions
     * Lists exceptions with filtering and pagination.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ExceptionDto>>> listExceptions(
            @RequestParam(required = false) List<ExceptionStatus> status,
            @RequestParam(required = false) List<DiscrepancySeverity> severity,
            @RequestParam(required = false) DiscrepancyType type,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) UUID runId,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PagedResponse<ExceptionDto> exceptions = exceptionService.listExceptions(
                status, severity, type, assignedTo, runId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(exceptions));
    }

    /**
     * GET /api/v1/exceptions/{id}
     * Retrieves single exception details including comments and audit log.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExceptionDetailDto>> getExceptionById(@PathVariable UUID id) {
        ExceptionDetailDto detail = exceptionService.getExceptionById(id);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    /**
     * PATCH /api/v1/exceptions/{id}
     * Updates exception status, assignee, severity, and resolution notes.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ExceptionDto>> updateException(
            @PathVariable UUID id,
            @RequestBody ExceptionUpdateRequest request,
            Authentication authentication) {

        AppUser actor = getCurrentUser(authentication);
        ExceptionDto updated = exceptionService.updateException(id, request, actor);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    /**
     * POST /api/v1/exceptions/{id}/comments
     * Adds an investigation comment to an exception.
     */
    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<CommentDto>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody CommentRequest request,
            Authentication authentication) {

        AppUser author = getCurrentUser(authentication);
        CommentDto comment = exceptionService.addComment(id, request, author);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(comment));
    }

    /**
     * GET /api/v1/exceptions/{id}/comments
     * Retrieves all comments for an exception in chronological order.
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<List<CommentDto>>> getComments(@PathVariable UUID id) {
        List<CommentDto> comments = exceptionService.getComments(id);
        return ResponseEntity.ok(ApiResponse.ok(comments));
    }

    /**
     * GET /api/v1/exceptions/{id}/audit-log
     * Retrieves full chronological audit history for an exception.
     */
    @GetMapping("/{id}/audit-log")
    public ResponseEntity<ApiResponse<List<AuditLogDto>>> getAuditLog(@PathVariable UUID id) {
        List<AuditLogDto> auditLogs = auditService.getAuditLogs(id);
        return ResponseEntity.ok(ApiResponse.ok(auditLogs));
    }

    private AppUser getCurrentUser(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            return userRepository.findByUsername(authentication.getName()).orElse(null);
        }
        return null;
    }
}
