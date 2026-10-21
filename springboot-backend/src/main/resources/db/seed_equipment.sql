-- ============================================================
-- 装备系统种子数据 (25 件装备 + 3 个套装)
-- INSERT IGNORE 保证幂等
-- ============================================================

INSERT IGNORE INTO `equipment` (`equipment_key`, `name`, `slot`, `rarity`, `description`, `base_stats_json`, `special_effects_json`, `set_key`, `max_durability`, `repair_cost`, `sell_price`, `enabled`)
VALUES

-- ============================================================
-- 武器 (weapon) x8
-- ============================================================

-- 1. 弯曲钢管 (weapon, common)
('broken_pipe', '弯曲钢管', 'weapon', 'common',
 '废墟中随手捡到的钢管，勉强可以作为武器。',
 '{"attack":5}',
 NULL, NULL,
 100, 10, 10, 1),

-- 2. 生锈菜刀 (weapon, common)
('rusty_cleaver', '生锈菜刀', 'weapon', 'common',
 '从废弃厨房翻出来的菜刀，刀刃虽钝但胜在结实。',
 '{"attack":3,"critRate":0.01}',
 NULL, NULL,
 80, 8, 5, 1),

-- 3. 消防斧 (weapon, uncommon)
('fire_axe', '消防斧', 'weapon', 'uncommon',
 '从消防柜中取出的利斧，破障能力极强。',
 '{"attack":8,"armorPen":2}',
 NULL, NULL,
 120, 20, 30, 1),

-- 4. 废墟幸存者砍刀 (weapon, uncommon) —— 套装:废墟幸存者
('survivor_machete', '废墟幸存者砍刀', 'weapon', 'uncommon',
 '在无数次逃亡中幸存下来的砍刀，刀身布满缺口却依然锋利。',
 '{"attack":7,"maxHp":15}',
 NULL, 'set_ruins_survivor',
 100, 20, 30, 1),

-- 5. 读者残页短刃 (weapon, rare)
('reader_scrap_blade', '读者残页短刃', 'weapon', 'rare',
 '残页凝成的短刃，会对异常剧情产生反应。',
 '{"attack":10,"critRate":0.03}',
 '{"storyEventBonus":0.02}', NULL,
 100, 30, 50, 1),

-- 6. 末日大剑 (weapon, rare)
('doom_greatsword', '末日大剑', 'weapon', 'rare',
 '沉重的双手巨剑，每一击都带着毁灭的重量。',
 '{"attack":14,"critDamage":0.1}',
 '{"bleedChance":0.05}', NULL,
 150, 40, 70, 1),

-- 7. 星陨之刃 (weapon, epic)
('starfall_blade', '星陨之刃', 'weapon', 'epic',
 '据说由坠落的星辰碎片锻造而成，剑身散发着幽蓝光芒。',
 '{"attack":22,"critRate":0.05,"critDamage":0.15}',
 '{"trueDamageBonus":0.03,"storyEventBonus":0.05}', NULL,
 200, 80, 200, 1),

-- 8. 深渊行者短刀 (weapon, epic) —— 套装:深渊行者
('abyss_walker_dagger', '深渊行者短刀', 'weapon', 'epic',
 '沾染深渊气息的短刀，攻击时偶尔会撕裂现实的缝隙。',
 '{"attack":18,"critRate":0.08,"critDamage":0.2}',
 '{"abyssPenetration":0.05}', 'set_abyss_walker',
 180, 70, 180, 1),

-- ============================================================
-- 防具 (armor) x6
-- ============================================================

-- 9. 车站守卫外套 (armor, common)
('station_guard_coat', '车站守卫外套', 'armor', 'common',
 '破旧但还能抵挡部分伤害的外套。',
 '{"defense":4,"maxHp":10}',
 NULL, NULL,
 100, 10, 10, 1),

-- 10. 磨损皮夹克 (armor, common)
('worn_leather_jacket', '磨损皮夹克', 'armor', 'common',
 '穿了很久的皮夹克，虽然破旧但还能提供基本防护。',
 '{"defense":3,"maxHp":8}',
 NULL, NULL,
 80, 8, 5, 1),

-- 11. 废墟幸存者护甲 (armor, uncommon) —— 套装:废墟幸存者
('survivor_vest', '废墟幸存者护甲', 'armor', 'uncommon',
 '用各种废料拼凑而成的护甲，虽不美观但实用。',
 '{"defense":8,"maxHp":20}',
 NULL, 'set_ruins_survivor',
 120, 25, 40, 1),

-- 12. 战术防弹背心 (armor, rare)
('tactical_vest', '战术防弹背心', 'armor', 'rare',
 '军用级别的防弹背心，内衬凯夫拉纤维。',
 '{"defense":14,"maxHp":30}',
 '{"damageReduction":0.03}', NULL,
 150, 45, 80, 1),

-- 13. 时空编织者长袍 (armor, epic)
('chrono_weaver_robe', '时空编织者长袍', 'armor', 'epic',
 '由时空碎片编织而成的长袍，穿戴者能隐约感知到时间的流动。',
 '{"defense":20,"maxHp":50,"insight":3}',
 '{"cooldownReduction":0.05,"narrativePressureBonus":0.03}', NULL,
 200, 90, 250, 1),

-- 14. 叙事者之铠 (armor, epic)
('narrator_armor', '叙事者之铠', 'armor', 'epic',
 '传说由某个强大的叙述者穿戴过的铠甲，蕴含着改写剧情的力量。',
 '{"defense":24,"maxHp":40}',
 '{"storyRewriteChance":0.02,"damageReduction":0.05}', NULL,
 200, 100, 300, 1),

-- ============================================================
-- 头盔 (helmet) x2
-- ============================================================

-- 15. 破损防暴头盔 (helmet, uncommon)
('cracked_riot_helmet', '破损防暴头盔', 'helmet', 'uncommon',
 '防暴警察遗留的头盔，面罩已经碎裂但主体仍完好。',
 '{"defense":5,"maxHp":10}',
 NULL, NULL,
 100, 20, 25, 1),

-- 16. 深渊凝视者兜帽 (helmet, epic)
('abyss_gazer_hood', '深渊凝视者兜帽', 'helmet', 'epic',
 '戴上后能隐约看到深渊的轮廓，但也会听到低语声。',
 '{"defense":12,"maxHp":25,"insight":4}',
 '{"abyssVision":true,"willpower":-1}', NULL,
 180, 70, 200, 1),

-- ============================================================
-- 饰品 (accessory) x5
-- ============================================================

-- 17. 破裂的观测徽章 (accessory, uncommon)
('cracked_watcher_badge', '破裂的观测徽章', 'accessory', 'uncommon',
 '带有微弱频道残响的徽章。',
 '{"insight":1,"channelHeat":5}',
 NULL, NULL,
 100, 20, 25, 1),

-- 18. 信号增幅手环 (accessory, uncommon)
('signal_amplifier_bracelet', '信号增幅手环', 'accessory', 'uncommon',
 '能够微弱增强频道信号接收的手环。',
 '{"channelHeat":8,"insight":1}',
 NULL, NULL,
 100, 25, 35, 1),

-- 19. 废墟幸存者护符 (accessory, rare) —— 套装:废墟幸存者
('survivor_amulet', '废墟幸存者护符', 'accessory', 'rare',
 '用废墟中的材料制成的粗糙护符，据说能带来好运。',
 '{"maxHp":20,"defense":3,"luck":2}',
 '{"dropRateBonus":0.03}', 'set_ruins_survivor',
 100, 35, 60, 1),

-- 20. 星流观测者之眼 (accessory, rare) —— 套装:星流观测者
('starseeker_eye', '星流观测者之眼', 'accessory', 'rare',
 '仿佛能窥见星流走向的奇异宝石，佩戴时视野变得异常清晰。',
 '{"insight":3,"channelHeat":10}',
 '{"channelHeatGainBonus":0.1}', 'set_starseeker',
 100, 50, 90, 1),

-- 21. 深渊行者指环 (accessory, epic) —— 套装:深渊行者
('abyss_walker_ring', '深渊行者指环', 'accessory', 'epic',
 '沾染了深渊气息的指环，戴上后指尖偶尔会泛起黑雾。',
 '{"critRate":0.05,"critDamage":0.1,"attack":5}',
 '{"abyssPenetration":0.03}', 'set_abyss_walker',
 150, 60, 160, 1),

-- ============================================================
-- 遗物 (relic) x4
-- ============================================================

-- 22. 深渊触痕遗物 (relic, rare)
('abyss_touched_relic', '深渊触痕遗物', 'relic', 'rare',
 '世界线偏移后残留的异常遗物。',
 '{"worldLineShift":2,"willpower":2}',
 '{"narrativePressureBonus":0.02}', NULL,
 100, 40, 60, 1),

-- 23. 星流观测者棱镜 (relic, rare) —— 套装:星流观测者
('starseeker_prism', '星流观测者棱镜', 'relic', 'rare',
 '能够折射星流光芒的水晶棱镜，持有者对频道波动更加敏感。',
 '{"channelHeat":15,"insight":2}',
 '{"channelHeatGainBonus":0.1}', 'set_starseeker',
 100, 50, 100, 1),

-- 24. 破碎时钟 (relic, epic)
('broken_timepiece', '破碎时钟', 'relic', 'epic',
 '指针永远停在某个瞬间的怀表，但偶尔会倒转几秒。',
 '{"worldLineShift":3,"insight":2}',
 '{"timeRewindChance":0.01,"cooldownReduction":0.03}', NULL,
 150, 80, 220, 1),

-- 25. 全知之书残页 (relic, epic)
('omniscient_page', '全知之书残页', 'relic', 'epic',
 '从传说中的"全知之书"上脱落的残页，上面的文字不断变化。',
 '{"insight":5,"willpower":3,"worldLineShift":2}',
 '{"storyEventBonus":0.05,"narrativePressureBonus":0.05}', NULL,
 150, 100, 350, 1);


-- ============================================================
-- 套装定义 (3 个套装)
-- ============================================================

INSERT IGNORE INTO `equipment_sets` (`set_key`, `name`, `description`, `bonuses_json`, `enabled`)
VALUES

-- 1. 废墟幸存者套装 (武器+防具+饰品, 3件)
('set_ruins_survivor', '废墟幸存者套装',
 '在废墟中挣扎求生之人所使用的装备组合。集齐2件获得攻防加成，集齐3件获得生命回复。',
 '{"2":{"attack":5,"defense":5},"3":{"attack":5,"defense":5,"combatHpRegen":2}}',
 1),

-- 2. 星流观测者套装 (饰品+遗物, 2件)
('set_starseeker', '星流观测者套装',
 '星流观测者专用的精密仪器组合。集齐2件获得频道热度获取加成。',
 '{"2":{"channelHeatGainBonus":0.1,"insight":2}}',
 1),

-- 3. 深渊行者套装 (武器+饰品, 2件)
('set_abyss_walker', '深渊行者套装',
 '曾深入深渊之人遗留的装备，散发着不祥的气息。集齐2件获得暴击加成。',
 '{"2":{"critRate":0.05,"critDamage":0.15,"attack":3}}',
 1);
