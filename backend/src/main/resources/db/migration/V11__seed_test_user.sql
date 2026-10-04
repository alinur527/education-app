INSERT INTO users (
    email,
    password_hash,
    first_name,
    last_name,
    language,
    is_active
)
SELECT
    'test@ent.local',
    '$2a$10$7EqJtq98hPqEX7fNZaFWo.OhiM9Jd7dUe1koRaSPo6e7i0rGUNShC',
    'Test',
    'User',
    'ru',
    TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'test@ent.local'
);
