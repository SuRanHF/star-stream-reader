const fs = require('fs');
const path = require('path');

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, '..', 'data');
const LOG_PATH = path.join(DATA_DIR, 'admin-actions.json');

function readLog() {
  try {
    const raw = fs.readFileSync(LOG_PATH, 'utf8');
    return JSON.parse(raw);
  } catch (e) {
    return [];
  }
}

function writeLog(entries) {
  const dir = path.dirname(LOG_PATH);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(LOG_PATH, JSON.stringify(entries, null, 2), 'utf8');
}

function generateId() {
  return 'alog_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
}

function logAction(adminName, action, targetPlayerId, payload) {
  const entries = readLog();
  const entry = {
    id: generateId(),
    admin: adminName || 'unknown',
    action,
    targetPlayerId: targetPlayerId || null,
    payload: payload || {},
    createdAt: new Date().toISOString()
  };
  entries.push(entry);
  // Keep last 500 entries
  if (entries.length > 500) entries.splice(0, entries.length - 500);
  writeLog(entries);
  return entry;
}

function getRecent(limit) {
  const entries = readLog();
  return entries.slice(-(limit || 50)).reverse();
}

module.exports = { logAction, getRecent, readLog };
