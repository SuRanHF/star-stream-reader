// 组队系统 API 路由 (Phase 4)
var express = require('express');
var router = express.Router();
var partyService = require('../services/partyService');

// 获取所有活跃队伍
router.get('/', function(req, res) {
  try {
    var parties = partyService.getActiveParties();
    res.json({ success: true, data: parties });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 获取玩家所在队伍
router.get('/my/:playerId', function(req, res) {
  try {
    var party = partyService.getPlayerParty(parseInt(req.params.playerId));
    res.json({ success: true, data: party });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 创建队伍
router.post('/create', function(req, res) {
  try {
    var result = partyService.createParty(req.body.leaderId, req.body.bossKey);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 加入队伍
router.post('/:id/join', function(req, res) {
  try {
    var result = partyService.joinParty(parseInt(req.params.id), req.body.playerId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 离开队伍
router.post('/:id/leave', function(req, res) {
  try {
    var result = partyService.leaveParty(parseInt(req.params.id), req.body.playerId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 准备/取消准备
router.post('/:id/ready', function(req, res) {
  try {
    var result = partyService.setReady(parseInt(req.params.id), req.body.playerId, req.body.ready);
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 发起讨伐
router.post('/:id/start-battle', function(req, res) {
  try {
    var result = partyService.startPartyBossBattle(parseInt(req.params.id), req.body.playerId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
