-- Profile pictures and team logos. The server re-encodes every upload as a 256x256 JPEG, which drops all metadata
-- (location, camera) and anything hidden in the original file; only that result is stored, in its own table.
ALTER TABLE users ADD COLUMN avatar_updated_at timestamptz;
ALTER TABLE teams ADD COLUMN logo_updated_at timestamptz;

CREATE TABLE user_avatars (
    user_id uuid PRIMARY KEY REFERENCES users (id),
    image bytea NOT NULL CHECK (octet_length(image) <= 200000)
);

CREATE TABLE team_logos (
    team_id uuid PRIMARY KEY REFERENCES teams (id),
    image bytea NOT NULL CHECK (octet_length(image) <= 200000)
);
