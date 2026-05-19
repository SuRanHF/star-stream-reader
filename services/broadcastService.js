// 星流放送服务 (Round 6)
// 管理 broadcast 生命周期: 创建、激活、参与、贡献、结算、领奖
const { getDb } = require('../db/database');
const playerService = require('./playerService');
const chapterService = require('./chapterService');

const ALLOWED_EVENT_TYPES = [
  'world_boss', 'exploration_drive', 'story_hunt', 'pk_tournament',
  'faction_conflict', 'disaster', 'opportunity_rain', 'stage_support'
];

const ALLOWED_CONTRIBUTION_TYPES = [
  'kill_monster', 'explore_location', 'trigger_story', 'defeat_boss',
  'win_pk', 'submit_resource', 'complete_stage'
];

const ALLOWED_STATUSES = ['draft', 'active', 'completed', 'failed', 'expired', 'rewarded', 'cancelled'];

const REWARD_LIMITS = {
  participation: { coins: 500, storyFragments: 10, rareItem: 1 },
  completion:   { coins: 1000, storyFragments: 20, rareItem: 2 },
  ranking:      { coins: 2000, storyFragments: 30, rareItem: 3 }
};

// ── 校验 ──
function validateBroadcastDraft(draft) {
  const errors = [];

  if (!draft.eventType || !ALLOWED_EVENT_TYPES.includes(draft.eventType)) {
    errors.push(`无效的 eventType: ${draft.eventType}，允许: ${ALLOWED_EVENT_TYPES.join(', ')}`);
  }

  if (!draft.title || String(draft.title).trim().length === 0) {
    errors.push('缺少 title');
  }

  if (draft.title && String(draft.title).length > 200) {
    errors.push('title 超过 200 字符');
  }

  if (draft.description && String(draft.description).length > 2000) {
    errors.push('description 超过 2000 字符');
  }

  const duration = draft.durationMinutes || 60;
  if (typeof duration !== 'number' || duration < 15 || duration > 180) {
    errors.push(`durationMinutes 必须在 15-180 之间，当前: ${duration}`);
  }

  // 校验 objectives
  if (!draft.objectives || !Array.isArray(draft.objectives) || draft.objectives.length === 0) {
    errors.push('objectives 不能为空且必须是数组');
  } else {
    for (const obj of draft.objectives) {
      if (!obj.type || !ALLOWED_CONTRIBUTION_TYPES.includes(obj.type)) {
        errors.push(`objective type 无效: ${obj.type}，允许: ${ALLOWED_CONTRIBUTION_TYPES.join(', ')}`);
      }
      if (obj.target === undefined || typeof obj.target !== 'number' || obj.target <= 0) {
        errors.push(`objective ${obj.type || '?'} 缺少合法的 target 数量`);
      }
    }
    // 不能有无法验证的 objective
    const hasUnverifiable = draft.objectives.some(o =>
      !ALLOWED_CONTRIBUTION_TYPES.includes(o.type) || !o.target || o.target <= 0
    );
    if (hasUnverifiable) errors.push('存在无法验证的 objective');
  }

  // 校验 rewards
  const rewards = draft.rewards || {};
  if (rewards.participation) {
    if (rewards.participation.coins > REWARD_LIMITS.participation.coins)
      errors.push(`参与奖 coins 超过上限 ${REWARD_LIMITS.participation.coins}`);
    if (rewards.participation.storyFragments > REWARD_LIMITS.participation.storyFragments)
      errors.push(`参与奖 storyFragments 超过上限 ${REWARD_LIMITS.participation.storyFragments}`);
  }
  if (rewards.ranking) {
    if (rewards.ranking.coins > REWARD_LIMITS.ranking.coins)
      errors.push(`排名奖 coins 超过上限 ${REWARD_LIMITS.ranking.coins}`);
  }

  // 校验引用的 monster/location/item 是否存在
  const db = getDb();
  if (draft.requirements) {
    if (draft.requirements.monster_key) {
      const m = db.prepare('SELECT id FROM monsters WHERE monster_key = ?').get(draft.requirements.monster_key);
      if (!m) errors.push(`引用的 monster_key 不存在: ${draft.requirements.monster_key}`);
    }
    if (draft.requirements.location_key) {
      const l = db.prepare('SELECT id FROM locations WHERE location_key = ?').get(draft.requirements.location_key);
      if (!l) errors.push(`引用的 location_key 不存在: ${draft.requirements.location_key}`);
    }
  }

  return { valid: errors.length === 0, errors };
}

// ── 创建 draft ──
function createDraft(draft) {
  const validation = validateBroadcastDraft(draft);
  if (!validation.valid) {
    return { success: false, error: { code: 'VALIDATION_FAILED', message: '校验失败', errors: validation.errors } };
  }

  const db = getDb();
  const eventKey = draft.eventKey || `broadcast_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const now = new Date().toISOString();
  const startTime = now;
  const endTime = new Date(Date.now() + (draft.durationMinutes || 60) * 60000).toISOString();

  const result = db.prepare(`INSERT INTO broadcast_events
    (event_key, title, description, event_type, status, start_time, end_time,
     requirements_json, objectives_json, rewards_json, failure_penalty_json, generated_by, ai_reason_json)
    VALUES (?, ?, ?, ?, 'draft', ?, ?, ?, ?, ?, ?, ?, ?)`).run(
    eventKey, draft.title, draft.description || '', draft.eventType,
    startTime, endTime,
    JSON.stringify(draft.requirements || {}),
    JSON.stringify(draft.objectives || []),
    JSON.stringify(draft.rewards || {}),
    JSON.stringify(draft.failurePenalty || {}),
    draft.generatedBy || 'system',
    JSON.stringify({ reason: draft.reason || '', worldStateSnapshot: draft.worldStateSnapshot || {} })
  );

  const created = db.prepare('SELECT * FROM broadcast_events WHERE id = ?').get(result.lastInsertRowid);
  return { success: true, data: created };
}

// ── 激活 broadcast ──
function activateEvent(eventId) {
  const db = getDb();
  const event = db.prepare('SELECT * FROM broadcast_events WHERE id = ?').get(eventId);
  if (!event) return { success: false, error: { code: 'NOT_FOUND', message: '放送事件不存在' } };
  if (event.status !== 'draft') return { success: false, error: { code: 'INVALID_STATUS', message: `当前状态 ${event.status} 不允许激活` } };

  const now = new Date().toISOString();
  const endTime = new Date(Date.now() + (JSON.parse(event.objectives_json) ? 0 : 60) * 60000).toISOString();

  // 用 stored duration 计算 end_time
  let end = endTime;
  try {
    const obj = JSON.parse(event.objectives_json || '[]');
    // objectives 不直接含 duration; 从 draft 的 ai_reason 中可以读取，或使用默认 60 分钟
    end = new Date(Date.now() + 60 * 60000).toISOString();
  } catch (e) { /* ignore */ }

  db.prepare(`UPDATE broadcast_events SET status = 'active', start_time = ?, end_time = ?, updated_at = datetime('now','localtime') WHERE id = ?`)
    .run(now, end, eventId);

  const updated = db.prepare('SELECT * FROM broadcast_events WHERE id = ?').get(eventId);
  return { success: true, data: updated };
}

// ── 玩家参加 ──
function joinEvent(eventId, playerId) {
  const db = getDb();
  const event = db.prepare('SELECT * FROM broadcast_events WHERE id = ?').get(eventId);
  if (!event) return { success: false, error: { code: 'NOT_FOUND', message: '放送事件不存在' } };
  if (event.status !== 'active') return { success: false, error: { code: 'NOT_ACTIVE', message: '放送事件未激活' } };

  const player = playerService.getRaw(playerId);
  if (!player) return { success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  // 检查是否已参加
  const existing = db.prepare('SELECT * FROM broadcast_participation WHERE event_id = ? AND player_id = ?').get(eventId, playerId);
  if (existing) return { success: false, error: { code: 'ALREADY_JOINED', message: '你已经参加了该放送' } };

  // 检查 requirements
  const reqs = JSON.parse(event.requirements_json);
  if (reqs.min_level) {
    const stats = JSON.parse(player.stats_json);
    if ((stats.level || 1) < reqs.min_level) {
      return { success: false, error: { code: 'REQUIREMENT_NOT_MET', message: `需要等级 ${reqs.min_level}` } };
    }
  }

  db.prepare(`INSERT INTO broadcast_participation (event_id, player_id)
    VALUES (?, ?)`).run(eventId, playerId);

  playerService.addLog(playerId, `参加了星流放送: ${event.title}`);

  return { success: true, data: { joined: true, eventId, playerId } };
}

// ── 记录贡献 ──
function recordContribution(eventId, playerId, contributionType, amount, metadata) {
  if (!ALLOWED_CONTRIBUTION_TYPES.includes(contributionType)) return;

  const db = getDb();
  const event = db.prepare('SELECT * FROM broadcast_events WHERE id = ?').get(eventId);
  if (!event || event.status !== 'active') return;

  // 检查玩家是否参加了
  const partic = db.prepare('SELECT * FROM broadcast_participation WHERE event_id = ? AND player_id = ?').get(eventId, playerId);
  if (!partic) return;

  // 检查 objective 是否包含此贡献类型
  const objectives = JSON.parse(event.objectives_json);
  const relevantObj = objectives.find(o => o.type === contributionType);
  if (!relevantObj) return;

  amount = amount || 1;

  db.prepare(`INSERT INTO broadcast_contributions (event_id, player_id, contribution_type, amount, metadata_json)
    VALUES (?, ?, ?, ?, ?)`).run(eventId, playerId, contributionType, amount, JSON.stringify(metadata || {}));

  // 更新参与者的贡献分数
  const newScore = (partic.contribution_score || 0) + (amount * (relevantObj.score_per_unit || 1));
  db.prepare(`UPDATE broadcast_participation SET contribution_score = ?, updated_at = datetime('now','localtime')
    WHERE id = ?`).run(newScore, partic.id);
}

// ── 查询全服进度 ──
function getEventProgress(eventId) {
  const db = getDb();
  const event = db.prepare('SELECT * FROM broadcast_events WHERE id = ?').get(eventId);
  if (!event) return { success: false, error: { code: 'NOT_FOUND', message: '放送事件不存在' } };

  const objectives = JSON.parse(event.objectives_json);
  const participants = db.prepare('SELECT * FROM broadcast_participation WHERE event_id = ?').all(eventId);

  // 按 objective type 汇总贡献
  const contributions = {};
  for (const obj of objectives) {
    const row = db.prepare(
      'SELECT COALESCE(SUM(amount), 0) as total FROM broadcast_contributions WHERE event_id = ? AND contribution_type = ?'
    ).get(eventId, obj.type);
    contributions[obj.type] = { total: row.total, target: obj.target, progress: Math.min(1, row.total / (obj.target || 1)) };
  }

  const allMet = objectives.every(o => {
    const c = contributions[o.type];
    return c && c.total >= o.target;
  });

  return {
    success: true,
    data: {
      event: {
        id: event.id, event_key: event.event_key, title: event.title,
        description: event.description, event_type: event.event_type, status: event.status,
        start_time: event.start_time, end_time: event.end_time
      },
      objectives: objectives.map(o => ({
        type: o.type, label: o.label || o.type, target: o.target,
        current: (contributions[o.type] || {}).total || 0,
        progress: (contributions[o.type] || {}).progress || 0
      })),
      totalParticipants: participants.length,
      allObjectivesMet: allMet
    }
  };
}

// ── 查询个人贡献 ──
function getPlayerContribution(eventId, playerId) {
  const db = getDb();
  const partic = db.prepare('SELECT * FROM broadcast_participation WHERE event_id = ? AND player_id = ?').get(eventId, playerId);
  if (!partic) return { success: false, error: { code: 'NOT_PARTICIPATING', message: '未参加该放送' } };

  const contributions = db.prepare(
    'SELECT * FROM broadcast_contributions WHERE event_id = ? AND player_id = ? ORDER BY created_at DESC'
  ).all(eventId, playerId);

  return {
    success: true,
    data: {
      score: partic.contribution_score,
      claimedReward: partic.claimed_reward,
      contributions: contributions.map(c => ({
        type: c.contribution_type, amount: c.amount, time: c.created_at,
        metadata: JSON.parse(c.metadata_json || '{}')
      }))
    }
  };
}

// ── 领取奖励 ──
function claimReward(eventId, playerId) {
  const db = getDb();
  const event = db.prepare('SELECT * FROM broadcast_events WHERE id = ?').get(eventId);
  if (!event) return { success: false, error: { code: 'NOT_FOUND', message: '放送事件不存在' } };
  if (!['active', 'completed', 'rewarded'].includes(event.status)) {
    return { success: false, error: { code: 'NOT_CLAIMABLE', message: '放送状态不可领奖' } };
  }

  const partic = db.prepare('SELECT * FROM broadcast_participation WHERE event_id = ? AND player_id = ?').get(eventId, playerId);
  if (!partic) return { success: false, error: { code: 'NOT_PARTICIPATING', message: '未参加该放送' } };

  if (partic.claimed_reward !== 'none') {
    return { success: false, error: { code: 'ALREADY_CLAIMED', message: '你已经领取过奖励了' } };
  }

  const rewards = JSON.parse(event.rewards_json);
  const participationReward = rewards.participation || {};
  let granted = {};

  // 发放参与奖 (只要有贡献即可)
  if (partic.contribution_score > 0 && participationReward) {
    if (participationReward.coins) {
      const player = playerService.get(playerId);
      playerService.update(playerId, { coins: (player.coins || 0) + participationReward.coins });
      granted.coins = participationReward.coins;
    }
    if (participationReward.storyFragments) {
      chapterService.awardResource(playerId, 'storyFragments', participationReward.storyFragments);
      granted.storyFragments = participationReward.storyFragments;
    }
    if (participationReward.scenarioProof) {
      chapterService.awardResource(playerId, 'storyFragments', participationReward.scenarioProof * 10);
      granted.storyFragments = (granted.storyFragments || 0) + participationReward.scenarioProof * 10;
    }
    if (participationReward.item) {
      try { require('./inventoryService').addItem(playerId, participationReward.item, 1); granted.item = participationReward.item; }
      catch (e) { /* ignore */ }
    }
  }

  // 如果 event completed/rewarded，也给 completion reward
  if (['completed', 'rewarded'].includes(event.status)) {
    const completionReward = rewards.completion || {};
    if (completionReward.coins) {
      const player = playerService.get(playerId);
      playerService.update(playerId, { coins: (player.coins || 0) + completionReward.coins });
      granted.coins = (granted.coins || 0) + completionReward.coins;
    }
    if (completionReward.storyFragments) {
      chapterService.awardResource(playerId, 'storyFragments', completionReward.storyFragments);
      granted.storyFragments = (granted.storyFragments || 0) + completionReward.storyFragments;
    }
  }

  db.prepare(`UPDATE broadcast_participation SET claimed_reward = 'participation', updated_at = datetime('now','localtime')
    WHERE id = ?`).run(partic.id);

  playerService.addLog(playerId, `领取了星流放送奖励: ${event.title}`);

  return { success: true, data: { claimed: 'participation', granted } };
}

// ── 结算放送 ──
function resolveEvent(eventId, success) {
  const db = getDb();
  const event = db.prepare('SELECT * FROM broadcast_events WHERE id = ?').get(eventId);
  if (!event) return { success: false, error: { code: 'NOT_FOUND', message: '放送事件不存在' } };
  if (event.status !== 'active') return { success: false, error: { code: 'INVALID_STATUS', message: '只有 active 的放送可以结算' } };

  const newStatus = success ? 'completed' : 'failed';

  db.prepare(`UPDATE broadcast_events SET status = ?, updated_at = datetime('now','localtime') WHERE id = ?`)
    .run(newStatus, eventId);

  // 如果成功，发放排名奖励
  if (success) {
    const rewards = JSON.parse(event.rewards_json);
    const rankingReward = rewards.ranking || {};
    if (Object.keys(rankingReward).length > 0) {
      const topParticipants = db.prepare(
        'SELECT * FROM broadcast_participation WHERE event_id = ? AND contribution_score > 0 ORDER BY contribution_score DESC LIMIT 5'
      ).all(eventId);

      topParticipants.forEach((p, i) => {
        if (rankingReward.coins) {
          const player = playerService.get(p.player_id);
          const scaledCoins = Math.round(rankingReward.coins * (1 - i * 0.15)); // 递减
          playerService.update(p.player_id, { coins: (player.coins || 0) + scaledCoins });
          playerService.addLog(p.player_id, `星流放送排名奖励: 第${i + 1}名`);
        }
        db.prepare(`UPDATE broadcast_participation SET claimed_reward = 'ranking', updated_at = datetime('now','localtime')
          WHERE id = ?`).run(p.id);
      });
    }

    // 发放全服完成奖励
    db.prepare(`UPDATE broadcast_events SET status = 'rewarded', updated_at = datetime('now','localtime') WHERE id = ?`)
      .run(eventId);
  }

  const updated = db.prepare('SELECT * FROM broadcast_events WHERE id = ?').get(eventId);
  return { success: true, data: updated };
}

// ── 查询 ──
function getActiveBroadcasts() {
  const db = getDb();
  return db.prepare("SELECT * FROM broadcast_events WHERE status = 'active' ORDER BY created_at DESC").all();
}

function getHistoryBroadcasts(limit) {
  const db = getDb();
  return db.prepare(
    "SELECT * FROM broadcast_events WHERE status IN ('completed','rewarded','failed','expired','cancelled') ORDER BY created_at DESC LIMIT ?"
  ).all(limit || 20);
}

function getContributionRanking(eventId, limit) {
  const db = getDb();
  const rows = db.prepare(
    `SELECT bp.*, p.player_name FROM broadcast_participation bp
     JOIN players p ON bp.player_id = p.id
     WHERE bp.event_id = ? AND bp.contribution_score > 0
     ORDER BY bp.contribution_score DESC LIMIT ?`
  ).all(eventId, limit || 20);

  return rows.map((r, i) => ({
    rank: i + 1,
    playerId: r.player_id,
    playerName: r.player_name,
    score: r.contribution_score,
    claimedReward: r.claimed_reward
  }));
}

// 全服星流放送贡献总榜（跨所有事件汇总）
function getGlobalContributionLeaderboard(limit) {
  var db = getDb();
  var rows = db.prepare(
    'SELECT bc.player_id, p.player_name, p.stats_json, COALESCE(SUM(bc.amount), 0) as total_contribution ' +
    'FROM broadcast_contributions bc ' +
    'JOIN players p ON bc.player_id = p.id ' +
    'GROUP BY bc.player_id ' +
    'ORDER BY total_contribution DESC ' +
    'LIMIT ?'
  ).all(limit || 50);

  return rows.map(function(r, i) {
    var stats = typeof r.stats_json === 'string' ? JSON.parse(r.stats_json) : (r.stats_json || {});
    return {
      rank: i + 1,
      player_id: r.player_id,
      player_name: r.player_name,
      level: stats.level || 1,
      total_contribution: r.total_contribution
    };
  });
}

// 获取当前激活的星流放送对 gameplay 的修正
function getActiveModifiers(playerId) {
  const active = getActiveBroadcasts();
  if (active.length === 0) return {};

  const db = getDb();
  const modifiers = {
    exploreRewardMult: 1.0,
    storyProbabilityBonus: 0,
    combatDamageBonus: 0,
    pkRatingBonus: 0,
    dangerLevelBonus: 0,
    dropRateBonus: 0,
    opportunityProbabilityBonus: 0,
    pkDamageBonus: 0,
    breakthroughCostReduction: 0
  };

  for (const event of active) {
    // 检查玩家是否参加
    const participant = db.prepare(
      'SELECT id FROM broadcast_participation WHERE event_id = ? AND player_id = ?'
    ).get(event.id, playerId);
    if (!participant) continue;

    switch (event.event_type) {
      case 'exploration_drive':
        modifiers.exploreRewardMult += 0.10;
        modifiers.storyProbabilityBonus += 0.05;
        break;
      case 'story_hunt':
        modifiers.storyProbabilityBonus += 0.05;
        break;
      case 'world_boss':
        modifiers.combatDamageBonus += 0.10;
        break;
      case 'pk_tournament':
        modifiers.pkRatingBonus += 5;
        break;
      case 'disaster':
        modifiers.dangerLevelBonus += 1;
        modifiers.dropRateBonus -= 0.05;
        break;
      case 'faction_conflict':
        modifiers.pkDamageBonus += 0.05;
        break;
      case 'opportunity_rain':
        modifiers.opportunityProbabilityBonus += 0.10;
        modifiers.dropRateBonus += 0.05;
        break;
      case 'stage_support':
        modifiers.breakthroughCostReduction += 0.10;
        break;
    }
  }

  return modifiers;
}

// 批量检测 active broadcast 并记录贡献（供其他服务调用）
function tryRecordContributions(playerId, contributions) {
  const active = getActiveBroadcasts();
  if (active.length === 0) return;

  for (const event of active) {
    const objectives = JSON.parse(event.objectives_json);
    for (const c of contributions) {
      if (objectives.some(o => o.type === c.type)) {
        recordContribution(event.id, playerId, c.type, c.amount, c.metadata || {});
      }
    }
  }
}

module.exports = {
  ALLOWED_EVENT_TYPES, ALLOWED_CONTRIBUTION_TYPES, ALLOWED_STATUSES, REWARD_LIMITS,
  validateBroadcastDraft, createDraft, activateEvent,
  joinEvent, recordContribution, getEventProgress, getPlayerContribution,
  claimReward, resolveEvent,
  getActiveBroadcasts, getHistoryBroadcasts, getContributionRanking, getGlobalContributionLeaderboard, getActiveModifiers,
  tryRecordContributions
};
