-- Repair the two mandatory story joins and remove eleven event-rate entries with no event of that type.
-- Scope: exploration_events.required_conditions_json/choices_json and locations.event_rates_json only; no player data.
-- Repeat execution: guarded updates become no-ops. Custom gate/hint/rate values are left intact.
-- After execution: inspect the two gate events, two choice events, and nine location keys below.
-- Apply only after the earlier exploration seed and choice-gate migrations, following the migration runbook.

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(`required_conditions_json`,
    '$.choice_unlockers[0].choice_indices', JSON_ARRAY(0, 1))
WHERE `event_key` = 'geumho_underground_story'
  AND JSON_LENGTH(JSON_EXTRACT(`required_conditions_json`, '$.choice_unlockers')) = 1
  AND JSON_UNQUOTE(JSON_EXTRACT(`required_conditions_json`, '$.choice_unlockers[0].event_key')) = 'geumho_shelter_story'
  AND JSON_EXTRACT(`required_conditions_json`, '$.choice_unlockers[0].choice_indices') = JSON_ARRAY(1);

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(`required_conditions_json`,
    '$.choice_unlockers[0].choice_indices', JSON_ARRAY(0, 1))
WHERE `event_key` = 'chungmuro_iron_king_story'
  AND JSON_LENGTH(JSON_EXTRACT(`required_conditions_json`, '$.choice_unlockers')) = 1
  AND JSON_UNQUOTE(JSON_EXTRACT(`required_conditions_json`, '$.choice_unlockers[0].event_key')) = 'geumho_underground_story'
  AND JSON_EXTRACT(`required_conditions_json`, '$.choice_unlockers[0].choice_indices') = JSON_ARRAY(1);

UPDATE `exploration_events`
SET `choices_json` = JSON_SET(`choices_json`, '$[0].unlock_events',
    JSON_ARRAY('geumho_underground_story'))
WHERE `event_key` = 'geumho_shelter_story'
  AND JSON_LENGTH(`choices_json`) = 2
  AND JSON_CONTAINS_PATH(`choices_json`, 'one', '$[0].unlock_events') = 0
  AND JSON_EXTRACT(`choices_json`, '$[1].unlock_events') = JSON_ARRAY('geumho_underground_story');

UPDATE `exploration_events`
SET `choices_json` = JSON_SET(`choices_json`, '$[0].unlock_events',
    JSON_ARRAY('chungmuro_iron_king_story'))
WHERE `event_key` = 'geumho_underground_story'
  AND JSON_LENGTH(`choices_json`) = 2
  AND JSON_CONTAINS_PATH(`choices_json`, 'one', '$[0].unlock_events') = 0
  AND JSON_EXTRACT(`choices_json`, '$[1].unlock_events') = JSON_ARRAY('chungmuro_iron_king_story');

UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.side_story')
WHERE `location_key` = 'ruined_station'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.side_story')) = '5';
UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.boss_clue')
WHERE `location_key` = 'geumho_station'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.boss_clue')) = '10';
UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.boss_clue')
WHERE `location_key` = 'dongmyo'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.boss_clue')) = '8';
UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.opportunity')
WHERE `location_key` = 'dongdaemun'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.opportunity')) = '10';
UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.boss_clue')
WHERE `location_key` = 'myeongdong'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.boss_clue')) = '8';
UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.boss_clue')
WHERE `location_key` = 'gwangjin_bridge'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.boss_clue')) = '18';
UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.opportunity')
WHERE `location_key` = 'gangnam_station'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.opportunity')) = '10';
UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.hidden')
WHERE `location_key` = 'gangnam_station'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.hidden')) = '5';
UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.boss_clue')
WHERE `location_key` = 'seoul_forest'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.boss_clue')) = '10';
UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.boss_clue')
WHERE `location_key` = 'demon_gate'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.boss_clue')) = '20';
UPDATE `locations` SET `event_rates_json` = JSON_REMOVE(`event_rates_json`, '$.empty')
WHERE `location_key` = 'demon_gate'
  AND JSON_UNQUOTE(JSON_EXTRACT(`event_rates_json`, '$.empty')) = '20';
