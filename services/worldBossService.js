// 世界Boss全服共斗系统
const { getDb } = require('../db/database');
const playerService = require('./playerService');

var BOSS_TEMPLATES = [
  { bossKey: 'calamity_devourer', name: '灾厄吞噬者', description: '从世界线裂隙中涌出的巨大阴影，吞噬一切故事的存在。', hp: 5000, maxHp: 5000, attack: 30, defense: 20, speed: 8, level: 10, rewards: { coins: 500, exp: 200 }, respawnMinutes: 60 },
  { bossKey: 'story_collapse', name: '叙事崩坏体', description: '未被讲述的故事凝聚成的扭曲生物，它的存在本身就在瓦解现实。', hp: 8000, maxHp: 8000, attack: 40, defense: 25, speed: 6, level: 15, rewards: { coins: 800, exp: 350 }, respawnMinutes: 90 },
  { bossKey: 'final_chapter_beast', name: '终章之兽', description: '被遗忘的结局篇章具象化成的巨兽，咆哮中夹杂着无数未完成的故事。', hp: 12000, maxHp: 12000, attack: 55, defense: 30, speed: 10, level: 20, rewards: { coins: 1200, exp: 500 }, respawnMinutes: 120 }
];

var COOLDOWN_MINUTES = 30; // Base cooldown after defeat before next spawn

function spawnBoss() {
  var db = getDb();
  // Don't spawn if there's already an active boss
  var active = db.prepare("SELECT * FROM world_bosses WHERE status IN ('spawning','active')").get();
  if (active) return active;

  // Check cooldown
  var lastDefeated = db.prepare("SELECT * FROM world_bosses WHERE status='defeated' ORDER BY defeat_time DESC LIMIT 1").get();
  if (lastDefeated) {
    var defeatedAt = new Date(lastDefeated.defeat_time + 'Z').getTime();
    var elapsed = (Date.now() - defeatedAt) / 60000;
    if (elapsed < COOLDOWN_MINUTES) return null;
  }

  // Pick a random template scaled by how many times it's been used
  var template = BOSS_TEMPLATES[Math.floor(Math.random() * BOSS_TEMPLATES.length)];

  // Scale HP based on active player count
  var activePlayers = playerService.getOnlinePlayers().length || 1;
  var hpScale = 1 + Math.floor(activePlayers / 5) * 0.5;
  var scaledHp = Math.floor(template.hp * hpScale);
  var scaledMaxHp = scaledHp;

  var info = db.prepare(
    "INSERT INTO world_bosses (boss_key, name, description, hp, max_hp, attack, defense, speed, level, rewards_json, status, spawn_time) VALUES (?,?,?,?,?,?,?,?,?,?,'active',datetime('now','localtime'))"
  ).run(template.bossKey, template.name, template.description, scaledHp, scaledMaxHp, template.attack, template.defense, template.speed, template.level, JSON.stringify(template.rewards));

  return db.prepare("SELECT * FROM world_bosses WHERE id = ?").get(info.lastInsertRowid);
}

function getActiveBoss() {
  var db = getDb();
  var boss = db.prepare("SELECT * FROM world_bosses WHERE status IN ('spawning','active') ORDER BY spawn_time DESC LIMIT 1").get();
  if (!boss) return null;
  if (boss.rewards_json) boss.rewards = JSON.parse(boss.rewards_json);
  return boss;
}

function fightBoss(playerId, action) {
  var db = getDb();
  var player = playerService.get(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  var boss = getActiveBoss();
  if (!boss) return { error: { code: 'NO_BOSS', message: '当前没有活跃的世界Boss' } };

  if (action === 'flee') {
    return { action: 'flee', success: true, message: '你从世界Boss面前撤退了。' };
  }

  var stats = player.stats || {};
  var staminaCost = 10;
  if ((stats.stamina || 0) < staminaCost) {
    return { error: { code: 'NO_STAMINA', message: '体力不足，需要' + staminaCost + '点体力' } };
  }

  // Calculate player damage
  var playerAtk = (stats.attack || 10) + (stats.allocatedAtk || 0) * 5;
  var playerCrit = (stats.critRate || 0.05) + (stats.allocatedCrit || 0) * 0.01;
  var critDamage = (stats.critDamage || 1.5) + (stats.allocatedCrit || 0) * 0.02;
  var isCrit = Math.random() < playerCrit;
  var baseDmg = Math.max(1, playerAtk - boss.defense * 0.5 + Math.floor(Math.random() * 10));
  var damage = isCrit ? Math.floor(baseDmg * critDamage) : Math.floor(baseDmg);

  // Apply damage to boss
  var newHp = Math.max(0, boss.hp - damage);
  var defeated = newHp <= 0;
  db.prepare("UPDATE world_bosses SET hp = ?, status = ?, defeat_time = datetime('now','localtime') WHERE id = ?")
    .run(newHp, defeated ? 'defeated' : 'active', boss.id);

  // Record participation
  var existing = db.prepare("SELECT * FROM world_boss_participation WHERE boss_id = ? AND player_id = ?").get(boss.id, playerId);
  if (existing) {
    db.prepare("UPDATE world_boss_participation SET damage_dealt = damage_dealt + ?, contribution_score = contribution_score + ?, updated_at = datetime('now','localtime') WHERE boss_id = ? AND player_id = ?")
      .run(damage, damage, boss.id, playerId);
  } else {
    db.prepare("INSERT INTO world_boss_participation (boss_id, player_id, damage_dealt, contribution_score) VALUES (?, ?, ?, ?)")
      .run(boss.id, playerId, damage, damage);
  }

  // Consume stamina
  stats.stamina = (stats.stamina || 50) - staminaCost;
  playerService.update(playerId, { stats_json: stats });

  // If defeated, distribute rewards
  if (defeated) {
    distributeRewards(boss.id);
  }

  playerService.addLog(playerId, (isCrit ? '[暴击] ' : '') + '对世界Boss ' + boss.name + ' 造成了 ' + damage + ' 点伤害');

  return {
    action: 'fight',
    success: true,
    damage: damage,
    crit: isCrit,
    bossHp: newHp,
    bossMaxHp: boss.max_hp,
    defeated: defeated,
    message: (isCrit ? '暴击！' : '') + '造成了 ' + damage + ' 点伤害！Boss剩余HP: ' + newHp + '/' + boss.max_hp
  };
}

function distributeRewards(bossId) {
  var db = getDb();
  var boss = db.prepare("SELECT * FROM world_bosses WHERE id = ?").get(bossId);
  if (!boss) return;

  var rewards = JSON.parse(boss.rewards_json || '{}');
  var participants = db.prepare(
    "SELECT * FROM world_boss_participation WHERE boss_id = ? ORDER BY contribution_score DESC"
  ).all(bossId);

  if (participants.length === 0) return;

  var total = participants.length;
  // Tier thresholds
  var top10 = Math.max(1, Math.ceil(total * 0.1));
  var top25 = Math.max(1, Math.ceil(total * 0.25));
  var top50 = Math.max(1, Math.ceil(total * 0.5));

  for (var i = 0; i < participants.length; i++) {
    var p = participants[i];
    var rank = i + 1;
    var multiplier = 1;
    if (rank <= top10) multiplier = 3;
    else if (rank <= top25) multiplier = 2;
    else if (rank <= top50) multiplier = 1.5;

    var coinsEarned = Math.floor((rewards.coins || 100) * multiplier);
    var expEarned = Math.floor((rewards.exp || 50) * multiplier);

    playerService.update(p.player_id, {
      coins: ((playerService.getRaw(p.player_id) || {}).coins || 0) + coinsEarned
    });

    var player = playerService.get(p.player_id);
    if (player && player.stats) {
      player.stats.exp = (player.stats.exp || 0) + expEarned;
      playerService.update(p.player_id, { stats_json: player.stats });
    }

    playerService.addLog(p.player_id, '世界Boss讨伐完成！排名 #' + rank + '，获得 ' + coinsEarned + ' 硬币，' + expEarned + ' 经验');

    // Mark reward claimed
    db.prepare("UPDATE world_boss_participation SET rewards_claimed = 1 WHERE boss_id = ? AND player_id = ?")
      .run(bossId, p.player_id);
  }
}

function getBossRanking(bossId) {
  var db = getDb();
  var rows = db.prepare(
    "SELECT wbp.*, p.player_name FROM world_boss_participation wbp JOIN players p ON wbp.player_id = p.id WHERE wbp.boss_id = ? ORDER BY wbp.contribution_score DESC LIMIT 50"
  ).all(bossId || getActiveBoss()?.id);

  return rows.map(function(r, i) {
    return {
      rank: i + 1,
      playerId: r.player_id,
      playerName: r.player_name,
      damage: r.damage_dealt,
      score: r.contribution_score,
      claimed: !!r.rewards_claimed
    };
  });
}

function getBossHistory(limit) {
  var db = getDb();
  return db.prepare("SELECT * FROM world_bosses WHERE status='defeated' ORDER BY defeat_time DESC LIMIT ?").all(limit || 10);
}

function getMyBossContribution(playerId, bossId) {
  var db = getDb();
  return db.prepare("SELECT * FROM world_boss_participation WHERE boss_id = ? AND player_id = ?").get(bossId, playerId);
}

// Called by scheduler to spawn new bosses
function tickSpawn() {
  try {
    var active = getActiveBoss();
    if (!active) {
      return spawnBoss();
    }
  } catch (e) { /* not critical */ }
  return null;
}

module.exports = { spawnBoss, getActiveBoss, fightBoss, getBossRanking, getBossHistory, getMyBossContribution, tickSpawn, BOSS_TEMPLATES };
