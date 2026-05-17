const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { getDb } = require('../db/database');

const JWT_SECRET = process.env.JWT_SECRET;

function signToken(user) {
  return jwt.sign(
    { id: user.id, username: user.username, role: user.role },
    JWT_SECRET,
    { expiresIn: '7d' }
  );
}

function register(username, email, password) {
  const db = getDb();

  if (!username || !email || !password) {
    return { error: { code: 'MISSING_FIELDS', message: '用户名、邮箱和密码为必填项' } };
  }
  if (password.length < 6) {
    return { error: { code: 'WEAK_PASSWORD', message: '密码长度不能少于6位' } };
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    return { error: { code: 'INVALID_EMAIL', message: '邮箱格式不正确' } };
  }

  // Check uniqueness
  const existingUser = db.prepare(
    'SELECT id FROM users WHERE username = ? OR email = ?'
  ).get(username, email);
  if (existingUser) {
    return { error: { code: 'DUPLICATE_USER', message: '用户名或邮箱已被注册' } };
  }

  const passwordHash = bcrypt.hashSync(password, 10);
  const result = db.prepare(
    'INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)'
  ).run(username, email, passwordHash);

  const user = db.prepare('SELECT * FROM users WHERE id = ?').get(result.lastInsertRowid);
  const token = signToken(user);

  return { user: { id: user.id, username: user.username, email: user.email, role: user.role }, token };
}

function login(usernameOrEmail, password) {
  const db = getDb();

  if (!usernameOrEmail || !password) {
    return { error: { code: 'MISSING_FIELDS', message: '请输入用户名/邮箱和密码' } };
  }

  const user = db.prepare(
    'SELECT * FROM users WHERE username = ? OR email = ?'
  ).get(usernameOrEmail, usernameOrEmail);

  if (!user) {
    return { error: { code: 'INVALID_CREDENTIALS', message: '用户名或密码错误' } };
  }

  const valid = bcrypt.compareSync(password, user.password_hash);
  if (!valid) {
    return { error: { code: 'INVALID_CREDENTIALS', message: '用户名或密码错误' } };
  }

  const token = signToken(user);

  return { user: { id: user.id, username: user.username, email: user.email, role: user.role }, token };
}

function getMe(userId) {
  const db = getDb();
  const user = db.prepare(
    'SELECT id, username, email, role, created_at FROM users WHERE id = ?'
  ).get(userId);
  if (!user) return null;

  // Find player bound to this user
  const player = db.prepare('SELECT * FROM players WHERE user_id = ?').get(userId);

  return { user, player: player || null };
}

module.exports = { register, login, getMe, signToken };
