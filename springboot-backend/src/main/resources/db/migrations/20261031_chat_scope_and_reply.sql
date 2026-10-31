-- Purpose: isolate team/faction chat by scope and persist reply references.
-- Scope: chat_messages only; adds scope_id, reply_to_id and (channel, scope_id, id) index.
-- Player data: existing chat rows are retained. Old team/faction rows have NULL scope_id
-- and are intentionally excluded by the new service; no player records are rewritten.
-- Repeat execution: each DDL is guarded by information_schema for the current schema.
-- Prerequisite: chat_messages exists in the selected MySQL 8 schema. Check schema and
-- backup per docs/database-migration-runbook.md before an operator applies this file.
-- Post-check: SHOW COLUMNS FROM chat_messages; SHOW INDEX FROM chat_messages;
-- SELECT channel, COUNT(*) FROM chat_messages WHERE channel IN ('team','faction')
--   AND scope_id IS NULL GROUP BY channel;

SET @chat_scope_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_messages' AND COLUMN_NAME = 'scope_id'
);
SET @chat_scope_sql := IF(@chat_scope_column_exists = 0,
    'ALTER TABLE `chat_messages` ADD COLUMN `scope_id` VARCHAR(100) NULL COMMENT ''party_no or faction_key''',
    'SELECT 1');
PREPARE chat_scope_stmt FROM @chat_scope_sql;
EXECUTE chat_scope_stmt;
DEALLOCATE PREPARE chat_scope_stmt;

SET @chat_reply_column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_messages' AND COLUMN_NAME = 'reply_to_id'
);
SET @chat_reply_sql := IF(@chat_reply_column_exists = 0,
    'ALTER TABLE `chat_messages` ADD COLUMN `reply_to_id` BIGINT NULL',
    'SELECT 1');
PREPARE chat_reply_stmt FROM @chat_reply_sql;
EXECUTE chat_reply_stmt;
DEALLOCATE PREPARE chat_reply_stmt;

SET @chat_scope_index_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_messages'
      AND INDEX_NAME = 'idx_chat_channel_scope_id'
);
SET @chat_index_sql := IF(@chat_scope_index_exists = 0,
    'ALTER TABLE `chat_messages` ADD INDEX `idx_chat_channel_scope_id` (`channel`, `scope_id`, `id`)',
    'SELECT 1');
PREPARE chat_index_stmt FROM @chat_index_sql;
EXECUTE chat_index_stmt;
DEALLOCATE PREPARE chat_index_stmt;
