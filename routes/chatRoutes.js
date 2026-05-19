// 聊天频道路由
var express = require('express');
var router = express.Router();
var chatService = require('../services/chatService');
var { authRequired } = require('../middleware/authMiddleware');

// 发送消息 (需登录)
router.post('/send', authRequired, function(req, res) {
  try {
    var playerId = parseInt(req.body.playerId);
    var playerName = String(req.body.playerName || '').trim();
    var message = String(req.body.message || '');
    var channel = req.body.channel || 'global';

    if (!playerId) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少 playerId' });
    if (!playerName) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少 playerName' });

    var result = chatService.sendMessage(playerId, playerName, message, channel);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 获取最近消息
router.get('/messages/:channel', function(req, res) {
  try {
    var channel = req.params.channel || 'global';
    var limit = parseInt(req.query.limit) || 50;
    var sinceId = req.query.since ? parseInt(req.query.since) : null;
    var result = chatService.getRecentMessages(channel, limit, sinceId);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 获取活跃聊天者
router.get('/active/:channel', function(req, res) {
  try {
    var channel = req.params.channel || 'global';
    var minutes = parseInt(req.query.minutes) || 10;
    var result = chatService.getActiveChatters(channel, minutes);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
