const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const storyService = require('../services/storyService');
const recoveryService = require('../services/recoveryService');
const playerService = require('../services/playerService');

router.get('/current/:playerId', (req, res) => {
  try {
    recoveryService.applyPassiveRecovery(Number(req.params.playerId));
    const result = storyService.getCurrentChapter(Number(req.params.playerId));
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/choose', (req, res) => {
  try {
    const { playerId, choiceKey } = req.body;
    if (!playerId || !choiceKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    recoveryService.applyPassiveRecovery(Number(playerId));
    // assertNotResting throws on rest — catch and return structured error
    try {
      playerService.assertNotResting(Number(playerId), '剧情选择');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    const result = storyService.applyChoice(Number(playerId), choiceKey);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
