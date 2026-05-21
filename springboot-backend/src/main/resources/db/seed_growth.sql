-- ============================================================
-- Phase 3 成长系统种子数据
-- titles + avatar_rank_configs + main_chapters 初始数据
-- ============================================================

-- === 称号种子 (6个基础称号) ===

INSERT IGNORE INTO `titles` (`title_key`, `name`, `category`, `rarity`, `description`, `unlock_conditions_json`, `effects_json`, `enabled`)
VALUES

('first_explorer', '第一位探索者', 'explorer', 'common',
 '完成第一次探索的记录者。',
 '{"exploreCount": 1}',
 '{"staminaBonus": 5}',
 1),

('station_witness', '车站见证者', 'reader', 'common',
 '多次探索废弃车站后获得。',
 '{"locationExploreCount": {"ruined_station": 5}}',
 '{"channelHeatBonus": 5}',
 1),

('story_fragment_collector', '故事碎片收集者', 'reader', 'uncommon',
 '收集足够故事碎片后获得。',
 '{"storyFragments": 10}',
 '{"insightBonus": 1}',
 1),

('starstream_observed', '被星流注视者', 'starstream', 'uncommon',
 '你的行动开始被频道记录。',
 '{"channelHeat": 50}',
 '{"channelHeatGainRate": 0.05}',
 1),

('boss_trace_hunter', 'Boss线索猎人', 'explorer', 'rare',
 '多次发现阶段Boss线索后获得。',
 '{"bossClue": {"station_keeper": 3}}',
 '{"attackBonus": 3}',
 1),

('no_throne_king_seed', '无王座之王的种子', 'king', 'epic',
 '拒绝被既定王座定义的化身。',
 '{"worldLineShift": 10, "channelHeat": 100}',
 '{"narrativePressureBonus": 0.03}',
 1);

-- === 化身位阶种子 (F→A 6个基础位阶) ===

INSERT IGNORE INTO `avatar_rank_configs` (`rank_key`, `rank_name`, `display_name`, `description`, `order_num`, `next_rank_key`, `requirements_json`, `rewards_json`, `enabled`)
VALUES

('F', '临时化身', 'F级·临时化身',
 '刚被剧本系统记录，只拥有最低限度行动资格。',
 1, 'E',
 '[]',
 '{}',
 1),

('E', '剧本幸存者', 'E级·剧本幸存者',
 '完成初步生存，能够稳定参与基础剧本。',
 2, 'D',
 '[{"type":"levelMin","value":2,"label":"等级达到2"},{"type":"storyFragmentsMin","value":5,"label":"故事碎片≥5"},{"type":"explorationsByLocation","locationKey":"ruined_station","count":2,"label":"探索废弃车站≥2次"}]',
 '{"stats":{"maxHp":20,"maxStamina":10},"log":"你已从临时化身晋升为剧本幸存者。剧本开始认真对待你的存在。"}',
 1),

('D', '频道记录者', 'D级·频道记录者',
 '行动开始被频道记录，拥有初步故事价值。',
 3, 'C',
 '[{"type":"levelMin","value":5,"label":"等级达到5"},{"type":"storyEventsTriggeredMin","value":1,"label":"触发至少1次主线事件"},{"type":"bossClue","bossKey":"station_keeper","count":1,"label":"发现至少1条看门人线索"}]',
 '{"stats":{"attack":5,"defense":5},"channelHeat":20,"log":"你的行动开始被频道记录。从此以后，你的存在将在星流中留下痕迹。"}',
 1),

('C', '剧本执行者', 'C级·剧本执行者',
 '可以独立完成探索、战斗和阶段目标。',
 4, 'B',
 '[{"type":"levelMin","value":10,"label":"等级达到10"},{"type":"titlesCountMin","value":3,"label":"拥有至少3个称号"},{"type":"storyFragmentsMin","value":20,"label":"故事碎片≥20"}]',
 '{"stats":{"insight":3,"willpower":3},"log":"你已具备独立执行剧本的资格。"}',
 1),

('B', '星流候选者', 'B级·星流候选者',
 '开始被星座或频道关注，拥有成为故事核心的资格。',
 5, 'A',
 '[{"type":"levelMin","value":15,"label":"等级达到15"},{"type":"channelHeatMin","value":300,"label":"频道热度≥300"},{"type":"pkRatingMin","value":1000,"label":"PK评分≥1000"}]',
 '{"stats":{"channelHeat":50,"leadership":3},"log":"星流开始把你列入候选观察名单。"}',
 1),

('A', '故事承载者', 'A级·故事承载者',
 '拥有完整故事路线，能够在星流中留下显著痕迹。',
 6, NULL,
 '[{"type":"levelMin","value":20,"label":"等级达到20"},{"type":"rareTitleRequired","value":true,"label":"拥有至少一个稀有称号"},{"type":"worldLineShiftMin","value":10,"label":"世界线偏移≥10"}]',
 '{"storyGrade":"notable","stats":{"maxHp":50,"attack":10,"defense":10},"log":"你的故事终于拥有了足以被星流承认的重量。从此刻起，你是真正的故事承载者。"}',
 1);

-- === 主线阶段种子 (3个基础阶段) ===

INSERT IGNORE INTO `main_chapters` (`chapter_key`, `name`, `description`, `order_num`, `unlock_conditions_json`, `completion_conditions_json`, `rewards_json`, `next_chapter_key`, `enabled`)
VALUES

('main_ch01_paid_service', '第一阶段：付费服务开始',
 '剧本系统初次覆盖现实，化身需要在废弃车站中活下去。',
 1,
 '{}',
 '{"storyEventsTriggeredMin":1,"bossClue":{"station_keeper":3},"storyFragmentsMin":10}',
 '{"coins":50,"storyFragments":5,"channelHeat":20}',
 'main_ch02_contract',
 1),

('main_ch02_contract', '第二阶段：契约与赞助',
 '星流正式向地球开放频道，星座的目光开始聚焦。',
 2,
 '{"requiredPreviousChapter":"main_ch01_paid_service"}',
 '{"channelHeatMin":100,"titlesCountMin":2}',
 '{"coins":100,"storyFragments":10,"channelHeat":30}',
 'main_ch03_kings_conflict',
 1),

('main_ch03_kings_conflict', '第三阶段：王座冲突',
 '关于绝对王座的预言在幸存者中传开，诸王开始集结。',
 3,
 '{"requiredPreviousChapter":"main_ch02_contract","avatarRankMin":"C","worldLineShiftMin":10}',
 '{}',
 '{"coins":200,"storyFragments":20,"channelHeat":50}',
 NULL,
 1);
