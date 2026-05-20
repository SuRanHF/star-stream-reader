// 星流全自动调度引擎 — 统一心跳驱动所有周期性逻辑
var _interval = null;
var _running = false;

function start() {
  if (_interval) return;
  console.log('[Scheduler] 启动调度引擎 (60s 心跳)');
  _interval = setInterval(function() {
    if (_running) return;
    _running = true;
    try {
      tick();
    } catch (e) {
      console.error('[Scheduler] tick error:', e.message);
    }
    _running = false;
  }, 60000);
  // 立即执行首次
  setTimeout(function() { tick(); }, 5000);
}

function stop() {
  if (_interval) { clearInterval(_interval); _interval = null; }
}

function tick() {
  // 1. 世界线衰减
  try { require('./worldlineService').applyDecay(); } catch (e) {}

  // 2. 过期放送自动结算
  try { autoResolveBroadcasts(); } catch (e) { console.error('[Scheduler] broadcast resolve:', e.message); }

  // 3. 无活跃放送时自动生成
  try { autoGenerateBroadcasts(); } catch (e) { console.error('[Scheduler] broadcast generate:', e.message); }

  // 4. PK 挑战过期清理
  try { require('./pkService').expireOldChallenges(); } catch (e) {}

  // 5. 营地周战结算 (每周一 0:05 触发)
  try { checkWeeklyWar(); } catch (e) {}

  // 6. 世界线热点迁移（每12小时检查一次）
  try {
    var now = Date.now();
    if (!globalThis._lastAnomalyCheck || now - globalThis._lastAnomalyCheck > 43200000) {
      globalThis._lastAnomalyCheck = now;
      require('./worldlineService').migrateWorldlineAnomaly();
    }
  } catch (e) { console.error('[Scheduler] anomaly migrate:', e.message); }
}

function autoResolveBroadcasts() {
  var db = require('../db/database').getDb();
  var now = new Date().toISOString();
  var expired = db.prepare(
    "SELECT * FROM broadcast_events WHERE status = 'active' AND end_time < ?"
  ).all(now);

  var broadcastService = require('./broadcastService');
  for (var i = 0; i < expired.length; i++) {
    var evt = expired[i];
    // 计算目标完成度决定成功/失败
    var objectives = JSON.parse(evt.objectives_json || '[]');
    var allMet = true;
    for (var j = 0; j < objectives.length; j++) {
      var total = db.prepare(
        'SELECT COALESCE(SUM(amount), 0) as s FROM broadcast_contributions WHERE event_id = ? AND contribution_type = ?'
      ).get(evt.id, objectives[j].type);
      if ((total.s || 0) < (objectives[j].target || 1)) { allMet = false; break; }
    }
    broadcastService.resolveEvent(evt.id, allMet);
    console.log('[Scheduler] Auto-resolved broadcast:', evt.title, allMet ? 'SUCCESS' : 'FAILED');
    // 广播到聊天
    try {
      var chatService = require('./chatService');
      chatService.sendMessage(0, '星之流', allMet
        ? '星流放送「' + evt.title + '」已结束 — 目标达成！星座们满意地点头。'
        : '星流放送「' + evt.title + '」已结束 — 目标未达成。星座们的视线变得更加锐利。', 'global');
    } catch (e) {}
  }
}

function autoGenerateBroadcasts() {
  var db = require('../db/database').getDb();
  var activeCount = db.prepare("SELECT COUNT(*) as c FROM broadcast_events WHERE status = 'active'").get().c;
  var draftCount = db.prepare("SELECT COUNT(*) as c FROM broadcast_events WHERE status = 'draft'").get().c;
  if (activeCount > 0 || draftCount >= 2) return;

  var worldState = require('./worldStateService').getWorldStateSummary();
  var aiDirector = require('./aiDirectorService');

  // 生成两个不同方案
  var draft1 = aiDirector.fallbackBroadcastGenerator(worldState);
  if (!draft1) return;
  var draft2 = aiDirector.fallbackBroadcastGenerator(worldState);
  if (!draft2) return;
  // 确保两个方案不同：第二个用不同的 eventType
  var draft1Types = getAllowedTypes(worldState);
  draft1.eventType = draft1Types[0];
  if (draft1Types.length > 1) draft2.eventType = draft1Types[1];
  else draft2.eventType = draft1Types[0];
  // 重新生成draft2（如果eventType不同）
  if (draft2.eventType !== draft1.eventType) {
    draft2 = generateByType(draft2.eventType, worldState);
  }

  // 用世界状态评分选最优方案
  var score1 = scoreDraft(draft1, worldState);
  var score2 = scoreDraft(draft2, worldState);

  // 冠军方案自动激活，另一方案保留为draft（备选）
  var chosen = score1 >= score2 ? draft1 : draft2;
  var spare = score1 >= score2 ? draft2 : draft1;

  var broadcastService = require('./broadcastService');
  var created = broadcastService.createDraft(chosen);
  if (created.success) {
    broadcastService.activateEvent(created.data.id);
    console.log('[Scheduler] Auto-generated & activated:', chosen.title, '(score:', Math.max(score1, score2) + ')');
  }

  // 备选方案也存为draft
  var spareCreated = broadcastService.createDraft(spare);
  if (spareCreated.success) {
    console.log('[Scheduler] Spare draft saved:', spare.title);
  }

  // 聊天通知
  try {
    var chatService = require('./chatService');
    chatService.sendMessage(0, '星之流',
      '新放送已激活：「' + chosen.title + '」— ' + (chosen.description || '').slice(0, 80), 'global');
  } catch (e) {}
}

function getAllowedTypes(worldState) {
  var types = [];
  if (worldState.averageLevel < 5) types.push('exploration_drive');
  if (worldState.activePlayers >= 2) types.push('world_boss');
  if (worldState.recentPkStats && worldState.recentPkStats.last24h >= 5) types.push('pk_tournament');
  if (worldState.averageWorldLineShift > 3) types.push('disaster');
  types.push('story_hunt');
  types.push('opportunity_rain');
  types.push('stage_support');
  return types;
}

function generateByType(eventType, worldState) {
  var aiDirector = require('./aiDirectorService');
  // 手动调整世界状态来引导fallback生成器
  var adjusted = Object.assign({}, worldState);
  switch (eventType) {
    case 'exploration_drive': adjusted.averageLevel = 1; break;
    case 'world_boss': adjusted.totalBossKills = 0; adjusted.activePlayers = 5; break;
    case 'pk_tournament': adjusted.recentPkStats = { last24h: 10 }; break;
    case 'disaster': adjusted.averageWorldLineShift = 5; break;
    case 'story_hunt':
      adjusted.locationExplorationStats = [{ c: 20 }, { c: 3 }];
      adjusted.totalBossKills = 99;
      adjusted.recentPkStats = { last24h: 0 };
      adjusted.averageWorldLineShift = 0;
      adjusted.averageLevel = 99;
      break;
    case 'stage_support':
      adjusted.currentStageDistribution = { 'main_ch01': 10 };
      adjusted.totalPlayers = 15;
      adjusted.totalBossKills = 99;
      adjusted.recentPkStats = { last24h: 0 };
      adjusted.averageWorldLineShift = 0;
      adjusted.averageLevel = 99;
      break;
  }
  var draft = aiDirector.fallbackBroadcastGenerator(adjusted);
  draft.eventType = eventType;
  return draft;
}

function scoreDraft(draft, worldState) {
  var score = 0;
  // 平均等级匹配
  if (draft.eventType === 'exploration_drive' && worldState.averageLevel < 5) score += 3;
  if (draft.eventType === 'world_boss' && worldState.activePlayers >= 5) score += 2;
  if (draft.eventType === 'pk_tournament' && (worldState.recentPkStats?.last24h || 0) >= 5) score += 2;
  if (draft.eventType === 'disaster' && worldState.averageWorldLineShift > 3) score += 3;
  // 多样性加分：新类型优先
  var db = require('../db/database').getDb();
  var lastType = db.prepare(
    "SELECT event_type FROM broadcast_events WHERE status IN ('completed','rewarded','failed') ORDER BY created_at DESC LIMIT 1"
  ).get();
  if (!lastType || lastType.event_type !== draft.eventType) score += 2;
  return score;
}

var _lastWarCheck = null;
function checkWeeklyWar() {
  var now = new Date();
  var day = now.getDay(); // 0=Sun, 1=Mon
  var hour = now.getHours();
  var minute = now.getMinutes();
  if (day !== 1 || hour !== 0 || minute > 5) return;
  var dateKey = now.toISOString().slice(0, 10);
  if (_lastWarCheck === dateKey) return;
  _lastWarCheck = dateKey;

  try {
    var factionService = require('./factionService');
    var result = factionService.resolveWeeklyWar();
    if (result && result.winner) {
      var chatService = require('./chatService');
      chatService.sendMessage(0, '星之流',
        '本周阵营战结束！' + result.winner + '阵营获得了星座霸权。该阵营成员本周享受全属性加成。', 'global');
      console.log('[Scheduler] Weekly faction war resolved:', result.winner);
    }
  } catch (e) { console.error('[Scheduler] war settle:', e.message); }
}

module.exports = { start, stop };
