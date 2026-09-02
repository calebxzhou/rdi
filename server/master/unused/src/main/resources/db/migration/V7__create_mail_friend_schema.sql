-- PostgreSQL mailbox and friendship storage.  Legacy MongoDB mail is not migrated.
-- Epoch milliseconds are used here so the Kotlin repositories need no optional
-- Exposed java-time module; values are always generated in UTC by PostgreSQL.

CREATE TABLE mail (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    sender_id UUID REFERENCES account(id) ON DELETE SET NULL,
    receiver_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'Normal',
    reference_id UUID,
    unread BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL DEFAULT (extract(epoch FROM clock_timestamp()) * 1000)::BIGINT,
    CONSTRAINT mail_kind_value CHECK (kind IN ('Normal', 'System', 'FriendRequest')),
    CONSTRAINT mail_title_length CHECK (char_length(BTRIM(title)) BETWEEN 1 AND 120),
    CONSTRAINT mail_friend_request_reference_required
        CHECK (kind <> 'FriendRequest' OR reference_id IS NOT NULL)
    -- System/progress mail may contain long logs; normal-mail limits belong to MailService.
);

CREATE INDEX mail_receiver_created_idx ON mail(receiver_id, created_at DESC, id DESC);
CREATE INDEX mail_expiry_idx ON mail(created_at);

CREATE TABLE friend_request (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    requester_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'Pending',
    created_at BIGINT NOT NULL DEFAULT (extract(epoch FROM clock_timestamp()) * 1000)::BIGINT,
    expires_at BIGINT NOT NULL DEFAULT ((extract(epoch FROM clock_timestamp()) * 1000) + 259200000)::BIGINT,
    handled_at BIGINT,
    CONSTRAINT friend_request_distinct_players CHECK (requester_id <> receiver_id),
    CONSTRAINT friend_request_status_value CHECK (status IN ('Pending', 'Accepted', 'Rejected', 'Expired')),
    CONSTRAINT friend_request_expiry_order CHECK (expires_at > created_at),
    CONSTRAINT friend_request_handled_order CHECK (handled_at IS NULL OR handled_at >= created_at)
);

CREATE INDEX friend_request_receiver_status_idx
    ON friend_request(receiver_id, status, created_at DESC);
CREATE INDEX friend_request_requester_created_idx
    ON friend_request(requester_id, created_at DESC);
CREATE UNIQUE INDEX friend_request_pending_pair_idx
    ON friend_request (LEAST(requester_id, receiver_id), GREATEST(requester_id, receiver_id))
    WHERE status = 'Pending';

-- reference_id is deliberately a generic, polymorphic identifier.  The
-- FriendService validates FriendRequest references transactionally; a direct
-- foreign key here would prevent future actionable mail kinds from referring
-- to their own tables.
CREATE UNIQUE INDEX mail_friend_request_reference_idx
    ON mail(reference_id)
    WHERE kind = 'FriendRequest' AND reference_id IS NOT NULL;

CREATE TABLE friendship (
    player_low_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    player_high_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL DEFAULT (extract(epoch FROM clock_timestamp()) * 1000)::BIGINT,
    removed_at BIGINT,
    PRIMARY KEY (player_low_id, player_high_id),
    CONSTRAINT friendship_distinct_players CHECK (player_low_id < player_high_id),
    CONSTRAINT friendship_removed_order CHECK (removed_at IS NULL OR removed_at >= created_at)
);

CREATE INDEX friendship_low_active_idx
    ON friendship(player_low_id, player_high_id) WHERE removed_at IS NULL;
CREATE INDEX friendship_high_active_idx
    ON friendship(player_high_id, player_low_id) WHERE removed_at IS NULL;

CREATE TABLE friend_tag (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    owner_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (extract(epoch FROM clock_timestamp()) * 1000)::BIGINT,
    CONSTRAINT friend_tag_name_length CHECK (name = BTRIM(name) AND char_length(name) BETWEEN 1 AND 12)
);

CREATE UNIQUE INDEX friend_tag_owner_name_lower_unique
    ON friend_tag(owner_id, LOWER(name));
CREATE INDEX friend_tag_owner_idx ON friend_tag(owner_id, name);

CREATE TABLE friend_tag_assignment (
    tag_id UUID NOT NULL REFERENCES friend_tag(id) ON DELETE CASCADE,
    friend_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    PRIMARY KEY (tag_id, friend_id)
);

CREATE INDEX friend_tag_assignment_friend_idx ON friend_tag_assignment(friend_id, tag_id);
