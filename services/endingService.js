const { getDb } = require('../db/database');
const playerService = require('./playerService');

function checkEndings(playerId) {
  const db = getDb();
  const player = playerService.getRaw(playerId);
  if (!player) return [];

  const allEndings = db.prepare('SELECT * FROM endings ORDER BY priority DESC').all();
  const playerTitles = JSON.parse(player.titles_json);
  const storyFlags = JSON.parse(player.story_flags_json);
  const permFlags = JSON.parse(player.permanent_flags_json);
  const stats = JSON.parse(player.stats_json);
  const relationships = JSON.parse(player.relationships_json);
  const sponsors = JSON.parse(player.sponsors_json);

  const matched = [];

  // 收集所有称号的 block_endings
  const blockedEndings = new Set();
  for (const tKey of playerTitles) {
    const titleDef = db.prepare('SELECT effects_json FROM titles WHERE title_key = ?').get(tKey);
    if (titleDef) {
      const effects = JSON.parse(titleDef.effects_json);
      if (effects.block_endings) {
        for (const ek of effects.block_endings) blockedEndings.add(ek);
      }
    }
  }

  for (const ending of allEndings) {
    // Check title block_endings
    if (blockedEndings.has(ending.ending_key)) continue;

    const conditions = JSON.parse(ending.conditions_json);
    if (evaluateEndingConditions(conditions, playerTitles, storyFlags, permFlags, stats, relationships, sponsors)) {
      matched.push({
        ending_key: ending.ending_key,
        name: ending.name,
        description: ending.description,
        priority: ending.priority,
        is_hidden: !!ending.is_hidden
      });
    }
  }

  return matched;
}

function getEnding(playerId) {
  return checkEndings(playerId);
}

function evaluateEndingConditions(conditions, playerTitles, storyFlags, permFlags, stats, relationships, sponsors) {
  // Check flags
  if (conditions.flags) {
    for (const [flag, val] of Object.entries(conditions.flags)) {
      if ((storyFlags[flag] || permFlags[flag]) !== val) return false;
    }
  }

  // Check permanent_flags with thresholds
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

  // Check stats
  if (conditions.stats) {
    for (const [stat, threshold] of Object.entries(conditions.stats)) {
      const val = stats[stat] || 0;
      if (threshold.min !== undefined && val < threshold.min) return false;
      if (threshold.max !== undefined && val > threshold.max) return false;
    }
  }

  // Check required titles
  if (conditions.titles) {
    for (const t of conditions.titles) {
      if (!playerTitles.includes(t)) return false;
    }
  }

  // Check sponsors count (supports both plain number and {min: N} format)
  if (conditions.sponsors_count !== undefined) {
    const min = typeof conditions.sponsors_count === 'object' ? conditions.sponsors_count.min : conditions.sponsors_count;
    if ((sponsors || []).length < min) return false;
  }

  return true;
}

module.exports = { checkEndings, getEnding };
