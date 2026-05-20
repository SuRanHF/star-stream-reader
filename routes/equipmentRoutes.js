const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const equipmentService = require('../services/equipmentService');
const playerService = require('../services/playerService');

// ===== 装备套装 (must be before /:playerId to avoid conflict) =====

// GET /api/equipment/sets — get all equipment sets
router.get('/sets', (req, res) => {
  try {
    var sets = equipmentService.getAllSets().map(function(s) {
      return {
        set_key: s.set_key,
        set_name: s.set_name,
        total_pieces: JSON.parse(s.pieces_json || '[]').length,
        pieces: JSON.parse(s.pieces_json || '[]'),
        bonuses: JSON.parse(s.bonuses_json || '[]')
      };
    });
    res.json({ sets });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// GET /api/equipment/sets/my/:playerId — get player's active set bonuses
router.get('/sets/my/:playerId', (req, res) => {
  try {
    var info = equipmentService.getActiveSetInfo(Number(req.params.playerId));
    var bonuses = equipmentService.getActiveSetBonuses(Number(req.params.playerId));
    res.json({ active_sets: info, bonuses: bonuses });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// GET /api/equipment/:playerId
router.get('/:playerId', (req, res) => {
  try {
    var data = equipmentService.getEquipmentWithSets(Number(req.params.playerId));
    var available = equipmentService.getAvailableEquipment(Number(req.params.playerId));
    res.json({
      equipped: data.equipped,
      available: available,
      set_bonuses: data.set_bonuses,
      active_sets: data.active_sets
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/equip', (req, res) => {
  try {
    var { playerId, equipmentKey, slot } = req.body;
    if (!playerId || !equipmentKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    try {
      playerService.assertNotResting(Number(playerId), '装备');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    var result = equipmentService.equipItem(Number(playerId), equipmentKey, slot);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/unequip', (req, res) => {
  try {
    var { playerId, slot } = req.body;
    if (!playerId || !slot) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    try {
      playerService.assertNotResting(Number(playerId), '卸下装备');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    var result = equipmentService.unequipItem(Number(playerId), slot);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// POST /api/equipment/repair — repair a single slot
router.post('/repair', (req, res) => {
  try {
    var { playerId, slot } = req.body;
    if (!playerId || !slot) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    var result = equipmentService.repairItem(Number(playerId), slot);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// POST /api/equipment/repair-all — repair all equipped items
router.post('/repair-all', (req, res) => {
  try {
    var { playerId } = req.body;
    if (!playerId) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    var result = equipmentService.repairAll(Number(playerId));
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
