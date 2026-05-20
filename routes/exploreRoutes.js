const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const exploreService = require('../services/exploreService');
const recoveryService = require('../services/recoveryService');
const playerService = require('../services/playerService');

router.get('/locations/:playerId', (req, res) => {
  try {
    const playerId = parseInt(req.params.playerId);
    if (isNaN(playerId)) return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: '无效的玩家ID' } });
    recoveryService.applyPassiveRecovery(playerId);
    const locations = exploreService.getUnlockedLocations(playerId);
    res.json({ success: true, data: { locations } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.post('/start', (req, res) => {
  try {
    const { playerId, locationKey, firstExplore } = req.body;
    if (!playerId || !locationKey) return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    const pid = parseInt(playerId);
    if (isNaN(pid)) return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: '无效的玩家ID' } });
    recoveryService.applyPassiveRecovery(pid);
    try {
      playerService.assertNotResting(pid, '探索');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ success: false, error: { code: 'PLAYER_RESTING', message: e.message } });
      }
      throw e;
    }
    const result = exploreService.startExploration(pid, locationKey, { firstExplore: !!firstExplore });
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
