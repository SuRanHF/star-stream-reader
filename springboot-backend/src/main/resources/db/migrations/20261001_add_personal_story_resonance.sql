-- Add one optional personal story interaction based on two pieces of evidence.
-- Only static exploration/story configuration is changed; no player records are touched.

INSERT IGNORE INTO `exploration_events`
(`event_key`, `event_type`, `stage_key`, `location_key`, `name`, `description`, `weight`,
 `stamina_cost`, `repeatable`, `required_conditions_json`, `rewards_json`,
 `progress_effects_json`, `choices_json`, `log_template`)
VALUES
('darkest_spring_story_stitch', 'story', 'vol2_dark_spring', 'seoul_forest',
 '交错的两段记录',
 '李智慧的日记末页写着请继续，晦日钟声又预示着世界线可能改变。你意识到两段记录似乎在彼此回应，却没有证据证明其中一段会吞没另一段。你可以选择把它们并置，或分别保存。',
 8, 0, 0,
 JSON_OBJECT(
     'required_events', JSON_ARRAY('darkest_spring_first_omen', 'darkest_spring_forest_vision'),
     'required_side_events', JSON_ARRAY('geumho_echo_whisper'),
     'choice_unlockers', JSON_ARRAY(JSON_OBJECT(
         'event_key', 'darkest_spring_forest_vision', 'choice_indices', JSON_ARRAY(0, 1)
     ))
 ),
 '{}', '{}',
 '[
   {
     "label": "把两段记录写在同一页",
     "consequence_text": "你把日记末页的‘请继续’和晦日钟声的警告写在同一页。两段叙事在你的时间线里相互接续，形成一条新的个人理解。",
     "set_story_flags": {"story_dark_spring_interwoven": true}
   },
   {
     "label": "分别保存，不替它们下结论",
     "consequence_text": "你把两份记录分别封存，拒绝让其中一段改写另一段。它们仍各自保留原貌，你决定暂时不替它们下结论。",
     "set_story_flags": {"story_dark_spring_separated": true}
   }
 ]',
 '你发现李智慧日记与晦日钟声之间的呼应，并决定如何记录在自己的时间线上。');

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_OBJECT(
    'required_events', JSON_ARRAY('darkest_spring_first_omen', 'darkest_spring_forest_vision'),
    'required_side_events', JSON_ARRAY('geumho_echo_whisper'),
    'choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'darkest_spring_forest_vision', 'choice_indices', JSON_ARRAY(0, 1)
    ))
)
WHERE `event_key` = 'darkest_spring_story_stitch';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_SET(
    COALESCE(`required_conditions_json`, JSON_OBJECT()),
    '$.personal_contexts', JSON_ARRAY(
        JSON_OBJECT(
            'story_flag', 'story_dark_spring_interwoven',
            'text', '你记得那次把日记末页与钟声警告并置的选择；它们在你的记录中接成了一条线。'
        ),
        JSON_OBJECT(
            'story_flag', 'story_dark_spring_separated',
            'text', '你曾选择把日记与钟声分开保存；此刻你仍能分清哪些是读到的，哪些是推想。'
        )
    ),
    '$.choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'darkest_spring_forest_vision', 'choice_indices', JSON_ARRAY(0, 1)
    ))
)
WHERE `event_key` = 'darkest_spring_abyss_gate';

-- Both forest-vision choices explicitly lead to the demon gate and unlock this personal follow-up.
UPDATE `exploration_events`
SET `choices_json` = JSON_SET(
    COALESCE(`choices_json`, JSON_ARRAY()),
    '$[0].unlock_events', JSON_ARRAY('darkest_spring_abyss_gate', 'darkest_spring_story_stitch'),
    '$[1].unlock_events', JSON_ARRAY('darkest_spring_abyss_gate', 'darkest_spring_story_stitch')
)
WHERE `event_key` = 'darkest_spring_forest_vision';
