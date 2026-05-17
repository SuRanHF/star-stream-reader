const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const titleService = require('../services/titleService');

router.get('/:playerId', (req, res) => {
  try {
    const unlocked = titleService.getUnlocked(Number(req.params.playerId));
    res.json({ titles: unlocked });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
