-- ============================================================
-- 全量编码修复脚本
-- 修复 equipment, quests, skills 中的乱码
-- 使用 Python + pymysql 执行，确保 UTF-8 编码正确
-- 仅更新静态定义；不删除玩家装备、任务进度、技能记录或历程日志。
-- ============================================================

-- ============================================================
-- Phase 1: 修复 equipment 表
-- 按 equipment_key 修复名称/描述，保留引用记录及现有数值、掉落平衡和启用状态。
-- ============================================================
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
 100, 40, 60, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`);

-- ============================================================
-- Phase 2: 修复 quests 表
-- 按 quest_key 修复显示文本；保留所有玩家任务实例、进度日志及现行任务目标/奖励配置。
-- ============================================================
INSERT INTO `quests` (`quest_key`, `title`, `description`, `quest_type`, `category`, `target_type`, `target_value`, `rewards_json`, `reset_cycle`, `sort_order`) VALUES
('daily_explore_3',    '探索世界',     '进行3次探索',           'daily',  'explore',    'explore_count',          3,  '{"coins":50,"exp":20}',                'daily',  1),
('daily_combat_3',     '战斗修行',     '赢得3场战斗',           'daily',  'combat',     'combat_win_count',        3,  '{"coins":60,"exp":25}',                'daily',  2),
('daily_trade_1',      '市场交易',     '完成1次购买',           'daily',  'trade',      'trade_buy_count',         1,  '{"coins":30,"exp":10}',                'daily',  3),
('weekly_pk_5',        '擂台争霸',     '参与5次PK',             'weekly', 'pk',         'pk_participate_count',    5,  '{"coins":200,"exp":80,"storyFragments":3}',  'weekly', 1),
('weekly_boss_3',      '世界Boss挑战', '参与3次世界Boss攻击',   'weekly', 'world_boss', 'world_boss_attack_count', 3,  '{"coins":250,"exp":100,"storyFragments":5}', 'weekly', 2),
('achv_level_5',       '初露锋芒',     '达到5级',               'achievement', 'growth', 'level_reach',         5,  '{"coins":500,"exp":150,"titleKey":"growth_novice"}',        'none',   1),
('achv_rank_C',        '位阶晋升',     '达到Avatar Rank C',     'achievement', 'growth', 'avatar_rank_reach',   1,  '{"coins":1000,"exp":300,"storyFragments":10}',              'none',   2),
('daily_trade_sell_1', '交易周转',     '成功出售1件物品或装备', 'daily', 'trade', 'trade_sell_count', 1,
 '{"coins":30,"exp":10}', 'daily', 4),
('weekly_pk_win_3',    '擂台胜者',     '赢得3场玩家对战',       'weekly', 'pk', 'pk_win_count', 3,
 '{"coins":120,"exp":50,"storyFragments":2}', 'weekly', 3),
('achv_faction_contribution_50', '阵营功勋', '累计向所属阵营贡献50点', 'achievement', 'faction',
 'faction_contribution_value', 50, '{"coins":200,"exp":80,"storyFragments":3}', 'none', 3)
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `description` = VALUES(`description`);

-- ============================================================
-- Phase 3: 修复 skills 表
-- 按 skill_key 修复显示文本；保留玩家已学技能及现行技能效果、成本和启用状态。
-- ============================================================
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
 0, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`);

-- ============================================================
-- Phase 4: player_logs 安全边界
-- 不按固定数据库自增 ID 删除日志。ID 会因数据库不同而指向不同玩家历程；
-- 需要人工先查询确认具体乱码内容，再使用单独审核过的修复操作。
-- ============================================================

-- ============================================================
-- Phase 5: 修复 locations 中 broken_market 与 broken_mall 重名问题
-- broken_market 是另一个更高级的商城区域，重命名为"断裂集市"以区分
-- ============================================================
UPDATE `locations` SET `name` = '断裂集市', `description` = '比断裂商场更危险的废墟区，资源虽多但风险也更高。断裂的混凝土之间偶尔能看到发光的物品。' WHERE `location_key` = 'broken_market';
