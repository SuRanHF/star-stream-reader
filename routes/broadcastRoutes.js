// 星流放送路由 (Round 6)
const express = require('express');
const router = express.Router();
const broadcastService = require('../services/broadcastService');
const playerService = require('../services/playerService');
const { authRequired, requireOwnPlayer } = require('../middleware/authMiddleware');

// 获取当前活跃放送
router.get('/active', (req, res) => {
  try {
    const active = broadcastService.getActiveBroadcasts();
    const withProgress = active.map(e => {
      const progress = broadcastService.getEventProgress(e.id);
      return { ...e, progress: progress.success ? progress.data : null };
    });
    res.json({ success: true, data: withProgress });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 获取历史放送
router.get('/history', (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 20;
    const history = broadcastService.getHistoryBroadcasts(limit);
    res.json({ success: true, data: history });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 玩家参加放送 (需登录)
router.post('/:eventId/join', authRequired, requireOwnPlayer, (req, res) => {
  try {
    const eventId = parseInt(req.params.eventId);
    if (isNaN(eventId)) return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: '无效的 eventId' } });
    const { playerId } = req.body;
    if (!playerId) return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少 playerId' } });

    try {
      playerService.assertNotResting(Number(playerId), '参加星流放送');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ success: false, error: { code: 'PLAYER_RESTING', message: e.message } });
      }
      throw e;
    }

    const result = broadcastService.joinEvent(eventId, playerId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 查询放送进度
router.get('/:eventId/progress', (req, res) => {
  try {
    const eventId = parseInt(req.params.eventId);
    if (isNaN(eventId)) return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: '无效的 eventId' } });
    const result = broadcastService.getEventProgress(eventId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 查询我的贡献 (需登录)
router.get('/:eventId/my-contribution/:playerId', authRequired, (req, res) => {
  try {
    const eventId = parseInt(req.params.eventId);
    if (isNaN(eventId)) return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: '无效的 eventId' } });
    const playerId = parseInt(req.params.playerId);
    if (isNaN(playerId)) return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: '无效的 playerId' } });
    const result = broadcastService.getPlayerContribution(eventId, playerId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 领取奖励 (需登录)
router.post('/:eventId/claim', authRequired, requireOwnPlayer, (req, res) => {
  try {
    const eventId = parseInt(req.params.eventId);
    if (isNaN(eventId)) return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: '无效的 eventId' } });
    const { playerId } = req.body;
    if (!playerId) return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少 playerId' } });

    try {
      playerService.assertNotResting(Number(playerId), '领取放送奖励');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ success: false, error: { code: 'PLAYER_RESTING', message: e.message } });
      }
      throw e;
    }

    const result = broadcastService.claimReward(eventId, playerId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 贡献排行（按事件）
router.get('/:eventId/ranking', (req, res) => {
  try {
    const eventId = parseInt(req.params.eventId);
    if (isNaN(eventId)) return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: '无效的 eventId' } });
    const limit = parseInt(req.query.limit) || 20;
    const ranking = broadcastService.getContributionRanking(eventId, limit);
    res.json({ success: true, data: ranking });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 全服贡献总榜
router.get('/leaderboard', (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 50;
    const rankings = broadcastService.getGlobalContributionLeaderboard(limit);
    res.json({ success: true, data: rankings });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 提交资源 (需登录)
router.post('/:eventId/submit-resource', authRequired, requireOwnPlayer, (req, res) => {
  try {
    const eventId = parseInt(req.params.eventId);
    if (isNaN(eventId)) return res.status(400).json({ success: false, error: { code: 'INVALID_INPUT', message: '无效的 eventId' } });
    const { playerId, resourceType, amount } = req.body;
    if (!playerId || !resourceType || !amount) {
      return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少 playerId/resourceType/amount' } });
    }

    try {
      playerService.assertNotResting(Number(playerId), '提交放送资源');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ success: false, error: { code: 'PLAYER_RESTING', message: e.message } });
      }
      throw e;
    }

    broadcastService.recordContribution(eventId, playerId, 'submit_resource', amount, { resourceType });
    res.json({ success: true, data: { submitted: { resourceType, amount } } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
