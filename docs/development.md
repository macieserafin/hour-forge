# Developing HourForge

This guide gets you from a fresh clone to a running copy with tests passing. To run HourForge for real use, read
[Running HourForge yourself](self-hosting.md) instead.

## Requirements

| Tool | Version | Used for |
|---|---|---|
| JDK | 21 | API and its tests |
| Node.js | 22.12 or newer (CI uses 24) | Web app |
| Docker with Compose | recent | Development database, mail server, integration tests |

The Maven wrapper (`backend/mvnw`) downloads Maven. You need no other global tools.

## Run the API and the web app

Start PostgreSQL and a development mail server. Mailpit catches every e-mail the API sends.

```bash
cd backend
printf 'POSTGRES_PASSWORD=%s\nDB_PASSWORD=%s\n' "$(openssl rand -hex 12)" dev-password > .env
docker compose up -d
```

PostgreSQL listens on `127.0.0.1:55432`, SMTP on `1025`, and the Mailpit inbox is at <http://127.0.0.1:8025>. The
`.env` file stays out of Git.

Start the API in a second terminal. The API reads its settings from environment variables:

```bash
cd backend
export DB_PASSWORD=dev-password SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025 MAIL_STARTTLS=false
./mvnw spring-boot:run
```

Flyway creates the schema at the first start. The API listens on port 8080 and its health endpoints on 9090.

Start the web app in a third terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open <http://127.0.0.1:5173>. Vite forwards `/api` to `http://localhost:8080`; set `BACKEND_URL` to use another address.
Register an account and read the confirmation e-mail in Mailpit.

## Real e-mail instead of Mailpit

Mailpit stays the default, and the tests never use a real server. To see real confirmation and reset messages in your
own mailbox, switch the API to the `smtp` profile. It sends through your mail account, always over STARTTLS with a
checked certificate, and only to the addresses you list.

1. Copy the example and fill in the password and the address that may receive test mail. `.env.smtp` sits next to
   `.env` in the repository root and stays out of Git (`git check-ignore -v .env.smtp` names the rule):

   ```bash
   cp .env.smtp.example .env.smtp
   chmod 600 .env.smtp
   ```

   Then open `.env.smtp` in your editor and set `SPRING_MAIL_PASSWORD` and `MAIL_ALLOWED_RECIPIENTS`.
   `MAIL_ALLOWED_RECIPIENTS` is required. Mail to any other address, including older test registrations still waiting
   in the local outbox, never reaches the real server. It stays in the outbox like undelivered mail: switch back to
   Mailpit within about two hours and it arrives there, later it is marked failed.

2. Start the database and Mailpit as above, then start the API with the profile. The variables named after `env -u`
   might still be exported in your shell, and an exported variable would win over `.env.smtp`:

   ```bash
   cd backend
   env -u SPRING_MAIL_HOST -u SPRING_MAIL_PORT -u SPRING_MAIL_USERNAME -u SPRING_MAIL_PASSWORD -u MAIL_STARTTLS -u MAIL_FROM -u MAIL_ALLOWED_RECIPIENTS SPRING_PROFILES_ACTIVE=smtp ./mvnw spring-boot:run
   ```

   Start the web app with `npm run dev` and open <http://localhost:5173>. The links in the messages use `FRONTEND_URL` from
   `.env` (`http://localhost:5173`).

3. To go back to Mailpit, stop the API and start it the usual way, without `SPRING_PROFILES_ACTIVE=smtp`.

**Check the log and the outbox.** Each failed attempt logs `E-mail delivery failed` with the subject and the error
type, never the address. `RecipientNotAllowedException` means the address is not listed, `MailAuthenticationException`
a wrong login or password, and `MailSendException` no connection, no STARTTLS or a certificate for another host name.
The outbox is a table, and a sent message disappears from it:

```bash
docker compose exec postgres psql -U postgres -d hourforge -c "select recipient, subject, status, attempts, last_error, next_attempt_at from outgoing_emails order by created_at"
```

Run this from the repository root, where `.env` sets `COMPOSE_FILE`. To drop the old test messages that are still
waiting, run `delete from outgoing_emails where status = 'PENDING'` the same way.

**Test the flows** with an address from `MAIL_ALLOWED_RECIPIENTS`:

1. Register at <http://localhost:5173/register>. The message "Potwierdź adres e-mail w HourForge" arrives within a few
   seconds. Its link opens `http://localhost:5173/verify-email/…` and confirms the account.
2. Sign out, choose "Nie pamiętasz hasła?" (<http://localhost:5173/forgot-password>) and enter the same address. The message "Reset hasła w HourForge" links to
   `http://localhost:5173/reset-password/…`. Set a new password and sign in with it. The same link does not work twice.
3. In the received message, "Show original" (Gmail) or the headers should show `SPF: PASS`, `DKIM: PASS` and
   `DMARC: PASS` for the sender's domain.

**The whole production stack** (Caddy, `https://localhost`) can use the same `.env.smtp` instead of Mailpit. In
`deploy/`, with `HOURFORGE_DOMAIN=localhost` in `.env` and a filled-in `backend.env`, run:

```bash
docker compose -f compose.yaml -f compose.try-smtp.yaml up -d --build
```

The links then open `https://localhost`. Check with `docker compose logs backend` and switch back with
`compose.try-locally.yaml` instead of `compose.try-smtp.yaml`.

## Optional integrations

Jira, sign-in with Google, the bot check and two-step sign-in stay off until you set their variables. The names and
meaning are in the [settings table](self-hosting.md#settings). Use test credentials from your own developer accounts and
keep them in your shell or an untracked file.

## Tests and checks

| Where | Command | Covers |
|---|---|---|
| `backend` | `./mvnw test` | Unit tests (`*Test`), no Spring and no database. |
| `backend` | `./mvnw verify` | Unit and integration tests (`*IT`) against PostgreSQL in Testcontainers. Docker must run. |
| `frontend` | `npm run lint` | Biome. `npm run format` fixes what it can. |
| `frontend` | `npm run typecheck` | TypeScript. |
| `frontend` | `npm test` | Vitest: unit tests in `tests/unit`, component tests in `tests/integration`. |
| `frontend` | `npm run build` | Type check and production build. |
| `frontend` | `npm run test:e2e` | Playwright against a real API and database. |

Run one end-to-end flow on the desktop layout:

```bash
cd frontend
npm run test:e2e -- --project=desktop tests/e2e/flows.spec.ts
```

CI runs the same checks on every pull request, plus a scan of dependencies, secrets and container images. See
[`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

Write tests for observable behavior: results, errors, limits and permissions. Reproduce a bug in a test before you fix
it. Tests that need a database use PostgreSQL, not an in-memory substitute.

## Repository layout

```
backend/    Spring Boot API (Maven)
frontend/   React web app (Vite)
deploy/     Compose files, Caddy, backup scripts, monitoring rules
docs/       Guides for hosting, operations and development
.github/    CI workflow, Dependabot, issue and pull request templates
```

[Architecture](architecture.md) describes the modules inside `backend/` and `frontend/`.

## Database changes

Migrations live in `backend/src/main/resources/db/migration` as `V<number>__<name>.sql`. Never edit a migration that
reached `main`; add a new one. The production API signs in with an account that cannot change the schema, so every
schema change goes through a migration. Make changes in two steps (add, then remove) when the old version still runs
during a deployment. [Deploying a new version](operations/deploy.md) explains why.

## Pull requests

[CONTRIBUTING.md](../CONTRIBUTING.md) lists the steps and the commit message format.
