CREATE TABLE host2 (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name TEXT NOT NULL,
    intro TEXT NOT NULL DEFAULT '暂无简介',
    owner_id UUID NOT NULL,
    mc_version TEXT NOT NULL,
    mod_loader TEXT NOT NULL,
    port INTEGER NOT NULL UNIQUE,
    whitelist BOOLEAN NOT NULL,
    setup_status TEXT NOT NULL,
    CONSTRAINT host2_name_length
        CHECK (char_length(BTRIM(name)) BETWEEN 1 AND 32),
    CONSTRAINT host2_intro_length
        CHECK (char_length(BTRIM(intro)) <= 200),
    CONSTRAINT host2_port_range
        CHECK (port BETWEEN 30000 AND 39999),
    CONSTRAINT host2_setup_status_value
        CHECK (
            setup_status IN (
                'AWAITING_UPLOAD',
                'PROCESSING',
                'READY',
                'FAILED'
            )
        )
);

CREATE INDEX host2_owner_id_idx ON host2(owner_id);

CREATE TABLE host2_member (
    host_id UUID NOT NULL
        REFERENCES host2(id)
        ON DELETE CASCADE,
    player_id UUID NOT NULL,
    role TEXT NOT NULL,
    PRIMARY KEY (host_id, player_id),
    CONSTRAINT host2_member_role_value
        CHECK (role IN ('ADMIN', 'MEMBER'))
);

CREATE INDEX host2_member_player_id_idx ON host2_member(player_id);

CREATE TABLE host2_mod (
    host_id UUID NOT NULL
        REFERENCES host2(id)
        ON DELETE CASCADE,
    platform TEXT NOT NULL,
    project_id TEXT NOT NULL,
    file_id TEXT NOT NULL,
    slug TEXT NOT NULL,
    hash TEXT NOT NULL,
    side TEXT NOT NULL,
    PRIMARY KEY (host_id, platform, project_id),
    CONSTRAINT host2_mod_platform_value
        CHECK (platform IN ('cf', 'mr', 'github')),
    CONSTRAINT host2_mod_side_value
        CHECK (side IN ('CLIENT', 'SERVER', 'BOTH', 'UNKNOWN'))
);
