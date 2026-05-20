// 悬赏求助系统 — 世界频道悬赏
const { getDb } = require('../db/database');
const playerService = require('./playerService');
const combatService = require('./combatService');

// Reset daily counts if date changed
function resetDailyIfNeeded(playerId) {
  var raw = playerService.getRaw(playerId);
  if (!raw) return;
  var today = new Date().toISOString().slice(0, 10);
  if (raw.help_date !== today) {
    var db = getDb();
    db.prepare("UPDATE players SET daily_help_count = 0, daily_assist_count = 0, help_date = ? WHERE id = ?")
      .run(today, playerId);
  }
}

function getDailyCounts(playerId) {
  resetDailyIfNeeded(playerId);
  var raw = playerService.getRaw(playerId);
  if (!raw) return { helpCount: 0, assistCount: 0, maxHelp: 30, maxAssist: 30 };
  return {
    helpCount: raw.daily_help_count || 0,
    assistCount: raw.daily_assist_count || 0,
    maxHelp: 30,
    maxAssist: 30
  };
}

// Publish a help bounty to world channel
function publishBounty(playerId, monsterKey, locationKey, monsterName, sharePercent, combatRewards) {
  resetDailyIfNeeded(playerId);
  var db = getDb();
  var player = playerService.getRaw(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  var helpCount = player.daily_help_count || 0;
  if (helpCount >= 30) {
    return { error: { code: 'DAILY_LIMIT_HELP', message: '今日求助次数已达上限 (30次)' } };
  }

  // Cancel any existing pending bounty for this player
  db.prepare("UPDATE help_bounties SET status='cancelled' WHERE owner_id=? AND status='pending'").run(playerId);

  var share = Math.max(10, Math.min(90, sharePercent || 50));
  var info = db.prepare(
    "INSERT INTO help_bounties (owner_id, monster_key, location_key, monster_name, share_percent, bounty_rewards_json) VALUES (?, ?, ?, ?, ?, ?)"
  ).run(playerId, monsterKey, locationKey || '', monsterName || '', share, JSON.stringify(combatRewards || {}));

  var bountyId = info.lastInsertRowid;

  // Increment daily help count
  db.prepare("UPDATE players SET daily_help_count = daily_help_count + 1 WHERE id = ?").run(playerId);

  // Build chat message content
  var content = '【' + player.player_name + '】遭遇了可怕的 ' + (monsterName || monsterKey) + '，不敌求助\n' +
    '悬赏 ' + share + '% 的修为与掉落\n' +
    '怪物: ' + (monsterName || monsterKey) + '\n' +
    '地点: ' + (locationKey || '未知地界');

  // Broadcast to chat and via WebSocket
  try {
    var chatService = require('./chatService');
    chatService.sendSystemMessage('assist_invite', content, playerId, player.player_name, {
      bountyId: bountyId,
      ownerId: playerId,
      monsterKey: monsterKey,
      locationKey: locationKey || '',
      sharePercent: share
    });
  } catch (e) { /* chat not critical */ }

  try {
    var wsService = require('./wsService');
    wsService.broadcast({
      type: 'assist_invite',
      bountyId: bountyId,
      ownerId: playerId,
      ownerName: player.player_name,
      monsterName: monsterName || monsterKey,
      locationKey: locationKey || '',
      sharePercent: share
    });
  } catch (e) { /* ws not critical */ }

  return {
    success: true,
    data: {
      bountyId: bountyId,
      message: '悬赏已发布到世界频道！分享比例: ' + share + '%'
    }
  };
}

// Accept and resolve a bounty (another player helps)
function acceptBounty(bountyId, helperId) {
  resetDailyIfNeeded(helperId);
  var db = getDb();
  var bounty = db.prepare("SELECT * FROM help_bounties WHERE id=? AND status='pending'").get(bountyId);
  if (!bounty) return { error: { code: 'BOUNTY_NOT_FOUND', message: '悬赏不存在或已过期' } };
  if (bounty.owner_id === helperId) return { error: { code: 'SELF_ASSIST', message: '不能接受自己的悬赏' } };

  var helper = playerService.getRaw(helperId);
  if (!helper) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  var assistCount = helper.daily_assist_count || 0;
  if (assistCount >= 30) {
    return { error: { code: 'DAILY_LIMIT_ASSIST', message: '今日助力次数已达上限 (30次)' } };
  }

  // Check owner is still online
  if (!playerService.isPlayerOnline(bounty.owner_id)) {
    return { error: { code: 'OWNER_OFFLINE', message: '悬赏发布者已离线' } };
  }

  // Resolve combat with helper
  var battleResult = combatService.resolveCombat(bounty.owner_id, bounty.monster_key, 'fight', helperId);

  // Mark bounty as resolved
  db.prepare("UPDATE help_bounties SET status='resolved', helper_id=?, resolved_at=datetime('now','localtime') WHERE id=?")
    .run(helperId, bountyId);

  // Increment assist count
  db.prepare("UPDATE players SET daily_assist_count = daily_assist_count + 1 WHERE id = ?").run(helperId);

  // Calculate share rewards based on share percent
  var sharePercent = bounty.share_percent || 50;
  var helperRewards = {};
  var ownerRewards = {};

  if (battleResult && battleResult.success && battleResult.battle) {
    var rewards = battleResult.battle.rewards || {};
    // Apply share percentage to coins and exp
    if (rewards.coins) {
      var helperCoins = Math.floor(rewards.coins * sharePercent / 100);
      var ownerCoins = rewards.coins - helperCoins;
      helperRewards.coins = helperCoins;
      ownerRewards.coins = ownerCoins;
    }
    if (rewards.exp) {
      var helperExp = Math.floor(rewards.exp * sharePercent / 100);
      var ownerExp = rewards.exp - helperExp;
      helperRewards.exp = helperExp;
      ownerRewards.exp = ownerExp;
    }
    helperRewards.items = rewards.items || [];
  }

  // Give helper their share
  if (helperRewards.coins) {
    var helperPlayer = playerService.get(helperId);
    playerService.update(helperId, { coins: (helperPlayer.coins || 0) + helperRewards.coins });
    playerService.addLog(helperId, '响应悬赏成功！获得硬币 ' + helperRewards.coins + '，经验 ' + (helperRewards.exp || 0));
  }
  if (helperRewards.items && helperRewards.items.length > 0) {
    try {
      var inventoryService = require('./inventoryService');
      for (var i = 0; i < helperRewards.items.length; i++) {
        inventoryService.addItem(helperId, helperRewards.items[i].item_key || helperRewards.items[i], 1);
      }
    } catch (e) { /* not critical */ }
  }

  // Notify owner via WS
  try {
    var wsService = require('./wsService');
    wsService.send(bounty.owner_id, {
      type: 'assist_resolved',
      content: helper.player_name + ' 已响应你的悬赏！',
      battle: battleResult ? battleResult.battle : null
    });
  } catch (e) { /* ws not critical */ }

  return {
    success: true,
    data: {
      battle: battleResult ? battleResult.battle : null,
      helperRewards: helperRewards,
      ownerRewards: ownerRewards,
      sharePercent: sharePercent,
      message: '悬赏已解决！获得 ' + (helperRewards.coins || 0) + ' 硬币'
    }
  };
}

// Get pending bounties (for chat display)
function getPendingBounties() {
  var db = getDb();
  var rows = db.prepare(
    "SELECT hb.*, p.player_name as owner_name FROM help_bounties hb " +
    "JOIN players p ON hb.owner_id = p.id " +
    "WHERE hb.status='pending' ORDER BY hb.created_at DESC LIMIT 20"
  ).all();
  return rows;
}

// Get player's active bounty
function getMyActiveBounty(playerId) {
  var db = getDb();
  return db.prepare(
    "SELECT * FROM help_bounties WHERE owner_id=? AND status='pending' ORDER BY created_at DESC LIMIT 1"
  ).get(playerId);
}

// Cancel bounty
function cancelBounty(playerId) {
  var db = getDb();
  var bounty = db.prepare("SELECT * FROM help_bounties WHERE owner_id=? AND status='pending'").get(playerId);
  if (!bounty) return { error: { code: 'NO_ACTIVE_BOUNTY', message: '没有进行中的悬赏' } };
  db.prepare("UPDATE help_bounties SET status='cancelled' WHERE id=?").run(bounty.id);
  return { success: true, data: { message: '悬赏已取消' } };
}

module.exports = { publishBounty, acceptBounty, getPendingBounties, getMyActiveBounty, cancelBounty, getDailyCounts };
