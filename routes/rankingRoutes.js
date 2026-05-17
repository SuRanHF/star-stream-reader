const express = require('express');
const router = express.Router();
const rankingService = require('../services/rankingService');

router.get('/', (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 50;
    const rankings = rankingService.getRankings(limit);
    res.json({ rankings });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.get('/:playerId', (req, res) => {
  try {
    const rank = rankingService.getPlayerRank(Number(req.params.playerId));
    if (!rank) return res.json({ rank: null });
    res.json({ rank });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
