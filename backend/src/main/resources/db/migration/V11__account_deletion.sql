-- Set when an account is anonymized; the row stays so that time entries and history keep their references.
ALTER TABLE users ADD COLUMN deleted_at timestamptz;
