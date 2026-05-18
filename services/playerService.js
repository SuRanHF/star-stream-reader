const { getDb } = require('../db/database');

const defaultStats = {
  // 剧情属性
  intelligence: 0, combat: 0, leadership: 0, bond: 0, cruelty: 0, insight: 0,
  // RPG 基础属性
  level: 1, exp: 0, hp: 100, maxHp: 100,
  attack: 10, defense: 5, speed: 10,
  critRate: 0.05, critDamage: 1.5,
  // 探索属性
  stamina: 50, maxStamina: 50,
  explorationPower: 1, luck: 1, dropRate: 0,
  // PK 属性
  rating: 1000, pkWins: 0, pkLosses: 0, pkStreak: 0,
  // 世界状态
  worldLineShift: 0, channelHeat: 0,
  // 自由属性点
  freePoints: 40,
  allocatedAtk: 0, allocatedDef: 0, allocatedSpd: 0, allocatedCrit: 0,
  // 背后星
  constellation: null
};

function migratePlayerStats(stats) {
  const defaults = defaultStats;
  for (const [k, v] of Object.entries(defaults)) {
    if (!(k in stats)) stats[k] = v;
  }
  return stats;
}

function create(playerName, userId) {
  const db = getDb();
  const name = playerName || '未命名读者';
  const result = db.prepare(`
    INSERT INTO players (player_name, stats_json, story_flags_json, permanent_flags_json,
      route_history_json, titles_json, title_progress_json, relationships_json, sponsors_json, logs_json,
      unlocked_chapters_json, completed_chapters_json, current_main_chapter, breakthrough_resources_json, boss_kills_json,
      decision_history_json, visited_nodes_json, stage_progress_json, current_location, activity_history_json, user_id)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(name,
    JSON.stringify(defaultStats),
    JSON.stringify({}),
    JSON.stringify({}),
    JSON.stringify([]),
    JSON.stringify([]),
    JSON.stringify({}),
    JSON.stringify({}),
    JSON.stringify([]),
    JSON.stringify([]),
    JSON.stringify([]),
    JSON.stringify([]),
    'main_ch01_paid_service',
    JSON.stringify({}),
    JSON.stringify([]),
    JSON.stringify([]),
    JSON.stringify([]),
    JSON.stringify({
      storyEventsTriggered: [],
      sideEventsTriggered: [],
      bossClues: {},
      opportunityEventsTriggered: [],
      hiddenEventsTriggered: [],
      storyPity: 0,
      explorationsByLocation: {},
      finalStoryEventTriggered: null,
      lastExplorationResultType: null
    }),
    '',
    JSON.stringify([]),
    userId || null
  );

  const playerId = result.lastInsertRowid;

  // Grant starter equipment
  const starterEquipment = ['rusty_dagger', 'station_guard_coat', 'old_reader_badge'];
  const insertInv = db.prepare('INSERT INTO player_inventory (player_id, item_key, quantity) VALUES (?, ?, 1)');
  const insertEquip = db.prepare('INSERT OR IGNORE INTO player_equipment (player_id, equipment_key, slot) VALUES (?, ?, ?)');

  for (const equipmentKey of starterEquipment) {
    insertInv.run(playerId, equipmentKey);
    const eqDef = db.prepare('SELECT slot FROM equipment WHERE equipment_key = ?').get(equipmentKey);
    if (eqDef) {
      insertEquip.run(playerId, equipmentKey, eqDef.slot);
    }
  }

  addLog(playerId, '获得初始装备: 生锈短刀、车站守卫外套、旧读者徽章');
  return get(playerId);
}

function get(id) {
  const db = getDb();
  const row = db.prepare('SELECT * FROM players WHERE id = ?').get(id);
  if (!row) return null;
  return parsePlayerRow(row);
}

function getRaw(id) {
  const db = getDb();
  return db.prepare('SELECT * FROM players WHERE id = ?').get(id);
}

function update(id, fields) {
  const db = getDb();
  const sets = [];
  const vals = [];
  for (const [k, v] of Object.entries(fields)) {
    if (k === 'id') continue;
    sets.push(`${k} = ?`);
    vals.push(typeof v === 'object' ? JSON.stringify(v) : v);
  }
  if (sets.length === 0) return;
  sets.push("updated_at = datetime('now','localtime')");
  db.prepare(`UPDATE players SET ${sets.join(', ')} WHERE id = ?`).run(...vals, id);
}

function reset(id) {
  const db = getDb();
  const player = getRaw(id);
  if (!player) return null;
  const permFlags = player.permanent_flags_json;
  // 保留突破资源和Boss击杀记录
  const breakthroughRes = player.breakthrough_resources_json || '{}';
  const bossKills = player.boss_kills_json || '[]';
  const unlockedChapters = player.unlocked_chapters_json || '[]';
  const completedChapters = player.completed_chapters_json || '[]';
  db.prepare(`
    UPDATE players SET current_chapter = 'ch1_01_last_train', coins = 0, story_fragments = 0,
      stats_json = ?, relationships_json = ?, route_history_json = '[]',
      story_flags_json = '{}', permanent_flags_json = ?, titles_json = '[]',
      title_progress_json = '{}', sponsors_json = '[]', logs_json = '[]',
      current_main_chapter = 'main_ch01_paid_service',
      consumed_chapters_json = '[]', pending_next_chapter = NULL,
      chapter_actions_json = '{}',
      updated_at = datetime('now','localtime')
    WHERE id = ?
  `).run(JSON.stringify(defaultStats), JSON.stringify({}), permFlags, id);
  return get(id);
}

function addLog(id, message) {
  const player = getRaw(id);
  if (!player) return;
  const logs = JSON.parse(player.logs_json);
  logs.push({ time: new Date().toISOString(), msg: message });
  if (logs.length > 200) logs.shift();
  update(id, { logs_json: logs });
}

function parsePlayerRow(row) {
  const db = getDb();
  const rawStats = JSON.parse(row.stats_json);
  const stats = migratePlayerStats(rawStats);

  // Resolve location name
  let current_location_name = '';
  if (row.current_location) {
    const loc = db.prepare('SELECT name FROM locations WHERE location_key = ?').get(row.current_location);
    current_location_name = loc ? loc.name : '';
  }

  // Resolve stage name and progress
  let stage_name = '';
  let stage_order = 1;
  let stage_total_nodes = 0;
  let stage_visited_nodes = 0;
  if (row.current_main_chapter) {
    const mc = db.prepare(
      'SELECT chapter_name, order_index, story_chapter_keys_json FROM main_chapters WHERE chapter_key = ?'
    ).get(row.current_main_chapter);
    if (mc) {
      stage_name = mc.chapter_name;
      stage_order = mc.order_index || 1;
      const storyKeys = JSON.parse(mc.story_chapter_keys_json || '[]');
      stage_total_nodes = storyKeys.length;
      const visited = JSON.parse(row.visited_nodes_json || '[]');
      stage_visited_nodes = visited.filter(n => storyKeys.includes(n)).length;
    }
  }

  // Resolve title details
  const titleKeys = JSON.parse(row.titles_json);
  const title_details = titleKeys.map(key => {
    const t = db.prepare('SELECT name, rarity, description FROM titles WHERE title_key = ?').get(key);
    return t ? { title_key: key, name: t.name, rarity: t.rarity, description: t.description } : { title_key: key, name: key, rarity: 'common', description: '' };
  });

  // Resolve equipment
  const { getEquippedMap } = require('./equipmentService');
  const equipment = getEquippedMap(row.id);

  return {
    id: row.id,
    player_name: row.player_name,
    current_chapter: row.current_chapter,
    coins: row.coins,
    story_fragments: row.story_fragments,
    stats,
    relationships: JSON.parse(row.relationships_json),
    route_history: JSON.parse(row.route_history_json),
    story_flags: JSON.parse(row.story_flags_json),
    permanent_flags: JSON.parse(row.permanent_flags_json),
    titles: titleKeys,
    title_details,
    title_progress: JSON.parse(row.title_progress_json),
    sponsors: JSON.parse(row.sponsors_json),
    logs: JSON.parse(row.logs_json),
    unlocked_chapters: JSON.parse(row.unlocked_chapters_json || '[]'),
    completed_chapters: JSON.parse(row.completed_chapters_json || '[]'),
    current_main_chapter: row.current_main_chapter || 'main_ch01_paid_service',
    stage_name,
    stage_order,
    stage_total_nodes,
    stage_visited_nodes,
    breakthrough_resources: JSON.parse(row.breakthrough_resources_json || '{}'),
    boss_kills: JSON.parse(row.boss_kills_json || '[]'),
    decision_history: JSON.parse(row.decision_history_json || '[]'),
    visited_nodes: JSON.parse(row.visited_nodes_json || '[]'),
    stage_progress: JSON.parse(row.stage_progress_json || '{}'),
    current_location: row.current_location || '',
    current_location_name,
    activity_history: JSON.parse(row.activity_history_json || '[]'),
    equipment,
    user_id: row.user_id || null,
    created_at: row.created_at,
    updated_at: row.updated_at
  };
}

function isResting(playerId) {
  const raw = getRaw(playerId);
  if (!raw) return false;
  const stats = JSON.parse(raw.stats_json || '{}');
  return !!(stats.isResting);
}

function assertNotResting(playerId, actionName) {
  if (isResting(playerId)) {
    const err = new Error('你正在休息，无法进行该操作。');
    err.code = 'PLAYER_RESTING';
    err.actionName = actionName;
    throw err;
  }
}

const CONSTELLATIONS = {
  golden_sun: {
    key: 'golden_sun',
    name: '金乌',
    title: '最古之金乌',
    description: '自天地初开便存在的三足金乌，是这个世界最古老的神话。赐予信徒灼热的火之祝福，攻击力与暴击伤害大幅提升。',
    effects: { atk: 5, critDamage: 0.2 }
  },
  black_flame_dragon: {
    key: 'black_flame_dragon',
    name: '深渊的黑炎龙',
    title: '深渊的黑炎龙',
    description: '栖息于星之深渊的黑焰巨龙，毁灭即为它的本质。15岁的黑焰皇帝。极致攻击力，防御略显薄弱。',
    effects: { atk: 8, def: -2 }
  },
  demon_judge_of_fire: {
    key: 'demon_judge_of_fire',
    name: '恶魔般的火之审判者',
    title: '恶魔般的火之审判者',
    description: '燃烧地狱烈焰的大天使，对邪恶绝不手软。她爱着世间一切故事与美好。攻击力与正义感同样炽热。',
    effects: { atk: 4, critRate: 0.08, spd: 2 }
  },
  abyss_eye: {
    key: 'abyss_eye',
    name: '深渊之眼',
    title: '深渊的凝视者',
    description: '隐藏在星流深渊中的远古观测者，洞察万物本质。暴击率与洞察力大幅提升。',
    effects: { critRate: 0.1, insight: 3 }
  },
  wheel_of_fate: {
    key: 'wheel_of_fate',
    name: '命运之轮',
    title: '因果的编织者',
    description: '编织因果之线的神秘存在，在星流之轮上刻下命运的轨迹。幸运与掉落率提升。',
    effects: { luck: 3, dropRate: 0.1 }
  },
  queen_of_underworld: {
    key: 'queen_of_underworld',
    name: '冥界的女王',
    title: '冥界的女王',
    description: '掌管冥界暗之权力的女王，她的眷顾让死亡不再是终点。复活代价减半，羁绊增强。',
    effects: { reviveDiscount: 0.5, bond: 3 }
  },
  maritime_war_god: {
    key: 'maritime_war_god',
    name: '海上不败战神',
    title: '海上不败战神',
    description: '大海上从未败过的战争之神。忠诚、正义，永远守护自己所信之物。防御与速度大幅提升。',
    effects: { def: 5, spd: 3, atk: 1 }
  },
  star_stream_watcher: {
    key: 'star_stream_watcher',
    name: '星流观测者',
    title: '星流的守望者',
    description: '默默守望星之流不知多少纪元的古老存在，给予均衡的庇佑。全战斗属性小幅提升，硬币获得增加。',
    effects: { atk: 2, def: 2, spd: 2, coinMultiplier: 0.1 }
  }
};

function getConstellations() {
  return Object.values(CONSTELLATIONS).map(c => ({
    key: c.key,
    name: c.name,
    title: c.title,
    description: c.description,
    effects: c.effects
  }));
}

function selectConstellation(playerId, constellationKey) {
  if (!CONSTELLATIONS[constellationKey]) return { error: { code: 'INVALID_CONSTELLATION', message: '该星座不存在' } };
  const player = getRaw(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };
  const stats = JSON.parse(player.stats_json);
  if (stats.constellation) return { error: { code: 'ALREADY_SELECTED', message: '已选择过背后星，无法更改' } };
  stats.constellation = constellationKey;
  update(playerId, { stats_json: stats });
  const c = CONSTELLATIONS[constellationKey];
  addLog(playerId, `选择了背后星: ${c.title}（${c.name}）`);
  return { constellation: { key: constellationKey, name: c.name, title: c.title, effects: c.effects } };
}

function revivePlayer(playerId, method) {
  const player = get(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };
  const stats = { ...player.stats };
  if (!stats.isDead) return { error: { code: 'NOT_DEAD', message: '你还活着，无需复活' } };

  const level = stats.level || 1;
  const coinCost = Math.round(100 * level * (stats.constellation === 'queen_of_underworld' ? 0.5 : 1));
  let success = false;
  let message = '';

  if (method === 'coins') {
    if ((player.coins || 0) < coinCost) {
      return { error: { code: 'NOT_ENOUGH_COINS', message: `金币不足，需要 ${coinCost} 枚金币` } };
    }
    const newCoins = player.coins - coinCost;
    stats.isDead = false;
    stats.hp = Math.round(stats.maxHp * 0.3);
    update(playerId, { stats_json: stats, coins: newCoins });
    addLog(playerId, `向冥界女王支付了 ${coinCost} 金币，从冥界归来。`);
    success = true;
    message = `支付了 ${coinCost} 金币，灵魂回归肉身。`;
  } else if (method === 'title') {
    const titles = [...(player.titles || [])];
    if (titles.length === 0) {
      return { error: { code: 'NO_TITLES', message: '没有任何称号可以献祭' } };
    }
    const sacrificed = titles.pop();
    stats.isDead = false;
    stats.hp = Math.round(stats.maxHp * 0.3);
    update(playerId, { stats_json: stats, titles_json: titles });
    addLog(playerId, `献祭了称号「${sacrificed}」作为回归的代价。`);
    success = true;
    message = `献祭了称号「${sacrificed}」，灵魂被冥界女王放回。`;
  } else {
    return { error: { code: 'INVALID_METHOD', message: '无效的复活方式' } };
  }

  return { success, message, coinCost, method, player: get(playerId) };
}

function getConstellationBonus(stats) {
  const key = stats.constellation;
  if (!key || !CONSTELLATIONS[key]) return {};
  return CONSTELLATIONS[key].effects;
}

module.exports = { create, get, getRaw, update, reset, addLog, isResting, assertNotResting,
  defaultStats, migratePlayerStats, getConstellations, selectConstellation, getConstellationBonus,
  revivePlayer, CONSTELLATIONS };
