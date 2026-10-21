ALTER TABLE team_members DROP CONSTRAINT ck_team_members_role;
ALTER TABLE team_members ADD CONSTRAINT ck_team_members_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'));
