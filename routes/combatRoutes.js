const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const combatService = require('../services/combatService');
const playerService = require('../services/playerService');

router.post('/simulate', (req, res) => {
  try {
    const { playerId, monsterKey } = req.body;
    if (!playerId || !monsterKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    const player = playerService.get(Number(playerId));
    if (!player) return res.status(400).json({ code: 'PLAYER_NOT_FOUND', message: '玩家不存在' });
    const result = combatService.simulateBattle(player, monsterKey, {});
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/resolve', (req, res) => {
  try {
    const { playerId, monsterKey, action } = req.body;
    if (!playerId || !monsterKey || !action) {
      return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    }
    if (!['fight', 'flee', 'support'].includes(action)) {
      return res.status(400).json({ success: false, error: { code: 'INVALID_ACTION', message: '无效的战斗行动' } });
    }
    const result = combatService.resolveCombat(Number(playerId), monsterKey, action);
    if (result.error) {
      return res.status(400).json({ success: false, error: result.error });
    }
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
