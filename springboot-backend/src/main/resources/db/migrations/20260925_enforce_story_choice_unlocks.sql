-- Enforce the personal branch gates declared by the exploration seed data.
-- This updates static event configuration only; no player progress is modified.
UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'geumho_shelter_story', 'choice_indices', JSON_ARRAY(1)
    ))
)
WHERE `event_key` = 'geumho_underground_story';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'geumho_underground_story', 'choice_indices', JSON_ARRAY(1)
    ))
)
WHERE `event_key` = 'chungmuro_iron_king_story';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'chungmuro_iron_king_story', 'choice_indices', JSON_ARRAY(0, 1)
    ))
)
WHERE `event_key` = 'chungmuro_shadow_king_story';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'chungmuro_iron_king_story', 'choice_indices', JSON_ARRAY(1)
    ))
)
WHERE `event_key` = 'chungmuro_underground_resistance';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'chungmuro_shadow_king_story', 'choice_indices', JSON_ARRAY(0, 1)
    ))
)
WHERE `event_key` = 'chungmuro_conspiracy_story';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'chungmuro_conspiracy_story', 'choice_indices', JSON_ARRAY(0, 1)
    ))
)
WHERE `event_key` = 'chungmuro_revelation_story';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'dongdaemun_flame_giant_story', 'choice_indices', JSON_ARRAY(0)
    ))
)
WHERE `event_key` = 'dongdaemun_missing_chapter_story';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'chungmuro_underground_resistance', 'choice_indices', JSON_ARRAY(0)
    ))
)
WHERE `event_key` = 'myeongdong_library_story';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'myeongdong_library_story', 'choice_indices', JSON_ARRAY(0)
    ))
)
WHERE `event_key` = 'myeongdong_forbidden_story';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'gangnam_dungeon_gate_story', 'choice_indices', JSON_ARRAY(0)
    ))
)
WHERE `event_key` = 'gangnam_deep_floor_story';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'gangnam_deep_floor_story', 'choice_indices', JSON_ARRAY(1)
    ))
)
WHERE `event_key` = 'gangnam_memory_wall_story';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'darkest_spring_first_omen', 'choice_indices', JSON_ARRAY(1)
    ))
)
WHERE `event_key` = 'darkest_spring_forest_vision';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'darkest_spring_forest_vision', 'choice_indices', JSON_ARRAY(0)
    ))
)
WHERE `event_key` = 'darkest_spring_abyss_gate';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'darkest_spring_abyss_gate', 'choice_indices', JSON_ARRAY(1)
    ))
)
WHERE `event_key` = 'darkest_spring_seed_of_hope';
