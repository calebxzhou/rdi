-- Keep a surrogate row identity so nullable target_path and content type can
-- participate in the complete logical uniqueness contract.
ALTER TABLE modpack_content
    ADD COLUMN content_id BIGSERIAL;

ALTER TABLE modpack_content
    DROP CONSTRAINT modpack_content_pkey,
    ADD PRIMARY KEY (content_id);

CREATE UNIQUE INDEX modpack_content_full_identity_idx
    ON modpack_content (
        version_id, platform, project_id, file_id, hash, side, type,
        COALESCE(target_path, '')
    );
