# Monitoring and alerts

## Health, metrics and logs

- The API answers health checks and Prometheus metrics on a separate management port (9090) inside the Docker network:
  `/actuator/health/readiness`, `/actuator/health/liveness`, `/actuator/prometheus`. The port is never published and
  Caddy does not route to it.
- Logs are JSON lines (`docker compose logs backend`). Each request has an identifier set by Caddy (`X-Request-Id`),
  returned to the browser and present as `requestId` in every API log line, so an error a person reports can be found.
- Security events (sign-ins, sign-outs, password and address changes, two-step sign-in, Google linking, team membership
  and roles, invitations, deletions and operator actions) are lines from the `security` logger with `event`, `actor`
  (account ID), the affected IDs and the client `ip`; never e-mail addresses, tokens or passwords:

  ```bash
  docker compose logs backend | grep '"logger":"security"'
  ```

## Alerts

The optional `monitoring` profile adds Prometheus, Alertmanager and node-exporter. Rules are in
`deploy/monitoring/alerts.yml`, with tests in `alerts.test.yml`:

| Alert | When |
|---|---|
| `BackendDown` | The API does not answer for 2 minutes. |
| `HighServerErrorRate` | More than 5% of API requests end with a 5xx error for 10 minutes. |
| `DiskSpaceLow` / `DiskSpaceCritical` | Less than 15% / 5% free on a disk of the server. |
| `BackupTooOld` / `BackupStatusMissing` | No successful backup for 13 hours / never. |
| `MailNotDelivered` / `MailBacklog` | E-mails failed after all attempts / more than 50 waiting for 30 minutes. |

To switch it on:

```bash
cp monitoring/alertmanager.yml.example monitoring/alertmanager.yml   # fill in the SMTP account and your address
docker compose --profile monitoring up -d
```

Prometheus (<http://127.0.0.1:9091>) and Alertmanager (<http://127.0.0.1:9093>) listen on the server only; open them
through an SSH tunnel, e.g. `ssh -L 9091:127.0.0.1:9091 <server>`.

These alerts come from the server itself, so they cannot tell you that the whole server is down. Add an external
uptime check of `https://<domain>/api/config` (any uptime service) for that.
