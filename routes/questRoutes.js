// 任务系统路由
var express = require('express');
var router = express.Router();
var questService = require('../services/questService');

// GET /api/quests/daily/:playerId — 获取每日任务
router.get('/daily/:playerId', function(req, res) {
  try {
    var playerId = parseInt(req.params.playerId);
    if (!playerId) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少 playerId' });
    var data = questService.getDailyQuests(playerId);
    res.json({ success: true, data: data });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// GET /api/quests/weekly/:playerId — 获取每周任务
router.get('/weekly/:playerId', function(req, res) {
  try {
    var playerId = parseInt(req.params.playerId);
    if (!playerId) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少 playerId' });
    var data = questService.getWeeklyQuests(playerId);
    res.json({ success: true, data: data });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// GET /api/quests/all/:playerId — 获取所有任务
router.get('/all/:playerId', function(req, res) {
  try {
    var playerId = parseInt(req.params.playerId);
    if (!playerId) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少 playerId' });
    var data = questService.getAllQuests(playerId);
    res.json({ success: true, data: data });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// POST /api/quests/claim/:playerId — 领取任务奖励
router.post('/claim/:playerId', function(req, res) {
  try {
    var playerId = parseInt(req.params.playerId);
    var questId = parseInt(req.body.questId);
    if (!playerId || !questId) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少参数' });
    var result = questService.claimQuestReward(playerId, questId);
    if (result.error) return res.status(400).json(result);
    res.json({ success: true, data: result.data });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
