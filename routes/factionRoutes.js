// 星座阵营 API 路由 (Phase 3)
var express = require('express');
var router = express.Router();
var factionService = require('../services/factionService');

// 所有阵营列表
router.get('/', function(req, res) {
  try {
    var factions = factionService.getAllFactions();
    res.json({ success: true, data: factions });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 阵营排行榜
router.get('/leaderboard', function(req, res) {
  try {
    var leaderboard = factionService.getFactionLeaderboard();
    res.json({ success: true, data: leaderboard });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 当前周常阵营战
router.get('/war/current', function(req, res) {
  try {
    var war = factionService.getCurrentWeeklyWar();
    res.json({ success: true, data: war });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 玩家所属阵营
router.get('/my/:playerId', function(req, res) {
  try {
    var playerId = parseInt(req.params.playerId);
    if (isNaN(playerId)) {
      return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: 'Invalid player ID' } });
    }
    var faction = factionService.getPlayerFaction(playerId);
    if (!faction) return res.json({ success: true, data: null });
    res.json({ success: true, data: faction });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 阵营详情
router.get('/:constellationKey', function(req, res) {
  try {
    var faction = factionService.getFactionDetail(req.params.constellationKey);
    if (!faction) return res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: '阵营不存在' } });
    res.json({ success: true, data: faction });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 阵营成员列表
router.get('/:constellationKey/members', function(req, res) {
  try {
    var members = factionService.getFactionMembers(req.params.constellationKey, parseInt(req.query.limit) || 50);
    res.json({ success: true, data: members });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
