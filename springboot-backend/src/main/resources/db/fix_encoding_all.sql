-- ============================================================
-- 全量编码修复脚本
-- 修复 equipment, quests, skills, player_logs 中的乱码
-- 使用 Python + pymysql 执行，确保 UTF-8 编码正确
-- ============================================================

-- ============================================================
-- Phase 1: 修复 equipment 表
-- 删除乱码行后重新插入正确数据 (来源 seed_equipment.sql)
-- ============================================================
DELETE FROM `player_equipment` WHERE `equipment_key` IN
  ('broken_pipe', 'station_guard_coat', 'cracked_watcher_badge', 'reader_scrap_blade', 'abyss_touched_relic');
DELETE FROM `equipment` WHERE `equipment_key` IN
  ('broken_pipe', 'station_guard_coat', 'cracked_watcher_badge', 'reader_scrap_blade', 'abyss_touched_relic');

INSERT INTO `equipment` (`equipment_key`, `name`, `slot`, `rarity`, `description`, `base_stats_json`, `special_effects_json`, `max_durability`, `repair_cost`, `sell_price`, `enabled`)
VALUES

-- 1. 弯曲钢管 (weapon, common)
('broken_pipe', '弯曲钢管', 'weapon', 'common',
 '废墟中随手捡到的钢管，勉强可以作为武器。',
 '{"attack":5}',
 NULL,
 100, 10, 10, 1),

-- 2. 车站守卫外套 (armor, common)
('station_guard_coat', '车站守卫外套', 'armor', 'common',
 '破旧但还能抵挡部分伤害的外套。',
 '{"defense":4,"maxHp":10}',
 NULL,
 100, 10, 10, 1),

-- 3. 破裂的观测徽章 (accessory, uncommon)
('cracked_watcher_badge', '破裂的观测徽章', 'accessory', 'uncommon',
 '带有微弱频道残响的徽章。',
 '{"insight":1,"channelHeat":5}',
 NULL,
 100, 20, 25, 1),

-- 4. 读者残页短刃 (weapon, rare)
('reader_scrap_blade', '读者残页短刃', 'weapon', 'rare',
 '残页凝成的短刃，会对异常剧情产生反应。',
 '{"attack":10,"critRate":0.03}',
 '{"storyEventBonus":0.02}',
 100, 30, 50, 1),

-- 5. 深渊触痕遗物 (relic, rare)
('abyss_touched_relic', '深渊触痕遗物', 'relic', 'rare',
 '世界线偏移后残留的异常遗物。',
 '{"worldLineShift":2,"willpower":2}',
 '{"narrativePressureBonus":0.02}',
 100, 40, 60, 1);

-- ============================================================
-- Phase 2: 修复 quests 表
-- 删除乱码行后重新插入正确数据 (来源 init.sql)
-- ============================================================
DELETE FROM `player_quests`;
DELETE FROM `quest_progress_logs`;
DELETE FROM `quests`;

INSERT INTO `quests` (`quest_key`, `title`, `description`, `quest_type`, `category`, `target_type`, `target_value`, `rewards_json`, `reset_cycle`, `sort_order`) VALUES
('daily_explore_3',    '探索世界',     '进行3次探索',           'daily',  'explore',    'explore_count',          3,  '{"coins":50,"exp":20}',                'daily',  1),
('daily_combat_3',     '战斗修行',     '赢得3场战斗',           'daily',  'combat',     'combat_win_count',        3,  '{"coins":60,"exp":25}',                'daily',  2),
('daily_trade_1',      '市场交易',     '完成1次购买',           'daily',  'trade',      'trade_buy_count',         1,  '{"coins":30,"exp":10}',                'daily',  3),
('weekly_pk_5',        '擂台争霸',     '参与5次PK',             'weekly', 'pk',         'pk_participate_count',    5,  '{"coins":200,"exp":80,"storyFragments":3}',  'weekly', 1),
('weekly_boss_3',      '世界Boss挑战', '参与3次世界Boss攻击',   'weekly', 'world_boss', 'world_boss_attack_count', 3,  '{"coins":250,"exp":100,"storyFragments":5}', 'weekly', 2),
('achv_level_5',       '初露锋芒',     '达到5级',               'achievement', 'growth', 'level_reach',         5,  '{"coins":500,"exp":150,"titleKey":"growth_novice"}',        'none',   1),
('achv_rank_C',        '位阶晋升',     '达到Avatar Rank C',     'achievement', 'growth', 'avatar_rank_reach',   1,  '{"coins":1000,"exp":300,"storyFragments":10}',              'none',   2);

-- ============================================================
-- Phase 3: 修复 skills 表
-- 删除乱码行后重新插入正确数据 (来源 seed_skills.sql)
-- ============================================================
DELETE FROM `player_skills` WHERE `skill_key` IN
  ('steady_breath', 'emergency_focus', 'reader_observation', 'iron_resolve', 'station_survival', 'narrative_pressure_seed');
DELETE FROM `skills`;

INSERT INTO `skills` (`skill_key`, `name`, `type`, `rarity`, `description`, `unlock_conditions_json`, `effects_json`, `cost_json`, `cooldown_seconds`, `enabled`) VALUES

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

-- ============================================================
-- Phase 4: 清理 player_logs 中的乱码记录
-- 删除 message 中包含明显乱码特征的记录
-- 乱码特征: 非 UTF-8 连续字节序列
-- ============================================================
DELETE FROM `player_logs` WHERE id IN (12, 13, 14, 16, 38, 40, 52, 73, 76, 77, 78, 79, 80);

-- ============================================================
-- Phase 5: 修复 locations 中 broken_market 与 broken_mall 重名问题
-- broken_market 是另一个更高级的商城区域，重命名为"断裂集市"以区分
-- ============================================================
UPDATE `locations` SET `name` = '断裂集市', `description` = '比断裂商场更危险的废墟区，资源虽多但风险也更高。断裂的混凝土之间偶尔能看到发光的物品。' WHERE `location_key` = 'broken_market';
