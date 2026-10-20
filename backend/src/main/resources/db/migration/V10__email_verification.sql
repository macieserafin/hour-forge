ALTER TABLE users ADD COLUMN email_verified_at timestamptz;

-- One-time links sent by e-mail (confirmation, password reset, address change). Only a hash of the token is stored.
CREATE TABLE email_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    purpose varchar(32) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'CHANGE_EMAIL')),
    token_hash varchar(64) NOT NULL CHECK (length(token_hash) = 64),
    new_email varchar(255),
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_tokens_new_email CHECK ((purpose = 'CHANGE_EMAIL') = (new_email IS NOT NULL))
);

CREATE INDEX ix_email_tokens_user_purpose ON email_tokens (user_id, purpose) WHERE used_at IS NULL;
