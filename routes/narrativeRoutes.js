// 碎片化叙事 API 路由 (Phase 5)
var express = require('express');
var router = express.Router();
var narrativeService = require('../services/narrativeService');

// 获取物品记忆
router.get('/item-memories/:itemKey', function(req, res) {
  try {
    var memories = narrativeService.getItemMemories(req.params.itemKey);
    res.json({ success: true, data: memories });
  } catch (e) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 获取地点回响
router.get('/location-echoes/:locationKey', function(req, res) {
  try {
    var echoes = narrativeService.getLocationEchoes(req.params.locationKey);
    res.json({ success: true, data: echoes });
  } catch (e) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 检查NPC残影遭遇
router.get('/ghost-check/:playerId/:locationKey', function(req, res) {
  try {
    var playerId = parseInt(req.params.playerId);
    if (isNaN(playerId)) {
      return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: 'Invalid player ID' } });
    }
    var ghost = narrativeService.checkNpcGhostEncounter(playerId, req.params.locationKey);
    res.json({ success: true, data: ghost });
  } catch (e) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 处理NPC残影对话
router.post('/ghost-encounter', function(req, res) {
  try {
    var playerId = parseInt(req.body.playerId);
    var ghostKey = req.body.ghostKey;
    var nodeIndex = parseInt(req.body.nodeIndex) || 0;
    var choiceIndex = parseInt(req.body.choiceIndex) || 0;

    if (!playerId || !ghostKey) {
      return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    }

    var result = narrativeService.processNpcGhostEncounter(playerId, ghostKey, nodeIndex, choiceIndex);
    if (result && result.error) {
      return res.status(404).json({ success: false, error: { code: result.error.code || result.error, message: result.error.message || '' } });
    }
    res.json({ success: true, data: result });
  } catch (e) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 玩家遭遇历史
router.get('/encounters/:playerId', function(req, res) {
  try {
    var playerId = parseInt(req.params.playerId);
    if (isNaN(playerId)) {
      return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: 'Invalid player ID' } });
    }
    var encounters = narrativeService.getPlayerEncounters(playerId, parseInt(req.query.limit) || 20);
    res.json({ success: true, data: encounters });
  } catch (e) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
