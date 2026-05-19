// 星座阵营系统 (Phase 3)
// 8 个星座阵营，玩家选择星座后自动加入对应阵营
// 共享贡献分数、阵营等级、周常阵营战
const { getDb } = require('../db/database');

const CONSTELLATIONS = {
  golden_sun: '金乌神教',
  black_flame_dragon: '黑焰龙渊',
  demon_judge_of_fire: '火之审判庭',
  abyss_eye: '深渊凝视者',
  wheel_of_fate: '命运编织会',
  queen_of_underworld: '冥界女王府',
  maritime_war_god: '海上战神盟',
  star_stream_watcher: '星流守望塔'
};

function getAllFactions() {
  var db = getDb();
  var factions = db.prepare('SELECT * FROM constellation_factions ORDER BY total_contribution_score DESC').all();
  return factions.map(function(f) {
    var memberCount = db.prepare(
      "SELECT COUNT(*) as c FROM players WHERE stats_json LIKE ?"
    ).get('%' + f.constellation_key + '%').c;
    return {
      constellationKey: f.constellation_key,
      factionName: f.faction_name,
      totalContributionScore: f.total_contribution_score,
      activeMembers: memberCount,
      factionLevel: f.faction_level,
      factionSkills: JSON.parse(f.faction_skills_json || '[]')
    };
  });
}

function getFactionDetail(constellationKey) {
  var db = getDb();
  var f = db.prepare('SELECT * FROM constellation_factions WHERE constellation_key = ?').get(constellationKey);
  if (!f) return null;

  var memberCount = db.prepare(
    "SELECT COUNT(*) as c FROM players WHERE stats_json LIKE ?"
  ).get('%' + constellationKey + '%').c;

  var skills = JSON.parse(f.faction_skills_json || '[]');

  return {
    constellationKey: f.constellation_key,
    factionName: f.faction_name,
    totalContributionScore: f.total_contribution_score,
    activeMembers: memberCount,
    factionLevel: f.faction_level,
    factionSkills: skills
  };
}

function getFactionMembers(constellationKey, limit) {
  var db = getDb();
  var players = db.prepare(
    "SELECT id, player_name, stats_json FROM players WHERE stats_json LIKE ? LIMIT ?"
  ).all('%' + constellationKey + '%', limit || 50);

  return players.map(function(p) {
    var stats = JSON.parse(p.stats_json || '{}');
    return {
      playerId: p.id,
      playerName: p.player_name,
      level: stats.level || 1
    };
  });
}

function getFactionLeaderboard() {
  return getAllFactions();
}

function getPlayerFaction(playerId) {
  var db = getDb();
  var player = db.prepare('SELECT stats_json FROM players WHERE id = ?').get(playerId);
  if (!player) return null;
  var stats = JSON.parse(player.stats_json || '{}');
  var constellationKey = stats.constellation;
  if (!constellationKey) return null;
  return getFactionDetail(constellationKey);
}

function recordContribution(playerId, amount, contributionType) {
  var db = getDb();
  var player = db.prepare('SELECT stats_json FROM players WHERE id = ?').get(playerId);
  if (!player) return;
  var stats = JSON.parse(player.stats_json || '{}');
  var constellationKey = stats.constellation;
  if (!constellationKey) return;

  amount = amount || 1;

  db.prepare(
    'INSERT INTO faction_contributions (player_id, constellation_key, contribution_type, amount) VALUES (?, ?, ?, ?)'
  ).run(playerId, constellationKey, contributionType || 'general', amount);

  db.prepare(
    'UPDATE constellation_factions SET total_contribution_score = total_contribution_score + ?, updated_at = datetime(\'now\',\'localtime\') WHERE constellation_key = ?'
  ).run(amount, constellationKey);

  checkFactionLevelUp(constellationKey);
}

function checkFactionLevelUp(constellationKey) {
  var db = getDb();
  var f = db.prepare('SELECT * FROM constellation_factions WHERE constellation_key = ?').get(constellationKey);
  if (!f) return;

  var score = f.total_contribution_score;
  var newLevel = f.faction_level;

  if (score >= 10000 && f.faction_level < 5) newLevel = 5;
  else if (score >= 5000 && f.faction_level < 4) newLevel = 4;
  else if (score >= 2000 && f.faction_level < 3) newLevel = 3;
  else if (score >= 500 && f.faction_level < 2) newLevel = 2;
  else return;

  if (newLevel !== f.faction_level) {
    var skills = JSON.parse(f.faction_skills_json || '[]');
    skills.push('faction_skill_lv' + newLevel);
    db.prepare(
      'UPDATE constellation_factions SET faction_level = ?, faction_skills_json = ?, updated_at = datetime(\'now\',\'localtime\') WHERE constellation_key = ?'
    ).run(newLevel, JSON.stringify(skills), constellationKey);
  }
}

function getCurrentWeeklyWar() {
  var db = getDb();
  var now = new Date();
  var weekStart = getWeekStart(now);
  var weekEnd = getWeekEnd(now);

  var war = db.prepare(
    'SELECT * FROM faction_wars WHERE week_start = ? AND week_end = ?'
  ).get(weekStart, weekEnd);

  if (!war) {
    db.prepare(
      'INSERT INTO faction_wars (week_start, week_end) VALUES (?, ?)'
    ).run(weekStart, weekEnd);
    war = db.prepare(
      'SELECT * FROM faction_wars WHERE week_start = ? AND week_end = ?'
    ).get(weekStart, weekEnd);
  }

  var scores = JSON.parse(war.final_scores_json || '{}');
  var factions = db.prepare('SELECT constellation_key, faction_name, total_contribution_score FROM constellation_factions ORDER BY total_contribution_score DESC').all();

  return {
    id: war.id,
    weekStart: war.week_start,
    weekEnd: war.week_end,
    status: war.status,
    scores: scores,
    factions: factions.map(function(f) {
      return {
        constellationKey: f.constellation_key,
        factionName: f.faction_name,
        contributionScore: f.total_contribution_score
      };
    }),
    winnerConstellation: war.winner_constellation
  };
}

function resolveWeeklyWar() {
  var db = getDb();
  var now = new Date();
  var weekStart = getWeekStart(now);
  var weekEnd = getWeekEnd(now);

  var war = db.prepare(
    'SELECT * FROM faction_wars WHERE week_start = ? AND week_end = ?'
  ).get(weekStart, weekEnd);
  if (!war) return null;
  if (war.status !== 'active') return { alreadyResolved: true, winner: war.winner_constellation };

  var factions = db.prepare(
    'SELECT constellation_key, faction_name, total_contribution_score FROM constellation_factions ORDER BY total_contribution_score DESC'
  ).all();

  var scores = {};
  factions.forEach(function(f) {
    scores[f.constellation_key] = f.total_contribution_score;
  });

  var winner = factions.length > 0 ? factions[0] : null;

  db.prepare(
    'UPDATE faction_wars SET status = \'resolved\', final_scores_json = ?, winner_constellation = ?, updated_at = datetime(\'now\',\'localtime\') WHERE id = ?'
  ).run(JSON.stringify(scores), winner ? winner.constellation_key : null, war.id);

  if (winner) {
    // 记录到 world_state
    db.prepare("UPDATE world_state SET dominant_constellation = ?, updated_at = datetime('now','localtime') WHERE id = 1")
      .run(winner.constellation_key);

    // 衰减所有阵营贡献 (85%)
    for (var i = 0; i < factions.length; i++) {
      var newScore = Math.floor(factions[i].total_contribution_score * 0.85);
      db.prepare("UPDATE constellation_factions SET total_contribution_score = ?, updated_at = datetime('now','localtime') WHERE constellation_key = ?")
        .run(newScore, factions[i].constellation_key);
    }

    // 胜者奖励：所有成员 constellationFavor +2
    var members = db.prepare(
      "SELECT player_id FROM faction_contributions WHERE constellation_key = ? GROUP BY player_id"
    ).all(winner.constellation_key);
    var playerService = require('./playerService');
    var chapterService = require('./chapterService');
    for (var i = 0; i < members.length; i++) {
      try {
        chapterService.awardResource(members[i].player_id, 'constellationFavor', 2);
      } catch(e) {}
    }
  }

  return {
    weekStart: weekStart,
    weekEnd: weekEnd,
    finalScores: scores,
    winnerConstellation: winner ? winner.constellation_key : null,
    winnerFactionName: winner ? winner.faction_name : null
  };
}

function getDomainModifiers(playerId) {
  var db = getDb();
  var ws = db.prepare('SELECT dominant_constellation FROM world_state WHERE id = 1').get();
  if (!ws || !ws.dominant_constellation) return {};

  var player = require('./playerService').get(playerId);
  if (!player) return {};
  var playerFaction = player.stats && player.stats.constellation;

  var mods = {
    exploreStoryProbBonus: 0,
    combatDamageBonus: 0,
    tradeFeeReduction: 0
  };

  if (playerFaction === ws.dominant_constellation) {
    mods.exploreStoryProbBonus = 0.05;
    mods.combatDamageBonus = 0.08;
    mods.tradeFeeReduction = 0.5;
  } else {
    // 非该阵营也能享受部分加成（星座恩泽波及全频道）
    mods.exploreStoryProbBonus = 0.02;
    mods.combatDamageBonus = 0.03;
  }

  return mods;
}

function getWeekStart(date) {
  var d = new Date(date);
  var day = d.getDay();
  var diff = d.getDate() - day + (day === 0 ? -6 : 1);
  d.setDate(diff);
  d.setHours(0, 0, 0, 0);
  return d.toISOString();
}

function getWeekEnd(date) {
  var d = new Date(date);
  var day = d.getDay();
  var diff = d.getDate() - day + (day === 0 ? 0 : 7);
  d.setDate(diff);
  d.setHours(23, 59, 59, 999);
  return d.toISOString();
}

function getPlayerContributions(playerId, limit) {
  var db = getDb();
  return db.prepare(
    'SELECT * FROM faction_contributions WHERE player_id = ? ORDER BY created_at DESC LIMIT ?'
  ).all(playerId, limit || 20);
}

module.exports = {
  CONSTELLATIONS,
  getAllFactions,
  getFactionDetail,
  getFactionMembers,
  getFactionLeaderboard,
  getPlayerFaction,
  recordContribution,
  getCurrentWeeklyWar,
  resolveWeeklyWar,
  getPlayerContributions,
  getDomainModifiers
};
