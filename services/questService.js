// 每日/每周任务系统
var getDb = function() { return require('../db/database').getDb(); };
var playerService = require('./playerService');

// ── Quest Template Pool ──
// reward_coins, story_fragments, constellationFavor, items[], faction_contrib
var QUEST_TEMPLATES = [
  // ── Exploration quests ──
  {
    quest_key: 'explore_any_3', type: 'daily',
    name: '星流漫游者', description: '在任意地点探索3次',
    event: 'explore', target: 3, weight: 10,
    rewards: { coins: 80, story_fragments: 1 }
  },
  {
    quest_key: 'explore_any_5', type: 'daily',
    name: '勤勉的观测者', description: '在任意地点探索5次',
    event: 'explore', target: 5, weight: 8,
    rewards: { coins: 120, story_fragments: 2 }
  },
  {
    quest_key: 'explore_any_10', type: 'weekly',
    name: '不知疲倦的旅人', description: '在任意地点探索10次',
    event: 'explore', target: 10, weight: 10,
    rewards: { coins: 500, story_fragments: 5, constellationFavor: 1 }
  },
  {
    quest_key: 'explore_ruins', type: 'daily',
    name: '废墟探索者', description: '在废墟车站或废都楼阁探索2次',
    event: 'explore_location', target: 2, weight: 6,
    params: { locations: ['ruin_station', 'ruin_tower'] },
    rewards: { coins: 100, story_fragments: 1, constellationFavor: 1 }
  },
  {
    quest_key: 'explore_dungeon', type: 'daily',
    name: '深渊探险家', description: '在深渊相关地点探索1次',
    event: 'explore_location', target: 1, weight: 5,
    params: { locations: ['abyss_dungeon', 'abyss_entry'] },
    rewards: { coins: 150, story_fragments: 2 }
  },
  {
    quest_key: 'explore_any_8', type: 'weekly',
    name: '行遍星流', description: '在任意地点探索8次',
    event: 'explore', target: 8, weight: 10,
    rewards: { coins: 400, story_fragments: 3, constellationFavor: 1 }
  },

  // ── Combat quests ──
  {
    quest_key: 'slay_monster_3', type: 'daily',
    name: '猎魔新兵', description: '击败任意3只怪物',
    event: 'defeat_monster', target: 3, weight: 10,
    rewards: { coins: 100, story_fragments: 1 }
  },
  {
    quest_key: 'slay_monster_5', type: 'daily',
    name: '身经百战', description: '击败任意5只怪物',
    event: 'defeat_monster', target: 5, weight: 7,
    rewards: { coins: 150, story_fragments: 2 }
  },
  {
    quest_key: 'slay_boss', type: 'daily',
    name: '强敌狩猎', description: '击败1只首领怪物',
    event: 'defeat_boss', target: 1, weight: 8,
    rewards: { coins: 200, story_fragments: 3, constellationFavor: 2 }
  },
  {
    quest_key: 'slay_elite', type: 'daily',
    name: '精英歼灭者', description: '击败2只精英怪物',
    event: 'defeat_elite', target: 2, weight: 6,
    rewards: { coins: 150, story_fragments: 2 }
  },
  {
    quest_key: 'slay_monster_15', type: 'weekly',
    name: '屠戮者', description: '击败任意15只怪物',
    event: 'defeat_monster', target: 15, weight: 10,
    rewards: { coins: 600, story_fragments: 5, items: ['medium_hp_potion'] }
  },
  {
    quest_key: 'slay_boss_3', type: 'weekly',
    name: 'Boss猎人', description: '击败3只首领怪物',
    event: 'defeat_boss', target: 3, weight: 8,
    rewards: { coins: 800, story_fragments: 8, constellationFavor: 3 }
  },

  // ── PK quests ──
  {
    quest_key: 'win_pk_1', type: 'daily',
    name: '初战告捷', description: '在PK中获胜1次',
    event: 'win_pk', target: 1, weight: 7,
    rewards: { coins: 120, constellationFavor: 1 }
  },
  {
    quest_key: 'win_pk_2', type: 'weekly',
    name: '常胜将军', description: '在PK中获胜2次',
    event: 'win_pk', target: 2, weight: 8,
    rewards: { coins: 500, story_fragments: 3, constellationFavor: 2 }
  },
  {
    quest_key: 'participate_pk_3', type: 'daily',
    name: '角斗士', description: '参与3次PK（胜败均可）',
    event: 'pk_encounter', target: 3, weight: 6,
    rewards: { coins: 100, story_fragments: 1 }
  },

  // ── Chat quests ──
  {
    quest_key: 'chat_3', type: 'daily',
    name: '闲聊一刻', description: '在聊天频道发言3次',
    event: 'chat', target: 3, weight: 9,
    rewards: { coins: 60, story_fragments: 1 }
  },
  {
    quest_key: 'chat_5', type: 'daily',
    name: '活跃观测者', description: '在聊天频道发言5次',
    event: 'chat', target: 5, weight: 8,
    rewards: { coins: 100, story_fragments: 1 }
  },
  {
    quest_key: 'chat_20', type: 'weekly',
    name: '社交达人', description: '在聊天频道发言20次',
    event: 'chat', target: 20, weight: 8,
    rewards: { coins: 350, story_fragments: 2 }
  },

  // ── Item usage quests ──
  {
    quest_key: 'use_item_3', type: 'daily',
    name: '道具爱好者', description: '使用任意道具3次',
    event: 'use_item', target: 3, weight: 7,
    rewards: { coins: 80, story_fragments: 1 }
  },
  {
    quest_key: 'use_item_5', type: 'weekly',
    name: '道具专家', description: '使用任意道具5次',
    event: 'use_item', target: 5, weight: 6,
    rewards: { coins: 300, story_fragments: 2, items: ['small_hp_potion'] }
  },

  // ── Trade quests ──
  {
    quest_key: 'trade_1', type: 'daily',
    name: '交易初学者', description: '完成1次交易（买卖均可）',
    event: 'trade', target: 1, weight: 8,
    rewards: { coins: 100, story_fragments: 1 }
  },
  {
    quest_key: 'trade_3', type: 'weekly',
    name: '市场常客', description: '完成3次交易',
    event: 'trade', target: 3, weight: 7,
    rewards: { coins: 400, story_fragments: 2 }
  },

  // ── Party quests ──
  {
    quest_key: 'join_party_1', type: 'daily',
    name: '结伴同行', description: '加入1次组队Boss战',
    event: 'join_party', target: 1, weight: 7,
    rewards: { coins: 120, story_fragments: 2, constellationFavor: 1 }
  },
  {
    quest_key: 'party_boss_2', type: 'weekly',
    name: '并肩作战', description: '参与2次组队Boss讨伐',
    event: 'party_boss', target: 2, weight: 7,
    rewards: { coins: 500, story_fragments: 5, constellationFavor: 3 }
  },

  // ── Rest quests ──
  {
    quest_key: 'rest_2', type: 'daily',
    name: '休养生息', description: '休息2次',
    event: 'rest', target: 2, weight: 5,
    rewards: { coins: 60, story_fragments: 1 }
  },

  // ── Story quests ──
  {
    quest_key: 'trigger_story_2', type: 'daily',
    name: '故事追寻者', description: '触发2次剧情事件',
    event: 'story_event', target: 2, weight: 8,
    rewards: { coins: 100, story_fragments: 2, constellationFavor: 1 }
  },
  {
    quest_key: 'trigger_story_5', type: 'weekly',
    name: '星流记录者', description: '触发5次剧情事件',
    event: 'story_event', target: 5, weight: 9,
    rewards: { coins: 450, story_fragments: 5, constellationFavor: 2 }
  }
];

// ── Helper: Get today's date string ──
function todayStr() {
  var d = new Date();
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

// ── Helper: Get week start (Monday) ──
function weekStartStr() {
  var d = new Date();
  var day = d.getDay();
  var monday = new Date(d);
  monday.setDate(d.getDate() - ((day + 6) % 7));
  return monday.getFullYear() + '-' + String(monday.getMonth() + 1).padStart(2, '0') + '-' + String(monday.getDate()).padStart(2, '0');
}

// ── Helper: Weighted random selection ──
function weightedPick(templates, n) {
  var picked = [];
  var available = templates.slice();
  var totalWeight = available.reduce(function(sum, t) { return sum + t.weight; }, 0);

  for (var i = 0; i < n && available.length > 0; i++) {
    var r = Math.random() * totalWeight;
    var cumulative = 0;
    var selected = null;
    var selectedIdx = -1;
    for (var j = 0; j < available.length; j++) {
      cumulative += available[j].weight;
      if (r < cumulative) {
        selected = available[j];
        selectedIdx = j;
        break;
      }
    }
    if (!selected) {
      selected = available[available.length - 1];
      selectedIdx = available.length - 1;
    }
    picked.push(selected);
    totalWeight -= selected.weight;
    available.splice(selectedIdx, 1);
  }
  return picked;
}

// ── Get or generate daily quests for a player ──
function getDailyQuests(playerId) {
  var db = getDb();
  var today = todayStr();

  var existing = db.prepare(
    'SELECT * FROM daily_quests WHERE player_id = ? AND date_assigned = ?'
  ).all(playerId, today);

  if (existing.length > 0) {
    return existing.map(formatQuest);
  }

  // Generate new daily quests
  var dailyTemplates = QUEST_TEMPLATES.filter(function(t) { return t.type === 'daily'; });
  var count = 3 + Math.floor(Math.random() * 3); // 3-5 quests
  var picked = weightedPick(dailyTemplates, count);

  // Check if weekly quests exist too, get existing quest_keys to avoid duplicates
  var weekStart = weekStartStr();
  var existingWeekly = db.prepare(
    'SELECT quest_key FROM weekly_quests WHERE player_id = ? AND date_assigned = ?'
  ).all(playerId, weekStart);

  var weeklyKeys = {};
  for (var wi = 0; wi < existingWeekly.length; wi++) {
    weeklyKeys[existingWeekly[wi].quest_key] = true;
  }

  var inserted = [];
  for (var i = 0; i < picked.length; i++) {
    var t = picked[i];
    // Avoid exact duplicate quest_key with weekly for same player
    if (weeklyKeys[t.quest_key]) continue;
    var rewardsJson = JSON.stringify(t.rewards);
    var paramsJson = JSON.stringify(t.params || null);
    db.prepare(
      'INSERT INTO daily_quests (player_id, quest_key, quest_type, quest_name, description, event_type, progress, target, claimed, date_assigned, rewards_json, params_json) VALUES (?, ?, ?, ?, ?, ?, 0, ?, 0, ?, ?, ?)'
    ).run(playerId, t.quest_key, 'daily', t.name, t.description, t.event, t.target, today, rewardsJson, paramsJson);
  }

  var rows = db.prepare(
    'SELECT * FROM daily_quests WHERE player_id = ? AND date_assigned = ?'
  ).all(playerId, today);

  return rows.map(formatQuest);
}

// ── Get or generate weekly quests for a player ──
function getWeeklyQuests(playerId) {
  var db = getDb();
  var weekStart = weekStartStr();

  var existing = db.prepare(
    'SELECT * FROM weekly_quests WHERE player_id = ? AND date_assigned = ?'
  ).all(playerId, weekStart);

  if (existing.length > 0) {
    return existing.map(formatQuest);
  }

  // Generate new weekly quests
  var weeklyTemplates = QUEST_TEMPLATES.filter(function(t) { return t.type === 'weekly'; });
  var count = 2 + Math.floor(Math.random() * 2); // 2-3 quests
  var picked = weightedPick(weeklyTemplates, count);

  var inserted = [];
  for (var i = 0; i < picked.length; i++) {
    var t = picked[i];
    var rewardsJson = JSON.stringify(t.rewards);
    var paramsJson = JSON.stringify(t.params || null);
    db.prepare(
      'INSERT INTO weekly_quests (player_id, quest_key, quest_type, quest_name, description, event_type, progress, target, claimed, date_assigned, rewards_json, params_json) VALUES (?, ?, ?, ?, ?, ?, 0, ?, 0, ?, ?, ?)'
    ).run(playerId, t.quest_key, 'weekly', t.name, t.description, t.event, t.target, weekStart, rewardsJson, paramsJson);
  }

  var rows = db.prepare(
    'SELECT * FROM weekly_quests WHERE player_id = ? AND date_assigned = ?'
  ).all(playerId, weekStart);

  return rows.map(formatQuest);
}

// ── Format a quest row to object ──
function formatQuest(row) {
  var rewards = {};
  try { rewards = JSON.parse(row.rewards_json || '{}'); } catch (e) { /* ignore */ }
  var params = null;
  try { params = JSON.parse(row.params_json || 'null'); } catch (e) { /* ignore */ }

  return {
    id: row.id,
    quest_key: row.quest_key,
    quest_type: row.quest_type,
    quest_name: row.quest_name,
    description: row.description,
    event_type: row.event_type,
    progress: row.progress || 0,
    target: row.target || 0,
    claimed: row.claimed === 1,
    date_assigned: row.date_assigned,
    rewards: rewards,
    params: params
  };
}

// ── Check and update quest progress when events happen ──
// eventType: 'explore', 'explore_location', 'story_event', 'defeat_monster', 'defeat_boss',
//            'defeat_elite', 'win_pk', 'pk_encounter', 'chat', 'trade', 'use_item',
//            'join_party', 'party_boss', 'rest'
// data: additional context { location_key, is_boss, monster_key, etc. }
function checkProgress(playerId, eventType, data) {
  data = data || {};
  var db = getDb();

  // Clean up expired daily/weekly quests first
  cleanupQuests(playerId);

  var today = todayStr();
  var weekStart = weekStartStr();

  // Check daily quests
  var dailyQuests = db.prepare(
    'SELECT * FROM daily_quests WHERE player_id = ? AND date_assigned = ? AND claimed = 0'
  ).all(playerId, today);

  for (var i = 0; i < dailyQuests.length; i++) {
    var q = dailyQuests[i];
    if (matchesEvent(q, eventType, data)) {
      var newProg = Math.min(q.target, q.progress + 1);
      db.prepare('UPDATE daily_quests SET progress = ? WHERE id = ?').run(newProg, q.id);
    }
  }

  // Check weekly quests
  var weeklyQuests = db.prepare(
    'SELECT * FROM weekly_quests WHERE player_id = ? AND date_assigned = ? AND claimed = 0'
  ).all(playerId, weekStart);

  for (var j = 0; j < weeklyQuests.length; j++) {
    var wq = weeklyQuests[j];
    if (matchesEvent(wq, eventType, data)) {
      var wnewProg = Math.min(wq.target, wq.progress + 1);
      db.prepare('UPDATE weekly_quests SET progress = ? WHERE id = ?').run(wnewProg, wq.id);
    }
  }
}

// ── Check if an event matches a quest's criteria ──
function matchesEvent(quest, eventType, data) {
  if (quest.event_type !== eventType) {
    // "explore_location" quests also count as generic "explore" events
    if (!(quest.event_type === 'explore_location' && eventType === 'explore')) {
      return false;
    }
  }

  // Check params/location constraints
  var params = null;
  try { params = JSON.parse(quest.params_json || 'null'); } catch (e) { /* ignore */ }

  if (params && params.locations && params.locations.length > 0) {
    var loc = data.location_key || '';
    if (params.locations.indexOf(loc) === -1) return false;
  }

  if (eventType === 'defeat_boss' && quest.event_type === 'defeat_boss') {
    if (data && data.is_boss !== true) return false;
  }

  if (eventType === 'defeat_elite' && quest.event_type === 'defeat_elite') {
    if (data && !data.is_elite) return false;
  }

  return true;
}

// ── Clean up expired quests ──
function cleanupQuests(playerId) {
  var db = getDb();
  var today = todayStr();
  var weekStart = weekStartStr();

  // Delete daily quests not from today
  db.prepare('DELETE FROM daily_quests WHERE player_id = ? AND date_assigned != ?').run(playerId, today);

  // Delete weekly quests not from this week
  db.prepare('DELETE FROM weekly_quests WHERE player_id = ? AND date_assigned != ?').run(playerId, weekStart);
}

// ── Claim quest reward ──
function claimQuestReward(playerId, questId) {
  var db = getDb();
  var quest = findQuestById(db, questId);
  if (!quest) return { error: { code: 'QUEST_NOT_FOUND', message: '任务不存在' } };
  if (quest.player_id !== playerId) return { error: { code: 'NOT_YOUR_QUEST', message: '这不是你的任务' } };
  if (quest.claimed === 1) return { error: { code: 'ALREADY_CLAIMED', message: '奖励已领取' } };
  if (quest.progress < quest.target) return { error: { code: 'NOT_COMPLETE', message: '任务未完成' } };

  var rewards = {};
  try { rewards = JSON.parse(quest.rewards_json || '{}'); } catch (e) { /* ignore */ }

  var player = playerService.getRaw(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  // Apply rewards
  var earned = {};
  if (rewards.coins && rewards.coins > 0) {
    playerService.update(playerId, { coins: (player.coins || 0) + rewards.coins });
    earned.coins = rewards.coins;
  }
  if (rewards.story_fragments && rewards.story_fragments > 0) {
    var chapterService = require('./chapterService');
    chapterService.awardResource(playerId, 'storyFragments', rewards.story_fragments);
    earned.story_fragments = rewards.story_fragments;
  }
  if (rewards.constellationFavor && rewards.constellationFavor > 0) {
    var chapService = require('./chapterService');
    chapService.awardResource(playerId, 'constellationFavor', rewards.constellationFavor);
    earned.constellationFavor = rewards.constellationFavor;
  }
  if (rewards.items && rewards.items.length > 0) {
    var invService = require('./inventoryService');
    for (var i = 0; i < rewards.items.length; i++) {
      invService.addItem(playerId, rewards.items[i], 1);
    }
    earned.items = rewards.items;
  }
  if (rewards.faction_contrib && rewards.faction_contrib > 0) {
    try {
      var factionService = require('./factionService');
      factionService.recordContribution(playerId, rewards.faction_contrib, 'quest_reward');
      earned.faction_contrib = rewards.faction_contrib;
    } catch (e) { /* faction not critical */ }
  }

  // Mark as claimed
  var tableName = quest.quest_type === 'daily' ? 'daily_quests' : 'weekly_quests';
  db.prepare('UPDATE ' + tableName + ' SET claimed = 1 WHERE id = ?').run(questId);

  playerService.addLog(playerId, '完成任务「' + quest.quest_name + '」，获得奖励。');

  return { success: true, data: { quest_id: questId, quest_name: quest.quest_name, earned: earned } };
}

// ── Find a quest across both tables ──
function findQuestById(db, questId) {
  var q = db.prepare('SELECT * FROM daily_quests WHERE id = ?').get(questId);
  if (q) return q;
  return db.prepare('SELECT * FROM weekly_quests WHERE id = ?').get(questId);
}

// ── Get all active quests for a player ──
function getAllQuests(playerId) {
  cleanupQuests(playerId);
  var daily = getDailyQuests(playerId);
  var weekly = getWeeklyQuests(playerId);
  return { daily: daily, weekly: weekly };
}

module.exports = {
  getDailyQuests,
  getWeeklyQuests,
  getAllQuests,
  checkProgress,
  claimQuestReward,
  cleanupQuests
};
