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

module.exports = { getPlayerSkills, getAllSkills, unlockSkill };
