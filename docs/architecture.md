# Architecture

HourForge has three parts: a Spring Boot API, a React web app and a PostgreSQL database. Caddy sits in front of the API
and the web app in production. This page shows where code lives and which rules shape it.

## Overview

```
Browser ── HTTPS ──> Caddy ──┬── /api/*  ──> API (Spring Boot) ──> PostgreSQL
                             └── others  ──> Web app (static files, nginx)

API ──> SMTP server (required)        API ──> Jira Cloud, Google (optional)
backup service ──> PostgreSQL ──> restic repository (encrypted, off the server)
```

Only Caddy publishes ports. PostgreSQL has no published port and no route to the internet. Production runs one API
instance, because scheduled jobs assume a single running copy. [Running HourForge yourself](self-hosting.md) lists
the containers and their limits.

## API

The API is a modular monolith. Each package under `backend/src/main/java/pl/macieserafin/hourforge` owns one area, with
its controller, services, entities, repositories and request and response types together.

| Package | Area |
|---|---|
| `auth`, `identity`, `session`, `twofactor`, `google` | Registration, sign-in, e-mail confirmation, password reset, sessions, two-step sign-in, sign-in with Google. |
| `user`, `account`, `preferences`, `image` | Profile, personal data copy, account deletion, settings, profile pictures. |
| `team`, `invitation`, `project`, `tag`, `entrytemplate` | Teams and roles, invitation links, projects, tags, entry templates. |
| `timeentry`, `dashboard`, `report` | Timer, manual entries, corrections, dashboards, reports, CSV and PDF export. |
| `jira` | Optional Jira Cloud connection and issue links. |
| `notification`, `mail`, `jobs` | Notifications, e-mail outbox with retries, scheduled cleanup and reminders. |
| `operator` | Panel for the person who runs the instance. |
| `security`, `config`, `logging` | Filters, rate limits, bot check, security headers, JSON logs, security events. |

Rules that hold across packages:

- **Permissions are checked in the backend.** The web app hides buttons, but only the API decides.
  `TeamAccess` answers who may do what in a team; roles are owner, admin and member per team.
- **The schema belongs to Flyway.** Hibernate only validates it (`ddl-auto=validate`).
  Migrations are in `backend/src/main/resources/db/migration`.
- **Two database accounts in production.** The migration account owns the schema and runs migrations as a deployment
  step. The API account reads and writes rows and cannot change the schema.
- **Mail goes through a database outbox.** A change and its e-mail commit in one transaction. A delivery job sends
  pending mail every few seconds, retries with growing delays and marks a message `FAILED` after eight attempts.
- **Secrets at rest are encrypted.** Two-step sign-in secrets and Jira tokens use AES-256-GCM with versioned keys, so a
  key can rotate without locking anyone out. See [rotating secrets](operations/secrets-rotation.md).
- **Limits protect the instance.** Sign-in attempts, requests per client, request body size and parallel data exports
  have limits. Sign-in and request limits sit under `app.rate-limit` and the export limit under `app.data-export` in
  `application.properties`.
- **Logs contain no one-time tokens.** Redactors strip invitation, reset and OAuth values before logging.

The management endpoints (`health`, `info`, `prometheus`) run on a separate port, 9090, reachable only inside the
Compose network.

## Web app

The web app is a single-page React application built into static files. It talks to the API with
`fetch`, through the client in `frontend/src/lib/api.ts`.

| Path | Content |
|---|---|
| `frontend/src/app` | Application shell, routes, session, theme. |
| `frontend/src/features` | One folder per screen area: `auth`, `start`, `work`, `entries`, `history`, `projects`, `teams`, `reports`, `account`, `operator`, `help`. |
| `frontend/src/lib` | API calls and state helpers per area, shared by features. |
| `frontend/src/ui` | Shared components. |
| `frontend/src/brand`, `assets`, `content` | Logo, images, fonts and help texts. |
| `frontend/tests` | `unit`, `integration` (components, with the HTTP boundary controlled) and `e2e` (Playwright). |

The server answers with a strict content security policy and caching rules: `index.html` is never cached, hashed asset
files are cached for a long time.

## Testing strategy

Backend integration tests (`*IT`) start the Spring context against a PostgreSQL container, so they cover HTTP,
security, JPA, migrations and transactions together. Unit tests (`*Test`) cover plain logic. The frontend has Vitest for
logic and components and Playwright for the main flows against a real API. [Developing HourForge](development.md) lists
the commands.

## Delivery

GitHub Actions builds, tests and scans every change, then publishes images to GHCR tagged with the commit SHA when a
change reaches `main`. Deployment stays manual and follows [Deploying a new version](operations/deploy.md).
Dependabot proposes dependency updates every week.
