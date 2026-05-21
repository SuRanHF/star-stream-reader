-- ============================================================
-- Phase 5B: 技能系统种子数据 (6 个初始技能)
-- 使用 INSERT IGNORE 保证幂等
-- ============================================================

INSERT IGNORE INTO `skills` (`skill_key`, `name`, `type`, `rarity`, `description`, `unlock_conditions_json`, `effects_json`, `cost_json`, `cooldown_seconds`, `enabled`) VALUES

('steady_breath', '稳定呼吸', 'passive', 'common',
 '学会控制呼吸后，你在探索中的体力消耗略微降低。',
 '{"levelMin": 2}',
 '{"maxStamina": 5, "staminaCostReduce": 0.03}',
 '{"coins": 50}',
 0, 1),

('emergency_focus', '紧急专注', 'passive', 'common',
 '危急情况下保持判断力，提升少量洞察。',
 '{"insightMin": 1}',
 '{"insight": 1}',
 '{"coins": 50}',
 0, 1),

('reader_observation', '读者观测', 'exploration', 'uncommon',
 '你开始学会从事件缝隙中观察故事结构。',
 '{"storyFragmentsMin": 10}',
 '{"storyEventBonus": 0.03, "channelHeatGainRate": 0.03}',
 '{"storyFragments": 5, "coins": 100}',
 0, 1),

('iron_resolve', '钢铁意志', 'combat', 'uncommon',
 '在战斗中更难被击溃。',
 '{"levelMin": 5, "willpowerMin": 1}',
 '{"defense": 3, "maxHp": 10}',
 '{"coins": 150}',
 0, 1),

('station_survival', '废站生存术', 'exploration', 'rare',
 '在废弃车站中行动更加熟练。',
 '{"locationExploreCount": {"ruined_station": 10}}',
 '{"attack": 2, "defense": 2, "staminaCostReduce": 0.05}',
 '{"coins": 200, "storyFragments": 10}',
 0, 1),

('narrative_pressure_seed', '叙事压制雏形', 'narrative', 'rare',
 '你开始理解故事位格之间的压制关系。',
 '{"avatarRankMin": "D", "channelHeatMin": 100}',
 '{"narrativePressureBonus": 0.03, "worldLineShift": 1}',
 '{"coins": 300, "storyFragments": 15}',
 0, 1);
