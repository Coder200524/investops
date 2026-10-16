import React, { createContext, useContext, useEffect, useState } from 'react'
import { authService } from '../services/authService'
import { apiClient } from '../services/apiClient'
import type { AppUser } from '../types/auth'

interface AuthContextType {
  user: AppUser | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  isAuthenticated: false,
  isLoading: true,
  login: async () => {},
  logout: () => {},
})

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AppUser | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(true)

  useEffect(() => {
    const initAuth = async () => {
      const header = apiClient.getAuthHeader()
      if (header) {
        try {
          const currentUser = await authService.getCurrentUser()
          if (currentUser) {
            setUser(currentUser)
          } else {
            apiClient.clearCredentials()
          }
        } catch {
          apiClient.clearCredentials()
        }
      }
      setIsLoading(false)
    }

    initAuth()
  }, [])

  const login = async (username: string, password: string) => {
    setIsLoading(true)
    try {
      const res = await authService.login(username, password)
      setUser({
        id: res.userId,
        username: res.username,
        email: res.email,
        role: res.role,
        active: true,
      })
    } finally {
      setIsLoading(false)
    }
  }

  const logout = () => {
    authService.logout()
    setUser(null)
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
