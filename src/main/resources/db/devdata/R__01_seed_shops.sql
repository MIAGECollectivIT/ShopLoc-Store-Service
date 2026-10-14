-- Seed test data (executed in local and dev environments only)
INSERT INTO shops (name, address, url)
SELECT 'Burger King Lille', '10 Rue Nationale, 59000 Lille', 'https://static.actu.fr/uploads/2024/12/f3334bf1b9576f7334bf1b957cb334v.jpg'
WHERE NOT EXISTS (SELECT 1 FROM shops WHERE name = 'Burger King Lille');

INSERT INTO shops (name, address, url)
SELECT 'Printemps Paris', '25 Avenue de l''Opéra, 75001 Paris', 'https://cdn.prod.website-files.com/69c11320dc2f4180f9b647cc/69c11320dc2f4180f9b64a86_printemps-mobile.webp'
WHERE NOT EXISTS (SELECT 1 FROM shops WHERE name = 'Printemps Paris');
