CREATE TABLE IF NOT EXISTS {{table:links}} (
    minecraft_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
    minecraft_name VARCHAR(64) NOT NULL,
    discord_id VARCHAR(32) NOT NULL UNIQUE,
    linked_at BIGINT NOT NULL
);
