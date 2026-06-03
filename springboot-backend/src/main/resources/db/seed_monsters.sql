-- ============================================================
-- Phase 5C 怪物种子数据
-- ============================================================

INSERT IGNORE INTO `monsters` (`monster_key`, `name`, `type`, `rarity`, `location_key`, `description`, `stats_json`, `skills_json`, `rewards_json`, `drops_json`, `narrative_tags_json`, `enabled`)
VALUES

-- 1. 变异站鼠 (废弃车站)
('station_rat', '变异站鼠', 'normal', 'common', 'ruined_station',
 '一只因星流辐射而变异的巨大老鼠，在废弃车站的阴影中肆虐。',
 '{"level":1,"hp":35,"attack":6,"defense":2,"speed":8,"critRate":0.02,"critDamage":1.3}',
 '[]',
 '{"exp":8,"coins":8,"storyFragments":0}',
 '{"items":[{"itemKey":"stamina_bread","quantity":1,"dropRate":0.2}]}',
 '["beast"]',
 1),

-- 2. 破碎化身影子 (废弃车站)
('broken_avatar_shadow', '破碎化身影子', 'normal', 'common', 'ruined_station',
 '一个身形残缺、面容模糊的化身影子，在车站深处漫无目的地游荡。',
 '{"level":2,"hp":50,"attack":8,"defense":3,"speed":7,"critRate":0.03,"critDamage":1.4}',
 '[]',
 '{"exp":12,"coins":12,"storyFragments":1}',
 '{"items":[{"itemKey":"first_aid_bandage","quantity":1,"dropRate":0.15}],"equipment":[{"equipmentKey":"broken_pipe","dropRate":0.05}]}',
 '["abyss","corrupted"]',
 1),

-- 3. 车站看门人残影 (废弃车站 - 精英)
('station_keeper_fragment', '车站看门人残影', 'elite', 'uncommon', 'ruined_station',
 '曾经守护着这座车站的存在，即便死后仍在履行职责。比普通残影强大得多。',
 '{"level":4,"hp":90,"attack":14,"defense":6,"speed":9,"critRate":0.05,"critDamage":1.5}',
 '[]',
 '{"exp":25,"coins":30,"storyFragments":2,"channelHeat":5}',
 '{"items":[{"itemKey":"station_keeper_trace","quantity":1,"dropRate":0.5}],"equipment":[{"equipmentKey":"cracked_watcher_badge","dropRate":0.08}]}',
 '["king","guardian"]',
 1),

-- 4. 商场饥饿回响 (断裂商场)
('mall_hunger_echo', '商场饥饿回响', 'normal', 'common', 'broken_mall',
 '断裂商场中残存的饥饿记忆实体化，散发着令人不安的气息。',
 '{"level":5,"hp":100,"attack":16,"defense":7,"speed":10,"critRate":0.04,"critDamage":1.5}',
 '[]',
 '{"exp":28,"coins":35,"storyFragments":1}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.2}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.05}]}',
 '["starstream","starving"]',
 1);
