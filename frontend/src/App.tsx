import React, { useState, useEffect } from 'react';
import './App.css';

type NavTab = 'overview' | 'trades' | 'reconciliation' | 'exceptions' | 'audit';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<NavTab>('overview');
  const [backendStatus, setBackendStatus] = useState<'UP' | 'OFFLINE' | 'CHECKING'>('CHECKING');
  const [lastChecked, setLastChecked] = useState<string>('');

  const checkHealth = async () => {
    setBackendStatus('CHECKING');
    try {
      const response = await fetch('http://localhost:8081/actuator/health', {
        headers: { 'Accept': 'application/json' },
      });
      if (response.ok) {
        const data = await response.json();
        setBackendStatus(data.status === 'UP' ? 'UP' : 'OFFLINE');
      } else {
        setBackendStatus('OFFLINE');
      }
    } catch {
      setBackendStatus('OFFLINE');
    } finally {
      setLastChecked(new Date().toLocaleTimeString());
    }
  };

  useEffect(() => {
    checkHealth();
  }, []);

  return (
    <div className="app-container">
      {/* Sidebar Navigation */}
      <aside className="app-sidebar">
        <div className="brand-section">
          <div className="brand-logo">IO</div>
          <div className="brand-text">
            <h1>InvestOps</h1>
            <p>Trade Reconciliation</p>
          </div>
        </div>

        <nav className="nav-menu">
          <button
            className={`nav-item ${activeTab === 'overview' ? 'active' : ''}`}
            onClick={() => setActiveTab('overview')}
          >
            <span>📊</span> Overview & Health
          </button>
          <button
            className={`nav-item ${activeTab === 'trades' ? 'active' : ''}`}
            onClick={() => setActiveTab('trades')}
          >
            <span>📥</span> Trade Ingestion
          </button>
          <button
            className={`nav-item ${activeTab === 'reconciliation' ? 'active' : ''}`}
            onClick={() => setActiveTab('reconciliation')}
          >
            <span>⚖️</span> Reconciliation Engine
          </button>
          <button
            className={`nav-item ${activeTab === 'exceptions' ? 'active' : ''}`}
            onClick={() => setActiveTab('exceptions')}
          >
            <span>🚨</span> Exception Management
          </button>
          <button
            className={`nav-item ${activeTab === 'audit' ? 'active' : ''}`}
            onClick={() => setActiveTab('audit')}
          >
            <span>📜</span> Audit History
          </button>
        </nav>

        <div className="sidebar-footer">
          <div>InvestOps v0.1.0-alpha</div>
          <div>Apollo SWE Internship Portfolio</div>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="app-main">
        {/* Header Bar */}
        <header className="app-header">
          <div className="header-title-area">
            <h2>Operations Console</h2>
            <span className="badge-tag">Milestone 1 — Initial Scaffolding</span>
          </div>

          <div className="header-actions">
            <div className="status-indicator">
              <span
                className={`pulse-dot ${
                  backendStatus === 'UP' ? '' : backendStatus === 'CHECKING' ? 'checking' : 'offline'
                }`}
              />
              <span>
                Backend API:{' '}
                <strong>
                  {backendStatus === 'UP'
                    ? 'Connected (UP)'
                    : backendStatus === 'CHECKING'
                    ? 'Checking...'
                    : 'Offline (Port 8081)'}
                </strong>
              </span>
            </div>

            <button className="btn-secondary" onClick={checkHealth} title="Refresh backend connectivity check">
              🔄 Check Health
            </button>

            <div className="user-profile">
              <div className="avatar">OP</div>
              <span>Analyst Console</span>
            </div>
          </div>
        </header>

        {/* Page Content */}
        <div className="page-content">
          {/* Hero Banner */}
          <section className="hero-banner">
            <h2 className="hero-title">Investment Operations &amp; Trade Reconciliation Platform</h2>
            <p className="hero-description">
              InvestOps provides automated, institutional-grade reconciliation across simulated internal trading
              and counterparty clearing systems. It enforces zero-duplicate ingestion, O(n+m) deterministic
              break detection, and optimistic-locking exception workflows.
            </p>
            <div className="hero-meta-row">
              <div className="meta-pill">
                <span>Architecture:</span>
                <strong>Modular Monolith</strong>
              </div>
              <div className="meta-pill">
                <span>Backend:</span>
                <strong>Java 17 / Spring Boot 3.3.4</strong>
              </div>
              <div className="meta-pill">
                <span>Database:</span>
                <strong>PostgreSQL 16 + Flyway</strong>
              </div>
              <div className="meta-pill">
                <span>Frontend:</span>
                <strong>React 19 + TypeScript + Vite</strong>
              </div>
              {lastChecked && (
                <div className="meta-pill">
                  <span>Last Probed:</span>
                  <strong>{lastChecked}</strong>
                </div>
              )}
            </div>
          </section>

          {/* Core Architectural Pillars */}
          <div className="grid-cards">
            <div className="arch-card">
              <div className="card-icon blue">📦</div>
              <h3>Modular Monolith</h3>
              <p>
                Strictly separated domain packages with in-process interfaces and domain events, ensuring ACID
                guarantees with clean future extraction to Kafka.
              </p>
            </div>

            <div className="arch-card">
              <div className="card-icon emerald">🎯</div>
              <h3>Exact Precision</h3>
              <p>
                Financial quantities and prices stored as PostgreSQL <code>NUMERIC(18,8)</code> and evaluated with
                Java <code>BigDecimal.compareTo()</code> to eliminate floating-point breaks.
              </p>
            </div>

            <div className="arch-card">
              <div className="card-icon amber">⚡</div>
              <h3>Idempotent Ingestion</h3>
              <p>
                Composite database constraints on <code>(source_system, external_trade_id)</code> and batch-level
                SHA-256 hashing ensure safe, duplicate-free retries.
              </p>
            </div>

            <div className="arch-card">
              <div className="card-icon violet">🛡️</div>
              <h3>Audit &amp; Concurrency</h3>
              <p>
                Discrepancy lifecycle with optimistic locking (<code>@Version</code>) to prevent lost updates,
                accompanied by append-only audit tracking.
              </p>
            </div>
          </div>

          {/* Milestone 1 Readiness Checklist */}
          <section className="section-panel">
            <h3>
              <span>Milestone 1 Scaffolding Status</span>
              <span className="badge-tag">Ready for Milestone 2</span>
            </h3>
            <div className="checklist-items">
              <div className="check-item">
                <span className="check-icon">✓</span>
                <span className="check-desc">
                  Spring Boot 3.3.4 backend structure initialized with Maven Wrapper (<code>mvnw.cmd</code> / <code>mvnw</code>)
                </span>
                <span className="check-tag">com.investops</span>
              </div>

              <div className="check-item">
                <span className="check-icon">✓</span>
                <span className="check-desc">
                  Spring Web, JPA, Flyway, PostgreSQL, Security, Validation, Actuator, and Lombok configured
                </span>
                <span className="check-tag">backend/pom.xml</span>
              </div>

              <div className="check-item">
                <span className="check-icon">✓</span>
                <span className="check-desc">
                  PostgreSQL 16 Docker Compose configured with persistent volume and <code>pg_isready</code> health check
                </span>
                <span className="check-tag">docker-compose.yml</span>
              </div>

              <div className="check-item">
                <span className="check-icon">✓</span>
                <span className="check-desc">
                  Environment variables template and database credentials safely decoupled from version control
                </span>
                <span className="check-tag">.env.example</span>
              </div>

              <div className="check-item">
                <span className="check-icon">✓</span>
                <span className="check-desc">
                  Flyway database migration pipeline established (V1 initial schema for <code>app_user</code>)
                </span>
                <span className="check-tag">V1__create_users_table.sql</span>
              </div>

              <div className="check-item">
                <span className="check-icon">✓</span>
                <span className="check-desc">
                  Spring Boot Actuator health endpoint configured and tested (Context load + Health test: 2/2 passed)
                </span>
                <span className="check-tag">/actuator/health</span>
              </div>

              <div className="check-item">
                <span className="check-icon">✓</span>
                <span className="check-desc">
                  React 19 + TypeScript + Vite frontend application shell scaffolded independently
                </span>
                <span className="check-tag">frontend/</span>
              </div>
            </div>
          </section>
        </div>
      </main>
    </div>
  );
};

export default App;
