-- Reset and address-change links are bound to the credential version (session_version) in force when they were issued,
-- so a later password change, reset, address change or "sign out everywhere" makes them useless.
-- Links issued before this migration carry no version and are refused.
ALTER TABLE email_tokens ADD COLUMN credential_version integer;
