package com.investops.dashboard.controller;

import com.investops.common.dto.ApiResponse;
import com.investops.dashboard.dto.DashboardSummaryDto;
import com.investops.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryDto>> getDashboardSummary() {
        DashboardSummaryDto summary = dashboardService.getDashboardSummary();
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
