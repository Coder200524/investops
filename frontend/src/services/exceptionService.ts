import type { PagedResponse } from '../types/api'
import type {
  AuditLogDto,
  CommentDto,
  ExceptionDetailDto,
  ExceptionDto,
  ExceptionStatus,
  ExceptionUpdateRequest,
} from '../types/exception'
import type { DiscrepancySeverity, DiscrepancyType } from '../types/reconciliation'
import { apiClient } from './apiClient'

export interface ExceptionQueryParams {
  status?: ExceptionStatus[]
  severity?: DiscrepancySeverity[]
  type?: DiscrepancyType | ''
  assignedTo?: string
  runId?: string
  page?: number
  size?: number
  sort?: string
}

export const exceptionService = {
  async getExceptions(params: ExceptionQueryParams = {}): Promise<PagedResponse<ExceptionDto>> {
    const query = new URLSearchParams()
    if (params.status && params.status.length > 0) {
      query.append('status', params.status.join(','))
    }
    if (params.severity && params.severity.length > 0) {
      query.append('severity', params.severity.join(','))
    }
    if (params.type) {
      query.append('type', params.type)
    }
    if (params.assignedTo) {
      query.append('assignedTo', params.assignedTo)
    }
    if (params.runId) {
      query.append('runId', params.runId)
    }
    if (params.page !== undefined) query.append('page', params.page.toString())
    if (params.size !== undefined) query.append('size', params.size.toString())
    if (params.sort) query.append('sort', params.sort)

    const queryString = query.toString() ? `?${query.toString()}` : ''
    return apiClient.get<PagedResponse<ExceptionDto>>(`/api/v1/exceptions${queryString}`)
  },

  async getExceptionById(id: string): Promise<ExceptionDetailDto> {
    return apiClient.get<ExceptionDetailDto>(`/api/v1/exceptions/${id}`)
  },

  async updateException(id: string, request: ExceptionUpdateRequest): Promise<ExceptionDto> {
    return apiClient.patch<ExceptionDto>(`/api/v1/exceptions/${id}`, request)
  },

  async addComment(id: string, content: string): Promise<CommentDto> {
    return apiClient.post<CommentDto>(`/api/v1/exceptions/${id}/comments`, { content })
  },

  async getComments(id: string): Promise<CommentDto[]> {
    return apiClient.get<CommentDto[]>(`/api/v1/exceptions/${id}/comments`)
  },

  async getAuditLogs(id: string): Promise<AuditLogDto[]> {
    return apiClient.get<AuditLogDto[]>(`/api/v1/exceptions/${id}/audit-log`)
  },
}
