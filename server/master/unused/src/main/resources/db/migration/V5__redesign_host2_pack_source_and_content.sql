-- Host2 source/revision redesign. V1-V4 remain immutable.
-- Host2 has no production rows yet, so the old upload/mod state is removed
-- instead of being translated into a synthetic PackSource.

CREATE TABLE host2_content_revision (
    host_id UUID NOT NULL
        REFERENCES host2(id)
        ON DELETE CASCADE,
    revision BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalized_at TIMESTAMPTZ,
    PRIMARY KEY (host_id, revision),
    CONSTRAINT host2_content_revision_positive
        CHECK (revision > 0)
);

ALTER TABLE host2
    DROP CONSTRAINT host2_setup_status_value,
    DROP COLUMN mc_version,
    DROP COLUMN mod_loader,
    DROP COLUMN setup_status,
    ADD COLUMN pack_status TEXT,
    ADD COLUMN source_type TEXT,
    ADD COLUMN modpack2_version_id UUID,
    ADD COLUMN legacy_modpack_id TEXT,
    ADD COLUMN legacy_version_name TEXT,
    ADD COLUMN active_content_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN pending_content_revision BIGINT;

ALTER TABLE host2
    ALTER COLUMN pack_status SET NOT NULL,
    ALTER COLUMN source_type SET NOT NULL;

ALTER TABLE host2
    ADD CONSTRAINT host2_pack_status_value
        CHECK (pack_status IN ('Busy', 'Ok', 'Fail')),
    ADD CONSTRAINT host2_source_type_value
        CHECK (source_type IN ('Modpack2', 'Legacy')),
    ADD CONSTRAINT host2_pack_source_shape
        CHECK (
            (
                source_type = 'Modpack2'
                AND modpack2_version_id IS NOT NULL
                AND legacy_modpack_id IS NULL
                AND legacy_version_name IS NULL
            )
            OR
            (
                source_type = 'Legacy'
                AND modpack2_version_id IS NULL
                AND legacy_modpack_id IS NOT NULL
                AND legacy_version_name IS NOT NULL
            )
        ),
    ADD CONSTRAINT host2_legacy_modpack_id_format
        CHECK (
            legacy_modpack_id IS NULL
            OR legacy_modpack_id ~ '^[0-9A-Fa-f]{24}$'
        ),
    ADD CONSTRAINT host2_legacy_version_name_value
        CHECK (
            legacy_version_name IS NULL
            OR (
                legacy_version_name = BTRIM(legacy_version_name)
                AND char_length(legacy_version_name) > 0
            )
        ),
    ADD CONSTRAINT host2_active_revision_non_negative
        CHECK (active_content_revision >= 0),
    ADD CONSTRAINT host2_pending_revision_positive
        CHECK (
            pending_content_revision IS NULL
            OR pending_content_revision > 0
        ),
    ADD CONSTRAINT host2_pending_not_active
        CHECK (
            pending_content_revision IS NULL
            OR pending_content_revision > active_content_revision
        );

-- Revision zero is the wire-level state for a Host that has not published a
-- Pack yet. It deliberately has no header row. These generated nullable keys
-- let the composite FKs protect every published revision while allowing zero.
ALTER TABLE host2
    ADD COLUMN active_content_revision_ref BIGINT
        GENERATED ALWAYS AS (NULLIF(active_content_revision, 0)) STORED,
    ADD COLUMN pending_content_revision_ref BIGINT
        GENERATED ALWAYS AS (NULLIF(pending_content_revision, 0)) STORED;

ALTER TABLE host2
    ADD CONSTRAINT host2_active_revision_fk
        FOREIGN KEY (id, active_content_revision_ref)
        REFERENCES host2_content_revision(host_id, revision)
        DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT host2_pending_revision_fk
        FOREIGN KEY (id, pending_content_revision_ref)
        REFERENCES host2_content_revision(host_id, revision)
        DEFERRABLE INITIALLY DEFERRED;

-- This FK protects an active Modpack2 version from deletion. Legacy source
-- identifiers are Mongo ObjectId strings and are checked above instead.
ALTER TABLE host2
    ADD CONSTRAINT host2_modpack2_version_fk
        FOREIGN KEY (modpack2_version_id)
        REFERENCES modpack_version(id)
        ON DELETE RESTRICT;

-- V1 already created host2_owner_id_idx; do not create it a second time.
CREATE INDEX host2_modpack2_version_idx ON host2(modpack2_version_id)
    WHERE modpack2_version_id IS NOT NULL;
CREATE INDEX host2_legacy_source_idx
    ON host2(legacy_modpack_id, legacy_version_name)
    WHERE legacy_modpack_id IS NOT NULL;

-- Replace the old upload-only model. This is intentionally the only
-- destructive change in V5; there are no production Host2 rows.
DROP TABLE host2_mod;

CREATE TABLE host2_content_snapshot (
    host_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    origin TEXT NOT NULL,
    platform TEXT NOT NULL,
    type TEXT NOT NULL,
    project_id TEXT NOT NULL,
    file_id TEXT NOT NULL,
    slug TEXT NOT NULL,
    hash TEXT NOT NULL,
    target_path TEXT,
    side TEXT NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    file_size BIGINT NOT NULL,
    PRIMARY KEY (host_id, revision, origin, platform, project_id),
    FOREIGN KEY (host_id, revision)
        REFERENCES host2_content_revision(host_id, revision)
        ON DELETE CASCADE,
    CONSTRAINT host2_content_origin_value
        CHECK (origin IN ('Pack', 'Extra')),
    CONSTRAINT host2_content_platform_value
        CHECK (platform IN ('CurseForge', 'Modrinth', 'GitHub')),
    CONSTRAINT host2_content_type_value
        CHECK (type IN ('Mod', 'ShaderPack', 'ResPack', 'DataPack', 'Other')),
    CONSTRAINT host2_content_side_value
        CHECK (side IN ('Client', 'Server', 'Both')),
    CONSTRAINT host2_content_file_size_non_negative
        CHECK (file_size >= 0),
    CONSTRAINT host2_content_target_value
        CHECK (
            target_path IS NULL
            OR (
                target_path = BTRIM(target_path)
                AND char_length(target_path) BETWEEN 1 AND 512
            )
        )
);

CREATE TABLE host2_operation (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    host_id UUID NOT NULL,
    kind TEXT NOT NULL,
    phase TEXT NOT NULL,
    target_source_type TEXT,
    target_modpack2_version_id UUID,
    target_legacy_modpack_id TEXT,
    target_legacy_version_name TEXT,
    target_revision BIGINT,
    staging_path TEXT,
    backup_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT host2_operation_kind_value
        CHECK (kind IN ('InitialInstall', 'Switch', 'Apply', 'Delete')),
    CONSTRAINT host2_operation_phase_not_blank
        CHECK (char_length(BTRIM(phase)) > 0),
    CONSTRAINT host2_operation_target_revision_non_negative
        CHECK (target_revision IS NULL OR target_revision >= 0),
    CONSTRAINT host2_operation_target_source_shape
        CHECK (
            (
                kind IN ('InitialInstall', 'Switch')
                AND (
                    (
                        target_source_type = 'Modpack2'
                        AND target_modpack2_version_id IS NOT NULL
                        AND target_legacy_modpack_id IS NULL
                        AND target_legacy_version_name IS NULL
                    )
                    OR
                    (
                        target_source_type = 'Legacy'
                        AND target_modpack2_version_id IS NULL
                        AND target_legacy_modpack_id IS NOT NULL
                        AND target_legacy_version_name IS NOT NULL
                        AND target_legacy_modpack_id ~ '^[0-9A-Fa-f]{24}$'
                    )
                )
            )
            OR
            (
                kind = 'Apply'
                AND (
                    (
                        target_source_type IS NULL
                        AND target_modpack2_version_id IS NULL
                        AND target_legacy_modpack_id IS NULL
                        AND target_legacy_version_name IS NULL
                    )
                    OR
                    (
                        target_source_type = 'Modpack2'
                        AND target_modpack2_version_id IS NOT NULL
                        AND target_legacy_modpack_id IS NULL
                        AND target_legacy_version_name IS NULL
                    )
                    OR
                    (
                        target_source_type = 'Legacy'
                        AND target_modpack2_version_id IS NULL
                        AND target_legacy_modpack_id IS NOT NULL
                        AND target_legacy_version_name IS NOT NULL
                        AND target_legacy_modpack_id ~ '^[0-9A-Fa-f]{24}$'
                    )
                )
            )
            OR
            (
                kind = 'Delete'
                AND target_source_type IS NULL
                AND target_modpack2_version_id IS NULL
                AND target_legacy_modpack_id IS NULL
                AND target_legacy_version_name IS NULL
                AND target_revision IS NULL
            )
        ),
    CONSTRAINT host2_operation_target_source_type_value
        CHECK (
            target_source_type IS NULL
            OR target_source_type IN ('Modpack2', 'Legacy')
        ),
    CONSTRAINT host2_operation_target_legacy_version_name_value
        CHECK (
            target_legacy_version_name IS NULL
            OR (
                target_legacy_version_name = BTRIM(target_legacy_version_name)
                AND char_length(target_legacy_version_name) > 0
            )
        )
);

CREATE INDEX host2_operation_host_idx ON host2_operation(host_id);
CREATE UNIQUE INDEX host2_operation_one_active_per_host
    ON host2_operation(host_id)
    WHERE completed_at IS NULL;

-- A delete tombstone deliberately has no FK to host2. It survives the host
-- row deletion and lets startup recovery finish moving/cleaning data. The
-- operation FK is RESTRICT so its journal row remains until cleanup finishes.
CREATE TABLE host2_delete_tombstone (
    operation_id UUID PRIMARY KEY
        REFERENCES host2_operation(id)
        ON DELETE RESTRICT,
    host_id UUID NOT NULL,
    deleting_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE INDEX host2_delete_tombstone_host_idx
    ON host2_delete_tombstone(host_id);
