// 聊天频道服务
var getDb = function() { return require('../db/database').getDb(); };

var MAX_MESSAGE_LENGTH = 500;
var MAX_RECENT_MESSAGES = 100;

function sendMessage(playerId, playerName, message, channel) {
  channel = channel || 'global';
  if (!message || String(message).trim().length === 0) {
    return { error: { code: 'EMPTY_MESSAGE', message: '消息不能为空' } };
  }
  if (String(message).length > MAX_MESSAGE_LENGTH) {
    return { error: { code: 'MESSAGE_TOO_LONG', message: '消息最长' + MAX_MESSAGE_LENGTH + '字符' } };
  }

  var db = getDb();
  var result = db.prepare(
    'INSERT INTO chat_messages (player_id, player_name, message, channel) VALUES (?, ?, ?, ?)'
  ).run(playerId, playerName, String(message).trim(), channel);

  return {
    success: true,
    data: {
      id: result.lastInsertRowid,
      player_id: playerId,
      player_name: playerName,
      message: String(message).trim(),
      channel: channel,
      created_at: new Date().toISOString()
    }
  };
}

function getRecentMessages(channel, limit, sinceId) {
  channel = channel || 'global';
  limit = Math.min(limit || 50, MAX_RECENT_MESSAGES);

  var db = getDb();
  var rows;
  if (sinceId) {
    rows = db.prepare(
      'SELECT * FROM chat_messages WHERE channel = ? AND id > ? ORDER BY id DESC LIMIT ?'
    ).all(channel, sinceId, limit);
  } else {
    rows = db.prepare(
      'SELECT * FROM chat_messages WHERE channel = ? ORDER BY id DESC LIMIT ?'
    ).all(channel, limit);
  }

  // Return in chronological order (oldest first)
  rows.reverse();

  return {
    success: true,
    data: rows.map(function(r) {
      return {
        id: r.id,
        player_id: r.player_id,
        player_name: r.player_name,
        message: r.message,
        channel: r.channel,
        created_at: r.created_at
      };
    })
  };
}

function getActiveChatters(channel, minutes) {
  channel = channel || 'global';
  minutes = minutes || 10;
  var db = getDb();
  var rows = db.prepare(
    "SELECT DISTINCT player_id, player_name FROM chat_messages WHERE channel = ? AND created_at > datetime('now', 'localtime', '-' || ? || ' minutes')"
  ).all(channel, minutes);

  return { success: true, data: rows };
}

module.exports = { sendMessage, getRecentMessages, getActiveChatters, MAX_MESSAGE_LENGTH };
