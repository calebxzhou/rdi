-- A Modrinth version may contain multiple files with the same project/version
-- identity. Preserve each distinct hash, target path, and side.
ALTER TABLE modpack_content
    DROP CONSTRAINT modpack_content_pkey,
    ADD PRIMARY KEY (version_id, platform, project_id, file_id, hash, side);
