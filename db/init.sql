-- Table creation
CREATE TABLE IF NOT EXISTS shops (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    url VARCHAR(500),
    category VARCHAR(255) NOT NULL,
    rib VARCHAR(34) NOT NULL,
    user_id BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_shops_user_id ON shops(user_id);

-- Seed test data on initial startup
INSERT INTO shops (name, address, url, category, rib, user_id) VALUES
('Burger King Lille', '10 Rue Nationale, 59000 Lille', 'https://www.urban-coeurcommerce.com/wp-content/uploads/2025/01/photo-13-7-1-1.jpg', 'Restauration', 'FR7630006000011234567890189', 1),
('Printemps Paris', '25 Avenue de l''Opéra, 75001 Paris', 'https://cdn.prod.website-files.com/69c11320dc2f4180f9b647cc/69c11320dc2f4180f9b64a86_printemps-mobile.webp', 'Commerce de détail', 'FR7630006000011234567890189', 1);