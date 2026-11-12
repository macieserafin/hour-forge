# Running HourForge yourself

HourForge runs as a small set of containers on one server: Caddy (HTTPS), the web app, the API, PostgreSQL and a
backup service, optionally with monitoring. Everything is in [`deploy/`](../deploy).

## Requirements

- A Linux server with Docker Engine and the Compose plugin; 2 CPU cores and 4 GB of memory fit the default limits.
- A domain whose DNS `A`/`AAAA` records point at the server, with ports 80 and 443 open. Caddy gets and renews the
  Let's Encrypt certificate by itself.
- **A mail server (SMTP) is required.** People confirm their address and reset their password by links sent by e-mail;
  the API does not start without `SPRING_MAIL_HOST`. Use a mail provider rather than sending from the server itself,
  see [e-mail deliverability](email-deliverability.md).
- Somewhere off the server for backups (another server, or S3-compatible object storage), see [restore](operations/restore.md).

## Try it on your computer first

```bash
cd deploy
cp .env.example .env               # set the passwords; keep HOURFORGE_DOMAIN=localhost
cp backend.env.example backend.env
docker compose -f compose.yaml -f compose.try-locally.yaml up -d --build
```

Open <https://localhost> (Caddy's own certificate: accept it in the browser) and register; the confirmation e-mail is in
Mailpit at <http://127.0.0.1:8025>. `compose.try-locally.yaml` only adds that test mail server: never use it on a server.

## First start on a server

1. Copy `deploy/` to the server, then fill in the two settings files. Never commit them.
   - `.env` (read by Compose only): `HOURFORGE_DOMAIN`, the database passwords (`DB_PASSWORD`, `MIGRATION_DB_PASSWORD`,
     `POSTGRES_PASSWORD`), `BACKUP_PASSWORD` and, for an off-site copy, `BACKUP_REPOSITORY` with its keys.
     Long random values, e.g. `openssl rand -hex 24`.
   - `backend.env` (the API's settings): the mail server (`SPRING_MAIL_*`, `MAIL_FROM`), `TWO_FACTOR_ENCRYPTION_KEY`
     (`openssl rand -base64 32`) and `OPERATOR_EMAILS`.
2. Start: `docker compose up -d --build` (or set `BACKEND_IMAGE`/`FRONTEND_IMAGE`/`BACKUP_IMAGE` to published images and
   `docker compose pull` first). Compose creates the database accounts, runs the migrations (`migrate`), then starts the
   API, the web app and Caddy once each is healthy.
3. Check: `docker compose ps` shows every service healthy; `https://<domain>` opens; registration mails arrive.
4. Register the account(s) listed in `OPERATOR_EMAILS`: they can open the operator panel at `/operator`.

What Compose sets up for you: only Caddy publishes ports; the database has no port and no internet access; the API signs
in to the database with an account that can read and write rows but never change the schema (migrations use a
separate account); HTTPS with HSTS, a strict content security policy and a `Secure` session cookie; logs as JSON lines
without one-time tokens.

## Settings

| Setting | Where | Notes |
|---|---|---|
| `HOURFORGE_DOMAIN` | `.env` | The public address; `FRONTEND_URL` and sign-in callbacks follow from it. |
| `DB_PASSWORD`, `MIGRATION_DB_PASSWORD`, `POSTGRES_PASSWORD` | `.env` | Application, migration and administrator accounts of PostgreSQL. |
| `BACKUP_PASSWORD`, `BACKUP_REPOSITORY` | `.env` | Encryption of the backups and where they go. |
| `BACKEND_MEMORY`, `POSTGRES_MEMORY` … | `.env` | Container limits; defaults fit a 4 GB server. |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `MAIL_STARTTLS`, `MAIL_FROM` | `backend.env` | Required. |
| `TWO_FACTOR_ENCRYPTION_KEY`, `TWO_FACTOR_ENCRYPTION_KEY_VERSION` | `backend.env` | Without a key, two-step sign-in cannot be turned on. |
| `OPERATOR_EMAILS` | `backend.env` | Comma-separated confirmed addresses allowed into `/operator`. |
| `JIRA_INTEGRATION_ENABLED`, `JIRA_*` | `backend.env` | Optional, off by default. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | `backend.env` | Optional sign-in with Google, off while the ID is empty. |
| `TURNSTILE_SITE_KEY`, `TURNSTILE_SECRET_KEY` | `backend.env` | Optional bot check on registration, off while either is empty. |
| `GEOIP_DATABASE` | `backend.env` | Optional local IP location file for sign-in history. |
| `DB_STATEMENT_TIMEOUT_MS` | `backend.env` | Longest database query, default 30000. |

Optional features stay off until configured: Jira, Google sign-in and Turnstile each need their own keys from the
provider; the comments in `backend.env.example` say where to get them.

## Keys you must not lose

`TWO_FACTOR_ENCRYPTION_KEY` encrypts the authenticator secrets and `JIRA_TOKEN_ENCRYPTION_KEY` the Jira tokens. They are
not in the database backup on purpose. Losing the two-step key locks everybody who uses an authenticator app out of it:
they can still sign in with backup codes, or the operator turns two-step sign-in off for them. Losing the Jira key
disconnects Jira for everybody until they connect again. Keep both keys and `BACKUP_PASSWORD` in a password manager or
another safe place, **separately from the backups**. To change a key, see [rotating secrets](operations/secrets-rotation.md).

## Updating

Follow [deploying a new version](operations/deploy.md): back up, run the migrations, restart. One API instance runs at a
time, so an update means a short interruption (usually under a minute).

## Backups and monitoring

The `backup` service takes an encrypted backup every 6 hours and keeps 7 daily, 4 weekly and 6 monthly copies. Test the
restore regularly: [restore](operations/restore.md). For alerts (server down, server errors, disk, backups, mail), see
[monitoring](operations/monitoring.md).
