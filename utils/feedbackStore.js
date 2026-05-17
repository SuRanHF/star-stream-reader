const fs = require('fs');
const path = require('path');

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, '..', 'data');
const FEEDBACK_PATH = path.join(DATA_DIR, 'feedback.json');

function read() {
  try {
    const raw = fs.readFileSync(FEEDBACK_PATH, 'utf8');
    return JSON.parse(raw);
  } catch (e) {
    return [];
  }
}

function write(entries) {
  const dir = path.dirname(FEEDBACK_PATH);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(FEEDBACK_PATH, JSON.stringify(entries, null, 2), 'utf8');
}

function generateId() {
  return 'fb_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
}

function add(fields) {
  const entries = read();
  const entry = {
    id: generateId(),
    nickname: fields.nickname || '',
    type: fields.type || 'other',
    content: fields.content || '',
    page: fields.page || '',
    playerId: fields.playerId || null,
    status: 'new',
    note: '',
    createdAt: new Date().toISOString()
  };
  entries.push(entry);
  write(entries);
  return entry;
}

function update(id, fields) {
  const entries = read();
  const idx = entries.findIndex(e => e.id === id);
  if (idx < 0) return null;
  if (fields.status !== undefined) entries[idx].status = fields.status;
  if (fields.note !== undefined) entries[idx].note = fields.note;
  write(entries);
  return entries[idx];
}

function getAll(status) {
  let entries = read();
  if (status) entries = entries.filter(e => e.status === status);
  return entries.reverse();
}

module.exports = { add, update, getAll };
