-- Optional second sign-in step with a time-based code (TOTP, RFC 6238) for accounts with a password.
-- The shared secret is stored encrypted (AES-GCM, TWO_FACTOR_ENCRYPTION_KEY); enabled_at stays null while the person
-- is still setting it up. last_used_step stops a code from being used twice.
CREATE TABLE user_two_factor (
    user_id uuid PRIMARY KEY REFERENCES users (id),
    secret varchar(200) NOT NULL,
    enabled_at timestamptz,
    last_used_step bigint,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- One-time backup codes, stored as SHA-256 hashes (they are long random values, so a fast hash is enough).
CREATE TABLE two_factor_backup_codes (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    code_hash varchar(64) NOT NULL,
    used_at timestamptz
);

CREATE INDEX two_factor_backup_codes_user ON two_factor_backup_codes (user_id);
