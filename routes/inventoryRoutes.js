const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
const inventoryService = require('../services/inventoryService');
const playerService = require('../services/playerService');

// Public route — synthesis recipes (no auth needed)
router.get('/synthesis/recipes', (req, res) => {
  try {
    var recipes = inventoryService.getSynthesisRecipes();
    res.json({ recipes });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Auth required for all other routes
router.use(requireOwnPlayer);

router.get('/:playerId', (req, res) => {
  try {
    const items = inventoryService.getInventory(Number(req.params.playerId));
    res.json({ items });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/use', (req, res) => {
  try {
    const { playerId, itemKey } = req.body;
    if (!playerId || !itemKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    try {
      playerService.assertNotResting(Number(playerId), '使用道具');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    const result = inventoryService.useItem(Number(playerId), itemKey);
    if (result.error) return res.status(400).json(result.error);
    // Quest progress tracking
    try {
      const questService = require('../services/questService');
      questService.checkProgress(Number(playerId), 'use_item', { item_key: itemKey });
    } catch (e) { /* quest not critical */ }
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Batch sell items
router.post('/sell-batch', (req, res) => {
  try {
    const { playerId, items } = req.body;
    if (!playerId || !items || !Array.isArray(items) || items.length === 0) {
      return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    }
    const result = inventoryService.sellBatch(Number(playerId), items);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Batch use items (multiple of same consumable)
router.post('/use-batch', (req, res) => {
  try {
    const { playerId, itemKey, quantity } = req.body;
    if (!playerId || !itemKey || !quantity) {
      return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    }
    try {
      playerService.assertNotResting(Number(playerId), '使用道具');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    const result = inventoryService.useBatch(Number(playerId), itemKey, Number(quantity));
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// POST /api/inventory/synthesis — synthesize items
router.post('/synthesis', (req, res) => {
  try {
    var { playerId, recipeKey } = req.body;
    if (!playerId || !recipeKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    var result = inventoryService.synthesize(Number(playerId), recipeKey);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// POST /api/inventory/synthesis-all — synthesize all possible
router.post('/synthesis-all', (req, res) => {
  try {
    var { playerId, recipeKey } = req.body;
    if (!playerId || !recipeKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    var result = inventoryService.synthesizeAll(Number(playerId), recipeKey);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
