-- Drop foreign key constraint between shops and users
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_shops_user'
    ) THEN
        ALTER TABLE shops DROP CONSTRAINT fk_shops_user;
    END IF;
END $$;

-- Drop users shadow table
DROP TABLE IF EXISTS users;
