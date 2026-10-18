#!/bin/sh
set -eu

# Only runs when PostgreSQL initializes an empty data directory.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=app_user="$DB_USERNAME" --set=app_password="$DB_PASSWORD" \
    --set=db_name="$POSTGRES_DB" <<'SQL'
CREATE USER :"app_user" WITH PASSWORD :'app_password';
GRANT CONNECT ON DATABASE :"db_name" TO :"app_user";
GRANT USAGE, CREATE ON SCHEMA public TO :"app_user";
SQL
