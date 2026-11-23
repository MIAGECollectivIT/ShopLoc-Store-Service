-- Migration V6: Add alert_threshold to products table (SLE-59, SLE-248)
ALTER TABLE products
ADD COLUMN IF NOT EXISTS alert_threshold INTEGER NOT NULL DEFAULT 5;

ALTER TABLE products
ADD CONSTRAINT chk_product_alert_threshold_non_negative CHECK (alert_threshold >= 0);

