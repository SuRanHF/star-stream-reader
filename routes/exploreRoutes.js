const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const exploreService = require('../services/exploreService');
const recoveryService = require('../services/recoveryService');
const playerService = require('../services/playerService');

router.get('/locations/:playerId', (req, res) => {
  try {
    recoveryService.applyPassiveRecovery(Number(req.params.playerId));
    const locations = exploreService.getUnlockedLocations(Number(req.params.playerId));
    res.json({ locations });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/start', (req, res) => {
  try {
    const { playerId, locationKey } = req.body;
    if (!playerId || !locationKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    recoveryService.applyPassiveRecovery(Number(playerId));
    try {
      playerService.assertNotResting(Number(playerId), '探索');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    const result = exploreService.startExploration(Number(playerId), locationKey);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
