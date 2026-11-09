-- Migration V5: Add public UUID identifier (uid) to shops table (SLE-157)

-- Add uid column with default gen_random_uuid()
ALTER TABLE shops ADD COLUMN IF NOT EXISTS uid UUID DEFAULT gen_random_uuid();

-- Seed deterministic UUIDs for initial development stores if present and not set
UPDATE shops SET uid = '11111111-1111-1111-1111-111111111111' WHERE id = 1 AND uid IS NULL;
UPDATE shops SET uid = '22222222-2222-2222-2222-222222222222' WHERE id = 2 AND uid IS NULL;

-- Backfill any remaining null uid values
UPDATE shops SET uid = gen_random_uuid() WHERE uid IS NULL;

-- Enforce NOT NULL constraint
ALTER TABLE shops ALTER COLUMN uid SET NOT NULL;

-- Add unique constraint
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_shops_uid'
    ) THEN
        ALTER TABLE shops ADD CONSTRAINT uq_shops_uid UNIQUE (uid);
    END IF;
END $$;

-- Create index on uid for fast lookups
CREATE INDEX IF NOT EXISTS idx_shops_uid ON shops(uid);

