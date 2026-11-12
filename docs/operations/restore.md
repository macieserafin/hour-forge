# Backups and restoring

## What is backed up

The `backup` service runs `pg_dump` of the application database every 6 hours (`BACKUP_INTERVAL_SECONDS`) and stores it
in a [restic](https://restic.net) repository, encrypted with `BACKUP_PASSWORD`. It keeps 7 daily, 4 weekly and 6 monthly
copies (`BACKUP_KEEP_DAILY`, `BACKUP_KEEP_WEEKLY`, `BACKUP_KEEP_MONTHLY`). After each success it writes the time to a
status file that the `BackupTooOld` alert reads.

The repository is a local volume by default, which does not survive the loss of the server. For a copy off the server,
set `BACKUP_REPOSITORY` to object storage, e.g. `s3:https://s3.eu-central-003.backblazeb2.com/<bucket>/hourforge`, with
`BACKUP_S3_ACCESS_KEY_ID` and `BACKUP_S3_SECRET_ACCESS_KEY`. Not in the backup, and kept elsewhere on purpose: the
encryption keys of the application (`TWO_FACTOR_ENCRYPTION_KEY`, `JIRA_TOKEN_ENCRYPTION_KEY`) and `BACKUP_PASSWORD`.

## Recovery objectives

- **RPO (data that can be lost): at most 6 hours**, the backup interval; a backup also runs before every deployment.
  Shorten `BACKUP_INTERVAL_SECONDS` for a smaller loss.
- **RTO (time to be back): about 1 hour** on a working server, most of it noticing the problem and deciding. Measured
  locally on 2026-11-12 with 100,000 time entries (39 MB database, 3.4 MB dump): backup 4 s, restore 2 s, the stack
  healthy again 13 s later. The restore grows with the database; run the restore test to know your own figure.
  On a new server add its setup (Docker, the `deploy/` files, DNS); plan half a day.

## Test the restore (monthly, and after changing the backup settings)

```bash
docker compose run --rm --entrypoint restore-test.sh backup
```

It restores the latest backup into a throw-away database inside the backup container, compares the schema version and
row counts with the live database and prints how long the restore took. The live database is not touched. It ends
with `PASS` or an explanation.

## Restore into the live database

This replaces the current data with the snapshot.

```bash
docker compose stop backend
docker compose run --rm --entrypoint restic backup snapshots         # choose a snapshot, or use "latest"
docker compose run --rm --entrypoint restore.sh backup <snapshot-id>
docker compose up -d
```

## On a new server

1. Install Docker, copy `deploy/` with the same `.env` and `backend.env` (the same keys and `BACKUP_PASSWORD`).
2. `docker compose up -d postgres postgres-init` to create an empty database and its accounts.
3. Restore as above (`restore.sh`), then `docker compose up -d`.
4. Point DNS at the new server; Caddy gets a new certificate on the first request.
