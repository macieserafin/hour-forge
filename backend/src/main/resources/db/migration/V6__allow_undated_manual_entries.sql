ALTER TABLE time_entries ALTER COLUMN work_date DROP NOT NULL;
ALTER TABLE time_entries ADD CONSTRAINT ck_time_entries_timer_date
    CHECK (entry_type = 'MANUAL' OR work_date IS NOT NULL);
