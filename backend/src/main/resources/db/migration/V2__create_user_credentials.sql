CREATE TABLE user_credentials (
    user_id uuid PRIMARY KEY REFERENCES users (id),
    password_hash varchar(255) NOT NULL
);
