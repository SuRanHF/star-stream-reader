-- The first geumho underground-market choice explicitly charges one story fragment.
-- Only update rows where that cost is missing; repeated runs and customized values are preserved.
UPDATE `exploration_events`
SET `choices_json` = JSON_SET(
    `choices_json`,
    '$[0].rewards_override.storyFragments',
    -1
)
WHERE `event_key` = 'geumho_underground_story'
  AND `location_key` = 'geumho_station'
  AND JSON_EXTRACT(
      `choices_json`, '$[0].rewards_override.storyFragments'
  ) IS NULL;
