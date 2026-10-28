-- Entry templates remember a work tag as well (project, tag, description, Jira task). The tag has to belong to the team of the template.
ALTER TABLE entry_templates ADD COLUMN tag_id uuid;
ALTER TABLE entry_templates ADD CONSTRAINT fk_entry_templates_tag FOREIGN KEY (tag_id, team_id) REFERENCES tags (id, team_id);
CREATE INDEX ix_entry_templates_tag ON entry_templates (tag_id) WHERE tag_id IS NOT NULL;
