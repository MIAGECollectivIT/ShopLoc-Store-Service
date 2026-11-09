-- Create users table to model partner accounts owning stores
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY
);

-- Ensure a default user exists for initial/seed stores
INSERT INTO users (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- Add category, rib, and user_id to shops table
ALTER TABLE shops ADD COLUMN IF NOT EXISTS category VARCHAR(255);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS rib VARCHAR(34);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- Backfill default data for existing records if present
UPDATE shops SET category = 'Restauration' WHERE category IS NULL AND name LIKE '%Burger King%';
UPDATE shops SET category = 'Commerce de détail' WHERE category IS NULL;
UPDATE shops SET rib = 'FR7630006000011234567890189' WHERE rib IS NULL;
UPDATE shops SET user_id = 1 WHERE user_id IS NULL;

-- Enforce NOT NULL constraints
ALTER TABLE shops ALTER COLUMN category SET NOT NULL;
ALTER TABLE shops ALTER COLUMN rib SET NOT NULL;
ALTER TABLE shops ALTER COLUMN user_id SET NOT NULL;

-- Add foreign key constraint to users table
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_shops_user'
    ) THEN
        ALTER TABLE shops ADD CONSTRAINT fk_shops_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

-- Create index on user_id for high-performance lookups of a user's stores
CREATE INDEX IF NOT EXISTS idx_shops_user_id ON shops(user_id);
