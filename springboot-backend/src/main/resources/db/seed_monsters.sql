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
 '{"items":[{"itemKey":"stamina_bread","quantity":1,"dropRate":0.2},{"itemKey":"monster_bone","quantity":1,"dropRate":0.4}]}',
 '["beast"]',
 1),

-- 2. 破碎化身影子 (废弃车站)
('broken_avatar_shadow', '破碎化身影子', 'normal', 'common', 'ruined_station',
 '一个身形残缺、面容模糊的化身影子，在车站深处漫无目的地游荡。',
 '{"level":2,"hp":50,"attack":8,"defense":3,"speed":7,"critRate":0.03,"critDamage":1.4}',
 '[]',
 '{"exp":12,"coins":12,"storyFragments":1}',
 '{"items":[{"itemKey":"first_aid_bandage","quantity":1,"dropRate":0.15},{"itemKey":"small_hp_potion","quantity":1,"dropRate":0.12}],"equipment":[{"equipmentKey":"broken_pipe","dropRate":0.05}]}',
 '["abyss","corrupted"]',
 1),

-- 3. 车站看门人残影 (废弃车站 - 精英)
('station_keeper_fragment', '车站看门人残影', 'elite', 'uncommon', 'ruined_station',
 '曾经守护着这座车站的存在，即便死后仍在履行职责。比普通残影强大得多。',
 '{"level":4,"hp":90,"attack":14,"defense":6,"speed":9,"critRate":0.05,"critDamage":1.5}',
 '[]',
 '{"exp":25,"coins":30,"storyFragments":2,"channelHeat":5}',
 '{"items":[{"itemKey":"station_keeper_trace","quantity":1,"dropRate":0.5}],"equipment":[{"equipmentKey":"cracked_watcher_badge","dropRate":0.08},{"equipmentKey":"station_guard_coat","dropRate":0.12}]}',
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
 '{"items":[{"itemKey":"first_aid_bandage","quantity":1,"dropRate":0.2},{"itemKey":"purification_water","quantity":1,"dropRate":0.15}],"equipment":[{"equipmentKey":"broken_pipe","dropRate":0.05}]}',
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
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.25},{"itemKey":"story_fragment","quantity":1,"dropRate":0.3}],"equipment":[{"equipmentKey":"hunters_ring","dropRate":0.08}]}',
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

('iron_king_trial', '铁王的钢铁化投影', 'elite', 'rare', 'chungmuro',
 '铁王没有以真身迎战，而是以钢铁化能力驱使一道星流投影逼你证明自己的选择。它保留了铁王的力量，却不是铁王的全部实力。',
 '{"level":7,"hp":450,"attack":30,"defense":22,"speed":12,"critRate":0,"critDamage":1.5}',
 '[]',
 '{"exp":30,"coins":25,"storyFragments":1,"channelHeat":3}',
 '{}',
 '["king","steel","story"]',
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
 '{"items":[{"itemKey":"first_aid_bandage","quantity":2,"dropRate":0.3}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.1},{"equipmentKey":"rusty_dagger","dropRate":0.08}]}',
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
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.2},{"itemKey":"abyss_shard","quantity":1,"dropRate":0.08}]}',
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
 '{"items":[{"itemKey":"story_fragment_core","quantity":5,"dropRate":0.5},{"itemKey":"abyss_residue","quantity":1,"dropRate":0.2}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.2},{"equipmentKey":"cracked_watcher_badge","dropRate":0.15},{"equipmentKey":"abyss_touched_relic","dropRate":0.08}]}',
 '["king","gatekeeper"]',
 1),

('chaos_spawn', '混沌滋生体', 'normal', 'common', 'demon_gate',
 '从门缝渗出的混沌能量凝聚而成——它不断变化形态，即使它自己也不知道下一秒会变成什么。',
 '{"level":18,"hp":150,"attack":32,"defense":12,"speed":16,"critRate":0.09,"critDamage":1.6}',
 '[]',
 '{"exp":30,"coins":40,"storyFragments":2}',
 '{"items":[{"itemKey":"stamina_bread","quantity":2,"dropRate":0.3}]}',
 '["combat","chaos"]',
 1),

-- === 与五个探索遭遇文案一一对应的独立怪物 ===
('fire_lord_projection', '火之主的世界线投影', 'normal', 'common', 'dongdaemun',
 '焦黑广场上由星流之火凝聚的人形投影，胸腔中燃烧着一颗跃动的核心；它并非火之主本体。',
 '{"level":8,"hp":100,"attack":16,"defense":8,"speed":14,"critRate":0.06,"critDamage":1.6}',
 '[]',
 '{"exp":24,"coins":30,"storyFragments":1}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.15}]}',
 '["starstream","fire","projection"]',
 1),

('library_hunger_echo', '图书馆饥饿回声', 'normal', 'common', 'myeongdong',
 '由被诅咒书籍吞噬的记忆残渣凝成的实体；翻动的书页是它向外寻找新故事的触须。',
 '{"level":6,"hp":80,"attack":15,"defense":6,"speed":11,"critRate":0.04,"critDamage":1.5}',
 '[]',
 '{"exp":15,"coins":18,"storyFragments":2}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.2}]}',
 '["starstream","library","starving"]',
 1),

('bridge_hunger_entity', '饥饿的桥下之物', 'normal', 'common', 'gwangjin_bridge',
 '由桥下黑水凝成的人形，会从周围空气中吸取故事；它的轮廓像溺水者，却不是普通怨灵。',
 '{"level":10,"hp":95,"attack":18,"defense":8,"speed":12,"critRate":0.05,"critDamage":1.5}',
 '[]',
 '{"exp":22,"coins":28,"storyFragments":2}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.2}]}',
 '["starstream","water","starving"]',
 1),

('dungeon_guardian_program', '地下城守卫程序', 'normal', 'common', 'gangnam_station',
 '旧世界遗留的自动战斗程序，以古老铠甲作为躯壳；即使操作者早已消失，它仍执行守门指令。',
 '{"level":12,"hp":140,"attack":18,"defense":12,"speed":10,"critRate":0.05,"critDamage":1.5}',
 '[]',
 '{"exp":28,"coins":34,"storyFragments":1}',
 '{"items":[{"itemKey":"first_aid_bandage","quantity":1,"dropRate":0.25}]}',
 '["ancient","guardian","program"]',
 1),

('corrupted_forest_beast', '魔化林兽', 'normal', 'common', 'seoul_forest',
 '被星流侵蚀的森林野兽，眼中燃起幽绿火焰；它会破坏拦路树木，并凭本能袭击闯入者。',
 '{"level":15,"hp":130,"attack":20,"defense":10,"speed":13,"critRate":0.06,"critDamage":1.6}',
 '[]',
 '{"exp":32,"coins":42,"storyFragments":2}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":1,"dropRate":0.2}]}',
 '["nature","corrupted","beast"]',
 1),

-- ============================================================
-- Phase 5D 高级Boss种子数据 (9个)
-- ============================================================

-- 高级Boss 1: 废弃车站Boss —— 看门人完全体
('station_keeper_full', '看门人完全体', 'boss', 'rare', 'ruined_station',
 '车站看门人不再是一缕残影——它的躯体已经完全实体化，锈蚀的铠甲下流淌着星流能量。它用空洞的双眼注视着你，仿佛在审判你是否有资格踏过这座车站。废弃车站的一切阴暗角落都是它的领地。',
 '{"level":15,"hp":420,"attack":48,"defense":28,"speed":10,"critRate":0.08,"critDamage":1.8}',
 '[{"skillKey":"keeper_gaze","name":"看门人凝视","description":"锁定目标，降低目标30%速度持续2回合","cooldown":3,"damageType":"psychic"},{"skillKey":"rust_cage","name":"锈蚀牢笼","description":"以锈蚀铁链束缚目标，造成攻击力120%伤害并禁锢1回合","cooldown":4,"damageType":"physical"}]',
 '{"exp":120,"coins":150,"storyFragments":8,"channelHeat":15}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":3,"dropRate":0.6},{"itemKey":"station_keeper_trace","quantity":2,"dropRate":0.8}],"equipment":[{"equipmentKey":"cracked_watcher_badge","dropRate":0.25}]}',
 '["king","guardian","station_master"]',
 1),

-- 高级Boss 2: 忠武路Boss —— 铁王真身
('iron_king_true', '铁王真身', 'boss', 'epic', 'chungmuro',
 '曾经的地下君王终于褪去了所有伪装。铁王——一个由废弃金属和故事碎片铸造的巨大人形，王冠是一顶锈蚀的齿轮。它不会说话，但它的每一步都让忠武路的地基震颤。它等待着一个能打碎它王座的人。',
 '{"level":25,"hp":850,"attack":82,"defense":48,"speed":12,"critRate":0.10,"critDamage":2.0}',
 '[{"skillKey":"iron_decree","name":"铁王敕令","description":"发出王者威压，对全体敌人造成攻击力80%伤害并降低20%攻击力持续2回合","cooldown":5,"damageType":"psychic"},{"skillKey":"throne_slam","name":"王座冲击","description":"以自身为圆心释放冲击波，造成攻击力150%伤害并击退目标","cooldown":4,"damageType":"physical"}]',
 '{"exp":250,"coins":320,"storyFragments":15,"channelHeat":25}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":5,"dropRate":0.7},{"itemKey":"station_keeper_trace","quantity":3,"dropRate":0.5}],"equipment":[{"equipmentKey":"cracked_watcher_badge","dropRate":0.3},{"equipmentKey":"reader_scrap_blade","dropRate":0.25}]}',
 '["king","knight","ruler"]',
 1),

-- 高级Boss 3: 东大门Boss —— 火焰巨人觉醒形态
('flame_giant_awakened', '火焰巨人觉醒形态', 'boss', 'epic', 'dongdaemun',
 '火焰巨人并非陨落——它只是沉睡。当星流能量达到临界点时，它从东大门的地基深处苏醒，化身为高达十米的烈焰巨像。它的身体由液态星流金属和永恒之火组成，每一步都会在地面留下燃烧的足印。',
 '{"level":30,"hp":1250,"attack":115,"defense":65,"speed":13,"critRate":0.10,"critDamage":2.1}',
 '[{"skillKey":"inferno_wave","name":"炼狱火浪","description":"释放扇形火焰冲击，造成攻击力140%伤害并附带灼烧效果（每回合损失5%最大生命值，持续3回合）","cooldown":4,"damageType":"fire"},{"skillKey":"molten_armor","name":"熔岩护甲","description":"液态金属包裹全身，提升50%防御力并反弹20%受到的物理伤害，持续3回合","cooldown":6,"damageType":"buff"}]',
 '{"exp":380,"coins":450,"storyFragments":20,"channelHeat":30}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":6,"dropRate":0.75},{"itemKey":"station_keeper_trace","quantity":4,"dropRate":0.6}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.3}]}',
 '["combat","elemental","flame"]',
 1),

-- 高级Boss 4: 明洞Boss —— 禁书守护者
('forbidden_book_guardian', '禁书守护者', 'boss', 'epic', 'myeongdong',
 '明洞图书馆深处的终极守卫——它不是一个人，而是一整座图书馆的意志化身。它的身体由无数禁书页编织而成，每一页都记载着被星流抹除的真实。它守护的不是书，而是所有读者都不敢直视的真相。',
 '{"level":35,"hp":1800,"attack":155,"defense":85,"speed":14,"critRate":0.12,"critDamage":2.2}',
 '[{"skillKey":"forbidden_knowledge","name":"禁忌知识","description":"撕裂目标的记忆，造成攻击力130%伤害并沉默目标2回合（无法使用技能）","cooldown":4,"damageType":"psychic"},{"skillKey":"page_storm","name":"禁页风暴","description":"召唤无数锋利的书页形成风暴，对全体敌人造成攻击力100%伤害","cooldown":5,"damageType":"physical"}]',
 '{"exp":520,"coins":600,"storyFragments":25,"channelHeat":35}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":8,"dropRate":0.8},{"itemKey":"station_keeper_trace","quantity":5,"dropRate":0.7}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.35},{"equipmentKey":"cracked_watcher_badge","dropRate":0.3}]}',
 '["reader","guardian","forbidden"]',
 1),

-- 高级Boss 5: 广津大桥Boss —— 桥之审判官
('bridge_judge', '桥之审判官', 'boss', 'legendary', 'gwangjin_bridge',
 '广津大桥的最终审判者——一个身披白色法袍的巨大人形，手持天平，一端是故事，一端是灵魂。它不属于任何阵营，只忠于"公正"这个概念本身。每一个试图过桥的人都必须在它面前证明自己故事的重量。',
 '{"level":40,"hp":2500,"attack":210,"defense":110,"speed":15,"critRate":0.12,"critDamage":2.3}',
 '[{"skillKey":"soul_weighing","name":"灵魂称量","description":"以天平衡量目标灵魂，若目标当前生命值低于50%则直接斩杀，否则造成攻击力160%伤害","cooldown":5,"damageType":"true"},{"skillKey":"bridge_verdict","name":"大桥裁决","description":"宣判目标有罪，降低目标40%防御力并造成攻击力120%伤害，持续3回合","cooldown":4,"damageType":"psychic"}]',
 '{"exp":720,"coins":850,"storyFragments":35,"channelHeat":45}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":10,"dropRate":0.85},{"itemKey":"station_keeper_trace","quantity":6,"dropRate":0.75}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.4},{"equipmentKey":"cracked_watcher_badge","dropRate":0.35}]}',
 '["king","judge","neutral"]',
 1),

-- 高级Boss 6: 江南站Boss —— 地下城之主
('dungeon_lord', '地下城之主', 'boss', 'legendary', 'gangnam_station',
 '江南站地下城的至高统治者——它不是怪物，而是地下城本身。整个空间就是它的身体，每一面墙壁都是它的皮肤，每一条通道都是它的血管。它用无数触手般的记忆之墙绞杀入侵者，用坍塌的天花板碾碎反抗者。',
 '{"level":45,"hp":3600,"attack":290,"defense":145,"speed":16,"critRate":0.13,"critDamage":2.4}',
 '[{"skillKey":"memory_collapse","name":"记忆坍塌","description":"操控地下城结构，对全体敌人造成攻击力110%伤害并降低30%速度持续2回合","cooldown":5,"damageType":"physical"},{"skillKey":"dungeon_devour","name":"地下城吞噬","description":"将目标吞入墙壁，造成攻击力180%伤害并眩晕目标1回合","cooldown":4,"damageType":"true"}]',
 '{"exp":950,"coins":1100,"storyFragments":45,"channelHeat":55}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":12,"dropRate":0.9},{"itemKey":"station_keeper_trace","quantity":8,"dropRate":0.8}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.45},{"equipmentKey":"cracked_watcher_badge","dropRate":0.4}]}',
 '["king","ruler","dungeon_master"]',
 1),

-- 高级Boss 7: 首尔森林Boss —— 世界树守卫
('world_tree_guardian', '世界树守卫', 'boss', 'legendary', 'seoul_forest',
 '世界树的终极守卫——一个由整棵世界树的生命力凝聚而成的远古存在。它的身体高达数十米，树根就是它的四肢，树冠就是它的王冠。它守护着世界树三千年，见证了无数故事的诞生与消亡。它是自然本身的意志。',
 '{"level":50,"hp":5200,"attack":390,"defense":190,"speed":17,"critRate":0.14,"critDamage":2.5}',
 '[{"skillKey":"roots_of_fate","name":"命运根系","description":"召唤世界树根系缠绕全体敌人，造成攻击力100%伤害并束缚2回合（无法行动）","cooldown":6,"damageType":"physical"},{"skillKey":"natures_wrath","name":"自然之怒","description":"释放积蓄三千年的生命能量，对单体造成攻击力250%伤害并回复自身15%最大生命值","cooldown":5,"damageType":"nature"}]',
 '{"exp":1300,"coins":1500,"storyFragments":55,"channelHeat":65}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":15,"dropRate":0.9},{"itemKey":"station_keeper_trace","quantity":10,"dropRate":0.85}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.5},{"equipmentKey":"cracked_watcher_badge","dropRate":0.45}]}',
 '["abyss","nature","ancient","world_tree"]',
 1),

-- 高级Boss 8: 恶魔之门Boss —— 72柱之首
('demon_pillar_first', '72柱之首', 'boss', 'legendary', 'demon_gate',
 '72柱魔神之首——它曾是旧世界最强大的存在之一，被封印在恶魔之门的最深处。当封印松动时，它以半实体的形态显现：上半身是一个戴着王冠的无面人形，下半身则是无数触手般的黑色烟柱。它渴望的不是毁灭，而是被"记住"。',
 '{"level":55,"hp":7200,"attack":520,"defense":230,"speed":18,"critRate":0.15,"critDamage":2.6}',
 '[{"skillKey":"pillar_of_abyss","name":"深渊之柱","description":"召唤黑色烟柱从地面升起，对全体敌人造成攻击力120%伤害并降低40%治疗效果持续3回合","cooldown":5,"damageType":"abyss"},{"skillKey":"seal_of_the_first","name":"首柱封印","description":"以自身为媒介发动封印，对单体造成攻击力200%伤害并封印目标所有技能2回合","cooldown":6,"damageType":"true"}]',
 '{"exp":1800,"coins":2000,"storyFragments":70,"channelHeat":80}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":18,"dropRate":0.95},{"itemKey":"station_keeper_trace","quantity":12,"dropRate":0.9}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.55},{"equipmentKey":"cracked_watcher_badge","dropRate":0.5}]}',
 '["abyss","demon","ancient","sealed"]',
 1),

-- 高级Boss 9: 全服Boss —— 星流审查者 (世界Boss)
('starstream_examiner', '星流审查者', 'boss', 'legendary', 'starfield',
 '星流本身的意志化身——它不属于任何地点，因为它就是星流本身。当故事偏离了"应有的轨迹"时，它便会降临，以绝对的权力修正一切。它的形态不断变化，时而是一本书，时而是一面镜子，时而是一把审判之剑。它不是敌人，它是"规则"。',
 '{"level":60,"hp":10000,"attack":750,"defense":300,"speed":20,"critRate":0.18,"critDamage":3.0}',
 '[{"skillKey":"narrative_rewrite","name":"叙事重写","description":"改写现实，对全体敌人造成攻击力150%伤害并重置所有敌人技能冷却","cooldown":7,"damageType":"true"},{"skillKey":"starstream_judgment","name":"星流审判","description":"以星流之名审判目标，造成攻击力300%伤害，若目标生命值低于30%则直接抹杀","cooldown":6,"damageType":"true"},{"skillKey":"timeline_lock","name":"时间线锁定","description":"锁定时间线，使自身免疫所有控制效果并提升50%全属性，持续3回合","cooldown":8,"damageType":"buff"}]',
 '{"exp":3000,"coins":3500,"storyFragments":100,"channelHeat":100}',
 '{"items":[{"itemKey":"story_fragment_core","quantity":25,"dropRate":1.0},{"itemKey":"station_keeper_trace","quantity":15,"dropRate":0.95}],"equipment":[{"equipmentKey":"reader_scrap_blade","dropRate":0.6},{"equipmentKey":"cracked_watcher_badge","dropRate":0.55}]}',
 '["starstream","examiner","world_boss","absolute"]',
 1);

-- 个人终局回响：不是原著鬼怪之王本人，也不接入共享世界首领。
INSERT INTO `monsters`
(`monster_key`, `name`, `type`, `rarity`, `location_key`, `description`, `stats_json`,
 `skills_json`, `rewards_json`, `drops_json`, `narrative_tags_json`, `enabled`)
VALUES
('last_wall_dokkaebi_echo', '终墙鬼怪之王回声', 'elite', 'epic', 'demon_gate',
 '根据《星流读者》第12卷终墙战斗情境制作的玩家个人回声模拟，并非原著鬼怪之王本人。刘众赫与隐密的谋略家的原著经历不会由玩家取代或改写。',
 '{"level":18,"hp":150,"attack":23,"defense":14,"speed":11,"critRate":0,"critDamage":1.5}',
 '[]',
 '{"exp":80,"coins":50,"storyFragments":1}',
 '{}',
 '["story_echo","last_wall","personal_timeline"]',
 1)
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`),
 `type` = VALUES(`type`),
 `rarity` = VALUES(`rarity`),
 `location_key` = VALUES(`location_key`),
 `description` = VALUES(`description`),
 `stats_json` = VALUES(`stats_json`),
 `skills_json` = VALUES(`skills_json`),
 `rewards_json` = VALUES(`rewards_json`),
 `drops_json` = VALUES(`drops_json`),
 `narrative_tags_json` = VALUES(`narrative_tags_json`),
 `enabled` = VALUES(`enabled`);
