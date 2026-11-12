#!/bin/sh
# Takes a backup now and then every BACKUP_INTERVAL_SECONDS (default 6 hours): pg_dump in the custom format, streamed into
# the restic repository (encrypted with RESTIC_PASSWORD), followed by the retention policy. After every successful run
# the time is written to /status/backup.prom for the "backup too old" alert (node-exporter textfile collector).
#
#   backup.sh          run forever (the compose service)
#   backup.sh once     one backup, then exit (before a deployment, see docs/operations/deploy.md)
set -eu

: "${PGHOST:?}" "${PGDATABASE:?}" "${PGUSER:?}" "${PGPASSWORD:?}" "${RESTIC_REPOSITORY:?}" "${RESTIC_PASSWORD:?}"
interval="${BACKUP_INTERVAL_SECONDS:-21600}"
status_dir="${BACKUP_STATUS_DIR:-/status}"

log() { echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) backup: $*"; }

write_status() {
    # name value: one gauge in the Prometheus text format, written atomically.
    tmp="$status_dir/.backup.prom.$$"
    {
        echo "# HELP hourforge_backup_last_success_timestamp_seconds Time of the last successful database backup."
        echo "# TYPE hourforge_backup_last_success_timestamp_seconds gauge"
        echo "hourforge_backup_last_success_timestamp_seconds $1"
        echo "# HELP hourforge_backup_last_size_bytes Size of the last database dump."
        echo "# TYPE hourforge_backup_last_size_bytes gauge"
        echo "hourforge_backup_last_size_bytes $2"
    } > "$tmp" && mv "$tmp" "$status_dir/backup.prom"
}

ensure_repository() {
    if ! restic cat config >/dev/null 2>&1; then
        log "initialising the repository"
        restic init
    fi
}

run_backup() {
    started=$(date +%s)
    # The dump goes straight into restic; pipefail is not in POSIX sh, so pg_dump's status is checked through a file.
    rm -f /tmp/pg_dump.failed
    summary=$({ pg_dump --format=custom --no-password || touch /tmp/pg_dump.failed; } \
        | restic backup --stdin --stdin-filename "$PGDATABASE.dump" --tag database --host hourforge --json | tail -n 1)
    snapshot=$(echo "$summary" | grep -o '"snapshot_id":"[^"]*"' | cut -d'"' -f4)
    if [ -e /tmp/pg_dump.failed ] || [ -z "$snapshot" ]; then
        log "FAILED: pg_dump or restic did not finish"
        # A snapshot of a broken dump must never be the one a restore picks.
        if [ -n "$snapshot" ]; then restic forget --quiet "$snapshot" || true; fi
        return 1
    fi
    size=$(echo "$summary" | grep -o '"total_bytes_processed":[0-9]*' | cut -d: -f2)
    restic forget --tag database --host hourforge --keep-daily "${BACKUP_KEEP_DAILY:-7}" \
        --keep-weekly "${BACKUP_KEEP_WEEKLY:-4}" --keep-monthly "${BACKUP_KEEP_MONTHLY:-6}" --prune --quiet
    write_status "$(date +%s)" "${size:-0}"
    log "snapshot $snapshot done in $(( $(date +%s) - started ))s, dump ${size:-?} bytes"
}

ensure_repository
if [ "${1:-}" = "once" ]; then
    run_backup
    exit
fi
while true; do
    run_backup || log "FAILED; the next attempt follows the schedule"
    sleep "$interval"
done
