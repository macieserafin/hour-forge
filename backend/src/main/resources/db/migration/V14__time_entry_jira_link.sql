-- A time entry may point at one Jira issue of the team's connected site. Only identifiers are stored (the issue id,
-- which survives renames, and the key for display); titles are read from Jira when needed, never copied here.
ALTER TABLE time_entries
    ADD COLUMN jira_connection_id uuid,
    ADD COLUMN jira_issue_id varchar(64),
    ADD COLUMN jira_issue_key varchar(64),
    ADD CONSTRAINT ck_time_entries_jira_link CHECK (
        (jira_connection_id IS NULL AND jira_issue_id IS NULL AND jira_issue_key IS NULL)
        OR (jira_connection_id IS NOT NULL AND jira_issue_id IS NOT NULL AND jira_issue_key IS NOT NULL)),
    -- The connection must belong to the same team as the entry.
    ADD CONSTRAINT fk_time_entries_jira_connection FOREIGN KEY (jira_connection_id, team_id)
        REFERENCES jira_connections (id, team_id);

CREATE INDEX idx_time_entries_jira_connection ON time_entries (jira_connection_id) WHERE jira_connection_id IS NOT NULL;
