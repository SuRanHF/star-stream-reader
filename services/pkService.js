// 世界 PK 系统 — 玩家对战
const { getDb } = require('../db/database');
const playerService = require('./playerService');
const combatService = require('./combatService');
const titleService = require('./titleService');
const chapterService = require('./chapterService');

// 获取可挑战的对手列表
function getOpponents(playerId) {
  var db = getDb();
  var onlineIds = playerService.getOnlinePlayers();
  if (onlineIds.length === 0) return [];
  var placeholders = onlineIds.map(function() { return '?'; }).join(',');
  var query = 'SELECT p.id, p.player_name, p.stats_json, p.titles_json, ' +
    'COALESCE(r.rating, 1000) as rating, COALESCE(r.wins, 0) as wins, COALESCE(r.losses, 0) as losses ' +
    'FROM players p LEFT JOIN rankings r ON p.id = r.player_id ' +
    'WHERE p.id != ? AND p.id IN (' + placeholders + ') ' +
    'ORDER BY r.rating DESC LIMIT 20';
  var params = [playerId].concat(onlineIds);
  var stmt = db.prepare(query);
  var players = stmt.all.apply(stmt, params);

  return players.map(function(p) {
    var stats = JSON.parse(p.stats_json);
    return {
      id: p.id,
      player_name: p.player_name,
      level: stats.level || 1,
      rating: p.rating,
      wins: p.wins,
      losses: p.losses,
      combat_power: estimateCombatPower(p)
    };
  });
}

// Create a pending PK challenge (attacker clicks "挑战", defender sees popup)
function createChallenge(attackerId, defenderId) {
  if (attackerId === defenderId) return { error: { code: 'SELF_CHALLENGE', message: '不能挑战自己' } };
  var db = getDb();
  var existing = db.prepare(
    "SELECT id FROM pk_challenges WHERE attacker_id=? AND defender_id=? AND status='pending'"
  ).get(attackerId, defenderId);
  if (existing) return { error: { code: 'DUPLICATE_CHALLENGE', message: '已有待处理的挑战' } };
  db.prepare(
    "INSERT INTO pk_challenges (attacker_id, defender_id, status) VALUES (?, ?, 'pending')"
  ).run(attackerId, defenderId);
  return { success: true, data: { message: '挑战已发出，等待对方回应' } };
}

function getPendingChallenges(playerId) {
  var db = getDb();
  var rows = db.prepare(
    "SELECT c.id, c.attacker_id, c.defender_id, c.created_at, p.player_name " +
    "FROM pk_challenges c JOIN players p ON c.attacker_id = p.id " +
    "WHERE c.defender_id = ? AND c.status = 'pending' ORDER BY c.created_at DESC"
  ).all(playerId);
  return rows.map(function(r) {
    return { id: r.id, attacker_id: r.attacker_id, attacker_name: r.player_name, created_at: r.created_at };
  });
}

function resolveChallenge(challengeId, accept, playerId) {
  var db = getDb();
  var ch = db.prepare("SELECT * FROM pk_challenges WHERE id = ? AND status = 'pending'").get(challengeId);
  if (!ch) return { error: { code: 'CHALLENGE_NOT_FOUND', message: '挑战不存在或已过期' } };
  if (ch.defender_id !== playerId) return { error: { code: 'NOT_YOUR_CHALLENGE', message: '这不是发给你的挑战' } };
  if (!accept) {
    db.prepare("UPDATE pk_challenges SET status='rejected', resolved_at=datetime('now','localtime') WHERE id=?").run(challengeId);
    return { success: true, data: { accepted: false, message: '已拒绝挑战' } };
  }
  db.prepare("UPDATE pk_challenges SET status='accepted', resolved_at=datetime('now','localtime') WHERE id=?").run(challengeId);
  var result = challenge(ch.attacker_id, ch.defender_id);
  return { success: true, data: { accepted: true, battle: result } };
}

// Expire old challenges (>5 min)
function expireOldChallenges() {
  var db = getDb();
  db.prepare("UPDATE pk_challenges SET status='expired', resolved_at=datetime('now','localtime') WHERE status='pending' AND datetime(created_at, '+5 minutes') < datetime('now','localtime')").run();
}

function estimateCombatPower(playerRow) {
  const stats = JSON.parse(playerRow.stats_json);
  return Math.round(
    (stats.attack || 10) * 5 +
    (stats.defense || 5) * 3 +
    (stats.hp || 100) * 0.5 +
    (stats.speed || 10) * 2 +
    (stats.level || 1) * 10
  );
}

// 发起 PK 挑战
function challenge(attackerId, defenderId) {
  const db = getDb();
  if (attackerId === defenderId) return { error: { code: 'SELF_CHALLENGE', message: '不能挑战自己' } };

  const attacker = playerService.get(attackerId);
  const defender = playerService.get(defenderId);
  if (!attacker || !defender) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  // 使用真实战斗计算 (含装备、称号加成)
  const atkPower = combatService.calculateCombatPower(attacker);
  const defPower = combatService.calculateCombatPower(defender);

  // 称号PK加成
  const atkPKMods = titleService.computePKModifiers(attacker);
  const defPKMods = titleService.computePKModifiers(defender);
  // 星流放送PK修正
  let atkBroadcastMods = {};
  let defBroadcastMods = {};
  try {
    const broadcastService = require('./broadcastService');
    atkBroadcastMods = broadcastService.getActiveModifiers(attackerId);
    defBroadcastMods = broadcastService.getActiveModifiers(defenderId);
  } catch (e) { /* broadcast not critical */ }

  // 叙事压制
  const narrativeSupp = titleService.computeNarrativeSuppression(attacker, defender);

  const atkAtk = atkPower.atk * (1 + (atkPKMods.attackPct || 0) + (atkBroadcastMods.pkDamageBonus || 0) + (narrativeSupp.modifier || 0));
  const atkDef = atkPower.def * (1 + (atkPKMods.defensePct || 0));
  const defAtk = defPower.atk * (1 + (defPKMods.attackPct || 0) + (defBroadcastMods.pkDamageBonus || 0));
  const defDef = defPower.def * (1 + (defPKMods.defensePct || 0));

  // 模拟战斗 (使用真实战斗公式)
  const rounds = [];
  let atkHp = atkPower.hp;
  let defHp = defPower.hp;
  const attackerFirst = atkPower.spd >= defPower.spd;
  const MAX_ROUNDS = 30;
  let result = 'draw';

  for (let i = 0; i < MAX_ROUNDS; i++) {
    if (attackerFirst) {
      const aDmg = calcPKDamage(atkAtk, defDef, atkPower.critRate, atkPower.critDamage);
      defHp -= aDmg.damage;
      rounds.push({ round: i + 1, attacker_dmg: aDmg.damage, defender_dmg: 0, attacker_crit: aDmg.crit });
      if (defHp <= 0) { result = 'attacker'; break; }

      const dDmg = calcPKDamage(defAtk, atkDef, defPower.critRate, defPower.critDamage);
      atkHp -= dDmg.damage;
      rounds[rounds.length - 1].defender_dmg = dDmg.damage;
      if (atkHp <= 0) { result = 'defender'; break; }
    } else {
      const dDmg = calcPKDamage(defAtk, atkDef, defPower.critRate, defPower.critDamage);
      atkHp -= dDmg.damage;
      rounds.push({ round: i + 1, attacker_dmg: 0, defender_dmg: dDmg.damage, defender_crit: dDmg.crit });
      if (atkHp <= 0) { result = 'defender'; break; }

      const aDmg = calcPKDamage(atkAtk, defDef, atkPower.critRate, atkPower.critDamage);
      defHp -= aDmg.damage;
      rounds[rounds.length - 1].attacker_dmg = aDmg.damage;
      if (defHp <= 0) { result = 'attacker'; break; }
    }
  }

  if (result === 'draw') {
    result = atkHp > defHp ? 'attacker' : (defHp > atkHp ? 'defender' : 'draw');
  }

  const attackerWins = result === 'attacker';
  const winner = attackerWins ? attacker : defender;
  const loser = attackerWins ? defender : attacker;

  // Rating 变化 (修复: 正确传递 winner/loser rating)
  const atkRating = getOrCreateRanking(attackerId);
  const defRating = getOrCreateRanking(defenderId);

  let ratingChange;
  if (attackerWins) {
    ratingChange = calculateRatingChange(atkRating.rating, defRating.rating);
  } else {
    // Defender won, so winner=defender, loser=attacker
    ratingChange = calculateRatingChange(defRating.rating, atkRating.rating);
  }

  // 称号 rating 加成 + broadcast rating 加成
  const atkRatingBonus = (atkPKMods.ratingBonus || 0) + (atkBroadcastMods.pkRatingBonus || 0);
  const defRatingBonus = (defPKMods.ratingBonus || 0) + (defBroadcastMods.pkRatingBonus || 0);

  // 更新 rankings
  if (attackerWins) {
    const atkGain = ratingChange.winnerChange + atkRatingBonus;
    const defLoss = ratingChange.loserChange; // 已是负数
    updateRanking(attackerId, atkRating.rating + atkGain, atkRating.wins + 1, atkRating.losses);
    updateRanking(defenderId, Math.max(0, defRating.rating + defLoss), defRating.wins, defRating.losses + 1);
  } else {
    const atkLoss = ratingChange.loserChange; // 已是负数
    const defGain = ratingChange.winnerChange + defRatingBonus;
    updateRanking(attackerId, Math.max(0, atkRating.rating + atkLoss), atkRating.wins, atkRating.losses + 1);
    updateRanking(defenderId, defRating.rating + defGain, defRating.wins + 1, defRating.losses);
  }

  // 败者标记死亡
  const loserStats = JSON.parse(loser.stats_json || '{}');
  loserStats.isDead = true;
  playerService.update(loser.id, { stats_json: loserStats });
  playerService.addLog(loser.id, '你在PK中战败了...意识沉入冥界。');

  // 世界线偏移 — PK死亡增加世界线偏移
  try {
    const worldlineService = require('./worldlineService');
    worldlineService.contributeShift(1, loser.id);
  } catch (e) { /* worldline not critical */ }

  // 记录 PK (loserChange 已是负数，直接使用)
  const atkChange = attackerWins ? ratingChange.winnerChange + atkRatingBonus : ratingChange.loserChange;
  const defChange = attackerWins ? ratingChange.loserChange : ratingChange.winnerChange + defRatingBonus;

  // PK获胜突破资源奖励
  if (attackerWins) {
    // constellationFavor: 有概率获得
    if (Math.random() < 0.2) {
      chapterService.awardResource(attackerId, 'constellationFavor', 1);
    }
    // abyssMark: PK评分越高概率越大
    const atkRatingNow = getOrCreateRanking(attackerId).rating;
    if (Math.random() < Math.min(0.25, atkRatingNow / 10000)) {
      chapterService.awardResource(attackerId, 'abyssMark', 1);
    }
  } else {
    if (Math.random() < 0.2) {
      chapterService.awardResource(defenderId, 'constellationFavor', 1);
    }
    const defRatingNow = getOrCreateRanking(defenderId).rating;
    if (Math.random() < Math.min(0.25, defRatingNow / 10000)) {
      chapterService.awardResource(defenderId, 'abyssMark', 1);
    }
  }

  db.prepare(`INSERT INTO pk_records (attacker_id, defender_id, winner_id, loser_id, battle_data_json, rating_change_json)
    VALUES (?, ?, ?, ?, ?, ?)`).run(
    attackerId, defenderId,
    winner.id, loser.id,
    JSON.stringify({ rounds, attackerHpRemaining: Math.max(0, atkHp), defenderHpRemaining: Math.max(0, defHp) }),
    JSON.stringify({ attacker_change: atkChange, defender_change: defChange })
  );

  // Round 6: 星流放送贡献记录 — PK 获胜
  if (attackerWins) {
    try {
      const broadcastService = require('./broadcastService');
      broadcastService.tryRecordContributions(attackerId, [{ type: 'win_pk', amount: 1, metadata: { opponent_id: defenderId } }]);
    } catch (e) { /* broadcast not critical */ }

    // Phase 3: 阵营贡献记录 — PK 获胜
    try {
      const factionService = require('./factionService');
      factionService.recordContribution(attackerId, 3, 'win_pk');
    } catch (e) { /* faction not critical */ }
  }

  // Quest progress tracking — both participants registered
  try {
    var questService = require('./questService');
    questService.checkProgress(attackerId, 'pk_encounter', { opponent_id: defenderId });
    questService.checkProgress(defenderId, 'pk_encounter', { opponent_id: attackerId });
    if (attackerWins) {
      questService.checkProgress(attackerId, 'win_pk', { opponent_id: defenderId });
    } else {
      questService.checkProgress(defenderId, 'win_pk', { opponent_id: attackerId });
    }
  } catch (e) { /* quest not critical */ }

  return {
    winner_id: winner.id,
    winner_name: winner.player_name,
    loser_id: loser.id,
    loser_name: loser.player_name,
    attacker_wins: attackerWins,
    rating_change: {
      attacker: atkChange,
      defender: defChange
    },
    battle_log: { rounds, total_rounds: rounds.length },
    narrative_suppression: {
      atk_identity: narrativeSupp.atkIdentity,
      def_identity: narrativeSupp.defIdentity,
      modifier: narrativeSupp.modifier,
      details: narrativeSupp.details
    }
  };
}

function calcPKDamage(atk, def, critRate, critDmg) {
  const baseDmg = Math.max(1, Math.round(atk - def * 0.5));
  const crit = Math.random() < (critRate || 0.05);
  const dmg = crit ? Math.round(baseDmg * (critDmg || 1.5)) : baseDmg;
  return { damage: dmg, crit };
}

function calculateRatingChange(winnerRating, loserRating) {
  const expectedWinner = 1 / (1 + Math.pow(10, (loserRating - winnerRating) / 400));
  const kFactor = 32;
  const change = Math.round(kFactor * (1 - expectedWinner));
  const clamped = Math.max(5, Math.min(40, change));
  return { winnerChange: clamped, loserChange: -clamped };
}

function getOrCreateRanking(playerId) {
  const db = getDb();
  let r = db.prepare('SELECT * FROM rankings WHERE player_id = ?').get(playerId);
  if (!r) {
    db.prepare('INSERT INTO rankings (player_id) VALUES (?)').run(playerId);
    r = db.prepare('SELECT * FROM rankings WHERE player_id = ?').get(playerId);
  }
  return r;
}

function updateRanking(playerId, rating, wins, losses) {
  const db = getDb();
  const existing = db.prepare('SELECT * FROM rankings WHERE player_id = ?').get(playerId);
  const highest = existing ? Math.max(existing.highest_rating, rating) : rating;
  db.prepare(`UPDATE rankings SET rating = ?, wins = ?, losses = ?, highest_rating = ?, updated_at = datetime('now','localtime')
    WHERE player_id = ?`).run(rating, wins, losses, highest, playerId);
}

function getRankings() {
  return require('./rankingService').getRankings();
}

function getPKRecords(playerId) {
  const db = getDb();
  const records = db.prepare(`
    SELECT pr.*,
      a.player_name as attacker_name,
      d.player_name as defender_name
    FROM pk_records pr
    JOIN players a ON pr.attacker_id = a.id
    JOIN players d ON pr.defender_id = d.id
    WHERE pr.attacker_id = ? OR pr.defender_id = ?
    ORDER BY pr.created_at DESC
    LIMIT 20
  `).all(playerId, playerId);

  return records.map(r => ({
    id: r.id,
    attacker_name: r.attacker_name,
    defender_name: r.defender_name,
    winner_id: r.winner_id,
    loser_id: r.loser_id,
    rating_change: JSON.parse(r.rating_change_json),
    battle_data: JSON.parse(r.battle_data_json),
    created_at: r.created_at
  }));
}

module.exports = { getOpponents, challenge, createChallenge, getPendingChallenges, resolveChallenge, expireOldChallenges, getRankings, getPKRecords };
