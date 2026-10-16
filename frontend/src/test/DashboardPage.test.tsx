import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { DashboardPage } from '../pages/DashboardPage'
import { dashboardService } from '../services/dashboardService'
import type { DashboardSummaryDto } from '../types/dashboard'

describe('DashboardPage', () => {
  const mockSummary: DashboardSummaryDto = {
    totalTrades: 1250,
    totalReconciliationRuns: 8,
    totalReconciled: 1200,
    matchRate: 96.5,
    openExceptions: 14,
    exceptionsBySeverity: {
      CRITICAL: 2,
      HIGH: 5,
      MEDIUM: 4,
      LOW: 3,
    },
    exceptionsByStatus: {
      OPEN: 6,
      ACKNOWLEDGED: 4,
      IN_REVIEW: 4,
      RESOLVED: 20,
      CLOSED: 15,
    },
    exceptionsByType: {
      QUANTITY_MISMATCH: 5,
      PRICE_DIFFERENCE: 4,
      MISSING_IN_SOURCE_A: 3,
      MISSING_IN_SOURCE_B: 2,
    },
    recentRuns: [
      {
        id: 'run-uuid-12345678',
        runDate: '2026-10-15T10:00:00Z',
        tradeDateFrom: '2026-10-01',
        tradeDateTo: '2026-10-15',
        sourceA: 'SYSTEM_A',
        sourceB: 'SYSTEM_B',
        status: 'COMPLETED',
        totalCompared: 100,
        matchedCount: 96,
        breakCount: 4,
        matchRate: 96.0,
        priceTolerance: 0.01,
        startedAt: '2026-10-15T10:00:00Z',
        completedAt: '2026-10-15T10:00:05Z',
        createdAt: '2026-10-15T10:00:00Z',
      },
    ],
    recentHighSeverityExceptions: [
      {
        id: 'ex-uuid-1',
        resultId: 'res-uuid-1',
        runId: 'run-uuid-1',
        businessKey: 'TRADE-AAPL-001',
        exceptionType: 'PRICE_DIFFERENCE',
        severity: 'HIGH',
        status: 'OPEN',
        fieldName: 'price',
        expectedValue: '150.00',
        actualValue: '152.50',
        version: 1,
        createdAt: '2026-10-15T10:05:00Z',
        updatedAt: '2026-10-15T10:05:00Z',
      },
    ],
    lastRunAt: '2026-10-15T10:00:00Z',
    lastRunMatchRate: 96.0,
  }

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('renders dashboard KPI cards with metrics from actual API response', async () => {
    vi.spyOn(dashboardService, 'getSummary').mockResolvedValue(mockSummary)

    render(
      <DashboardPage
        onNavigateToRecon={vi.fn()}
        onNavigateToExceptions={vi.fn()}
      />,
    )

    // Check loading indicator first
    expect(screen.getByText(/Loading real-time financial metrics/i)).toBeInTheDocument()

    // Await data loaded
    await waitFor(() => {
      expect(screen.getByTestId('kpi-total-trades')).toHaveTextContent('1,250')
      expect(screen.getByTestId('kpi-total-runs')).toHaveTextContent('8')
      expect(screen.getByTestId('kpi-match-rate')).toHaveTextContent('96.5%')
      expect(screen.getByTestId('kpi-open-exceptions')).toHaveTextContent('14')
    })

    // Check recent run row rendered
    expect(screen.getByText('TRADE-AAPL-001')).toBeInTheDocument()
    expect(screen.getByText('SYSTEM_A vs SYSTEM_B')).toBeInTheDocument()
  })

  it('renders error state when backend API call fails', async () => {
    vi.spyOn(dashboardService, 'getSummary').mockRejectedValue(new Error('Network error connecting to backend'))

    render(
      <DashboardPage
        onNavigateToRecon={vi.fn()}
        onNavigateToExceptions={vi.fn()}
      />,
    )

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/Network error connecting to backend/i)
    })
  })
})
