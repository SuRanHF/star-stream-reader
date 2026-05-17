const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const pkService = require('../services/pkService');
const playerService = require('../services/playerService');

router.get('/opponents/:playerId', (req, res) => {
  try {
    const opponents = pkService.getOpponents(Number(req.params.playerId));
    res.json({ opponents });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/challenge', (req, res) => {
  try {
    const { attackerId, defenderId } = req.body;
    if (!attackerId || !defenderId) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    try {
      playerService.assertNotResting(Number(attackerId), '世界PK');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    const result = pkService.challenge(Number(attackerId), Number(defenderId));
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.get('/rankings', (req, res) => {
  try {
    const rankings = pkService.getRankings();
    res.json({ rankings });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.get('/records/:playerId', (req, res) => {
  try {
    const records = pkService.getPKRecords(Number(req.params.playerId));
    res.json({ records });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
