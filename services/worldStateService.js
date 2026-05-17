// 世界状态汇总服务 (Round 6: Star Stream Broadcast)
// 提供全服数据摘要供 AI Director 和 fallback 生成器使用
const { getDb } = require('../db/database');

function getWorldStateSummary() {
  const db = getDb();

  const playerCount = db.prepare('SELECT COUNT(*) as c FROM players').get().c;
  const allPlayers = db.prepare('SELECT * FROM players').all();

  // 活跃玩家: 最近 24 小时有更新的
  const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const activePlayers = allPlayers.filter(p => p.updated_at >= oneDayAgo);
  const activeCount = activePlayers.length;

  // 等级统计
  const levels = allPlayers.map(p => {
    try { const s = JSON.parse(p.stats_json); return s.level || 1; }
    catch (e) { return 1; }
  });
  const averageLevel = levels.length > 0 ? Math.round(levels.reduce((a, b) => a + b, 0) / levels.length) : 0;
  const highestLevel = levels.length > 0 ? Math.max(...levels) : 0;

  // 阶段分布
  const stageDist = {};
  allPlayers.forEach(p => {
    const stage = p.current_main_chapter || 'main_ch01_paid_service';
    stageDist[stage] = (stageDist[stage] || 0) + 1;
  });

  // 世界线偏移和频道热度
  let totalWLO = 0;
  let totalCH = 0;
  allPlayers.forEach(p => {
    try { const s = JSON.parse(p.stats_json); totalWLO += s.worldLineShift || 0; totalCH += s.channelHeat || 0; }
    catch (e) { /* ignore */ }
  });
  const averageWorldLineShift = allPlayers.length > 0 ? Math.round(totalWLO / allPlayers.length * 100) / 100 : 0;
  const averageChannelHeat = allPlayers.length > 0 ? Math.round(totalCH / allPlayers.length * 100) / 100 : 0;

  // 最常见称号
  const titleCount = {};
  allPlayers.forEach(p => {
    try { const t = JSON.parse(p.titles_json); t.forEach(tt => { titleCount[tt] = (titleCount[tt] || 0) + 1; }); }
    catch (e) { /* ignore */ }
  });
  const mostCommonTitles = Object.entries(titleCount)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .map(([k, v]) => ({ title_key: k, count: v }));

  // 近期探索统计
  const recentExplorations = db.prepare(
    "SELECT COUNT(*) as c FROM exploration_logs WHERE created_at >= ?"
  ).get(oneDayAgo).c;

  // 近期战斗统计
  const recentBattles = db.prepare(
    "SELECT COUNT(*) as c FROM battle_logs WHERE created_at >= ?"
  ).get(oneDayAgo).c;

  // 近期 PK 统计
  const recentPKs = db.prepare(
    "SELECT COUNT(*) as c FROM pk_records WHERE created_at >= ?"
  ).get(oneDayAgo).c;

  // Boss 击杀统计
  const totalBossKills = allPlayers.reduce((sum, p) => {
    try { return sum + JSON.parse(p.boss_kills_json || '[]').length; }
    catch (e) { return sum; }
  }, 0);

  // 各地图探索统计
  const locationStats = db.prepare(
    "SELECT location_key, COUNT(*) as c FROM exploration_logs GROUP BY location_key"
  ).all();

  // 资源经济
  const totalCoins = allPlayers.reduce((sum, p) => sum + (p.coins || 0), 0);
  const totalStoryFragments = allPlayers.reduce((sum, p) => sum + (p.story_fragments || 0), 0);

  // 当前活跃放送
  const activeBroadcasts = db.prepare(
    "SELECT * FROM broadcast_events WHERE status = 'active'"
  ).all();

  // 各 event_type 的历史计数
  const eventTypeHistory = {};
  try {
    const typeRows = db.prepare(
      "SELECT event_type, COUNT(*) as c FROM broadcast_events WHERE status IN ('completed','rewarded','failed','expired') GROUP BY event_type"
    ).all();
    typeRows.forEach(r => { eventTypeHistory[r.event_type] = r.c; });
  } catch (e) { /* table may not exist yet */ }

  return {
    activePlayers: activeCount,
    totalPlayers: playerCount,
    averageLevel,
    highestLevel,
    currentStageDistribution: stageDist,
    averageWorldLineShift,
    averageChannelHeat,
    mostCommonTitles,
    recentExplorationStats: { last24h: recentExplorations },
    recentBattleStats: { last24h: recentBattles },
    recentPkStats: { last24h: recentPKs },
    totalBossKills,
    locationExplorationStats: locationStats,
    currentActiveBroadcasts: activeBroadcasts.length,
    resourceEconomy: {
      averageCoins: playerCount > 0 ? Math.round(totalCoins / playerCount) : 0,
      averageStoryFragments: playerCount > 0 ? Math.round(totalStoryFragments / playerCount * 10) / 10 : 0
    },
    eventTypeHistory
  };
}

function getPlayerStateSamples(limit) {
  const db = getDb();
  const players = db.prepare('SELECT * FROM players ORDER BY updated_at DESC LIMIT ?').all(limit || 20);

  return players.map(p => {
    let stats = {};
    try { stats = JSON.parse(p.stats_json); } catch (e) { /* ignore */ }
    let titles = [];
    try { titles = JSON.parse(p.titles_json); } catch (e) { /* ignore */ }
    let permFlags = {};
    try { permFlags = JSON.parse(p.permanent_flags_json); } catch (e) { /* ignore */ }
    let resources = {};
    try { resources = JSON.parse(p.breakthrough_resources_json); } catch (e) { /* ignore */ }
    let completedChapters = [];
    try { completedChapters = JSON.parse(p.completed_chapters_json); } catch (e) { /* ignore */ }

    const combatPower = (stats.attack || 10) + (stats.defense || 5) + (stats.speed || 10) + (stats.level || 1) * 2;

    return {
      playerId: p.id,
      playerName: p.player_name,
      level: stats.level || 1,
      currentStage: p.current_main_chapter || 'main_ch01_paid_service',
      currentChapter: p.current_chapter || '',
      titles: titles.slice(0, 5),
      combatPower,
      rating: stats.rating || 1000,
      worldLineShift: stats.worldLineShift || 0,
      channelHeat: stats.channelHeat || 0,
      recentActions: extractRecentActions(p),
      keyResources: {
        coins: p.coins || 0,
        storyFragments: p.story_fragments || 0,
        breakthroughResources: resources,
        completedStages: completedChapters.length
      }
    };
  });
}

function extractRecentActions(player) {
  const actions = [];
  try {
    const logs = JSON.parse(player.logs_json || '[]');
    const recent = logs.slice(-5);
    actions.push(...recent.map(l => l.msg));
  } catch (e) { /* ignore */ }
  return actions;
}

function getActivePlayerCount() {
  const db = getDb();
  const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  return db.prepare("SELECT COUNT(*) as c FROM players WHERE updated_at >= ?").get(oneDayAgo).c;
}

function getResourceEconomy() {
  const db = getDb();
  const players = db.prepare('SELECT coins, story_fragments FROM players').all();
  const totalCoins = players.reduce((s, p) => s + (p.coins || 0), 0);
  const totalSF = players.reduce((s, p) => s + (p.story_fragments || 0), 0);
  return {
    averageCoins: players.length > 0 ? Math.round(totalCoins / players.length) : 0,
    averageStoryFragments: players.length > 0 ? Math.round(totalSF / players.length * 10) / 10 : 0,
    totalPlayers: players.length
  };
}

module.exports = { getWorldStateSummary, getPlayerStateSamples, getActivePlayerCount, getResourceEconomy };
