-- An account that signs in only through a linked provider keeps its credential row without a password: the row
-- still carries the session version, which sessions, links (V33) and the operator's block rely on.
ALTER TABLE user_credentials ALTER COLUMN password_hash DROP NOT NULL;

-- Accounts linked to an external sign-in provider, found only by the provider's subject (Google "sub", which is
-- case-sensitive and never reused); the provider's e-mail never selects an account. Removed by the account
-- deletion, so there is no cascade.
CREATE TABLE user_identities (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    provider varchar(16) NOT NULL CHECK (provider IN ('GOOGLE')),
    subject varchar(255) NOT NULL,
    email varchar(255),
    linked_at timestamptz NOT NULL,
    last_used_at timestamptz,
    CONSTRAINT uq_user_identities_provider_subject UNIQUE (provider, subject),
    CONSTRAINT uq_user_identities_user_provider UNIQUE (user_id, provider)
);

-- How a sign-in was made. Every earlier sign-in used the password, and so does a server still running the previous
-- version, which does not know the column: the default covers both.
ALTER TABLE login_events ADD COLUMN method varchar(16) NOT NULL DEFAULT 'PASSWORD'
    CONSTRAINT ck_login_events_method CHECK (method IN ('PASSWORD', 'GOOGLE'));
