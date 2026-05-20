const express = require('express');
const router = express.Router();

const { authRequired, requireOwnPlayer } = require('../middleware/authMiddleware');
const avatarRankService = require('../services/avatarRankService');

router.use(authRequired);

// 位阶排行榜 — 无需玩家所有权检查
router.get('/leaderboard', function(req, res) {
  try {
    var limit = parseInt(req.query.limit) || 50;
    var rankings = avatarRankService.getAvatarRankLeaderboard(limit);
    res.json({ success: true, data: { rankings } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 以下接口需玩家所有权
router.use(requireOwnPlayer);

router.get('/:playerId', function(req, res) {
  try {
    var result = avatarRankService.getPlayerAvatarRank(Number(req.params.playerId));
    if (result.error) return res.status(404).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.post('/:playerId/rank-up', function(req, res) {
  try {
    var result = avatarRankService.rankUp(Number(req.params.playerId));
    if (!result.success) {
      var s = result.error.code === 'RANK_REQUIREMENTS_NOT_MET' ? 400 : 409;
      return res.status(s).json({ success: false, error: result.error });
    }
    res.json({ success: true, data: result.data });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// Phase 6: 回归
router.post('/:playerId/prestige', function(req, res) {
  try {
    var playerId = parseInt(req.params.playerId);
    var result = avatarRankService.prestige(playerId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result.data });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
