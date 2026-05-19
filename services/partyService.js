// 组队系统 + 组队Boss战 (Phase 4)
const { getDb } = require('../db/database');
const playerService = require('./playerService');
const combatService = require('./combatService');

var MAX_PARTY_SIZE = 3;

function createParty(leaderId, bossKey) {
  var db = getDb();
  var player = playerService.getRaw(leaderId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  // Check if already in a party
  var existing = db.prepare(
    "SELECT p.id FROM parties p JOIN party_members pm ON pm.party_id = p.id WHERE pm.player_id = ? AND p.status IN ('recruiting','full','in_combat')"
  ).get(leaderId);
  if (existing) return { error: { code: 'ALREADY_IN_PARTY', message: '你已经在一个队伍中' } };

  if (bossKey) {
    var boss = db.prepare('SELECT * FROM monsters WHERE monster_key = ?').get(bossKey);
    if (!boss) return { error: { code: 'BOSS_NOT_FOUND', message: 'Boss不存在' } };
  }

  var result = db.prepare(
    'INSERT INTO parties (leader_id, boss_key) VALUES (?, ?)'
  ).run(leaderId, bossKey || null);

  var partyId = result.lastInsertRowid;
  db.prepare('INSERT INTO party_members (party_id, player_id, ready) VALUES (?, ?, 1)').run(partyId, leaderId);

  playerService.addLog(leaderId, '创建了讨伐队伍' + (bossKey ? '，目标: ' + bossKey : ''));

  return { party: getParty(partyId) };
}

function joinParty(partyId, playerId) {
  var db = getDb();
  var party = db.prepare("SELECT * FROM parties WHERE id = ? AND status IN ('recruiting','full')").get(partyId);
  if (!party) return { error: { code: 'PARTY_NOT_FOUND', message: '队伍不存在或已无法加入' } };

  // Check if already in a party
  var existing = db.prepare(
    "SELECT p.id FROM parties p JOIN party_members pm ON pm.party_id = p.id WHERE pm.player_id = ? AND p.status IN ('recruiting','full','in_combat')"
  ).get(playerId);
  if (existing) return { error: { code: 'ALREADY_IN_PARTY', message: '你已经在一个队伍中' } };

  // Check if already in this party
  var inThisParty = db.prepare('SELECT * FROM party_members WHERE party_id = ? AND player_id = ?').get(partyId, playerId);
  if (inThisParty) return { error: { code: 'ALREADY_MEMBER', message: '你已经在这个队伍中' } };

  // Check member count
  var memberCount = db.prepare('SELECT COUNT(*) as c FROM party_members WHERE party_id = ?').get(partyId).c;
  if (memberCount >= MAX_PARTY_SIZE) return { error: { code: 'PARTY_FULL', message: '队伍已满' } };

  db.prepare('INSERT INTO party_members (party_id, player_id, ready) VALUES (?, ?, 1)').run(partyId, playerId);

  var newCount = db.prepare('SELECT COUNT(*) as c FROM party_members WHERE party_id = ?').get(partyId).c;
  if (newCount >= MAX_PARTY_SIZE) {
    db.prepare("UPDATE parties SET status = 'full', updated_at = datetime('now','localtime') WHERE id = ?").run(partyId);
  }

  var joiner = playerService.getRaw(playerId);
  playerService.addLog(playerId, '加入了讨伐队伍');
  playerService.addLog(party.leader_id, (joiner ? joiner.player_name : '一位玩家') + ' 加入了队伍');

  try {
    var questService = require('./questService');
    questService.checkProgress(playerId, 'join_party', { party_id: partyId });
  } catch (e) { /* quest not critical */ }

  return { party: getParty(partyId) };
}

function leaveParty(partyId, playerId) {
  var db = getDb();
  var party = db.prepare('SELECT * FROM parties WHERE id = ?').get(partyId);
  if (!party) return { error: { code: 'PARTY_NOT_FOUND', message: '队伍不存在' } };

  db.prepare('DELETE FROM party_members WHERE party_id = ? AND player_id = ?').run(partyId, playerId);

  var remaining = db.prepare('SELECT COUNT(*) as c FROM party_members WHERE party_id = ?').get(partyId).c;
  if (remaining === 0) {
    db.prepare("UPDATE parties SET status = 'disbanded', updated_at = datetime('now','localtime') WHERE id = ?").run(partyId);
  } else if (party.leader_id === playerId) {
    // Transfer leadership
    var newLeader = db.prepare('SELECT player_id FROM party_members WHERE party_id = ? LIMIT 1').get(partyId);
    if (newLeader) {
      db.prepare('UPDATE parties SET leader_id = ?, updated_at = datetime(\'now\',\'localtime\') WHERE id = ?').run(newLeader.player_id, partyId);
      playerService.addLog(newLeader.player_id, '你成为了新的队长');
    }
  }

  playerService.addLog(playerId, '离开了讨伐队伍');
  return { success: true };
}

function getParty(partyId) {
  var db = getDb();
  var party = db.prepare('SELECT * FROM parties WHERE id = ?').get(partyId);
  if (!party) return null;

  var members = db.prepare(
    'SELECT pm.*, p.player_name, p.stats_json FROM party_members pm JOIN players p ON p.id = pm.player_id WHERE pm.party_id = ?'
  ).all(partyId);

  var leader = db.prepare('SELECT player_name FROM players WHERE id = ?').get(party.leader_id);

  return {
    id: party.id,
    leaderId: party.leader_id,
    leaderName: leader ? leader.player_name : '未知',
    status: party.status,
    bossKey: party.boss_key,
    members: members.map(function(m) {
      var stats = JSON.parse(m.stats_json || '{}');
      return {
        playerId: m.player_id,
        playerName: m.player_name,
        level: stats.level || 1,
        ready: !!m.ready,
        joinedAt: m.joined_at
      };
    }),
    createdAt: party.created_at,
    updatedAt: party.updated_at
  };
}

function getActiveParties() {
  var db = getDb();
  var rows = db.prepare("SELECT * FROM parties WHERE status IN ('recruiting','full') ORDER BY created_at DESC").all();
  return rows.map(function(r) { return getParty(r.id); });
}

function getPlayerParty(playerId) {
  var db = getDb();
  var row = db.prepare(
    "SELECT p.id FROM parties p JOIN party_members pm ON pm.party_id = p.id WHERE pm.player_id = ? AND p.status IN ('recruiting','full','in_combat')"
  ).get(playerId);
  if (!row) return null;
  return getParty(row.id);
}

function setReady(partyId, playerId, ready) {
  var db = getDb();
  db.prepare('UPDATE party_members SET ready = ? WHERE party_id = ? AND player_id = ?').run(ready ? 1 : 0, partyId, playerId);
  return { success: true };
}

function startPartyBossBattle(partyId, playerId) {
  var db = getDb();
  var party = db.prepare('SELECT * FROM parties WHERE id = ?').get(partyId);
  if (!party) return { error: { code: 'PARTY_NOT_FOUND', message: '队伍不存在' } };
  if (party.leader_id !== playerId) return { error: { code: 'NOT_LEADER', message: '只有队长可以发起讨伐' } };
  if (!party.boss_key) return { error: { code: 'NO_BOSS', message: '没有设定讨伐目标' } };

  var members = db.prepare('SELECT * FROM party_members WHERE party_id = ?').all(partyId);
  if (members.length < 2) return { error: { code: 'NOT_ENOUGH_MEMBERS', message: '至少需要2人才能发起讨伐' } };

  var unready = members.filter(function(m) { return !m.ready; });
  if (unready.length > 0) return { error: { code: 'NOT_ALL_READY', message: '所有成员需要准备就绪' } };

  db.prepare("UPDATE parties SET status = 'in_combat', updated_at = datetime('now','localtime') WHERE id = ?").run(partyId);

  // Resolve party boss battle — each member fights
  var results = [];
  for (var i = 0; i < members.length; i++) {
    var m = members[i];
    try {
      var battleResult = combatService.resolveCombat(m.player_id, party.boss_key, 'attack');
      results.push({
        playerId: m.player_id,
        result: battleResult.result || 'loss',
        damage: battleResult.battle_log ? battleResult.battle_log.total_rounds : 0
      });
    } catch (e) {
      results.push({ playerId: m.player_id, result: 'error', message: e.message });
    }
  }

  // Check if boss was defeated (all survived and at least one won)
  var victories = results.filter(function(r) { return r.result === 'win'; });
  var bossDefeated = victories.length >= Math.ceil(members.length * 0.5);

  if (bossDefeated) {
    // Bonus rewards for all members
    for (var j = 0; j < members.length; j++) {
      try {
        var chapterService = require('./chapterService');
        chapterService.awardResource(members[j].player_id, 'constellationFavor', 1);
      } catch (e) { /* non-critical */ }
    }
    // Quest progress
    for (var k = 0; k < members.length; k++) {
      try {
        var qs = require('./questService');
        qs.checkProgress(members[k].player_id, 'party_boss', { party_id: partyId, boss_key: party.boss_key });
      } catch (e) { /* quest not critical */ }
    }
    db.prepare("UPDATE parties SET status = 'disbanded', updated_at = datetime('now','localtime') WHERE id = ?").run(partyId);
    playerService.addLog(party.leader_id, '队伍成功讨伐了 ' + party.boss_key + '！');
  } else {
    db.prepare("UPDATE parties SET status = 'recruiting', updated_at = datetime('now','localtime') WHERE id = ?").run(partyId);
  }

  return {
    partyId: partyId,
    bossDefeated: bossDefeated,
    results: results
  };
}

module.exports = { createParty, joinParty, leaveParty, getParty, getActiveParties, getPlayerParty, setReady, startPartyBossBattle, MAX_PARTY_SIZE };
