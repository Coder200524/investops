import type { ExceptionDto } from './exception'
import type { ReconciliationRunDto } from './reconciliation'

export interface DashboardSummaryDto {
  totalTrades: number
  totalReconciliationRuns: number
  totalReconciled: number
  matchRate: number
  openExceptions: number
  exceptionsBySeverity: Record<string, number>
  exceptionsByStatus: Record<string, number>
  exceptionsByType: Record<string, number>
  recentRuns: ReconciliationRunDto[]
  recentHighSeverityExceptions: ExceptionDto[]
  lastRunAt?: string | null
  lastRunMatchRate?: number | null
}
