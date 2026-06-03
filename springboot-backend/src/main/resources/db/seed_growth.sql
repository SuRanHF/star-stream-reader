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


-- === ORV 原著称号 (20 个) ===
-- 叙事阵营: reader(读者) / king(王者) / abyss(深渊) / starstream(星流) / combat(战斗)
-- 克制链: reader→abyss→king→starstream→reader, combat无克制

INSERT IGNORE INTO `titles` (`title_key`, `name`, `category`, `rarity`, `description`, `unlock_conditions_json`, `effects_json`, `tags_json`, `strong_against_json`, `weak_against_json`, `enabled`)
VALUES

-- --- reader 阵营 ---
('regression_witness', '回归见证人', 'reader', 'rare',
 '你亲眼见证了刘众赫——那个经历了无数次回归的男人。你意识到这个世界不是第一次被毁灭，也不会是最后一次。',
 '{"hasTitleFlag":"title_regression_witness"}',
 '{"attack":5,"speed":3}',
 '["reader","narrative"]',
 '["abyss"]',
 '["starstream"]',
 1),

('fourth_wall_touched', '第四面墙触碰者', 'reader', 'epic',
 '长期浸淫在故事中，你开始察觉到"墙"的存在——那道分隔读者与角色的无形之壁。触碰它，你偶尔能感知到来自高维度的注视。',
 '{"exploreCount":50}',
 '{"defense":10,"insight":3}',
 '["reader","narrative"]',
 '["abyss","king"]',
 '["starstream"]',
 1),

('omniscient_reader', '全知读者', 'reader', 'legendary',
 '你读过那本书——记载了所有过去与未来的书。你不是预言家，但你知道每一个角色下一句要说什么。这种知识既是力量，也是诅咒。',
 '{"hasTitleFlag":"title_omniscient_reader"}',
 '{"attack":20,"critRate":0.1,"critDamage":0.3,"insight":5}',
 '["reader","narrative"]',
 '["abyss","king","starstream"]',
 '[]',
 1),

('script_rewriter', '剧本改写者', 'reader', 'legendary',
 '你不只读故事——你改写它。当剧本预言了忠武路的毁灭，你在空白的边缘写下了新的结局。星流审查者至今无法理解这行字是如何出现的。',
 '{"hasTitleFlag":"title_script_rewriter"}',
 '{"attack":15,"defense":10,"willpower":5}',
 '["reader","narrative"]',
 '["abyss","king"]',
 '["starstream"]',
 1),

('forbidden_reader', '禁书读者', 'reader', 'legendary',
 '明洞图书馆的禁书区记录了你最深的恐惧和最远的可能。你翻到了最后一页——即使上面写着你的死亡。你知道得越多，担子越重。但这份重量正是真正的读者与浏览者的区别。',
 '{"hasTitleFlag":"title_forbidden_reader"}',
 '{"attack":18,"critDamage":0.5,"insight":4}',
 '["reader","narrative"]',
 '["abyss"]',
 '["starstream"]',
 1),

('first_reader', '最初读者', 'reader', 'legendary',
 '在地下城最深处的记忆之墙，一本无人阅读的书等了亿万年。你是它的第一个读者。当第一页被翻开时，整个被遗忘的剧本世界都感知到了一声颤抖。',
 '{"hasTitleFlag":"title_first_reader"}',
 '{"attack":25,"defense":15,"channelHeatGainRate":0.1}',
 '["reader","narrative"]',
 '["abyss","king","starstream","combat"]',
 '[]',
 1),

('heavy_soul', '沉重灵魂', 'reader', 'rare',
 '广津大桥的审判衡量了你的故事——不是力量，而是你所承载的一切经历。你的灵魂足够沉重，足以在桥梁上留下足迹。',
 '{"hasTitleFlag":"title_heavy_soul"}',
 '{"defense":8,"maxHp":30}',
 '["reader","narrative"]',
 '["abyss"]',
 '["king"]',
 1),

('wall_listener', '墙壁聆听者', 'reader', 'rare',
 '地下迷宫深处，墙壁之中保存着所有被星流遗弃的故事。你能听到它们在低语——不是用耳朵，而是用故事碎片。',
 '{"hasTitleFlag":"title_wall_listener"}',
 '{"insight":3,"channelHeat":5}',
 '["reader","narrative"]',
 '["abyss"]',
 '["king"]',
 1),

('truth_bringer', '真相传道者', 'reader', 'epic',
 '在十王的秘密会议上，你站起来面对九位王，揭露了这个世界的本质——他们不是统治者，而是角色。真相是一把比任何武器都更锋利的东西。',
 '{"hasTitleFlag":"title_truth_bringer"}',
 '{"attack":12,"channelHeatGainRate":0.08}',
 '["reader","narrative"]',
 '["king","abyss"]',
 '["starstream"]',
 1),

-- --- king 阵营 ---
('king_without_throne', '无王座之王', 'king', 'epic',
 '你拒绝了铁王——在一个以力量为唯一规则的世界里，选择了站着而不是跪着。没有王座，但你自己就是一把不被任何手握住、也不为任何手而挥动的剑。',
 '{"hasTitleFlag":"title_defiant"}',
 '{"attack":12,"defense":8}',
 '["king","narrative"]',
 '["starstream"]',
 '["reader"]',
 1),

-- --- abyss 阵营 ---
('shadow_walker', '影行者', 'abyss', 'rare',
 '影王教会了你一种被遗忘的能力——在剧本的阴影中行走，不被星座注视。有时候，不被看到是最有力的位置。',
 '{"hasTitleFlag":"title_shadow_walker"}',
 '{"speed":8,"defense":5}',
 '["abyss","narrative"]',
 '["king"]',
 '["reader"]',
 1),

('demon_walker', '恶魔行者', 'abyss', 'epic',
 '你踏入了恶魔之门——旧世界在身后合上。恶魔的目录上多了一个名字：你的。这是在绝对陌生的领域中踏出第一步的印记。',
 '{"hasTitleFlag":"title_demon_walker"}',
 '{"attack":10,"maxHp":40}',
 '["abyss","narrative"]',
 '["king"]',
 '["reader"]',
 1),

('world_walker', '世界行者', 'abyss', 'legendary',
 '在踏入恶魔之门前，你回头看了最后一眼。旧世界的余韵在夜风中向你低语："别忘记——把这一切写下来。不是当作结局——当作开篇。"',
 '{"hasTitleFlag":"title_world_walker"}',
 '{"attack":15,"defense":12,"willpower":4}',
 '["abyss","narrative"]',
 '["king","starstream"]',
 '["reader"]',
 1),

('tree_planter', '世界树种树人', 'abyss', 'epic',
 '在世界树苗下，你埋下了故事碎片——用殉道星座的残骸培育一株承载所有被遗忘故事的树。当它开花时，世界将回忆起一切。',
 '{"hasTitleFlag":"title_tree_planter"}',
 '{"defense":10,"maxHp":50,"channelHeatGainRate":0.05}',
 '["abyss","narrative"]',
 '["king"]',
 '["reader","starstream"]',
 1),

('memory_keeper', '记忆守护者', 'abyss', 'rare',
 '记忆之墙中，你没有选择成为英雄——你选择成为一名记住者。对于被遗弃的故事来说，被记得就是复活。',
 '{"hasTitleFlag":"title_memory_keeper"}',
 '{"insight":4,"channelHeat":10}',
 '["abyss","narrative"]',
 '["king"]',
 '["reader"]',
 1),

-- --- starstream 阵营 ---
('story_bearer', '故事承载者', 'starstream', 'epic',
 '你读到了火之主最后的频道——一个自愿沉默的星座的故事。你没有杀死它，你理解了它。理解——是比胜利更稀有的东西。',
 '{"hasTitleFlag":"title_story_bearer"}',
 '{"attack":8,"channelHeatGainRate":0.1}',
 '["starstream","narrative"]',
 '["reader"]',
 '["abyss"]',
 1),

('queens_messenger', '晦日使者', 'starstream', 'legendary',
 '你成为了"最黑暗春天的女王"在世间的代言人。从此刻起，你凝视的每一片黑暗中，都能看到等待萌发的春天。',
 '{"hasTitleFlag":"title_queens_messenger"}',
 '{"attack":20,"defense":10,"channelHeatGainRate":0.12}',
 '["starstream","narrative","abyss"]',
 '["reader","king"]',
 '["abyss"]',
 1),

-- --- combat 阵营 ---
('giant_slayer', '巨人杀手', 'combat', 'epic',
 '在东大门的烈焰中，你击败了一尊陨落的星座。那场战斗的硝烟至今仍留在你的衣角上——提醒你和所有看到你身影的人：巨人也是可以被杀死的。',
 '{"hasTitleFlag":"title_giant_slayer"}',
 '{"attack":15,"critRate":0.08,"critDamage":0.2}',
 '["combat","narrative"]',
 '[]',
 '[]',
 1),

-- --- support → king ---
('liberator', '解放者', 'king', 'rare',
 '广津大桥的守护者已在桥上站了数百年。你没有要求她继续坚守——你请她离开了。真正的守护需要的不是解放，但真正的善良是说出那句话。',
 '{"hasTitleFlag":"title_liberator"}',
 '{"speed":5,"channelHeat":15}',
 '["king","narrative"]',
 '["starstream"]',
 '["reader"]',
 1),

-- --- explorer → combat ---
('dungeon_delver', '地下城探索者', 'combat', 'rare',
 '江南站的地下城入口处，你是第一千零三十七位签下名字的探索者。在你之前的六百人再也没有出来。但你签了。',
 '{"hasTitleFlag":"title_dungeon_delver"}',
 '{"defense":6,"maxHp":25}',
 '["combat","narrative"]',
 '[]',
 '[]',
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
 '[{"type":"levelMin","value":6,"label":"等级达到6"},{"type":"storyEventsTriggeredMin","value":1,"label":"触发至少1次主线事件"},{"type":"bossClue","bossKey":"station_keeper","count":1,"label":"发现至少1条看门人线索"}]',
 '{"stats":{"attack":5,"defense":5},"channelHeat":20,"log":"你的行动开始被频道记录。从此以后，你的存在将在星流中留下痕迹。"}',
 1),

('C', '剧本执行者', 'C级·剧本执行者',
 '可以独立完成探索、战斗和阶段目标。',
 4, 'B',
 '[{"type":"levelMin","value":12,"label":"等级达到12"},{"type":"titlesCountMin","value":3,"label":"拥有至少3个称号"},{"type":"storyFragmentsMin","value":20,"label":"故事碎片≥20"}]',
 '{"stats":{"insight":3,"willpower":3},"log":"你已具备独立执行剧本的资格。"}',
 1),

('B', '星流候选者', 'B级·星流候选者',
 '开始被星座或频道关注，拥有成为故事核心的资格。',
 5, 'A',
 '[{"type":"levelMin","value":20,"label":"等级达到20"},{"type":"channelHeatMin","value":300,"label":"频道热度≥300"},{"type":"pkRatingMin","value":1000,"label":"PK评分≥1000"}]',
 '{"stats":{"channelHeat":50,"leadership":3},"log":"星流开始把你列入候选观察名单。"}',
 1),

('A', '故事承载者', 'A级·故事承载者',
 '拥有完整故事路线，能够在星流中留下显著痕迹。',
 6, NULL,
 '[{"type":"levelMin","value":30,"label":"等级达到30"},{"type":"rareTitleRequired","value":true,"label":"拥有至少一个稀有称号"},{"type":"worldLineShiftMin","value":10,"label":"世界线偏移≥10"}]',
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
