import type { DashboardSummaryDto } from '../types/dashboard'
import { apiClient } from './apiClient'

export const dashboardService = {
  async getSummary(): Promise<DashboardSummaryDto> {
    return apiClient.get<DashboardSummaryDto>('/api/v1/dashboard/summary')
  },
}
