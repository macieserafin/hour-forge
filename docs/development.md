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
