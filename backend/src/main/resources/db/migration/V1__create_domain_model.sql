CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(255) NOT NULL,
    display_name varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE teams (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL,
    timezone varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE team_members (
    id uuid PRIMARY KEY,
    team_id uuid NOT NULL REFERENCES teams (id),
    user_id uuid NOT NULL REFERENCES users (id),
    role varchar(20) NOT NULL,
    joined_at timestamptz NOT NULL,
    left_at timestamptz,
    CONSTRAINT uq_team_members_team_user UNIQUE (team_id, user_id),
    CONSTRAINT ck_team_members_role CHECK (role IN ('OWNER', 'MEMBER'))
);
CREATE INDEX ix_team_members_user ON team_members (user_id);
CREATE UNIQUE INDEX uq_team_members_active_owner ON team_members (team_id)
    WHERE role = 'OWNER' AND left_at IS NULL;

CREATE TABLE team_invites (
    id uuid PRIMARY KEY,
    team_id uuid NOT NULL REFERENCES teams (id),
    created_by uuid NOT NULL REFERENCES users (id),
    token_hash varchar(255) NOT NULL,
    max_uses integer,
    used_count integer NOT NULL DEFAULT 0,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_team_invites_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_team_invites_max_uses CHECK (max_uses IS NULL OR max_uses > 0),
    CONSTRAINT ck_team_invites_used_count CHECK (used_count >= 0)
);

CREATE TABLE projects (
    id uuid PRIMARY KEY,
    team_id uuid NOT NULL REFERENCES teams (id),
    name varchar(255) NOT NULL,
    description text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at timestamptz,
    CONSTRAINT uq_projects_id_team UNIQUE (id, team_id)
);
CREATE INDEX ix_projects_team ON projects (team_id);

CREATE TABLE time_entries (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    team_id uuid NOT NULL REFERENCES teams (id),
    project_id uuid NOT NULL,
    entry_type varchar(20) NOT NULL,
    work_date date NOT NULL,
    started_at timestamptz,
    ended_at timestamptz,
    duration_seconds integer,
    description text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_time_entries_project_team FOREIGN KEY (project_id, team_id)
        REFERENCES projects (id, team_id),
    CONSTRAINT ck_time_entries_type CHECK (entry_type IN ('TIMER', 'MANUAL')),
    CONSTRAINT ck_time_entries_state CHECK (
        (entry_type = 'MANUAL' AND started_at IS NULL AND ended_at IS NULL
            AND duration_seconds IS NOT NULL AND duration_seconds > 0)
        OR
        (entry_type = 'TIMER' AND started_at IS NOT NULL AND (
            (ended_at IS NULL AND duration_seconds IS NULL)
            OR (ended_at IS NOT NULL AND ended_at > started_at
                AND duration_seconds IS NOT NULL AND duration_seconds > 0)
        ))
    )
);
CREATE UNIQUE INDEX uq_time_entries_active_timer ON time_entries (user_id)
    WHERE entry_type = 'TIMER' AND ended_at IS NULL;
CREATE INDEX ix_time_entries_user_date ON time_entries (user_id, work_date);
CREATE INDEX ix_time_entries_team_date ON time_entries (team_id, work_date);
CREATE INDEX ix_time_entries_project_date ON time_entries (project_id, work_date);
