ALTER TABLE projects
    ADD CONSTRAINT ck_projects_description_length CHECK (char_length(description) <= 2000);

ALTER TABLE time_entries
    ADD CONSTRAINT ck_time_entries_description_length CHECK (char_length(description) <= 2000);
