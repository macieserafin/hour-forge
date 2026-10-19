ALTER TABLE team_members ADD COLUMN removed_at timestamptz;
-- The reason for historical departures is unknown: require a new invitation for those accounts.
UPDATE team_members SET removed_at = left_at WHERE left_at IS NOT NULL;
ALTER TABLE team_members ADD CONSTRAINT ck_team_members_removal
    CHECK (removed_at IS NULL OR (left_at IS NOT NULL AND removed_at = left_at));

-- Never extend expired/short-lived invitations; cap only overly distant expiry dates.
UPDATE team_invites SET expires_at = LEAST(expires_at, CURRENT_TIMESTAMP + interval '30 days');

CREATE INDEX ix_team_invites_team_created ON team_invites (team_id, created_at DESC, id);
