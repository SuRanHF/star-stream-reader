-- Run after importing seed_synthesis_items.sql and seed_monsters.sql.
-- This only enriches monster drop configuration; it does not touch player inventory/progress.
-- JSON_CONTAINS makes each update safe to re-run without duplicating a drop entry.

UPDATE `monsters`
SET `drops_json` = JSON_SET(
    COALESCE(`drops_json`, JSON_OBJECT()),
    '$.items',
    JSON_ARRAY_APPEND(
        COALESCE(JSON_EXTRACT(`drops_json`, '$.items'), JSON_ARRAY()),
        '$', JSON_OBJECT('itemKey', 'monster_bone', 'quantity', 1, 'dropRate', 0.4)
    )
)
WHERE `monster_key` = 'station_rat'
  AND COALESCE(JSON_CONTAINS(`drops_json`, JSON_OBJECT('itemKey', 'monster_bone'), '$.items'), 0) = 0;

UPDATE `monsters`
SET `drops_json` = JSON_SET(
    COALESCE(`drops_json`, JSON_OBJECT()),
    '$.items',
    JSON_ARRAY_APPEND(
        COALESCE(JSON_EXTRACT(`drops_json`, '$.items'), JSON_ARRAY()),
        '$', JSON_OBJECT('itemKey', 'small_hp_potion', 'quantity', 1, 'dropRate', 0.12)
    )
)
WHERE `monster_key` = 'broken_avatar_shadow'
  AND COALESCE(JSON_CONTAINS(`drops_json`, JSON_OBJECT('itemKey', 'small_hp_potion'), '$.items'), 0) = 0;

UPDATE `monsters`
SET `drops_json` = JSON_SET(
    COALESCE(`drops_json`, JSON_OBJECT()),
    '$.items',
    JSON_ARRAY_APPEND(
        COALESCE(JSON_EXTRACT(`drops_json`, '$.items'), JSON_ARRAY()),
        '$', JSON_OBJECT('itemKey', 'purification_water', 'quantity', 1, 'dropRate', 0.15)
    )
)
WHERE `monster_key` = 'wisdom_keeper_shadow'
  AND COALESCE(JSON_CONTAINS(`drops_json`, JSON_OBJECT('itemKey', 'purification_water'), '$.items'), 0) = 0;

UPDATE `monsters`
SET `drops_json` = JSON_SET(
    COALESCE(`drops_json`, JSON_OBJECT()),
    '$.items',
    JSON_ARRAY_APPEND(
        COALESCE(JSON_EXTRACT(`drops_json`, '$.items'), JSON_ARRAY()),
        '$', JSON_OBJECT('itemKey', 'story_fragment', 'quantity', 1, 'dropRate', 0.3)
    )
)
WHERE `monster_key` = 'old_tale_echo'
  AND COALESCE(JSON_CONTAINS(`drops_json`, JSON_OBJECT('itemKey', 'story_fragment'), '$.items'), 0) = 0;

UPDATE `monsters`
SET `drops_json` = JSON_SET(
    COALESCE(`drops_json`, JSON_OBJECT()),
    '$.equipment',
    JSON_ARRAY_APPEND(
        COALESCE(JSON_EXTRACT(`drops_json`, '$.equipment'), JSON_ARRAY()),
        '$', JSON_OBJECT('equipmentKey', 'hunters_ring', 'dropRate', 0.08)
    )
)
WHERE `monster_key` = 'old_tale_echo'
  AND COALESCE(JSON_CONTAINS(`drops_json`, JSON_OBJECT('equipmentKey', 'hunters_ring'), '$.equipment'), 0) = 0;

UPDATE `monsters`
SET `drops_json` = JSON_SET(
    COALESCE(`drops_json`, JSON_OBJECT()),
    '$.equipment',
    JSON_ARRAY_APPEND(
        COALESCE(JSON_EXTRACT(`drops_json`, '$.equipment'), JSON_ARRAY()),
        '$', JSON_OBJECT('equipmentKey', 'rusty_dagger', 'dropRate', 0.08)
    )
)
WHERE `monster_key` = 'forgotten_hero_shadow'
  AND COALESCE(JSON_CONTAINS(`drops_json`, JSON_OBJECT('equipmentKey', 'rusty_dagger'), '$.equipment'), 0) = 0;

UPDATE `monsters`
SET `drops_json` = JSON_SET(
    COALESCE(`drops_json`, JSON_OBJECT()),
    '$.items',
    JSON_ARRAY_APPEND(
        COALESCE(JSON_EXTRACT(`drops_json`, '$.items'), JSON_ARRAY()),
        '$', JSON_OBJECT('itemKey', 'abyss_shard', 'quantity', 1, 'dropRate', 0.08)
    )
)
WHERE `monster_key` = 'drowned_memory'
  AND COALESCE(JSON_CONTAINS(`drops_json`, JSON_OBJECT('itemKey', 'abyss_shard'), '$.items'), 0) = 0;
