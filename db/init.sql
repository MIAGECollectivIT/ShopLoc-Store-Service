-- Création de la table
CREATE TABLE IF NOT EXISTS shops (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    url VARCHAR(500)
);

-- Insertion de données de test au premier démarrage
INSERT INTO shops (name, address, url) VALUES
('Burger King Lille', '10 Rue Nationale, 59000 Lille', 'https://fr.wikipedia.org/wiki/Fichier:Burger_King_logo_%281999%E2%80%932020%29.svg'),
('Prinptemps Paris', '25 Avenue de l''Opéra, 75001 Paris', 'https://cdn.prod.website-files.com/69c11320dc2f4180f9b647cc/69c11320dc2f4180f9b64a86_printemps-mobile.webp');