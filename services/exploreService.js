// 探索系统 (Round 5: exploration event system)
// 探索驱动剧情触发 — 每次探索根据地图概率和称号修正随机产生事件
const { getDb } = require('../db/database');
const playerService = require('./playerService');
const combatService = require('./combatService');
const inventoryService = require('./inventoryService');
const titleService = require('./titleService');
const chapterService = require('./chapterService');

const DEFAULT_PROBABILITIES = {
  story: 0.12, side_story: 0.05, battle: 0.35, elite_battle: 0.03,
  boss_clue: 0.03, opportunity: 0.10, resource: 0.15, hidden: 0.02, nothing: 0.15
};

const DEFAULT_STAGE_PROGRESS = {
  storyEventsTriggered: [],
  sideEventsTriggered: [],
  bossClues: {},
  opportunityEventsTriggered: [],
  hiddenEventsTriggered: [],
  storyPity: 0,
  explorationsByLocation: {},
  finalStoryEventTriggered: null,
  lastExplorationResultType: null
};

function migrateStageProgress(progress) {
  const defaults = DEFAULT_STAGE_PROGRESS;
  for (const [k, v] of Object.entries(defaults)) {
    if (!(k in progress)) {
      if (Array.isArray(v)) progress[k] = [];
      else if (typeof v === 'object' && v !== null) progress[k] = { ...v };
      else progress[k] = v;
    }
  }
  if (progress.bossClues && typeof progress.bossClues === 'object' && !Array.isArray(progress.bossClues)) {
    // bossClues is an object map, ensure it's preserved
  }
  return progress;
}

function getStageProgress(playerId) {
  const player = playerService.getRaw(playerId);
  if (!player) return null;
  const raw = JSON.parse(player.stage_progress_json || '{}');
  return migrateStageProgress(raw);
}

function saveStageProgress(playerId, progress) {
  playerService.update(playerId, { stage_progress_json: progress });
}

// ── 获取玩家已解锁的地图 ──
function getUnlockedLocations(playerId) {
  const db = getDb();
  const player = playerService.get(playerId);
  if (!player) return [];

  const stats = player.stats;
  const now = Date.now();
  const lastRestAt = stats._lastRestAt || now;
  const minutesPassed = Math.floor((now - lastRestAt) / 60000);
  if (minutesPassed >= 5 && (stats.stamina || 0) < (stats.maxStamina || 50)) {
    const recovery = Math.floor(minutesPassed / 5);
    stats.stamina = Math.min(stats.maxStamina || 50, (stats.stamina || 0) + recovery);
    stats._lastRestAt = now;
    playerService.update(playerId, { stats_json: stats });
  }

  const allLocations = db.prepare('SELECT * FROM locations ORDER BY min_level').all();
  const unlocked = [];

  const titleLocations = new Set();
  for (const tKey of player.titles) {
    const titleDef = db.prepare('SELECT effects_json FROM titles WHERE title_key = ?').get(tKey);
    if (titleDef) {
      const effects = JSON.parse(titleDef.effects_json);
      if (effects.unlock_locations) {
        for (const loc of effects.unlock_locations) titleLocations.add(loc);
      }
    }
  }

  for (const loc of allLocations) {
    const conditions = JSON.parse(loc.unlock_conditions_json);
    if (checkUnlockConditions(player, conditions) || titleLocations.has(loc.location_key)) {
      unlocked.push({
        location_key: loc.location_key,
        name: loc.name,
        description: loc.description,
        min_level: loc.min_level,
        danger_level: loc.danger_level,
        drop_rate_modifier: loc.drop_rate_modifier
      });
    }
  }

  return unlocked;
}

function checkUnlockConditions(player, conditions) {
  const stats = player.stats;
  const storyFlags = player.story_flags || {};
  const permFlags = player.permanent_flags || {};
  const titles = player.titles || [];

  if (conditions.required_level && stats.level < conditions.required_level) return false;
  if (conditions.required_flags) {
    for (const [flag, val] of Object.entries(conditions.required_flags)) {
      if ((storyFlags[flag] || permFlags[flag]) !== val) return false;
    }
  }
  if (conditions.required_titles) {
    for (const t of conditions.required_titles) {
      if (!titles.includes(t)) return false;
    }
  }
  return true;
}

function applyLocationUnlocks(playerId, locationKeys) {
  const player = playerService.getRaw(playerId);
  if (!player) return;
  const permFlags = JSON.parse(player.permanent_flags_json);
  for (const loc of locationKeys) {
    permFlags[`location_${loc}`] = true;
  }
  playerService.update(playerId, { permanent_flags_json: permFlags });
}

// ── 核心: 探索事件系统 ──
function startExploration(playerId, locationKey, opts) {
  opts = opts || {};
  const db = getDb();
  const player = playerService.get(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  const location = db.prepare('SELECT * FROM locations WHERE location_key = ?').get(locationKey);
  if (!location) return { error: { code: 'LOCATION_NOT_FOUND', message: '地图不存在' } };

  const conditions = JSON.parse(location.unlock_conditions_json);
  if (!checkUnlockConditions(player, conditions)) {
    return { error: { code: 'LOCATION_LOCKED', message: '该地图尚未解锁' } };
  }

  // 称号修正
  const explorationMods = titleService.computeExplorationModifiers(player);
  const eventProbMods = titleService.computeEventProbabilityModifiers(player);
  const coinMultiplier = titleService.computeCoinMultiplier(player);

  // 星流放送修正
  let broadcastMods = {};
  try {
    const broadcastService = require('./broadcastService');
    broadcastMods = broadcastService.getActiveModifiers(playerId);
  } catch (e) { /* broadcast not critical */ }
  const effectiveCoinMultiplier = coinMultiplier * (broadcastMods.exploreRewardMult || 1.0);

  // 体力检查
  const stats = { ...player.stats };
  const heatMultiplier = 1 + (stats.channelHeat || 0) * 0.01;
  const finalCoinMultiplier = effectiveCoinMultiplier * heatMultiplier;
  const staminaReduction = explorationMods.staminaReduction || 0;
  let staminaCost = Math.max(1, 5 - staminaReduction);
  if (opts.firstExplore) {
    staminaCost = 0;
  }
  if (staminaCost > 0 && (stats.stamina || 0) < staminaCost) {
    return { error: { code: 'NO_STAMINA', message: `体力不足，需要${staminaCost}点体力，请等待恢复或使用道具` } };
  }
  if (staminaCost > 0) {
    stats.stamina -= staminaCost;
    playerService.update(playerId, { stats_json: stats });
  }

  // 获取当前阶段
  const currentMain = player.current_main_chapter || 'main_ch01_paid_service';
  const stageConfig = db.prepare('SELECT * FROM main_chapters WHERE chapter_key = ?').get(currentMain);

  // 获取地图事件概率
  const baseProbs = { ...DEFAULT_PROBABILITIES, ...JSON.parse(location.event_probabilities_json || '{}') };

  // 获取阶段进度
  const stageProgress = getStageProgress(playerId);

  // 应用称号修正
  const adjustedProbs = applyTitleEventModifiers(baseProbs, eventProbMods);

  // 应用 broadcast 修正
  if (broadcastMods.storyProbabilityBonus) {
    adjustedProbs.story = Math.min(1.0, (adjustedProbs.story || 0) + broadcastMods.storyProbabilityBonus);
  }
  if (broadcastMods.opportunityProbabilityBonus) {
    adjustedProbs.opportunity = Math.min(1.0, (adjustedProbs.opportunity || 0) + broadcastMods.opportunityProbabilityBonus);
  }

  // 应用剧情保底 pity
  const pityThreshold = 5;
  if (stageProgress.storyPity >= pityThreshold) {
    adjustedProbs.story = 1.0; // 必定触发
  } else {
    adjustedProbs.story = Math.min(1.0, adjustedProbs.story + (stageProgress.storyPity || 0) * 0.08);
  }

  // 抽取 resultType
  const roll = Math.random();
  let cumulative = 0;
  let resultType = 'nothing';
  const typeOrder = ['story', 'side_story', 'battle', 'elite_battle', 'boss_clue', 'opportunity', 'resource', 'hidden'];

  for (const t of typeOrder) {
    cumulative += adjustedProbs[t] || 0;
    if (roll < cumulative) { resultType = t; break; }
  }

  // ── 根据 resultType 处理事件 ──
  const result = processEventType(db, player, playerId, location, locationKey, resultType, stageProgress, currentMain, stageConfig, finalCoinMultiplier);

  // 更新阶段进度
  saveStageProgress(playerId, stageProgress);

  // 记录最后探索结果类型
  stageProgress.lastExplorationResultType = resultType;

  // 记录探索日志
  db.prepare(`INSERT INTO exploration_logs (player_id, location_key, result_type, result_json)
    VALUES (?, ?, ?, ?)`).run(playerId, locationKey, resultType, JSON.stringify(result.resultData || {}));

  // Round 6: 星流放送贡献记录
  try {
    const broadcastService = require('./broadcastService');
    const contribs = [{ type: 'explore_location', amount: 1, metadata: { location_key: locationKey, result_type: resultType } }];
    if (resultType === 'story') contribs.push({ type: 'trigger_story', amount: 1, metadata: { event_name: result.resultData?.event_name } });
    broadcastService.tryRecordContributions(playerId, contribs);
  } catch (e) { /* broadcast not critical */ }

  // Update current_location for seamless exploration
  playerService.update(playerId, { current_location: locationKey });

  const updatedPlayer = playerService.get(playerId);

  return {
    location: { location_key: location.location_key, name: location.name, danger_level: location.danger_level },
    stamina_cost: staminaCost,
    remaining_stamina: (updatedPlayer.stats.stamina || 0),
    result_type: resultType,
    result: result.resultData,
    player: updatedPlayer,
    stage_progress: stageProgress,
    story_pity: stageProgress.storyPity
  };
}

// ── 探索推进章节: 检查 pending_next_chapter 并推进 ──
function tryAdvanceChapter(playerId) {
  const db = getDb();
  const player = playerService.getRaw(playerId);
  if (!player) return { advanced: false };

  const pendingNext = player.pending_next_chapter;
  if (!pendingNext) return { advanced: false };

  const chapter = db.prepare('SELECT * FROM chapters WHERE chapter_key = ?').get(pendingNext);
  if (!chapter) return { advanced: false };

  db.prepare(`UPDATE players SET current_chapter = ?, pending_next_chapter = NULL, updated_at = datetime('now','localtime') WHERE id = ?`)
    .run(pendingNext, playerId);
  playerService.addLog(playerId, `探索推进剧情至: ${chapter.title}`);

  return { advanced: true, new_chapter_key: pendingNext, chapter_title: chapter.title };
}

function processEventType(db, player, playerId, location, locationKey, resultType, stageProgress, currentMain, stageConfig, coinMultiplier) {
  const playerFull = playerService.get(playerId);

  switch (resultType) {
    case 'story':
      return handleStoryEvent(db, player, playerId, locationKey, stageProgress, currentMain, stageConfig);

    case 'side_story':
      return handleSideStoryEvent(db, playerId, locationKey, stageProgress);

    case 'opportunity':
      return handleOpportunityEvent(db, playerId, locationKey, stageProgress, playerFull);

    case 'battle':
      return handleBattleEvent(db, player, playerId, location, false, coinMultiplier);

    case 'elite_battle':
      return handleBattleEvent(db, player, playerId, location, true, coinMultiplier);

    case 'boss_clue':
      return handleBossClueEvent(db, playerId, locationKey, stageProgress, currentMain);

    case 'hidden':
      return handleHiddenEvent(db, playerId, locationKey, stageProgress, playerFull);

    case 'resource':
      return handleResourceEvent(playerId, location, coinMultiplier);

    case 'nothing':
    default:
      return { resultData: { description: '这次探索没有发现任何特别的东西。', coins: 0 } };
  }
}

// ── 剧情事件处理 ──
function handleStoryEvent(db, player, playerId, locationKey, stageProgress, currentMain, stageConfig) {
  // 检查是否可以触发 final_story_event
  const completedStages = JSON.parse(player.completed_chapters_json || '[]');
  const stageCompleted = completedStages.includes(currentMain);

  let event;
  let isFinal = false;

  if (!stageCompleted) {
    // 检查阶段目标（不含 final_story_event 以避免循环依赖）
    const objectiveCheck = chapterService.checkCurrentStageObjectives(playerId, false);
    const finalEventKey = `story_stage_${String(stageConfig.order_index).padStart(2, '0')}_settlement`;

    if (objectiveCheck.canComplete && Math.random() < 0.7) {
      // 其他目标满足 → 触发最终剧情事件
      event = db.prepare(
        'SELECT * FROM exploration_events WHERE event_key = ?'
      ).get(finalEventKey);
      if (event) isFinal = true;
    }

    if (!event) {
      // 从当前阶段未触发的 story events 中选一个
      const triggeredStoryEvents = stageProgress.storyEventsTriggered || [];
      const availableEvents = db.prepare(
        `SELECT * FROM exploration_events WHERE event_type = 'story' AND stage_key = ? AND event_key NOT IN (${triggeredStoryEvents.map(() => '?').join(',') || '?'}) AND weight > 0`
      ).all(currentMain, ...triggeredStoryEvents);

      if (availableEvents.length === 0) {
        const allStageEvents = db.prepare(
          "SELECT * FROM exploration_events WHERE event_type = 'story' AND stage_key = ? AND weight > 0"
        ).all(currentMain);
        event = allStageEvents[Math.floor(Math.random() * allStageEvents.length)] || null;
      } else {
        event = weightedSelect(availableEvents);
      }
    }
  } else {
    // 阶段已完成
    const availableEvents = db.prepare(
      "SELECT * FROM exploration_events WHERE event_type = 'story' AND stage_key = ? AND weight > 0"
    ).all(currentMain);
    event = availableEvents[Math.floor(Math.random() * availableEvents.length)] || null;
  }

  if (!event) {
    stageProgress.storyPity = (stageProgress.storyPity || 0) + 1;
    return { resultData: { event_name: '无剧情事件', description: '当前阶段暂无可用剧情事件。你感到故事暂时缺少方向。', event_type: 'story' } };
  }

  // 应用事件奖励和风险
  const rewards = JSON.parse(event.rewards_json);
  const risks = JSON.parse(event.risks_json);
  const progressEffects = JSON.parse(event.progress_effects_json);

  applyEventRewards(playerId, rewards, player);
  applyEventRisks(playerId, risks);

  // 更新阶段进度
  if (!stageProgress.storyEventsTriggered.includes(event.event_key)) {
    stageProgress.storyEventsTriggered.push(event.event_key);
  }

  if (isFinal) {
    stageProgress.finalStoryEventTriggered = event.event_key;
  }

  // 探索计数
  stageProgress.explorationsByLocation[locationKey] = (stageProgress.explorationsByLocation[locationKey] || 0) + 1;

  // 清零 pity
  stageProgress.storyPity = 0;

  if (isFinal) {
    chapterService.awardResource(playerId, 'scenarioProof', 1);
  }

  // 尝试推进章节
  const chapterAdvance = tryAdvanceChapter(playerId);

  return {
    resultData: {
      event_key: event.event_key,
      event_name: event.name,
      description: event.description,
      event_type: 'story',
      is_final: isFinal,
      rewards,
      risks: Object.keys(risks).length > 0 ? risks : null,
      stage_progress_update: progressEffects,
      story_pity_reset: true,
      chapter_advanced: chapterAdvance.advanced,
      new_chapter_key: chapterAdvance.new_chapter_key || null,
      new_chapter_name: chapterAdvance.chapter_title || null
    }
  };
}

// ── 支线剧情处理 ──
function handleSideStoryEvent(db, playerId, locationKey, stageProgress) {
  const availableSideEvents = db.prepare(
    "SELECT * FROM exploration_events WHERE event_type = 'side_story' AND (location_key = ? OR location_key IS NULL)"
  ).all(locationKey);
  const triggered = stageProgress.sideEventsTriggered || [];
  const fresh = availableSideEvents.filter(e => !triggered.includes(e.event_key));

  let event;
  if (fresh.length > 0) {
    event = fresh[Math.floor(Math.random() * fresh.length)];
  } else if (availableSideEvents.length > 0) {
    event = availableSideEvents[Math.floor(Math.random() * availableSideEvents.length)];
  }

  if (!event) {
    return { resultData: { event_name: '无支线事件', description: '这个地方似乎没有更多的支线故事了。', event_type: 'side_story' } };
  }

  const rewards = JSON.parse(event.rewards_json);
  const risks = JSON.parse(event.risks_json);
  applyEventRewards(playerId, rewards);
  applyEventRisks(playerId, risks);

  if (!stageProgress.sideEventsTriggered.includes(event.event_key)) {
    stageProgress.sideEventsTriggered.push(event.event_key);
  }
  stageProgress.explorationsByLocation[locationKey] = (stageProgress.explorationsByLocation[locationKey] || 0) + 1;

  // 尝试推进章节
  const chapterAdvance = tryAdvanceChapter(playerId);

  return {
    resultData: {
      event_key: event.event_key,
      event_name: event.name,
      description: event.description,
      event_type: 'side_story',
      rewards,
      risks: Object.keys(risks).length > 0 ? risks : null,
      chapter_advanced: chapterAdvance.advanced,
      new_chapter_key: chapterAdvance.new_chapter_key || null,
      new_chapter_name: chapterAdvance.chapter_title || null
    }
  };
}

// ── 机遇事件处理 ──
function handleOpportunityEvent(db, playerId, locationKey, stageProgress, playerFull) {
  const availableOpp = db.prepare(
    "SELECT * FROM exploration_events WHERE event_type = 'opportunity' AND (location_key = ? OR location_key IS NULL)"
  ).all(locationKey);

  if (availableOpp.length === 0) {
    return { resultData: { event_name: '无机遇', description: '今天似乎不是遇到好运的日子。', event_type: 'opportunity' } };
  }

  // 过滤条件满足的机遇
  const eligible = availableOpp.filter(e => {
    const conds = JSON.parse(e.required_conditions_json);
    if (Object.keys(conds).length === 0) return true;
    return checkEventConditions(playerFull, conds);
  });

  const pool = eligible.length > 0 ? eligible : availableOpp;
  const event = weightedSelect(pool);

  if (!event) {
    return { resultData: { event_name: '机遇溜走了', description: '你感觉到有什么好的东西在附近——但就是找不到它。', event_type: 'opportunity' } };
  }

  const rewards = JSON.parse(event.rewards_json);
  const risks = JSON.parse(event.risks_json);
  applyEventRewards(playerId, rewards);
  applyEventRisks(playerId, risks);

  // 装备奖励特殊处理
  if (rewards.equipment) {
    inventoryService.addItem(playerId, rewards.equipment, 1);
    // Resolve equipment name for frontend display
    try {
      const eqRow = db.prepare('SELECT name FROM equipment WHERE equipment_key = ?').get(rewards.equipment);
      if (eqRow) rewards.equipment_name = eqRow.name;
    } catch (e) { /* non-critical */ }
  }
  // 道具奖励
  if (rewards.items) {
    for (const itemKey of rewards.items) {
      inventoryService.addItem(playerId, itemKey, 1);
    }
  }
  // 称号进度
  if (rewards.title_progress) {
    titleService.incrementTitleProgress(playerId, rewards.title_progress);
  }

  if (!stageProgress.opportunityEventsTriggered.includes(event.event_key)) {
    stageProgress.opportunityEventsTriggered.push(event.event_key);
  }
  stageProgress.explorationsByLocation[locationKey] = (stageProgress.explorationsByLocation[locationKey] || 0) + 1;

  return {
    resultData: {
      event_key: event.event_key,
      event_name: event.name,
      description: event.description,
      event_type: 'opportunity',
      rewards,
      risks: Object.keys(risks).length > 0 ? risks : null
    }
  };
}

// ── Boss 线索处理 ──
function handleBossClueEvent(db, playerId, locationKey, stageProgress, currentMain) {
  const clues = db.prepare(
    "SELECT * FROM exploration_events WHERE event_type = 'boss_clue' AND (location_key = ? OR location_key IS NULL)"
  ).all(locationKey);

  if (clues.length === 0) {
    return { resultData: { event_name: '无Boss线索', description: '探查了这片区域，没有发现任何强敌的踪迹。', event_type: 'boss_clue' } };
  }

  const event = clues[Math.floor(Math.random() * clues.length)];
  const rewards = JSON.parse(event.rewards_json);
  const progressEffects = JSON.parse(event.progress_effects_json);
  applyEventRewards(playerId, rewards);

  // 记录Boss线索
  if (progressEffects.bossClues) {
    const bossKey = progressEffects.bossClues;
    stageProgress.bossClues[bossKey] = (stageProgress.bossClues[bossKey] || 0) + 1;
  }

  stageProgress.explorationsByLocation[locationKey] = (stageProgress.explorationsByLocation[locationKey] || 0) + 1;

  return {
    resultData: {
      event_key: event.event_key,
      event_name: event.name,
      description: event.description,
      event_type: 'boss_clue',
      rewards,
      clues: stageProgress.bossClues
    }
  };
}

// ── 隐藏事件处理 ──
function handleHiddenEvent(db, playerId, locationKey, stageProgress, playerFull) {
  const available = db.prepare(
    "SELECT * FROM exploration_events WHERE event_type = 'hidden' AND (location_key = ? OR location_key IS NULL)"
  ).all(locationKey);

  if (available.length === 0) {
    return { resultData: { event_name: '一切如常', description: '你仔仔细细探查了每一个角落——什么特别的东西都没有。', event_type: 'hidden' } };
  }

  const event = available[Math.floor(Math.random() * available.length)];
  const rewards = JSON.parse(event.rewards_json);
  const risks = JSON.parse(event.risks_json);
  applyEventRewards(playerId, rewards);
  applyEventRisks(playerId, risks);

  if (!stageProgress.hiddenEventsTriggered.includes(event.event_key)) {
    stageProgress.hiddenEventsTriggered.push(event.event_key);
  }
  stageProgress.explorationsByLocation[locationKey] = (stageProgress.explorationsByLocation[locationKey] || 0) + 1;

  return {
    resultData: {
      event_key: event.event_key,
      event_name: event.name,
      description: event.description,
      event_type: 'hidden',
      rewards,
      risks: Object.keys(risks).length > 0 ? risks : null
    }
  };
}

// ── 战斗事件处理 ──
function handleBattleEvent(db, player, playerId, location, isElite, coinMultiplier) {
  const monsterPool = JSON.parse(location.monster_pool_json);
  if (monsterPool.length === 0) {
    const coins = Math.round((15 + Math.floor(Math.random() * 35)) * coinMultiplier);
    playerService.update(playerId, { coins: player.coins + coins });
    return { resultData: { event_name: '安静地带', description: `这里没有怪物。获得硬币 +${coins}`, event_type: isElite ? 'elite_battle' : 'battle', coins } };
  }

  const monsterKey = monsterPool[Math.floor(Math.random() * monsterPool.length)];
  const monster = db.prepare('SELECT * FROM monsters WHERE monster_key = ?').get(monsterKey);
  if (!monster) {
    const coins = Math.round((15 + Math.floor(Math.random() * 35)) * coinMultiplier);
    playerService.update(playerId, { coins: player.coins + coins });
    return { resultData: { event_name: '异常遭遇', description: `怪物数据异常，在它消失前你拾起了硬币 +${coins}`, event_type: isElite ? 'elite_battle' : 'battle', coins } };
  }

  const playerPower = combatService.calculateCombatPower(playerService.get(playerId));

  // Return combat encounter data — battle is NOT auto-resolved
  return {
    resultData: {
      combat_encounter: true,
      event_name: monster.name,
      description: `遭遇${isElite ? '精英' : ''}怪物: ${monster.name} (Lv.${monster.level})`,
      event_type: isElite ? 'elite_battle' : 'battle',
      monster_key: monsterKey,
      monster: {
        name: monster.name,
        level: monster.level,
        hp: monster.hp,
        attack: monster.attack,
        defense: monster.defense,
        speed: monster.speed
      },
      playerPower: {
        atk: playerPower.atk,
        def: playerPower.def,
        spd: playerPower.spd,
        hp: playerPower.hp,
        maxHp: playerPower.maxHp,
        critRate: playerPower.critRate,
        critDamage: playerPower.critDamage,
        level: playerPower.level
      },
      is_elite: isElite,
      coinMultiplier
    }
  };
}

// ── 资源事件处理 ──
function handleResourceEvent(playerId, location, coinMultiplier) {
  const roll = Math.random();
  if (roll < 0.5) {
    const coins = Math.round((25 + Math.floor(Math.random() * 45)) * coinMultiplier);
    playerService.update(playerId, { coins: (playerService.getRaw(playerId).coins || 0) + coins });
    chapterService.awardResource(playerId, 'storyFragments', 1);
    return { resultData: { event_name: '发现资源', description: `你搜刮了一些残留物资。获得硬币 +${coins}，故事碎片 +1`, event_type: 'resource', coins, story_fragments: 1 } };
  } else if (roll < 0.8) {
    inventoryService.addItem(playerId, 'small_hp_potion', 1);
    return { resultData: { event_name: '发现补给', description: '你找到了一个小型HP药剂。', event_type: 'resource', items: ['small_hp_potion'] } };
  } else {
    const coins = Math.round(10 * coinMultiplier);
    playerService.update(playerId, { coins: (playerService.getRaw(playerId).coins || 0) + coins });
    chapterService.awardResource(playerId, 'storyFragments', 2);
    return { resultData: { event_name: '意外发现', description: `在废墟中翻找时找到了一个隐藏的暗格！硬币 +${coins}，故事碎片 +2`, event_type: 'resource', coins, story_fragments: 2 } };
  }
}

// ── 辅助: 应用事件奖励 ──
function applyEventRewards(playerId, rewards, player) {
  if (!rewards || Object.keys(rewards).length === 0) return;

  const p = player || playerService.get(playerId);
  const rawPlayer = playerService.getRaw(playerId);
  let coins = p.coins || rawPlayer.coins;
  let storyFragments = p.story_fragments || rawPlayer.story_fragments;
  const stats = p.stats || JSON.parse(rawPlayer.stats_json || 'null') || {};
  const storyFlags = p.story_flags || JSON.parse(rawPlayer.story_flags_json || 'null') || {};
  const sponsors = p.sponsors || JSON.parse(rawPlayer.sponsors_json || 'null') || [];

  if (rewards.coins) coins += rewards.coins;
  if (rewards.story_fragments) storyFragments += rewards.story_fragments;
  if (rewards.flags) Object.assign(storyFlags, rewards.flags);
  if (rewards.stats) {
    for (const [k, v] of Object.entries(rewards.stats)) {
      stats[k] = (stats[k] || 0) + v;
    }
  }
  if (rewards.sponsors_add) {
    for (const s of rewards.sponsors_add) {
      if (!sponsors.includes(s)) sponsors.push(s);
    }
  }

  // 阶段资源
  if (rewards.scenarioProof) chapterService.awardResource(playerId, 'scenarioProof', rewards.scenarioProof);
  if (rewards.constellationFavor) chapterService.awardResource(playerId, 'constellationFavor', rewards.constellationFavor);
  if (rewards.kingToken) chapterService.awardResource(playerId, 'kingToken', rewards.kingToken);
  if (rewards.abyssMark) chapterService.awardResource(playerId, 'abyssMark', rewards.abyssMark);
  if (rewards.finalPage) chapterService.awardResource(playerId, 'finalPage', rewards.finalPage);

  playerService.update(playerId, {
    coins, story_fragments: storyFragments,
    stats_json: stats, story_flags_json: storyFlags, sponsors_json: sponsors
  });
}

// ── 辅助: 应用事件风险 ──
function applyEventRisks(playerId, risks) {
  if (!risks || Object.keys(risks).length === 0) return;

  const player = playerService.getRaw(playerId);
  const stats = JSON.parse(player.stats_json);
  const permFlags = JSON.parse(player.permanent_flags_json);

  if (risks.hp_loss) {
    stats.hp = Math.max(0, (stats.hp || 0) - risks.hp_loss);
  }
  if (risks.worldLineShift) {
    stats.worldLineShift = (stats.worldLineShift || 0) + risks.worldLineShift;
  }
  if (risks.channelHeat) {
    stats.channelHeat = (stats.channelHeat || 0) + risks.channelHeat;
  }

  playerService.update(playerId, { stats_json: stats, permanent_flags_json: permFlags });
}

function checkEventConditions(player, conditions) {
  if (Object.keys(conditions).length === 0) return true;
  const stats = player.stats || {};
  const storyFlags = player.story_flags || {};
  const permFlags = player.permanent_flags || {};
  if (conditions.required_level && stats.level < conditions.required_level) return false;
  if (conditions.required_flags) {
    for (const [k, v] of Object.entries(conditions.required_flags)) {
      if ((storyFlags[k] || permFlags[k]) !== v) return false;
    }
  }
  return true;
}

// ── 应用称号对事件概率的修正 ──
function applyTitleEventModifiers(baseProbs, mods) {
  if (!mods || Object.keys(mods).length === 0) return { ...baseProbs };
  const adjusted = { ...baseProbs };
  for (const [key, val] of Object.entries(mods)) {
    if (adjusted[key] !== undefined) {
      adjusted[key] = Math.max(0, Math.min(1.0, adjusted[key] + val));
    }
  }
  return adjusted;
}

// ── 加权随机选择 ──
function weightedSelect(events) {
  if (events.length === 0) return null;
  if (events.length === 1) return events[0];
  const totalWeight = events.reduce((sum, e) => sum + (e.weight || 1), 0);
  let r = Math.random() * totalWeight;
  for (const e of events) {
    r -= (e.weight || 1);
    if (r <= 0) return e;
  }
  return events[events.length - 1];
}

module.exports = {
  getUnlockedLocations,
  startExploration,
  getStageProgress,
  checkUnlockConditions,
  applyLocationUnlocks,
  applyTitleEventModifiers,
  tryAdvanceChapter
};
