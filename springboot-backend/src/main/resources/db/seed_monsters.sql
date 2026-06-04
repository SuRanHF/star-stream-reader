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
 1),

-- === 金湖站 (geumho_station) ===

('shelter_scavenger', '避难所拾荒者', 'normal', 'common', 'geumho_station',
 '在避难所废墟中翻找物资的幸存者，已被生存的本能吞噬了理性。',
 '{"level":3,"hp":55,"attack":9,"defense":4,"speed":8,"critRate":0.03,"critDamage":1.3}',
 '[]',
 '{"exp":10,"coins":10,"storyFragments":0}',
 '{"items":[{"itemKey":"stamina_bread","quantity":1,"dropRate":0.25}]}',
 '["combat","survivor"]',
 1),

('wisdom_keeper_shadow', '智慧守护者的残影', 'elite', 'uncommon', 'geumho_station',
 '金湖站曾经的智慧守护者留下的残影，仍在守护着避难所的秘密。',
 '{"level":4,"hp":80,"attack":12,"defense":5,"speed":9,"critRate":0.04,"critDamage":1.4}',
 '[]',
 '{"exp":18,"coins":20,"storyFragments":1}',
 '{"items":[{"itemKey":"first_aid_bandage","quantity":1,"dropRate":0.2}],"equipment":[{"equipmentKey":"broken_pipe","dropRate":0.05}]}',
 '["reader","guardian"]',
 1),

-- === 东庙 (dongmyo) ===

('market_thug', '市场恶棍', 'normal', 'common', 'dongmyo',
 '东庙交易市场的地头蛇，靠武力收取保护费的低级化身。',
 '{"level":3,"hp":60,"attack":10,"defense":5,"speed":7,"critRate":0.03,"critDamage":1.3}',
 '[]',
 '{"exp":12,"coins":15,"storyFragments":0}',
 '{"items":[{"itemKey":"stamina_bread","quantity":1,"dropRate":0.2}]}',
 '["combat","criminal"]',
 1),

('old_tale_echo', '旧日故事的残响', 'elite', 'uncommon', 'dongmyo',
 '在东庙的古老物件中残留的故事碎片实体化——它不是生命，而是一段被遗忘的叙事。',
 '{"level":5,"hp":75,"attack":13,"defense":4,"speed":10,"critRate":0.05,"critDamage":1.5}',
 '[]',
 '{"exp":20,"coins":25,"storyFragments":2}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.25}]}',
 '["starstream","narrative"]',
 1),

-- === 忠武路 (chungmuro) ===

('iron_king_knight', '铁王近卫', 'elite', 'rare', 'chungmuro',
 '铁王麾下的忠实骑士，身披生锈的铁甲，手持断裂的长剑。即使王已不在，仍守卫着空荡的王座。',
 '{"level":7,"hp":110,"attack":18,"defense":10,"speed":8,"critRate":0.05,"critDamage":1.6}',
 '[]',
 '{"exp":25,"coins":35,"storyFragments":2,"channelHeat":5}',
 '{"items":[{"itemKey":"first_aid_bandage","quantity":2,"dropRate":0.3}],"equipment":[{"equipmentKey":"cracked_watcher_badge","dropRate":0.08}]}',
 '["king","knight"]',
 1),

('script_censor', '剧本审查者', 'elite', 'rare', 'chungmuro',
 '星流派遣的审查者——一个由文字组成的无面实体。它会吞噬任何试图改写剧本的人。',
 '{"level":8,"hp":95,"attack":20,"defense":6,"speed":12,"critRate":0.06,"critDamage":1.5}',
 '[]',
 '{"exp":22,"coins":30,"storyFragments":3,"channelHeat":10}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.3}]}',
 '["starstream","censor"]',
 1),

-- === 东大门 (dongdaemun) ===

('flame_spark', '火焰余烬', 'normal', 'common', 'dongdaemun',
 '火焰巨人陨落后散落的余烬碎片——仍然燃烧着微弱的星流之火。',
 '{"level":8,"hp":70,"attack":22,"defense":4,"speed":14,"critRate":0.06,"critDamage":1.6}',
 '[]',
 '{"exp":18,"coins":20,"storyFragments":1}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.15}]}',
 '["combat","elemental"]',
 1),

('forgotten_hero_shadow', '被遗忘的英雄残影', 'elite', 'rare', 'dongdaemun',
 '曾在东大门战斗过的某位陨落英雄——他的故事已无人记得，但他的剑仍在挥动。',
 '{"level":9,"hp":130,"attack":22,"defense":12,"speed":9,"critRate":0.07,"critDamage":1.7}',
 '[]',
 '{"exp":30,"coins":40,"storyFragments":3,"channelHeat":8}',
 '{"items":[{"itemKey":"first_aid_bandage","quantity":2,"dropRate":0.3}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.1}]}',
 '["reader","fallen"]',
 1),

-- === 明洞 (myeongdong) ===

('library_guardian', '图书馆守护者', 'elite', 'rare', 'myeongdong',
 '明洞图书馆的守卫——一个由禁书页编织而成的人形。沉默寡言，但对知识的尊重超越一切。',
 '{"level":7,"hp":100,"attack":15,"defense":14,"speed":7,"critRate":0.04,"critDamage":1.4}',
 '[]',
 '{"exp":22,"coins":28,"storyFragments":3,"channelHeat":5}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":2,"dropRate":0.25}]}',
 '["reader","guardian"]',
 1),

('forbidden_page_wraith', '禁书页幽灵', 'normal', 'common', 'myeongdong',
 '从禁书区的被焚书页中升起的幽灵——它携带着读者最深的恐惧和最远的可能。',
 '{"level":6,"hp":65,"attack":17,"defense":5,"speed":11,"critRate":0.05,"critDamage":1.5}',
 '[]',
 '{"exp":15,"coins":18,"storyFragments":2}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.2}]}',
 '["abyss","spectral"]',
 1),

-- === 广津大桥 (gwangjin_bridge) ===

('bridge_warden', '大桥典狱官', 'elite', 'rare', 'gwangjin_bridge',
 '广津大桥的审判者——衡量每一个过桥者的故事重量。若灵魂太轻，便不能通过。',
 '{"level":11,"hp":150,"attack":24,"defense":14,"speed":8,"critRate":0.06,"critDamage":1.6}',
 '[]',
 '{"exp":32,"coins":42,"storyFragments":3,"channelHeat":8}',
 '{"items":[{"itemKey":"station_keeper_trace","quantity":1,"dropRate":0.3}],"equipment":[{"equipmentKey":"cracked_watcher_badge","dropRate":0.1}]}',
 '["king","judge"]',
 1),

('drowned_memory', '溺水记忆', 'normal', 'common', 'gwangjin_bridge',
 '坠入江中的故事碎片凝聚而成的怨灵——每个浪花都是一声未说完的话。',
 '{"level":10,"hp":85,"attack":19,"defense":7,"speed":11,"critRate":0.05,"critDamage":1.5}',
 '[]',
 '{"exp":20,"coins":25,"storyFragments":2}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.2}]}',
 '["abyss","drowned"]',
 1),

-- === 江南站 (gangnam_station) ===

('dungeon_crawler', '地下城爬行者', 'normal', 'common', 'gangnam_station',
 '江南站地下城中最常见的威胁——由被遗弃的探索者残骸拼接而成的怪物。',
 '{"level":12,"hp":120,"attack":22,"defense":10,"speed":8,"critRate":0.05,"critDamage":1.5}',
 '[]',
 '{"exp":24,"coins":30,"storyFragments":1}',
 '{"items":[{"itemKey":"first_aid_bandage","quantity":1,"dropRate":0.25}]}',
 '["combat","undead"]',
 1),

('wall_whisper', '墙壁低语者', 'elite', 'rare', 'gangnam_station',
 '地下城记忆之墙的守护灵——它反复低语着被星流删除的故事。听到它声音的人会获得被遗忘的知识…或失去心智。',
 '{"level":14,"hp":140,"attack":26,"defense":16,"speed":10,"critRate":0.07,"critDamage":1.6}',
 '[]',
 '{"exp":35,"coins":45,"storyFragments":4,"channelHeat":12}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":2,"dropRate":0.35}]}',
 '["reader","keeper"]',
 1),

('nameless_king_remnant', '无名王残渣', 'boss', 'epic', 'gangnam_station',
 '在地下城最深处沉睡的古老存在——据说他是第一代"无王座之王"。拒绝被任何人记住，但无法拒绝存在本身。',
 '{"level":16,"hp":250,"attack":35,"defense":20,"speed":12,"critRate":0.08,"critDamage":2.0}',
 '[]',
 '{"exp":60,"coins":80,"storyFragments":6,"channelHeat":20}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":3,"dropRate":0.5}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.15},{"equipmentKey":"cracked_watcher_badge","dropRate":0.12}]}',
 '["king","ancient"]',
 1),

-- === 首尔森林 (seoul_forest) ===

('tree_root_guardian', '世界树根守护者', 'elite', 'rare', 'seoul_forest',
 '世界树根部孕育的守护者——由树根和故事碎片编织而成的巨兽。它守护的不是宝藏，而是所有被埋葬的记忆。',
 '{"level":16,"hp":180,"attack":28,"defense":22,"speed":7,"critRate":0.06,"critDamage":1.6}',
 '[]',
 '{"exp":38,"coins":50,"storyFragments":4,"channelHeat":12}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":2,"dropRate":0.3}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.08}]}',
 '["abyss","nature"]',
 1),

('star_deer', '星辰鹿', 'rare', 'epic', 'seoul_forest',
 '在世界树周围游荡的白色巨鹿。它的角上刻着已灭绝星座的名字——每一条都是一个被遗忘的故事。温和但不可侵犯。',
 '{"level":17,"hp":200,"attack":30,"defense":18,"speed":15,"critRate":0.08,"critDamage":1.8}',
 '[]',
 '{"exp":50,"coins":65,"storyFragments":5,"channelHeat":18}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":3,"dropRate":0.4}]}',
 '["starstream","celestial"]',
 1),

-- === 恶魔之门 (demon_gate) ===

('demon_overseer', '恶魔监工', 'elite', 'rare', 'demon_gate',
 '恶魔之门前的守卫——它手持燃烧的铁鞭，驱赶着所有试图逃离旧世界的灵魂。',
 '{"level":19,"hp":220,"attack":35,"defense":20,"speed":11,"critRate":0.07,"critDamage":1.7}',
 '[]',
 '{"exp":45,"coins":55,"storyFragments":5,"channelHeat":15}',
 '{"items":[{"itemKey":"station_keeper_trace","quantity":2,"dropRate":0.3}]}',
 '["abyss","demon"]',
 1),

('gatekeeper_of_old_world', '旧世界守门人', 'boss', 'epic', 'demon_gate',
 '恶魔之门最深处的看守者——它不属于新世界，也不属于旧世界。永恒地站在门前，注视着每一个选择踏入未知的化身。',
 '{"level":22,"hp":350,"attack":42,"defense":25,"speed":10,"critRate":0.08,"critDamage":2.0}',
 '[]',
 '{"exp":80,"coins":100,"storyFragments":8,"channelHeat":30}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":5,"dropRate":0.5}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.2},{"equipmentKey":"cracked_watcher_badge","dropRate":0.15}]}',
 '["king","gatekeeper"]',
 1),

('chaos_spawn', '混沌滋生体', 'normal', 'common', 'demon_gate',
 '从门缝渗出的混沌能量凝聚而成——它不断变化形态，即使它自己也不知道下一秒会变成什么。',
 '{"level":18,"hp":150,"attack":32,"defense":12,"speed":16,"critRate":0.09,"critDamage":1.6}',
 '[]',
 '{"exp":30,"coins":40,"storyFragments":2}',
 '{"items":[{"itemKey":"stamina_bread","quantity":2,"dropRate":0.3}]}',
 '["combat","chaos"]',
 1);
