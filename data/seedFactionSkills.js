// 星座阵营技能树种子数据 (Phase 3 - Round 2 Expansion)
// 8 constellations, each with 3-4 unique skills

function seedFactionSkills(db) {
  var insert = db.prepare(`
    INSERT OR IGNORE INTO faction_skills (constellation_key, skill_key, skill_name, description, skill_type, effect_json, required_faction_level, cost_faction_contribution, cooldown)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  var skills = [
    // ========================================== 金乌神教 (攻击/暴击) ==========================================
    ['golden_sun', 'golden_sun_inferno', '金乌焚天', '调用金乌之力，对敌人造成200%攻击力的火焰伤害，并在2回合内灼烧目标。', 'attack',
      '{"atk_mult":2.0,"burn_duration":2,"burn_damage":0.15}', 2, 50, 5],
    ['golden_sun', 'golden_sun_radiance', '烈日辉光', '释放金乌的辉光，提升自身暴击率15%，持续3回合。', 'buff',
      '{"crit_rate_bonus":0.15,"duration":3}', 1, 30, 4],
    ['golden_sun', 'golden_sun_feather', '三足金羽', '金乌羽翼的投影，对敌人造成150%攻击力伤害，并使下一次攻击必定暴击。', 'attack',
      '{"atk_mult":1.5,"next_attack_crit":true}', 2, 40, 6],
    ['golden_sun', 'golden_sun_ancient', '最古之阳', '召唤金乌从远古投射而来的力量，提升全队攻击力20%并恢复自身HP15%，持续2回合。', 'buff',
      '{"atk_bonus":0.20,"heal_hp_pct":0.15,"duration":2}', 3, 80, 8],

    // ========================================== 黑焰龙渊 (纯攻击/速度) ==========================================
    ['black_flame_dragon', 'black_flame_breath', '黑焰龙息', '深渊黑焰龙的吐息攻击，对敌人造成220%攻击力的暗属性伤害，无视30%防御。', 'attack',
      '{"atk_mult":2.2,"def_ignore_pct":0.30}', 2, 50, 5],
    ['black_flame_dragon', 'black_flame_speed', '龙翼疾风', '以黑焰龙翼包裹自身，本回合速度翻倍并获得先手权。', 'buff',
      '{"speed_mult":2.0,"priority":true,"duration":1}', 1, 30, 4],
    ['black_flame_dragon', 'black_flame_emperor', '黑龙皇帝', '以黑焰皇帝之威压压制全场，对全体敌人造成130%攻击力伤害，50%概率附加1回合虚弱。', 'attack',
      '{"atk_mult":1.3,"aoe":true,"debuff_chance":0.5,"debuff_effect":"weakness_1turn"}', 3, 70, 7],
    ['black_flame_dragon', 'black_flame_revenge', '龙血沸腾', '生命值低于50%时激活，攻击力提升30%，速度提升20%，直到战斗结束。', 'passive',
      '{"hp_below_50":{"atk_bonus":0.30,"spd_bonus":0.20}}', 2, 45, 0],

    // ========================================== 火之审判庭 (均衡/暴击) ==========================================
    ['demon_judge_of_fire', 'judge_fire_verdict', '审判之火', '以降下的业火对敌人进行审判，造成180%攻击力伤害，暴击时附加10%当前HP的真实伤害。', 'attack',
      '{"atk_mult":1.8,"crit_bonus_true_dmg_pct":0.10}', 2, 50, 5],
    ['demon_judge_of_fire', 'judge_fire_shield', '审判圣盾', '火之审判者的加护，获得相当于防御力150%的火焰护盾值，持续2回合。', 'defense',
      '{"shield_mult":1.5,"duration":2}', 1, 35, 5],
    ['demon_judge_of_fire', 'judge_fire_sentence', '天谴之焰', '对敌人施加天谴，降低其暴击抗性20%和防御15%，持续3回合。', 'debuff',
      '{"crit_resist_reduce":0.20,"def_reduce":0.15,"duration":3}', 2, 45, 6],
    ['demon_judge_of_fire', 'judge_fire_mercy', '审判者的慈悲', '被动：每当自身生命值低于30%时，自动恢复最大HP的25%，每场战斗限触发一次。', 'passive',
      '{"hp_below_30":{"heal_pct":0.25,"once_per_battle":true}}', 3, 75, 0],

    // ========================================== 深渊凝视者 (暴击/弱点) ==========================================
    ['abyss_eye', 'abyss_eye_mark', '深渊标记', '以深渊之眼锁定目标弱点，对敌人施加标记，使其受到的暴击伤害增加40%，持续3回合。', 'debuff',
      '{"crit_dmg_taken_bonus":0.40,"duration":3}', 2, 45, 5],
    ['abyss_eye', 'abyss_eye_gaze', '深渊凝视', '召唤深渊之眼的注视，对单体敌人造成170%攻击力伤害，必定命中且暴击率+25%。', 'attack',
      '{"atk_mult":1.7,"always_hit":true,"crit_rate_bonus":0.25}', 1, 35, 4],
    ['abyss_eye', 'abyss_eye_reveal', '弱点揭示', '揭示敌人最深的弱点，使其防御力降低40%并失去所有增益效果。', 'debuff',
      '{"def_reduce":0.40,"remove_buffs":true}', 2, 55, 7],
    ['abyss_eye', 'abyss_eye_truth', '深渊真相', '被动：暴击时恢复造成伤害的15%作为HP，并有10%概率发现敌人隐藏弱点（下次攻击伤害+50%）。', 'passive',
      '{"crit_lifesteal":0.15,"hidden_weakpoint_chance":0.10,"weakpoint_dmg_bonus":0.50}', 3, 80, 0],

    // ========================================== 命运编织会 (速度/幸运) ==========================================
    ['wheel_of_fate', 'wheel_fate_weave', '命运编织', '重新编织命运的丝线，改变当前回合的行动顺序，并提升自身速度20%持续2回合。', 'buff',
      '{"reorder_turn":true,"spd_bonus":0.20,"duration":2}', 1, 30, 4],
    ['wheel_of_fate', 'wheel_fate_thread', '因果之线', '将因果之线缠绕在敌人身上，使其25%概率行动失败（眩晕），持续2回合。', 'debuff',
      '{"stun_chance":0.25,"duration":2}', 2, 45, 5],
    ['wheel_of_fate', 'wheel_fate_fortune', '幸运时刻', '借命运之轮的力量扭转局势，获得一次必定闪避，并在下次攻击时伤害+35%。', 'buff',
      '{"guaranteed_dodge":true,"next_atk_bonus":0.35}', 2, 40, 6],
    ['wheel_of_fate', 'wheel_fate_loop', '命运轮回', '被动：当受到致命伤害时，有30%概率触发"命运回环"，保留1HP并清除所有负面效果。每场战斗最多触发一次。', 'passive',
      '{"death_save_chance":0.30,"death_save_hp":1,"clear_debuffs":true,"once_per_battle":true}', 3, 80, 0],

    // ========================================== 冥界女王府 (防御/持久) ==========================================
    ['queen_of_underworld', 'underworld_grasp', '冥界之握', '冥界女王的冰冷触碰，对敌人造成150%攻击力伤害并吸取伤害量30%的HP。', 'attack',
      '{"atk_mult":1.5,"lifesteal_pct":0.30}', 1, 35, 4],
    ['queen_of_underworld', 'underworld_ward', '冥府护盾', '以冥界黑莲之力构筑护盾，获得最大HP25%的护盾值并提升防御力15%，持续3回合。', 'defense',
      '{"shield_pct":0.25,"def_bonus":0.15,"duration":3}', 2, 45, 5],
    ['queen_of_underworld', 'underworld_bloom', '死莲绽放', '黑莲在敌人脚下绽放，造成180%攻击力伤害，若目标生命低于30%则伤害翻倍。', 'attack',
      '{"atk_mult":1.8,"execute_mult":2.0,"execute_threshold":0.30}', 2, 55, 6],
    ['queen_of_underworld', 'underworld_eternal', '冥界永恒', '被动：受到致命伤害时触发，以HP为1存活并获得"不死"状态1回合（免疫死亡）。每场战斗最多触发一次。', 'passive',
      '{"death_save_hp":1,"undying_duration":1,"once_per_battle":true}', 3, 75, 0],

    // ========================================== 海上战神盟 (防御/速度) ==========================================
    ['maritime_war_god', 'maritime_tide_wall', '潮汐壁', '唤起潮汐形成防御之壁，提升防御力40%并反弹15%受到的伤害，持续2回合。', 'defense',
      '{"def_bonus":0.40,"damage_reflect_pct":0.15,"duration":2}', 1, 35, 5],
    ['maritime_war_god', 'maritime_trident', '三叉戟投掷', '以战神三叉戟的投影发动攻击，造成200%攻击力伤害，无视格挡效果。', 'attack',
      '{"atk_mult":2.0,"ignore_block":true}', 2, 50, 5],
    ['maritime_war_god', 'maritime_wind_wave', '风浪突进', '借助海风之力加速，本回合速度+50%，并对敌人发动先制打击（额外造成一次50%攻击力伤害）。', 'attack',
      '{"spd_bonus":0.50,"bonus_atk_mult":0.5}', 2, 45, 4],
    ['maritime_war_god', 'maritime_unsinkable', '永不沉没', '被动：每回合结束时恢复最大HP的5%。当HP低于20%时，防御力额外+25%。', 'passive',
      '{"hp_regen_pct":0.05,"hp_below_20":{"def_bonus":0.25}}', 3, 70, 0],

    // ========================================== 星流守望塔 (均衡/硬币) ==========================================
    ['star_stream_watcher', 'star_flow_channel', '星流频道', '开启星流频道共鸣，提升全属性10%并获得额外硬币+30%的效果，持续3回合。', 'buff',
      '{"all_stats_bonus":0.10,"coin_bonus_pct":0.30,"duration":3}', 1, 30, 5],
    ['star_stream_watcher', 'star_flow_beam', '星流光束', '汇聚星流之力发射光束，造成160%攻击力伤害，每拥有100硬币增加1%伤害（上限50%）。', 'attack',
      '{"atk_mult":1.6,"coin_scaling_dmg":0.01,"coin_per_100":100,"coin_cap":50}', 2, 50, 4],
    ['star_stream_watcher', 'star_flow_watch', '守望者的祝福', '星流的守望者降下均衡祝福，恢复自身HP20%并清除一项负面效果。', 'defense',
      '{"heal_pct":0.20,"cleanse_debuff":1}', 2, 40, 5],
    ['star_stream_watcher', 'star_flow_guardian', '星流守护', '被动：当装备的星流守望塔阵营技能达到3个时，所有技能冷却减少1回合。硬币获取量+20%。', 'passive',
      '{"set_bonus_3":{"cooldown_reduce":1},"coin_bonus_passive":0.20}', 3, 80, 0]
  ];

  for (var i = 0; i < skills.length; i++) {
    insert.run.apply(insert, skills[i]);
  }
  console.log('Faction skills seed complete: ' + skills.length + ' skills (8 constellations).');
}

module.exports = { seedFactionSkills };
