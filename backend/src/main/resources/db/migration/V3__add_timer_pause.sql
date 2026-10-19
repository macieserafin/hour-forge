ALTER TABLE time_entries
    ADD COLUMN paused_at timestamptz,
    ADD COLUMN paused_duration_micros bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_time_entries_pause CHECK (
        paused_duration_micros >= 0
        AND (paused_at IS NULL OR
             (entry_type = 'TIMER' AND ended_at IS NULL AND paused_at >= started_at))
        AND (entry_type = 'TIMER' OR (paused_at IS NULL AND paused_duration_micros = 0))
    );
