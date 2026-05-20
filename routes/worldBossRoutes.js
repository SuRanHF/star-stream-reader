const express = require('express');
const router = express.Router();
const { authRequired } = require('../middleware/authMiddleware');

const worldBossService = require('../services/worldBossService');

// Public: get active boss status
router.get('/status', (req, res) => {
  try {
    var boss = worldBossService.getActiveBoss();
    var ranking = boss ? worldBossService.getBossRanking(boss.id) : [];
    res.json({ active: boss, ranking: ranking.slice(0, 20) });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Public: get boss ranking
router.get('/ranking/:bossId', (req, res) => {
  try {
    var ranking = worldBossService.getBossRanking(Number(req.params.bossId));
    res.json({ ranking });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Public: get boss history
router.get('/history', (req, res) => {
  try {
    var history = worldBossService.getBossHistory(Number(req.query.limit) || 10);
    res.json({ history });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Auth required: fight the world boss
router.post('/fight', authRequired, (req, res) => {
  try {
    var { playerId, action } = req.body;
    if (!playerId) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    var result = worldBossService.fightBoss(Number(playerId), action || 'fight');
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// Auth required: get player's contribution to active boss
router.get('/my-contribution/:playerId', authRequired, (req, res) => {
  try {
    var boss = worldBossService.getActiveBoss();
    if (!boss) return res.json({ contribution: null });
    var contrib = worldBossService.getMyBossContribution(Number(req.params.playerId), boss.id);
    res.json({ contribution: contrib || null });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
