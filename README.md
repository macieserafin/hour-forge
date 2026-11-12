<a id="readme-top"></a>

<div align="center">

# HourForge

Time tracking for small teams. Start a timer or add hours by hand, describe the work, and see where the team's time goes.

[![CI](https://github.com/macieserafin/hour-forge/actions/workflows/ci.yml/badge.svg)](https://github.com/macieserafin/hour-forge/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

[Run it yourself](docs/self-hosting.md) · [Develop](docs/development.md) · [Report a bug](https://github.com/macieserafin/hour-forge/issues/new?template=bug_report.md) · [Request a feature](https://github.com/macieserafin/hour-forge/issues/new?template=feature_request.md)

</div>

<details>
  <summary>Contents</summary>

- [About](#about)
- [Built with](#built-with)
- [Getting started](#getting-started)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Security](#security)
- [License](#license)

</details>

## About

HourForge records working time against projects. A person starts a timer, stops it and writes down what they did, or
adds hours for an earlier day. A team owner invites colleagues with a link, creates projects and reads the team's time
and history.

What you get:

- **Time entries** by timer or by hand, with a description, tags and reusable templates.
- **Teams and projects.** Owners, admins and members, joining by invitation link, one person in several teams.
- **Dashboards and reports** for one person and for a whole team, with charts and CSV, XLSX and PDF export.
- **Accounts that protect themselves.** Confirmed e-mail addresses, two-step sign-in, optional sign-in with Google, a
  list of active sessions and structured security events in the server log.
- **Your data stays yours.** Anyone can download a copy of their data or delete their account.
- **Jira Cloud** (optional). Each person connects their own Jira account and links time entries to issues.
- **An operator panel** for the person who runs the instance.

You host HourForge on your own server. Everything runs in containers: HTTPS, web app, API, PostgreSQL and encrypted
backups.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

### Built with

| Part | Technology |
|---|---|
| API | Java 21, Spring Boot 4, Spring Security, JPA, Flyway |
| Database | PostgreSQL 18 |
| Web app | React 19, TypeScript, Vite, TanStack Query |
| Tests | JUnit, Testcontainers, Vitest, Playwright |
| Delivery | Docker Compose, Caddy, restic, GitHub Actions, Trivy |

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Getting started

You need Docker with the Compose plugin and a free port 443. To try the whole stack on your computer:

```bash
git clone https://github.com/macieserafin/hour-forge.git
cd hour-forge/deploy
cp .env.example .env               # set the passwords; keep HOURFORGE_DOMAIN=localhost
cp backend.env.example backend.env
docker compose -f compose.yaml -f compose.try-locally.yaml up -d --build
```

Open <https://localhost> and accept Caddy's local certificate. Register an account; the confirmation e-mail arrives in
Mailpit at <http://127.0.0.1:8025>.

To run HourForge on a server, follow [Running HourForge yourself](docs/self-hosting.md). To change the code, start with
[Developing HourForge](docs/development.md).

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Documentation

| Guide | What it covers |
|---|---|
| [Running HourForge yourself](docs/self-hosting.md) | Requirements, settings, first start, updates. |
| [Developing HourForge](docs/development.md) | Local setup, tests, code checks, repository layout. |
| [Architecture](docs/architecture.md) | Modules, data flow, security model. |
| [Deploying a new version](docs/operations/deploy.md) | Backup, migration, start, short downtime. |
| [Rolling back](docs/operations/rollback.md) | Going back to the previous version, including migrations. |
| [Backups and restoring](docs/operations/restore.md) | Schedule, retention, recovery time, restore test. |
| [Rotating secrets](docs/operations/secrets-rotation.md) | Database, backup and encryption keys. |
| [Monitoring and alerts](docs/operations/monitoring.md) | Metrics, alert rules, what each alert means. |
| [Record of processing](docs/operations/processing-register.md) | The GDPR record to fill in, with the measures in place. |
| [Handling a data breach](docs/operations/data-breach.md) | Detect, contain, assess, report within 72 hours. |
| [E-mail that arrives](docs/email-deliverability.md) | Sender domain, SPF, DKIM, DMARC. |

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Contributing

Bug reports, ideas and pull requests are welcome. [CONTRIBUTING.md](CONTRIBUTING.md) explains how to set up, which
checks to run and how to write commit messages.

## Security

Report vulnerabilities privately, as described in [SECURITY.md](SECURITY.md). Do not open a public issue for them.

## License

HourForge is released under the [MIT License](LICENSE).

<p align="right">(<a href="#readme-top">back to top</a>)</p>
