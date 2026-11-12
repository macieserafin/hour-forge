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
- Messages wait in the database outbox and are retried for about 4 hours when the provider refuses or is down; after that
  they are marked failed and the `MailNotDelivered` alert fires (see [monitoring](operations/monitoring.md)).

## DNS records for the sending domain

| Record | Value |
|---|---|
| SPF (`TXT` on the sending domain) | `v=spf1 include:<provider's SPF domain> -all` |
| DKIM | the key(s) the provider gives you, at least 2048 bits |
| DMARC (`TXT` on `_dmarc.<domain>`) | start with `v=DMARC1; p=none; rua=mailto:<your address>`; after 2–4 weeks of clean reports `p=quarantine` |

The domain in `MAIL_FROM` must be the one SPF and DKIM are set up for (alignment), otherwise DMARC fails.

## Before going live

- Send a test message to [mail-tester.com](https://www.mail-tester.com) and aim for 9/10 or more.
- Check that the messages reach Gmail, Outlook.com and the providers your users have (in Poland also Onet and WP).
- Register the domain in Google Postmaster Tools to watch the reputation.
- The one-click unsubscribe address `POST /api/notifications/unsubscribe/one-click` must be reachable without signing in;
  the bundled Caddy configuration passes it through.
