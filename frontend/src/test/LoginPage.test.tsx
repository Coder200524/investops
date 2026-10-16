import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { LoginPage } from '../pages/LoginPage'
import { AuthProvider } from '../context/AuthContext'
import { authService } from '../services/authService'
import { ApiError } from '../services/apiClient'
import type { LoginResponse } from '../types/auth'

describe('LoginPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('renders login form and demo credential buttons', () => {
    render(
      <AuthProvider>
        <LoginPage />
      </AuthProvider>,
    )

    expect(screen.getByLabelText(/Operator Username/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Sign In to Operations Portal/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /analyst1/i })).toBeInTheDocument()
  })

  it('authenticates user and executes callback on success', async () => {
    const mockResponse: LoginResponse = {
      userId: 'user-uuid-1',
      username: 'analyst1',
      email: 'analyst1@investops.com',
      role: 'ANALYST',
    }

    vi.spyOn(authService, 'login').mockResolvedValue(mockResponse)
    const onSuccess = vi.fn()

    render(
      <AuthProvider>
        <LoginPage onSuccess={onSuccess} />
      </AuthProvider>,
    )

    const submitBtn = screen.getByRole('button', { name: /Sign In to Operations Portal/i })
    fireEvent.click(submitBtn)

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalled()
    })
  })

  it('displays error alert on 401 invalid credentials', async () => {
    vi.spyOn(authService, 'login').mockRejectedValue(
      new ApiError(401, 'Authentication required or invalid credentials.'),
    )

    render(
      <AuthProvider>
        <LoginPage />
      </AuthProvider>,
    )

    const submitBtn = screen.getByRole('button', { name: /Sign In to Operations Portal/i })
    fireEvent.click(submitBtn)

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/invalid credentials/i)
    })
  })
})
