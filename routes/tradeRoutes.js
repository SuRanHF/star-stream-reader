// 交易系统 API 路由 (Phase 4)
var express = require('express');
var router = express.Router();
var tradeService = require('../services/tradeService');
var { requireOwnPlayer } = require('../middleware/authMiddleware');

// 获取所有活跃挂单
router.get('/listings', function(req, res) {
  try {
    var itemType = req.query.type || null;
    var listings = tradeService.getActiveListings(itemType);
    res.json({ success: true, data: listings });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// Player ownership check for all subsequent routes
router.use(requireOwnPlayer);

// 获取玩家自己的挂单
router.get('/listings/my/:playerId', function(req, res) {
  try {
    var listings = tradeService.getPlayerListings(parseInt(req.params.playerId));
    res.json({ success: true, data: listings });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 创建挂单
router.post('/listings', function(req, res) {
  try {
    var result = tradeService.createListing(
      req.body.sellerId, req.body.itemKey, req.body.itemType, req.body.quantity, req.body.price
    );
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 购买挂单
router.post('/listings/:id/buy', function(req, res) {
  try {
    var result = tradeService.buyListing(parseInt(req.params.id), req.body.buyerId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 取消挂单
router.post('/listings/:id/cancel', function(req, res) {
  try {
    var result = tradeService.cancelListing(parseInt(req.params.id), req.body.playerId);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
