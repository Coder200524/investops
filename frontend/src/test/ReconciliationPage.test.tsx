import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ReconciliationPage } from '../pages/ReconciliationPage'
import { reconciliationService } from '../services/reconciliationService'
import type {
  ReconciliationResultDto,
  ReconciliationRunDto,
  ReconciliationRunResponse,
} from '../types/reconciliation'
import type { PagedResponse } from '../types/api'

describe('ReconciliationPage', () => {
  const mockRun: ReconciliationRunDto = {
    id: 'run-101',
    runDate: '2026-10-15T12:00:00Z',
    tradeDateFrom: '2026-10-01',
    tradeDateTo: '2026-10-15',
    sourceA: 'SYSTEM_A',
    sourceB: 'SYSTEM_B',
    status: 'COMPLETED',
    totalCompared: 50,
    matchedCount: 45,
    breakCount: 5,
    matchRate: 90.0,
    priceTolerance: 0.01,
    startedAt: '2026-10-15T12:00:00Z',
    completedAt: '2026-10-15T12:00:02Z',
    createdAt: '2026-10-15T12:00:00Z',
  }

  const mockRunsPaged: PagedResponse<ReconciliationRunDto> = {
    content: [mockRun],
    pageNumber: 0,
    pageSize: 10,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false,
  }

  const mockResult: ReconciliationResultDto = {
    id: 'res-501',
    runId: 'run-101',
    businessKey: 'TRADE-XYZ-99',
    matchStatus: 'MISMATCHED',
    discrepancyType: 'PRICE_DIFFERENCE',
    severity: 'HIGH',
    differenceDetails: 'Price difference exceeds tolerance ($100.00 vs $105.00)',
    tradeA: {
      id: 't-1',
      sourceSystem: 'SYSTEM_A',
      externalTradeId: 'TRADE-XYZ-99',
      symbol: 'XYZ',
      side: 'BUY',
      quantity: 100,
      price: 100.0,
      tradeDate: '2026-10-10',
      settlementDate: '2026-10-12',
      createdAt: '2026-10-10T00:00:00Z',
      updatedAt: '2026-10-10T00:00:00Z',
    },
    tradeB: {
      id: 't-2',
      sourceSystem: 'SYSTEM_B',
      externalTradeId: 'TRADE-XYZ-99',
      symbol: 'XYZ',
      side: 'BUY',
      quantity: 100,
      price: 105.0,
      tradeDate: '2026-10-10',
      settlementDate: '2026-10-12',
      createdAt: '2026-10-10T00:00:00Z',
      updatedAt: '2026-10-10T00:00:00Z',
    },
    createdAt: '2026-10-15T12:00:02Z',
  }

  const mockResultsPaged: PagedResponse<ReconciliationResultDto> = {
    content: [mockResult],
    pageNumber: 0,
    pageSize: 15,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false,
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(reconciliationService, 'listRuns').mockResolvedValue(mockRunsPaged)
    vi.spyOn(reconciliationService, 'getRunResults').mockResolvedValue(mockResultsPaged)
    vi.spyOn(reconciliationService, 'getRunById').mockResolvedValue(mockRun)
  })

  it('renders runs history and displays results for selected run', async () => {
    render(<ReconciliationPage initialRunId="run-101" />)

    await waitFor(() => {
      expect(screen.getByTestId('selected-run-summary')).toBeInTheDocument()
      expect(screen.getAllByText(/90\.0%/).length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('TRADE-XYZ-99')).toBeInTheDocument()
    })

    // Click Compare button
    const compareBtn = screen.getByRole('button', { name: /Compare/i })
    fireEvent.click(compareBtn)

    // Side by side modal should be open
    expect(screen.getByText(/Trade Comparison: TRADE-XYZ-99/i)).toBeInTheDocument()
    expect(screen.getByText('Source A: SYSTEM_A')).toBeInTheDocument()
    expect(screen.getByText('Source B: SYSTEM_B')).toBeInTheDocument()
    expect(screen.getByText('$100')).toBeInTheDocument()
    expect(screen.getByText('$105')).toBeInTheDocument()
  })

  it('triggers reconciliation run and handles 202 Accepted with polling banner', async () => {
    const mockAccepted: ReconciliationRunResponse = {
      runId: 'run-new-202',
      status: 'RUNNING',
      message: 'Reconciliation initiated',
    }

    vi.spyOn(reconciliationService, 'initiateRun').mockResolvedValue(mockAccepted)

    render(<ReconciliationPage />)

    const runBtn = screen.getByRole('button', { name: /Run Reconciliation/i })
    fireEvent.click(runBtn)

    await waitFor(() => {
      expect(screen.getByTestId('polling-banner')).toBeInTheDocument()
      expect(screen.getByText(/Asynchronous Execution in Progress/i)).toBeInTheDocument()
    })
  })
})
