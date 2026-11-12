-- Outbox of e-mails: a message is stored in the transaction of the change that caused it and delivered by a background
-- job, with retries. A message that has left is removed (its body may hold a one-time link); one that kept failing stays
-- as FAILED for a week, for the operator, and is then removed too.
CREATE TABLE outgoing_emails (
    id                    uuid PRIMARY KEY,
    recipient             varchar(320) NOT NULL,
    subject               text NOT NULL,
    text_body             text NOT NULL,
    html_body             text NOT NULL,
    category              varchar(16) NOT NULL CHECK (category IN ('TRANSACTIONAL', 'NOTIFICATION')),
    one_click_unsubscribe text,
    attachment_name       varchar(255),
    attachment_type       varchar(127),
    attachment_content    bytea,
    status                varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'FAILED')),
    attempts              integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at       timestamptz NOT NULL,
    last_error            varchar(200),
    created_at            timestamptz NOT NULL,
    CONSTRAINT ck_outgoing_emails_attachment
        CHECK ((attachment_name IS NULL) = (attachment_content IS NULL) AND (attachment_name IS NULL) = (attachment_type IS NULL))
);

CREATE INDEX ix_outgoing_emails_due ON outgoing_emails (next_attempt_at) WHERE status = 'PENDING';
