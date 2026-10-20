-- When a person last changed a saved entry (null: never edited), so reports can mark corrected entries.
ALTER TABLE time_entries ADD COLUMN last_edited_at timestamptz;

-- A person's reusable starting points: project, note and optionally a Jira issue, per team.
CREATE TABLE entry_templates (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    team_id uuid NOT NULL REFERENCES teams (id),
    name varchar(80) NOT NULL CHECK (length(btrim(name)) > 0),
    project_id uuid NOT NULL,
    description text CHECK (description IS NULL OR length(description) <= 2000),
    jira_connection_id uuid,
    jira_issue_id varchar(64),
    jira_issue_key varchar(64),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_entry_templates_project FOREIGN KEY (project_id, team_id) REFERENCES projects (id, team_id),
    CONSTRAINT fk_entry_templates_jira FOREIGN KEY (jira_connection_id, team_id) REFERENCES jira_connections (id, team_id),
    CONSTRAINT ck_entry_templates_jira CHECK (
        (jira_connection_id IS NULL AND jira_issue_id IS NULL AND jira_issue_key IS NULL)
        OR (jira_connection_id IS NOT NULL AND jira_issue_id IS NOT NULL AND jira_issue_key IS NOT NULL))
);
CREATE UNIQUE INDEX uq_entry_templates_name ON entry_templates (user_id, team_id, lower(name));
CREATE INDEX idx_entry_templates_project ON entry_templates (project_id);
