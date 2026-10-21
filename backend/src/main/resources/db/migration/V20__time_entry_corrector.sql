-- Who last changed a saved entry: the person themselves or an owner or admin who corrected it (null: never edited).
ALTER TABLE time_entries ADD COLUMN last_edited_by uuid REFERENCES users (id);
