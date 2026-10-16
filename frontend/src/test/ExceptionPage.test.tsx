import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ExceptionPage } from '../pages/ExceptionPage'
import { exceptionService } from '../services/exceptionService'
import { authService } from '../services/authService'
import { ApiError } from '../services/apiClient'
import type { ExceptionDetailDto, ExceptionDto } from '../types/exception'
import type { PagedResponse } from '../types/api'

describe('ExceptionPage', () => {
  const mockException: ExceptionDto = {
    id: 'ex-100',
    resultId: 'res-100',
    runId: 'run-100',
    businessKey: 'TRADE-BREAK-01',
    exceptionType: 'QUANTITY_MISMATCH',
    severity: 'CRITICAL',
    status: 'OPEN',
    fieldName: 'quantity',
    expectedValue: '1000',
    actualValue: '900',
    differenceDetails: 'Quantity difference of 100 shares',
    version: 0,
    createdAt: '2026-10-15T14:00:00Z',
    updatedAt: '2026-10-15T14:00:00Z',
  }

  const mockPaged: PagedResponse<ExceptionDto> = {
    content: [mockException],
    pageNumber: 0,
    pageSize: 15,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false,
  }

  const mockDetail: ExceptionDetailDto = {
    exception: mockException,
    comments: [
      {
        id: 'c-1',
        authorUsername: 'analyst1',
        content: 'Contacting counterparty desk to confirm order ticket.',
        createdAt: '2026-10-15T14:05:00Z',
      },
    ],
    auditLogs: [
      {
        id: 'a-1',
        eventType: 'CREATED',
        newValue: 'OPEN',
        actorUsername: 'System',
        createdAt: '2026-10-15T14:00:00Z',
      },
    ],
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(exceptionService, 'getExceptions').mockResolvedValue(mockPaged)
    vi.spyOn(exceptionService, 'getExceptionById').mockResolvedValue(mockDetail)
    vi.spyOn(authService, 'listUsers').mockResolvedValue([
      { id: 'u-1', username: 'analyst1', email: 'analyst1@investops.com', role: 'ANALYST', active: true },
    ])
  })

  it('renders exception table and opens detail with expected vs actual values', async () => {
    render(<ExceptionPage initialExceptionId="ex-100" />)

    await waitFor(() => {
      expect(screen.getByText('TRADE-BREAK-01')).toBeInTheDocument()
      expect(screen.getByText('1000')).toBeInTheDocument() // expected
      expect(screen.getByText('900')).toBeInTheDocument() // actual
    })

    // Check investigation comments rendered
    expect(screen.getByText(/Contacting counterparty desk/i)).toBeInTheDocument()
    // Check audit logs rendered
    expect(screen.getByText('CREATED')).toBeInTheDocument()
  })

  it('validates state transitions: disables invalid transitions according to backend state machine', async () => {
    render(<ExceptionPage initialExceptionId="ex-100" />)

    await waitFor(() => {
      expect(screen.getByText('TRADE-BREAK-01')).toBeInTheDocument()
    })

    // Current state is OPEN
    // Allowed from OPEN: ACKNOWLEDGED, IN_REVIEW, ESCALATED, RESOLVED
    const ackBtn = screen.getByRole('button', { name: /ACKNOWLEDGED/i })
    expect(ackBtn).not.toBeDisabled()

    // CLOSED is not allowed directly from OPEN (only from RESOLVED)
    const closedBtn = screen.getByRole('button', { name: /CLOSED/i })
    expect(closedBtn).toBeDisabled()
  })

  it('handles optimistic locking 409 conflict and prompts user to refresh stale record', async () => {
    vi.spyOn(exceptionService, 'updateException').mockRejectedValue(
      new ApiError(409, 'Conflict: Exception has been modified by another transaction.'),
    )

    render(<ExceptionPage initialExceptionId="ex-100" />)

    await waitFor(() => {
      expect(screen.getByText('TRADE-BREAK-01')).toBeInTheDocument()
    })

    const ackBtn = screen.getByRole('button', { name: /ACKNOWLEDGED/i })
    fireEvent.click(ackBtn)

    await waitFor(() => {
      expect(screen.getByTestId('optimistic-lock-alert')).toBeInTheDocument()
      expect(screen.getByText(/Refresh Stale Record/i)).toBeInTheDocument()
    })
  })
})
