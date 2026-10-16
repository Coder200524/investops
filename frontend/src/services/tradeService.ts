import type { PagedResponse } from '../types/api'
import type { ImportBatchDto, SourceSystem, TradeImportResponse, TradeRecordDto, TradeSide } from '../types/trade'
import { apiClient } from './apiClient'

export interface TradeQueryParams {
  sourceSystem?: SourceSystem | ''
  tradeDateFrom?: string
  tradeDateTo?: string
  symbol?: string
  side?: TradeSide | ''
  page?: number
  size?: number
  sort?: string
}

export const tradeService = {
  async importTrades(file: File, sourceSystem: SourceSystem): Promise<TradeImportResponse> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('sourceSystem', sourceSystem)
    return apiClient.postFormData<TradeImportResponse>('/api/v1/trades/import', formData)
  },

  async getTrades(params: TradeQueryParams = {}): Promise<PagedResponse<TradeRecordDto>> {
    const query = new URLSearchParams()
    if (params.sourceSystem) query.append('sourceSystem', params.sourceSystem)
    if (params.tradeDateFrom) query.append('tradeDateFrom', params.tradeDateFrom)
    if (params.tradeDateTo) query.append('tradeDateTo', params.tradeDateTo)
    if (params.symbol) query.append('symbol', params.symbol)
    if (params.side) query.append('side', params.side)
    if (params.page !== undefined) query.append('page', params.page.toString())
    if (params.size !== undefined) query.append('size', params.size.toString())
    if (params.sort) query.append('sort', params.sort)

    const queryString = query.toString() ? `?${query.toString()}` : ''
    return apiClient.get<PagedResponse<TradeRecordDto>>(`/api/v1/trades${queryString}`)
  },

  async getTradeById(id: string): Promise<TradeRecordDto> {
    return apiClient.get<TradeRecordDto>(`/api/v1/trades/${id}`)
  },

  async getBatchById(batchId: string): Promise<ImportBatchDto> {
    return apiClient.get<ImportBatchDto>(`/api/v1/trades/batches/${batchId}`)
  },
}
