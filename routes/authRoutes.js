const express = require('express');
const router = express.Router();
const authService = require('../services/authService');
const playerService = require('../services/playerService');
const { authRequired } = require('../middleware/authMiddleware');

// POST /api/auth/register
router.post('/register', (req, res) => {
  try {
    const { username, email, password } = req.body;
    const result = authService.register(username, email, password);
    if (result.error) {
      return res.status(400).json({ success: false, error: result.error });
    }
    res.json({ success: true, data: { user: result.user, token: result.token } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// POST /api/auth/login
router.post('/login', (req, res) => {
  try {
    const { usernameOrEmail, password } = req.body;
    const result = authService.login(usernameOrEmail, password);
    if (result.error) {
      const status = result.error.code === 'INVALID_CREDENTIALS' ? 401 : 400;
      return res.status(status).json({ success: false, error: result.error });
    }
    res.json({ success: true, data: { user: result.user, token: result.token } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// GET /api/auth/me
router.get('/me', authRequired, (req, res) => {
  try {
    const result = authService.getMe(req.user.id);

    // Also look for any player created by legacy code (no user_id) and bind it
    if (result && !result.player) {
      const legacyPlayer = playerService.getRaw(null);
      // Can't find by null user_id easily, skip for now
    }

    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// POST /api/auth/logout
router.post('/logout', (req, res) => {
  // Stateless JWT — client deletes token
  res.json({ success: true, data: { message: '已退出登录' } });
});

// POST /api/auth/create-player — create player bound to current user
router.post('/create-player', authRequired, (req, res) => {
  try {
    const { playerName } = req.body;

    // Check if user already has a player
    const existing = authService.getMe(req.user.id);
    if (existing && existing.player) {
      return res.status(400).json({ success: false, error: { code: 'ALREADY_HAS_PLAYER', message: '该账号已绑定角色' } });
    }

    const player = playerService.create(playerName, req.user.id);
    res.json({ success: true, data: { player } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
