#!/bin/sh
set -eu

# Run through /bin/sh by the Compose provisioning service after PostgreSQL is healthy; safe to run again on every start.
#
# Without MIGRATION_DB_USERNAME (local development) the application account DB_USERNAME also owns the schema and the
# application migrates it at start. With MIGRATION_DB_USERNAME (production) the migration account owns the schema and
# runs the migrations as a deployment step; DB_USERNAME can then only read and write rows, never change the schema.
if [ -z "${MIGRATION_DB_USERNAME:-}" ]; then
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=app_user="$DB_USERNAME" --set=app_password="$DB_PASSWORD" \
    --set=db_name="$POSTGRES_DB" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'app_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')
\gexec
ALTER ROLE :"app_user" WITH LOGIN PASSWORD :'app_password';
GRANT CONNECT ON DATABASE :"db_name" TO :"app_user";
GRANT USAGE, CREATE ON SCHEMA public TO :"app_user";
SQL
exit 0
fi

if [ "$MIGRATION_DB_USERNAME" = "$DB_USERNAME" ]; then
    echo "MIGRATION_DB_USERNAME must differ from DB_USERNAME" >&2
    exit 1
fi

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=app_user="$DB_USERNAME" --set=app_password="$DB_PASSWORD" \
    --set=migration_user="$MIGRATION_DB_USERNAME" --set=migration_password="$MIGRATION_DB_PASSWORD" \
    --set=db_name="$POSTGRES_DB" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', name)
FROM (VALUES (:'app_user'), (:'migration_user')) AS wanted(name)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = name)
\gexec
ALTER ROLE :"migration_user" WITH LOGIN PASSWORD :'migration_password';
ALTER ROLE :"app_user" WITH LOGIN PASSWORD :'app_password';
GRANT CONNECT ON DATABASE :"db_name" TO :"migration_user", :"app_user";

-- The migration account owns the schema and everything in it, including tables an older setup let the application
-- account create.
ALTER SCHEMA public OWNER TO :"migration_user";
REASSIGN OWNED BY :"app_user" TO :"migration_user";

-- The application account: rows only. No CREATE on the schema, for it or for anybody else.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM :"app_user";
GRANT USAGE ON SCHEMA public TO :"app_user";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"app_user";
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO :"app_user";
-- Tables and sequences that later migrations create.
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_user" IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO :"app_user";
SQL
