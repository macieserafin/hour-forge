-- Personal settings. A person without a row simply has the defaults, so nothing needs backfilling.
CREATE TABLE user_preferences (
    user_id uuid PRIMARY KEY REFERENCES users (id),
    default_team_id uuid,
    start_page varchar(20) NOT NULL DEFAULT 'DASHBOARD' CHECK (start_page IN ('DASHBOARD', 'TIME', 'PROJECTS', 'TEAMS')),
    time_format varchar(20) NOT NULL DEFAULT 'CLOCK' CHECK (time_format IN ('CLOCK', 'DECIMAL')),
    week_start varchar(20) NOT NULL DEFAULT 'MONDAY' CHECK (week_start IN ('MONDAY', 'SUNDAY')),
    long_timer_email boolean NOT NULL DEFAULT true,
    browser_notifications boolean NOT NULL DEFAULT false,
    long_timer_hours integer NOT NULL DEFAULT 10 CHECK (long_timer_hours BETWEEN 4 AND 24),
    title_timer boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT now()
);
