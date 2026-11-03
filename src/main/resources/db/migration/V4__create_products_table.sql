-- Create products table for Click & Collect catalog
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL,
    reference VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(10, 2) NOT NULL,
    image_url VARCHAR(1000),
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_shop FOREIGN KEY (store_id) REFERENCES shops(id) ON DELETE CASCADE,
    CONSTRAINT uq_store_product_reference UNIQUE (store_id, reference),
    CONSTRAINT chk_product_price_non_negative CHECK (price >= 0),
    CONSTRAINT chk_product_stock_non_negative CHECK (stock_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_products_store_id ON products(store_id);
CREATE INDEX IF NOT EXISTS idx_products_store_reference ON products(store_id, reference);
