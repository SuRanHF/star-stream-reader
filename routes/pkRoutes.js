const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const pkService = require('../services/pkService');
const playerService = require('../services/playerService');

router.get('/opponents/:playerId', (req, res) => {
  try {
    const opponents = pkService.getOpponents(Number(req.params.playerId));
    res.json({ success: true, data: { opponents } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// Create a pending PK challenge (attacker initiates, defender must accept/reject)
router.post('/challenge', (req, res) => {
  try {
    var attackerId = Number(req.body.attackerId);
    var defenderId = Number(req.body.defenderId);
    if (!attackerId || !defenderId) return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    try {
      playerService.assertNotResting(attackerId, '世界PK');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') return res.status(400).json({ success: false, error: { code: 'PLAYER_RESTING', message: e.message } });
      throw e;
    }
    if (!playerService.isPlayerOnline(defenderId)) {
      return res.status(400).json({ success: false, error: { code: 'PLAYER_OFFLINE', message: '对方不在线，无法挑战' } });
    }
    var result = pkService.createChallenge(attackerId, defenderId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// Resolve a pending PK challenge (defender accepts or rejects)
router.post('/challenge/resolve', (req, res) => {
  try {
    var challengeId = Number(req.body.challengeId);
    var playerId = Number(req.body.playerId);
    var accept = req.body.accept === true;
    if (!challengeId || !playerId) return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    pkService.expireOldChallenges();
    var result = pkService.resolveChallenge(challengeId, accept, playerId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.get('/rankings', (req, res) => {
  try {
    const rankings = pkService.getRankings();
    res.json({ success: true, data: { rankings } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.get('/records/:playerId', (req, res) => {
  try {
    const records = pkService.getPKRecords(Number(req.params.playerId));
    res.json({ success: true, data: { records } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
