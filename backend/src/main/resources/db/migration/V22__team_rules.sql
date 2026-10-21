-- Rules a team's owner or admins set for the entries its members save, and the first day of the team's reporting week.
ALTER TABLE teams ADD COLUMN require_description boolean NOT NULL DEFAULT false;
ALTER TABLE teams ADD COLUMN require_tag_or_issue boolean NOT NULL DEFAULT false;
ALTER TABLE teams ADD COLUMN week_start varchar(10) NOT NULL DEFAULT 'MONDAY' CHECK (week_start IN ('MONDAY', 'SUNDAY'));
