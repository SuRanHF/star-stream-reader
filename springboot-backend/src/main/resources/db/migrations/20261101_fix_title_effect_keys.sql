-- Correct two seeded title effect keys without changing their numeric values.
-- Only exact old JSON values are eligible; customized title effects are left untouched.
-- Re-running this migration changes no rows.
UPDATE `titles`
SET `effects_json` = '{"attack": 3}'
WHERE `title_key` = 'boss_trace_hunter'
  AND JSON_LENGTH(`effects_json`) = 1
  AND JSON_CONTAINS(`effects_json`, CAST('{"attackBonus": 3}' AS JSON));

UPDATE `titles`
SET `effects_json` = '{"maxStamina": 5}'
WHERE `title_key` = 'first_explorer'
  AND JSON_LENGTH(`effects_json`) = 1
  AND JSON_CONTAINS(`effects_json`, CAST('{"staminaBonus": 5}' AS JSON));
