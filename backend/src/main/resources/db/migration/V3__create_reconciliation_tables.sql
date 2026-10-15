-- ==============================================================================
-- V3: Create Reconciliation Runs and Results Tables
-- Architecture Reference: docs/ARCHITECTURE.md Section 2 (Database Design)
-- ==============================================================================

-- 1. Reconciliation Runs Table
CREATE TABLE IF NOT EXISTS reconciliation_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trade_date_from DATE NOT NULL,
    trade_date_to DATE NOT NULL,
    source_a VARCHAR(20) NOT NULL,
    source_b VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    total_compared INT NOT NULL DEFAULT 0,
    matched_count INT NOT NULL DEFAULT 0,
    break_count INT NOT NULL DEFAULT 0,
    price_tolerance NUMERIC(18,8) NOT NULL DEFAULT 0.01000000,
    initiated_by UUID REFERENCES app_user(id) ON DELETE SET NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_run_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_run_source_a CHECK (source_a IN ('SYSTEM_A', 'SYSTEM_B')),
    CONSTRAINT chk_run_source_b CHECK (source_b IN ('SYSTEM_A', 'SYSTEM_B')),
    CONSTRAINT chk_run_date_range CHECK (trade_date_to >= trade_date_from)
);

CREATE INDEX IF NOT EXISTS idx_recon_run_status ON reconciliation_run(status);
CREATE INDEX IF NOT EXISTS idx_recon_run_dates ON reconciliation_run(trade_date_from, trade_date_to);
CREATE INDEX IF NOT EXISTS idx_recon_run_created_at ON reconciliation_run(created_at);

-- 2. Reconciliation Results Table
CREATE TABLE IF NOT EXISTS reconciliation_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES reconciliation_run(id) ON DELETE CASCADE,
    trade_a_id UUID REFERENCES trade_record(id) ON DELETE SET NULL,
    trade_b_id UUID REFERENCES trade_record(id) ON DELETE SET NULL,
    match_status VARCHAR(20) NOT NULL,
    discrepancy_type VARCHAR(50) NOT NULL DEFAULT 'NONE',
    severity VARCHAR(20),
    difference_details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_result_match_status CHECK (match_status IN ('MATCHED', 'MISMATCHED', 'UNMATCHED', 'MISSING')),
    CONSTRAINT chk_result_severity CHECK (severity IS NULL OR severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX IF NOT EXISTS idx_recon_result_run_id ON reconciliation_result(run_id);
CREATE INDEX IF NOT EXISTS idx_recon_result_match_status ON reconciliation_result(match_status);
CREATE INDEX IF NOT EXISTS idx_recon_result_discrepancy ON reconciliation_result(discrepancy_type);
CREATE INDEX IF NOT EXISTS idx_recon_result_trade_a ON reconciliation_result(trade_a_id);
CREATE INDEX IF NOT EXISTS idx_recon_result_trade_b ON reconciliation_result(trade_b_id);
