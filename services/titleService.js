const { getDb } = require('../db/database');
const playerService = require('./playerService');

function checkUnlocks(playerId) {
  const db = getDb();
  const player = playerService.getRaw(playerId);
  if (!player) return [];

  const allTitles = db.prepare('SELECT * FROM titles').all();
  const playerTitles = JSON.parse(player.titles_json);
  const titleProgress = JSON.parse(player.title_progress_json);
  const storyFlags = JSON.parse(player.story_flags_json);
  const permFlags = JSON.parse(player.permanent_flags_json);
  const stats = JSON.parse(player.stats_json);
  const sponsors = JSON.parse(player.sponsors_json);

  const newlyUnlocked = [];

  for (const title of allTitles) {
    if (playerTitles.includes(title.title_key)) continue;

    const conditions = JSON.parse(title.conditions_json);
    if (!evaluateConditions(conditions, playerTitles, titleProgress, storyFlags, permFlags, stats, sponsors)) {
      // Update progress
      if (conditions.flags) {
        let progress = 0;
        let target = Object.keys(conditions.flags).length;
        for (const [k, v] of Object.entries(conditions.flags)) {
          if (storyFlags[k] === v || permFlags[k] === v) progress++;
        }
        if (!titleProgress[title.title_key]) titleProgress[title.title_key] = {};
        titleProgress[title.title_key].progress = progress;
        titleProgress[title.title_key].target = target;
      }
      continue;
    }

    // Check exclusive titles
    const exclusiveWith = JSON.parse(title.exclusive_with_json);
    if (exclusiveWith.length > 0 && exclusiveWith.some(t => playerTitles.includes(t))) continue;

    playerTitles.push(title.title_key);
    newlyUnlocked.push({ title_key: title.title_key, name: title.name, description: title.description, rarity: title.rarity, effects: JSON.parse(title.effects_json) });
  }

  if (newlyUnlocked.length > 0) {
    playerService.update(playerId, { titles_json: playerTitles, title_progress_json: titleProgress });
  }

  return newlyUnlocked;
}

function getUnlocked(playerId) {
  const db = getDb();
  const player = playerService.getRaw(playerId);
  if (!player) return [];

  const playerTitles = JSON.parse(player.titles_json);
  if (playerTitles.length === 0) return [];

  const placeholders = playerTitles.map(() => '?').join(',');
  const rows = db.prepare(`SELECT * FROM titles WHERE title_key IN (${placeholders})`).all(...playerTitles);
  return rows.map(t => ({
    title_key: t.title_key,
    name: t.name,
    description: t.description,
    rarity: t.rarity,
    effects: JSON.parse(t.effects_json)
  }));
}

function evaluateConditions(conditions, playerTitles, titleProgress, storyFlags, permFlags, stats, sponsors) {
  // Check stats thresholds
  if (conditions.stats) {
    for (const [stat, threshold] of Object.entries(conditions.stats)) {
      const val = stats[stat] || 0;
      if (threshold.min !== undefined && val < threshold.min) return false;
      if (threshold.max !== undefined && val > threshold.max) return false;
    }
  }

  // Check flags
  if (conditions.flags) {
    for (const [flag, val] of Object.entries(conditions.flags)) {
      if ((storyFlags[flag] || permFlags[flag]) !== val) return false;
    }
  }

  // Check permanent_flags
  if (conditions.permanent_flags) {
    for (const [flag, threshold] of Object.entries(conditions.permanent_flags)) {
      if (typeof threshold === 'boolean') {
        if (threshold === true && !permFlags[flag]) return false;
        if (threshold === false && permFlags[flag]) return false;
      } else {
        const val = permFlags[flag] || 0;
        if (threshold.min !== undefined && val < threshold.min) return false;
        if (threshold.max !== undefined && val > threshold.max) return false;
      }
    }
  }

  // Check titles count (supports both plain number and {min: N} format)
  if (conditions.titles_count !== undefined) {
    const min = typeof conditions.titles_count === 'object' ? conditions.titles_count.min : conditions.titles_count;
    if (playerTitles.length < min) return false;
  }

  // Check sponsors count (supports both plain number and {min: N} format)
  if (conditions.sponsors_count !== undefined) {
    const min = typeof conditions.sponsors_count === 'object' ? conditions.sponsors_count.min : conditions.sponsors_count;
    if ((sponsors || []).length < min) return false;
  }

  // Check required titles
  if (conditions.titles) {
    for (const t of conditions.titles) {
      if (!playerTitles.includes(t)) return false;
    }
  }

  return true;
}

// 计算称号对战斗的加成
function computeCombatModifiers(player) {
  const titles = player.titles || [];
  if (titles.length === 0) return {};

  const db = getDb();
  const mods = { attackBonus: 0, defenseBonus: 0, critRateBonus: 0, critDamageBonus: 0, damageReduction: 0 };

  const placeholders = titles.map(() => '?').join(',');
  const rows = db.prepare(`SELECT effects_json FROM titles WHERE title_key IN (${placeholders})`).all(...titles);

  for (const row of rows) {
    const effects = JSON.parse(row.effects_json);
    const cb = effects.combat_bonus;
    if (!cb) continue;
    if (cb.attack_pct) mods.attackBonus += cb.attack_pct;
    if (cb.defense_pct) mods.defenseBonus += cb.defense_pct;
    if (cb.crit_rate) mods.critRateBonus += cb.crit_rate;
    if (cb.crit_damage) mods.critDamageBonus += cb.crit_damage;
    if (cb.damage_reduction) mods.damageReduction += cb.damage_reduction;
  }

  return mods;
}

// 计算称号对探索的加成
function computeExplorationModifiers(player) {
  const titles = player.titles || [];
  if (titles.length === 0) return {};

  const db = getDb();
  const mods = { luckBonus: 0, dropRateBonus: 0, staminaReduction: 0, safetyBonus: 0 };

  const placeholders = titles.map(() => '?').join(',');
  const rows = db.prepare(`SELECT effects_json FROM titles WHERE title_key IN (${placeholders})`).all(...titles);

  for (const row of rows) {
    const effects = JSON.parse(row.effects_json);
    const eb = effects.exploration_bonus;
    if (!eb) continue;
    if (eb.luck) mods.luckBonus += eb.luck;
    if (eb.drop_rate) mods.dropRateBonus += eb.drop_rate;
    if (eb.stamina_reduction) mods.staminaReduction += eb.stamina_reduction;
    if (eb.safety) mods.safetyBonus += eb.safety;
  }

  return mods;
}

// 计算称号对PK的加成
function computePKModifiers(player) {
  const titles = player.titles || [];
  if (titles.length === 0) return {};

  const db = getDb();
  const mods = { attackPct: 0, defensePct: 0, ratingBonus: 0 };

  const placeholders = titles.map(() => '?').join(',');
  const rows = db.prepare(`SELECT effects_json FROM titles WHERE title_key IN (${placeholders})`).all(...titles);

  for (const row of rows) {
    const effects = JSON.parse(row.effects_json);
    const pb = effects.pk_bonus;
    if (!pb) continue;
    if (pb.attack_pct) mods.attackPct += pb.attack_pct;
    if (pb.defense_pct) mods.defensePct += pb.defense_pct;
    if (pb.rating) mods.ratingBonus += pb.rating;
  }

  return mods;
}

// 计算称号对硬币的加成
function computeCoinMultiplier(player) {
  const titles = player.titles || [];
  if (titles.length === 0) return 1.0;

  const db = getDb();
  let multiplier = 1.0;

  const placeholders = titles.map(() => '?').join(',');
  const rows = db.prepare(`SELECT effects_json FROM titles WHERE title_key IN (${placeholders})`).all(...titles);

  for (const row of rows) {
    const effects = JSON.parse(row.effects_json);
    if (effects.coin_multiplier) {
      multiplier *= effects.coin_multiplier;
    }
  }

  return Math.round(multiplier * 100) / 100;
}

// 计算称号对 story stats 的加成 (stat_modifier)
function computeEffectiveStats(player) {
  const titles = player.titles || [];
  if (titles.length === 0) return {};

  const db = getDb();
  const mods = {};

  const placeholders = titles.map(() => '?').join(',');
  const rows = db.prepare(`SELECT effects_json FROM titles WHERE title_key IN (${placeholders})`).all(...titles);

  for (const row of rows) {
    const effects = JSON.parse(row.effects_json);
    const sm = effects.stat_modifier;
    if (!sm) continue;
    for (const [stat, val] of Object.entries(sm)) {
      mods[stat] = (mods[stat] || 0) + val;
    }
  }

  return mods;
}

// 计算称号对探索事件概率的修正
function computeEventProbabilityModifiers(player) {
  const titles = player.titles || [];
  if (titles.length === 0) return {};

  const db = getDb();
  const mods = {};

  const placeholders = titles.map(() => '?').join(',');
  const rows = db.prepare(`SELECT effects_json FROM titles WHERE title_key IN (${placeholders})`).all(...titles);

  for (const row of rows) {
    const effects = JSON.parse(row.effects_json);
    const epm = effects.event_prob_modifiers;
    if (!epm) continue;
    for (const [eventType, val] of Object.entries(epm)) {
      mods[eventType] = (mods[eventType] || 0) + val;
    }
  }

  return mods;
}

// 增加称号进度 (用于机遇事件)
function incrementTitleProgress(playerId, titleKey) {
  const db = getDb();
  const player = playerService.getRaw(playerId);
  if (!player) return;

  const titleProgress = JSON.parse(player.title_progress_json || '{}');
  titleProgress[titleKey] = (titleProgress[titleKey] || 0) + 1;
  playerService.update(playerId, { title_progress_json: titleProgress });

  // 检查是否可以立即解锁称号
  const playerFull = playerService.get(playerId);
  const unlocked = checkUnlocks(playerId);
  for (const t of unlocked) {
    if (t.title_key === titleKey) {
      playerService.addLog(playerId, `机遇触发: 获得称号「${t.name}」`);
      return t;
    }
  }
  return null;
}

// ── PvP 叙事压制系统 v1 ──
const NARRATIVE_COUNTERS = {
  reader:          { strong: ['regressor', 'demon_king'], weak: ['lonely_one'] },
  regressor:       { strong: ['king'], weak: ['reader', 'constellation_agent'] },
  king:            { strong: ['demon_king', 'king_without_throne'], weak: ['regressor', 'constellation_agent'] },
  king_without_throne: { strong: ['observer'], weak: ['lonely_one', 'king'] },
  demon_king:      { strong: ['sacrifice', 'constellation_agent'], weak: ['reader', 'salvation'] },
  constellation_agent: { strong: ['king', 'regressor'], weak: ['anti_constellation', 'demon_king'] },
  anti_constellation: { strong: ['constellation_agent'], weak: ['observer'] },
  salvation:       { strong: ['demon_king', 'sacrifice'], weak: [] },
  sacrifice:       { strong: ['salvation'], weak: ['demon_king'] },
  observer:        { strong: [], weak: ['king_without_throne'] },  // 对所有 +5% 在处理逻辑中单独处理
  lonely_one:      { strong: ['king_without_throne', 'reader'], weak: [] }
};

// 从称号推断叙事身份
function determineNarrativeIdentity(player) {
  const titles = player.titles || [];
  if (titles.length === 0) return null;

  const db = getDb();
  const tagCounts = {};

  for (const tKey of titles) {
    const titleDef = db.prepare('SELECT effects_json FROM titles WHERE title_key = ?').get(tKey);
    if (!titleDef) continue;
    const effects = JSON.parse(titleDef.effects_json);
    const tags = effects.narrative_tags || [];
    for (const tag of tags) {
      tagCounts[tag] = (tagCounts[tag] || 0) + 1;
    }
  }

  if (Object.keys(tagCounts).length === 0) return null;
  return Object.entries(tagCounts).sort((a, b) => b[1] - a[1])[0][0];
}

// 计算叙事压制修正
function computeNarrativeSuppression(attacker, defender) {
  const atkIdentity = determineNarrativeIdentity(attacker);
  const defIdentity = determineNarrativeIdentity(defender);

  let damageModifier = 0;
  const details = [];

  if (atkIdentity && defIdentity) {
    const counters = NARRATIVE_COUNTERS[atkIdentity];
    if (counters) {
      if (counters.strong.includes(defIdentity)) {
        damageModifier += 0.15;
        details.push({ type: 'narrative_strong', label: `${atkIdentity} 克制 ${defIdentity}`, bonus: 0.15 });
      }
      if (counters.weak.includes(defIdentity)) {
        damageModifier -= 0.15;
        details.push({ type: 'narrative_weak', label: `${defIdentity} 克制 ${atkIdentity}`, penalty: -0.15 });
      }
    }
    // observer 对所有非弱项 +5%
    if (atkIdentity === 'observer' && (!counters || !counters.weak.includes(defIdentity))) {
      damageModifier += 0.05;
      details.push({ type: 'observer_neutral', label: '观测者对非克星 +5%', bonus: 0.05 });
    }
  }

  // 阶段压制 (章节差距)
  const atkStageIndex = getMainChapterOrder(attacker);
  const defStageIndex = getMainChapterOrder(defender);
  if (atkStageIndex > defStageIndex) {
    const gradeBonus = Math.min(0.12, (atkStageIndex - defStageIndex) * 0.03);
    damageModifier += gradeBonus;
    details.push({ type: 'grade', label: `阶段压制 +${Math.round(gradeBonus * 100)}%`, bonus: gradeBonus });
  }

  // 频道干扰 (channelHeat 差距)
  const atkStats = attacker.stats || {};
  const defStats = defender.stats || {};
  const atkHeat = atkStats.channelHeat || 0;
  const defHeat = defStats.channelHeat || 0;
  if (atkHeat > defHeat) {
    const heatBonus = Math.min(0.10, (atkHeat - defHeat) * 0.002);
    damageModifier += heatBonus;
    details.push({ type: 'channel_interference', label: `频道干扰 +${Math.round(heatBonus * 100)}%`, bonus: heatBonus });
  }

  // Clamp to [-0.25, 0.30]
  damageModifier = Math.max(-0.25, Math.min(0.30, damageModifier));

  return { modifier: damageModifier, details, atkIdentity, defIdentity };
}

function getMainChapterOrder(player) {
  const db = getDb();
  const chapterKey = player.current_main_chapter || 'main_ch01_paid_service';
  const ch = db.prepare('SELECT order_index FROM main_chapters WHERE chapter_key = ?').get(chapterKey);
  return ch ? ch.order_index : 1;
}

module.exports = {
  checkUnlocks, getUnlocked,
  computeCombatModifiers, computeExplorationModifiers, computePKModifiers,
  computeCoinMultiplier, computeEffectiveStats,
  computeEventProbabilityModifiers, incrementTitleProgress,
  determineNarrativeIdentity, computeNarrativeSuppression
};
