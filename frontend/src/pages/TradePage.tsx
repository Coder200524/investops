import React, { useState, useEffect, useCallback } from 'react'
import { tradeService } from '../services/tradeService'
import type { TradeQueryParams } from '../services/tradeService'
import type { TradeRecordDto, SourceSystem, TradeSide, TradeImportResponse } from '../types/trade'
import { StatusBadge } from '../components/StatusBadge'
import { Pagination } from '../components/Pagination'
import { Modal } from '../components/Modal'
import { ApiError } from '../services/apiClient'

export const TradePage: React.FC = () => {
  // Trade List State
  const [trades, setTrades] = useState<TradeRecordDto[]>([])
  const [totalElements, setTotalElements] = useState<number>(0)
  const [totalPages, setTotalPages] = useState<number>(0)
  const [currentPage, setCurrentPage] = useState<number>(0)
  const [pageSize] = useState<number>(15)
  const [loading, setLoading] = useState<boolean>(false)
  const [listError, setListError] = useState<string | null>(null)

  // Filters State
  const [filterSource, setFilterSource] = useState<SourceSystem | ''>('')
  const [filterDateFrom, setFilterDateFrom] = useState<string>('')
  const [filterDateTo, setFilterDateTo] = useState<string>('')
  const [filterSymbol, setFilterSymbol] = useState<string>('')
  const [filterSide, setFilterSide] = useState<TradeSide | ''>('')

  // Upload State
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [uploadSource, setUploadSource] = useState<SourceSystem>('SYSTEM_A')
  const [uploading, setUploading] = useState<boolean>(false)
  const [uploadResult, setUploadResult] = useState<TradeImportResponse | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [isDragOver, setIsDragOver] = useState<boolean>(false)

  // Detail Modal State
  const [selectedTrade, setSelectedTrade] = useState<TradeRecordDto | null>(null)

  const fetchTrades = useCallback(async (page: number = 0) => {
    setLoading(true)
    setListError(null)
    try {
      const params: TradeQueryParams = {
        sourceSystem: filterSource,
        tradeDateFrom: filterDateFrom || undefined,
        tradeDateTo: filterDateTo || undefined,
        symbol: filterSymbol || undefined,
        side: filterSide,
        page,
        size: pageSize,
        sort: 'tradeDate,desc',
      }
      const response = await tradeService.getTrades(params)
      setTrades(response.content || [])
      setTotalElements(response.totalElements || 0)
      setTotalPages(response.totalPages || 0)
      setCurrentPage(response.pageNumber || 0)
    } catch (err: any) {
      setListError(err.message || 'Failed to load trades from backend.')
    } finally {
      setLoading(false)
    }
  }, [filterSource, filterDateFrom, filterDateTo, filterSymbol, filterSide, pageSize])

  useEffect(() => {
    fetchTrades(0)
  }, [fetchTrades])

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!uploadFile) {
      setUploadError('Please select a CSV file to upload.')
      return
    }

    setUploading(true)
    setUploadError(null)
    setUploadResult(null)

    try {
      const result = await tradeService.importTrades(uploadFile, uploadSource)
      setUploadResult(result)
      setUploadFile(null)
      fetchTrades(0)
    } catch (err: any) {
      if (err instanceof ApiError && err.status === 409) {
        setUploadError(
          err.problemDetail?.detail ||
            'Duplicate Batch: This identical CSV file has already been imported for this source system (file hash match).',
        )
      } else {
        setUploadError(err.message || 'Failed to import CSV file.')
      }
    } finally {
      setUploading(false)
    }
  }

  const handleFileDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(false)
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0]
      if (file.name.endsWith('.csv')) {
        setUploadFile(file)
        setUploadError(null)
      } else {
        setUploadError('Only CSV files are supported (.csv).')
      }
    }
  }

  const resetFilters = () => {
    setFilterSource('')
    setFilterDateFrom('')
    setFilterDateTo('')
    setFilterSymbol('')
    setFilterSide('')
  }

  return (
    <div className="page-container" data-testid="trade-page">
      <div className="page-header">
        <div>
          <h1 className="page-title">Trade Management & Ingestion</h1>
          <p className="page-subtitle">Upload source-system CSV files, inspect batches, and review imported trade records</p>
        </div>
      </div>

      {/* Upload Section */}
      <div className="panel" style={{ marginBottom: '1.5rem' }}>
        <h2 className="panel-title">Ingest Simulated Trade File (CSV)</h2>
        <form onSubmit={handleUpload} className="upload-form">
          <div className="upload-grid">
            <div className="form-group">
              <label htmlFor="upload-source-system">Source System</label>
              <select
                id="upload-source-system"
                className="form-control"
                value={uploadSource}
                onChange={(e) => setUploadSource(e.target.value as SourceSystem)}
              >
                <option value="SYSTEM_A">SYSTEM_A (Order Management System)</option>
                <option value="SYSTEM_B">SYSTEM_B (Custodian / Settlement Ledger)</option>
              </select>
            </div>

            <div
              className={`dropzone ${isDragOver ? 'drag-over' : ''} ${uploadFile ? 'has-file' : ''}`}
              onDragOver={(e) => {
                e.preventDefault()
                setIsDragOver(true)
              }}
              onDragLeave={() => setIsDragOver(false)}
              onDrop={handleFileDrop}
            >
              <input
                id="trade-file-input"
                type="file"
                accept=".csv"
                style={{ display: 'none' }}
                onChange={(e) => {
                  if (e.target.files && e.target.files[0]) {
                    setUploadFile(e.target.files[0])
                    setUploadError(null)
                  }
                }}
              />
              <label htmlFor="trade-file-input" className="dropzone-label">
                {uploadFile ? (
                  <div className="selected-file-info">
                    <strong>{uploadFile.name}</strong> ({(uploadFile.size / 1024).toFixed(1)} KB)
                    <span className="change-file-text">Click to choose a different file</span>
                  </div>
                ) : (
                  <div>
                    <span>Drag and drop CSV file here, or <strong>browse files</strong></span>
                    <span className="dropzone-hint">Required headers: trade_id, trade_date, settlement_date, symbol, side, quantity, price, counterparty, status</span>
                  </div>
                )}
              </label>
            </div>

            <div className="upload-actions">
              <button
                type="submit"
                id="btn-upload-trade-csv"
                className="btn btn-primary"
                disabled={!uploadFile || uploading}
              >
                {uploading ? 'Importing Batch...' : 'Upload & Process CSV'}
              </button>
            </div>
          </div>
        </form>

        {uploadError && (
          <div className="alert alert-danger" role="alert" style={{ marginTop: '1rem' }}>
            <strong>Import Error:</strong> {uploadError}
          </div>
        )}

        {/* Upload Summary Card */}
        {uploadResult && (
          <div className="alert alert-success" style={{ marginTop: '1rem' }} data-testid="upload-summary">
            <h4 style={{ margin: '0 0 0.5rem 0', fontWeight: 600 }}>Batch Import Completed: {uploadResult.filename}</h4>
            <div className="import-stats-row">
              <div className="stat-pill">
                <span>Total Rows:</span> <strong>{uploadResult.totalRows}</strong>
              </div>
              <div className="stat-pill text-success">
                <span>Valid (Imported):</span> <strong>{uploadResult.validRows}</strong>
              </div>
              <div className="stat-pill text-warning">
                <span>Skipped (Duplicates):</span> <strong>{uploadResult.skippedRows}</strong>
              </div>
              <div className="stat-pill text-danger">
                <span>Failed (Validation):</span> <strong>{uploadResult.failedRows}</strong>
              </div>
            </div>

            {uploadResult.errors && uploadResult.errors.length > 0 && (
              <div className="import-errors-container" style={{ marginTop: '1rem' }}>
                <h5 style={{ margin: '0 0 0.5rem 0' }}>Validation Errors ({uploadResult.errors.length})</h5>
                <div className="table-responsive">
                  <table className="data-table data-table-sm">
                    <thead>
                      <tr>
                        <th>Row #</th>
                        <th>Field</th>
                        <th>Error Message</th>
                        <th>Raw Value</th>
                      </tr>
                    </thead>
                    <tbody>
                      {uploadResult.errors.map((err, idx) => (
                        <tr key={idx}>
                          <td>{err.row}</td>
                          <td><code>{err.field || '—'}</code></td>
                          <td>{err.message}</td>
                          <td><code>{err.rawValue || '—'}</code></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Trade Listing Section */}
      <div className="panel">
        <div className="panel-header">
          <h2 className="panel-title">Imported Trade Records ({totalElements.toLocaleString()})</h2>
        </div>

        {/* Filters Bar */}
        <div className="filter-bar">
          <div className="filter-item">
            <label htmlFor="filter-source">Source</label>
            <select
              id="filter-source"
              className="form-control"
              value={filterSource}
              onChange={(e) => setFilterSource(e.target.value as SourceSystem | '')}
            >
              <option value="">All Sources</option>
              <option value="SYSTEM_A">SYSTEM_A</option>
              <option value="SYSTEM_B">SYSTEM_B</option>
            </select>
          </div>

          <div className="filter-item">
            <label htmlFor="filter-date-from">Trade Date From</label>
            <input
              id="filter-date-from"
              type="date"
              className="form-control"
              value={filterDateFrom}
              onChange={(e) => setFilterDateFrom(e.target.value)}
            />
          </div>

          <div className="filter-item">
            <label htmlFor="filter-date-to">Trade Date To</label>
            <input
              id="filter-date-to"
              type="date"
              className="form-control"
              value={filterDateTo}
              onChange={(e) => setFilterDateTo(e.target.value)}
            />
          </div>

          <div className="filter-item">
            <label htmlFor="filter-symbol">Symbol</label>
            <input
              id="filter-symbol"
              type="text"
              placeholder="e.g. AAPL"
              className="form-control"
              value={filterSymbol}
              onChange={(e) => setFilterSymbol(e.target.value.toUpperCase())}
            />
          </div>

          <div className="filter-item">
            <label htmlFor="filter-side">Side</label>
            <select
              id="filter-side"
              className="form-control"
              value={filterSide}
              onChange={(e) => setFilterSide(e.target.value as TradeSide | '')}
            >
              <option value="">All Sides</option>
              <option value="BUY">BUY</option>
              <option value="SELL">SELL</option>
            </select>
          </div>

          <div className="filter-item filter-actions">
            <button className="btn btn-secondary" onClick={resetFilters}>
              Clear Filters
            </button>
          </div>
        </div>

        {listError && (
          <div className="alert alert-danger" role="alert" style={{ margin: '1rem' }}>
            {listError}
          </div>
        )}

        {/* Data Table */}
        <div className="table-responsive">
          <table className="data-table" data-testid="trade-table">
            <thead>
              <tr>
                <th>Source</th>
                <th>Trade ID</th>
                <th>Symbol</th>
                <th>Side</th>
                <th>Quantity</th>
                <th>Price</th>
                <th>Trade Date</th>
                <th>Settlement</th>
                <th>Account</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={10} className="text-center">
                    <div className="spinner" style={{ margin: '1rem auto' }} />
                    Loading trades...
                  </td>
                </tr>
              ) : trades.length > 0 ? (
                trades.map((trade) => (
                  <tr key={trade.id} className="clickable-row" onClick={() => setSelectedTrade(trade)}>
                    <td>
                      <span className="badge badge-secondary">{trade.sourceSystem}</span>
                    </td>
                    <td className="font-mono text-xs font-semibold">{trade.externalTradeId}</td>
                    <td><strong>{trade.symbol}</strong></td>
                    <td>
                      <StatusBadge status={trade.side} type="side" />
                    </td>
                    <td className="text-right font-mono">{Number(trade.quantity).toLocaleString()}</td>
                    <td className="text-right font-mono">${Number(trade.price).toFixed(2)}</td>
                    <td className="text-xs">{trade.tradeDate}</td>
                    <td className="text-xs">{trade.settlementDate}</td>
                    <td className="text-xs">{trade.account || '—'}</td>
                    <td>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={(e) => {
                          e.stopPropagation()
                          setSelectedTrade(trade)
                        }}
                      >
                        Inspect
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={10} className="text-center empty-state-cell">
                    No trades found matching current filter criteria.
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
          onPageChange={(page) => fetchTrades(page)}
        />
      </div>

      {/* Trade Detail Modal */}
      {selectedTrade && (
        <Modal
          isOpen={!!selectedTrade}
          title={`Trade Record: ${selectedTrade.externalTradeId}`}
          onClose={() => setSelectedTrade(null)}
        >
          <div className="detail-grid">
            <div className="detail-item">
              <span className="detail-label">Internal UUID</span>
              <span className="detail-value font-mono text-xs">{selectedTrade.id}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Source System</span>
              <span className="detail-value font-semibold">{selectedTrade.sourceSystem}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">External Trade ID</span>
              <span className="detail-value font-mono font-semibold">{selectedTrade.externalTradeId}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Symbol / Ticker</span>
              <span className="detail-value font-semibold">{selectedTrade.symbol}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Side</span>
              <span className="detail-value">
                <StatusBadge status={selectedTrade.side} type="side" />
              </span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Quantity</span>
              <span className="detail-value font-mono">{selectedTrade.quantity}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Execution Price</span>
              <span className="detail-value font-mono">${selectedTrade.price}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Trade Execution Date</span>
              <span className="detail-value">{selectedTrade.tradeDate}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Settlement Date</span>
              <span className="detail-value">{selectedTrade.settlementDate}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Account / Portfolio</span>
              <span className="detail-value">{selectedTrade.account || 'N/A'}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Counterparty</span>
              <span className="detail-value">{selectedTrade.counterparty || 'N/A'}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Batch ID</span>
              <span className="detail-value font-mono text-xs">{selectedTrade.importBatchId || 'N/A'}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Ingested At</span>
              <span className="detail-value text-xs">{new Date(selectedTrade.createdAt).toLocaleString()}</span>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
