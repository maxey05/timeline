CREATE TABLE IF NOT EXISTS idea (
    id              TEXT PRIMARY KEY,
    title           TEXT NOT NULL,
    status          TEXT NOT NULL,
    created_at      TEXT NOT NULL,  
    updated_at      TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS idea_tag (
    idea_id         TEXT NOT NULL REFERENCES idea(id) ON DELETE CASCADE,
    tag_name        TEXT NOT NULL,
    PRIMARY KEY     (idea_id, tag_name)
);

CREATE TABLE IF NOT EXISTS idea_block (
    idea_id         TEXT NOT NULL REFERENCES idea(id) ON DELETE CASCADE,
    position        INTEGER NOT NULL,
    type            TEXT NOT NULL,
    text            TEXT,
    uri             TEXT,
    label           TEXT,
    alt_text        TEXT,
    PRIMARY KEY (idea_id, position)
);

