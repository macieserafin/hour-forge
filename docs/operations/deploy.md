# Deploying a new version

Run on the server, in `deploy/`. One API instance runs at a time: its background jobs (retention, reminders, weekly
reports, the mail outbox) assume a single instance, so an update has a short interruption, usually under a minute.
Sessions are stored in the database, so nobody is signed out.

## Steps

1. **Read the release notes** of the new version: a note says when a migration cannot be undone by going back to the
   previous image (see [rollback](rollback.md)).
2. **Back up now**, so the newest copy is from just before the migration:

   ```bash
   docker compose run --rm backup once
   ```

3. **Get the new images**: set `BACKEND_IMAGE`, `FRONTEND_IMAGE` and `BACKUP_IMAGE` in `.env` to the new tag (the commit
   SHA published by CI), then `docker compose pull`. Building on the server instead: `git pull` and
   `docker compose build`.
4. **Migrate and restart**:

   ```bash
   docker compose up -d
   ```

   Compose runs `migrate` (with the migration account) before it replaces the API. If a migration fails, PostgreSQL
   rolls it back, Compose stops before touching the running API (the old version keeps working on the old schema) and
   `docker compose logs migrate` says why.
5. **Check**: `docker compose ps` shows every service healthy; sign in; the `X-Request-Id` of a request can be found in
   `docker compose logs backend`. Note the previous image tag for a rollback.

## Migrations that keep a rollback possible

Write schema changes in two releases (expand, then contract): first add new columns or tables that the old version
ignores, release; remove or rename only in a later release, once no running version needs the old shape. A migration
that cannot be reversed this way must say so in the release notes. Migrations run in a transaction; avoid statements
that cannot (for example `CREATE INDEX CONCURRENTLY`) or put them in their own migration.

The API has a 30-second limit per query (`DB_STATEMENT_TIMEOUT_MS`); migrations run with their own account and have no
such limit.
