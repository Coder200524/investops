import React, { useState, useEffect, useRef, useCallback } from 'react'
import { reconciliationService } from '../services/reconciliationService'
import type {
  MatchStatus,
  ReconciliationRequest,
  ReconciliationResultDto,
  ReconciliationRunDto,
} from '../types/reconciliation'
import { StatusBadge } from '../components/StatusBadge'
import { Pagination } from '../components/Pagination'
import { Modal } from '../components/Modal'

interface ReconciliationPageProps {
  initialRunId?: string
}

export const ReconciliationPage: React.FC<ReconciliationPageProps> = ({ initialRunId }) => {
  // Run Creation Form State
  const [tradeDateFrom, setTradeDateFrom] = useState<string>('2026-10-01')
  const [tradeDateTo, setTradeDateTo] = useState<string>('2026-10-31')
  const [sourceA, setSourceA] = useState<string>('SYSTEM_A')
  const [sourceB, setSourceB] = useState<string>('SYSTEM_B')
  const [priceTolerance, setPriceTolerance] = useState<number>(0.01)
  const [submittingRun, setSubmittingRun] = useState<boolean>(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [activePollRunId, setActivePollRunId] = useState<string | null>(null)
  const [pollingStatusMessage, setPollingStatusMessage] = useState<string | null>(null)

  // Polling ref to safely clean up on unmount
  const pollTimerRef = useRef<any>(null)

  // Runs History List State
  const [runs, setRuns] = useState<ReconciliationRunDto[]>([])
  const [runsTotal, setRunsTotal] = useState<number>(0)
  const [runsPages, setRunsPages] = useState<number>(0)
  const [runsPage, setRunsPage] = useState<number>(0)
  const [runsLoading, setRunsLoading] = useState<boolean>(false)

  // Selected Run & Results State
  const [selectedRun, setSelectedRun] = useState<ReconciliationRunDto | null>(null)
  const [results, setResults] = useState<ReconciliationResultDto[]>([])
  const [resultsTotal, setResultsTotal] = useState<number>(0)
  const [resultsPages, setResultsPages] = useState<number>(0)
  const [resultsPage, setResultsPage] = useState<number>(0)
  const [resultsLoading, setResultsLoading] = useState<boolean>(false)
  const [filterMatchStatus, setFilterMatchStatus] = useState<MatchStatus | ''>('')

  // Comparison Modal State
  const [comparisonResult, setComparisonResult] = useState<ReconciliationResultDto | null>(null)

  // Fetch runs list
  const fetchRuns = useCallback(async (page: number = 0) => {
    setRunsLoading(true)
    try {
      const paged = await reconciliationService.listRuns(page, 10)
      setRuns(paged.content || [])
      setRunsTotal(paged.totalElements || 0)
      setRunsPages(paged.totalPages || 0)
      setRunsPage(paged.pageNumber || 0)
    } catch (err: any) {
      setFormError(err.message || 'Failed to fetch reconciliation runs.')
    } finally {
      setRunsLoading(false)
    }
  }, [])

  // Fetch results for selected run
  const fetchResults = useCallback(async (runId: string, page: number = 0, status?: MatchStatus | '') => {
    setResultsLoading(true)
    try {
      const paged = await reconciliationService.getRunResults(
        runId,
        status || undefined,
        page,
        15,
      )
      setResults(paged.content || [])
      setResultsTotal(paged.totalElements || 0)
      setResultsPages(paged.totalPages || 0)
      setResultsPage(paged.pageNumber || 0)
    } catch (err: any) {
      console.error('Failed to load run results', err)
    } finally {
      setResultsLoading(false)
    }
  }, [])

  // Initial load
  useEffect(() => {
    fetchRuns(0)
  }, [fetchRuns])

  // Select initial run if provided from navigation
  useEffect(() => {
    if (initialRunId) {
      reconciliationService.getRunById(initialRunId).then((run) => {
        setSelectedRun(run)
        fetchResults(run.id, 0, '')
      }).catch(console.error)
    }
  }, [initialRunId, fetchResults])

  // Polling logic for asynchronous runs
  const startPolling = useCallback((runId: string) => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current)
    }

    setActivePollRunId(runId)
    setPollingStatusMessage(`Reconciliation initiated (Run ID: ${runId.substring(0, 8)}...). Processing trades in background...`)

    let attempts = 0
    const maxAttempts = 30 // poll for up to 60s (every 2s)

    pollTimerRef.current = setInterval(async () => {
      attempts++
      try {
        const run = await reconciliationService.getRunById(runId)
        if (run.status === 'COMPLETED' || run.status === 'FAILED') {
          if (pollTimerRef.current) clearInterval(pollTimerRef.current)
          pollTimerRef.current = null
          setActivePollRunId(null)
          setPollingStatusMessage(null)
          setSelectedRun(run)
          fetchRuns(0)
          fetchResults(run.id, 0, '')
        } else {
          setPollingStatusMessage(`Reconciliation running... compared ${run.totalCompared} trades so far.`)
        }
      } catch (err) {
        if (pollTimerRef.current) clearInterval(pollTimerRef.current)
        pollTimerRef.current = null
        setActivePollRunId(null)
        setPollingStatusMessage(null)
      }

      if (attempts >= maxAttempts) {
        if (pollTimerRef.current) clearInterval(pollTimerRef.current)
        pollTimerRef.current = null
        setActivePollRunId(null)
        setPollingStatusMessage('Polling timed out. Run may still be processing in the background.')
      }
    }, 2000)
  }, [fetchRuns, fetchResults])

  // Clean up timer on unmount
  useEffect(() => {
    return () => {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current)
      }
    }
  }, [])

  // Handle run creation
  const handleCreateRun = async (e: React.FormEvent) => {
    e.preventDefault()
    setFormError(null)
    setSubmittingRun(true)

    try {
      const request: ReconciliationRequest = {
        tradeDateFrom,
        tradeDateTo,
        sourceA,
        sourceB,
        priceTolerance: Number(priceTolerance),
      }
      const response = await reconciliationService.initiateRun(request)
      // Backend returns 202 Accepted with runId
      startPolling(response.runId)
    } catch (err: any) {
      setFormError(err.message || 'Failed to trigger reconciliation run.')
    } finally {
      setSubmittingRun(false)
    }
  }

  const handleSelectRun = (run: ReconciliationRunDto) => {
    setSelectedRun(run)
    setFilterMatchStatus('')
    fetchResults(run.id, 0, '')
  }

  return (
    <div className="page-container" data-testid="reconciliation-page">
      <div className="page-header">
        <div>
          <h1 className="page-title">Trade Reconciliation Engine</h1>
          <p className="page-subtitle">Deterministic two-pass matching across source systems with field-level tolerance</p>
        </div>
      </div>

      {/* Form: Trigger Reconciliation Run */}
      <div className="panel" style={{ marginBottom: '1.5rem' }}>
        <h2 className="panel-title">Execute New Reconciliation Run</h2>
        <form onSubmit={handleCreateRun}>
          <div className="recon-form-grid">
            <div className="form-group">
              <label htmlFor="recon-date-from">Trade Date From</label>
              <input
                id="recon-date-from"
                type="date"
                required
                className="form-control"
                value={tradeDateFrom}
                onChange={(e) => setTradeDateFrom(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label htmlFor="recon-date-to">Trade Date To</label>
              <input
                id="recon-date-to"
                type="date"
                required
                className="form-control"
                value={tradeDateTo}
                onChange={(e) => setTradeDateTo(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label htmlFor="recon-source-a">Source A</label>
              <select
                id="recon-source-a"
                className="form-control"
                value={sourceA}
                onChange={(e) => setSourceA(e.target.value)}
              >
                <option value="SYSTEM_A">SYSTEM_A (OMS)</option>
                <option value="SYSTEM_B">SYSTEM_B (Custodian)</option>
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="recon-source-b">Source B</label>
              <select
                id="recon-source-b"
                className="form-control"
                value={sourceB}
                onChange={(e) => setSourceB(e.target.value)}
              >
                <option value="SYSTEM_B">SYSTEM_B (Custodian)</option>
                <option value="SYSTEM_A">SYSTEM_A (OMS)</option>
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="recon-tolerance">Price Tolerance ($)</label>
              <input
                id="recon-tolerance"
                type="number"
                step="0.0001"
                min="0"
                className="form-control"
                value={priceTolerance}
                onChange={(e) => setPriceTolerance(parseFloat(e.target.value))}
              />
            </div>

            <div className="form-group" style={{ display: 'flex', alignItems: 'flex-end' }}>
              <button
                type="submit"
                id="btn-trigger-reconciliation"
                className="btn btn-primary"
                disabled={submittingRun || !!activePollRunId}
                style={{ width: '100%' }}
              >
                {submittingRun ? 'Submitting Run...' : 'Run Reconciliation (202)'}
              </button>
            </div>
          </div>
        </form>

        {formError && (
          <div className="alert alert-danger" role="alert" style={{ marginTop: '1rem' }}>
            {formError}
          </div>
        )}

        {/* Polling / Asynchronous Status Banner */}
        {pollingStatusMessage && (
          <div className="alert alert-info" role="status" style={{ marginTop: '1rem' }} data-testid="polling-banner">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <div className="spinner-sm" />
              <div>
                <strong>Asynchronous Execution in Progress:</strong> {pollingStatusMessage}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Selected Run Summary Card */}
      {selectedRun && (
        <div className="panel" style={{ marginBottom: '1.5rem' }} data-testid="selected-run-summary">
          <div className="panel-header">
            <div>
              <h2 className="panel-title">Run Details: {selectedRun.id}</h2>
              <span className="text-xs text-muted">
                Executed on {new Date(selectedRun.createdAt).toLocaleString()} | Sources: {selectedRun.sourceA} vs {selectedRun.sourceB}
              </span>
            </div>
            <StatusBadge status={selectedRun.status} type="run" />
          </div>

          <div className="run-metrics-grid">
            <div className="metric-box">
              <span className="metric-label">Total Compared</span>
              <span className="metric-num">{selectedRun.totalCompared}</span>
            </div>
            <div className="metric-box">
              <span className="metric-label">Matched Trades</span>
              <span className="metric-num text-success">{selectedRun.matchedCount}</span>
            </div>
            <div className="metric-box">
              <span className="metric-label">Break Count</span>
              <span className="metric-num text-danger">{selectedRun.breakCount}</span>
            </div>
            <div className="metric-box">
              <span className="metric-label">Match Rate</span>
              <span className="metric-num" style={{ color: selectedRun.matchRate >= 95 ? '#059669' : '#d97706' }}>
                {selectedRun.matchRate.toFixed(1)}%
              </span>
            </div>
            <div className="metric-box">
              <span className="metric-label">Price Tolerance</span>
              <span className="metric-num font-mono">${selectedRun.priceTolerance}</span>
            </div>
          </div>

          {/* Results Table for Selected Run */}
          <div style={{ marginTop: '1.5rem' }}>
            <div className="filter-bar" style={{ marginBottom: '1rem' }}>
              <div className="filter-item">
                <label htmlFor="filter-match-status">Match Status</label>
                <select
                  id="filter-match-status"
                  className="form-control"
                  value={filterMatchStatus}
                  onChange={(e) => {
                    const status = e.target.value as MatchStatus | ''
                    setFilterMatchStatus(status)
                    fetchResults(selectedRun.id, 0, status)
                  }}
                >
                  <option value="">All Results</option>
                  <option value="MATCHED">MATCHED</option>
                  <option value="MISMATCHED">MISMATCHED</option>
                  <option value="MISSING">MISSING</option>
                </select>
              </div>
            </div>

            <div className="table-responsive">
              <table className="data-table" data-testid="results-table">
                <thead>
                  <tr>
                    <th>Business Key</th>
                    <th>Match Status</th>
                    <th>Discrepancy Type</th>
                    <th>Severity</th>
                    <th>Source A Side</th>
                    <th>Source B Side</th>
                    <th>Details</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {resultsLoading ? (
                    <tr>
                      <td colSpan={8} className="text-center">
                        <div className="spinner" style={{ margin: '1rem auto' }} />
                        Loading reconciliation results...
                      </td>
                    </tr>
                  ) : results.length > 0 ? (
                    results.map((res) => (
                      <tr key={res.id} className="clickable-row" onClick={() => setComparisonResult(res)}>
                        <td className="font-mono text-xs font-semibold">{res.businessKey}</td>
                        <td>
                          <StatusBadge status={res.matchStatus} type="match" />
                        </td>
                        <td className="text-xs">
                          {res.discrepancyType ? res.discrepancyType.replace(/_/g, ' ') : '—'}
                        </td>
                        <td>
                          {res.severity ? <StatusBadge status={res.severity} type="severity" /> : '—'}
                        </td>
                        <td className="text-xs">
                          {res.tradeA ? `${res.tradeA.side} ${res.tradeA.quantity} @ $${res.tradeA.price}` : 'MISSING'}
                        </td>
                        <td className="text-xs">
                          {res.tradeB ? `${res.tradeB.side} ${res.tradeB.quantity} @ $${res.tradeB.price}` : 'MISSING'}
                        </td>
                        <td className="text-xs">{res.differenceDetails || 'Exact match'}</td>
                        <td>
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={(e) => {
                              e.stopPropagation()
                              setComparisonResult(res)
                            }}
                          >
                            Compare
                          </button>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={8} className="text-center empty-state-cell">
                        No results found for this run with the selected filter.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <Pagination
              currentPage={resultsPage}
              totalPages={resultsPages}
              totalElements={resultsTotal}
              pageSize={15}
              onPageChange={(page) => fetchResults(selectedRun.id, page, filterMatchStatus)}
            />
          </div>
        </div>
      )}

      {/* Runs History Table */}
      <div className="panel">
        <div className="panel-header">
          <h2 className="panel-title">Reconciliation Runs History ({runsTotal})</h2>
        </div>

        <div className="table-responsive">
          <table className="data-table" data-testid="runs-history-table">
            <thead>
              <tr>
                <th>Run ID</th>
                <th>Sources</th>
                <th>Date Range</th>
                <th>Total Compared</th>
                <th>Matched</th>
                <th>Breaks</th>
                <th>Match Rate</th>
                <th>Status</th>
                <th>Run Date</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {runsLoading ? (
                <tr>
                  <td colSpan={10} className="text-center">
                    <div className="spinner" style={{ margin: '1rem auto' }} />
                    Loading runs history...
                  </td>
                </tr>
              ) : runs.length > 0 ? (
                runs.map((run) => (
                  <tr
                    key={run.id}
                    className={`clickable-row ${selectedRun?.id === run.id ? 'row-selected' : ''}`}
                    onClick={() => handleSelectRun(run)}
                  >
                    <td className="font-mono text-xs">{run.id.substring(0, 8)}...</td>
                    <td>{run.sourceA} vs {run.sourceB}</td>
                    <td className="text-xs">{run.tradeDateFrom} &rarr; {run.tradeDateTo}</td>
                    <td className="text-right font-mono">{run.totalCompared}</td>
                    <td className="text-right font-mono text-success">{run.matchedCount}</td>
                    <td className="text-right font-mono text-danger">{run.breakCount}</td>
                    <td className="text-right font-mono font-semibold">{run.matchRate.toFixed(1)}%</td>
                    <td><StatusBadge status={run.status} type="run" /></td>
                    <td className="text-xs">{new Date(run.createdAt).toLocaleString()}</td>
                    <td>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={(e) => {
                          e.stopPropagation()
                          handleSelectRun(run)
                        }}
                      >
                        Inspect Results
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={10} className="text-center empty-state-cell">
                    No reconciliation runs found. Trigger your first run above!
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <Pagination
          currentPage={runsPage}
          totalPages={runsPages}
          totalElements={runsTotal}
          pageSize={10}
          onPageChange={(page) => fetchRuns(page)}
        />
      </div>

      {/* Side-by-Side Trade Comparison Modal */}
      {comparisonResult && (
        <Modal
          isOpen={!!comparisonResult}
          title={`Trade Comparison: ${comparisonResult.businessKey}`}
          onClose={() => setComparisonResult(null)}
          maxWidth="900px"
        >
          <div className="comparison-modal-body">
            <div className="comparison-banner">
              <div>
                <strong>Match Status: </strong>
                <StatusBadge status={comparisonResult.matchStatus} type="match" />
              </div>
              {comparisonResult.discrepancyType && (
                <div>
                  <strong>Discrepancy: </strong>
                  <span className="font-semibold text-danger">
                    {comparisonResult.discrepancyType.replace(/_/g, ' ')}
                  </span>
                </div>
              )}
              {comparisonResult.severity && (
                <div>
                  <strong>Severity: </strong>
                  <StatusBadge status={comparisonResult.severity} type="severity" />
                </div>
              )}
            </div>

            {comparisonResult.differenceDetails && (
              <div className="alert alert-warning" style={{ margin: '1rem 0' }}>
                <strong>Break Analysis:</strong> {comparisonResult.differenceDetails}
              </div>
            )}

            {/* Side-by-side comparison table */}
            <div className="side-by-side-grid">
              {/* Left Side: Trade A */}
              <div className="side-panel side-panel-a">
                <div className="side-panel-header">
                  <h3>Source A: {comparisonResult.tradeA?.sourceSystem || 'SYSTEM_A'}</h3>
                  {comparisonResult.tradeA ? (
                    <span className="badge badge-success">Present</span>
                  ) : (
                    <span className="badge badge-danger">Missing</span>
                  )}
                </div>
                {comparisonResult.tradeA ? (
                  <div className="field-list">
                    <div className="field-row">
                      <span className="field-name">External ID</span>
                      <span className="field-val font-mono">{comparisonResult.tradeA.externalTradeId}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Symbol</span>
                      <span className="field-val font-semibold">{comparisonResult.tradeA.symbol}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Side</span>
                      <span className="field-val"><StatusBadge status={comparisonResult.tradeA.side} type="side" /></span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Quantity</span>
                      <span className="field-val font-mono">{comparisonResult.tradeA.quantity}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Price</span>
                      <span className="field-val font-mono">${comparisonResult.tradeA.price}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Trade Date</span>
                      <span className="field-val">{comparisonResult.tradeA.tradeDate}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Settlement Date</span>
                      <span className="field-val">{comparisonResult.tradeA.settlementDate}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Account</span>
                      <span className="field-val">{comparisonResult.tradeA.account || '—'}</span>
                    </div>
                  </div>
                ) : (
                  <div className="empty-side-text">Record not found in Source A.</div>
                )}
              </div>

              {/* Right Side: Trade B */}
              <div className="side-panel side-panel-b">
                <div className="side-panel-header">
                  <h3>Source B: {comparisonResult.tradeB?.sourceSystem || 'SYSTEM_B'}</h3>
                  {comparisonResult.tradeB ? (
                    <span className="badge badge-success">Present</span>
                  ) : (
                    <span className="badge badge-danger">Missing</span>
                  )}
                </div>
                {comparisonResult.tradeB ? (
                  <div className="field-list">
                    <div className="field-row">
                      <span className="field-name">External ID</span>
                      <span className="field-val font-mono">{comparisonResult.tradeB.externalTradeId}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Symbol</span>
                      <span className="field-val font-semibold">{comparisonResult.tradeB.symbol}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Side</span>
                      <span className="field-val"><StatusBadge status={comparisonResult.tradeB.side} type="side" /></span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Quantity</span>
                      <span className="field-val font-mono">{comparisonResult.tradeB.quantity}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Price</span>
                      <span className="field-val font-mono">${comparisonResult.tradeB.price}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Trade Date</span>
                      <span className="field-val">{comparisonResult.tradeB.tradeDate}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Settlement Date</span>
                      <span className="field-val">{comparisonResult.tradeB.settlementDate}</span>
                    </div>
                    <div className="field-row">
                      <span className="field-name">Account</span>
                      <span className="field-val">{comparisonResult.tradeB.account || '—'}</span>
                    </div>
                  </div>
                ) : (
                  <div className="empty-side-text">Record not found in Source B.</div>
                )}
              </div>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
