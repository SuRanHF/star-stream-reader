-- Repair the seeded flame-giant story choice that referenced a non-existent equipment key.
-- The predicate keeps this migration idempotent and leaves any customized choice untouched.
UPDATE `exploration_events`
SET `choices_json` = JSON_SET(
    `choices_json`,
    '$[1].rewards_override.equipment[0].equipmentKey',
    'starfall_blade'
)
WHERE `event_key` = 'dongdaemun_flame_giant_story'
  AND JSON_UNQUOTE(JSON_EXTRACT(
      `choices_json`, '$[1].rewards_override.equipment[0].equipmentKey'
  )) = 'starfall_sword';
