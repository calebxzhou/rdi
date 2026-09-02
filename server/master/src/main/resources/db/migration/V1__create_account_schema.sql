CREATE TABLE account (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    pwd TEXT NOT NULL,
    qq TEXT NOT NULL UNIQUE,
    msid UUID UNIQUE,
    is_slim BOOLEAN NOT NULL DEFAULT TRUE,
    skin TEXT NOT NULL DEFAULT 'https://littleskin.cn/textures/526fe866ed25a7ee1cf894b81a2199aaa03f139803623a25a793f6ae57e22f02',
    cape TEXT,
    CONSTRAINT account_name_not_blank CHECK (char_length(BTRIM(name)) > 0),
    CONSTRAINT account_password_length CHECK (char_length(pwd) BETWEEN 6 AND 16),
    CONSTRAINT account_qq_format CHECK (qq ~ '^[0-9]{5,10}$'),
    CONSTRAINT account_skin_not_blank CHECK (char_length(BTRIM(skin)) > 0)
);
