-- Covers the player/event/first-completion aggregation used by the rolling story discovery ranking.
-- This migration changes only the story-log index; it does not modify player records.

DROP PROCEDURE IF EXISTS add_story_discovery_first_index;
DELIMITER //
CREATE PROCEDURE add_story_discovery_first_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'player_story_logs'
          AND index_name = 'idx_psl_discovery_first'
    ) THEN
        ALTER TABLE `player_story_logs`
            ADD INDEX `idx_psl_discovery_first` (`player_id`, `event_key`, `created_at`, `choice_index`);
    END IF;
END//
DELIMITER ;

CALL add_story_discovery_first_index();
DROP PROCEDURE add_story_discovery_first_index;
