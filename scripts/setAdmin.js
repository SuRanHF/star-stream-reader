const { initDb, getDb } = require('../db/database');

async function main() {
  await initDb();
  const db = getDb();

  // Set existing "admin" user to admin role
  const existing = db.prepare("SELECT id, username, role FROM users WHERE username = 'admin'").get();
  if (existing) {
    db.prepare("UPDATE users SET role = 'admin' WHERE id = ?").run(existing.id);
    console.log(`已将 "admin" (id=${existing.id}) 设为管理员。`);
  }

  // Register 苏冉 if not exists
  const bcrypt = require('bcryptjs');
  const suran = db.prepare("SELECT id FROM users WHERE username = '苏冉'").get();
  if (!suran) {
    const hash = bcrypt.hashSync('123456', 10);
    db.prepare("INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)").run('苏冉', 'suran@game.com', hash);
    const newUser = db.prepare("SELECT id FROM users WHERE username = '苏冉'").get();
    db.prepare("UPDATE users SET role = 'admin' WHERE id = ?").run(newUser.id);
    console.log(`已注册 "苏冉" (id=${newUser.id}) 并设为管理员。密码: 123456`);
  } else {
    db.prepare("UPDATE users SET role = 'admin' WHERE id = ?").run(suran.id);
    console.log(`已将 "苏冉" (id=${suran.id}) 设为管理员。`);
  }

  // List all admins
  const admins = db.prepare("SELECT id, username, role FROM users WHERE role = 'admin'").all();
  console.log('\n当前管理员：');
  admins.forEach(u => console.log(`  ${u.username} (id=${u.id})`));
}

main().catch(e => { console.error(e); process.exit(1); });
