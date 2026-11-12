# Record of processing activities

Article 30 of the GDPR asks a controller that processes personal data regularly to keep a record of what it processes.
This page is the template for the person who runs an instance. Fill it in, keep the copy private and update it when
something changes. A supervisory authority can ask to see it.

It is a record for you, not a public text. The public side is the privacy policy in the application.

## Controller

| Item | Value |
|---|---|
| Name and address | the values in `frontend/src/content/instance.ts` |
| Contact for privacy requests | the same contact e-mail |
| Data protection officer | none required for a small instance |

## Processing activities

| Activity | Data | People | Purpose and basis | Recipients | Retention |
|---|---|---|---|---|---|
| Accounts | Display name, e-mail address, password hash, optional picture, settings | Users | Provide the service (contract) | Hosting, backup storage, mail provider | Until account deletion, then anonymised |
| Work records | Time entries, descriptions, projects, tags, teams, roles, invitations, Jira issue keys | Users and members of their teams | Provide the service (contract) | Hosting, backup storage; team owners and admins see team entries | Until account deletion; entries stay in team reports without identifying data |
| Account security | Sessions, sign-in history (browser, system, shortened IP, approximate place), two-step sign-in secrets (encrypted) | Users | Keep the service and accounts safe (legitimate interest) | Hosting | Sign-in history 90 days; sessions until they expire |
| Security log | Event name, account ID, affected IDs, IP address | Users and visitors | Detect abuse and answer breach questions (legitimate interest, legal obligation) | Hosting | Log rotation by size |
| E-mail delivery | Address, message content | Users | Confirmations, password reset, notifications (contract) | Mail provider | Until delivered; failed mail 7 days |
| Backups | Encrypted copy of the database | Users | Restore after failure (legitimate interest) | Backup storage | 7 daily, 4 weekly, 6 monthly copies |
| Google sign-in (optional) | Google account identifier, e-mail address, name | Users who choose it | Sign-in (consent) | Google | Until the user disconnects or deletes the account |
| Jira connection (optional) | Encrypted access tokens, issue keys and titles | Users who choose it | Link entries to issues (consent) | Atlassian | Until the user disconnects or deletes the account |
| Bot check (optional) | Technical browser data, IP address | Visitors who register | Block automated registrations (legitimate interest) | Cloudflare | As the provider states |

## Technical and organisational measures

- HTTPS with HSTS, a strict content security policy and a `Secure` session cookie.
- Passwords stored as hashes. Two-step sign-in secrets and Jira tokens encrypted with versioned keys.
- Rate limits on sign-in and requests. Sign-in history and active sessions visible to each user.
- A database account without schema rights for the application, and no public database port.
- Encrypted, tested backups with a measured recovery time.
- Security event log without passwords or one-time tokens. Alerts for outages, server errors, disk, backups and mail.
- Dependency, secret and image scans in CI on every change.

Details are in [Running HourForge yourself](../self-hosting.md), [Monitoring](monitoring.md) and
[Backups and restoring](restore.md).

## Providers and agreements

For each provider in the [subprocessors list](../../frontend/src/content/legal.ts), keep its data processing agreement
(most providers publish one you accept in the account settings) and note where it processes data. Check this again
whenever you add a provider.

## Organisations that use their own team

When an employer or client uses HourForge for the work time of its people, it decides what is recorded and acts as
controller of those entries. Give it a data processing agreement on request. It may also need its own impact assessment
for monitoring work, because entries show what a person did and when. HourForge lets a team turn off the live view of
who is working now.

## Review

Review this page every year and after every change of providers, features or data.
