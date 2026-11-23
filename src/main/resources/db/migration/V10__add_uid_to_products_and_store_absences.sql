-- Migration V10: Add public UUID identifier (uid) to products and store_absences tables

ALTER TABLE products ADD COLUMN IF NOT EXISTS uid UUID DEFAULT gen_random_uuid();
UPDATE products SET uid = gen_random_uuid() WHERE uid IS NULL;
ALTER TABLE products ALTER COLUMN uid SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_products_uid ON products(uid);

ALTER TABLE store_absences ADD COLUMN IF NOT EXISTS uid UUID DEFAULT gen_random_uuid();
UPDATE store_absences SET uid = gen_random_uuid() WHERE uid IS NULL;
ALTER TABLE store_absences ALTER COLUMN uid SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_store_absences_uid ON store_absences(uid);
