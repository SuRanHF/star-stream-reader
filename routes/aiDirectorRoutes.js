// AI 导演路由 (Round 6)
const express = require('express');
const router = express.Router();
const aiDirectorService = require('../services/aiDirectorService');
const broadcastService = require('../services/broadcastService');
const worldStateService = require('../services/worldStateService');

// 生成星流放送草案
router.post('/generate-broadcast', async (req, res) => {
  try {
    const worldState = worldStateService.getWorldStateSummary();
    const draft = await aiDirectorService.generateBroadcastDraft(worldState);

    // 校验
    const validation = broadcastService.validateBroadcastDraft(draft);
    if (!validation.valid) {
      return res.status(400).json({
        code: 'DRAFT_VALIDATION_FAILED',
        message: 'AI 生成的草案未通过校验',
        errors: validation.errors,
        draft
      });
    }

    // 保存为 draft
    const result = broadcastService.createDraft(draft);
    if (result.error) {
      return res.status(400).json(result.error);
    }

    res.json({
      success: true,
      message: '星流放送草案已生成',
      data: result.data,
      validation: { passed: true }
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 激活放送
router.post('/:eventId/activate', (req, res) => {
  try {
    const eventId = parseInt(req.params.eventId);
    const result = broadcastService.activateEvent(eventId);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 结算放送
router.post('/:eventId/resolve', (req, res) => {
  try {
    const eventId = parseInt(req.params.eventId);
    const { success: isSuccess } = req.body;
    const result = broadcastService.resolveEvent(eventId, !!isSuccess);
    if (result.error) return res.status(400).json(result.error);
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 取消放送
router.post('/:eventId/cancel', (req, res) => {
  try {
    const { getDb } = require('../db/database');
    const db = getDb();
    const eventId = parseInt(req.params.eventId);
    const event = db.prepare('SELECT * FROM broadcast_events WHERE id = ?').get(eventId);
    if (!event) return res.status(404).json({ code: 'NOT_FOUND', message: '放送不存在' });
    if (event.status === 'active' || event.status === 'draft') {
      db.prepare(`UPDATE broadcast_events SET status = 'cancelled', updated_at = datetime('now','localtime') WHERE id = ?`).run(eventId);
      return res.json({ success: true, message: '放送已取消' });
    }
    res.status(400).json({ code: 'INVALID_STATUS', message: `无法取消状态为 ${event.status} 的放送` });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 获取世界状态（调试用）
router.get('/world-state', (req, res) => {
  try {
    const state = worldStateService.getWorldStateSummary();
    res.json({ success: true, data: state });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

module.exports = router;
