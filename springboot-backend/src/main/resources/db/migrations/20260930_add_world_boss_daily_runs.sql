-- Persist one scheduled world-boss opening per application-local calendar date.
-- Backfill dates that already have a boss so enabling the guard mid-day cannot reopen one.
CREATE TABLE IF NOT EXISTS `world_boss_daily_runs` (
    `run_date`   DATE         NOT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`run_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界Boss每日场次记录';

INSERT IGNORE INTO `world_boss_daily_runs` (`run_date`, `created_at`)
SELECT DATE(`start_at`), MIN(`created_at`)
FROM `world_bosses`
WHERE `start_at` IS NOT NULL
GROUP BY DATE(`start_at`);
