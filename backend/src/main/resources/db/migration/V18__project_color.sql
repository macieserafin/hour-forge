-- An optional colour a project is shown in. Only the name of a fixed palette is stored.
ALTER TABLE projects ADD COLUMN color varchar(20)
    CHECK (color IS NULL OR color IN ('VIOLET', 'BLUE', 'TEAL', 'GREEN', 'AMBER', 'ORANGE', 'RED', 'PINK', 'SLATE', 'BROWN'));
