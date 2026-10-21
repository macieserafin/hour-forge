-- An optional colour a team is shown in. Only the name of the fixed palette is stored.
ALTER TABLE teams ADD COLUMN color varchar(20)
    CHECK (color IS NULL OR color IN ('VIOLET', 'BLUE', 'TEAL', 'GREEN', 'AMBER', 'ORANGE', 'RED', 'PINK', 'SLATE', 'BROWN'));
