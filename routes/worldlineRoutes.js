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
    var minShift = parseFloat(req.query.minShift) || 0.5;
    // worldLineShift is not stored in exploration_logs; retrieve recent logs and check
    var rows = db.prepare(
      'SELECT * FROM exploration_logs ORDER BY created_at DESC LIMIT ?'
    ).all(limit * 5);
    var history = [];
    for (var i = 0; i < rows.length && history.length < limit; i++) {
      var r = rows[i];
      var data = JSON.parse(r.result_json || '{}');
      var shift = data.risks && data.risks.worldLineShift
        ? data.risks.worldLineShift
        : (data.worldLineShift || 0);
      if (Math.abs(shift) >= minShift) {
        history.push({
          playerId: r.player_id,
          location: r.location_key,
          worldLineShift: shift,
          event: data.event_name || r.result_type,
          createdAt: r.created_at
        });
      }
    }
    res.json({ success: true, data: { history: history } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
