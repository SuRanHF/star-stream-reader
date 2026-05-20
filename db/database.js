const fs = require('fs');
const path = require('path');

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, '..', 'data');
const DB_PATH = path.join(DATA_DIR, 'game.db');

let db = null;
let _origPrepare = null;
let _saveTimer = null;
let _savePending = false;

function createPrepared(sql) {
  return {
    run(...params) {
      const stmt = _origPrepare(sql);
      try {
        stmt.bind(params);
        stmt.step();
      } finally {
        stmt.free();
      }
      const idStmt = _origPrepare('SELECT last_insert_rowid() as id');
      let lastId = 0;
      if (idStmt.step()) lastId = idStmt.get()[0];
      idStmt.free();
      scheduleSave();
      return { lastInsertRowid: lastId, changes: db.getRowsModified() };
    },
    get(...params) {
      const stmt = _origPrepare(sql);
      try {
        if (params.length > 0) stmt.bind(params);
        let row = null;
        if (stmt.step()) {
          const cols = stmt.getColumnNames();
          const vals = stmt.get();
          row = {};
          cols.forEach((c, i) => { row[c] = vals[i]; });
        }
        return row;
      } finally {
        stmt.free();
      }
    },
    all(...params) {
      const stmt = _origPrepare(sql);
      try {
        if (params.length > 0) stmt.bind(params);
        const cols = stmt.getColumnNames();
        const rows = [];
        while (stmt.step()) {
          const vals = stmt.get();
          const row = {};
          cols.forEach((c, i) => { row[c] = vals[i]; });
          rows.push(row);
        }
        return rows;
      } finally {
        stmt.free();
      }
    }
  };
}

function scheduleSave() {
  if (_savePending) return;
  _savePending = true;
  if (_saveTimer) clearTimeout(_saveTimer);
  _saveTimer = setTimeout(() => {
    _saveDb();
  }, 50);
}

async function initDb() {
  if (db) return db;

  try {
    const SQL = await require('sql.js')({
      locateFile: file => path.join(__dirname, '..', 'node_modules', 'sql.js', 'dist', file)
    });

    const dir = path.dirname(DB_PATH);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

    if (fs.existsSync(DB_PATH)) {
      const buffer = fs.readFileSync(DB_PATH);
      db = new SQL.Database(buffer);
    } else {
      db = new SQL.Database();
    }

    _origPrepare = db.prepare.bind(db);
    db.prepare = createPrepared;

    // Initialize schema
    db.run('PRAGMA foreign_keys = ON');
    const sqlText = fs.readFileSync(path.join(__dirname, 'init.sql'), 'utf-8');
    db.run(sqlText);

    // Run migrations
    runMigrations(db);

    _saveDb();

    console.log('Database initialized at', DB_PATH);
    return db;
  } catch (err) {
    console.error('Failed to initialize database:', err);
    throw new Error(`Database initialization failed: ${err.message}`);
  }
}

function getDb() {
  if (!db) throw new Error('Database not initialized. Call initDb() first.');
  return db;
}

function _saveDb() {
  if (!db) return;
  try {
    const data = db.export();
    const buf = Buffer.from(data);
    const dir = path.dirname(DB_PATH);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const tmpPath = DB_PATH + '.tmp';
    fs.writeFileSync(tmpPath, buf);
    fs.renameSync(tmpPath, DB_PATH);
  } catch (err) {
    console.error('Failed to save database:', err);
    try { if (fs.existsSync(DB_PATH + '.tmp')) fs.unlinkSync(DB_PATH + '.tmp'); } catch (_) {}
  } finally {
    _savePending = false;
  }
}

function saveDb() {
  if (db) {
    if (_saveTimer) clearTimeout(_saveTimer);
    _savePending = false;
    _saveDb();
  }
}

async function closeDb() {
  if (db) {
    saveDb();
    db.close();
    db = null;
    _origPrepare = null;
  }
}

function beginTransaction() {
  getDb().run('BEGIN');
}

function commitTransaction() {
  getDb().run('COMMIT');
  saveDb();
}

function rollbackTransaction() {
  getDb().run('ROLLBACK');
}

function runMigrations(database) {
  function columnExists(table, column) {
    try {
      const rows = database.prepare(`PRAGMA table_info(${table})`).all();
      return rows.some(r => r.name === column);
    } catch (e) {
      return false;
    }
  }

  if (!columnExists('chapters', 'main_chapter_key')) {
    database.run('ALTER TABLE chapters ADD COLUMN main_chapter_key TEXT NOT NULL DEFAULT \'\'');
  }

  if (!columnExists('players', 'unlocked_chapters_json')) {
    database.run('ALTER TABLE players ADD COLUMN unlocked_chapters_json TEXT NOT NULL DEFAULT \'[]\'');
  }
  if (!columnExists('players', 'completed_chapters_json')) {
    database.run('ALTER TABLE players ADD COLUMN completed_chapters_json TEXT NOT NULL DEFAULT \'[]\'');
  }
  if (!columnExists('players', 'current_main_chapter')) {
    database.run('ALTER TABLE players ADD COLUMN current_main_chapter TEXT NOT NULL DEFAULT \'main_ch01_paid_service\'');
  }
  if (!columnExists('players', 'breakthrough_resources_json')) {
    database.run('ALTER TABLE players ADD COLUMN breakthrough_resources_json TEXT NOT NULL DEFAULT \'{}\'');
  }
  if (!columnExists('players', 'boss_kills_json')) {
    database.run('ALTER TABLE players ADD COLUMN boss_kills_json TEXT NOT NULL DEFAULT \'[]\'');
  }

  if (!columnExists('choices', 'choice_type')) {
    database.run('ALTER TABLE choices ADD COLUMN choice_type TEXT NOT NULL DEFAULT \'progress\'');
  }
  if (!columnExists('choices', 'decision_group')) {
    database.run('ALTER TABLE choices ADD COLUMN decision_group TEXT DEFAULT NULL');
  }
  if (!columnExists('choices', 'is_repeatable')) {
    database.run('ALTER TABLE choices ADD COLUMN is_repeatable INTEGER NOT NULL DEFAULT 0');
  }
  if (!columnExists('choices', 'completes_stage')) {
    database.run('ALTER TABLE choices ADD COLUMN completes_stage INTEGER NOT NULL DEFAULT 0');
  }

  if (!columnExists('main_chapters', 'stage_objectives_json')) {
    database.run('ALTER TABLE main_chapters ADD COLUMN stage_objectives_json TEXT NOT NULL DEFAULT \'{}\'');
  }

  if (!columnExists('players', 'decision_history_json')) {
    database.run('ALTER TABLE players ADD COLUMN decision_history_json TEXT NOT NULL DEFAULT \'[]\'');
  }
  if (!columnExists('players', 'visited_nodes_json')) {
    database.run('ALTER TABLE players ADD COLUMN visited_nodes_json TEXT NOT NULL DEFAULT \'[]\'');
  }

  if (!columnExists('locations', 'event_probabilities_json')) {
    database.run('ALTER TABLE locations ADD COLUMN event_probabilities_json TEXT NOT NULL DEFAULT \'{}\'');
  }
  if (!columnExists('players', 'stage_progress_json')) {
    database.run('ALTER TABLE players ADD COLUMN stage_progress_json TEXT NOT NULL DEFAULT \'{}\'');
  }

  if (!columnExists('players', 'current_location')) {
    database.run("ALTER TABLE players ADD COLUMN current_location TEXT NOT NULL DEFAULT ''");
  }

  if (!columnExists('players', 'activity_history_json')) {
    database.run("ALTER TABLE players ADD COLUMN activity_history_json TEXT NOT NULL DEFAULT '[]'");
  }
  if (!columnExists('choices', 'hide_after_use')) {
    database.run('ALTER TABLE choices ADD COLUMN hide_after_use INTEGER NOT NULL DEFAULT 0');
  }

  if (!columnExists('players', 'consumed_chapters_json')) {
    database.run("ALTER TABLE players ADD COLUMN consumed_chapters_json TEXT NOT NULL DEFAULT '[]'");
  }
  if (!columnExists('players', 'pending_next_chapter')) {
    database.run('ALTER TABLE players ADD COLUMN pending_next_chapter TEXT DEFAULT NULL');
  }

  if (!columnExists('players', 'chapter_actions_json')) {
    database.run("ALTER TABLE players ADD COLUMN chapter_actions_json TEXT NOT NULL DEFAULT '{}'");
  }

  if (!columnExists('players', 'last_heartbeat')) {
    database.run("ALTER TABLE players ADD COLUMN last_heartbeat TEXT NOT NULL DEFAULT ''");
    database.run("UPDATE players SET last_heartbeat = datetime('now','localtime') WHERE last_heartbeat = ''");
  }

  database.run(`
    CREATE TABLE IF NOT EXISTS pk_challenges (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      attacker_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
      defender_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
      status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','accepted','rejected','expired')),
      created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
      resolved_at TEXT
    )
  `);

  database.run(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT UNIQUE NOT NULL,
      email TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      role TEXT NOT NULL DEFAULT 'player',
      created_at TEXT DEFAULT (datetime('now','localtime')),
      updated_at TEXT DEFAULT (datetime('now','localtime'))
    )
  `);
  if (!columnExists('players', 'user_id')) {
    database.run('ALTER TABLE players ADD COLUMN user_id INTEGER REFERENCES users(id)');
  }

  // Additional indexes
  database.run('CREATE INDEX IF NOT EXISTS idx_players_user_id ON players(user_id)');
  database.run('CREATE INDEX IF NOT EXISTS idx_choices_chapter_type ON choices(chapter_key, choice_type)');
  database.run('CREATE INDEX IF NOT EXISTS idx_exploration_events_stage_type ON exploration_events(stage_key, event_type)');
  database.run('CREATE INDEX IF NOT EXISTS idx_chapters_main_order ON chapters(main_chapter_key, order_index)');

  // Quest tables — daily and weekly quests
  database.run(`
    CREATE TABLE IF NOT EXISTS daily_quests (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
      quest_key TEXT NOT NULL,
      quest_type TEXT NOT NULL DEFAULT 'daily',
      quest_name TEXT NOT NULL DEFAULT '',
      description TEXT NOT NULL DEFAULT '',
      event_type TEXT NOT NULL DEFAULT '',
      progress INTEGER NOT NULL DEFAULT 0,
      target INTEGER NOT NULL DEFAULT 0,
      claimed INTEGER NOT NULL DEFAULT 0,
      date_assigned TEXT NOT NULL DEFAULT '',
      rewards_json TEXT NOT NULL DEFAULT '{}',
      params_json TEXT DEFAULT NULL
    )
  `);
  database.run(`
    CREATE TABLE IF NOT EXISTS weekly_quests (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
      quest_key TEXT NOT NULL,
      quest_type TEXT NOT NULL DEFAULT 'weekly',
      quest_name TEXT NOT NULL DEFAULT '',
      description TEXT NOT NULL DEFAULT '',
      event_type TEXT NOT NULL DEFAULT '',
      progress INTEGER NOT NULL DEFAULT 0,
      target INTEGER NOT NULL DEFAULT 0,
      claimed INTEGER NOT NULL DEFAULT 0,
      date_assigned TEXT NOT NULL DEFAULT '',
      rewards_json TEXT NOT NULL DEFAULT '{}',
      params_json TEXT DEFAULT NULL
    )
  `);
  database.run('CREATE INDEX IF NOT EXISTS idx_daily_quests_player_date ON daily_quests(player_id, date_assigned)');
  database.run('CREATE INDEX IF NOT EXISTS idx_weekly_quests_player_date ON weekly_quests(player_id, date_assigned)');

  // Phase 2: world_state singleton
  database.run(`
    CREATE TABLE IF NOT EXISTS world_state (
      id INTEGER PRIMARY KEY CHECK(id = 1),
      world_line_shift REAL NOT NULL DEFAULT 0,
      ripple_decay_rate REAL NOT NULL DEFAULT 0.01,
      last_decay_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
      global_event_triggered_at_10 INTEGER NOT NULL DEFAULT 0,
      global_event_triggered_at_25 INTEGER NOT NULL DEFAULT 0,
      global_event_triggered_at_50 INTEGER NOT NULL DEFAULT 0,
      global_event_triggered_at_100 INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
    )
  `);
  var wsRow = database.prepare('SELECT id FROM world_state WHERE id = 1').get();
  if (!wsRow) {
    database.prepare('INSERT INTO world_state (id) VALUES (1)').run();
  }

  if (!columnExists('world_state', 'anomaly_location')) {
    database.run("ALTER TABLE world_state ADD COLUMN anomaly_location TEXT DEFAULT NULL");
  }
  if (!columnExists('world_state', 'anomaly_intensity')) {
    database.run("ALTER TABLE world_state ADD COLUMN anomaly_intensity INTEGER NOT NULL DEFAULT 0");
  }
  if (!columnExists('world_state', 'dominant_constellation')) {
    database.run("ALTER TABLE world_state ADD COLUMN dominant_constellation TEXT DEFAULT NULL");
  }

  // Phase 6: prestige columns
  if (!columnExists('players', 'prestige_level')) {
    database.run('ALTER TABLE players ADD COLUMN prestige_level INTEGER NOT NULL DEFAULT 0');
  }
  if (!columnExists('players', 'prestige_bonus_json')) {
    database.run("ALTER TABLE players ADD COLUMN prestige_bonus_json TEXT NOT NULL DEFAULT '{}'");
  }

  // Phase 3: Initialize constellation factions
  var facCount = database.prepare('SELECT COUNT(*) as c FROM constellation_factions').get().c;
  if (facCount === 0) {
    var factions = [
      { key: 'golden_sun', name: '金乌神教' },
      { key: 'black_flame_dragon', name: '黑焰龙渊' },
      { key: 'demon_judge_of_fire', name: '火之审判庭' },
      { key: 'abyss_eye', name: '深渊凝视者' },
      { key: 'wheel_of_fate', name: '命运编织会' },
      { key: 'queen_of_underworld', name: '冥界女王府' },
      { key: 'maritime_war_god', name: '海上战神盟' },
      { key: 'star_stream_watcher', name: '星流守望塔' }
    ];
    var insertFaction = database.prepare(
      'INSERT INTO constellation_factions (constellation_key, faction_name) VALUES (?, ?)'
    );
    for (var fi = 0; fi < factions.length; fi++) {
      insertFaction.run(factions[fi].key, factions[fi].name);
    }
    console.log('Phase 3: 8 constellation factions initialized.');
  }

  // Round 11: Migrate help_bounties table + pk_challenges mode column + player daily help limits
  database.run(`
    CREATE TABLE IF NOT EXISTS help_bounties (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      owner_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
      monster_key TEXT NOT NULL,
      location_key TEXT NOT NULL DEFAULT '',
      monster_name TEXT NOT NULL DEFAULT '',
      share_percent INTEGER NOT NULL DEFAULT 50 CHECK(share_percent >= 10 AND share_percent <= 90),
      status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','accepted','resolved','expired','cancelled')),
      helper_id INTEGER REFERENCES players(id) ON DELETE SET NULL,
      bounty_rewards_json TEXT NOT NULL DEFAULT '{}',
      created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
      resolved_at TEXT
    )
  `);
  database.run('CREATE INDEX IF NOT EXISTS idx_help_bounties_status ON help_bounties(status)');
  database.run('CREATE INDEX IF NOT EXISTS idx_help_bounties_owner ON help_bounties(owner_id)');

  if (!columnExists('pk_challenges', 'mode')) {
    database.run("ALTER TABLE pk_challenges ADD COLUMN mode TEXT NOT NULL DEFAULT 'spar'");
  }

  if (!columnExists('chat_messages', 'msg_type')) {
    database.run("ALTER TABLE chat_messages ADD COLUMN msg_type TEXT NOT NULL DEFAULT 'chat'");
  }

  if (!columnExists('players', 'daily_help_count')) {
    database.run("ALTER TABLE players ADD COLUMN daily_help_count INTEGER NOT NULL DEFAULT 0");
  }
  if (!columnExists('players', 'daily_assist_count')) {
    database.run("ALTER TABLE players ADD COLUMN daily_assist_count INTEGER NOT NULL DEFAULT 0");
  }
  if (!columnExists('players', 'help_date')) {
    database.run("ALTER TABLE players ADD COLUMN help_date TEXT NOT NULL DEFAULT ''");
  }

  // Equipment durability columns
  if (!columnExists('player_equipment', 'durability')) {
    database.run("ALTER TABLE player_equipment ADD COLUMN durability INTEGER NOT NULL DEFAULT 100");
  }
  if (!columnExists('player_equipment', 'max_durability')) {
    database.run("ALTER TABLE player_equipment ADD COLUMN max_durability INTEGER NOT NULL DEFAULT 100");
  }
  // Phase 1: Currency merge migration (scenarioProof→story_fragments, kingToken→abyssMark, delete finalPage)
  try {
    var players = database.prepare('SELECT id, story_fragments, breakthrough_resources_json FROM players').all();
    for (var pi = 0; pi < players.length; pi++) {
      var p = players[pi];
      var res = JSON.parse(p.breakthrough_resources_json || '{}');
      var newFragments = (p.story_fragments || 0);
      var changed = false;
      if (res.scenarioProof) {
        newFragments += res.scenarioProof * 10;
        delete res.scenarioProof;
        changed = true;
      }
      if (res.kingToken) {
        res.abyssMark = (res.abyssMark || 0) + res.kingToken;
        delete res.kingToken;
        changed = true;
      }
      if (res.finalPage !== undefined) {
        delete res.finalPage;
        changed = true;
      }
      if (changed) {
        database.prepare('UPDATE players SET story_fragments = ?, breakthrough_resources_json = ? WHERE id = ?')
          .run(newFragments, JSON.stringify(res), p.id);
      }
    }
    console.log('Phase 1 currency migration complete.');
  } catch (e) {
    console.log('Phase 1 migration skipped:', e.message);
  }
}

module.exports = { initDb, getDb, saveDb, closeDb, beginTransaction, commitTransaction, rollbackTransaction };