-- A13-D8: a link by e-mail confirms the removal of an account without a password (purpose DELETE_ACCOUNT).
-- Only the allowed purposes grow. The CHECK from V10 was declared on the column without a name, so PostgreSQL named it
-- email_tokens_purpose_check; it gets an explicit name now. ck_email_tokens_new_email stays: DELETE_ACCOUNT has no new_email.
ALTER TABLE email_tokens DROP CONSTRAINT email_tokens_purpose_check;
ALTER TABLE email_tokens ADD CONSTRAINT ck_email_tokens_purpose
    CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD', 'CHANGE_EMAIL', 'DELETE_ACCOUNT'));
