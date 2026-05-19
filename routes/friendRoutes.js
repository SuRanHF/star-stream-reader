// 好友系统路由
var express = require('express');
var router = express.Router();
var friendService = require('../services/friendService');
var { authRequired } = require('../middleware/authMiddleware');

router.use(authRequired);

// 获取好友列表
router.get('/list/:playerId', function(req, res) {
  try {
    var result = friendService.getFriendList(Number(req.params.playerId));
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 获取待处理的好友申请
router.get('/requests/:playerId', function(req, res) {
  try {
    var result = friendService.getPendingRequests(Number(req.params.playerId));
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 发送好友申请
router.post('/request', function(req, res) {
  try {
    var playerId = parseInt(req.body.playerId);
    var friendId = parseInt(req.body.friendId);
    if (!playerId || !friendId) {
      return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少 playerId 或 friendId' });
    }
    var result = friendService.sendFriendRequest(playerId, friendId);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 接受好友申请
router.post('/accept', function(req, res) {
  try {
    var playerId = parseInt(req.body.playerId);
    var requestId = parseInt(req.body.requestId);
    if (!playerId || !requestId) {
      return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少 playerId 或 requestId' });
    }
    var result = friendService.acceptFriendRequest(playerId, requestId);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 拒绝好友申请
router.post('/decline', function(req, res) {
  try {
    var playerId = parseInt(req.body.playerId);
    var requestId = parseInt(req.body.requestId);
    if (!playerId || !requestId) {
      return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少 playerId 或 requestId' });
    }
    var result = friendService.declineFriendRequest(playerId, requestId);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 赠送礼物
router.post('/gift', function(req, res) {
  try {
    var playerId = parseInt(req.body.playerId);
    var targetId = parseInt(req.body.targetId);
    var itemKey = req.body.itemKey;
    if (!playerId || !targetId || !itemKey) {
      return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少参数' });
    }
    var result = friendService.sendGift(playerId, targetId, itemKey);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 最近互动
router.get('/recent/:playerId', function(req, res) {
  try {
    var result = friendService.getRecentInteractions(Number(req.params.playerId));
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 删除好友
router.post('/remove', function(req, res) {
  try {
    var playerId = parseInt(req.body.playerId);
    var friendId = parseInt(req.body.friendId);
    if (!playerId || !friendId) {
      return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少 playerId 或 friendId' });
    }
    var result = friendService.removeFriend(playerId, friendId);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
