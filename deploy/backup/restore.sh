#!/bin/sh
# Restores a snapshot (default: the latest) into the database named by PGDATABASE, replacing its contents.
# Run with the backend stopped (see docs/operations/restore.md):
#   docker compose stop backend
#   docker compose run --rm --entrypoint restore.sh backup [snapshot-id]
#   docker compose up -d
# Objects are restored with their owner and grants, so the migration and application accounts keep their roles.
set -eu

: "${PGHOST:?}" "${PGDATABASE:?}" "${PGUSER:?}" "${PGPASSWORD:?}" "${RESTIC_REPOSITORY:?}" "${RESTIC_PASSWORD:?}"
snapshot="${1:-latest}"

echo "Restoring snapshot $snapshot of $PGDATABASE into $PGHOST/$PGDATABASE as $PGUSER."
started=$(date +%s)
restic dump --tag database "$snapshot" "/$PGDATABASE.dump" > /tmp/restore.dump
pg_restore --clean --if-exists --no-password --exit-on-error --single-transaction --dbname "$PGDATABASE" /tmp/restore.dump
rm -f /tmp/restore.dump
echo "Restored in $(( $(date +%s) - started ))s."
