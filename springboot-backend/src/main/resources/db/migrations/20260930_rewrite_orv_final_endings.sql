-- Retire unreachable, invented finale definitions and keep the source-aligned personal finale.
-- Static event, ending, and NPC-location configuration only; no player data is modified.

-- The finale's optional last-page memory must come from an NPC that can appear
-- in the active exploration map. Keep the rift scene at the seeded demon gate.
UPDATE `npc_ghosts`
SET `location_keys_json` = JSON_ARRAY('demon_gate')
WHERE `ghost_key` = 'oldest_dream_fragment';

-- The second finale scene must follow the player's actual promise choice, not merely
-- the first scene having been discovered (which would let exploration skip the choice).
UPDATE `exploration_events`
SET `required_conditions_json` = JSON_OBJECT(
        'required_events', JSON_ARRAY('demon_gate_first_step_story')
    ),
    `choices_json` = JSON_SET(
        COALESCE(`choices_json`, JSON_ARRAY()),
        '$[0].unlock_events', JSON_ARRAY('ending_dream_continuation_choice')
    )
WHERE `event_key` = 'ending_old_dream_promise';

UPDATE `exploration_events`
SET `required_conditions_json` = JSON_OBJECT(
    'required_events', JSON_ARRAY('ending_old_dream_promise'),
    'choice_unlockers', JSON_ARRAY(JSON_OBJECT(
        'event_key', 'ending_old_dream_promise', 'choice_indices', JSON_ARRAY(0)
    )),
    'personal_contexts', JSON_ARRAY(
        JSON_OBJECT(
            'story_flag', 'oldest_dream_location',
            'text', '你想起白发读者曾让你看见的病房与沉睡少年。眼前这个终点，因此不再只是一个抽象的故事结尾。'
        ),
        JSON_OBJECT(
            'story_flag', 'oldest_dream_last_page',
            'text', '你记得最古之梦说过：最后一页还没有写出来，而你正在用自己的选择写下它。'
        )
    )
)
WHERE `event_key` = 'ending_dream_continuation_choice';

DELETE FROM `endings`
WHERE `ending_key` IN (
    'end_survivor', 'end_demon_king', 'end_savior', 'end_companion',
    'end_collapse', 'end_lonely_reader', 'end_true'
);

INSERT INTO `endings`
(`ending_key`, `name`, `description`, `priority`, `conditions_json`, `is_hidden`)
VALUES
('end_continue_dream', '接续之梦：新的最古老之梦',
 '你履行约定，让最古老的梦结束。世界出现了无人接替的空缺，你选择继续延续梦境，并成为新的最古老之梦。你不能再以原来的身份陪伴同行者；但一部分记忆化作拥有自主意识的分身，留在他们身边继续生活。你仍能以星光注视那段故事，而自己的旅程从此没有终点。',
 5, '{"flags":{"ending_new_oldest_dream":true}}', 0),
('end_frozen_world', '停滞的世界线（假设分支）',
 '你已经让最古老的梦结束，却选择不接替空缺。梦境无法继续，世界的时间就此停止；所有人都停留在故事的最后一刻，等不到下一页。这是你个人时间线中对终局选择的另一种回答。',
 4, '{"flags":{"ending_world_frozen":true}}', 0)
ON DUPLICATE KEY UPDATE
 `name` = VALUES(`name`),
 `description` = VALUES(`description`),
 `priority` = VALUES(`priority`),
 `conditions_json` = VALUES(`conditions_json`),
 `is_hidden` = VALUES(`is_hidden`);
