-- Team owners can hide the live "who is working now" view (privacy by default stays a team choice).
ALTER TABLE teams ADD COLUMN active_timers_visible boolean NOT NULL DEFAULT true;
