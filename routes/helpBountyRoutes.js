// 悬赏求助路由
const express = require('express');
const router = express.Router();
const { authRequired } = require('../middleware/authMiddleware');
const bountyService = require('../services/helpBountyService');

// Publish a help bounty to world channel
router.post('/publish', authRequired, (req, res) => {
  try {
    var playerId = Number(req.body.playerId);
    var monsterKey = req.body.monsterKey || '';
    var locationKey = req.body.locationKey || '';
    var monsterName = req.body.monsterName || '';
    var sharePercent = Number(req.body.sharePercent) || 50;
    var combatRewards = req.body.combatRewards || {};

    if (!playerId || !monsterKey) {
      return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    }

    var result = bountyService.publishBounty(playerId, monsterKey, locationKey, monsterName, sharePercent, combatRewards);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Accept a bounty (helper clicks "前往相助")
router.post('/accept/:bountyId', authRequired, (req, res) => {
  try {
    var bountyId = Number(req.params.bountyId);
    var helperId = Number(req.body.playerId);

    if (!bountyId || !helperId) {
      return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    }

    var result = bountyService.acceptBounty(bountyId, helperId);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Get pending bounties
router.get('/pending', (req, res) => {
  try {
    var bounties = bountyService.getPendingBounties();
    res.json({ success: true, data: bounties });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Get my active bounty
router.get('/my/:playerId', (req, res) => {
  try {
    var bounty = bountyService.getMyActiveBounty(Number(req.params.playerId));
    res.json({ success: true, data: bounty || null });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Cancel bounty
router.post('/cancel', authRequired, (req, res) => {
  try {
    var playerId = Number(req.body.playerId);
    if (!playerId) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少 playerId' });
    var result = bountyService.cancelBounty(playerId);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Get daily limits
router.get('/daily-limits/:playerId', (req, res) => {
  try {
    var counts = bountyService.getDailyCounts(Number(req.params.playerId));
    res.json({ success: true, data: counts });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
