-- Migration V13: Backfill owner_uid in shops for legacy stores (SLE-70)
UPDATE shops
SET owner_uid = 'e9b8f2c1-7d4a-4e89-b1c2-3f4a5b6c7d8e'
WHERE owner_uid IS NULL;

