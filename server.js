const crypto = require('crypto');
require('dotenv').config();

if (!process.env.JWT_SECRET) {
  const generated = crypto.randomBytes(32).toString('hex');
  process.env.JWT_SECRET = generated;
  if (process.env.NODE_ENV === 'production') {
    console.warn('============================================================');
    console.warn('WARNING: JWT_SECRET auto-generated. Set it in Railway Variables');
    console.warn('  JWT_SECRET=' + generated);
    console.warn('  All tokens will be invalidated on next restart if not set.');
    console.warn('============================================================');
  } else {
    console.warn('WARNING: JWT_SECRET not set. Using generated default for development.');
  }
}

if (!process.env.ADMIN_KEY || process.env.ADMIN_KEY.length < 8) {
  const generated = crypto.randomBytes(12).toString('hex');
  process.env.ADMIN_KEY = generated;
  console.warn('============================================================');
  console.warn('ADMIN_KEY auto-generated. Set it in Railway Variables:');
  console.warn('  ADMIN_KEY=' + generated);
  console.warn('============================================================');
}

const express = require('express');
const rateLimit = require('express-rate-limit');
const fs = require('fs');
const path = require('path');
const { initDb, getDb } = require('./db/database');
const { seedStory } = require('./data/seedStory');
const { seedTitles } = require('./data/seedTitles');
const { seedEndings } = require('./data/seedEndings');
const { seedLocations } = require('./data/seedLocations');
const { seedMonsters } = require('./data/seedMonsters');
const { seedItems } = require('./data/seedItems');
const { seedEquipment } = require('./data/seedEquipment');
const { seedSkills } = require('./data/seedSkills');
const { seedMainChapters } = require('./data/seedMainChapters');
const { seedExplorationEvents } = require('./data/seedExplorationEvents');
const { seedFactionSkills } = require('./data/seedFactionSkills');
const { seedEquipmentSets } = require('./data/seedEquipmentSets');
const { seedNarrative } = require('./data/seedNarrative');
const { authRequired } = require('./middleware/authMiddleware');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(express.json({ limit: '2mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// Rate limiting for auth routes
const loginLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 5,
  message: { success: false, error: { code: 'RATE_LIMITED', message: '登录尝试过于频繁，请60秒后重试' } },
  standardHeaders: true,
  legacyHeaders: false
});

// Start server
async function start() {
  // Initialize database
  await initDb();
  const db = getDb();

  // Seed data if empty
  const chapterCount = db.prepare('SELECT COUNT(*) as c FROM chapters').get().c;
  if (chapterCount === 0) {
    console.log('Seeding database...');
    seedStory(db);
    seedTitles(db);
    seedEndings(db);
    seedLocations(db);
    seedMonsters(db);
    seedItems(db);
    seedEquipment(db);
    seedSkills(db);
    seedMainChapters(db);
    seedExplorationEvents(db);
    seedFactionSkills(db);
    seedEquipmentSets(db);
    console.log('Database seeded successfully.');
  } else {
    // Round 2: seed new tables if empty
    const locCount = db.prepare('SELECT COUNT(*) as c FROM locations').get().c;
    if (locCount === 0) {
      console.log('Seeding Round 2 data...');
      seedLocations(db);
      seedMonsters(db);
      seedItems(db);
      seedEquipment(db);
      seedSkills(db);
      console.log('Round 2 seed complete.');
    }
    // Round 3: seed main chapters if empty
    const mcCount = db.prepare('SELECT COUNT(*) as c FROM main_chapters').get().c;
    if (mcCount === 0) {
      console.log('Seeding Round 3 main chapters...');
      seedMainChapters(db);
    }
    // Round 5: seed exploration events if empty
    const eeCount = db.prepare('SELECT COUNT(*) as c FROM exploration_events').get().c;
    if (eeCount === 0) {
      console.log('Seeding Round 5 exploration events...');
      seedExplorationEvents(db);
    }
    // Phase 5: seed narrative data if empty
    var nmCount = db.prepare('SELECT COUNT(*) as c FROM item_memories').get().c;
    if (nmCount === 0) {
      console.log('Seeding Phase 5 narrative data...');
      seedNarrative(db);
    }
    // Phase 3 Round 2: seed faction skills if empty
    var fsCount = db.prepare('SELECT COUNT(*) as c FROM faction_skills').get().c;
    if (fsCount === 0) {
      console.log('Seeding faction skills...');
      seedFactionSkills(db);
    }
    // Phase 3 Round 2: seed equipment sets if empty
    var esCount = db.prepare('SELECT COUNT(*) as c FROM equipment_sets').get().c;
    if (esCount === 0) {
      console.log('Seeding equipment sets...');
      seedEquipmentSets(db);
    }
    console.log(`Database contains ${chapterCount} chapters, skipping story seed.`);
  }

  // === Auth Routes (public) ===
  app.use('/api/auth/login', loginLimiter);
  app.use('/api/auth', require('./routes/authRoutes'));

  // === Protected Routes ===

  // Round 1
  app.use('/api/player', authRequired, require('./routes/playerRoutes'));
  app.use('/api/story', authRequired, require('./routes/storyRoutes'));
  app.use('/api/titles', authRequired, require('./routes/titleRoutes'));
  app.use('/api/endings', authRequired, require('./routes/endingRoutes'));
  // Round 3: main chapters / stage system
  app.use('/api/chapters', authRequired, require('./routes/chapterRoutes'));

  // Quests (daily/weekly)
  app.use('/api/quests', authRequired, require('./routes/questRoutes'));

  // Round 2: exploration, combat, inventory, equipment, skills, PK
  app.use('/api/explore', authRequired, require('./routes/exploreRoutes'));
  app.use('/api/combat', authRequired, require('./routes/combatRoutes'));
  app.use('/api/inventory', authRequired, require('./routes/inventoryRoutes'));
  app.use('/api/equipment', authRequired, require('./routes/equipmentRoutes'));
  // Phase 3 Round 2: Public faction skill catalog (no auth required,
  // must be before app.use('/api/skills', authRequired, ...) below)
  app.get('/api/skills/faction-catalog/:constellationKey', (req, res) => {
    try {
      var skillService = require('./services/skillService');
      var skills = skillService.getFactionSkills(req.params.constellationKey);
      res.json({ success: true, data: { skills } });
    } catch (e) {
      res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
    }
  });

  app.use('/api/skills', authRequired, require('./routes/skillRoutes'));
  app.use('/api/pk', authRequired, require('./routes/pkRoutes'));

  // Avatar Rank (auth required)
  app.use('/api/avatar-rank', authRequired, require('./routes/avatarRankRoutes'));

  // === Public Routes (no auth required) ===
  app.use('/api/worldline', authRequired, require('./routes/worldlineRoutes'));
  app.use('/api/rankings', require('./routes/rankingRoutes'));

  // Admin (auth + admin check)
  app.use('/api/admin', require('./routes/adminRoutes'));

  // Round 6: broadcast (partial auth — active/history public, join/claim protected)
  app.use('/api/broadcast', require('./routes/broadcastRoutes'));
  app.use('/api/chat', require('./routes/chatRoutes'));
  app.use('/api/friends', require('./routes/friendRoutes'));
  app.use('/api/factions', authRequired, require('./routes/factionRoutes'));
  app.use('/api/trade', authRequired, require('./routes/tradeRoutes'));
  app.use('/api/party', authRequired, require('./routes/partyRoutes'));
  app.use('/api/narrative', authRequired, require('./routes/narrativeRoutes'));
  app.use('/api/ai-director', require('./routes/aiDirectorRoutes'));

  // Health check
  app.get('/api/health', (req, res) => {
    res.json({ success: true, data: { status: 'ok', time: new Date().toISOString() } });
  });

  // Admin page route
  app.get('/admin', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'admin.html'));
  });

  // Feedback (public)
  app.use('/api/feedback', require('./routes/feedbackRoutes'));

  // Changelog (public)
  const changelogPath = path.join(__dirname, 'data', 'changelog.json');
  app.get('/api/changelog', (req, res) => {
    try {
      const raw = fs.readFileSync(changelogPath, 'utf8');
      const changelog = JSON.parse(raw);
      res.json({ success: true, data: changelog });
    } catch (e) {
      console.error('Failed to read changelog:', e.message);
      res.json({ success: true, data: [] });
    }
  });

  // Version check — client uses this to detect new deployments
  app.get('/api/version', authRequired, (req, res) => {
    try {
      const raw = fs.readFileSync(changelogPath, 'utf8');
      const changelog = JSON.parse(raw);
      const version = changelog[0]?.version || '0.0.0';
      res.json({ success: true, data: { version, deployTime: changelog[0]?.date || '' } });
    } catch (e) {
      res.json({ success: true, data: { version: '0.0.0', deployTime: '' } });
    }
  });

  // Heartbeat — client pings every 30s to mark player online
  app.post('/api/heartbeat', authRequired, (req, res) => {
    try {
      const playerService = require('./services/playerService');
      playerService.updateHeartbeat(req.body.playerId);
      // Return pending PK challenges for this player
      const pendingChallenges = require('./services/pkService').getPendingChallenges(req.body.playerId);
      res.json({ success: true, data: { pendingChallenges: pendingChallenges || [] } });
    } catch (e) {
      res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
    }
  });

  // Peer revive — revive another player (same cost as self-revive)
  app.post('/api/player/peer-revive', authRequired, (req, res) => {
    try {
      const playerService = require('./services/playerService');
      const reviverId = req.body.reviverId;
      const targetId = req.body.targetId;
      const method = req.body.method;
      const result = playerService.revivePlayer(targetId, method);
      if (result.error) return res.status(400).json(result);
      const reviver = playerService.get(reviverId);
      if (method === 'coins') {
        const coinCost = result.coinCost;
        if ((reviver.coins || 0) < coinCost) {
          return res.status(400).json({ success: false, error: { code: 'NOT_ENOUGH_COINS', message: '金币不足' } });
        }
        playerService.update(reviverId, { coins: reviver.coins - coinCost });
        playerService.addLog(reviverId, '支付' + coinCost + '金币复活了' + (playerService.get(targetId)?.player_name || '一位玩家'));
        playerService.addLog(targetId, reviver.player_name + '支付了' + coinCost + '金币将你从冥界拉回。');
        return res.json({ success: true, data: result });
      } else if (method === 'title') {
        var reviverTitles = reviver.titles || [];
        if (reviverTitles.length === 0) {
          return res.status(400).json({ success: false, error: { code: 'NO_TITLES', message: '没有任何称号可以献祭' } });
        }
        var sacrificed = reviverTitles.pop();
        playerService.update(reviverId, { titles_json: reviverTitles });
        playerService.addLog(reviverId, '献祭称号「' + sacrificed + '」复活了' + (playerService.get(targetId)?.player_name || '一位玩家'));
        playerService.addLog(targetId, reviver.player_name + '献祭了称号「' + sacrificed + '」将你从冥界拉回。');
        return res.json({ success: true, data: result });
      }
      res.status(400).json({ success: false, error: { code: 'INVALID_METHOD', message: '无效的复活方式' } });
    } catch (e) {
      console.error(e);
      res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: e.message } });
    }
  });


  // Global error handler — must be registered after all routes
  app.use((err, req, res, next) => {
    console.error('Unhandled error:', err);
    res.status(500).json({ success: false, error: { code: 'INTERNAL_ERROR', message: '服务器内部错误' } });
  });

  app.listen(PORT, () => {
    console.log(`全知读者视角 游戏服务器已启动 (Round 11): http://localhost:${PORT}`);
    // 启动全服调度引擎
    try {
      require('./services/schedulerService').start();
    } catch (e) {
      console.error('Failed to start scheduler:', e.message);
    }
  });
}

// Process-level crash handlers
process.on('uncaughtException', (err) => {
  console.error('UNCAUGHT EXCEPTION:', err);
  // Attempt graceful shutdown
  const { closeDb } = require('./db/database');
  closeDb().catch(() => {});
  process.exit(1);
});

process.on('unhandledRejection', (reason) => {
  console.error('UNHANDLED REJECTION:', reason);
});

start().catch(e => {
  console.error('Failed to start server:', e);
  process.exit(1);
});
