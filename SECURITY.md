# Security policy

## Report a vulnerability

Report vulnerabilities privately. Do not open a public issue or pull request for them.

Use GitHub's private reporting: open the [Security tab](https://github.com/macieserafin/hour-forge/security) of this
repository and choose **Report a vulnerability**.

Include:

- what you found and which part of HourForge it affects (API endpoint, screen, deployment file),
- steps to reproduce it, with the version or commit you tested,
- what an attacker gains,
- a proof of concept, if you have one, without real user data.

You get a first answer within 7 days. We then confirm the problem, work on a fix and agree a date for disclosure with
you. Please keep the details private until a fix is available.

## Supported versions

HourForge has no numbered releases yet. Only the latest commit on `main` and the images published from it receive
security fixes.

## What is in scope

- The API, the web app and the container images built from this repository.
- The deployment files in [`deploy/`](deploy) and the guides in [`docs/`](docs) when following them leaves an instance
  insecure.

Out of scope: instances run by other people (report to their operator), denial of service by volume, social
engineering, and findings that need access to the server or to a user's unlocked device.

## For people who run an instance

[Running HourForge yourself](docs/self-hosting.md) describes the protections Compose sets up. Keep your instance safe:

- Keep `.env` and `backend.env` out of Git and out of backups you do not encrypt.
- Update images and dependencies every few weeks. [Deploying a new version](docs/operations/deploy.md) lists the steps.
- Test your restore at least once. See [Backups and restoring](docs/operations/restore.md).
- Rotate secrets after anyone with access leaves. See [Rotating secrets](docs/operations/secrets-rotation.md).
