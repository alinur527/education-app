ALTER TABLE users
ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'STUDENT';

UPDATE users
SET role = 'ADMIN'
WHERE email = 'test@ent.local';
