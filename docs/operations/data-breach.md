# Handling a personal data breach

A personal data breach is a security incident that destroys, loses, changes or exposes personal data, or gives someone
unauthorised access to it. Examples: a leaked database or backup, a stolen `.env`, an account takeover, a bug that shows
one person's data to another.

The GDPR gives the controller **72 hours** from the moment it becomes aware of a breach to report it to the supervisory
authority, unless the breach is unlikely to put people at risk. In Poland that is the UODO. This page is the checklist
for the person who runs the instance. Keep notes with timestamps from the first step.

## 1. Detect and record

Signals: a report sent as described in [SECURITY.md](../../SECURITY.md), an alert from [monitoring](monitoring.md), odd
lines in the security log, a message from a provider.

1. Write down the time you learned about it. The 72 hours start here.
2. Open a private incident note: what you know, who knows, every action with its time.

## 2. Contain

Stop the damage first; investigate after.

| Situation | Action |
|---|---|
| Account takeover | Block the account in the operator panel (`/operator`). The person resets the password and checks the active sessions. |
| Secret exposed (`.env`, `backend.env`, backup password, API keys) | Rotate it following [Rotating secrets](secrets-rotation.md). Rotate the database passwords and the Google and Jira client secrets too, if they were in the same place. |
| Server or container compromised | Take the instance offline (`docker compose stop caddy`), keep the disks and logs, rebuild the server from a clean image and restore from a backup made before the incident ([Backups and restoring](restore.md)). |
| Backup repository exposed | The data is encrypted with `BACKUP_PASSWORD`. If the password also leaked, treat the data as exposed. Create a new repository with a new password. |
| Application bug | Deploy a fix or switch the feature off. See [Deploying a new version](deploy.md) and [Rolling back](rollback.md). |

## 3. Assess

Answer these questions from the logs, with the dates:

- What data was involved (accounts, work entries, sign-in history, tokens) and how many people?
- Was it only read, or also changed or deleted?
- Since when, and until when?
- Who could use it, and what could they do with it?

Where to look:

```bash
# Security events: sign-ins, password and address changes, membership, deletions, operator actions
docker compose logs backend | grep '"logger":"security"'

# One request, from the identifier a person reported or from the Caddy log
docker compose logs backend | grep '<request id>'
```

Each security event has an `event` name, the `actor` account ID, the affected IDs and the client `ip`. Logs rotate by
size, so export the relevant lines at once. Sign-in history in the database covers the last 90 days.

Rate the risk to people. A breach of encrypted backups with an unexposed key is unlikely to put anyone at risk. Exposed
work entries with names and descriptions can.

## 4. Report to the authority (within 72 hours)

Report unless the breach is unlikely to put people at risk. Use the UODO form at <https://uodo.gov.pl> (section on
reporting personal data breaches). Include:

- the nature of the breach, with the categories and the approximate number of people and records,
- the contact details of the person who answers questions (see [SECURITY.md](../../SECURITY.md) and the privacy policy),
- the likely consequences,
- what you did and plan to do.

If you cannot give everything within 72 hours, report what you have and send the rest later, and explain the delay.

## 5. Tell the people affected

If the breach is likely to put people at high risk, tell them without undue delay, in plain language: what happened,
what data, what it may mean for them, what you did and what they should do (for example, change the password). Use the
instance's mail provider or a notice in the application. Owners of affected teams should hear about it too.

If you process data for an organisation that runs its own team (an employer, a client), tell that organisation at once,
because it may have its own duty to report.

## 6. Close and learn

- Record the breach in your breach register even when you did not report it: facts, effects, actions, the reasoning.
- Fix the cause, add a test or an alert, and update the documentation.
- Check that the privacy policy and the [list of subprocessors](../../frontend/src/content/legal.ts) still describe reality.

## Contacts to fill in before the instance opens

Keep the real values in a private note, not in the repository:

- the person who decides and reports (name, phone, e-mail),
- the hosting provider's support and abuse contact,
- the mail provider's security contact,
- the lawyer or data protection adviser, if you have one.
