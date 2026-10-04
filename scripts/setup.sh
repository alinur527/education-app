#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -e .env ]; then echo '.env already exists; preserved.'; exit 0; fi
umask 077
db_secret=$(openssl rand -hex 32)
jwt_secret=$(openssl rand -hex 48)
cat > .env <<EOF
DB_USERNAME=education
DB_PASSWORD=$db_secret
DB_NAME=education
DB_PORT=55432
DB_URL=jdbc:postgresql://localhost:55432/education
APP_JWT_SECRET=$jwt_secret
APP_JWT_EXPIRATION_MS=86400000
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
EOF
echo 'Created local .env with random secrets. PostgreSQL port: 55432.'
