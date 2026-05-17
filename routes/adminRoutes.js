const express = require('express');
const router = express.Router();
const { authRequired } = require('../middleware/authMiddleware');
const { getDb } = require('../db/database');
const playerService = require('../services/playerService');
const { logAction, getRecent } = require('../utils/adminLog');
const feedbackStore = require('../utils/feedbackStore');

// Admin check — supports JWT admin role OR ADMIN_KEY header
function adminCheck(req, res, next) {
  // Channel A: JWT admin role
  if (req.user && req.user.role === 'admin') return next();

  // Channel B: ADMIN_KEY header
  const adminKey = process.env.ADMIN_KEY;
  if (adminKey && adminKey.length >= 8 && req.headers['x-admin-key'] === adminKey) {
    req._adminViaKey = true;
    req._adminName = 'admin-key';
    return next();
  }

  return res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: '需要管理员权限或有效的 ADMIN_KEY' } });
}

function getAdminName(req) {
  if (req._adminViaKey) return 'admin-key';
  return req.user ? (req.user.username || 'admin') : 'admin';
}

// Both channels require at least authRequired (which passes through for X-Admin-Key if no token)
// But X-Admin-Key without JWT would fail authRequired. So we need a flexible approach.
// authRequired checks for Bearer token. If missing, return 401.
// We need X-Admin-Key to bypass authRequired.

function flexibleAuth(req, res, next) {
  const adminKey = process.env.ADMIN_KEY;
  if (adminKey && adminKey.length >= 8 && req.headers['x-admin-key'] === adminKey) {
    req._adminViaKey = true;
    req._adminName = 'admin-key';
    return next();
  }
  // Fall through to JWT auth
  authRequired(req, res, (err) => {
    if (err) return next(err);
    if (!req.user || req.user.role !== 'admin') {
      return res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: '需要管理员权限' } });
    }
    req._adminName = req.user.username || 'admin';
    next();
  });
}

router.use(flexibleAuth);

// ===== Existing: List all players =====
router.get('/players', (req, res) => {
  try {
    const db = getDb();
    const { search, limit, offset } = req.query;
    let sql = 'SELECT id, player_name, stats_json, coins, story_fragments, current_chapter, current_location, user_id, created_at, current_main_chapter, logs_json, titles_json FROM players';
    const params = [];
    if (search) {
      sql += ' WHERE player_name LIKE ? OR id = ?';
      params.push(`%${search}%`, parseInt(search) || 0);
    }
    sql += ' ORDER BY id DESC LIMIT ? OFFSET ?';
    params.push(parseInt(limit) || 50, parseInt(offset) || 0);

    const players = db.prepare(sql).all(...params);
    const total = db.prepare('SELECT COUNT(*) as c FROM players').get().c;

    const result = players.map(p => {
      const stats = JSON.parse(p.stats_json || '{}');
      return {
        id: p.id,
        player_name: p.player_name,
        level: stats.level || 1,
        hp: stats.hp || 0,
        maxHp: stats.maxHp || 100,
        stamina: stats.stamina || 0,
        maxStamina: stats.maxStamina || 50,
        attack: stats.attack || 10,
        defense: stats.defense || 5,
        speed: stats.speed || 10,
        critRate: stats.critRate || 0.05,
        coins: p.coins || 0,
        story_fragments: p.story_fragments || 0,
        exp: stats.exp || 0,
        freePoints: stats.freePoints || 0,
        constellation: stats.constellation || null,
        isDead: !!stats.isDead,
        isResting: !!stats.isResting,
        current_chapter: p.current_chapter,
        current_main_chapter: p.current_main_chapter,
        current_location: p.current_location,
        user_id: p.user_id,
        created_at: p.created_at
      };
    });

    res.json({ success: true, data: { players: result, total } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== Existing: Get single player full data =====
router.get('/players/:id', (req, res) => {
  try {
    const player = playerService.get(Number(req.params.id));
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });
    res.json({ success: true, data: { player } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== Existing: Update player stats =====
router.post('/players/:id/update', (req, res) => {
  try {
    const playerId = Number(req.params.id);
    const player = playerService.getRaw(playerId);
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const stats = JSON.parse(player.stats_json || '{}');
    const fields = {};
    const allowedStats = ['hp', 'maxHp', 'stamina', 'maxStamina', 'attack', 'defense', 'speed',
      'critRate', 'critDamage', 'level', 'exp', 'freePoints', 'luck', 'channelHeat', 'worldLineShift',
      'insight', 'willpower', 'leadership', 'bond'];

    let statsChanged = false;
    for (const key of allowedStats) {
      if (req.body[key] !== undefined) {
        stats[key] = typeof req.body[key] === 'number' ? req.body[key] : stats[key];
        statsChanged = true;
      }
    }
    if (req.body.constellation !== undefined) {
      stats.constellation = req.body.constellation || null;
      statsChanged = true;
    }
    if (req.body.isDead !== undefined) {
      stats.isDead = !!req.body.isDead;
      statsChanged = true;
    }
    if (req.body.isResting !== undefined) {
      stats.isResting = !!req.body.isResting;
      statsChanged = true;
    }
    if (statsChanged) fields.stats_json = stats;

    if (req.body.coins !== undefined) fields.coins = parseInt(req.body.coins) || 0;
    if (req.body.story_fragments !== undefined) fields.story_fragments = parseInt(req.body.story_fragments) || 0;

    if (Object.keys(fields).length > 0) {
      playerService.update(playerId, fields);
    }

    const updated = playerService.get(playerId);
    playerService.addLog(playerId, '[管理员] 数据已被调整');
    logAction(getAdminName(req), 'update_player', playerId, { fields: Object.keys(fields) });
    res.json({ success: true, data: { player: updated } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== Existing: Force revive =====
router.post('/players/:id/force-revive', (req, res) => {
  try {
    const playerId = Number(req.params.id);
    const player = playerService.getRaw(playerId);
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const stats = JSON.parse(player.stats_json || '{}');
    stats.isDead = false;
    stats.hp = stats.maxHp || 100;
    playerService.update(playerId, { stats_json: stats });
    playerService.addLog(playerId, '[管理员] 强制复活');
    logAction(getAdminName(req), 'force_revive', playerId, {});

    res.json({ success: true, data: { player: playerService.get(playerId) } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== NEW: Update player stats (dedicated, with validation) =====
router.patch('/players/:id/stats', (req, res) => {
  try {
    const playerId = Number(req.params.id);
    const player = playerService.getRaw(playerId);
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const stats = JSON.parse(player.stats_json || '{}');
    const allowedStats = ['hp', 'maxHp', 'stamina', 'maxStamina', 'attack', 'defense', 'speed',
      'critRate', 'critDamage', 'level', 'exp', 'freePoints', 'luck', 'channelHeat', 'worldLineShift',
      'insight', 'willpower', 'leadership', 'bond'];
    const changed = [];

    for (const key of allowedStats) {
      if (req.body[key] !== undefined) {
        const val = Number(req.body[key]);
        if (isNaN(val) || val < 0) {
          return res.status(400).json({ success: false, error: { code: 'INVALID_VALUE', message: `${key} 必须为非负数字` } });
        }
        stats[key] = val;
        changed.push(key);
      }
    }

    // Validate hp <= maxHp, stamina <= maxStamina
    if (req.body.hp !== undefined || req.body.maxHp !== undefined) {
      if (stats.hp > stats.maxHp) stats.hp = stats.maxHp;
    }
    if (req.body.stamina !== undefined || req.body.maxStamina !== undefined) {
      if (stats.stamina > stats.maxStamina) stats.stamina = stats.maxStamina;
    }

    if (changed.length === 0) {
      return res.status(400).json({ success: false, error: { code: 'NO_CHANGES', message: '没有提供有效的属性字段' } });
    }

    playerService.update(playerId, { stats_json: stats });
    playerService.addLog(playerId, '[管理员] 属性已调整: ' + changed.join(', '));
    logAction(getAdminName(req), 'update_stats', playerId, { changed, values: req.body });

    res.json({ success: true, data: { player: playerService.get(playerId), changed } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== NEW: Update player resources =====
router.patch('/players/:id/resources', (req, res) => {
  try {
    const playerId = Number(req.params.id);
    const player = playerService.getRaw(playerId);
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const fields = {};
    const resourceKeys = ['coins', 'story_fragments'];
    const stats = JSON.parse(player.stats_json || '{}');
    const statsResources = ['scenarioProof', 'constellationFavor', 'kingToken', 'abyssMark', 'finalPage'];
    const changed = [];

    for (const key of resourceKeys) {
      if (req.body[key] !== undefined) {
        const val = parseInt(req.body[key]);
        if (isNaN(val) || val < 0) {
          return res.status(400).json({ success: false, error: { code: 'INVALID_VALUE', message: `${key} 必须为非负整数` } });
        }
        fields[key] = val;
        changed.push(key);
      }
    }

    let statsChanged = false;
    for (const key of statsResources) {
      if (req.body[key] !== undefined) {
        const val = Number(req.body[key]);
        if (isNaN(val) || val < 0) {
          return res.status(400).json({ success: false, error: { code: 'INVALID_VALUE', message: `${key} 必须为非负数字` } });
        }
        stats[key] = val;
        statsChanged = true;
        changed.push(key);
      }
    }
    if (statsChanged) fields.stats_json = stats;

    if (changed.length === 0) {
      return res.status(400).json({ success: false, error: { code: 'NO_CHANGES', message: '没有提供有效的资源字段' } });
    }

    playerService.update(playerId, fields);
    playerService.addLog(playerId, '[管理员] 资源已调整: ' + changed.join(', '));
    logAction(getAdminName(req), 'update_resources', playerId, { changed, values: req.body });

    res.json({ success: true, data: { player: playerService.get(playerId), changed } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== NEW: Update player progress =====
router.patch('/players/:id/progress', (req, res) => {
  try {
    const playerId = Number(req.params.id);
    const player = playerService.getRaw(playerId);
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const fields = {};
    const changed = [];
    const db = getDb();

    if (req.body.current_chapter !== undefined) {
      const chapter = db.prepare('SELECT chapter_key FROM chapters WHERE chapter_key = ?').get(req.body.current_chapter);
      if (!chapter) {
        return res.status(400).json({ success: false, error: { code: 'INVALID_CHAPTER', message: `章节 ${req.body.current_chapter} 不存在` } });
      }
      fields.current_chapter = req.body.current_chapter;
      changed.push('current_chapter');
    }

    if (req.body.current_main_chapter !== undefined) {
      const mc = db.prepare('SELECT chapter_key FROM main_chapters WHERE chapter_key = ?').get(req.body.current_main_chapter);
      if (!mc) {
        return res.status(400).json({ success: false, error: { code: 'INVALID_MAIN_CHAPTER', message: `主线阶段 ${req.body.current_main_chapter} 不存在` } });
      }
      fields.current_main_chapter = req.body.current_main_chapter;
      changed.push('current_main_chapter');
    }

    if (req.body.current_location !== undefined) {
      if (req.body.current_location) {
        const loc = db.prepare('SELECT location_key FROM locations WHERE location_key = ?').get(req.body.current_location);
        if (!loc) {
          return res.status(400).json({ success: false, error: { code: 'INVALID_LOCATION', message: `位置 ${req.body.current_location} 不存在` } });
        }
      }
      fields.current_location = req.body.current_location;
      changed.push('current_location');
    }

    if (changed.length === 0) {
      return res.status(400).json({ success: false, error: { code: 'NO_CHANGES', message: '没有提供有效的进度字段' } });
    }

    playerService.update(playerId, fields);
    playerService.addLog(playerId, '[管理员] 进度已调整: ' + changed.join(', '));
    logAction(getAdminName(req), 'update_progress', playerId, { changed, values: req.body });

    res.json({ success: true, data: { player: playerService.get(playerId), changed } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== NEW: Grant item/equipment/skill/title =====
router.post('/players/:id/grant', (req, res) => {
  try {
    const playerId = Number(req.params.id);
    const { type, key, quantity } = req.body;
    if (!type || !key) {
      return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少 type 或 key 参数' } });
    }
    if (!['item', 'equipment', 'skill', 'title'].includes(type)) {
      return res.status(400).json({ success: false, error: { code: 'INVALID_TYPE', message: 'type 必须为 item/equipment/skill/title' } });
    }

    const player = playerService.getRaw(playerId);
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });
    const db = getDb();
    const adminName = getAdminName(req);

    if (type === 'item') {
      const item = db.prepare('SELECT * FROM items WHERE item_key = ?').get(key);
      if (!item) return res.status(400).json({ success: false, error: { code: 'ITEM_NOT_FOUND', message: `道具 ${key} 不存在` } });

      const qty = Math.max(1, parseInt(quantity) || 1);
      const existing = db.prepare('SELECT id, quantity FROM player_inventory WHERE player_id = ? AND item_key = ?').get(playerId, key);
      if (existing) {
        db.prepare('UPDATE player_inventory SET quantity = quantity + ? WHERE id = ?').run(qty, existing.id);
      } else {
        db.prepare('INSERT INTO player_inventory (player_id, item_key, quantity) VALUES (?, ?, ?)').run(playerId, key, qty);
      }
      playerService.addLog(playerId, `[管理员] 发放道具: ${item.name || key} x${qty}`);
      logAction(adminName, 'grant_item', playerId, { item_key: key, quantity: qty });
      return res.json({ success: true, data: { type, key, quantity: qty, message: `已发放 ${item.name || key} x${qty}` } });
    }

    if (type === 'equipment') {
      const eq = db.prepare('SELECT * FROM equipment WHERE equipment_key = ?').get(key);
      if (!eq) return res.status(400).json({ success: false, error: { code: 'EQUIP_NOT_FOUND', message: `装备 ${key} 不存在` } });

      const existing = db.prepare('SELECT id FROM player_equipment WHERE player_id = ? AND equipment_key = ?').get(playerId, key);
      if (existing) {
        return res.status(400).json({ success: false, error: { code: 'ALREADY_HAS_EQUIP', message: '玩家已拥有该装备' } });
      }
      db.prepare('INSERT INTO player_equipment (player_id, equipment_key, slot, equipped) VALUES (?, ?, ?, 0)').run(playerId, key, eq.slot || 'misc');
      playerService.addLog(playerId, `[管理员] 发放装备: ${eq.name || key}`);
      logAction(adminName, 'grant_equipment', playerId, { equipment_key: key });
      return res.json({ success: true, data: { type, key, message: `已发放 ${eq.name || key}` } });
    }

    if (type === 'skill') {
      const skill = db.prepare('SELECT * FROM skills WHERE skill_key = ?').get(key);
      if (!skill) return res.status(400).json({ success: false, error: { code: 'SKILL_NOT_FOUND', message: `技能 ${key} 不存在` } });

      const existing = db.prepare('SELECT id FROM player_skills WHERE player_id = ? AND skill_key = ?').get(playerId, key);
      if (existing) {
        return res.status(400).json({ success: false, error: { code: 'ALREADY_HAS_SKILL', message: '玩家已拥有该技能' } });
      }
      db.prepare('INSERT INTO player_skills (player_id, skill_key, level, experience) VALUES (?, ?, 1, 0)').run(playerId, key);
      playerService.addLog(playerId, `[管理员] 发放技能: ${skill.name || key}`);
      logAction(adminName, 'grant_skill', playerId, { skill_key: key });
      return res.json({ success: true, data: { type, key, message: `已发放 ${skill.name || key}` } });
    }

    if (type === 'title') {
      const title = db.prepare('SELECT * FROM titles WHERE title_key = ?').get(key);
      if (!title) return res.status(400).json({ success: false, error: { code: 'TITLE_NOT_FOUND', message: `称号 ${key} 不存在` } });

      const playerTitles = JSON.parse(player.titles_json || '[]');
      if (playerTitles.includes(key)) {
        return res.status(400).json({ success: false, error: { code: 'ALREADY_HAS_TITLE', message: '玩家已拥有该称号' } });
      }
      playerTitles.push(key);
      playerService.update(playerId, { titles_json: playerTitles });
      playerService.addLog(playerId, `[管理员] 授予称号: ${title.name || key}`);
      logAction(adminName, 'grant_title', playerId, { title_key: key });
      return res.json({ success: true, data: { type, key, message: `已授予 ${title.name || key}` } });
    }
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== NEW: Quick actions =====
router.post('/players/:id/quick-action', (req, res) => {
  try {
    const playerId = Number(req.params.id);
    const { action } = req.body;
    const player = playerService.getRaw(playerId);
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const stats = JSON.parse(player.stats_json || '{}');
    const adminName = getAdminName(req);
    let msg = '';

    switch (action) {
      case 'fill_stamina':
        stats.stamina = stats.maxStamina || 50;
        msg = '体力已拉满';
        break;
      case 'fill_hp':
        stats.hp = stats.maxHp || 100;
        msg = '生命已拉满';
        break;
      case 'fill_all':
        stats.stamina = stats.maxStamina || 50;
        stats.hp = stats.maxHp || 100;
        msg = '体力生命已拉满';
        break;
      case 'zero_stamina':
        stats.stamina = 0;
        msg = '体力已清零';
        break;
      case 'start_rest':
        stats.isResting = true;
        msg = '已进入休息状态';
        break;
      case 'stop_rest':
        stats.isResting = false;
        msg = '已停止休息';
        break;
      case 'clear_death':
        stats.isDead = false;
        stats.hp = Math.max(stats.hp || 1, 1);
        msg = '已清除死亡状态';
        break;
      default:
        return res.status(400).json({ success: false, error: { code: 'INVALID_ACTION', message: '无效的操作: ' + action } });
    }

    playerService.update(playerId, { stats_json: stats });
    playerService.addLog(playerId, `[管理员] ${msg}`);
    logAction(adminName, 'quick_action', playerId, { action, result: msg });

    res.json({ success: true, data: { player: playerService.get(playerId), message: msg } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== NEW: Get player logs =====
router.get('/players/:id/logs', (req, res) => {
  try {
    const player = playerService.getRaw(Number(req.params.id));
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const logs = JSON.parse(player.logs_json || '[]');
    const limit = parseInt(req.query.limit) || 50;
    const recent = logs.slice(-limit).reverse();

    res.json({ success: true, data: { logs: recent, total: logs.length } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== NEW: Feedback management =====
router.get('/feedback', (req, res) => {
  try {
    const { status } = req.query;
    const items = feedbackStore.getAll(status || null);
    res.json({ success: true, data: { feedback: items, total: items.length } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.patch('/feedback/:id', (req, res) => {
  try {
    const { status, note } = req.body;
    const updated = feedbackStore.update(req.params.id, { status, note });
    if (!updated) return res.status(404).json({ success: false, error: { code: 'FEEDBACK_NOT_FOUND', message: '反馈不存在' } });

    logAction(getAdminName(req), 'update_feedback', null, { feedbackId: req.params.id, status, note });
    res.json({ success: true, data: { feedback: updated } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== NEW: Admin action log =====
router.get('/actions', (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 50;
    const actions = getRecent(limit);
    res.json({ success: true, data: { actions, total: actions.length } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

// ===== Existing: Title Management =====
router.get('/titles', (req, res) => {
  try {
    const db = getDb();
    const titles = db.prepare('SELECT * FROM titles ORDER BY id').all();
    const result = titles.map(t => ({
      id: t.id,
      title_key: t.title_key,
      name: t.name,
      description: t.description,
      rarity: t.rarity,
      conditions: JSON.parse(t.conditions_json || '{}'),
      effects: JSON.parse(t.effects_json || '{}'),
      exclusive_with: JSON.parse(t.exclusive_with_json || '[]')
    }));
    res.json({ success: true, data: { titles: result } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.get('/players/:id/titles', (req, res) => {
  try {
    const player = playerService.getRaw(Number(req.params.id));
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const playerTitles = JSON.parse(player.titles_json || '[]');
    const allTitles = getDb().prepare('SELECT title_key, name, rarity FROM titles').all();
    const titleMap = {};
    allTitles.forEach(t => { titleMap[t.title_key] = { name: t.name, rarity: t.rarity }; });

    const result = playerTitles.map(tk => ({
      title_key: tk,
      name: titleMap[tk]?.name || tk,
      rarity: titleMap[tk]?.rarity || 'common'
    }));

    res.json({ success: true, data: { titles: result } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

router.post('/players/:id/titles', (req, res) => {
  try {
    const playerId = Number(req.params.id);
    const { action, title_key } = req.body;
    if (!action || !title_key) {
      return res.status(400).json({ success: false, error: { code: 'MISSING_PARAMS', message: '缺少必要参数' } });
    }
    if (!['grant', 'revoke'].includes(action)) {
      return res.status(400).json({ success: false, error: { code: 'INVALID_ACTION', message: 'action 必须为 grant 或 revoke' } });
    }

    const player = playerService.getRaw(playerId);
    if (!player) return res.status(404).json({ success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } });

    const title = getDb().prepare('SELECT title_key, name FROM titles WHERE title_key = ?').get(title_key);
    if (!title) return res.status(400).json({ success: false, error: { code: 'TITLE_NOT_FOUND', message: '称号不存在' } });

    const playerTitles = JSON.parse(player.titles_json || '[]');
    if (action === 'grant') {
      if (playerTitles.includes(title_key)) {
        return res.status(400).json({ success: false, error: { code: 'ALREADY_HAS_TITLE', message: '玩家已拥有该称号' } });
      }
      playerTitles.push(title_key);
      playerService.addLog(playerId, `[管理员] 授予称号: ${title.name}`);
      logAction(getAdminName(req), 'grant_title', playerId, { title_key });
    } else {
      const idx = playerTitles.indexOf(title_key);
      if (idx === -1) {
        return res.status(400).json({ success: false, error: { code: 'NOT_HAVE_TITLE', message: '玩家未拥有该称号' } });
      }
      playerTitles.splice(idx, 1);
      playerService.addLog(playerId, `[管理员] 移除称号: ${title.name}`);
      logAction(getAdminName(req), 'revoke_title', playerId, { title_key });
    }

    playerService.update(playerId, { titles_json: playerTitles });
    res.json({ success: true, data: { titles: playerTitles } });
  } catch (e) {
    console.error(e);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
  }
});

module.exports = router;
