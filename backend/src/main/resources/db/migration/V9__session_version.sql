-- Bumped when the password changes or the user signs out everywhere; sessions carrying an older value are rejected.
ALTER TABLE user_credentials ADD COLUMN session_version integer NOT NULL DEFAULT 0;
