// 主线阶段系统 — 阶段推进、资源管理、条件检查
// 状态机: locked → current → (剧情完成) → awaiting_advance → (条件满足) → ready_to_advance → (推进) → completed
const { getDb } = require('../db/database');
const playerService = require('./playerService');

const defaultResources = {
  storyFragments: 0,
  constellationFavor: 0,
  abyssMark: 0
};

// 获取玩家阶段推进资源
function getResources(playerId) {
  const player = playerService.getRaw(playerId);
  if (!player) return null;
  const resources = JSON.parse(player.breakthrough_resources_json || '{}');
  for (const [k, v] of Object.entries(defaultResources)) {
    if (!(k in resources)) resources[k] = v;
  }
  return resources;
}

// 给予玩家阶段推进资源
function awardResource(playerId, resourceType, amount) {
  if (!amount || amount <= 0) return;
  // Redirect deprecated resource types
  if (resourceType === 'scenarioProof') {
    playerService.update(playerId, { story_fragments: (playerService.getRaw(playerId).story_fragments || 0) + (amount * 10) });
    return;
  }
  if (resourceType === 'kingToken') {
    resourceType = 'abyssMark';
  }
  if (resourceType === 'finalPage') return; // Removed currency

  const player = playerService.getRaw(playerId);
  if (!player) return;
  const resources = JSON.parse(player.breakthrough_resources_json || '{}');
  for (const [k, v] of Object.entries(defaultResources)) {
    if (!(k in resources)) resources[k] = v;
  }
  resources[resourceType] = (resources[resourceType] || 0) + amount;
  playerService.update(playerId, { breakthrough_resources_json: resources });
}

// 记录击败Boss
function recordBossKill(playerId, bossKey) {
  const player = playerService.getRaw(playerId);
  if (!player) return;
  const bossKills = JSON.parse(player.boss_kills_json || '[]');
  if (!bossKills.includes(bossKey)) {
    bossKills.push(bossKey);
    playerService.update(playerId, { boss_kills_json: bossKills });
  }
}

// 计算章节状态
function computeStatus(chapter, player, unlockedChapters, completedChapters) {
  const chapterKey = chapter.chapter_key;
  const currentMainChapter = player.current_main_chapter || 'main_ch01_paid_service';

  if (completedChapters.includes(chapterKey)) return 'completed';
  if (chapterKey === currentMainChapter) return 'current';
  if (chapter.is_unlocked_by_default || unlockedChapters.includes(chapterKey)) return 'unlocked';

  const conditions = JSON.parse(chapter.unlock_conditions_json);
  const prevChapter = conditions.required_previous_chapter;
  if (prevChapter && completedChapters.includes(prevChapter)) {
    const playerFull = playerService.get(player.id);
    const resources = getResources(player.id);
    const bossKills = JSON.parse(player.boss_kills_json || '[]');

    const advanceCost = JSON.parse(chapter.breakthrough_cost_json);
    let allMet = true;

    for (const [res, amount] of Object.entries(advanceCost)) {
      if ((resources[res] || 0) < amount) { allMet = false; break; }
    }

    if (allMet && conditions.required_level && playerFull.stats.level < conditions.required_level) {
      allMet = false;
    }

    if (allMet && conditions.required_boss_kills) {
      for (const bossKey of conditions.required_boss_kills) {
        if (!bossKills.includes(bossKey)) { allMet = false; break; }
      }
    }

    if (allMet && conditions.required_flags) {
      const storyFlags = playerFull.story_flags || {};
      const permFlags = playerFull.permanent_flags || {};
      for (const flag of conditions.required_flags) {
        if (!storyFlags[flag] && !permFlags[flag]) { allMet = false; break; }
      }
    }

    return allMet ? 'ready_to_advance' : 'awaiting_advance';
  }

  return 'locked';
}

// 构建缺失条件列表
function buildMissingRequirements(player, chapter) {
  const conditions = JSON.parse(chapter.unlock_conditions_json);
  const advanceCost = JSON.parse(chapter.breakthrough_cost_json);
  const playerFull = playerService.get(player.id);
  const resources = getResources(player.id);
  const bossKills = JSON.parse(player.boss_kills_json || '[]');
  const missing = [];

  for (const [res, amount] of Object.entries(advanceCost)) {
    const current = resources[res] || 0;
    if (current < amount) {
      missing.push({
        type: 'resource',
        resource: res,
        label: getResourceLabel(res),
        required: amount,
        current
      });
    }
  }

  if (conditions.required_level && playerFull.stats.level < conditions.required_level) {
    missing.push({
      type: 'level',
      label: '等级不足',
      required: conditions.required_level,
      current: playerFull.stats.level
    });
  }

  if (conditions.required_boss_kills) {
    for (const bossKey of conditions.required_boss_kills) {
      if (!bossKills.includes(bossKey)) {
        const db = getDb();
        const monster = db.prepare('SELECT name FROM monsters WHERE monster_key = ?').get(bossKey);
        missing.push({
          type: 'boss_kill',
          label: '需要击败Boss',
          boss_key: bossKey,
          boss_name: monster ? monster.name : bossKey
        });
      }
    }
  }

  if (conditions.required_flags) {
    const storyFlags = playerFull.story_flags || {};
    const permFlags = playerFull.permanent_flags || {};
    for (const flag of conditions.required_flags) {
      if (!storyFlags[flag] && !permFlags[flag]) {
        missing.push({
          type: 'flag',
          label: '需要达成特定条件',
          flag
        });
      }
    }
  }

  return missing;
}

// 获取玩家阶段状态
function getChapterStatus(playerId) {
  const db = getDb();
  const player = playerService.get(playerId);
  if (!player) return { success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  const rawPlayer = playerService.getRaw(playerId);
  const unlockedChapters = JSON.parse(rawPlayer.unlocked_chapters_json || '[]');
  const completedChapters = JSON.parse(rawPlayer.completed_chapters_json || '[]');
  const currentMainChapter = rawPlayer.current_main_chapter || 'main_ch01_paid_service';
  const resources = getResources(playerId);
  const bossKills = JSON.parse(rawPlayer.boss_kills_json || '[]');

  const allChapters = db.prepare('SELECT * FROM main_chapters ORDER BY order_index').all();
  const chapters = allChapters.map(ch => {
    const status = computeStatus(ch, rawPlayer, unlockedChapters, completedChapters);
    const advanceCost = JSON.parse(ch.breakthrough_cost_json);
    const rewards = JSON.parse(ch.rewards_json);
    const missingRequirements = (status === 'awaiting_advance' || status === 'ready_to_advance')
      ? buildMissingRequirements(player, ch) : [];

    return {
      chapter_key: ch.chapter_key,
      chapter_name: ch.chapter_name,
      description: ch.description,
      order_index: ch.order_index,
      status,
      is_unlocked: ch.is_unlocked_by_default || unlockedChapters.includes(ch.chapter_key) || completedChapters.includes(ch.chapter_key) || ch.chapter_key === currentMainChapter,
      is_completed: completedChapters.includes(ch.chapter_key),
      is_current: ch.chapter_key === currentMainChapter,
      is_awaiting_advance: status === 'awaiting_advance' || status === 'ready_to_advance',
      can_advance: status === 'ready_to_advance',
      missing_requirements: missingRequirements,
      advance_cost: advanceCost,
      rewards,
      advance_text: ch.breakthrough_text,
      story_chapter_keys: JSON.parse(ch.story_chapter_keys_json)
    };
  });

  // 检查当前阶段是否已完成但未进入下一阶段
  const currentStageCompleted = completedChapters.includes(currentMainChapter);
  let needsStageAdvance = false;
  let nextStage = null;

  if (currentStageCompleted) {
    needsStageAdvance = true;
    // 查找下一阶段
    const currentStageConfig = allChapters.find(c => c.chapter_key === currentMainChapter);
    if (currentStageConfig) {
      nextStage = allChapters.find(c => c.order_index > currentStageConfig.order_index);
    }
  }

  // 检查是否有 ready_to_advance 的阶段
  const readyStage = chapters.find(c => c.status === 'ready_to_advance');

  // 获取探索阶段进度
  const stageProgress = playerFull.stage_progress || {};

  return {
    success: true,
    data: {
      chapters,
      current_main_chapter: currentMainChapter,
      resources,
      unlocked_chapters: unlockedChapters,
      completed_chapters: completedChapters,
      boss_kills: bossKills,
      current_stage_completed: currentStageCompleted,
      needs_stage_advance: needsStageAdvance,
      next_stage: nextStage ? {
        chapter_key: nextStage.chapter_key,
        chapter_name: nextStage.chapter_name
      } : null,
      can_advance_next_stage: !!readyStage,
      advance_target: readyStage ? readyStage.chapter_key : null,
      exploration_progress: stageProgress
    }
  };
}

// 检查是否可以推进到目标阶段
function checkAdvance(playerId, chapterKey) {
  const db = getDb();
  const player = playerService.getRaw(playerId);
  if (!player) return { can: false, reason: '玩家不存在', missingRequirements: [] };

  const chapter = db.prepare('SELECT * FROM main_chapters WHERE chapter_key = ?').get(chapterKey);
  if (!chapter) return { can: false, reason: '阶段不存在', missingRequirements: [] };

  const unlockedChapters = JSON.parse(player.unlocked_chapters_json || '[]');
  const completedChapters = JSON.parse(player.completed_chapters_json || '[]');

  if (chapter.is_unlocked_by_default) {
    return { can: false, reason: '初始阶段无需推进', missingRequirements: [] };
  }

  if (unlockedChapters.includes(chapterKey)) {
    return { can: false, reason: '该阶段已解锁', missingRequirements: [] };
  }

  if (completedChapters.includes(chapterKey)) {
    return { can: false, reason: '该阶段已完成', missingRequirements: [] };
  }

  const conditions = JSON.parse(chapter.unlock_conditions_json);

  if (conditions.required_previous_chapter) {
    if (!completedChapters.includes(conditions.required_previous_chapter)) {
      return { can: false, reason: '前置阶段未完成', missingRequirements: [] };
    }
  }

  const playerFull = playerService.get(playerId);
  const resources = getResources(playerId);
  const bossKills = JSON.parse(player.boss_kills_json || '[]');
  const advanceCost = JSON.parse(chapter.breakthrough_cost_json);
  const missing = [];

  for (const [res, amount] of Object.entries(advanceCost)) {
    const current = resources[res] || 0;
    if (current < amount) {
      missing.push({
        type: 'resource', resource: res,
        label: getResourceLabel(res), required: amount, current
      });
    }
  }

  if (conditions.required_level && playerFull.stats.level < conditions.required_level) {
    missing.push({
      type: 'level', label: '等级不足',
      required: conditions.required_level, current: playerFull.stats.level
    });
  }

  if (conditions.required_boss_kills) {
    for (const bossKey of conditions.required_boss_kills) {
      if (!bossKills.includes(bossKey)) {
        const monster = db.prepare('SELECT name FROM monsters WHERE monster_key = ?').get(bossKey);
        missing.push({
          type: 'boss_kill', label: '需要击败Boss',
          boss_key: bossKey, boss_name: monster ? monster.name : bossKey
        });
      }
    }
  }

  if (conditions.required_flags) {
    const storyFlags = playerFull.story_flags || {};
    const permFlags = playerFull.permanent_flags || {};
    for (const flag of conditions.required_flags) {
      if (!storyFlags[flag] && !permFlags[flag]) {
        missing.push({ type: 'flag', label: '需要达成特定条件', flag });
      }
    }
  }

  if (conditions.required_titles) {
    const titles = playerFull.titles || [];
    for (const t of conditions.required_titles) {
      if (!titles.includes(t)) {
        missing.push({ type: 'title', label: '需要称号', title: t });
      }
    }
  }

  if (missing.length > 0) {
    return { can: false, reason: '推进条件不满足', missingRequirements: missing };
  }

  return { can: true, missingRequirements: [] };
}

// 执行阶段推进 — 解锁目标阶段
function advanceStage(playerId, chapterKey) {
  const check = checkAdvance(playerId, chapterKey);
  if (!check.can) {
    return {
      success: false,
      error: { code: 'CANNOT_ADVANCE', message: check.reason, missingRequirements: check.missingRequirements }
    };
  }

  const db = getDb();
  const player = playerService.getRaw(playerId);
  const chapter = db.prepare('SELECT * FROM main_chapters WHERE chapter_key = ?').get(chapterKey);

  // 扣除推进资源
  const advanceCost = JSON.parse(chapter.breakthrough_cost_json);
  const resources = getResources(playerId);
  for (const [res, amount] of Object.entries(advanceCost)) {
    resources[res] = Math.max(0, (resources[res] || 0) - amount);
  }

  // 发放奖励
  const rewards = JSON.parse(chapter.rewards_json);
  const playerFull = playerService.get(playerId);
  let coins = playerFull.coins + (rewards.coins || 0);
  let storyFragments = playerFull.story_fragments + (rewards.story_fragments || 0);

  // 永久标记
  const permFlags = JSON.parse(player.permanent_flags_json);
  if (rewards.permanent_flags) Object.assign(permFlags, rewards.permanent_flags);

  // 解锁目标阶段 (不标记为 completed)
  const unlockedChapters = JSON.parse(player.unlocked_chapters_json || '[]');
  if (!unlockedChapters.includes(chapterKey)) {
    unlockedChapters.push(chapterKey);
  }

  const updates = {
    current_main_chapter: chapterKey,
    current_chapter: chapter.first_story_node_key,
    coins,
    story_fragments: storyFragments,
    breakthrough_resources_json: resources,
    unlocked_chapters_json: unlockedChapters,
    permanent_flags_json: permFlags,
    consumed_chapters_json: [],
    chapter_actions_json: {}
  };

  playerService.update(playerId, updates);
  // Reset pending_next_chapter directly (playerService.update would JSON.stringify null)
  db.prepare(`UPDATE players SET pending_next_chapter = NULL, updated_at = datetime('now','localtime') WHERE id = ?`).run(playerId);
  playerService.addLog(playerId, `进入下一阶段: ${chapter.chapter_name}`);

  // Round 6: 星流放送贡献记录 — 阶段推进
  try {
    const broadcastService = require('./broadcastService');
    broadcastService.tryRecordContributions(playerId, [{ type: 'complete_stage', amount: 1, metadata: { chapter_key: chapterKey } }]);
  } catch (e) { /* broadcast not critical */ }

  // 解锁技能
  let unlockedSkills = [];
  let unlockedSkillNames = [];
  if (rewards.unlock_skills) {
    for (const sk of rewards.unlock_skills) {
      try {
        const existing = db.prepare('SELECT id FROM player_skills WHERE player_id = ? AND skill_key = ?').get(playerId, sk);
        if (!existing) {
          db.prepare('INSERT INTO player_skills (player_id, skill_key) VALUES (?, ?)').run(playerId, sk);
          unlockedSkills.push(sk);
          const skDef = db.prepare('SELECT name FROM skills WHERE skill_key = ?').get(sk);
          unlockedSkillNames.push(skDef ? skDef.name : sk);
        }
      } catch (e) { /* skill may not exist */ }
    }
  }

  return {
    success: true,
    data: {
      chapter: { chapter_key: chapter.chapter_key, chapter_name: chapter.chapter_name },
      resources_after: resources,
      rewards,
      unlocked_skills: unlockedSkills,
      unlocked_skill_names: unlockedSkillNames,
      player: playerService.get(playerId)
    }
  };
}

// 完成当前阶段（当玩家到达最后一个故事节点时调用）
function completeChapter(playerId) {
  const db = getDb();
  const player = playerService.getRaw(playerId);
  if (!player) return null;

  const currentMain = player.current_main_chapter || 'main_ch01_paid_service';
  const completedChapters = JSON.parse(player.completed_chapters_json || '[]');
  if (completedChapters.includes(currentMain)) return null;

  const chapter = db.prepare('SELECT * FROM main_chapters WHERE chapter_key = ?').get(currentMain);
  if (!chapter) return null;

  // 标记当前阶段完成
  completedChapters.push(currentMain);

  // 发放阶段完成奖励
  const rewards = JSON.parse(chapter.rewards_json);
  if (rewards.story_fragments) {
    awardResource(playerId, 'storyFragments', rewards.story_fragments);
  }
  awardResource(playerId, 'storyFragments', 10);

  // 自动设置下一阶段所需的关键 flag
  const storyFlags = JSON.parse(player.story_flags_json || '{}');
  if (currentMain === 'main_ch01_paid_service') {
    storyFlags.first_scenario_cleared = true;
  }

  playerService.update(playerId, {
    completed_chapters_json: completedChapters,
    story_flags_json: storyFlags
  });
  playerService.addLog(playerId, `完成阶段: ${chapter.chapter_name}`);

  // 查找下一阶段
  const nextChapter = db.prepare(
    'SELECT * FROM main_chapters WHERE order_index > ? ORDER BY order_index LIMIT 1'
  ).get(chapter.order_index);

  if (nextChapter) {
    playerService.addLog(playerId, `下一阶段条件已开放: ${nextChapter.chapter_name}`);
  }

  return {
    completed: chapter.chapter_key,
    next_pending: nextChapter ? nextChapter.chapter_key : null
  };
}

// 检查当前阶段目标是否完成 — stage_final 的前置条件
// includeFinalEventCheck=false 用于探索系统判断是否可触发最终事件（跳过自引用检查）
function checkCurrentStageObjectives(playerId, includeFinalEventCheck = true) {
  const db = getDb();
  const player = playerService.getRaw(playerId);
  if (!player) return { canComplete: false, missing: [{ label: '玩家不存在' }] };

  const currentMain = player.current_main_chapter || 'main_ch01_paid_service';
  const chapter = db.prepare('SELECT * FROM main_chapters WHERE chapter_key = ?').get(currentMain);
  if (!chapter) return { canComplete: false, missing: [{ label: '当前阶段配置不存在' }] };

  const objectivesConfig = JSON.parse(chapter.stage_objectives_json || '{}');
  const objectives = objectivesConfig.objectives || [];
  if (objectives.length === 0) return { canComplete: true, missing: [] };

  const playerFull = playerService.get(playerId);
  const storyFlags = playerFull.story_flags || {};
  const permFlags = playerFull.permanent_flags || {};
  const visitedNodes = playerFull.visited_nodes || [];
  const sponsors = playerFull.sponsors || [];
  const stats = playerFull.stats || {};
  const stageProgress = playerFull.stage_progress || {};
  const missing = [];

  // 检查 final_story_event 是否已触发 (通过探索)
  // 仅在 stage_final 选项目标检查时包含此项; 探索触发检查时跳过以避免循环依赖
  if (includeFinalEventCheck) {
    const finalEventKey = `story_stage_${String(chapter.order_index).padStart(2, '0')}_settlement`;
    const hasFinalEvent = stageProgress.finalStoryEventTriggered === finalEventKey ||
      (stageProgress.storyEventsTriggered || []).includes(finalEventKey);
    if (!hasFinalEvent) {
      missing.push({ type: 'final_story_event', label: '需要通过探索触发阶段最终剧情事件' });
    }
  }

  for (const obj of objectives) {
    let met = false;
    switch (obj.type) {
      case 'visited_nodes_min':
        met = visitedNodes.length >= (obj.count || 0);
        break;
      case 'any_flag':
        met = (obj.keys || []).some(k => storyFlags[k] || permFlags[k]);
        break;
      case 'has_sponsor':
        met = sponsors.length > 0;
        break;
      case 'coins_min':
        met = (playerFull.coins || 0) >= (obj.amount || 0);
        break;
      case 'story_fragments_min':
        met = (playerFull.story_fragments || 0) >= (obj.amount || 0);
        break;
      case 'story_events_min':
        met = (stageProgress.storyEventsTriggered || []).length >= (obj.count || 0);
        break;
      case 'explorations_by_location':
        met = (stageProgress.explorationsByLocation || {})[obj.location_key] >= (obj.count || 0);
        break;
      case 'boss_clues_min':
        met = Object.keys(stageProgress.bossClues || {}).length >= (obj.count || 0);
        break;
      case 'opportunity_events_min':
        met = (stageProgress.opportunityEventsTriggered || []).length >= (obj.count || 0);
        break;
      case 'resource_min':
        met = (playerFull.coins || 0) >= (obj.amount || 0);
        break;
      case 'boss_kill':
        met = (JSON.parse(player.boss_kills_json || '[]')).includes(obj.boss_key);
        break;
      case 'level_min':
        met = (stats.level || 1) >= (obj.level || 1);
        break;
      case 'title_required':
        met = (playerFull.titles || []).includes(obj.title_key);
        break;
      case 'flag_required':
        met = !!(storyFlags[obj.flag_key] || permFlags[obj.flag_key]);
        break;
      default:
        met = true;
    }
    if (!met) {
      missing.push({ type: obj.type, label: obj.label || '未知目标' });
    }
  }

  return { canComplete: missing.length === 0, missing };
}

function getResourceLabel(res) {
  const labels = {
    storyFragments: '故事碎片',
    constellationFavor: '星座垂青',
    abyssMark: '深渊刻痕'
  };
  return labels[res] || res;
}

// 向后兼容别名
const executeBreakthrough = advanceStage;
const checkBreakthrough = checkAdvance;

module.exports = {
  getChapterStatus,
  checkAdvance,
  advanceStage,
  awardResource,
  recordBossKill,
  getResources,
  completeChapter,
  checkCurrentStageObjectives,
  computeStatus,
  buildMissingRequirements,
  defaultResources,
  getResourceLabel,
  // 向后兼容
  checkBreakthrough,
  executeBreakthrough
};
