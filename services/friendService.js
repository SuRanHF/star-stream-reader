// 好友系统服务
var getDb = function() { return require('../db/database').getDb(); };
var playerService = require('./playerService');

function sendFriendRequest(playerId, friendId) {
  if (playerId === friendId) {
    return { error: { code: 'SELF_FRIEND', message: '不能添加自己为好友' } };
  }

  var db = getDb();
  var friend = playerService.getRaw(friendId);
  if (!friend) return { error: { code: 'PLAYER_NOT_FOUND', message: '目标玩家不存在' } };

  var existing = db.prepare(
    'SELECT * FROM friendships WHERE (player_id = ? AND friend_id = ?) OR (player_id = ? AND friend_id = ?)'
  ).get(playerId, friendId, friendId, playerId);

  if (existing) {
    if (existing.status === 'accepted') {
      return { error: { code: 'ALREADY_FRIENDS', message: '已经是好友' } };
    }
    if (existing.status === 'pending') {
      if (existing.player_id === playerId) {
        return { error: { code: 'ALREADY_REQUESTED', message: '已发送过好友申请，等待对方回应' } };
      }
      // The other player already sent a request — auto-accept
      db.prepare("UPDATE friendships SET status='accepted', updated_at=datetime('now','localtime') WHERE id=?").run(existing.id);
      playerService.addLog(playerId, '与 ' + friend.player_name + ' 成为好友');
      playerService.addLog(friendId, '与 ' + playerService.getRaw(playerId).player_name + ' 成为好友');
      return { success: true, data: { status: 'accepted', message: '好友申请已自动接受' } };
    }
    if (existing.status === 'blocked') {
      return { error: { code: 'BLOCKED', message: '无法发送好友申请' } };
    }
  }

  db.prepare(
    'INSERT INTO friendships (player_id, friend_id, status) VALUES (?, ?, ?)'
  ).run(playerId, friendId, 'pending');

  playerService.addLog(friendId, playerService.getRaw(playerId).player_name + ' 向你发送了好友申请');
  return { success: true, data: { status: 'pending', message: '好友申请已发送' } };
}

function acceptFriendRequest(playerId, requestId) {
  var db = getDb();
  var friendship = db.prepare('SELECT * FROM friendships WHERE id = ? AND friend_id = ? AND status = ?').get(requestId, playerId, 'pending');
  if (!friendship) return { error: { code: 'REQUEST_NOT_FOUND', message: '好友申请不存在或已过期' } };

  db.prepare("UPDATE friendships SET status='accepted', updated_at=datetime('now','localtime') WHERE id=?").run(requestId);

  var friend = playerService.getRaw(friendship.player_id);
  playerService.addLog(playerId, '与 ' + (friend ? friend.player_name : '对方') + ' 成为好友');
  playerService.addLog(friendship.player_id, '与 ' + playerService.getRaw(playerId).player_name + ' 成为好友');
  return { success: true, data: { message: '已接受好友申请' } };
}

function declineFriendRequest(playerId, requestId) {
  var db = getDb();
  var friendship = db.prepare('SELECT * FROM friendships WHERE id = ? AND friend_id = ? AND status = ?').get(requestId, playerId, 'pending');
  if (!friendship) return { error: { code: 'REQUEST_NOT_FOUND', message: '好友申请不存在' } };

  db.prepare("UPDATE friendships SET status='declined', updated_at=datetime('now','localtime') WHERE id=?").run(requestId);
  return { success: true, data: { message: '已拒绝好友申请' } };
}

function removeFriend(playerId, friendId) {
  var db = getDb();
  var friendship = db.prepare(
    "SELECT * FROM friendships WHERE ((player_id = ? AND friend_id = ?) OR (player_id = ? AND friend_id = ?)) AND status = 'accepted'"
  ).get(playerId, friendId, friendId, playerId);

  if (!friendship) return { error: { code: 'NOT_FRIENDS', message: '不是好友关系' } };

  db.prepare('DELETE FROM friendships WHERE id = ?').run(friendship.id);
  return { success: true, data: { message: '已删除好友' } };
}

function getFriendList(playerId) {
  var db = getDb();
  var rows = db.prepare(
    "SELECT f.*, p.player_name, p.stats_json FROM friendships f " +
    "JOIN players p ON (CASE WHEN f.player_id = ? THEN f.friend_id ELSE f.player_id END) = p.id " +
    "WHERE (f.player_id = ? OR f.friend_id = ?) AND f.status = 'accepted' " +
    "ORDER BY p.player_name"
  ).all(playerId, playerId, playerId);

  return rows.map(function(r) {
    var stats = typeof r.stats_json === 'string' ? JSON.parse(r.stats_json) : (r.stats_json || {});
    var friendId = r.player_id === playerId ? r.friend_id : r.player_id;
    return {
      friendship_id: r.id,
      player_id: friendId,
      player_name: r.player_name,
      level: stats.level || 1,
      avatarRank: stats.avatarRank || 'F',
      avatarRankName: stats.avatarRankName || '临时化身',
      isOnline: playerService.getOnlinePlayers().indexOf(friendId) >= 0,
      created_at: r.created_at
    };
  });
}

function getPendingRequests(playerId) {
  var db = getDb();
  var rows = db.prepare(
    "SELECT f.*, p.player_name FROM friendships f " +
    "JOIN players p ON f.player_id = p.id " +
    "WHERE f.friend_id = ? AND f.status = 'pending' " +
    "ORDER BY f.created_at DESC"
  ).all(playerId);

  return rows.map(function(r) {
    return {
      id: r.id,
      from_player_id: r.player_id,
      from_player_name: r.player_name,
      created_at: r.created_at
    };
  });
}

function sendGift(playerId, targetId, itemKey) {
  var db = getDb();
  var player = playerService.getRaw(playerId);
  var target = playerService.getRaw(targetId);
  if (!target) return { error: { code: 'PLAYER_NOT_FOUND', message: '目标玩家不存在' } };

  // Check friendship
  var friendship = db.prepare(
    "SELECT * FROM friendships WHERE status='accepted' AND ((player_id=? AND friend_id=?) OR (player_id=? AND friend_id=?))"
  ).get(playerId, targetId, targetId, playerId);
  if (!friendship) return { error: { code: 'NOT_FRIENDS', message: '只能给好友赠送礼物' } };

  // Check inventory
  var invRow = db.prepare("SELECT quantity FROM player_inventory WHERE player_id=? AND item_key=?").get(playerId, itemKey);
  if (!invRow || invRow.quantity < 1) return { error: { code: 'NO_ITEM', message: '你没有这个物品' } };

  // Perform transfer
  if (invRow.quantity <= 1) {
    db.prepare("DELETE FROM player_inventory WHERE player_id=? AND item_key=?").run(playerId, itemKey);
  } else {
    db.prepare("UPDATE player_inventory SET quantity=quantity-1 WHERE player_id=? AND item_key=?").run(playerId, itemKey);
  }
  var targetInv = db.prepare("SELECT quantity FROM player_inventory WHERE player_id=? AND item_key=?").get(targetId, itemKey);
  if (targetInv) {
    db.prepare("UPDATE player_inventory SET quantity=quantity+1 WHERE player_id=? AND item_key=?").run(targetId, itemKey);
  } else {
    var itemInfo = db.prepare("SELECT item_name FROM items WHERE item_key=?").get(itemKey);
    db.prepare("INSERT INTO player_inventory (player_id, item_key, item_name, quantity) VALUES (?,?,?,1)").run(targetId, itemKey, (itemInfo && itemInfo.item_name) || itemKey);
  }

  playerService.addLog(playerId, '向 ' + target.player_name + ' 赠送了 ' + itemKey);
  playerService.addLog(targetId, player.player_name + ' 赠送了你一份礼物：' + itemKey);
  return { success: true, data: { message: '礼物已送出' } };
}

function getRecentInteractions(playerId) {
  var db = getDb();
  // Get recent friend activity from player logs
  var rows = db.prepare(
    "SELECT p.player_name, p.id as player_id, '' as description, '' as created_at FROM players p " +
    "JOIN friendships f ON (f.player_id=? AND f.friend_id=p.id) OR (f.friend_id=? AND f.player_id=p.id) " +
    "WHERE f.status='accepted' LIMIT 10"
  ).all(playerId, playerId);
  return rows;
}

module.exports = {
  sendFriendRequest, acceptFriendRequest, declineFriendRequest,
  removeFriend, getFriendList, getPendingRequests,
  sendGift, getRecentInteractions
};
