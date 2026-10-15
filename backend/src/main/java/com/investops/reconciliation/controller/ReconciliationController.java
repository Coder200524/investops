package com.investops.reconciliation.controller;

import com.investops.common.dto.ApiResponse;
import com.investops.common.dto.PagedResponse;
import com.investops.reconciliation.dto.ReconciliationRequest;
import com.investops.reconciliation.dto.ReconciliationResultDto;
import com.investops.reconciliation.dto.ReconciliationRunDto;
import com.investops.reconciliation.dto.ReconciliationRunResponse;
import com.investops.reconciliation.entity.MatchStatus;
import com.investops.reconciliation.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /**
     * POST /api/v1/reconciliation/runs
     * Initiates an asynchronous reconciliation run.
     * Returns HTTP 202 Accepted with a run identifier.
     */
    @PostMapping("/runs")
    public ResponseEntity<ApiResponse<ReconciliationRunResponse>> initiateRun(
            @Valid @RequestBody ReconciliationRequest request) {

        ReconciliationRunResponse response = reconciliationService.initiateRun(request, null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/reconciliation/runs
     * Lists past reconciliation runs with pagination.
     */
    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<PagedResponse<ReconciliationRunDto>>> listRuns(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PagedResponse<ReconciliationRunDto> runs = reconciliationService.listRuns(pageable);
        return ResponseEntity.ok(ApiResponse.ok(runs));
    }

    /**
     * GET /api/v1/reconciliation/runs/{id}
     * Retrieves run details and summary statistics by run ID.
     */
    @GetMapping("/runs/{id}")
    public ResponseEntity<ApiResponse<ReconciliationRunDto>> getRunById(@PathVariable UUID id) {
        ReconciliationRunDto run = reconciliationService.getRunById(id);
        return ResponseEntity.ok(ApiResponse.ok(run));
    }

    /**
     * GET /api/v1/reconciliation/runs/{id}/results
     * Retrieves paginated reconciliation results for a run, with optional matchStatus filtering.
     */
    @GetMapping("/runs/{id}/results")
    public ResponseEntity<ApiResponse<PagedResponse<ReconciliationResultDto>>> getRunResults(
            @PathVariable UUID id,
            @RequestParam(required = false) MatchStatus matchStatus,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PagedResponse<ReconciliationResultDto> results = reconciliationService.getRunResults(id, matchStatus, pageable);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }
}
