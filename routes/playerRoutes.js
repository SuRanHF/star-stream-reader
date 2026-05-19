const express = require('express');
const router = express.Router();

const { requireOwnPlayer } = require('../middleware/authMiddleware');
router.use(requireOwnPlayer);

const playerService = require('../services/playerService');
const recoveryService = require('../services/recoveryService');

router.post('/create', (req, res) => {
  try {
    const { playerName } = req.body;
    const userId = req.user ? req.user.id : null;
    const player = playerService.create(playerName, userId);
    res.json({ success: true, data: { player } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 背后星
router.get('/constellations', (req, res) => {
  try {
    const list = playerService.getConstellations();
    res.json({ success: true, data: { constellations: list } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.post('/select-constellation', (req, res) => {
  try {
    const { playerId, constellationKey } = req.body;
    if (!playerId || !constellationKey) {
      return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    }
    const result = playerService.selectConstellation(Number(playerId), constellationKey);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 冥界 - 获取已死亡玩家列表
router.get('/dead-list', (req, res) => {
  try {
    const list = playerService.getDeadPlayers();
    res.json({ success: true, data: list });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.get('/:id', (req, res) => {
  try {
    recoveryService.applyPassiveRecovery(Number(req.params.id));
    const player = playerService.get(Number(req.params.id));
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });
    res.json({ success: true, data: { player } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.post('/reset/:id', (req, res) => {
  try {
    const player = playerService.reset(Number(req.params.id));
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });
    playerService.addLog(player.id, '进度已重置 (永久标记保留)');
    res.json({ success: true, data: { player } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/rest/start', (req, res) => {
  try {
    const { playerId } = req.body;
    if (!playerId) return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    const result = recoveryService.startRest(Number(playerId));
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    // Quest progress tracking
    try {
      const questService = require('../services/questService');
      questService.checkProgress(Number(playerId), 'rest', {});
    } catch (e) { /* quest not critical */ }
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

router.post('/rest/stop', (req, res) => {
  try {
    const { playerId } = req.body;
    if (!playerId) return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    const result = recoveryService.stopRest(Number(playerId));
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ code: 'SERVER_ERROR', message: e.message });
  }
});

// 复活
router.post('/revive', (req, res) => {
  try {
    const { playerId, method } = req.body;
    if (!playerId || !method) {
      return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    }
    const result = playerService.revivePlayer(Number(playerId), method);
    if (result.error) return res.status(400).json({ success: false, error: result.error });
    res.json({ success: true, data: result });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.post('/allocate-points', (req, res) => {
  try {
    const { playerId, atk, def, spd, crit } = req.body;
    if (!playerId) return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });

    const player = playerService.getRaw(Number(playerId));
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const stats = JSON.parse(player.stats_json);
    const allocAtk = Math.max(0, parseInt(atk) || 0);
    const allocDef = Math.max(0, parseInt(def) || 0);
    const allocSpd = Math.max(0, parseInt(spd) || 0);
    const allocCrit = Math.max(0, parseInt(crit) || 0);
    const totalAlloc = allocAtk + allocDef + allocSpd + allocCrit;

    if (totalAlloc > (stats.freePoints || 0)) {
      return res.status(400).json({ success: false, error: { code: 'INSUFFICIENT_POINTS', message: '可分配点数不足' } });
    }

    stats.freePoints = (stats.freePoints || 0) - totalAlloc;
    stats.allocatedAtk = (stats.allocatedAtk || 0) + allocAtk;
    stats.allocatedDef = (stats.allocatedDef || 0) + allocDef;
    stats.allocatedSpd = (stats.allocatedSpd || 0) + allocSpd;
    stats.allocatedCrit = (stats.allocatedCrit || 0) + allocCrit;

    playerService.update(player.id, { stats_json: stats });
    playerService.addLog(player.id, `属性分配: 攻击+${allocAtk} 防御+${allocDef} 速度+${allocSpd} 暴击+${allocCrit}`);

    const updated = playerService.get(player.id);
    res.json({ success: true, data: { player: updated } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// 重置属性分配 — 将已分配点数返还为自由点数，消耗硬币
router.post('/reset-allocation', (req, res) => {
  try {
    const { playerId } = req.body;
    if (!playerId) return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });

    const player = playerService.getRaw(Number(playerId));
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const stats = JSON.parse(player.stats_json);
    const totalAlloc = (stats.allocatedAtk || 0) + (stats.allocatedDef || 0) + (stats.allocatedSpd || 0) + (stats.allocatedCrit || 0);
    if (totalAlloc <= 0) {
      return res.status(400).json({ success: false, error: { code: 'NO_ALLOCATION', message: '没有已分配的属性点' } });
    }

    const cost = Math.max(50, totalAlloc * 20);
    if ((player.coins || 0) < cost) {
      return res.status(400).json({ success: false, error: { code: 'NOT_ENOUGH_COINS', message: `硬币不足，需要 ${cost} 枚` } });
    }

    // Refund allocated points to freePoints
    stats.freePoints = (stats.freePoints || 0) + totalAlloc;
    stats.allocatedAtk = 0;
    stats.allocatedDef = 0;
    stats.allocatedSpd = 0;
    stats.allocatedCrit = 0;

    const newCoins = player.coins - cost;
    playerService.update(player.id, { stats_json: stats, coins: newCoins });
    playerService.addLog(player.id, `重置全部分配：${totalAlloc} 点返还，消耗 ${cost} 硬币`);

    const updated = playerService.get(player.id);
    res.json({ success: true, data: { player: updated } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
