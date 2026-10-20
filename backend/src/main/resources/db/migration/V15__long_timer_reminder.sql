-- A running timer gets one reminder e-mail when it has run for too long; this remembers that it was sent.
ALTER TABLE time_entries ADD COLUMN long_timer_reminded_at timestamptz;
