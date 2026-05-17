const jwt = require('jsonwebtoken');
const playerService = require('../services/playerService');

// JWT_SECRET must be set — server.js generates a default on startup if not in env
const JWT_SECRET = process.env.JWT_SECRET;

function authRequired(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ success: false, error: { code: 'UNAUTHORIZED', message: '请先登录' } });
  }

  const token = authHeader.slice(7);
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = { id: decoded.id, username: decoded.username, role: decoded.role };
    next();
  } catch (e) {
    return res.status(401).json({ success: false, error: { code: 'TOKEN_INVALID', message: '登录已过期，请重新登录' } });
  }
}

function requireOwnPlayer(req, res, next) {
  // Extract playerId from request body or URL path
  // req.params is NOT populated yet when this middleware runs
  let playerId = req.body.playerId;

  if (!playerId) {
    // Parse numeric ID from URL path (e.g. "/7", "/current/7", "/opponents/7")
    const match = req.path.match(/\/(\d+)/);
    if (match) playerId = parseInt(match[1]);
  }

  if (!playerId) return next();

  const player = playerService.getRaw(playerId);
  if (!player) {
    return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });
  }

  // Allow access if player is unbound (legacy data) or owned by current user
  if (player.user_id !== null && player.user_id !== req.user.id) {
    return res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: '无权访问此玩家数据' } });
  }

  next();
}

module.exports = { authRequired, requireOwnPlayer };
