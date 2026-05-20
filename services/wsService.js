// WebSocket 实时通信服务
// 替代 HTTP 心跳轮询，实现 PK 通知、在线检测、聊天实时推送

const WebSocket = require('ws');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'dev-secret';

// playerId → { ws, playerName, userId, connectedAt }
const _connections = new Map();
// userId → Set of playerIds
const _userPlayers = new Map();

let _wss = null;

function init(server) {
  _wss = new WebSocket.Server({ server, path: '/ws' });

  _wss.on('connection', function(ws, req) {
    var authenticated = false;
    var playerId = null;
    var userId = null;
    var playerName = '';

    ws.isAlive = true;

    ws.on('pong', function() {
      ws.isAlive = true;
    });

    ws.on('message', function(raw) {
      try {
        var msg = JSON.parse(raw.toString());
      } catch (e) {
        ws.send(JSON.stringify({ type: 'error', content: 'Invalid JSON' }));
        return;
      }

      // 首次认证消息
      if (msg.type === 'auth') {
        try {
          var decoded = jwt.verify(msg.token, JWT_SECRET);
          userId = decoded.id;
        } catch (e) {
          ws.send(JSON.stringify({ type: 'error', content: 'Token invalid' }));
          return;
        }

        playerId = msg.playerId;
        playerName = msg.playerName || '';

        if (!playerId) {
          ws.send(JSON.stringify({ type: 'error', content: 'playerId required' }));
          return;
        }

        // 关闭旧连接（同一玩家重连）
        var old = _connections.get(playerId);
        if (old && old.ws !== ws) {
          try { old.ws.close(4001, 'reconnected'); } catch (e) { /* */ }
        }

        _connections.set(playerId, { ws: ws, playerName: playerName, userId: userId, connectedAt: new Date() });

        if (!_userPlayers.has(userId)) {
          _userPlayers.set(userId, new Set());
        }
        _userPlayers.get(userId).add(playerId);

        authenticated = true;
        ws.send(JSON.stringify({ type: 'auth_ok', playerId: playerId }));
        return;
      }

      // 未认证时忽略其他消息
      if (!authenticated) return;

      // ping（保持连接）
      if (msg.type === 'ping') {
        ws.send(JSON.stringify({ type: 'pong' }));
        return;
      }
    });

    ws.on('close', function() {
      if (playerId) {
        _connections.delete(playerId);
        if (userId && _userPlayers.has(userId)) {
          _userPlayers.get(userId).delete(playerId);
          if (_userPlayers.get(userId).size === 0) {
            _userPlayers.delete(userId);
          }
        }
      }
    });

    ws.on('error', function() { /* ignore */ });
  });

  // 心跳检测：每 20s 检查连接状态
  setInterval(function() {
    _wss.clients.forEach(function(ws) {
      if (ws.isAlive === false) return ws.terminate();
      ws.isAlive = false;
      try { ws.ping(); } catch (e) { /* */ }
    });
  }, 20000);

  console.log('WebSocket 服务已启动 (/ws)');
}

// 向指定玩家发送消息
function send(playerId, message) {
  var entry = _connections.get(playerId);
  if (!entry || entry.ws.readyState !== WebSocket.OPEN) return false;
  try {
    entry.ws.send(JSON.stringify(message));
    return true;
  } catch (e) {
    return false;
  }
}

// 向所有在线玩家广播
function broadcast(message, excludePlayerId) {
  _connections.forEach(function(entry, pid) {
    if (pid === excludePlayerId) return;
    if (entry.ws.readyState === WebSocket.OPEN) {
      try { entry.ws.send(JSON.stringify(message)); } catch (e) { /* */ }
    }
  });
}

// 检查玩家是否在线（基于 WebSocket 连接状态）
function isOnline(playerId) {
  var entry = _connections.get(playerId);
  return !!(entry && entry.ws.readyState === WebSocket.OPEN);
}

// 获取所有在线玩家 ID
function getOnlinePlayerIds() {
  var ids = [];
  _connections.forEach(function(entry, pid) {
    if (entry.ws.readyState === WebSocket.OPEN) {
      ids.push(pid);
    }
  });
  return ids;
}

// 获取在线玩家列表（含名称）
function getOnlinePlayers() {
  var players = [];
  _connections.forEach(function(entry, pid) {
    if (entry.ws.readyState === WebSocket.OPEN) {
      players.push({ id: pid, player_name: entry.playerName });
    }
  });
  return players;
}

function getConnectionCount() {
  return _connections.size;
}

module.exports = { init, send, broadcast, isOnline, getOnlinePlayerIds, getOnlinePlayers, getConnectionCount };
