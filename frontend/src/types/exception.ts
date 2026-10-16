import type { DiscrepancySeverity, DiscrepancyType } from './reconciliation'

export type ExceptionStatus = 'OPEN' | 'ACKNOWLEDGED' | 'IN_REVIEW' | 'ESCALATED' | 'RESOLVED' | 'CLOSED'

export interface ExceptionDto {
  id: string
  resultId: string
  runId: string
  businessKey?: string
  exceptionType: DiscrepancyType
  severity: DiscrepancySeverity
  status: ExceptionStatus
  fieldName?: string
  expectedValue?: string
  actualValue?: string
  differenceDetails?: string
  resolutionNotes?: string
  assignedToId?: string
  assignedToUsername?: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface CommentDto {
  id: string
  authorId?: string
  authorUsername?: string
  content: string
  createdAt: string
}

export interface AuditLogDto {
  id: string
  action?: string
  eventType?: string
  fieldChanged?: string
  fieldName?: string
  oldValue?: string
  newValue?: string
  actorId?: string
  actorUsername?: string
  createdAt: string
}

export interface ExceptionDetailDto {
  exception: ExceptionDto
  comments: CommentDto[]
  auditLogs: AuditLogDto[]
}

export interface ExceptionUpdateRequest {
  status?: ExceptionStatus
  assignedTo?: string | null
  severity?: DiscrepancySeverity
  resolutionNotes?: string
  version: number
}
