# Deploying a new version

Run on the server. One API instance runs at a time: its background jobs (retention, reminders, weekly reports, the mail
outbox) assume a single instance, so an update has a short interruption, usually under a minute. Sessions are stored in
the database, so nobody is signed out.

The server keeps a clone of this repository (for example `/opt/hourforge`) with its settings in `deploy/.env`,
`deploy/backend.env` and `deploy/monitoring/alertmanager.yml`, which Git ignores. CI publishes the images of every commit
of `main` to GHCR, tagged with the commit SHA.

## With the deployment script

**Read the release notes** first: a note says when a migration cannot be undone by going back to the previous image
(see [rollback](rollback.md)). Then deploy the commit, giving its full SHA:

```bash
sudo /opt/hourforge/deploy/deploy.sh <commit SHA>
```

The script takes only a 40-character SHA of a commit on `main`, and refuses to run while another deployment runs or when
files of the repository on the server were changed by hand. It then:

1. **Backs up** the database (`docker compose run --rm backup once`) and prints the snapshot ID. If the backup fails,
   nothing changes.
2. **Switches the repository files** (`compose.yaml`, `Caddyfile`, monitoring) to the commit and checks the `Caddyfile`
   with `caddy validate`.
3. **Sets the image tags** `BACKEND_IMAGE`, `FRONTEND_IMAGE` and `BACKUP_IMAGE` in `.env` to the commit. No other line of
   `.env` changes, and the file is never printed.
4. **Pulls and starts**: `docker compose pull` and `docker compose up -d`, with the monitoring profile when it runs.
   Compose runs `migrate` before it replaces the API. Caddy and Prometheus are recreated when their files changed.
5. **Checks** for up to 5 minutes (`DEPLOY_HEALTH_TIMEOUT`, in seconds) that every service is healthy, the backend,
   frontend and backup run the new images, and `https://<domain>/` and `/api/config` answer.

If a step after the backup fails, the script puts back the previous files and image tags, starts the previous version
and exits with an error. It does not restore the database: it prints the snapshot from step 1 for
[rollback](rollback.md). After a success it writes the commit to `/var/lib/hourforge-deploy/deployed-sha`, adds a line
to `history.log` in the same directory and prints the previous commit, which is the one to deploy to go back.

Running it again for the same commit is safe. The tests of the script (`bash deploy/tests/deploy-test.sh`, run in CI)
use fake `docker` and `curl` commands.

## Automatically after every green commit of `main`

The server can deploy by itself: a systemd timer runs `deploy/auto-deploy.sh` every two and a half minutes. It fetches
`main` and, when its newest commit is new, asks the public GitHub API (no token) whether the `CI` run of that push
passed and checks that the three images of the commit are in GHCR. Then it runs `deploy.sh` with that commit. GitHub
never connects to the server, so no key, port or secret of the server is on GitHub.

- Only the newest commit of `main` is deployed; commits merged in between may be skipped.
- CI still running: it waits and asks again on the next run. CI failed: the commit is never deployed.
- Every commit is tried once. When `deploy.sh` fails (and rolls back), the commit is recorded in `history.log` and not
  tried again: the next newer commit is, or you deploy by hand. A rollback made by hand stays until a newer commit
  arrives.
- From the moment it is switched on, **merging a pull request into `main` deploys it to production**. The checks in CI
  and reading the diff before merging are the only gate.

Install it once (as root, repository in `/opt/hourforge`):

```bash
sudo install -m 644 /opt/hourforge/deploy/systemd/hourforge-auto-deploy.service \
  /opt/hourforge/deploy/systemd/hourforge-auto-deploy.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now hourforge-auto-deploy.timer
```

It deploys nothing until the switch file exists:

```bash
sudo install -d -m 755 /etc/hourforge
sudo touch /etc/hourforge/auto-deploy.enabled   # on
sudo rm /etc/hourforge/auto-deploy.enabled      # off: nothing new is deployed, no change in the repository needed
```

What it did and why it waits: `journalctl -u hourforge-auto-deploy`. When a commit changes the unit files, install them
again with the commands above. The script also writes `/var/lib/hourforge-metrics/deploy.prom` (last successful
deployment, the deployed commit, a failed deployment, a green commit that waits); with the monitoring profile
node-exporter reads it and the alerts `DeploymentFailed` and `DeploymentWaiting` send an e-mail
([monitoring](monitoring.md)).

## By hand (when the script cannot be used)

Run in `deploy/`:

1. Read the release notes, as above.
2. **Back up now**, so the newest copy is from just before the migration:

   ```bash
   docker compose run --rm backup once
   ```

3. **Get the new images**: set `BACKEND_IMAGE`, `FRONTEND_IMAGE` and `BACKUP_IMAGE` in `.env` to the new tag (the commit
   SHA published by CI), switch the repository files to the same commit (`git checkout <sha>`), then
   `docker compose pull`. Building on the server instead: `git pull` and `docker compose build`.
4. **Migrate and restart**:

   ```bash
   docker compose up -d
   ```

   Compose runs `migrate` (with the migration account) before it replaces the API. If a migration fails, PostgreSQL
   rolls it back, Compose stops before touching the running API (the old version keeps working on the old schema) and
   `docker compose logs migrate` says why. A changed `Caddyfile` needs `docker compose up -d --force-recreate caddy`.
5. **Check**: `docker compose ps` shows every service healthy; sign in; the `X-Request-Id` of a request can be found in
   `docker compose logs backend`. Note the previous image tag for a rollback.

## Migrations that keep a rollback possible

Write schema changes in two releases (expand, then contract): first add new columns or tables that the old version
ignores, release; remove or rename only in a later release, once no running version needs the old shape. A migration
that cannot be reversed this way must say so in the release notes. Migrations run in a transaction; avoid statements
that cannot (for example `CREATE INDEX CONCURRENTLY`) or put them in their own migration.

The API has a 30-second limit per query (`DB_STATEMENT_TIMEOUT_MS`); migrations run with their own account and have no
such limit.
