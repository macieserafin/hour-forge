-- Accounts blocked by the instance operator cannot sign in; the reason is shown only in the operator panel.
ALTER TABLE users ADD COLUMN blocked_at timestamptz;
ALTER TABLE users ADD COLUMN blocked_reason varchar(500);

-- The one notice the operator shows to everybody (e.g. planned maintenance). At most one row.
CREATE TABLE instance_announcement (
    id smallint PRIMARY KEY CHECK (id = 1),
    message varchar(500) NOT NULL,
    level varchar(10) NOT NULL CHECK (level IN ('INFO', 'WARNING')),
    updated_at timestamptz NOT NULL
);
