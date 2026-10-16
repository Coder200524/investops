import React, { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { Icon } from '../components/Icon'

interface LoginPageProps {
  onSuccess?: () => void
}

export const LoginPage: React.FC<LoginPageProps> = ({ onSuccess }) => {
  const { login } = useAuth()
  const [username, setUsername] = useState<string>('analyst1')
  const [password, setPassword] = useState<string>('password123')
  const [loading, setLoading] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      await login(username, password)
      if (onSuccess) onSuccess()
    } catch (err: any) {
      setError(err.message || 'Invalid username or password.')
    } finally {
      setLoading(false)
    }
  }

  const fillDemoCredentials = (u: string, p: string) => {
    setUsername(u)
    setPassword(p)
  }

  return (
    <div className="login-container" data-testid="login-page">
      <div className="login-card">
        <div className="login-header">
          <div className="brand-logo-large">
            <span className="brand-icon">
              <Icon name="logo" size={26} />
            </span>
            <span className="brand-name">InvestOps</span>
          </div>
          <p className="login-subtitle">Institutional Trade Reconciliation & Exception Management Platform</p>
        </div>

        {error && (
          <div className="alert alert-danger" role="alert" style={{ marginBottom: '1rem' }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="login-username">Operator Username</label>
            <input
              id="login-username"
              type="text"
              required
              className="form-control"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="e.g. analyst1"
            />
          </div>

          <div className="form-group">
            <label htmlFor="login-password">Password</label>
            <input
              id="login-password"
              type="password"
              required
              className="form-control"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            id="btn-login-submit"
            className="btn btn-primary"
            style={{ width: '100%', marginTop: '0.5rem' }}
            disabled={loading}
          >
            {loading ? 'Authenticating...' : 'Sign In to Operations Portal'}
          </button>
        </form>

        <div className="demo-credentials-box">
          <div className="demo-credentials-title">Quick Demo Sign-In:</div>
          <div className="demo-buttons-row">
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => fillDemoCredentials('analyst1', 'password123')}
            >
              Operations Analyst (analyst1)
            </button>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => fillDemoCredentials('admin1', 'password123')}
            >
              System Admin (admin1)
            </button>
          </div>
          <span className="text-xs text-muted" style={{ display: 'block', marginTop: '0.5rem' }}>
            HTTP Basic authentication integrated directly with PostgreSQL <code>app_user</code> table.
          </span>
        </div>
      </div>
    </div>
  )
}
