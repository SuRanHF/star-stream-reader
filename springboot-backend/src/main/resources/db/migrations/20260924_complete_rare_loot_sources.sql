-- Give existing seeded equipment and the abyss consumable real gameplay sources.
-- Each update is idempotent and changes only monster drop configuration.
UPDATE `monsters`
SET `drops_json` = JSON_SET(
    COALESCE(`drops_json`, JSON_OBJECT()),
    '$.equipment',
    JSON_ARRAY_APPEND(
        COALESCE(JSON_EXTRACT(`drops_json`, '$.equipment'), JSON_ARRAY()),
        '$', JSON_OBJECT('equipmentKey', 'station_guard_coat', 'dropRate', 0.12)
    )
)
WHERE `monster_key` = 'station_keeper_fragment'
  AND COALESCE(JSON_CONTAINS(`drops_json`, JSON_OBJECT('equipmentKey', 'station_guard_coat'), '$.equipment'), 0) = 0;

UPDATE `monsters`
SET `drops_json` = JSON_SET(
    COALESCE(`drops_json`, JSON_OBJECT()),
    '$.items',
    JSON_ARRAY_APPEND(
        COALESCE(JSON_EXTRACT(`drops_json`, '$.items'), JSON_ARRAY()),
        '$', JSON_OBJECT('itemKey', 'abyss_residue', 'quantity', 1, 'dropRate', 0.2)
    )
)
WHERE `monster_key` = 'gatekeeper_of_old_world'
  AND COALESCE(JSON_CONTAINS(`drops_json`, JSON_OBJECT('itemKey', 'abyss_residue'), '$.items'), 0) = 0;

UPDATE `monsters`
SET `drops_json` = JSON_SET(
    COALESCE(`drops_json`, JSON_OBJECT()),
    '$.equipment',
    JSON_ARRAY_APPEND(
        COALESCE(JSON_EXTRACT(`drops_json`, '$.equipment'), JSON_ARRAY()),
        '$', JSON_OBJECT('equipmentKey', 'abyss_touched_relic', 'dropRate', 0.08)
    )
)
WHERE `monster_key` = 'gatekeeper_of_old_world'
  AND COALESCE(JSON_CONTAINS(`drops_json`, JSON_OBJECT('equipmentKey', 'abyss_touched_relic'), '$.equipment'), 0) = 0;
