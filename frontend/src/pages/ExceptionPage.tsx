import React, { useState, useEffect, useCallback } from 'react'
import { exceptionService } from '../services/exceptionService'
import type { ExceptionQueryParams } from '../services/exceptionService'
import { authService } from '../services/authService'
import type {
  ExceptionDetailDto,
  ExceptionDto,
  ExceptionStatus,
} from '../types/exception'
import type { DiscrepancySeverity, DiscrepancyType } from '../types/reconciliation'
import type { AppUser } from '../types/auth'
import { StatusBadge } from '../components/StatusBadge'
import { Pagination } from '../components/Pagination'
import { Modal } from '../components/Modal'
import { ApiError } from '../services/apiClient'

interface ExceptionPageProps {
  initialExceptionId?: string
}

export const ExceptionPage: React.FC<ExceptionPageProps> = ({ initialExceptionId }) => {
  // Exceptions List State
  const [exceptions, setExceptions] = useState<ExceptionDto[]>([])
  const [totalElements, setTotalElements] = useState<number>(0)
  const [totalPages, setTotalPages] = useState<number>(0)
  const [currentPage, setCurrentPage] = useState<number>(0)
  const [pageSize] = useState<number>(15)
  const [loading, setLoading] = useState<boolean>(false)
  const [listError, setListError] = useState<string | null>(null)

  // Filter States
  const [filterStatus, setFilterStatus] = useState<ExceptionStatus | ''>('')
  const [filterSeverity, setFilterSeverity] = useState<DiscrepancySeverity | ''>('')
  const [filterType, setFilterType] = useState<DiscrepancyType | ''>('')
  const [filterAssignee, setFilterAssignee] = useState<string>('')

  // Users List (Analysts for assignment)
  const [users, setUsers] = useState<AppUser[]>([])

  // Detail Modal / Panel State
  const [selectedExceptionId, setSelectedExceptionId] = useState<string | null>(null)
  const [detail, setDetail] = useState<ExceptionDetailDto | null>(null)
  const [detailLoading, setDetailLoading] = useState<boolean>(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [conflictWarning, setConflictWarning] = useState<string | null>(null)

  // Comment Input State
  const [newComment, setNewComment] = useState<string>('')
  const [submittingComment, setSubmittingComment] = useState<boolean>(false)

  // Resolution Notes State
  const [resolutionNotes, setResolutionNotes] = useState<string>('')
  const [updatingStatus, setUpdatingStatus] = useState<boolean>(false)

  // Load analysts list
  useEffect(() => {
    authService.listUsers().then(setUsers).catch(console.error)
  }, [])

  // Fetch exceptions
  const fetchExceptions = useCallback(async (page: number = 0) => {
    setLoading(true)
    setListError(null)
    try {
      const params: ExceptionQueryParams = {
        status: filterStatus ? [filterStatus] : undefined,
        severity: filterSeverity ? [filterSeverity] : undefined,
        type: filterType,
        assignedTo: filterAssignee || undefined,
        page,
        size: pageSize,
        sort: 'createdAt,desc',
      }
      const response = await exceptionService.getExceptions(params)
      setExceptions(response.content || [])
      setTotalElements(response.totalElements || 0)
      setTotalPages(response.totalPages || 0)
      setCurrentPage(response.pageNumber || 0)
    } catch (err: any) {
      setListError(err.message || 'Failed to load exceptions.')
    } finally {
      setLoading(false)
    }
  }, [filterStatus, filterSeverity, filterType, filterAssignee, pageSize])

  useEffect(() => {
    fetchExceptions(0)
  }, [fetchExceptions])

  // Fetch single exception detail
  const loadDetail = useCallback(async (id: string) => {
    setDetailLoading(true)
    setDetailError(null)
    setConflictWarning(null)
    try {
      const data = await exceptionService.getExceptionById(id)
      setDetail(data)
      setResolutionNotes(data.exception.resolutionNotes || '')
    } catch (err: any) {
      setDetailError(err.message || 'Failed to load exception detail.')
    } finally {
      setDetailLoading(false)
    }
  }, [])

  useEffect(() => {
    if (initialExceptionId) {
      setSelectedExceptionId(initialExceptionId)
      loadDetail(initialExceptionId)
    }
  }, [initialExceptionId, loadDetail])

  const openDetail = (id: string) => {
    setSelectedExceptionId(id)
    loadDetail(id)
  }

  // Handle status transition
  const handleTransitionStatus = async (targetStatus: ExceptionStatus) => {
    if (!detail) return
    setUpdatingStatus(true)
    setDetailError(null)
    setConflictWarning(null)

    try {
      await exceptionService.updateException(detail.exception.id, {
        status: targetStatus,
        resolutionNotes: targetStatus === 'RESOLVED' ? resolutionNotes : undefined,
        version: detail.exception.version,
      })
      // Reload detail and list
      await loadDetail(detail.exception.id)
      fetchExceptions(currentPage)
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setConflictWarning(
          'Optimistic Lock Conflict: Another analyst or process has updated this exception. Please refresh to load the latest state.',
        )
      } else {
        setDetailError(err.message || 'Failed to update exception status.')
      }
    } finally {
      setUpdatingStatus(false)
    }
  }

  // Handle Assignee change
  const handleAssigneeChange = async (userId: string) => {
    if (!detail) return
    setDetailError(null)
    setConflictWarning(null)
    try {
      await exceptionService.updateException(detail.exception.id, {
        assignedTo: userId || null,
        version: detail.exception.version,
      })
      await loadDetail(detail.exception.id)
      fetchExceptions(currentPage)
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setConflictWarning(
          'Optimistic Lock Conflict: Exception was modified by another transaction. Please refresh.',
        )
      } else {
        setDetailError(err.message || 'Failed to assign analyst.')
      }
    }
  }

  // Handle adding an investigation comment
  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!detail || !newComment.trim()) return
    setSubmittingComment(true)
    try {
      await exceptionService.addComment(detail.exception.id, newComment.trim())
      setNewComment('')
      await loadDetail(detail.exception.id)
    } catch (err: any) {
      setDetailError(err.message || 'Failed to add comment.')
    } finally {
      setSubmittingComment(false)
    }
  }

  // Determine valid transitions based on backend state machine
  const canTransition = (target: ExceptionStatus): boolean => {
    if (!detail) return false
    const current = detail.exception.status
    if (current === target) return false

    switch (current) {
      case 'OPEN':
        return ['ACKNOWLEDGED', 'IN_REVIEW', 'ESCALATED', 'RESOLVED'].includes(target)
      case 'ACKNOWLEDGED':
        return ['IN_REVIEW', 'ESCALATED', 'RESOLVED'].includes(target)
      case 'IN_REVIEW':
        return ['ACKNOWLEDGED', 'ESCALATED', 'RESOLVED'].includes(target)
      case 'ESCALATED':
        return ['IN_REVIEW', 'RESOLVED'].includes(target)
      case 'RESOLVED':
        return target === 'CLOSED'
      case 'CLOSED':
        return false
      default:
        return false
    }
  }

  const allStatuses: ExceptionStatus[] = [
    'OPEN',
    'ACKNOWLEDGED',
    'IN_REVIEW',
    'ESCALATED',
    'RESOLVED',
    'CLOSED',
  ]

  return (
    <div className="page-container" data-testid="exception-page">
      <div className="page-header">
        <div>
          <h1 className="page-title">Exception Management & Resolution</h1>
          <p className="page-subtitle">Triage trade breaks, investigate quantity and price discrepancies, assign to analysts, and maintain audit integrity</p>
        </div>
      </div>

      <div className="panel">
        <div className="panel-header">
          <h2 className="panel-title">Tracked Exception Records ({totalElements})</h2>
        </div>

        {/* Filters */}
        <div className="filter-bar">
          <div className="filter-item">
            <label htmlFor="filter-ex-status">Status</label>
            <select
              id="filter-ex-status"
              className="form-control"
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value as ExceptionStatus | '')}
            >
              <option value="">All Statuses</option>
              <option value="OPEN">OPEN</option>
              <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
              <option value="IN_REVIEW">IN_REVIEW</option>
              <option value="ESCALATED">ESCALATED</option>
              <option value="RESOLVED">RESOLVED</option>
              <option value="CLOSED">CLOSED</option>
            </select>
          </div>

          <div className="filter-item">
            <label htmlFor="filter-ex-severity">Severity</label>
            <select
              id="filter-ex-severity"
              className="form-control"
              value={filterSeverity}
              onChange={(e) => setFilterSeverity(e.target.value as DiscrepancySeverity | '')}
            >
              <option value="">All Severities</option>
              <option value="CRITICAL">CRITICAL</option>
              <option value="HIGH">HIGH</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="LOW">LOW</option>
            </select>
          </div>

          <div className="filter-item">
            <label htmlFor="filter-ex-type">Discrepancy Type</label>
            <select
              id="filter-ex-type"
              className="form-control"
              value={filterType}
              onChange={(e) => setFilterType(e.target.value as DiscrepancyType | '')}
            >
              <option value="">All Types</option>
              <option value="QUANTITY_MISMATCH">QUANTITY_MISMATCH</option>
              <option value="PRICE_DIFFERENCE">PRICE_DIFFERENCE</option>
              <option value="MISSING_IN_SOURCE_A">MISSING_IN_SOURCE_A</option>
              <option value="MISSING_IN_SOURCE_B">MISSING_IN_SOURCE_B</option>
              <option value="SETTLEMENT_DATE_MISMATCH">SETTLEMENT_DATE_MISMATCH</option>
              <option value="DUPLICATE_TRADE">DUPLICATE_TRADE</option>
            </select>
          </div>

          <div className="filter-item">
            <label htmlFor="filter-ex-assignee">Assignee</label>
            <select
              id="filter-ex-assignee"
              className="form-control"
              value={filterAssignee}
              onChange={(e) => setFilterAssignee(e.target.value)}
            >
              <option value="">All Assignees</option>
              {users.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.username} ({u.role})
                </option>
              ))}
            </select>
          </div>

          <div className="filter-item filter-actions">
            <button
              className="btn btn-secondary"
              onClick={() => {
                setFilterStatus('')
                setFilterSeverity('')
                setFilterType('')
                setFilterAssignee('')
              }}
            >
              Clear Filters
            </button>
          </div>
        </div>

        {listError && (
          <div className="alert alert-danger" role="alert" style={{ margin: '1rem' }}>
            {listError}
          </div>
        )}

        {/* Exception Data Table */}
        <div className="table-responsive">
          <table className="data-table" data-testid="exception-table">
            <thead>
              <tr>
                <th>Trade Key</th>
                <th>Type</th>
                <th>Severity</th>
                <th>Status</th>
                <th>Field Difference</th>
                <th>Assigned To</th>
                <th>Created At</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={8} className="text-center">
                    <div className="spinner" style={{ margin: '1rem auto' }} />
                    Loading exceptions...
                  </td>
                </tr>
              ) : exceptions.length > 0 ? (
                exceptions.map((ex) => (
                  <tr
                    key={ex.id}
                    className="clickable-row"
                    onClick={() => openDetail(ex.id)}
                  >
                    <td className="font-mono text-xs font-semibold">
                      {ex.businessKey || ex.id.substring(0, 8)}
                    </td>
                    <td className="text-xs">{ex.exceptionType.replace(/_/g, ' ')}</td>
                    <td>
                      <StatusBadge status={ex.severity} type="severity" />
                    </td>
                    <td>
                      <StatusBadge status={ex.status} type="exception" />
                    </td>
                    <td className="text-xs">
                      {ex.fieldName ? (
                        <span>
                          <code>{ex.fieldName}</code>: {ex.expectedValue || '—'} vs {ex.actualValue || '—'}
                        </span>
                      ) : (
                        ex.differenceDetails || 'Discrepancy'
                      )}
                    </td>
                    <td className="text-xs">
                      {ex.assignedToUsername ? (
                        <span className="analyst-tag">{ex.assignedToUsername}</span>
                      ) : (
                        <span className="text-muted">Unassigned</span>
                      )}
                    </td>
                    <td className="text-xs">{new Date(ex.createdAt).toLocaleString()}</td>
                    <td>
                      <button
                        className="btn btn-primary btn-sm"
                        onClick={(e) => {
                          e.stopPropagation()
                          openDetail(ex.id)
                        }}
                      >
                        Investigate
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={8} className="text-center empty-state-cell">
                    No exceptions found matching filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          totalElements={totalElements}
          pageSize={pageSize}
          onPageChange={(page) => fetchExceptions(page)}
        />
      </div>

      {/* Exception Investigation Modal */}
      {selectedExceptionId && (
        <Modal
          isOpen={!!selectedExceptionId}
          title={`Exception Investigation: ${detail?.exception?.businessKey || selectedExceptionId.substring(0, 8)}`}
          onClose={() => {
            setSelectedExceptionId(null)
            setDetail(null)
          }}
          maxWidth="950px"
        >
          {detailLoading && !detail ? (
            <div className="loading-state">
              <div className="spinner" />
              <p>Loading exception audit details...</p>
            </div>
          ) : detail ? (
            <div className="exception-detail-modal">
              {conflictWarning && (
                <div className="alert alert-danger" role="alert" data-testid="optimistic-lock-alert">
                  <strong>Conflict Detected:</strong> {conflictWarning}
                  <button
                    className="btn btn-secondary btn-sm"
                    style={{ marginLeft: '1rem' }}
                    onClick={() => loadDetail(detail.exception.id)}
                  >
                    Refresh Stale Record
                  </button>
                </div>
              )}

              {detailError && (
                <div className="alert alert-danger" role="alert">
                  {detailError}
                </div>
              )}

              {/* Status & Severity Header Bar */}
              <div className="detail-status-banner">
                <div className="detail-status-item">
                  <span className="text-xs text-muted">CURRENT STATUS</span>
                  <div>
                    <StatusBadge status={detail.exception.status} type="exception" />
                  </div>
                </div>

                <div className="detail-status-item">
                  <span className="text-xs text-muted">SEVERITY</span>
                  <div>
                    <StatusBadge status={detail.exception.severity} type="severity" />
                  </div>
                </div>

                <div className="detail-status-item">
                  <span className="text-xs text-muted">ASSIGNED ANALYST</span>
                  <div>
                    <select
                      className="form-control form-control-sm"
                      value={detail.exception.assignedToId || ''}
                      onChange={(e) => handleAssigneeChange(e.target.value)}
                    >
                      <option value="">(Unassigned)</option>
                      {users.map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.username} ({u.role})
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="detail-status-item">
                  <span className="text-xs text-muted">RECORD VERSION</span>
                  <span className="font-mono text-xs">v{detail.exception.version}</span>
                </div>
              </div>

              {/* Discrepancy Breakdown: Expected vs Actual */}
              <div className="panel" style={{ margin: '1rem 0' }}>
                <h4 className="panel-title text-sm">Discrepancy Breakdown</h4>
                <div className="detail-grid">
                  <div className="detail-item">
                    <span className="detail-label">Exception Type</span>
                    <span className="detail-value font-semibold">
                      {detail.exception.exceptionType.replace(/_/g, ' ')}
                    </span>
                  </div>
                  <div className="detail-item">
                    <span className="detail-label">Mismatched Field</span>
                    <span className="detail-value font-mono font-semibold">
                      {detail.exception.fieldName || 'N/A'}
                    </span>
                  </div>
                  <div className="detail-item">
                    <span className="detail-label">Expected Value (Source A)</span>
                    <span className="detail-value font-mono text-success">
                      {detail.exception.expectedValue || '—'}
                    </span>
                  </div>
                  <div className="detail-item">
                    <span className="detail-label">Actual Value (Source B)</span>
                    <span className="detail-value font-mono text-danger">
                      {detail.exception.actualValue || '—'}
                    </span>
                  </div>
                </div>
                {detail.exception.differenceDetails && (
                  <div className="break-note" style={{ marginTop: '0.75rem' }}>
                    <strong>Break Summary: </strong> {detail.exception.differenceDetails}
                  </div>
                )}
              </div>

              {/* State Machine Transition Controls */}
              <div className="panel" style={{ margin: '1rem 0' }}>
                <h4 className="panel-title text-sm">Lifecycle State Machine Transitions</h4>
                <p className="text-xs text-muted">
                  Allowed transitions follow the InvestOps backend state machine. Invalid transitions are disabled.
                </p>

                {detail.exception.status === 'RESOLVED' && (
                  <div className="alert alert-info" style={{ margin: '0.5rem 0' }}>
                    Exception is marked <strong>RESOLVED</strong>. Resolution notes are recorded below. Ready for closure.
                  </div>
                )}

                {detail.exception.status === 'CLOSED' && (
                  <div className="alert alert-secondary" style={{ margin: '0.5rem 0' }}>
                    This exception is <strong>CLOSED</strong> (terminal state). No further modifications allowed.
                  </div>
                )}

                {/* If IN_REVIEW or transitioning to RESOLVED, allow entering resolution notes */}
                {detail.exception.status !== 'RESOLVED' && detail.exception.status !== 'CLOSED' && (
                  <div className="form-group" style={{ margin: '0.75rem 0' }}>
                    <label htmlFor="resolution-notes-input">Resolution Notes (required when resolving):</label>
                    <textarea
                      id="resolution-notes-input"
                      className="form-control"
                      rows={2}
                      placeholder="Explain root cause, adjustment reason, or trade settlement confirmation..."
                      value={resolutionNotes}
                      onChange={(e) => setResolutionNotes(e.target.value)}
                    />
                  </div>
                )}

                <div className="transition-buttons-row">
                  {allStatuses.map((st) => {
                    const isCurrent = detail.exception.status === st
                    const allowed = canTransition(st)
                    return (
                      <button
                        key={st}
                        id={`btn-transition-${st.toLowerCase()}`}
                        className={`btn btn-sm ${isCurrent ? 'btn-primary' : 'btn-secondary'}`}
                        disabled={isCurrent || !allowed || updatingStatus}
                        onClick={() => handleTransitionStatus(st)}
                        title={
                          isCurrent
                            ? 'Current State'
                            : allowed
                            ? `Transition to ${st}`
                            : `Transition to ${st} not permitted from ${detail.exception.status}`
                        }
                      >
                        {isCurrent ? `● ${st}` : `&rarr; ${st}`}
                      </button>
                    )
                  })}
                </div>
              </div>

              {/* Two Column Section: Comments & Audit Log */}
              <div className="exception-timeline-grid">
                {/* Comments Column */}
                <div className="panel">
                  <h4 className="panel-title text-sm">Investigation Comments ({detail.comments.length})</h4>
                  <div className="comments-stream">
                    {detail.comments && detail.comments.length > 0 ? (
                      detail.comments.map((cmt) => (
                        <div key={cmt.id} className="comment-bubble">
                          <div className="comment-header">
                            <span className="font-semibold text-xs">{cmt.authorUsername || 'Analyst'}</span>
                            <span className="text-xs text-muted">
                              {new Date(cmt.createdAt).toLocaleString()}
                            </span>
                          </div>
                          <div className="comment-text">{cmt.content}</div>
                        </div>
                      ))
                    ) : (
                      <div className="text-muted text-xs" style={{ padding: '0.5rem 0' }}>
                        No comments recorded yet. Add initial investigation notes below.
                      </div>
                    )}
                  </div>

                  <form onSubmit={handleAddComment} style={{ marginTop: '0.75rem' }}>
                    <div className="form-group">
                      <textarea
                        className="form-control"
                        rows={2}
                        placeholder="Add an investigation comment..."
                        value={newComment}
                        onChange={(e) => setNewComment(e.target.value)}
                        disabled={detail.exception.status === 'CLOSED'}
                      />
                    </div>
                    <button
                      type="submit"
                      className="btn btn-secondary btn-sm"
                      disabled={!newComment.trim() || submittingComment || detail.exception.status === 'CLOSED'}
                    >
                      {submittingComment ? 'Posting...' : 'Post Comment'}
                    </button>
                  </form>
                </div>

                {/* Audit Log Column */}
                <div className="panel">
                  <h4 className="panel-title text-sm">Chronological Audit History ({detail.auditLogs.length})</h4>
                  <div className="audit-log-stream">
                    {detail.auditLogs && detail.auditLogs.length > 0 ? (
                      <table className="data-table data-table-sm">
                        <thead>
                          <tr>
                            <th>Time</th>
                            <th>Event</th>
                            <th>Change</th>
                            <th>Actor</th>
                          </tr>
                        </thead>
                        <tbody>
                          {detail.auditLogs.map((log) => (
                            <tr key={log.id}>
                              <td className="text-xs font-mono">
                                {new Date(log.createdAt).toLocaleTimeString()}
                              </td>
                              <td className="text-xs font-semibold">{log.action || log.eventType}</td>
                              <td className="text-xs">
                                {log.oldValue && log.newValue ? (
                                  <span>
                                    <del className="text-muted">{log.oldValue}</del> &rarr; <strong>{log.newValue}</strong>
                                  </span>
                                ) : (
                                  log.newValue || log.fieldChanged || log.fieldName || '—'
                                )}
                              </td>
                              <td className="text-xs">{log.actorUsername || 'System'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    ) : (
                      <div className="text-muted text-xs" style={{ padding: '0.5rem 0' }}>
                        No audit events logged yet.
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          ) : null}
        </Modal>
      )}
    </div>
  )
}
