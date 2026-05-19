// 恢复服务 — 体力/生命被动恢复 + 休息机制
const playerService = require('./playerService');

const NORMAL_STAMINA_INTERVAL = 60; // 普通状态: 每60秒恢复1点体力
const NORMAL_HP_INTERVAL = 60;      // 普通状态: 每60秒恢复1%最大生命(至少1点)
const REST_STAMINA_INTERVAL = 10;   // 休息状态: 每10秒恢复1点体力
const REST_HP_INTERVAL = 10;        // 休息状态: 每10秒恢复3%最大生命(至少3点)

function applyPassiveRecovery(playerId, now = Date.now()) {
  const raw = playerService.getRaw(playerId);
  if (!raw) return null;

  const stats = JSON.parse(raw.stats_json || '{}');
  if (stats.hp <= 0) return stats; // dead, no recovery

  const lastRecoveryAt = stats.lastRecoveryAt || now;
  const elapsedSec = Math.floor((now - lastRecoveryAt) / 1000);
  if (elapsedSec <= 0) return stats;

  const maxHp = stats.maxHp || 100;
  const maxStamina = stats.maxStamina || 50;
  const isResting = !!(stats.isResting);

  const staminaInterval = isResting ? REST_STAMINA_INTERVAL : NORMAL_STAMINA_INTERVAL;
  const hpInterval = isResting ? REST_HP_INTERVAL : NORMAL_HP_INTERVAL;

  // 体力恢复
  const staminaRecovery = Math.floor(elapsedSec / staminaInterval);
  if (staminaRecovery > 0 && (stats.stamina || 0) < maxStamina) {
    stats.stamina = Math.min(maxStamina, (stats.stamina || 0) + staminaRecovery);
  }

  // 生命恢复
  const hpRecovery = Math.floor(elapsedSec / hpInterval);
  if (hpRecovery > 0 && (stats.hp || 0) < maxHp) {
    if (isResting) {
      // 休息: 每周期恢复 3% 最大生命, 至少3点
      stats.hp = Math.min(maxHp, (stats.hp || 0) + hpRecovery * Math.max(3, Math.floor(maxHp * 0.03)));
    } else {
      // 普通: 每周期恢复 1% 最大生命, 至少1点
      stats.hp = Math.min(maxHp, (stats.hp || 0) + hpRecovery * Math.max(1, Math.floor(maxHp * 0.01)));
    }
  }

  // 更新最后恢复时间 (对齐到最后一个完整周期)
  const consumedSec = Math.max(staminaRecovery * staminaInterval, hpRecovery * hpInterval);
  stats.lastRecoveryAt = lastRecoveryAt + consumedSec * 1000;

  playerService.update(playerId, { stats_json: stats });
  return stats;
}

function startRest(playerId) {
  const raw = playerService.getRaw(playerId);
  if (!raw) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  const stats = JSON.parse(raw.stats_json || '{}');
  if (stats.isResting) {
    return { error: { code: 'ALREADY_RESTING', message: '你已经在休息了。' } };
  }

  const now = Date.now();
  stats.isResting = true;
  stats.restStartedAt = now;
  stats.lastRecoveryAt = now;

  playerService.update(playerId, { stats_json: stats });
  playerService.addLog(playerId, '你进入休息状态，生命与体力恢复速度提升。');

  // Quest progress
  try {
    var questService = require('./questService');
    questService.checkProgress(playerId, 'rest', {});
  } catch (e) { /* quest not critical */ }

  return { player: playerService.get(playerId), isResting: true };
}

function stopRest(playerId) {
  const raw = playerService.getRaw(playerId);
  if (!raw) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  const stats = JSON.parse(raw.stats_json || '{}');
  if (!stats.isResting) {
    return { error: { code: 'NOT_RESTING', message: '你当前没有在休息。' } };
  }

  // 先应用恢复
  applyPassiveRecovery(playerId);

  // 重新读取最新stats
  const updatedRaw = playerService.getRaw(playerId);
  const updatedStats = JSON.parse(updatedRaw.stats_json || '{}');
  updatedStats.isResting = false;
  updatedStats.restStartedAt = null;

  playerService.update(playerId, { stats_json: updatedStats });
  playerService.addLog(playerId, '你结束休息。');

  return { player: playerService.get(playerId), isResting: false };
}

module.exports = {
  applyPassiveRecovery,
  startRest,
  stopRest,
  NORMAL_STAMINA_INTERVAL,
  NORMAL_HP_INTERVAL,
  REST_STAMINA_INTERVAL,
  REST_HP_INTERVAL
};
