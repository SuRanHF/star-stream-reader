const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const equipmentService = require('../services/equipmentService');
const playerService = require('../services/playerService');

router.get('/:playerId', (req, res) => {
  try {
    const equipped = equipmentService.getEquipment(Number(req.params.playerId));
    const available = equipmentService.getAvailableEquipment(Number(req.params.playerId));
    res.json({ equipped, available });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/equip', (req, res) => {
  try {
    const { playerId, equipmentKey, slot } = req.body;
    if (!playerId || !equipmentKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    try {
      playerService.assertNotResting(Number(playerId), '装备');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    const result = equipmentService.equipItem(Number(playerId), equipmentKey, slot);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/unequip', (req, res) => {
  try {
    const { playerId, slot } = req.body;
    if (!playerId || !slot) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    try {
      playerService.assertNotResting(Number(playerId), '卸下装备');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    const result = equipmentService.unequipItem(Number(playerId), slot);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
