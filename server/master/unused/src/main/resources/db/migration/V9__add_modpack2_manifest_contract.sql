-- A verified installer contract is retained independently from the version
-- projection so old versions without a contract remain readable and retryable.
CREATE TABLE modpack2_version_manifest (
    version_id UUID PRIMARY KEY
        REFERENCES modpack_version(id) ON DELETE CASCADE,
    format TEXT NOT NULL,
    manifest_json TEXT NOT NULL,
    bindings_json TEXT NOT NULL,
    CONSTRAINT modpack2_version_manifest_format_value
        CHECK (format IN ('CurseForge', 'Modrinth')),
    CONSTRAINT modpack2_version_manifest_json_non_empty
        CHECK (char_length(manifest_json) > 0),
    CONSTRAINT modpack2_version_manifest_bindings_non_empty
        CHECK (char_length(bindings_json) > 0)
);
