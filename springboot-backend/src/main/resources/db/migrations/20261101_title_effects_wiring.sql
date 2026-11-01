-- Only exact legacy effects are migrated. A second run updates zero rows.
UPDATE titles SET effects_json = '{"channelHeatGainRate":0.05}',
 description = CONCAT(description, '佩戴时星流热度收益 +5%。')
WHERE title_key = 'station_witness' AND JSON_LENGTH(effects_json) = 1
 AND JSON_CONTAINS(effects_json, CAST('{"channelHeatBonus":5}' AS JSON));

UPDATE titles SET effects_json = '{"storyEventBonus":0.01}',
 description = CONCAT(description, '佩戴时剧情事件抽取权重 +1%。')
WHERE title_key = 'story_fragment_collector' AND JSON_LENGTH(effects_json) = 1
 AND JSON_CONTAINS(effects_json, CAST('{"insightBonus":1}' AS JSON));

UPDATE titles SET effects_json = '{"storyEventBonus":0.03}',
 description = CONCAT(description, '佩戴时剧情事件抽取权重 +3%。')
WHERE title_key = 'no_throne_king_seed' AND JSON_LENGTH(effects_json) = 1
 AND JSON_CONTAINS(effects_json, CAST('{"narrativePressureBonus":0.03}' AS JSON));

UPDATE titles SET effects_json = '{"defense":10,"storyEventBonus":0.03}',
 description = CONCAT(description, '佩戴时剧情事件抽取权重 +3%。')
WHERE title_key = 'fourth_wall_touched' AND JSON_LENGTH(effects_json) = 2
 AND JSON_CONTAINS(effects_json, CAST('{"defense":10,"insight":3}' AS JSON));

UPDATE titles SET effects_json = '{"attack":20,"critRate":0.1,"critDamage":0.3,"storyEventBonus":0.05}',
 description = CONCAT(description, '佩戴时剧情事件抽取权重 +5%。')
WHERE title_key = 'omniscient_reader' AND JSON_LENGTH(effects_json) = 4
 AND JSON_CONTAINS(effects_json, CAST('{"attack":20,"critRate":0.1,"critDamage":0.3,"insight":5}' AS JSON));

UPDATE titles SET effects_json = '{"attack":15,"defense":15}',
 description = CONCAT(description, '佩戴时防御 +15。')
WHERE title_key = 'script_rewriter' AND JSON_LENGTH(effects_json) = 3
 AND JSON_CONTAINS(effects_json, CAST('{"attack":15,"defense":10,"willpower":5}' AS JSON));

UPDATE titles SET effects_json = '{"attack":18,"critDamage":0.5,"storyEventBonus":0.04}',
 description = CONCAT(description, '佩戴时剧情事件抽取权重 +4%。')
WHERE title_key = 'forbidden_reader' AND JSON_LENGTH(effects_json) = 3
 AND JSON_CONTAINS(effects_json, CAST('{"attack":18,"critDamage":0.5,"insight":4}' AS JSON));

UPDATE titles SET effects_json = '{"storyEventBonus":0.03,"channelHeatGainRate":0.05}',
 description = CONCAT(description, '佩戴时剧情事件抽取权重 +3%、星流热度收益 +5%。')
WHERE title_key = 'wall_listener' AND JSON_LENGTH(effects_json) = 2
 AND JSON_CONTAINS(effects_json, CAST('{"insight":3,"channelHeat":5}' AS JSON));

UPDATE titles SET effects_json = '{"attack":15,"defense":16}',
 description = CONCAT(description, '佩戴时防御 +16。')
WHERE title_key = 'world_walker' AND JSON_LENGTH(effects_json) = 3
 AND JSON_CONTAINS(effects_json, CAST('{"attack":15,"defense":12,"willpower":4}' AS JSON));

UPDATE titles SET effects_json = '{"storyEventBonus":0.04,"channelHeatGainRate":0.10}',
 description = CONCAT(description, '佩戴时剧情事件抽取权重 +4%、星流热度收益 +10%。')
WHERE title_key = 'memory_keeper' AND JSON_LENGTH(effects_json) = 2
 AND JSON_CONTAINS(effects_json, CAST('{"insight":4,"channelHeat":10}' AS JSON));

UPDATE titles SET effects_json = '{"speed":5,"channelHeatGainRate":0.15}',
 description = CONCAT(description, '佩戴时星流热度收益 +15%。')
WHERE title_key = 'liberator' AND JSON_LENGTH(effects_json) = 2
 AND JSON_CONTAINS(effects_json, CAST('{"speed":5,"channelHeat":15}' AS JSON));
