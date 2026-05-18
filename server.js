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

  // Round 2: exploration, combat, inventory, equipment, skills, PK
  app.use('/api/explore', authRequired, require('./routes/exploreRoutes'));
  app.use('/api/combat', authRequired, require('./routes/combatRoutes'));
  app.use('/api/inventory', authRequired, require('./routes/inventoryRoutes'));
  app.use('/api/equipment', authRequired, require('./routes/equipmentRoutes'));
  app.use('/api/skills', authRequired, require('./routes/skillRoutes'));
  app.use('/api/pk', authRequired, require('./routes/pkRoutes'));

  // === Public Routes (no auth required) ===
  app.use('/api/rankings', require('./routes/rankingRoutes'));

  // Admin (auth + admin check)
  app.use('/api/admin', require('./routes/adminRoutes'));

  // Round 6: broadcast (partial auth — active/history public, join/claim protected)
  app.use('/api/broadcast', require('./routes/broadcastRoutes'));
  app.use('/api/ai-director', require('./routes/aiDirectorRoutes'));

  // Health check
  app.get('/api/health', (req, res) => {
    res.json({ status: 'ok', time: new Date().toISOString() });
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
  app.get('/api/version', (req, res) => {
    try {
      const raw = fs.readFileSync(changelogPath, 'utf8');
      const changelog = JSON.parse(raw);
      const version = changelog[0]?.version || '0.0.0';
      res.json({ version, deployTime: changelog[0]?.date || '' });
    } catch (e) {
      res.json({ version: '0.0.0', deployTime: '' });
    }
  });

  // Global error handler — must be registered after all routes
  app.use((err, req, res, next) => {
    console.error('Unhandled error:', err);
    res.status(500).json({ success: false, error: { code: 'INTERNAL_ERROR', message: '服务器内部错误' } });
  });

  app.listen(PORT, () => {
    console.log(`全知读者视角 游戏服务器已启动 (Round 11): http://localhost:${PORT}`);
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
