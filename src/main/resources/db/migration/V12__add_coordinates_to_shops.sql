-- Migration V12: Add GPS coordinates to shops for Click & Collect itinerary optimization (SLE-31, SLE-297)
ALTER TABLE shops ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7);

-- Create index on coordinates for spatial lookups
CREATE INDEX IF NOT EXISTS idx_shops_coordinates ON shops(latitude, longitude);

-- Backfill default coordinates for seed shops if present
UPDATE shops SET latitude = 50.6356000, longitude = 3.0617000 WHERE name = 'Burger King Lille' AND latitude IS NULL;
UPDATE shops SET latitude = 48.8698000, longitude = 2.3323000 WHERE name = 'Printemps Paris' AND latitude IS NULL;
