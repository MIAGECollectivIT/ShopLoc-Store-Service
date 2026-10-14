-- Table creation
CREATE TABLE IF NOT EXISTS shops (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    url VARCHAR(500)
);

-- Seed test data on initial startup
INSERT INTO shops (name, address, url) VALUES
('Burger King Lille', '10 Rue Nationale, 59000 Lille', 'https://www.urban-coeurcommerce.com/wp-content/uploads/2025/01/photo-13-7-1-1.jpg'),
('Printemps Paris', '25 Avenue de l''Opéra, 75001 Paris', 'https://cdn.prod.website-files.com/69c11320dc2f4180f9b647cc/69c11320dc2f4180f9b64a86_printemps-mobile.webp');