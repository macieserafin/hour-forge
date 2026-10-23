-- Accounts unused for 24 months are removed after two e-mail warnings (30 and 7 days before). The columns remember
-- the last warning, so each is sent once; any use of the app clears them. 0 means "kept": the account owns a team
-- that other people still use, so it is not removed automatically and the operator decides.
ALTER TABLE users ADD COLUMN inactivity_warned_days integer CHECK (inactivity_warned_days IN (0, 7, 30));
ALTER TABLE users ADD COLUMN inactivity_warned_at timestamptz;
