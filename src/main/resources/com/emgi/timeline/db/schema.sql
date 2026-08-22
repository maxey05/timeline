CREATE TABLE IF NOT EXISTS idea (
    id              TEXT PRIMARY KEY,
    title           TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    status          TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS idea_tag (
    idea_id         TEXT NOT NULL REFERENCES idea(id) ON DELETE CASCADE,
    tag_name        TEXT NOT NULL,
    PRIMARY KEY     (idea_id, tag_name)
);
