-- Modpack2 publishing: server mode, append provenance, and raw source files.
-- Existing V4/V5 rows are retained; their server side is treated as Generated.

ALTER TABLE modpack_version
    ADD COLUMN server_mode TEXT NOT NULL DEFAULT 'Generated',
    ADD COLUMN base_version_id UUID;

ALTER TABLE modpack_version
    ADD CONSTRAINT modpack_version_server_mode_value
        CHECK (server_mode IN ('Provided', 'Generated')),
    ADD CONSTRAINT modpack_version_base_version_fk
        FOREIGN KEY (base_version_id)
        REFERENCES modpack_version(id)
        ON DELETE RESTRICT;

-- Client and Server copies of one logical project may resolve to different
-- artifacts. Preserve both records while still rejecting duplicate identities
-- on the same side.
ALTER TABLE modpack_content
    DROP CONSTRAINT modpack_content_pkey,
    ADD PRIMARY KEY (version_id, platform, project_id, side);

CREATE INDEX modpack_version_base_version_idx
    ON modpack_version(base_version_id)
    WHERE base_version_id IS NOT NULL;

CREATE TABLE modpack_raw_file (
    version_id UUID NOT NULL
        REFERENCES modpack_version(id) ON DELETE CASCADE,
    root TEXT NOT NULL,
    path TEXT NOT NULL,
    sha1 TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    PRIMARY KEY (version_id, root, path),
    CONSTRAINT modpack_raw_file_root_value
        CHECK (root IN ('client', 'shared', 'server')),
    CONSTRAINT modpack_raw_file_path_value
        CHECK (
            path = BTRIM(path)
            AND char_length(path) BETWEEN 1 AND 1024
            AND LEFT(path, 1) NOT IN ('/', '\\')
            AND POSITION('..' IN REPLACE(path, '\\', '/')) = 0
        ),
    CONSTRAINT modpack_raw_file_sha1_value
        CHECK (sha1 ~ '^[0-9A-Fa-f]{40}$'),
    CONSTRAINT modpack_raw_file_size_value
        CHECK (file_size >= 0)
);

CREATE INDEX modpack_raw_file_version_root_idx
    ON modpack_raw_file(version_id, root, path);

-- A workspace can retain one logical project on both sides.  The side is part
-- of the snapshot identity for the same reason it is part of modpack_content.
ALTER TABLE host2_content_snapshot
    DROP CONSTRAINT host2_content_snapshot_pkey,
    ADD PRIMARY KEY (host_id, revision, origin, platform, project_id, side);

-- A completed upload is bound to the URL parent used by the request. This
-- prevents replaying a response onto a different Modpack2 parent after a
-- response-loss retry. NULL is retained for the old POST /modpack2 API.
ALTER TABLE modpack_upload_result
    ADD COLUMN request_modpack_id UUID
        REFERENCES modpack(id) ON DELETE CASCADE;

CREATE INDEX modpack_upload_result_request_parent_idx
    ON modpack_upload_result(request_modpack_id, upload_id)
    WHERE request_modpack_id IS NOT NULL;
