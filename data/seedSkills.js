// 全知读者视角 技能种子数据

function seedSkills(db) {
  const insert = db.prepare(`
    INSERT OR IGNORE INTO skills (skill_key, name, description, skill_type, rarity, effects_json, required_titles_json, required_fragments, cooldown)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  const skills = [
    // Attack skills
    ['quick_judgment', '快速判断', '利用对剧本格式的熟悉，在战斗开始的瞬间预判敌人的攻击路线，抢先发动两次快速打击。', 'attack', 'common',
      '{"damage_formula":"attack*0.7","hits":2,"accuracy":0.95}', '[]', 1, 3],
    ['desperate_counter', '绝境反击', '当生命垂危时释放全身残余的力量发动舍命一击。剩余生命值越低，造成的伤害越高。这是所有读者都熟悉的经典桥段。', 'attack', 'rare',
      '{"damage_formula":"attack*(1+lost_hp_pct*1.0)","single_hit":true,"min_damage_pct":1.2}', '["title_demon_king"]', 4, 6],
    ['demon_king_form', '魔王化身', '短暂调用主角曾拥有的七十二魔王之力的一部分投影，释放出毁灭性的黑焰攻击。但使用后必须献祭自身体力作为代价。', 'attack', 'legendary',
      '{"damage_formula":"attack*3.5","hp_cost_pct":0.20,"single_hit":true}', '["title_demon_king"]', 8, 10],

    // Passive skills
    ['weakness_insight', '弱点洞察', '通过长时间阅读剧本培养出的敏锐观察力，让你能在敌人动作的间隙中发现破绽，大幅提升致命一击的概率。', 'passive', 'common',
      '{"crit_rate_bonus":0.12,"crit_damage_multiplier":1.5}', '[]', 2, 0],
    ['coin_boost', '硬币强化', '被动增强你对频道赞助系统的亲和力。每场战斗胜利后，星座们愿意多投下几枚硬币——毕竟有趣的故事值得更多打赏。', 'passive', 'uncommon',
      '{"coin_drop_bonus_pct":0.30}', '[]', 2, 0],
    ['sponsor_rejection', '赞助拒绝', '你学会了在心理层面屏蔽星座们的低语。这将降低世界线偏移值的累积速度，并减少被赞助系统强制干涉的概率。', 'passive', 'rare',
      '{"world_line_shift_reduction_pct":0.25,"constellation_influence_resist":0.20}', '["title_constellation_hater"]', 3, 0],
    ['final_chapter_preview', '终章预读', '只有少数观测者掌握的禁忌技能——在战斗开始前预先阅读对方的属性面板。这是在成为"完全读者"的路上最关键的一步。', 'passive', 'legendary',
      '{"reveal_boss_stats":true,"first_turn_initiative":true,"boss_damage_taken_reduction":0.10}', '["title_final_chapter_reader"]', 10, 0],

    // Defense skill
    ['defensive_stance', '防御姿态', '进入完全防御状态，将剧本中的文字暂时凝为护盾包裹全身，大幅降低接下来受到的伤害。', 'defense', 'uncommon',
      '{"damage_reduction_pct":0.50,"duration_turns":2,"can_act":false}', '[]', 2, 5],

    // Exploration skills
    ['channel_taunt', '频道嘲讽', '故意在频道中发出挑衅信号，吸引周围的怪物主动来袭。虽然危险，但对于想快速获取经验和硬币的人来说效率极高。', 'exploration', 'uncommon',
      '{"encounter_rate_boost":0.50,"duration_steps":10,"elite_encounter_chance":0.05}', '[]', 2, 15],
    ['worldline_deflection', '世界线偏折', '利用对世界线运作规律的了解，在探索中轻微偏折前进的路径，有效避开最差的随机事件。', 'exploration', 'rare',
      '{"bad_event_avoid_chance":0.40,"good_event_boost":0.15}', '[]', 3, 8],

    // PK skill
    ['kings_dominance', '王权压制', '无名之王的权能碎片。在与其他角色对抗时，对等级低于你的对手施加不可抗拒的威慑效果，使对方全属性暂时下降。', 'pk', 'epic',
      '{"vs_lower_level":{"enemy_atk_reduce_pct":0.20,"enemy_def_reduce_pct":0.20,"enemy_speed_reduce_pct":0.15}}', '["title_absolute_throne"]', 5, 8],

    // Story skill
    ['story_reconstruction', '故事重构', '在关键时刻，你能够看到剧本中被隐藏的第三个选项——那些被删除的或未完成的分支。改写故事走向的能力，是所有角色的终极追求。', 'story', 'legendary',
      '{"reveal_hidden_choices":true,"unlock_alternate_routes":true,"max_uses_per_chapter":1}', '["title_true_reader"]', 10, 0],
  ];

  for (const s of skills) {
    insert.run(...s);
  }
  console.log('Skill seed data inserted.');
}

module.exports = { seedSkills };
