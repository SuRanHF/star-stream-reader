-- Add a story-specific, early-game iron king projection and connect it to the defiance choice.
-- Static configuration only: this migration does not touch player state.

INSERT IGNORE INTO `monsters`
(`monster_key`, `name`, `type`, `rarity`, `location_key`, `description`, `stats_json`,
 `skills_json`, `rewards_json`, `drops_json`, `narrative_tags_json`, `enabled`)
VALUES
('iron_king_trial', '铁王的钢铁化投影', 'elite', 'rare', 'chungmuro',
 '铁王没有以真身迎战，而是以钢铁化能力驱使一道星流投影逼你证明自己的选择。它保留了铁王的力量，却不是铁王的全部实力。',
 '{"level":7,"hp":450,"attack":30,"defense":22,"speed":12,"critRate":0,"critDamage":1.5}',
 '[]',
 '{"exp":30,"coins":25,"storyFragments":1,"channelHeat":3}',
 '{}',
 '["king","steel","story"]',
 1);

UPDATE `exploration_events`
SET `choices_json` = JSON_SET(
    `choices_json`,
    '$[0].risks',
    JSON_OBJECT(
        'damagePercent', 15,
        'monsterKey', 'iron_king_trial',
        'battleName', '铁王的钢铁化试炼',
        'battleDescription', '冲击波造成了约最大生命15%的伤害。铁王释放的钢铁化投影挡在前方；你可以迎战，也可以撤退。撤退不会撤销这次个人剧情选择。'
    )
)
WHERE `event_key` = 'chungmuro_iron_king_story'
  AND JSON_TYPE(`choices_json`) = 'ARRAY'
  AND JSON_LENGTH(`choices_json`) > 0;
