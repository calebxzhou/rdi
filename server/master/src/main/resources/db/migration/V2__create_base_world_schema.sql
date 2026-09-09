CREATE TABLE base_world (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    owner_id UUID NOT NULL REFERENCES account(id),
    name TEXT NOT NULL,
    level_type TEXT NOT NULL,
    generator_settings TEXT,
    size BIGINT NOT NULL CHECK (size >= 0)
);
