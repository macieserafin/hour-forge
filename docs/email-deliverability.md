# E-mail that arrives

HourForge sends confirmation and password links, invitations, notices about account changes and optional
notifications (reminders, weekly reports). The messages are already shaped the way filters expect: text and HTML
versions, a `Message-ID` of the sender's domain, `Auto-Submitted: auto-generated`, and for optional notifications a
`List-Unsubscribe` header with one-click unsubscribe (RFC 8058), which Gmail and Yahoo require. What decides whether they
reach the inbox is the sending domain and the provider.

## Sender and provider

- Send from your own domain, best a subdomain used only for mail, e.g. `MAIL_FROM=HourForge <no-reply@mail.example.com>`.
- Use a mail provider with a good reputation, in the EU if your users are (for example Brevo, Mailjet, Scaleway
  Transactional Email or Amazon SES in an EU region), and its SMTP settings in `backend.env`. Sending directly from the
  server mostly ends in spam; if you must, the server's reverse DNS (PTR) has to match its host name.
- Messages wait in the database outbox and are sent within seconds. When the provider refuses or is down they are tried
  again 8 times, waiting 1, 2, 4 … 64 minutes (about 2 hours in all); after that they are marked failed and the
  `MailNotDelivered` alert fires (see [monitoring](operations/monitoring.md)). A failure never blocks the request that
  caused the message, and the log keeps only its subject and the error type, never the address or the links.

## SMTP settings

The API sends through one SMTP server, set in `backend.env` next to `compose.yaml`:

| Setting | Value |
|---|---|
| `SPRING_MAIL_HOST` | the provider's SMTP host, as its panel shows it |
| `SPRING_MAIL_PORT` | `587` (submission with STARTTLS). Port 465 (TLS from the first byte) is not supported; port 25 is often blocked on servers. |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | the SMTP login and the SMTP key or password the provider issues for it, not your account password |
| `MAIL_STARTTLS` | `true`. The connection is then encrypted or not made at all: a server that offers no STARTTLS is refused before the password is sent, and its certificate must match `SPRING_MAIL_HOST`. Only a local test server such as Mailpit needs `false`. |
| `MAIL_FROM` | `HourForge <no-reply@mail.example.com>`, an address of the domain you verified with the provider |

Connecting, reading and writing time out after 5 seconds each, and the next attempt follows the schedule above.

The links in the messages start with `FRONTEND_URL`. The production `compose.yaml` sets it to
`https://<HOURFORGE_DOMAIN>` from `.env` and this value wins over one in `backend.env`, so set the domain once, in `.env`.

### Putting the password on the server

Keep the SMTP key in a password manager and type it on the server only, never into Git, a chat or an issue:

```sh
cd deploy
umask 077
cp -n backend.env.example backend.env   # once; -n keeps a file that is already there
chmod 600 backend.env
nano backend.env                        # or vi: fill in SPRING_MAIL_* and MAIL_FROM
docker compose up -d --force-recreate backend
```

A container reads `backend.env` only when it is created, so `--force-recreate backend` applies a change. It replaces
the backend container only; the database and its volume stay as they are. Do not use `docker compose down -v`, which
deletes the volumes.

Then check that the API started (`docker compose ps`, `docker compose logs --tail=50 backend`) and send yourself a
message: register, or ask for a password reset for your own account at `https://<domain>`. If nothing arrives, the
backend log shows `E-mail delivery failed` with the error type: `MailAuthenticationException` means a wrong login or
key, `MailSendException` no connection, no STARTTLS or a certificate that does not match the host.

## Verifying the sending domain

Every value below comes from your provider's panel: copy it from there exactly, and do not reuse examples from other
guides, because the include domain for SPF, the DKIM selector and key and the verification code differ by provider and
by account.

1. In the provider's panel, add the sending domain (e.g. `mail.example.com`, the domain of `MAIL_FROM`).
2. The panel lists DNS records to create. Add them at the DNS host of your domain, with the names and values as shown:
   usually a verification code, one or more DKIM records (`TXT` or `CNAME`) and an SPF record.
3. If the domain already has an SPF record, do not add a second one: merge the provider's `include:` into the existing
   record, because two SPF records on one name make both invalid.
4. Add a DMARC record if there is none (table below), wait for DNS to update (minutes to a few hours), and run the
   panel's check until the domain shows as verified or authenticated.
5. Accept the provider's data processing agreement and list the provider among your subprocessors (see
   [self-hosting](self-hosting.md#legal-texts)).
6. Create the SMTP key and put it on the server as above.

You can check what the world sees with `dig +short TXT mail.example.com` and `dig +short TXT _dmarc.mail.example.com`
(and `dig +short TXT <selector>._domainkey.mail.example.com` or `CNAME` for DKIM, with the selector from the panel).

## DNS records for the sending domain

| Record | Value |
|---|---|
| SPF (`TXT` on the sending domain) | `v=spf1 include:<provider's SPF domain> -all`, with the include from the provider's panel |
| DKIM | the key(s) the provider gives you, at least 2048 bits |
| DMARC (`TXT` on `_dmarc.<domain>`) | start with `v=DMARC1; p=none; rua=mailto:<your address>`; after 2–4 weeks of clean reports `p=quarantine` |

The domain in `MAIL_FROM` must be the one SPF and DKIM are set up for (alignment), otherwise DMARC fails. Check a real
message: in Gmail, "Show original" should list `SPF: PASS`, `DKIM: PASS` and `DMARC: PASS`.

## Before going live

- Send a test message to [mail-tester.com](https://www.mail-tester.com) and aim for 9/10 or more.
- Check that the messages reach Gmail, Outlook.com and the providers your users have (in Poland also Onet and WP).
- Register the domain in Google Postmaster Tools to watch the reputation.
- The one-click unsubscribe address `POST /api/notifications/unsubscribe/one-click` must be reachable without signing in;
  the bundled Caddy configuration passes it through.
