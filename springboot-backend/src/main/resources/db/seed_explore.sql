-- ============================================================
-- Phase 2 探索系统种子数据
-- locations + exploration_events 初始数据
-- ============================================================

-- === 地点种子 ===

INSERT IGNORE INTO `locations` (`location_key`, `name`, `description`, `unlock_conditions_json`, `event_rates_json`, `min_level`, `danger_level`, `recommended_rank`, `is_default`)
VALUES
('ruined_station', '废弃车站', '地铁三号线的废墟，倒塌的天花板下散落着使用过的票根与干涸的血迹。新晋角色最安全的起点。',
 '{}',
 '{"story":20,"resource":30,"opportunity":15,"boss_clue":10,"empty":25}',
 1, 2, 'F', 1),

('broken_mall', '断裂商场', '资源和遭遇更多的危险区域，断裂的混凝土之间偶尔能看到发光的物品。',
 '{"required_level":3}',
 '{"story":10,"resource":35,"opportunity":20,"boss_clue":15,"empty":20}',
 3, 4, 'E', 0);

-- === 废弃车站事件 (5个) ===

INSERT IGNORE INTO `exploration_events` (`event_key`, `event_type`, `location_key`, `name`, `description`, `weight`, `stamina_cost`, `repeatable`, `rewards_json`, `progress_effects_json`, `log_template`)
VALUES

-- 1. story 事件
('station_first_trace', 'story', 'ruined_station',
 '第一个异常痕迹',
 '你在废弃车站发现了第一处剧本异常痕迹，星流开始记录你的行动。',
 15, 5, 0,
 '{"storyFragments":2,"channelHeat":5}',
 '{"storyEventsTriggeredAdd":1,"storyPityReset":true}',
 '你在废弃车站发现了第一处剧本异常痕迹，星流开始记录你的行动。'),

-- 2. resource 事件
('station_resource_scrap', 'resource', 'ruined_station',
 '散落物资',
 '你在角落里找到了一些可用物资。',
 30, 5, 1,
 '{"coins":10,"storyFragments":1,"items":[{"itemKey":"stamina_bread","quantity":1,"dropRate":0.3}]}',
 '{"storyPityAdd":1}',
 '你在角落里找到了一些可用物资。'),

-- 3. opportunity 事件
('station_opportunity_whisper', 'opportunity', 'ruined_station',
 '频道低语',
 '一阵低语从频道深处传来，你感觉自己被短暂注视了。',
 15, 5, 1,
 '{"channelHeat":3,"worldLineShift":1}',
 '{"storyPityAdd":1}',
 '一阵低语从频道深处传来，你感觉自己被短暂注视了。'),

-- 4. boss_clue 事件
('station_boss_clue', 'boss_clue', 'ruined_station',
 '看门人的痕迹',
 '你发现了废弃车站看门人的痕迹。',
 10, 6, 0,
 '{"storyFragments":1}',
 '{"bossClueKey":"station_keeper","bossClueAdd":1}',
 '你发现了废弃车站看门人的痕迹。'),

-- 5. empty 事件
('station_empty_walk', 'empty', 'ruined_station',
 '无事发生',
 '你在废弃车站巡视了一圈，没有发现明显异常。',
 30, 4, 1,
 '{}',
 '{"storyPityAdd":1}',
 '你在废弃车站巡视了一圈，没有发现明显异常。');

-- === 断裂商场事件 (3个) ===

INSERT IGNORE INTO `exploration_events` (`event_key`, `event_type`, `location_key`, `name`, `description`, `weight`, `stamina_cost`, `repeatable`, `rewards_json`, `progress_effects_json`, `log_template`)
VALUES

('mall_resource_stash', 'resource', 'broken_mall',
 '商场残存物资',
 '断裂的货架下藏着一些未被拿走的补给。',
 35, 6, 1,
 '{"coins":20,"storyFragments":2,"items":[{"itemKey":"first_aid_bandage","quantity":1,"dropRate":0.4},{"itemKey":"channel_token","quantity":1,"dropRate":0.2}]}',
 '{"storyPityAdd":1}',
 '断裂的货架下藏着一些未被拿走的补给。'),

('mall_opportunity_signal', 'opportunity', 'broken_mall',
 '奇怪的信号',
 '你的终端捕捉到一段断断续续的信号，似乎是某个星座赞助者在试图连接。',
 20, 6, 1,
 '{"channelHeat":5,"worldLineShift":2}',
 '{"storyPityAdd":1}',
 '你的终端捕捉到一段断断续续的信号，似乎是某个星座赞助者在试图连接。'),

('mall_empty_patrol', 'empty', 'broken_mall',
 '废墟巡逻',
 '你在断裂商场中巡逻了一圈，除了一些可疑的影子外一切正常。',
 45, 5, 1,
 '{}',
 '{"storyPityAdd":1}',
 '你在断裂商场中巡逻了一圈，除了一些可疑的影子外一切正常。');

-- === 战斗占位事件 (2个) ===

INSERT IGNORE INTO `exploration_events` (`event_key`, `event_type`, `location_key`, `name`, `description`, `weight`, `stamina_cost`, `repeatable`, `rewards_json`, `risks_json`, `log_template`)
VALUES

('station_rat_ambush', 'battle_placeholder', 'ruined_station',
 '遭遇变异站鼠',
 '一只变异站鼠从阴影中窜出，朝你扑了过来！',
 20, 8, 1,
 '{}',
 '{"monsterKey":"station_rat"}',
 '你在废弃车站遭遇了变异站鼠！'),

('mall_echo_ambush', 'battle_placeholder', 'broken_mall',
 '遭遇商场饥饿回响',
 '一道饥饿的记忆残影从商场深处涌出！',
 15, 10, 1,
 '{}',
 '{"monsterKey":"mall_hunger_echo"}',
 '你在断裂商场遭遇了商场饥饿回响！');
