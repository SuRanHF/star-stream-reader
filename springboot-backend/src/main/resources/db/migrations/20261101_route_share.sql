-- One active snapshot per player. The public code is never stored: only its SHA-256 hash.
CREATE TABLE IF NOT EXISTS `route_shares` (
    `player_id` BIGINT NOT NULL,
    `code_hash` CHAR(64) NOT NULL,
    `nonce` VARCHAR(32) NOT NULL,
    `snapshot_json` JSON NOT NULL,
    `created_at` DATETIME NOT NULL,
    `expires_at` DATETIME NOT NULL,
    PRIMARY KEY (`player_id`),
    UNIQUE KEY `uk_route_shares_code_hash` (`code_hash`),
    KEY `idx_route_shares_expires` (`expires_at`),
    CONSTRAINT `fk_route_shares_player` FOREIGN KEY (`player_id`)
        REFERENCES `players` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='七天只读路线快照';
