const fs = require('fs');
const path = require('path');

const DB_PATH = path.join(__dirname, '..', 'data', 'game.db');

let db = null;
let _origPrepare = null;

function createPrepared(sql) {
  return {
    run(...params) {
      const stmt = _origPrepare(sql);
      stmt.bind(params);
      stmt.step();
      stmt.free();
      const idStmt = _origPrepare('SELECT last_insert_rowid() as id');
      let lastId = 0;
      if (idStmt.step()) lastId = idStmt.get()[0];
      idStmt.free();
      saveDb();
      return { lastInsertRowid: lastId, changes: db.getRowsModified() };
    },
    get(...params) {
      const stmt = _origPrepare(sql);
      if (params.length > 0) stmt.bind(params);
      let row = null;
      if (stmt.step()) {
        const cols = stmt.getColumnNames();
        const vals = stmt.get();
        row = {};
        cols.forEach((c, i) => { row[c] = vals[i]; });
      }
      stmt.free();
      return row;
    },
    all(...params) {
      const stmt = _origPrepare(sql);
      if (params.length > 0) stmt.bind(params);
      const cols = stmt.getColumnNames();
      const rows = [];
      while (stmt.step()) {
        const vals = stmt.get();
        const row = {};
        cols.forEach((c, i) => { row[c] = vals[i]; });
        rows.push(row);
      }
      stmt.free();
      return rows;
    }
  };
}

async function initDb() {
  if (db) return db;

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

  // Round 3 migrations: add columns if not exist
  runMigrations(db);

  saveDb();

  console.log('Database initialized at', DB_PATH);
  return db;
}

function getDb() {
  if (!db) throw new Error('Database not initialized. Call initDb() first.');
  return db;
}

function saveDb() {
  if (db) {
    const data = db.export();
    const buf = Buffer.from(data);
    const dir = path.dirname(DB_PATH);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(DB_PATH, buf);
  }
}

function closeDb() {
  if (db) {
    saveDb();
    db.close();
    db = null;
    _origPrepare = null;
  }
}

function runMigrations(database) {
  // Helper: check if column exists
  function columnExists(table, column) {
    try {
      const rows = database.prepare(`PRAGMA table_info(${table})`).all();
      return rows.some(r => r.name === column);
    } catch (e) {
      return false;
    }
  }

  // Round 3: chapters.main_chapter_key
  if (!columnExists('chapters', 'main_chapter_key')) {
    database.run('ALTER TABLE chapters ADD COLUMN main_chapter_key TEXT NOT NULL DEFAULT \'\'');
  }

  // Round 3: players extended fields
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

  // Round 4: choice type system
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

  // Round 4: stage objectives
  if (!columnExists('main_chapters', 'stage_objectives_json')) {
    database.run('ALTER TABLE main_chapters ADD COLUMN stage_objectives_json TEXT NOT NULL DEFAULT \'{}\'');
  }

  // Round 4: player decision & visited tracking
  if (!columnExists('players', 'decision_history_json')) {
    database.run('ALTER TABLE players ADD COLUMN decision_history_json TEXT NOT NULL DEFAULT \'[]\'');
  }
  if (!columnExists('players', 'visited_nodes_json')) {
    database.run('ALTER TABLE players ADD COLUMN visited_nodes_json TEXT NOT NULL DEFAULT \'[]\'');
  }

  // Round 5: exploration event system
  if (!columnExists('locations', 'event_probabilities_json')) {
    database.run('ALTER TABLE locations ADD COLUMN event_probabilities_json TEXT NOT NULL DEFAULT \'{}\'');
  }
  if (!columnExists('players', 'stage_progress_json')) {
    database.run('ALTER TABLE players ADD COLUMN stage_progress_json TEXT NOT NULL DEFAULT \'{}\'');
  }

  // Round 7: current_location for seamless exploration
  if (!columnExists('players', 'current_location')) {
    database.run("ALTER TABLE players ADD COLUMN current_location TEXT NOT NULL DEFAULT ''");
  }

  // Round 6: broadcast tables (create if not exist via init.sql IF NOT EXISTS)
  // Already covered by initDb running the full init.sql.  No ALTER needed for new tables.

  // Round 8: one-shot action + activity history
  if (!columnExists('players', 'activity_history_json')) {
    database.run("ALTER TABLE players ADD COLUMN activity_history_json TEXT NOT NULL DEFAULT '[]'");
  }
  if (!columnExists('choices', 'hide_after_use')) {
    database.run('ALTER TABLE choices ADD COLUMN hide_after_use INTEGER NOT NULL DEFAULT 0');
  }

  // Round 9: per-chapter mutual exclusivity + exploration-gated advancement
  if (!columnExists('players', 'consumed_chapters_json')) {
    database.run("ALTER TABLE players ADD COLUMN consumed_chapters_json TEXT NOT NULL DEFAULT '[]'");
  }
  if (!columnExists('players', 'pending_next_chapter')) {
    database.run('ALTER TABLE players ADD COLUMN pending_next_chapter TEXT DEFAULT NULL');
  }

  // Round 10: per-chapter action tracking (action/repeatable don't consume chapter)
  if (!columnExists('players', 'chapter_actions_json')) {
    database.run("ALTER TABLE players ADD COLUMN chapter_actions_json TEXT NOT NULL DEFAULT '{}'");
  }

  // Round 11: user authentication system
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
}

module.exports = { initDb, getDb, saveDb, closeDb };
