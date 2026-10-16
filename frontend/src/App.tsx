import React, { useState } from 'react'
import { AuthProvider, useAuth } from './context/AuthContext'
import { DashboardPage } from './pages/DashboardPage'
import { TradePage } from './pages/TradePage'
import { ReconciliationPage } from './pages/ReconciliationPage'
import { ExceptionPage } from './pages/ExceptionPage'
import { LoginPage } from './pages/LoginPage'
import { Icon } from './components/Icon'
import './App.css'

type NavTab = 'dashboard' | 'trades' | 'reconciliation' | 'exceptions' | 'login'

const MainApp: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth()
  const [activeTab, setActiveTab] = useState<NavTab>('dashboard')
  const [selectedRunId, setSelectedRunId] = useState<string | undefined>(undefined)
  const [selectedExceptionId, setSelectedExceptionId] = useState<string | undefined>(undefined)

  const handleNavigateToRecon = (runId?: string) => {
    setSelectedRunId(runId)
    setActiveTab('reconciliation')
  }

  const handleNavigateToExceptions = (exceptionId?: string) => {
    setSelectedExceptionId(exceptionId)
    setActiveTab('exceptions')
  }

  return (
    <div className="app-layout">
      {/* Top Navbar */}
      <header className="top-navbar">
        <div className="navbar-left">
          <div className="brand-logo" onClick={() => setActiveTab('dashboard')} style={{ cursor: 'pointer' }}>
            <span className="brand-icon">
              <Icon name="logo" size={18} />
            </span>
            <span className="brand-title">INVESTOPS</span>
            <span className="env-badge">ENTERPRISE</span>
          </div>
        </div>

        <div className="navbar-right">
          <div className="system-health-tag">
            <span className="health-dot active" />
            <span>PostgreSQL Engine Active</span>
          </div>

          {isAuthenticated && user ? (
            <div className="user-profile-badge">
              <span className="user-avatar">{user.username.charAt(0).toUpperCase()}</span>
              <div className="user-details">
                <span className="user-name">{user.username}</span>
                <span className="user-role-tag">{user.role}</span>
              </div>
              <button
                className="btn btn-secondary btn-xs"
                onClick={logout}
                style={{ marginLeft: '0.5rem' }}
              >
                Sign Out
              </button>
            </div>
          ) : (
            <button
              className="btn btn-primary btn-sm"
              onClick={() => setActiveTab('login')}
            >
              Sign In (Analyst)
            </button>
          )}
        </div>
      </header>

      {/* Main Container with Sidebar + Content */}
      <div className="app-body">
        {/* Sidebar */}
        <aside className="sidebar">
          <nav className="sidebar-nav">
            <button
              id="nav-dashboard"
              className={`nav-item ${activeTab === 'dashboard' ? 'active' : ''}`}
              onClick={() => setActiveTab('dashboard')}
            >
              <span className="nav-icon"><Icon name="dashboard" size={17} /></span>
              <span className="nav-label">Dashboard</span>
            </button>

            <button
              id="nav-trades"
              className={`nav-item ${activeTab === 'trades' ? 'active' : ''}`}
              onClick={() => setActiveTab('trades')}
            >
              <span className="nav-icon"><Icon name="trade" size={17} /></span>
              <span className="nav-label">Trade Ingestion</span>
            </button>

            <button
              id="nav-reconciliation"
              className={`nav-item ${activeTab === 'reconciliation' ? 'active' : ''}`}
              onClick={() => {
                setSelectedRunId(undefined)
                setActiveTab('reconciliation')
              }}
            >
              <span className="nav-icon"><Icon name="reconciliation" size={17} /></span>
              <span className="nav-label">Reconciliation</span>
            </button>

            <button
              id="nav-exceptions"
              className={`nav-item ${activeTab === 'exceptions' ? 'active' : ''}`}
              onClick={() => {
                setSelectedExceptionId(undefined)
                setActiveTab('exceptions')
              }}
            >
              <span className="nav-icon"><Icon name="exception" size={17} /></span>
              <span className="nav-label">Exceptions</span>
            </button>

            <div style={{ borderTop: '1px solid var(--border-subtle)', margin: '1rem 0' }} />

            <button
              id="nav-auth"
              className={`nav-item ${activeTab === 'login' ? 'active' : ''}`}
              onClick={() => setActiveTab('login')}
            >
              <span className="nav-icon"><Icon name="lock" size={17} /></span>
              <span className="nav-label">{isAuthenticated ? 'Account Profile' : 'Operator Login'}</span>
            </button>
          </nav>

          <div className="sidebar-footer">
            <div className="version-info">
              <span>InvestOps v1.0.0</span>
              <span className="text-muted">Apollo Portfolio Project</span>
            </div>
          </div>
        </aside>

        {/* Content View */}
        <main className="main-content">
          {activeTab === 'dashboard' && (
            <DashboardPage
              onNavigateToRecon={handleNavigateToRecon}
              onNavigateToExceptions={handleNavigateToExceptions}
            />
          )}

          {activeTab === 'trades' && <TradePage />}

          {activeTab === 'reconciliation' && (
            <ReconciliationPage initialRunId={selectedRunId} />
          )}

          {activeTab === 'exceptions' && (
            <ExceptionPage initialExceptionId={selectedExceptionId} />
          )}

          {activeTab === 'login' && (
            <LoginPage onSuccess={() => setActiveTab('dashboard')} />
          )}
        </main>
      </div>
    </div>
  )
}

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <MainApp />
    </AuthProvider>
  )
}

export default App
