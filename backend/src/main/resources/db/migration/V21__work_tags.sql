-- Work tags: a short label per team that members put on an entry. One tag per entry; tags are archived, never silently deleted.
CREATE TABLE tags (
    id uuid PRIMARY KEY,
    team_id uuid NOT NULL REFERENCES teams (id),
    name varchar(40) NOT NULL CHECK (length(btrim(name)) > 0),
    color varchar(20)
        CHECK (color IS NULL OR color IN ('VIOLET', 'BLUE', 'TEAL', 'GREEN', 'AMBER', 'ORANGE', 'RED', 'PINK', 'SLATE', 'BROWN')),
    archived_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_tags_id_team UNIQUE (id, team_id)
);
CREATE UNIQUE INDEX uq_tags_team_name ON tags (team_id, lower(name));

ALTER TABLE time_entries ADD COLUMN tag_id uuid;
ALTER TABLE time_entries ADD CONSTRAINT fk_time_entries_tag FOREIGN KEY (tag_id, team_id) REFERENCES tags (id, team_id);
CREATE INDEX ix_time_entries_tag ON time_entries (tag_id) WHERE tag_id IS NOT NULL;
