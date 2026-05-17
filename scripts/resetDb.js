// resetDb.js – 删除旧数据库并重新初始化 + seed
const fs = require('fs');
const path = require('path');

const DB_PATH = path.join(__dirname, '..', 'data', 'game.db');

// Step 1: 删除旧数据库
if (fs.existsSync(DB_PATH)) {
  fs.unlinkSync(DB_PATH);
  console.log('[reset-db] 已删除 data/game.db');
}

// Step 2: 重新初始化
async function main() {
  const { initDb, getDb, closeDb } = require('../db/database');
  await initDb();
  const db = getDb();

  // Step 3: 执行全部 seed
  console.log('[reset-db] 开始 seed...');
  const { seedStory } = require('../data/seedStory');
  const { seedTitles } = require('../data/seedTitles');
  const { seedEndings } = require('../data/seedEndings');
  const { seedLocations } = require('../data/seedLocations');
  const { seedMonsters } = require('../data/seedMonsters');
  const { seedItems } = require('../data/seedItems');
  const { seedEquipment } = require('../data/seedEquipment');
  const { seedSkills } = require('../data/seedSkills');
  const { seedMainChapters } = require('../data/seedMainChapters');

  seedStory(db);
  seedTitles(db);
  seedEndings(db);
  seedLocations(db);
  seedMonsters(db);
  seedItems(db);
  seedEquipment(db);
  seedSkills(db);
  seedMainChapters(db);

  // Step 4: 输出各表数量
  const tables = [
    'chapters', 'choices', 'titles', 'endings', 'locations', 'main_chapters',
    'monsters', 'items', 'equipment', 'skills',
    'players', 'player_inventory', 'player_equipment', 'player_skills',
    'battle_logs', 'exploration_logs', 'pk_records', 'rankings',
    'saves', 'authorized_sources', 'story_drafts'
  ];

  console.log('\n[reset-db] 各表数据量:');
  for (const table of tables) {
    try {
      const row = db.prepare(`SELECT COUNT(*) as c FROM ${table}`).get();
      const c = row ? row.c : 0;
      if (c > 0) console.log(`  ${table}: ${c}`);
    } catch (e) {
      // table may not exist yet
    }
  }

  closeDb();
  console.log('\n[reset-db] 数据库重置完成.');
}

main().catch(e => {
  console.error('[reset-db] 失败:', e);
  process.exit(1);
});
