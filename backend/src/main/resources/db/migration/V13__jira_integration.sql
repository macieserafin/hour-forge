-- Jira Cloud integration: one site per team, one grant per user and site, and short-lived OAuth states.

CREATE TABLE jira_connections (
    id uuid PRIMARY KEY,
    team_id uuid NOT NULL REFERENCES teams (id),
    cloud_id varchar(128) NOT NULL,
    site_url varchar(512) NOT NULL,
    site_name varchar(255) NOT NULL,
    connected_by uuid NOT NULL REFERENCES users (id),
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_jira_connections_team UNIQUE (team_id),
    -- Lets time entries reference (connection, team) so a link can never point at another team's site.
    CONSTRAINT uq_jira_connections_id_team UNIQUE (id, team_id)
);

-- Each person consents separately. Tokens are encrypted by the application; the key version says which key was used.
CREATE TABLE jira_user_authorizations (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    cloud_id varchar(128) NOT NULL,
    encrypted_access_token text NOT NULL,
    encrypted_refresh_token text,
    access_token_expires_at timestamptz NOT NULL,
    granted_scopes text NOT NULL,
    encryption_key_version varchar(64) NOT NULL,
    connected_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_jira_user_authorizations UNIQUE (user_id, cloud_id)
);

-- Only hashes of the state and of the session are stored. team_id has no foreign key on purpose:
-- rows live for minutes and the callback re-checks the team and the membership anyway.
CREATE TABLE oauth_states (
    id uuid PRIMARY KEY,
    state_hash varchar(64) NOT NULL CHECK (length(state_hash) = 64),
    session_hash varchar(64) NOT NULL CHECK (length(session_hash) = 64),
    user_id uuid NOT NULL REFERENCES users (id),
    team_id uuid NOT NULL,
    intent varchar(16) NOT NULL CHECK (intent IN ('SITE', 'MEMBER')),
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_oauth_states_hash UNIQUE (state_hash)
);

CREATE INDEX ix_oauth_states_user ON oauth_states (user_id);
