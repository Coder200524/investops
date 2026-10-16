-- ==============================================================================
-- V4: Create Exception Management Tables
-- Architecture Reference: docs/ARCHITECTURE.md Section 2 (Database Design)
-- ==============================================================================

-- 1. Trade Exceptions Table
CREATE TABLE IF NOT EXISTS trade_exception (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id UUID NOT NULL UNIQUE REFERENCES reconciliation_result(id) ON DELETE CASCADE,
    run_id UUID NOT NULL REFERENCES reconciliation_run(id) ON DELETE CASCADE,
    exception_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    field_name VARCHAR(50),
    expected_value VARCHAR(255),
    actual_value VARCHAR(255),
    resolution_notes TEXT,
    assigned_to UUID REFERENCES app_user(id) ON DELETE SET NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_exception_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_exception_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'IN_REVIEW', 'RESOLVED', 'ESCALATED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_exception_status_severity ON trade_exception (status, severity);
CREATE INDEX IF NOT EXISTS idx_exception_run ON trade_exception (run_id);
CREATE INDEX IF NOT EXISTS idx_exception_result ON trade_exception (result_id);
CREATE INDEX IF NOT EXISTS idx_exception_assigned_to ON trade_exception (assigned_to);
CREATE INDEX IF NOT EXISTS idx_exception_created_at ON trade_exception (created_at);

-- 2. Exception Comments Table
CREATE TABLE IF NOT EXISTS exception_comment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exception_id UUID NOT NULL REFERENCES trade_exception(id) ON DELETE CASCADE,
    author_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comment_exception ON exception_comment (exception_id, created_at);

-- 3. Exception Audit Log Table (Append-only)
CREATE TABLE IF NOT EXISTS exception_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exception_id UUID NOT NULL REFERENCES trade_exception(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    field_changed VARCHAR(50),
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_exception ON exception_audit_log (exception_id, created_at);
