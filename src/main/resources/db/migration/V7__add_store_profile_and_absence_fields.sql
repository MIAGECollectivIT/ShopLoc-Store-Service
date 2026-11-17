-- Migration V6: Add profile, opening hours and absence schedule fields to shops table (SLE-54)

ALTER TABLE shops ADD COLUMN IF NOT EXISTS opening_hours TEXT;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS absence_start_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS absence_end_date TIMESTAMP WITH TIME ZONE;
ALTER TABLE shops ADD COLUMN IF NOT EXISTS absence_reason VARCHAR(255);
