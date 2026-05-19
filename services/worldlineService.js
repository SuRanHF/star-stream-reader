// 世界线偏移服务 (Phase 2) — 全服共享 worldLineShift
var getDb = function() { return require('../db/database').getDb(); };
var broadcastService = null;

function getBroadcastService() {
  if (!broadcastService) broadcastService = require('./broadcastService');
  return broadcastService;
}

function getWorldLineShift() {
  var row = getDb().prepare('SELECT * FROM world_state WHERE id = 1').get();
  return row || { world_line_shift: 0, ripple_decay_rate: 0.01 };
}

function contributeShift(amount, playerId) {
  if (!amount) return;
  var db = getDb();
  if (amount < 0) {
    // 世界线稳定化（击杀Boss/完成放送等）
    db.prepare("UPDATE world_state SET world_line_shift = MAX(0, world_line_shift + ?), updated_at = datetime('now','localtime') WHERE id = 1").run(amount);
  } else {
    db.prepare('UPDATE world_state SET world_line_shift = world_line_shift + ?, updated_at = datetime(\'now\',\'localtime\') WHERE id = 1').run(amount);
  }
  checkThresholds();
}

function applyDecay() {
  var db = getDb();
  var ws = db.prepare('SELECT * FROM world_state WHERE id = 1').get();
  if (!ws) return;
  var current = ws.world_line_shift;
  if (current <= 0) return;
  var rate = ws.ripple_decay_rate || 0.01;
  var decay = Math.max(0.01, current * rate);
  var newValue = Math.max(0, current - decay);
  db.prepare('UPDATE world_state SET world_line_shift = ?, last_decay_at = datetime(\'now\',\'localtime\'), updated_at = datetime(\'now\',\'localtime\') WHERE id = 1').run(newValue);
}

function getActiveThresholdEffects() {
  var ws = getWorldLineShift();
  var shift = ws.world_line_shift;
  var effects = [];
  if (shift >= 100) effects.push({ threshold: 100, name: '终章共振', description: '世界线偏移达到临界值。所有化身属性+10%。' });
  else if (shift >= 50) effects.push({ threshold: 50, name: '剧本震荡', description: '世界线剧烈偏移。探索故事事件概率+8%。' });
  else if (shift >= 25) effects.push({ threshold: 25, name: '频道干扰', description: '世界线偏移加剧。战斗奖励+25%。' });
  else if (shift >= 10) effects.push({ threshold: 10, name: '涟漪效应', description: '世界线出现偏移。探索奖励+10%。' });
  return effects;
}

function checkThresholds() {
  var db = getDb();
  var ws = db.prepare('SELECT * FROM world_state WHERE id = 1').get();
  if (!ws) return;
  var shift = ws.world_line_shift;
  var thresholds = [
    { key: 'global_event_triggered_at_10', value: 10, title: '世界线涟漪', description: '世界线偏移达到10点。星之流的星座们开始注意到世界线的不稳定。所有化身的探索奖励将增加10%，持续直到偏移消退。' },
    { key: 'global_event_triggered_at_25', value: 25, title: '世界线震荡', description: '世界线偏移达到25点。频道开始出现干扰。战斗奖励提高25%，但频道热度波动将加剧。' },
    { key: 'global_event_triggered_at_50', value: 50, title: '世界线风暴', description: '世界线偏移达到50点。星座们发起了紧急放送。故事事件概率大幅提升。' },
    { key: 'global_event_triggered_at_100', value: 100, title: '世界线临界', description: '世界线偏移达到100点！最古老的梦开始注视。所有化身获得双倍奖励，但也将面对更危险的事件。' }
  ];

  for (var i = 0; i < thresholds.length; i++) {
    var t = thresholds[i];
    if (shift >= t.value && !ws[t.key]) {
      db.prepare('UPDATE world_state SET ' + t.key + ' = 1, updated_at = datetime(\'now\',\'localtime\') WHERE id = 1').run();
      try {
        var draft = {
          eventType: 'disaster',
          title: t.title,
          description: t.description,
          durationMinutes: 60,
          objectives: [
            { type: 'explore_location', target: 20, label: '全服探索', score_per_unit: 1 }
          ],
          rewards: {
            participation: { coins: 300, storyFragments: 10 },
            completion: { coins: 600, storyFragments: 25 }
          }
        };
        var created = getBroadcastService().createDraft(draft);
        if (created.success) {
          getBroadcastService().activateEvent(created.data.id);
        }
      } catch (e) {
        // broadcast not critical
      }
    }
  }
}

function getWorldlineGlobalModifiers() {
  var ws = getWorldLineShift();
  var shift = ws.world_line_shift;
  var mods = {
    exploreRewardMult: 1.0,
    combatRewardMult: 1.0,
    storyFragmentDropMult: 1.0,
    battleEncounterProbBonus: 0,
    monsterDamageMult: 1.0,
    pkDeathPenaltyMult: 1.0,
    storyEncounterProbBonus: 0,
    bossEncounterChance: 0,
    label: '世界线稳定',
    anomalyLocation: null,
    anomalyIntensity: 0
  };

  if (shift >= 10) {
    mods.exploreRewardMult += 0.10;
    mods.battleEncounterProbBonus += 0.05;
    mods.label = '涟漪效应';
  }
  if (shift >= 25) {
    mods.combatRewardMult += 0.25;
    mods.monsterDamageMult += 0.10;
    mods.label = '频道干扰';
  }
  if (shift >= 50) {
    mods.storyFragmentDropMult += 0.50;
    mods.pkDeathPenaltyMult += 1.0;
    mods.label = '剧本震荡';
  }
  if (shift >= 100) {
    mods.exploreRewardMult += 0.30;
    mods.storyFragmentDropMult += 0.30;
    mods.bossEncounterChance += 0.05;
    mods.label = '终章共振';
  }

  // 局部世界线热点
  var db = getDb();
  var wsRow = db.prepare('SELECT anomaly_location, anomaly_intensity FROM world_state WHERE id = 1').get();
  if (wsRow && wsRow.anomaly_location) {
    mods.anomalyLocation = wsRow.anomaly_location;
    mods.anomalyIntensity = wsRow.anomaly_intensity || 1;
  }

  return mods;
}

function migrateWorldlineAnomaly() {
  var db = getDb();
  var ws = db.prepare('SELECT * FROM world_state WHERE id = 1').get();
  if (!ws || ws.world_line_shift < 50) {
    // 偏移不足50，清除热点
    db.prepare("UPDATE world_state SET anomaly_location = NULL, anomaly_intensity = 0, updated_at = datetime('now','localtime') WHERE id = 1").run();
    return;
  }
  // 随机选择地点
  var locations = db.prepare('SELECT location_key FROM locations ORDER BY RANDOM() LIMIT 1').all();
  if (locations.length > 0) {
    var intensity = Math.floor(ws.world_line_shift / 20); // 50→2, 100→5
    db.prepare(
      "UPDATE world_state SET anomaly_location = ?, anomaly_intensity = ?, updated_at = datetime('now','localtime') WHERE id = 1"
    ).run(locations[0].location_key, intensity);
    console.log('[Worldline] Anomaly migrated to:', locations[0].location_key, 'intensity:', intensity);
  }
}

module.exports = {
  getWorldLineShift,
  contributeShift,
  applyDecay,
  getActiveThresholdEffects,
  checkThresholds,
  getWorldlineGlobalModifiers,
  migrateWorldlineAnomaly
};
