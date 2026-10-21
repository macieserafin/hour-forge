-- What a person says about their place in one team: shown to the team's owner and admins, not to other members.
ALTER TABLE team_members ADD COLUMN job_title varchar(80) CHECK (job_title IS NULL OR length(btrim(job_title)) > 0);
ALTER TABLE team_members ADD COLUMN fte_percent integer CHECK (fte_percent IS NULL OR fte_percent BETWEEN 1 AND 100);
