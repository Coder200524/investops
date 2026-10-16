export interface ApiResponse<T> {
  success: boolean
  data: T
  error?: ProblemDetail | null
  timestamp: string
}

export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail: string
  instance?: string
  errors?: FieldErrorDetail[]
}

export interface FieldErrorDetail {
  field: string
  message: string
  rejectedValue?: unknown
}

export interface PagedResponse<T> {
  content: T[]
  pageNumber: number
  pageSize: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  empty: boolean
}
