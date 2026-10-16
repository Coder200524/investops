export type UserRole = 'ANALYST' | 'ADMIN'

export interface AppUser {
  id: string
  username: string
  email: string
  role: UserRole
  active: boolean
}

export interface LoginResponse {
  userId: string
  username: string
  email: string
  role: UserRole
  token?: string
}

export interface AuthState {
  user: AppUser | null
  isAuthenticated: boolean
  basicAuthToken: string | null
}
