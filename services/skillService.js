// 技能系统 — 解锁、查询
const { getDb } = require('../db/database');
const playerService = require('./playerService');

function getPlayerSkills(playerId) {
  const db = getDb();
  const player = playerService.get(playerId);
  if (!player) return [];

  const learned = db.prepare(`
    SELECT ps.*, s.name, s.description, s.skill_type, s.rarity, s.effects_json, s.cooldown
    FROM player_skills ps
    JOIN skills s ON ps.skill_key = s.skill_key
    WHERE ps.player_id = ?
  `).all(playerId);

  return learned.map(s => ({
    skill_key: s.skill_key,
    name: s.name,
    description: s.description,
    skill_type: s.skill_type,
    rarity: s.rarity,
    effects: JSON.parse(s.effects_json),
    cooldown: s.cooldown,
    level: s.level,
    unlocked_at: s.unlocked_at
  }));
}

function getAllSkills(playerId) {
  const db = getDb();
  const player = playerService.get(playerId);
  if (!player) return [];

  const allSkills = db.prepare('SELECT * FROM skills').all();
  const learned = db.prepare('SELECT skill_key FROM player_skills WHERE player_id = ?').all(playerId);
  const learnedKeys = new Set(learned.map(l => l.skill_key));

  return allSkills.map(s => {
    const conditions = JSON.parse(s.required_titles_json);
    const unlocked = learnedKeys.has(s.skill_key);
    const canUnlock = !unlocked && checkSkillConditions(player, s);

    return {
      skill_key: s.skill_key,
      name: s.name,
      description: s.description,
      skill_type: s.skill_type,
      rarity: s.rarity,
      effects: JSON.parse(s.effects_json),
      required_titles: conditions,
      required_fragments: s.required_fragments,
      cooldown: s.cooldown,
      unlocked,
      can_unlock: canUnlock
    };
  });
}

function checkSkillConditions(player, skill) {
  const requiredTitles = JSON.parse(skill.required_titles_json);
  if (requiredTitles.length > 0) {
    for (const t of requiredTitles) {
      if (!player.titles.includes(t)) return false;
    }
  }
  if (skill.required_fragments > 0) {
    if ((player.story_fragments || 0) < skill.required_fragments) return false;
  }
  return true;
}

function unlockSkill(playerId, skillKey) {
  const db = getDb();
  const player = playerService.get(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  const skill = db.prepare('SELECT * FROM skills WHERE skill_key = ?').get(skillKey);
  if (!skill) return { error: { code: 'SKILL_NOT_FOUND', message: '技能不存在' } };

  const existing = db.prepare('SELECT * FROM player_skills WHERE player_id = ? AND skill_key = ?').get(playerId, skillKey);
  if (existing) return { error: { code: 'ALREADY_LEARNED', message: '已学习该技能' } };

  if (!checkSkillConditions(player, skill)) {
    return { error: { code: 'CONDITIONS_NOT_MET', message: '未满足解锁条件' } };
  }

  // Consume story fragments
  if (skill.required_fragments > 0) {
    playerService.update(playerId, { story_fragments: Math.max(0, player.story_fragments - skill.required_fragments) });
  }

  db.prepare(`INSERT INTO player_skills (player_id, skill_key, level)
    VALUES (?, ?, 1)`).run(playerId, skillKey);

  playerService.addLog(playerId, `习得技能: ${skill.name}`);

  return {
    success: true,
    skill: {
      skill_key: skill.skill_key,
      name: skill.name,
      description: skill.description,
      skill_type: skill.skill_type,
      rarity: skill.rarity,
      effects: JSON.parse(skill.effects_json),
      cooldown: skill.cooldown,
      level: 1
    }
  };
}

// ===== 星座阵营技能 =====

function getFactionSkills(constellationKey) {
  var db = getDb();
  var skills = db.prepare(
    'SELECT * FROM faction_skills WHERE constellation_key = ? ORDER BY required_faction_level, skill_key'
  ).all(constellationKey);
  return skills.map(function(s) {
    return {
      skill_key: s.skill_key,
      constellation_key: s.constellation_key,
      skill_name: s.skill_name,
      description: s.description,
      skill_type: s.skill_type,
      effects: JSON.parse(s.effect_json || '{}'),
      required_faction_level: s.required_faction_level,
      cost_faction_contribution: s.cost_faction_contribution,
      cooldown: s.cooldown
    };
  });
}

function getFactionSkillsForPlayer(playerId) {
  var db = getDb();
  var player = playerService.get(playerId);
  if (!player) return [];

  var constellationKey = player.stats.constellation;
  if (!constellationKey) return [];

  // Get player's faction data
  var factionData = null;
  try {
    var factionService = require('./factionService');
    factionData = factionService.getFactionDetail(constellationKey);
  } catch (e) { /* faction not critical */ }

  var factionLevel = factionData ? factionData.factionLevel : 1;

  // Get player's contribution resources
  var resources = player.breakthrough_resources || {};
  var favor = resources.constellationFavor || 0;

  // Get all skills for this constellation
  var allSkills = db.prepare(
    'SELECT * FROM faction_skills WHERE constellation_key = ? ORDER BY required_faction_level, skill_key'
  ).all(constellationKey);

  // Get learned skills
  var learned = db.prepare(
    'SELECT skill_key FROM player_faction_skills WHERE player_id = ?'
  ).all(playerId);
  var learnedKeys = new Set(learned.map(function(l) { return l.skill_key; }));

  return allSkills.map(function(s) {
    var unlocked = learnedKeys.has(s.skill_key);
    var canUnlock = !unlocked && factionLevel >= s.required_faction_level && favor >= s.cost_faction_contribution;

    return {
      skill_key: s.skill_key,
      constellation_key: s.constellation_key,
      skill_name: s.skill_name,
      description: s.description,
      skill_type: s.skill_type,
      effects: JSON.parse(s.effect_json || '{}'),
      required_faction_level: s.required_faction_level,
      cost_faction_contribution: s.cost_faction_contribution,
      cooldown: s.cooldown,
      unlocked: unlocked,
      can_unlock: canUnlock
    };
  });
}

function learnFactionSkill(playerId, skillKey) {
  var db = getDb();
  var player = playerService.get(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  var constellationKey = player.stats.constellation;
  if (!constellationKey) return { error: { code: 'NO_CONSTELLATION', message: '尚未选择背后星' } };

  var skill = db.prepare('SELECT * FROM faction_skills WHERE skill_key = ?').get(skillKey);
  if (!skill) return { error: { code: 'SKILL_NOT_FOUND', message: '阵营技能不存在' } };

  // Check skill belongs to player's constellation
  if (skill.constellation_key !== constellationKey) {
    return { error: { code: 'WRONG_CONSTELLATION', message: '该技能不属于你的阵营' } };
  }

  // Check already learned
  var existing = db.prepare(
    'SELECT * FROM player_faction_skills WHERE player_id = ? AND skill_key = ?'
  ).get(playerId, skillKey);
  if (existing) return { error: { code: 'ALREADY_LEARNED', message: '已学习该阵营技能' } };

  // Check faction level
  var factionLevel = 1;
  try {
    var factionService = require('./factionService');
    var factionData = factionService.getFactionDetail(constellationKey);
    factionLevel = factionData ? factionData.factionLevel : 1;
  } catch (e) { /* faction not critical */ }

  if (factionLevel < skill.required_faction_level) {
    return { error: { code: 'FACTION_LEVEL_LOW', message: '阵营等级不足，需要等级 ' + skill.required_faction_level } };
  }

  // Check contribution cost
  var resources = player.breakthrough_resources || {};
  var favor = resources.constellationFavor || 0;
  if (favor < skill.cost_faction_contribution) {
    return { error: { code: 'NOT_ENOUGH_FAVOR', message: '阵营贡献不足，需要 ' + skill.cost_faction_contribution + ' 点，当前 ' + favor + ' 点' } };
  }

  // Deduct constellationFavor
  resources.constellationFavor = favor - skill.cost_faction_contribution;
  var chapterService = require('./chapterService');
  // Directly update breakthrough resources
  playerService.update(playerId, { breakthrough_resources_json: resources });

  // Add skill
  db.prepare('INSERT INTO player_faction_skills (player_id, skill_key) VALUES (?, ?)').run(playerId, skillKey);

  playerService.addLog(playerId, '习得阵营技能: ' + skill.skill_name);

  return {
    success: true,
    skill: {
      skill_key: skill.skill_key,
      skill_name: skill.skill_name,
      description: skill.description,
      skill_type: skill.skill_type,
      effects: JSON.parse(skill.effect_json || '{}'),
      cooldown: skill.cooldown
    }
  };
}

function hasFactionSkill(playerId, skillKey) {
  var db = getDb();
  var row = db.prepare(
    'SELECT * FROM player_faction_skills WHERE player_id = ? AND skill_key = ?'
  ).get(playerId, skillKey);
  return !!row;
}

module.exports = { getPlayerSkills, getAllSkills, unlockSkill, getFactionSkills, getFactionSkillsForPlayer, learnFactionSkill, hasFactionSkill };
