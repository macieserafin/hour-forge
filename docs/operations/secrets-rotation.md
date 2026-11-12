# Rotating secrets

Change a secret when it may have leaked, when a person with access leaves, and otherwise once a year. After each change
run `docker compose up -d` (Compose restarts what uses it) and check that the stack is healthy.

| Secret | How |
|---|---|
| `DB_PASSWORD`, `MIGRATION_DB_PASSWORD` | Change in `.env`, then `docker compose up -d`: `postgres-init` sets the new passwords before `migrate` and the API start. |
| `POSTGRES_PASSWORD` | `docker compose exec postgres psql -U postgres -c "ALTER ROLE postgres PASSWORD '<new>'"`, then change `.env`. |
| `BACKUP_PASSWORD` | `docker compose run --rm --entrypoint restic backup key add` (asks for the new password), change `.env`, restart `backup`, then remove the old key with `restic key list` / `restic key remove <id>`. |
| SMTP password | Create a new one at the provider, change `backend.env`, `docker compose up -d backend`, then revoke the old one. |
| `GOOGLE_CLIENT_SECRET` | Add a new secret to the OAuth client in the Google Cloud console, change `backend.env`, restart the API, delete the old secret. |
| `JIRA_CLIENT_SECRET` | Rotate it in the Atlassian developer console, change `backend.env`, restart the API. |
| `TURNSTILE_SECRET_KEY` | Rotate it in the Cloudflare dashboard, change `backend.env`, restart the API. |
| `TWO_FACTOR_ENCRYPTION_KEY` | See below. |
| `JIRA_TOKEN_ENCRYPTION_KEY` | See below. |

## Encryption keys of the application

Both keys work the same way: the active key encrypts, older keys are kept only to read what they encrypted.

1. Generate a new key: `openssl rand -base64 32`.
2. In `backend.env`, move the current key to a "previous" setting under its version, set the new key and raise the version:

   ```
   TWO_FACTOR_ENCRYPTION_KEY=<new key>
   TWO_FACTOR_ENCRYPTION_KEY_VERSION=v2
   APP_TWOFACTOR_PREVIOUSENCRYPTIONKEYS_V1=<old key>
   ```

   For Jira: `JIRA_TOKEN_ENCRYPTION_KEY`, `JIRA_TOKEN_ENCRYPTION_KEY_VERSION` and `APP_JIRA_PREVIOUSENCRYPTIONKEYS_V1`.
3. `docker compose up -d backend`. A malformed key stops the start with a clear message.
   - Two-step sign-in: at start, every authenticator secret is rewritten with the new key ("Two-step sign-in secrets
     rewritten" in the log). The old key can be removed after this start.
   - Jira: each token is rewritten the next time it is used; keep the old key until people have used Jira at least once
     (or reconnected).
4. Store the new key next to the old one in your password manager; remove the old one from `backend.env` once it is no
   longer needed.

If an encryption key is lost: people with two-step sign-in use their backup codes, or the operator turns it off for
them in `/operator`; Jira connections have to be made again.
