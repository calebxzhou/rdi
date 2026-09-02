CREATE TABLE modpack (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name TEXT NOT NULL,
    owner_id UUID NOT NULL REFERENCES account(id) ON DELETE RESTRICT,
    intro TEXT NOT NULL,
    mc SMALLINT NOT NULL,
    loader TEXT NOT NULL,
    icon_url TEXT NOT NULL,
    source_url TEXT,
    CONSTRAINT modpack_name_length
        CHECK (name = BTRIM(name) AND char_length(name) BETWEEN 1 AND 64),
    CONSTRAINT modpack_name_unique UNIQUE (name),
    CONSTRAINT modpack_intro_length
        CHECK (char_length(intro) BETWEEN 10 AND 100),
    CONSTRAINT modpack_mc_value
        CHECK (mc IN (20, 21)),
    CONSTRAINT modpack_loader_value
        CHECK (loader IN ('Forge', 'NeoForge')),
    CONSTRAINT modpack_icon_url_length
        CHECK (char_length(BTRIM(icon_url)) BETWEEN 1 AND 2048),
    CONSTRAINT modpack_source_url_length
        CHECK (
            source_url IS NULL OR
            char_length(BTRIM(source_url)) BETWEEN 1 AND 2048
        )
);

CREATE INDEX modpack_owner_id_idx ON modpack(owner_id);
CREATE UNIQUE INDEX modpack_name_lower_unique
    ON modpack (LOWER(name));

CREATE TABLE modpack_category (
    modpack_id UUID NOT NULL REFERENCES modpack(id) ON DELETE CASCADE,
    category TEXT NOT NULL,
    PRIMARY KEY (modpack_id, category),
    CONSTRAINT modpack_category_value
        CHECK (
            category IN (
                'Large', 'Medium', 'Small', 'Magic', 'Hardcore', 'Skyblock',
                'Vanilla', 'Tech', 'Story', 'Adventure', 'Casual', 'Manage',
                'Apocalypse', 'War', 'HeavyMod', 'Rpg', 'Combat', 'LightMod',
                'Optimize', 'Other'
            )
        )
);

CREATE INDEX modpack_category_category_idx ON modpack_category(category, modpack_id);

CREATE TABLE modpack_stats (
    modpack_id UUID PRIMARY KEY REFERENCES modpack(id) ON DELETE CASCADE,
    play_count INTEGER NOT NULL DEFAULT 0,
    play_time_sec BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT modpack_stats_play_count_non_negative CHECK (play_count >= 0),
    CONSTRAINT modpack_stats_play_time_non_negative CHECK (play_time_sec >= 0)
);

CREATE INDEX modpack_stats_play_count_idx
    ON modpack_stats(play_count DESC, modpack_id DESC);

CREATE TABLE modpack_version (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    modpack_id UUID NOT NULL REFERENCES modpack(id) ON DELETE CASCADE,
    uploader_id UUID REFERENCES account(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    changelog TEXT NOT NULL,
    status TEXT NOT NULL,
    total_size BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT modpack_version_name_length
        CHECK (name = BTRIM(name) AND char_length(name) BETWEEN 1 AND 32),
    CONSTRAINT modpack_version_changelog_length
        CHECK (char_length(changelog) BETWEEN 0 AND 10000),
    CONSTRAINT modpack_version_status_value
        CHECK (status IN ('Fail', 'Ok', 'Building')),
    CONSTRAINT modpack_version_total_size_value
        CHECK (total_size >= 0 AND (status = 'Ok' OR total_size = 0)),
    CONSTRAINT modpack_version_name_unique UNIQUE (modpack_id, name)
);

CREATE INDEX modpack_version_modpack_id_idx ON modpack_version(modpack_id, id DESC);
CREATE INDEX modpack_version_uploader_id_idx ON modpack_version(uploader_id);
CREATE UNIQUE INDEX modpack_version_one_building_idx
    ON modpack_version(modpack_id)
    WHERE status = 'Building';
CREATE INDEX modpack_version_current_ok_idx
    ON modpack_version(modpack_id, id DESC)
    WHERE status = 'Ok';
CREATE UNIQUE INDEX modpack_version_name_lower_unique
    ON modpack_version (modpack_id, LOWER(name));

CREATE TABLE modpack_content (
    version_id UUID NOT NULL REFERENCES modpack_version(id) ON DELETE CASCADE,
    platform TEXT NOT NULL,
    type TEXT NOT NULL,
    project_id TEXT NOT NULL,
    file_id TEXT NOT NULL,
    slug TEXT NOT NULL,
    hash TEXT NOT NULL,
    target_path TEXT,
    side TEXT NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    file_size BIGINT NOT NULL,
    PRIMARY KEY (version_id, platform, project_id),
    CONSTRAINT modpack_content_platform_value
        CHECK (platform IN ('CurseForge', 'Modrinth', 'GitHub')),
    CONSTRAINT modpack_content_type_value
        CHECK (type IN ('Mod', 'ShaderPack', 'ResPack', 'DataPack', 'Other')),
    CONSTRAINT modpack_content_side_value
        CHECK (side IN ('Client', 'Server', 'Both')),
    CONSTRAINT modpack_content_file_size_non_negative
        CHECK (file_size >= 0)
);

CREATE TABLE modpack_upload_result (
    upload_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    modpack_id UUID NOT NULL REFERENCES modpack(id) ON DELETE CASCADE,
    version_id UUID NOT NULL REFERENCES modpack_version(id) ON DELETE CASCADE,
    expires_at BIGINT NOT NULL,
    CONSTRAINT modpack_upload_result_expiry CHECK (expires_at > 0)
);

CREATE INDEX modpack_upload_result_expiry_idx
    ON modpack_upload_result(expires_at);

CREATE OR REPLACE FUNCTION modpack_prevent_mc_loader_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.mc IS DISTINCT FROM OLD.mc OR NEW.loader IS DISTINCT FROM OLD.loader THEN
        RAISE EXCEPTION 'modpack mc and loader are immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER modpack_mc_loader_immutable
    BEFORE UPDATE OF mc, loader ON modpack
    FOR EACH ROW
    EXECUTE FUNCTION modpack_prevent_mc_loader_change();
