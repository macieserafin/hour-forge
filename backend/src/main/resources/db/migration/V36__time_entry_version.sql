-- Every change of an entry raises its version, so a form opened before another change can be told apart from the current state.
ALTER TABLE time_entries ADD COLUMN version integer NOT NULL DEFAULT 0;
