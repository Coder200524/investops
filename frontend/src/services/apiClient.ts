import type { ProblemDetail } from '../types/api'

export class ApiError extends Error {
  status: number
  problemDetail?: ProblemDetail

  constructor(status: number, message: string, problemDetail?: ProblemDetail) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problemDetail = problemDetail
  }
}

class ApiClient {
  private baseUrl: string
  private authHeader: string | null = null

  constructor() {
    this.baseUrl = import.meta.env.VITE_API_BASE_URL || ''
    const saved = sessionStorage.getItem('investops_auth_header')
    if (saved) {
      this.authHeader = saved
    }
  }

  setCredentials(username: string, password: string):void {
    const token = btoa(`${username}:${password}`)
    this.authHeader = `Basic ${token}`
    sessionStorage.setItem('investops_auth_header', this.authHeader)
  }

  clearCredentials(): void {
    this.authHeader = null
    sessionStorage.removeItem('investops_auth_header')
  }

  getAuthHeader(): string | null {
    return this.authHeader
  }

  private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const url = `${this.baseUrl}${endpoint}`
    const headers = new Headers(options.headers || {})

    if (!headers.has('Accept')) {
      headers.set('Accept', 'application/json')
    }

    if (this.authHeader && !headers.has('Authorization')) {
      headers.set('Authorization', this.authHeader)
    }

    const response = await fetch(url, {
      ...options,
      headers,
    })

    let json: any = null
    const contentType = response.headers.get('content-type')
    if (contentType && contentType.includes('application/json')) {
      try {
        json = await response.json()
      } catch (err) {
        // Failed to parse JSON
      }
    }

    if (!response.ok) {
      const problemDetail: ProblemDetail | undefined = json?.error || (json?.title ? json : undefined)
      let message = problemDetail?.detail || problemDetail?.title || `Request failed with status ${response.status}`
      if (response.status === 401) {
        message = 'Authentication required or invalid credentials.'
      } else if (response.status === 403) {
        message = 'Access denied. You do not have permission for this resource.'
      } else if (response.status === 409) {
        message = problemDetail?.detail || 'Conflict: Record has been modified by another process. Please refresh.'
      }

      throw new ApiError(response.status, message, problemDetail)
    }

    // Backend returns ApiResponse<T> where data is in json.data
    if (json && typeof json === 'object' && 'data' in json) {
      return json.data as T
    }

    return json as T
  }

  async get<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'GET' })
  }

  async post<T>(endpoint: string, body?: any): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: body ? JSON.stringify(body) : undefined,
    })
  }

  async patch<T>(endpoint: string, body: any): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    })
  }

  async postFormData<T>(endpoint: string, formData: FormData): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: formData,
    })
  }
}

export const apiClient = new ApiClient()
