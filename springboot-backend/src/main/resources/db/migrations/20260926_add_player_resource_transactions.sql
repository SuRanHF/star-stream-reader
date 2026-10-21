-- Append-only player resource ledger. This migration only creates schema; it does not backfill
-- historical balances or mutate player data. Apply through the normal deployment process.
CREATE TABLE IF NOT EXISTS `player_resource_transactions` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`             BIGINT       NOT NULL,
    `resource_type`         VARCHAR(32)  NOT NULL COMMENT 'currency / item / equipment / experience / energy / vitality / progress',
    `resource_key`          VARCHAR(100) NOT NULL COMMENT 'coins / story_fragments / item or progress key',
    `amount`                BIGINT       NOT NULL COMMENT 'Signed change amount',
    `balance_before`        BIGINT       NOT NULL,
    `balance_after`         BIGINT       NOT NULL,
    `source_type`           VARCHAR(64)  NOT NULL,
    `source_id`             VARCHAR(128) DEFAULT NULL,
    `transaction_group_id`  VARCHAR(64)  DEFAULT NULL,
    `idempotency_key`       VARCHAR(160) NOT NULL,
    `metadata_json`         JSON         DEFAULT NULL,
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resource_player_idempotency` (`player_id`, `idempotency_key`),
    KEY `idx_resource_player_created` (`player_id`, `created_at`, `id`),
    KEY `idx_resource_source` (`source_type`, `source_id`),
    CONSTRAINT `fk_resource_transaction_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家资源变更账本';
