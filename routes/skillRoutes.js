const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const skillService = require('../services/skillService');
const playerService = require('../services/playerService');

router.get('/:playerId', (req, res) => {
  try {
    const skills = skillService.getAllSkills(Number(req.params.playerId));
    res.json({ skills });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/unlock', (req, res) => {
  try {
    const { playerId, skillKey } = req.body;
    if (!playerId || !skillKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    try {
      playerService.assertNotResting(Number(playerId), '解锁技能');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    const result = skillService.unlockSkill(Number(playerId), skillKey);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
