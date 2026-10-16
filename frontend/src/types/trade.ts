export type SourceSystem = 'SYSTEM_A' | 'SYSTEM_B'
export type TradeSide = 'BUY' | 'SELL'

export interface TradeRecordDto {
  id: string
  sourceSystem: SourceSystem
  externalTradeId: string
  symbol: string
  side: TradeSide
  quantity: number
  price: number
  tradeDate: string
  settlementDate: string
  account?: string
  counterparty?: string
  currency?: string
  importBatchId?: string
  createdAt: string
  updatedAt: string
}

export interface TradeImportError {
  row: number
  field?: string
  message: string
  rawValue?: string
}

export interface TradeImportResponse {
  batchId: string
  filename: string
  sourceSystem: SourceSystem
  fileHash: string
  totalRows: number
  validRows: number
  skippedRows: number
  failedRows: number
  status: string
  errors: TradeImportError[]
}

export interface ImportBatchDto {
  id: string
  sourceSystem: SourceSystem
  filename: string
  fileHash: string
  totalRecords: number
  acceptedRecords: number
  rejectedRecords: number
  status: string
  errorMessage?: string
  createdAt: string
}
