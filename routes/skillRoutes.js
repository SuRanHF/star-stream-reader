const express = require('express');
const router = express.Router();

const skillService = require('../services/skillService');
const playerService = require('../services/playerService');
const { requireOwnPlayer } = require('../middleware/authMiddleware');

// All routes require player ownership
router.use(requireOwnPlayer);

router.get('/:playerId', (req, res) => {
  try {
    var skills = skillService.getAllSkills(Number(req.params.playerId));
    res.json({ skills });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/unlock', (req, res) => {
  try {
    var { playerId, skillKey } = req.body;
    if (!playerId || !skillKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    try {
      playerService.assertNotResting(Number(playerId), '解锁技能');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    var result = skillService.unlockSkill(Number(playerId), skillKey);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// ===== 星座阵营技能 =====

// GET /api/skills/faction/:constellationKey — get all faction skills for a constellation (public catalog)
router.get('/faction/:constellationKey', (req, res) => {
  try {
    var skills = skillService.getFactionSkills(req.params.constellationKey);
    res.json({ skills });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// GET /api/skills/faction/player/:playerId — get faction skills with player's unlock status
router.get('/faction/player/:playerId', (req, res) => {
  try {
    var skills = skillService.getFactionSkillsForPlayer(Number(req.params.playerId));
    res.json({ skills });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// POST /api/skills/faction/learn — learn a faction skill
router.post('/faction/learn', (req, res) => {
  try {
    var { playerId, skillKey } = req.body;
    if (!playerId || !skillKey) return res.status(400).json({ code: 'MISSING_PARAMS', message: '缺少必要参数' });
    try {
      playerService.assertNotResting(Number(playerId), '学习阵营技能');
    } catch (e) {
      if (e.code === 'PLAYER_RESTING') {
        return res.status(400).json({ code: 'PLAYER_RESTING', message: e.message });
      }
      throw e;
    }
    var result = skillService.learnFactionSkill(Number(playerId), skillKey);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
