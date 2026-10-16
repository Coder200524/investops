import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { TradePage } from '../pages/TradePage'
import { tradeService } from '../services/tradeService'
import { ApiError } from '../services/apiClient'
import type { TradeImportResponse, TradeRecordDto } from '../types/trade'
import type { PagedResponse } from '../types/api'

describe('TradePage', () => {
  const mockTrades: PagedResponse<TradeRecordDto> = {
    content: [
      {
        id: 'trade-uuid-1',
        sourceSystem: 'SYSTEM_A',
        externalTradeId: 'TX-1001',
        symbol: 'MSFT',
        side: 'BUY',
        quantity: 500,
        price: 320.5,
        tradeDate: '2026-10-15',
        settlementDate: '2026-10-17',
        account: 'ACC-ALPHA',
        createdAt: '2026-10-15T09:00:00Z',
        updatedAt: '2026-10-15T09:00:00Z',
      },
    ],
    pageNumber: 0,
    pageSize: 15,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false,
  }

  const mockImportSuccess: TradeImportResponse = {
    batchId: 'batch-uuid-1',
    filename: 'trades_system_a.csv',
    sourceSystem: 'SYSTEM_A',
    fileHash: 'sha256hash123',
    totalRows: 10,
    validRows: 8,
    skippedRows: 1,
    failedRows: 1,
    status: 'COMPLETED',
    errors: [
      {
        row: 5,
        field: 'quantity',
        message: 'Must be positive number',
        rawValue: '-10',
      },
    ],
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(tradeService, 'getTrades').mockResolvedValue(mockTrades)
  })

  it('renders trades table and trade details modal', async () => {
    render(<TradePage />)

    await waitFor(() => {
      expect(screen.getByText('TX-1001')).toBeInTheDocument()
      expect(screen.getByText('MSFT')).toBeInTheDocument()
    })

    // Click inspect button
    const inspectBtn = screen.getByRole('button', { name: /Inspect/i })
    fireEvent.click(inspectBtn)

    // Modal should show
    expect(screen.getByText(/Trade Record: TX-1001/i)).toBeInTheDocument()
    expect(screen.getAllByText('ACC-ALPHA').length).toBeGreaterThanOrEqual(1)
  })

  it('handles CSV file upload and displays summary stats and row errors', async () => {
    vi.spyOn(tradeService, 'importTrades').mockResolvedValue(mockImportSuccess)

    render(<TradePage />)

    const file = new File(['mock,csv,content'], 'trades_system_a.csv', { type: 'text/csv' })
    const fileInput = screen.getByLabelText(/Drag and drop CSV/i, { selector: 'input' })

    fireEvent.change(fileInput, { target: { files: [file] } })

    const uploadBtn = screen.getByRole('button', { name: /Upload & Process CSV/i })
    expect(uploadBtn).not.toBeDisabled()

    fireEvent.click(uploadBtn)

    await waitFor(() => {
      expect(screen.getByTestId('upload-summary')).toBeInTheDocument()
      expect(screen.getByText(/Batch Import Completed: trades_system_a.csv/i)).toBeInTheDocument()
      expect(screen.getByText('8')).toBeInTheDocument() // valid rows
      expect(screen.getByText('Must be positive number')).toBeInTheDocument() // row-level error
    })
  })

  it('handles duplicate file upload 409 conflict gracefully', async () => {
    vi.spyOn(tradeService, 'importTrades').mockRejectedValue(
      new ApiError(409, 'Duplicate batch detected: file hash match'),
    )

    render(<TradePage />)

    const file = new File(['duplicate'], 'trades_system_a.csv', { type: 'text/csv' })
    const fileInput = screen.getByLabelText(/Drag and drop CSV/i, { selector: 'input' })
    fireEvent.change(fileInput, { target: { files: [file] } })

    const uploadBtn = screen.getByRole('button', { name: /Upload & Process CSV/i })
    fireEvent.click(uploadBtn)

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/Duplicate Batch/i)
    })
  })
})
