const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const inventoryService = require('../services/inventoryService');
const playerService = require('../services/playerService');

router.get('/:playerId', (req, res) => {
  try {
    const items = inventoryService.getInventory(Number(req.params.playerId));
    res.json({ success: true, data: { items } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.post('/use', (req, res) => {
  try {
    const { playerId, itemKey } = req.body;
    if (!playerId || !itemKey) return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    try {
      playerService.assertNotResting(Number(playerId), '使用道具');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ success: false, error: { code: 'PLAYER_RESTING', message: e.message } });
      }
      throw e;
    }
    const result = inventoryService.useItem(Number(playerId), itemKey);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    // Quest progress tracking
    try {
      const questService = require('../services/questService');
      questService.checkProgress(Number(playerId), 'use_item', { item_key: itemKey });
    } catch (e) { /* quest not critical */ }
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
