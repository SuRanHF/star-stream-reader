-- Run once against an existing database before deploying the idempotent combat API.
-- Existing rows keep NULL request keys and remain valid; MySQL permits multiple NULLs
-- in this unique index.
ALTER TABLE `battle_logs`
    ADD COLUMN `request_key` VARCHAR(64) DEFAULT NULL COMMENT '战斗请求幂等键' AFTER `player_id`,
    ADD COLUMN `request_action` VARCHAR(20) DEFAULT NULL COMMENT 'fight / attack / flee' AFTER `request_key`,
    ADD COLUMN `result_json` JSON DEFAULT NULL COMMENT '可重放的战斗结算响应' AFTER `drops_json`;

CREATE UNIQUE INDEX `uk_bl_player_request`
    ON `battle_logs` (`player_id`, `request_key`);
