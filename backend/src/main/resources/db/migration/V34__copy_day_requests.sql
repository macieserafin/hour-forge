-- "Repeat day" remembers the id of each request, so a request repeated after a lost response returns the first result
-- instead of copying the day again. Rows are only needed for a short time and are swept by the retention job.
CREATE TABLE copy_day_requests (
    user_id       uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    request_id    uuid        NOT NULL,
    team_id       uuid        NOT NULL,
    from_date     date        NOT NULL,
    to_date       date        NOT NULL,
    copied        integer     NOT NULL,
    total_seconds bigint      NOT NULL,
    created_at    timestamptz NOT NULL,
    PRIMARY KEY (user_id, request_id)
);

CREATE INDEX idx_copy_day_requests_created_at ON copy_day_requests (created_at);
