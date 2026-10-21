-- Add distinct monsters for five exploration encounters whose old keys pointed
-- to unrelated creatures. Static configuration only; no player data is changed.
INSERT IGNORE INTO `monsters`
(`monster_key`, `name`, `type`, `rarity`, `location_key`, `description`, `stats_json`,
 `skills_json`, `rewards_json`, `drops_json`, `narrative_tags_json`, `enabled`)
VALUES
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
 1);

-- The exploration seeder only inserts missing event rows, so update the five
-- existing static encounter references without touching any player progress.
UPDATE `exploration_events`
SET `risks_json` = JSON_SET(COALESCE(`risks_json`, JSON_OBJECT()), '$.monsterKey', 'fire_lord_projection')
WHERE `event_key` = 'dongdaemun_battle_flame';

UPDATE `exploration_events`
SET `risks_json` = JSON_SET(COALESCE(`risks_json`, JSON_OBJECT()), '$.monsterKey', 'library_hunger_echo')
WHERE `event_key` = 'myeongdong_battle_bookworm';

UPDATE `exploration_events`
SET `risks_json` = JSON_SET(COALESCE(`risks_json`, JSON_OBJECT()), '$.monsterKey', 'bridge_hunger_entity')
WHERE `event_key` = 'gwangjin_battle_hunger';

UPDATE `exploration_events`
SET `risks_json` = JSON_SET(COALESCE(`risks_json`, JSON_OBJECT()), '$.monsterKey', 'dungeon_guardian_program')
WHERE `event_key` = 'gangnam_battle_dungeon';

UPDATE `exploration_events`
SET `risks_json` = JSON_SET(COALESCE(`risks_json`, JSON_OBJECT()), '$.monsterKey', 'corrupted_forest_beast')
WHERE `event_key` = 'seoul_forest_battle_beast';

-- Battle placeholders are paid from monster rewards at combat settlement.
-- Clear the unused event-level reward definitions for the 18 static encounters
-- so there is only one visible source of truth; player data is untouched.
UPDATE `exploration_events`
SET `rewards_json` = JSON_OBJECT()
WHERE `event_type` = 'battle_placeholder'
  AND `event_key` IN (
    'ruined_station_battle_rat',
    'geumho_station_battle_shadow',
    'dongmyo_battle_rat',
    'chungmuro_battle_keeper',
    'dongdaemun_battle_flame',
    'myeongdong_battle_bookworm',
    'gwangjin_battle_hunger',
    'gangnam_battle_dungeon',
    'seoul_forest_battle_beast',
    'demon_gate_battle_echo',
    'geumho_battle_scavenger',
    'dongmyo_battle_thug',
    'chungmuro_battle_knight',
    'dongdaemun_battle_spark',
    'myeongdong_battle_wraith',
    'gwangjin_battle_drowned',
    'gangnam_battle_crawler',
    'seoul_forest_battle_deer'
  );
