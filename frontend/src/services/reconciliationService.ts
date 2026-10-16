import type { PagedResponse } from '../types/api'
import type {
  MatchStatus,
  ReconciliationRequest,
  ReconciliationResultDto,
  ReconciliationRunDto,
  ReconciliationRunResponse,
} from '../types/reconciliation'
import { apiClient } from './apiClient'

export const reconciliationService = {
  async initiateRun(request: ReconciliationRequest): Promise<ReconciliationRunResponse> {
    return apiClient.post<ReconciliationRunResponse>('/api/v1/reconciliation/runs', request)
  },

  async listRuns(page: number = 0, size: number = 20): Promise<PagedResponse<ReconciliationRunDto>> {
    return apiClient.get<PagedResponse<ReconciliationRunDto>>(
      `/api/v1/reconciliation/runs?page=${page}&size=${size}&sort=createdAt,desc`,
    )
  },

  async getRunById(id: string): Promise<ReconciliationRunDto> {
    return apiClient.get<ReconciliationRunDto>(`/api/v1/reconciliation/runs/${id}`)
  },

  async getRunResults(
    id: string,
    matchStatus?: MatchStatus,
    page: number = 0,
    size: number = 25,
  ): Promise<PagedResponse<ReconciliationResultDto>> {
    let url = `/api/v1/reconciliation/runs/${id}/results?page=${page}&size=${size}&sort=createdAt,desc`
    if (matchStatus) {
      url += `&matchStatus=${matchStatus}`
    }
    return apiClient.get<PagedResponse<ReconciliationResultDto>>(url)
  },
}
