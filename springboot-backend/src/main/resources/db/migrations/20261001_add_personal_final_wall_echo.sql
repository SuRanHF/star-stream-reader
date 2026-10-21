-- Add one optional, personal finale echo encounter.
-- Static monster and story definitions only; no player progress is modified.

INSERT INTO `monsters`
(`monster_key`, `name`, `type`, `rarity`, `location_key`, `description`, `stats_json`,
 `skills_json`, `rewards_json`, `drops_json`, `narrative_tags_json`, `enabled`)
VALUES
('last_wall_dokkaebi_echo', '终墙鬼怪之王回声', 'elite', 'epic', 'demon_gate',
 '根据《星流读者》第12卷终墙战斗情境制作的玩家个人回声模拟，并非原著鬼怪之王本人。刘众赫与隐密的谋略家的原著经历不会由玩家取代或改写。',
 '{"level":18,"hp":150,"attack":23,"defense":14,"speed":11,"critRate":0,"critDamage":1.5}',
 '[]', '{"exp":80,"coins":50,"storyFragments":1}', '{}',
 '["story_echo","last_wall","personal_timeline"]', 1)
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`),
 `type` = VALUES(`type`),
 `rarity` = VALUES(`rarity`),
 `location_key` = VALUES(`location_key`),
 `description` = VALUES(`description`),
 `stats_json` = VALUES(`stats_json`),
 `skills_json` = VALUES(`skills_json`),
 `rewards_json` = VALUES(`rewards_json`),
 `drops_json` = VALUES(`drops_json`),
 `narrative_tags_json` = VALUES(`narrative_tags_json`),
 `enabled` = VALUES(`enabled`);

INSERT INTO `exploration_events`
(`event_key`, `event_type`, `stage_key`, `location_key`, `name`, `description`, `weight`,
 `stamina_cost`, `repeatable`, `required_conditions_json`, `rewards_json`,
 `progress_effects_json`, `choices_json`, `log_template`)
VALUES
('last_wall_echo_personal_story', 'story', 'vol12_finale', 'demon_gate', '终墙的个人回响',
 '终局选择落定后，最后一道墙上映出一段战斗回声：第三次回归的刘众赫与隐密的谋略家并肩，传说共鸣，混沌力量沿黑天魔刀暴涨。这是原著人物的故事，不会由你取代或改写。你可以让自己的时间线映照出一场虚构的回声遭遇；若进入，首次攻击额外造成 15% 伤害（最多 8 点），这是本游戏的平衡改编，不是原著数值。',
 100, 0, 0,
 JSON_OBJECT(
     'required_events', JSON_ARRAY('ending_dream_continuation_choice'),
     'personal_contexts', JSON_ARRAY(
         JSON_OBJECT('story_flag', 'ending_new_oldest_dream',
             'text', '你选择接续梦境。终墙的光影没有把你的选择改写成原著人物的经历，只为你的时间线留下另一段回声。'),
         JSON_OBJECT('story_flag', 'ending_world_frozen',
             'text', '你选择让世界停在此刻。这里不是世界恢复运转，而是终局留下的一段个人回声；它不会撤销你的结局选择。')
     )
 ),
 JSON_OBJECT(), JSON_OBJECT(),
 JSON_ARRAY(
     JSON_OBJECT(
         'label', '走入回声，与鬼怪之王的影像交手',
         'consequence_text', '你进入一场只属于自己时间线的战斗模拟。首次攻击额外造成 15% 伤害，最多 8 点；这项数值是本游戏改编，不是原著设定，战斗中仍可撤退。',
         'risks', JSON_OBJECT('monsterKey', 'last_wall_dokkaebi_echo'),
         'set_story_flags', JSON_OBJECT('story_final_wall_echo_attuned', true)
     ),
     JSON_OBJECT(
         'label', '只记录回声，不介入原著之战',
         'consequence_text', '你记下最后一道墙上的光影，不让自己的行动冒充刘众赫与隐密的谋略家的经历。',
         'set_story_flags', JSON_OBJECT('story_final_wall_echo_observed', true)
     )
 ),
 '你在自己的时间线里读到终墙战斗的回声，并决定是否进入一场个人遭遇。')
ON DUPLICATE KEY UPDATE
 `event_type` = VALUES(`event_type`),
 `stage_key` = VALUES(`stage_key`),
 `location_key` = VALUES(`location_key`),
 `name` = VALUES(`name`),
 `description` = VALUES(`description`),
 `required_conditions_json` = VALUES(`required_conditions_json`),
 `rewards_json` = VALUES(`rewards_json`),
 `progress_effects_json` = VALUES(`progress_effects_json`),
 `choices_json` = VALUES(`choices_json`),
 `log_template` = VALUES(`log_template`);
