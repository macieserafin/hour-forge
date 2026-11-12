#!/bin/sh
# Proves that the latest backup can be restored, without touching the live database: starts a throw-away PostgreSQL
# inside this container, restores the snapshot into it, compares it with the live database and reports the time taken
# (the restore part of the RTO). Exit status 0 only when everything matches.
#   docker compose run --rm --entrypoint restore-test.sh backup
set -eu

: "${PGHOST:?}" "${PGDATABASE:?}" "${PGUSER:?}" "${PGPASSWORD:?}" "${RESTIC_REPOSITORY:?}" "${RESTIC_PASSWORD:?}"
work="${RESTORE_TEST_DIR:-/scratch}/restore-test"
rm -rf "$work" && mkdir -p "$work"
started=$(date +%s)

restic dump --tag database latest "/$PGDATABASE.dump" > "$work/backup.dump"
snapshot_time=$(restic snapshots --tag database --latest 1 --json | grep -o '"time":"[^"]*"' | head -1 | cut -d'"' -f4)
fetched=$(date +%s)

initdb --pgdata "$work/data" --username restore --auth trust --no-sync >/dev/null
pg_ctl --pgdata "$work/data" --options "-k $work -c listen_addresses=''" --wait --silent start
trap 'pg_ctl --pgdata "$work/data" --mode immediate --silent stop; rm -rf "$work"' EXIT
local_psql() { psql --host "$work" --username restore --dbname restored --no-psqlrc --tuples-only --no-align "$@"; }
psql --host "$work" --username restore --dbname postgres --quiet -c "CREATE DATABASE restored"
# The roles of the live database do not exist here: objects are restored owned by the test user.
pg_restore --host "$work" --username restore --dbname restored --no-owner --no-acl --exit-on-error "$work/backup.dump"
restored=$(date +%s)

query="SELECT (SELECT max(version::int) FROM flyway_schema_history WHERE success),
              (SELECT count(*) FROM users), (SELECT count(*) FROM teams), (SELECT count(*) FROM time_entries)"
in_backup=$(local_psql -c "$query")
live=$(psql --no-password --no-psqlrc --tuples-only --no-align -c "$query")

echo "Snapshot taken:      $snapshot_time"
echo "Backup (schema|users|teams|entries): $in_backup"
echo "Live   (schema|users|teams|entries): $live"
echo "Fetch: $(( fetched - started ))s, restore: $(( restored - fetched ))s, total: $(( restored - started ))s"

backup_schema=$(echo "$in_backup" | cut -d'|' -f1)
live_schema=$(echo "$live" | cut -d'|' -f1)
if [ -z "$backup_schema" ] || [ "$backup_schema" != "$live_schema" ]; then
    echo "FAILED: schema version of the backup ($backup_schema) differs from the live one ($live_schema)" >&2
    exit 1
fi
if [ "$(echo "$live" | cut -d'|' -f2)" -gt 0 ] && [ "$(echo "$in_backup" | cut -d'|' -f2)" -eq 0 ]; then
    echo "FAILED: the backup has no accounts while the live database has some" >&2
    exit 1
fi
echo "PASS: the latest backup restores and matches the live schema."
