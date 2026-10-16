import React, { useEffect, useState, useCallback } from 'react'
import { dashboardService } from '../services/dashboardService'
import type { DashboardSummaryDto } from '../types/dashboard'
import { DonutChart } from '../components/DonutChart'
import { BarChart } from '../components/BarChart'
import { StatusBadge } from '../components/StatusBadge'

interface DashboardPageProps {
  onNavigateToRecon: (runId?: string) => void
  onNavigateToExceptions: (exceptionId?: string) => void
}

export const DashboardPage: React.FC<DashboardPageProps> = ({
  onNavigateToRecon,
  onNavigateToExceptions,
}) => {
  const [data, setData] = useState<DashboardSummaryDto | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const summary = await dashboardService.getSummary()
      setData(summary)
    } catch (err: any) {
      setError(err.message || 'Failed to load dashboard metrics from backend.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const severityChartData = [
    { label: 'Critical', value: data?.exceptionsBySeverity?.CRITICAL || 0, color: '#e11d48' },
    { label: 'High', value: data?.exceptionsBySeverity?.HIGH || 0, color: '#ea580c' },
    { label: 'Medium', value: data?.exceptionsBySeverity?.MEDIUM || 0, color: '#d97706' },
    { label: 'Low', value: data?.exceptionsBySeverity?.LOW || 0, color: '#2563eb' },
  ]

  const statusChartData = [
    { label: 'Open', value: data?.exceptionsByStatus?.OPEN || 0, color: '#e11d48' },
    { label: 'Acknowledged', value: data?.exceptionsByStatus?.ACKNOWLEDGED || 0, color: '#d97706' },
    { label: 'In Review', value: data?.exceptionsByStatus?.IN_REVIEW || 0, color: '#0284c7' },
    { label: 'Resolved', value: data?.exceptionsByStatus?.RESOLVED || 0, color: '#059669' },
    { label: 'Closed', value: data?.exceptionsByStatus?.CLOSED || 0, color: '#64748b' },
  ]

  const typeChartData = [
    { label: 'Quantity Mismatch', value: data?.exceptionsByType?.QUANTITY_MISMATCH || 0, color: '#6366f1' },
    { label: 'Price Difference', value: data?.exceptionsByType?.PRICE_DIFFERENCE || 0, color: '#06b6d4' },
    { label: 'Missing in Source A', value: data?.exceptionsByType?.MISSING_IN_SOURCE_A || 0, color: '#f43f5e' },
    { label: 'Missing in Source B', value: data?.exceptionsByType?.MISSING_IN_SOURCE_B || 0, color: '#f97316' },
    { label: 'Settlement Date Mismatch', value: data?.exceptionsByType?.SETTLEMENT_DATE_MISMATCH || 0, color: '#8b5cf6' },
    { label: 'Duplicate Trade', value: data?.exceptionsByType?.DUPLICATE_TRADE || 0, color: '#ec4899' },
  ]

  return (
    <div className="page-container" data-testid="dashboard-page">
      <div className="page-header">
        <div>
          <h1 className="page-title">Operations Dashboard</h1>
          <p className="page-subtitle">Real-time trade reconciliation, match rates, and exception metrics</p>
        </div>
        <div className="header-actions">
          <button className="btn btn-secondary" onClick={loadData} disabled={loading}>
            {loading ? 'Refreshing...' : 'Refresh Data'}
          </button>
        </div>
      </div>

      {error && (
        <div className="alert alert-danger" role="alert">
          <strong>Backend Connection Error:</strong> {error}
        </div>
      )}

      {loading && !data && (
        <div className="loading-state">
          <div className="spinner" />
          <p>Loading real-time financial metrics from backend API...</p>
        </div>
      )}

      {data && (
        <>
          {/* Top KPI Cards */}
          <div className="kpi-grid">
            <div className="kpi-card" data-testid="kpi-total-trades">
              <span className="kpi-label">Total Trades Imported</span>
              <div className="kpi-value">{data.totalTrades.toLocaleString()}</div>
              <span className="kpi-subtext">Across SYSTEM_A & SYSTEM_B</span>
            </div>

            <div className="kpi-card" data-testid="kpi-total-runs">
              <span className="kpi-label">Reconciliation Runs</span>
              <div className="kpi-value">{data.totalReconciliationRuns.toLocaleString()}</div>
              <span className="kpi-subtext">{data.totalReconciled.toLocaleString()} total trades compared</span>
            </div>

            <div className="kpi-card" data-testid="kpi-match-rate">
              <span className="kpi-label">Platform Match Rate</span>
              <div className="kpi-value" style={{ color: data.matchRate >= 95 ? '#059669' : '#d97706' }}>
                {data.matchRate.toFixed(1)}%
              </div>
              <span className="kpi-subtext">
                {data.lastRunMatchRate !== null && data.lastRunMatchRate !== undefined
                  ? `Last Run: ${data.lastRunMatchRate.toFixed(1)}%`
                  : 'No completed runs yet'}
              </span>
            </div>

            <div className="kpi-card" data-testid="kpi-open-exceptions">
              <span className="kpi-label">Total Open Exceptions</span>
              <div className="kpi-value" style={{ color: data.openExceptions > 0 ? '#e11d48' : '#059669' }}>
                {data.openExceptions.toLocaleString()}
              </div>
              <span className="kpi-subtext">Requires analyst investigation</span>
            </div>
          </div>

          {/* Charts Row */}
          <div className="dashboard-charts-grid">
            <DonutChart
              title="Exceptions by Severity"
              data={severityChartData}
              centerLabel="Exceptions"
            />
            <BarChart title="Exception Lifecycle Distribution" data={statusChartData} />
            <BarChart title="Discrepancy Type Breakdown" data={typeChartData} />
          </div>

          {/* Two-Column Tables Row */}
          <div className="dashboard-tables-grid">
            {/* Recent Reconciliation Runs */}
            <div className="panel">
              <div className="panel-header">
                <h3 className="panel-title">Recent Reconciliation Runs</h3>
                <button
                  className="btn btn-link btn-sm"
                  onClick={() => onNavigateToRecon()}
                >
                  View All Runs &rarr;
                </button>
              </div>
              <div className="table-responsive">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Run ID</th>
                      <th>Sources</th>
                      <th>Date Range</th>
                      <th>Compared</th>
                      <th>Match Rate</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentRuns && data.recentRuns.length > 0 ? (
                      data.recentRuns.map((run) => (
                        <tr
                          key={run.id}
                          className="clickable-row"
                          onClick={() => onNavigateToRecon(run.id)}
                        >
                          <td className="font-mono text-xs">{run.id.substring(0, 8)}...</td>
                          <td>
                            {run.sourceA} vs {run.sourceB}
                          </td>
                          <td className="text-xs">
                            {run.tradeDateFrom} &rarr; {run.tradeDateTo}
                          </td>
                          <td>{run.totalCompared}</td>
                          <td>
                            <strong>{run.matchRate.toFixed(1)}%</strong>
                          </td>
                          <td>
                            <StatusBadge status={run.status} type="run" />
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={6} className="text-center empty-state-cell">
                          No reconciliation runs executed yet.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Recent High-Severity Exceptions */}
            <div className="panel">
              <div className="panel-header">
                <h3 className="panel-title">Recent High-Severity Exceptions</h3>
                <button
                  className="btn btn-link btn-sm"
                  onClick={() => onNavigateToExceptions()}
                >
                  Manage Exceptions &rarr;
                </button>
              </div>
              <div className="table-responsive">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Trade Key</th>
                      <th>Type</th>
                      <th>Severity</th>
                      <th>Status</th>
                      <th>Discrepancy</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentHighSeverityExceptions && data.recentHighSeverityExceptions.length > 0 ? (
                      data.recentHighSeverityExceptions.map((ex) => (
                        <tr
                          key={ex.id}
                          className="clickable-row"
                          onClick={() => onNavigateToExceptions(ex.id)}
                        >
                          <td className="font-mono text-xs font-semibold">
                            {ex.businessKey || ex.id.substring(0, 8)}
                          </td>
                          <td className="text-xs">
                            {ex.exceptionType.replace(/_/g, ' ')}
                          </td>
                          <td>
                            <StatusBadge status={ex.severity} type="severity" />
                          </td>
                          <td>
                            <StatusBadge status={ex.status} type="exception" />
                          </td>
                          <td className="text-xs">
                            {ex.fieldName
                              ? `${ex.fieldName}: ${ex.expectedValue || '—'} vs ${ex.actualValue || '—'}`
                              : ex.differenceDetails || 'Discrepancy'}
                          </td>
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={5} className="text-center empty-state-cell">
                          No active high-severity exceptions! Platform is healthy.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
