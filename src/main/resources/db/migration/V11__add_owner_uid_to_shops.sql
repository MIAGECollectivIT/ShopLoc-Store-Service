-- Migration V11: Add owner_uid UUID column to shops and relax legacy user_id constraint

ALTER TABLE shops ADD COLUMN IF NOT EXISTS owner_uid UUID;
CREATE INDEX IF NOT EXISTS idx_shops_owner_uid ON shops(owner_uid);
ALTER TABLE shops ALTER COLUMN user_id DROP NOT NULL;
