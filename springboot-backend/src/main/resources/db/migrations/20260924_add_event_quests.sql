-- Add player-facing goals for progress event types already emitted by gameplay.
-- INSERT IGNORE keeps this safe for fresh databases that already used init.sql.
INSERT IGNORE INTO `quests`
    (`quest_key`, `title`, `description`, `quest_type`, `category`, `target_type`, `target_value`, `rewards_json`, `reset_cycle`, `sort_order`)
VALUES
    ('daily_trade_sell_1', '交易周转', '成功出售1件物品或装备', 'daily', 'trade', 'trade_sell_count', 1, '{"coins":30,"exp":10}', 'daily', 4),
    ('weekly_pk_win_3', '擂台胜者', '赢得3场玩家对战', 'weekly', 'pk', 'pk_win_count', 3, '{"coins":120,"exp":50,"storyFragments":2}', 'weekly', 3),
    ('achv_faction_contribution_50', '阵营功勋', '累计向所属阵营贡献50点', 'achievement', 'faction', 'faction_contribution_value', 50, '{"coins":200,"exp":80,"storyFragments":3}', 'none', 3);
