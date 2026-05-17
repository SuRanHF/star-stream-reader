const express = require('express');
const router = express.Router();
const feedbackStore = require('../utils/feedbackStore');

// POST /api/feedback — public, no auth required
router.post('/', (req, res) => {
  try {
    const { nickname, type, content, page, playerId } = req.body;
    if (!content || !content.trim()) {
      return res.status(400).json({ success: false, error: { code: 'MISSING_CONTENT', message: '请输入反馈内容' } });
    }
    const validTypes = ['bug', 'suggestion', 'experience', 'other'];
    const fbType = validTypes.includes(type) ? type : 'other';

    const entry = feedbackStore.add({
      nickname: (nickname || '').trim().substring(0, 50),
      type: fbType,
      content: content.trim().substring(0, 2000),
      page: (page || '').trim().substring(0, 100),
      playerId: playerId || null
    });

    res.json({ success: true, data: { id: entry.id, message: '感谢你的反馈！' } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
