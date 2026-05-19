// 世界线偏移 API 路由 (Phase 2)
var express = require('express');
var router = express.Router();
var worldlineService = require('../services/worldlineService');

router.get('/status', function(req, res) {
  try {
    var ws = worldlineService.getWorldLineShift();
    var effects = worldlineService.getActiveThresholdEffects();
    res.json({
      success: true,
      data: {
        worldLineShift: ws.world_line_shift,
        rippleDecayRate: ws.ripple_decay_rate,
        lastDecayAt: ws.last_decay_at,
        activeEffects: effects
      }
    });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.get('/history', function(req, res) {
  try {
    var db = require('../db/database').getDb();
    var limit = parseInt(req.query.limit) || 20;
    var rows = db.prepare(
      'SELECT * FROM exploration_logs WHERE risks_json LIKE ? ORDER BY created_at DESC LIMIT ?'
    ).all('%worldLineShift%', limit);
    var history = rows.map(function(r) {
      var risks = JSON.parse(r.risks_json || '{}');
      return {
        playerId: r.player_id,
        location: r.location_key,
        worldLineShift: risks.worldLineShift || 0,
        event: r.event_name,
        createdAt: r.created_at
      };
    }).filter(function(h) { return h.worldLineShift !== 0; });
    res.json({ success: true, data: { history: history } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
