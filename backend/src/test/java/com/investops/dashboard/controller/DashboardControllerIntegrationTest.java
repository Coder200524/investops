package com.investops.dashboard.controller;

import com.investops.dashboard.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("DashboardController Integration Tests")
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardService dashboardService;

    @Test
    @DisplayName("GET /api/v1/dashboard/summary should return 200 OK with expected fields")
    void testGetDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTrades").isNumber())
                .andExpect(jsonPath("$.data.totalReconciliationRuns").isNumber())
                .andExpect(jsonPath("$.data.matchRate").isNumber())
                .andExpect(jsonPath("$.data.openExceptions").isNumber())
                .andExpect(jsonPath("$.data.exceptionsBySeverity").isMap())
                .andExpect(jsonPath("$.data.exceptionsByStatus").isMap())
                .andExpect(jsonPath("$.data.exceptionsByType").isMap())
                .andExpect(jsonPath("$.data.recentRuns").isArray())
                .andExpect(jsonPath("$.data.recentHighSeverityExceptions").isArray());
    }
}
