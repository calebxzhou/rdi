-- Host2 is backed exclusively by Modpack2 versions. V5 remains immutable;
-- this migration removes its legacy source compatibility columns and checks.

DROP INDEX host2_legacy_source_idx;

ALTER TABLE host2
    DROP CONSTRAINT host2_source_type_value,
    DROP CONSTRAINT host2_pack_source_shape,
    DROP CONSTRAINT host2_legacy_modpack_id_format,
    DROP CONSTRAINT host2_legacy_version_name_value,
    ALTER COLUMN modpack2_version_id SET NOT NULL,
    DROP COLUMN source_type,
    DROP COLUMN legacy_modpack_id,
    DROP COLUMN legacy_version_name;

ALTER TABLE host2_operation
    DROP CONSTRAINT host2_operation_target_source_shape,
    DROP CONSTRAINT host2_operation_target_source_type_value,
    DROP CONSTRAINT host2_operation_target_legacy_version_name_value,
    DROP COLUMN target_source_type,
    DROP COLUMN target_legacy_modpack_id,
    DROP COLUMN target_legacy_version_name;

ALTER TABLE host2_operation
    ADD CONSTRAINT host2_operation_target_modpack2_version_shape
        CHECK (
            (
                kind IN ('InitialInstall', 'Switch')
                AND target_modpack2_version_id IS NOT NULL
                AND target_revision IS NOT NULL
                AND target_revision > 0
            )
            OR
            (
                kind = 'Apply'
                AND target_modpack2_version_id IS NULL
                AND target_revision IS NOT NULL
                AND target_revision > 0
            )
            OR
            (
                kind = 'Delete'
                AND target_modpack2_version_id IS NULL
                AND target_revision IS NULL
            )
        ),
    ADD CONSTRAINT host2_operation_target_modpack2_version_fk
        FOREIGN KEY (target_modpack2_version_id)
        REFERENCES modpack_version(id)
        ON DELETE RESTRICT
        NOT VALID;
