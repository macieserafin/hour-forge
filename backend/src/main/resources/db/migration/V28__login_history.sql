-- Sign-ins (successful and failed) of an account, shown to its owner and kept for 90 days by the retention job.
-- Only what a person needs to recognise a device is stored: browser, system, a shortened address and an
-- approximate place, never the full address or user agent.
CREATE TABLE login_events (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    occurred_at timestamptz NOT NULL,
    outcome varchar(10) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILED')),
    browser varchar(40),
    system varchar(40),
    address varchar(60),
    location varchar(160)
);

CREATE INDEX login_events_user_time ON login_events (user_id, occurred_at DESC);
CREATE INDEX login_events_time ON login_events (occurred_at);
