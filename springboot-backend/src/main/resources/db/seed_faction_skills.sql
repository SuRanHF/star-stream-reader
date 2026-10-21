-- ============================================================
-- Phase ?: 8星座阵营专属技能种子数据 (24 个技能)
-- 每个阵营 3 个专属技能，INSERT IGNORE 保证幂等
-- ============================================================

-- ========== 1. 金乌神教 (暴击取向，灼热贯穿) ==========

INSERT IGNORE INTO `skills` (`skill_key`, `name`, `type`, `rarity`, `description`, `unlock_conditions_json`, `effects_json`, `cost_json`, `cooldown_seconds`, `enabled`) VALUES

('solarch_crit_bless', '灼日恩赐', 'passive', 'uncommon',
 '金乌神火灌注周身，你对敌人弱点的感知愈发敏锐。',
 '{"factionKey": "solarch_cult", "factionContribMin": 50}',
 '{"critRateBonus": 0.04, "critDamageBonus": 0.08}',
 '{"coins": 200}',
 0, 1),

('solarch_flame_pierce', '金乌贯日', 'combat', 'rare',
 '凝聚太阳真火化为利箭，贯穿敌人护甲。灼热伤害无视部分防御。',
 '{"factionKey": "solarch_cult", "factionContribMin": 100, "channelHeatMin": 150}',
 '{"attack": 5, "armorPenetration": 3, "burnChance": 0.25}',
 '{"coins": 400, "storyFragments": 20}',
 12, 1),

('solarch_heat_sense', '炽热感知', 'exploration', 'rare',
 '金乌之力赋予你感知热源的能力，在探索中更容易发现隐藏的敌人弱点。',
 '{"factionKey": "solarch_cult", "factionContribMin": 80}',
 '{"critRateBonus": 0.02, "storyEventBonus": 0.04}',
 '{"coins": 300, "storyFragments": 15}',
 0, 1),


-- ========== 2. 黑焰龙渊 (纯攻取向，速度抢先) ==========

('abyss_dragon_fury', '龙渊狂怒', 'passive', 'uncommon',
 '龙渊深处的黑焰灌注血脉，每次攻击都裹挟深渊之力。',
 '{"factionKey": "blackflame_abyss", "factionContribMin": 50}',
 '{"attack": 4, "speedBonus": 2}',
 '{"coins": 200}',
 0, 1),

('abyss_shadow_rush', '黑焰突袭', 'combat', 'rare',
 '化为黑焰残影瞬间接近敌人，先手攻击时伤害大幅提升。速度越快，威力越强。',
 '{"factionKey": "blackflame_abyss", "factionContribMin": 100, "channelHeatMin": 150}',
 '{"firstStrikeBonus": 0.20, "speedBonus": 3, "attack": 3}',
 '{"coins": 400, "storyFragments": 20}',
 10, 1),

('abyss_flame_trail', '焰痕追踪', 'exploration', 'uncommon',
 '黑焰在你经过之处留下印记，帮助你追踪猎物并在龙渊遗迹中获得优势。',
 '{"factionKey": "blackflame_abyss", "factionContribMin": 60}',
 '{"attack": 2, "staminaCostReduce": 0.04}',
 '{"coins": 250, "storyFragments": 10}',
 0, 1),


-- ========== 3. 火之审判庭 (攻速均衡，暴击加成) ==========

('tribunal_judgment', '审判烙印', 'passive', 'uncommon',
 '审判庭的圣火在你体内留下烙印，每次暴击都额外灼烧敌人。',
 '{"factionKey": "fire_tribunal", "factionContribMin": 50}',
 '{"critRateBonus": 0.03, "critDamageBonus": 0.10}',
 '{"coins": 200}',
 0, 1),

('tribunal_holy_fire', '裁决圣火', 'combat', 'rare',
 '召唤审判庭圣火对敌人进行裁决。攻速越快，圣火连锁次数越多。',
 '{"factionKey": "fire_tribunal", "factionContribMin": 100, "channelHeatMin": 150}',
 '{"attack": 4, "speedBonus": 2, "critRateBonus": 0.03}',
 '{"coins": 400, "storyFragments": 20}',
 15, 1),

('tribunal_verdict', '罪与罚', 'narrative', 'epic',
 '你获得了审判庭的授权，能够对邪恶势力施加叙事层面的压制。',
 '{"factionKey": "fire_tribunal", "factionContribMin": 150, "channelHeatMin": 250}',
 '{"narrativePressureBonus": 0.05, "critDamageBonus": 0.05, "attack": 2}',
 '{"coins": 600, "storyFragments": 30}',
 0, 1),


-- ========== 4. 深渊凝视者 (暴击取向，弱点洞察) ==========

('gaze_weakness_scan', '深渊之眼', 'passive', 'uncommon',
 '凝视深渊时，深渊也在凝视你。你获得了洞察万物弱点的能力。',
 '{"factionKey": "abyss_gazers", "factionContribMin": 50}',
 '{"insight": 2, "critRateBonus": 0.04}',
 '{"coins": 200}',
 0, 1),

('gaze_fatal_strike', '致命凝视', 'combat', 'rare',
 '将深渊之力凝聚于一击，对敌人弱点发动致命攻击。目标生命越低，暴击率越高。',
 '{"factionKey": "abyss_gazers", "factionContribMin": 100, "channelHeatMin": 150}',
 '{"critRateBonus": 0.06, "critDamageBonus": 0.15, "insight": 1}',
 '{"coins": 400, "storyFragments": 20}',
 18, 1),

('gaze_truth_sight', '真视深渊', 'exploration', 'rare',
 '深渊赋予你看穿幻象的能力，在探索中更容易发现隐藏线索和真相。',
 '{"factionKey": "abyss_gazers", "factionContribMin": 80}',
 '{"storyEventBonus": 0.05, "insight": 2}',
 '{"coins": 350, "storyFragments": 20}',
 0, 1),


-- ========== 5. 命运编织会 (速度取向，幸运提升) ==========

('weave_fate_thread', '命运丝线', 'passive', 'uncommon',
 '你学会了感知命运的丝线，在关键时刻总能获得一丝幸运眷顾。',
 '{"factionKey": "fate_weavers", "factionContribMin": 50}',
 '{"luck": 3, "speedBonus": 2}',
 '{"coins": 200}',
 0, 1),

('weave_destiny_rewind', '命运回溯', 'combat', 'rare',
 '在危急时刻短暂回溯命运，闪避致命攻击并获得速度爆发。',
 '{"factionKey": "fate_weavers", "factionContribMin": 100, "channelHeatMin": 150}',
 '{"speedBonus": 4, "dodgeBonus": 0.15, "luck": 2}',
 '{"coins": 400, "storyFragments": 20}',
 20, 1),

('weave_lucky_encounter', '织运逢缘', 'exploration', 'rare',
 '命运织网让你在探索中更容易遭遇有利事件和珍贵资源。',
 '{"factionKey": "fate_weavers", "factionContribMin": 80}',
 '{"luck": 2, "storyEventBonus": 0.06, "coinGainBonus": 0.05}',
 '{"coins": 350, "storyFragments": 15}',
 0, 1),


-- ========== 6. 冥界女王府 (防御取向，持久战) ==========

('queen_deathless_pact', '不死契约', 'passive', 'uncommon',
 '与冥界女王签下契约，你的生命力获得冥界庇佑，更难被击倒。',
 '{"factionKey": "queen_of_underworld", "factionContribMin": 50}',
 '{"maxHp": 20, "defense": 3}',
 '{"coins": 200}',
 0, 1),

('queen_soul_shield', '冥魂护盾', 'combat', 'rare',
 '召唤冥界灵魂形成护盾，吸收伤害并在破碎时对周围敌人造成灵魂反噬。',
 '{"factionKey": "queen_of_underworld", "factionContribMin": 100, "channelHeatMin": 150}',
 '{"defense": 5, "maxHp": 15, "damageReduce": 0.08}',
 '{"coins": 400, "storyFragments": 20}',
 25, 1),

('queen_soul_resilience', '灵魂坚韧', 'exploration', 'uncommon',
 '冥界之力让你的灵魂更加坚韧，降低遭遇负面事件的概率。',
 '{"factionKey": "queen_of_underworld", "factionContribMin": 60}',
 '{"defense": 2, "staminaCostReduce": 0.05}',
 '{"coins": 250, "storyFragments": 10}',
 0, 1),


-- ========== 7. 海上战神盟 (防御取向，速度加成) ==========

('maritime_tide_guard', '潮汐守卫', 'passive', 'uncommon',
 '海上战神的庇护让你如鱼得水，移动和反应速度都获得提升。',
 '{"factionKey": "sea_war_god", "factionContribMin": 50}',
 '{"defense": 2, "speedBonus": 3}',
 '{"coins": 200}',
 0, 1),

('maritime_storm_charge', '风暴冲锋', 'combat', 'rare',
 '借助海神之力掀起风暴，攻防兼备的冲锋。防御越高，冲锋伤害越强。',
 '{"factionKey": "sea_war_god", "factionContribMin": 100, "channelHeatMin": 150}',
 '{"attack": 3, "defense": 4, "speedBonus": 2}',
 '{"coins": 400, "storyFragments": 20}',
 15, 1),

('maritime_naval_explore', '航海远征', 'exploration', 'uncommon',
 '海上战神的航海经验赋予你更广阔的探索视野和更强的续航能力。',
 '{"factionKey": "sea_war_god", "factionContribMin": 60}',
 '{"maxStamina": 10, "storyEventBonus": 0.03}',
 '{"coins": 250, "storyFragments": 10}',
 0, 1),


-- ========== 8. 星流守望塔 (均衡取向，硬币加成) ==========

('sentinel_star_bless', '星流庇佑', 'passive', 'uncommon',
 '星流之力庇护着你，每次行动都带来额外的星币收益。',
 '{"factionKey": "starwatch_tower", "factionContribMin": 50}',
 '{"coinGainBonus": 0.08, "attack": 1, "defense": 1}',
 '{"coins": 150}',
 0, 1),

('sentinel_cosmic_shield', '星穹护盾', 'combat', 'rare',
 '凝聚星辰之力形成护盾，在战斗中提供均衡的攻防加成。',
 '{"factionKey": "starwatch_tower", "factionContribMin": 100, "channelHeatMin": 150}',
 '{"attack": 3, "defense": 3, "damageReduce": 0.05}',
 '{"coins": 400, "storyFragments": 20}',
 20, 1),

('sentinel_starlight_path', '星路引航', 'exploration', 'rare',
 '星光照亮前路，让你在探索中发现更多隐藏资源和星币奖励。',
 '{"factionKey": "starwatch_tower", "factionContribMin": 80}',
 '{"coinGainBonus": 0.06, "storyEventBonus": 0.04, "luck": 1}',
 '{"coins": 300, "storyFragments": 15}',
 0, 1);
