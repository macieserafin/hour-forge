-- A team's owner can have the previous week's PDF report mailed to the owner and admins on Monday morning.
ALTER TABLE teams ADD COLUMN weekly_report_enabled boolean NOT NULL DEFAULT false;
-- The Monday of the last week a report was sent for, so a restart or a repeated run never sends one twice.
ALTER TABLE teams ADD COLUMN weekly_report_sent_for date;
