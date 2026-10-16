import type { TradeRecordDto } from './trade'

export type RunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export type MatchStatus = 'MATCHED' | 'MISMATCHED' | 'MISSING'
export type DiscrepancySeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type DiscrepancyType =
  | 'QUANTITY_MISMATCH'
  | 'PRICE_DIFFERENCE'
  | 'MISSING_IN_SOURCE_A'
  | 'MISSING_IN_SOURCE_B'
  | 'SETTLEMENT_DATE_MISMATCH'
  | 'DUPLICATE_TRADE'

export interface ReconciliationRequest {
  tradeDateFrom: string
  tradeDateTo: string
  sourceA: string
  sourceB: string
  priceTolerance?: number
}

export interface ReconciliationRunResponse {
  runId: string
  status: RunStatus
  message: string
}

export interface ReconciliationRunDto {
  id: string
  runDate: string
  tradeDateFrom: string
  tradeDateTo: string
  sourceA: string
  sourceB: string
  status: RunStatus
  totalCompared: number
  matchedCount: number
  breakCount: number
  matchRate: number
  priceTolerance: number
  initiatedBy?: string
  startedAt: string
  completedAt?: string
  createdAt: string
}

export interface ReconciliationResultDto {
  id: string
  runId: string
  businessKey: string
  matchStatus: MatchStatus
  tradeA?: TradeRecordDto | null
  tradeB?: TradeRecordDto | null
  discrepancyType?: DiscrepancyType | null
  severity?: DiscrepancySeverity | null
  differenceDetails?: string | null
  createdAt: string
}
