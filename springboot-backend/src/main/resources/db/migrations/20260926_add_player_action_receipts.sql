-- Request-level idempotency receipts for successful player actions.
-- This migration creates schema only and does not modify player data.
CREATE TABLE IF NOT EXISTS `player_action_receipts` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`         BIGINT       NOT NULL,
    `action_type`       VARCHAR(40)  NOT NULL,
    `idempotency_key`   VARCHAR(64)  NOT NULL,
    `request_hash`      CHAR(64)     NOT NULL,
    `response_json`     JSON         NOT NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_action_receipt_player_action_key` (`player_id`, `action_type`, `idempotency_key`),
    KEY `idx_action_receipt_created` (`created_at`),
    CONSTRAINT `fk_action_receipt_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家写操作幂等回执';
