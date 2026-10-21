-- Repair dead location gates by accepting both historical aliases and current seeded story keys.
-- This updates location configuration only; player progress is not rewritten.
UPDATE `locations`
SET `unlock_conditions_json` = JSON_SET(
    JSON_REMOVE(COALESCE(`unlock_conditions_json`, JSON_OBJECT()), '$.prerequisite_events'),
    '$.prerequisite_events_any', JSON_ARRAY('geumho_shelter_first', 'geumho_shelter_story')
)
WHERE `location_key` = 'chungmuro';

UPDATE `locations`
SET `unlock_conditions_json` = JSON_SET(
    JSON_REMOVE(COALESCE(`unlock_conditions_json`, JSON_OBJECT()), '$.prerequisite_events'),
    '$.prerequisite_events_any', JSON_ARRAY('chungmuro_tenkings_arrival', 'chungmuro_revelation_story')
)
WHERE `location_key` = 'dongdaemun';

UPDATE `locations`
SET `unlock_conditions_json` = JSON_SET(
    JSON_REMOVE(COALESCE(`unlock_conditions_json`, JSON_OBJECT()), '$.prerequisite_events'),
    '$.prerequisite_events_any', JSON_ARRAY('gwangjin_bridge_crossing', 'gwangjin_bridge_trap_story')
)
WHERE `location_key` = 'gangnam_station';

UPDATE `locations`
SET `unlock_conditions_json` = JSON_SET(
    JSON_REMOVE(COALESCE(`unlock_conditions_json`, JSON_OBJECT()), '$.prerequisite_events'),
    '$.prerequisite_events_any', JSON_ARRAY('gangnam_entrance_discovery', 'gangnam_dungeon_gate_story')
)
WHERE `location_key` = 'demon_gate';

-- Seoul Forest had a self-referential choice prerequisite, but no choice grants
-- the seoul_forest location. Its intended access rule is the existing level-15 gate.
UPDATE `locations`
SET `unlock_conditions_json` = JSON_REMOVE(
    COALESCE(`unlock_conditions_json`, JSON_OBJECT()), '$.unlock_by_choice'
)
WHERE `location_key` = 'seoul_forest'
  AND JSON_UNQUOTE(JSON_EXTRACT(`unlock_conditions_json`, '$.unlock_by_choice')) = 'seoul_forest';
