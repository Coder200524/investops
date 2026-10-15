-- ==============================================================================
-- V2: Create Trade Records and Import Batches Tables
-- Architecture Reference: docs/ARCHITECTURE.md Section 2 (Database Design)
-- ==============================================================================

-- 1. Import Batch Tracking
CREATE TABLE IF NOT EXISTS import_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename VARCHAR(255) NOT NULL,
    source_system VARCHAR(20) NOT NULL,
    uploaded_by UUID REFERENCES app_user(id) ON DELETE SET NULL,
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    skipped_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    error_details TEXT,
    file_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_batch_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_batch_source_system CHECK (source_system IN ('SYSTEM_A', 'SYSTEM_B'))
);

CREATE INDEX IF NOT EXISTS idx_batch_file_hash ON import_batch(file_hash);
CREATE INDEX IF NOT EXISTS idx_batch_status ON import_batch(status);
CREATE INDEX IF NOT EXISTS idx_batch_created_at ON import_batch(created_at);

-- 2. Trade Records
CREATE TABLE IF NOT EXISTS trade_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_trade_id VARCHAR(50) NOT NULL,
    source_system VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    settlement_date DATE NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity NUMERIC(18,8) NOT NULL,
    price NUMERIC(18,8) NOT NULL,
    counterparty VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'VALIDATED',
    import_batch_id UUID NOT NULL REFERENCES import_batch(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trade_source_external UNIQUE (source_system, external_trade_id),
    CONSTRAINT chk_trade_source_system CHECK (source_system IN ('SYSTEM_A', 'SYSTEM_B')),
    CONSTRAINT chk_trade_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_trade_status CHECK (status IN ('PENDING', 'VALIDATED', 'REJECTED')),
    CONSTRAINT chk_trade_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_trade_price_positive CHECK (price > 0),
    CONSTRAINT chk_trade_settlement_date CHECK (settlement_date >= trade_date)
);

CREATE INDEX IF NOT EXISTS idx_trade_source_date ON trade_record (source_system, trade_date);
CREATE INDEX IF NOT EXISTS idx_trade_external_id ON trade_record (external_trade_id);
CREATE INDEX IF NOT EXISTS idx_trade_batch_id ON trade_record (import_batch_id);
CREATE INDEX IF NOT EXISTS idx_trade_symbol ON trade_record (symbol);
