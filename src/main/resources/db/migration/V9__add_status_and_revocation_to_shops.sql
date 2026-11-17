-- Migration V9: Add status, revocation timestamp and audit info to shops table (SLE-70, SLE-255)

ALTER TABLE shops ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE shops ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_shops_status_category ON shops (status, category);

