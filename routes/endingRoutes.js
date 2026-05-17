const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const endingService = require('../services/endingService');

router.get('/:playerId', (req, res) => {
  try {
    const endings = endingService.checkEndings(Number(req.params.playerId));
    res.json({ endings });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
