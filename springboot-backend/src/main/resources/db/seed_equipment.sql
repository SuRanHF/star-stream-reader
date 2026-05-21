-- ============================================================
-- Phase 5A 装备系统种子数据
-- 5 个初始装备
-- ============================================================

INSERT IGNORE INTO `equipment` (`equipment_key`, `name`, `slot`, `rarity`, `description`, `base_stats_json`, `special_effects_json`, `max_durability`, `repair_cost`, `sell_price`, `enabled`)
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
