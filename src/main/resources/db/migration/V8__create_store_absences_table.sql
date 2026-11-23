-- Migration V8: Create store_absences table for multiple absences per store (SLE-54)

CREATE TABLE IF NOT EXISTS store_absences (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE,
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_store_absences_store_id ON store_absences(store_id);

-- Migrate existing single absences to store_absences table
INSERT INTO store_absences (store_id, start_date, end_date, reason)
SELECT id, absence_start_date, absence_end_date, absence_reason
FROM shops
WHERE absence_start_date IS NOT NULL;
