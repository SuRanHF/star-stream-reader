const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const chapterService = require('../services/chapterService');
const playerService = require('../services/playerService');

router.get('/status/:playerId', (req, res) => {
  try {
    const result = chapterService.getChapterStatus(Number(req.params.playerId));
    if (!result.success) return res.status(400).json(result.error);
    res.json(result.data);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/advance', (req, res) => {
  try {
    const { playerId, chapterKey } = req.body;
    if (!playerId || !chapterKey) return res.json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    try {
      playerService.assertNotResting(Number(playerId), '阶段推进');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.json({ success: false, error: { code: 'PLAYER_RESTING', message: e.message } });
      }
      throw e;
    }
    const result = chapterService.advanceStage(Number(playerId), chapterKey);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.post('/breakthrough', (req, res) => {
  try {
    const { playerId, chapterKey } = req.body;
    if (!playerId || !chapterKey) return res.json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    try {
      playerService.assertNotResting(Number(playerId), '阶段推进');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.json({ success: false, error: { code: 'PLAYER_RESTING', message: e.message } });
      }
      throw e;
    }
    const result = chapterService.advanceStage(Number(playerId), chapterKey);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.get('/resources/:playerId', (req, res) => {
  try {
    const resources = chapterService.getResources(Number(req.params.playerId));
    if (!resources) return res.status(400).json({ code: 'PLAYER_NOT_FOUND', message: '玩家不存在' });
    res.json({ resources });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
