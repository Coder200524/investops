import React from 'react'
import type { DiscrepancySeverity, MatchStatus, RunStatus } from '../types/reconciliation'
import type { ExceptionStatus } from '../types/exception'

interface StatusBadgeProps {
  status?: MatchStatus | RunStatus | ExceptionStatus | DiscrepancySeverity | string
  type?: 'match' | 'run' | 'exception' | 'severity' | 'side'
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  if (!status) return null

  const getBadgeClass = (): string => {
    switch (status) {
      // Match Status
      case 'MATCHED':
        return 'badge-success'
      case 'MISMATCHED':
        return 'badge-warning'
      case 'MISSING':
        return 'badge-danger'

      // Run Status
      case 'COMPLETED':
        return 'badge-success'
      case 'RUNNING':
        return 'badge-info badge-pulse'
      case 'PENDING':
        return 'badge-secondary'
      case 'FAILED':
        return 'badge-danger'

      // Exception Status
      case 'OPEN':
        return 'badge-danger'
      case 'ACKNOWLEDGED':
        return 'badge-warning'
      case 'IN_REVIEW':
        return 'badge-info'
      case 'RESOLVED':
        return 'badge-success'
      case 'CLOSED':
        return 'badge-secondary'

      // Severity
      case 'CRITICAL':
        return 'badge-critical'
      case 'HIGH':
        return 'badge-danger'
      case 'MEDIUM':
        return 'badge-warning'
      case 'LOW':
        return 'badge-info'

      // Trade Side
      case 'BUY':
        return 'badge-buy'
      case 'SELL':
        return 'badge-sell'

      default:
        return 'badge-secondary'
    }
  }

  return <span className={`badge ${getBadgeClass()}`}>{status.replace(/_/g, ' ')}</span>
}
