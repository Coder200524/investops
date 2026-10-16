import type { AppUser, LoginResponse } from '../types/auth'
import { apiClient } from './apiClient'

export const authService = {
  async login(username: string, password: string): Promise<LoginResponse> {
    apiClient.setCredentials(username, password)
    try {
      const response = await apiClient.post<LoginResponse>('/api/v1/auth/login', { username, password })
      return response
    } catch (err) {
      apiClient.clearCredentials()
      throw err
    }
  },

  async getCurrentUser(): Promise<AppUser | null> {
    return apiClient.get<AppUser | null>('/api/v1/auth/me')
  },

  async listUsers(): Promise<AppUser[]> {
    return apiClient.get<AppUser[]>('/api/v1/auth/users')
  },

  logout(): void {
    apiClient.clearCredentials()
  },
}
