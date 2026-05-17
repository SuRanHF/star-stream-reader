const { getDb } = require('../db/database');
const playerService = require('./playerService');
const titleService = require('./titleService');
const endingService = require('./endingService');
const chapterService = require('./chapterService');

// ---- 阶段辅助函数 ----

function getStoryMainChapterKey(chapterKey) {
  const db = getDb();
  const ch = db.prepare('SELECT main_chapter_key FROM chapters WHERE chapter_key = ?').get(chapterKey);
  return ch ? ch.main_chapter_key : null;
}

function getCurrentMainChapter(player) {
  return player.current_main_chapter || 'main_ch01_paid_service';
}

function isCurrentStageCompleted(player) {
  const completedStages = JSON.parse(player.completed_chapters_json || '[]');
  return completedStages.includes(getCurrentMainChapter(player));
}

function isCrossStageToNext(player, nextChapterKey) {
  const currentMain = getCurrentMainChapter(player);
  const nextMain = getStoryMainChapterKey(nextChapterKey);
  if (!nextMain) return false;
  if (nextMain === currentMain) return false;
  const unlockedChapters = JSON.parse(player.unlocked_chapters_json || '[]');
  const completedChapters = JSON.parse(player.completed_chapters_json || '[]');
  if (unlockedChapters.includes(nextMain) || completedChapters.includes(nextMain)) return false;
  return true;
}

function assertStoryStageAccess(player, currentChapter) {
  const currentMain = getCurrentMainChapter(player);
  if (currentChapter.main_chapter_key !== currentMain) {
    return { ok: false, code: 'STORY_STAGE_MISMATCH', message: '当前剧情节点不属于当前主线阶段' };
  }
  if (isCurrentStageCompleted(player)) {
    return { ok: false, code: 'STAGE_AWAITING_ADVANCE', message: '当前阶段已完成，请先满足条件并进入下一阶段' };
  }
  return { ok: true };
}

// ---- 决策组检查 ----
function getChosenInDecisionGroup(player, decisionGroup, excludeChoiceKey) {
  if (!decisionGroup) return [];
  const db = getDb();
  const routeHistory = JSON.parse(player.route_history_json);
  const groupChoices = db.prepare(
    'SELECT choice_key FROM choices WHERE decision_group = ? AND choice_key != ?'
  ).all(decisionGroup, excludeChoiceKey);
  const groupKeys = groupChoices.map(c => c.choice_key);
  return groupKeys.filter(k => routeHistory.includes(k));
}

// ---- 检查一次性 action 是否已完成 ----
function isOneShotActionDone(player, choiceKey) {
  const activityHistory = JSON.parse(player.activity_history_json || '[]');
  return activityHistory.some(a => a.choice_key === choiceKey);
}

// ---- 主要函数 ----

function getCurrentChapter(playerId) {
  const db = getDb();
  const player = playerService.getRaw(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  const chapter = db.prepare('SELECT * FROM chapters WHERE chapter_key = ?').get(player.current_chapter);
  if (!chapter) return { error: { code: 'CHAPTER_NOT_FOUND', message: '章节不存在' } };

  const allChoices = db.prepare('SELECT * FROM choices WHERE chapter_key = ?').all(player.current_chapter);
  const routeHistory = JSON.parse(player.route_history_json);
  const playerTitles = JSON.parse(player.titles_json);
  const storyFlags = JSON.parse(player.story_flags_json || '{}');
  const permFlags = JSON.parse(player.permanent_flags_json || '{}');
  const consumedChapters = JSON.parse(player.consumed_chapters_json || '[]');
  const isChapterConsumed = consumedChapters.includes(player.current_chapter);

  const available = [];
  const locked = [];

  const stageCompleted = isCurrentStageCompleted(player);
  const stageAccess = assertStoryStageAccess(player, chapter);

  for (const ch of allChoices) {
    const choiceType = ch.choice_type || 'progress';
    const decisionGroup = ch.decision_group || null;
    const hideAfterUse = !!(ch.hide_after_use);

    // 阶段完成后所有选项锁定
    if (stageCompleted || !stageAccess.ok) {
      locked.push(formatChoice(ch));
      continue;
    }

    // 章节已消耗: 所有选项锁定
    if (isChapterConsumed) {
      locked.push({ ...formatChoice(ch), locked_reason: '本章已结束，请通过探索推进剧情' });
      continue;
    }

    // Flag / title 检查 (所有类型都检查)
    const reqFlags = JSON.parse(ch.required_flags_json);
    const blockedFlags = JSON.parse(ch.blocked_flags_json);
    const reqTitles = JSON.parse(ch.required_titles_json);
    const blockedTitles = JSON.parse(ch.blocked_titles_json);

    if (!flagsMatch(storyFlags, permFlags, reqFlags)) { locked.push(formatChoice(ch)); continue; }
    if (!flagsBlocked(storyFlags, permFlags, blockedFlags)) { locked.push(formatChoice(ch)); continue; }
    if (reqTitles.length > 0 && !reqTitles.every(t => playerTitles.includes(t))) { locked.push(formatChoice(ch)); continue; }
    if (blockedTitles.length > 0 && blockedTitles.some(t => playerTitles.includes(t))) { locked.push(formatChoice(ch)); continue; }

    // hide_after_use action: 如果已完成, 移到 locked
    if (hideAfterUse && isOneShotActionDone(player, ch.choice_key)) {
      locked.push({ ...formatChoice(ch), locked_reason: '已完成' });
      continue;
    }

    // action / repeatable: 每章只能选一个调查
    if (choiceType === 'action' || choiceType === 'repeatable') {
      const chapterActions = JSON.parse(player.chapter_actions_json || '{}');
      if (chapterActions[player.current_chapter]) {
        locked.push({ ...formatChoice(ch), locked_reason: '本章已选择过调查行动' });
      } else {
        available.push(formatChoice(ch));
      }
      continue;
    }

    // 已在 routeHistory 中: 锁定
    if (routeHistory.includes(ch.choice_key)) {
      locked.push(formatChoice(ch));
      continue;
    }

    // decision: 同组其他选项已被选择 → 锁定
    if (choiceType === 'decision' && decisionGroup) {
      const chosenInGroup = getChosenInDecisionGroup(player, decisionGroup, ch.choice_key);
      if (chosenInGroup.length > 0) {
        locked.push(formatChoice(ch));
        continue;
      }
    }

    available.push(formatChoice(ch));
  }

  const parsedPlayer = playerService.get(playerId);
  const endings = endingService.checkEndings(playerId);

  const result = {
    chapter: { chapter_key: chapter.chapter_key, title: chapter.title, summary: chapter.summary, is_ending_chapter: !!chapter.is_ending_chapter },
    choices: available,
    locked_choices: locked,
    player: parsedPlayer,
    endings: endings.length > 0 ? endings : null
  };

  if (isChapterConsumed) {
    result.chapter_consumed = true;
    result.consumed_message = '本章已结束，请通过探索推进剧情';
  }

  if (stageCompleted) {
    result.stage_blocked = true;
    result.stage_message = '当前阶段剧情已完成，请在主线页满足条件后进入下一阶段';
  } else if (!stageAccess.ok) {
    result.stage_blocked = true;
    result.stage_message = stageAccess.message;
  }

  return result;
}

function canChoose(playerId, choiceKey) {
  const db = getDb();
  const player = playerService.getRaw(playerId);
  if (!player) return { ok: false, code: 'PLAYER_NOT_FOUND', message: '玩家不存在' };

  const choice = db.prepare('SELECT * FROM choices WHERE choice_key = ?').get(choiceKey);
  if (!choice) return { ok: false, code: 'CHOICE_NOT_FOUND', message: '选项不存在' };

  if (choice.chapter_key !== player.current_chapter)
    return { ok: false, code: 'WRONG_CHAPTER', message: '该选项不属于当前章节' };

  const currentChapter = db.prepare('SELECT * FROM chapters WHERE chapter_key = ?').get(player.current_chapter);
  if (!currentChapter) return { ok: false, code: 'CHAPTER_NOT_FOUND', message: '章节不存在' };

  // 章节已消耗: 拒绝所有选择
  const consumedChapters = JSON.parse(player.consumed_chapters_json || '[]');
  if (consumedChapters.includes(player.current_chapter))
    return { ok: false, code: 'CHAPTER_CONSUMED', message: '本章已结束，请通过探索推进剧情' };

  const stageAccess = assertStoryStageAccess(player, currentChapter);
  if (!stageAccess.ok) return stageAccess;

  const choiceType = choice.choice_type || 'progress';
  const decisionGroup = choice.decision_group || null;
  const hideAfterUse = !!(choice.hide_after_use);
  const routeHistory = JSON.parse(player.route_history_json);
  const playerTitles = JSON.parse(player.titles_json);
  const storyFlags = JSON.parse(player.story_flags_json || '{}');
  const permFlags = JSON.parse(player.permanent_flags_json || '{}');

  // Flag / title 检查
  const reqFlags = JSON.parse(choice.required_flags_json);
  if (!flagsMatch(storyFlags, permFlags, reqFlags))
    return { ok: false, code: 'FLAGS_NOT_MET', message: '未满足选项所需的条件标记' };

  const blockedFlags = JSON.parse(choice.blocked_flags_json);
  if (!flagsBlocked(storyFlags, permFlags, blockedFlags))
    return { ok: false, code: 'BLOCKED_BY_FLAGS', message: '当前状态阻止了此选项' };

  const reqTitles = JSON.parse(choice.required_titles_json);
  if (reqTitles.length > 0 && !reqTitles.every(t => playerTitles.includes(t)))
    return { ok: false, code: 'TITLES_NOT_MET', message: '未拥有所需称号' };

  const blockedTitles = JSON.parse(choice.blocked_titles_json);
  if (blockedTitles.length > 0 && blockedTitles.some(t => playerTitles.includes(t)))
    return { ok: false, code: 'BLOCKED_BY_TITLES', message: '当前称号阻止了此选项' };

  // hide_after_use action: 检查是否已完成
  if (hideAfterUse && isOneShotActionDone(player, choiceKey)) {
    return { ok: false, code: 'ACTION_ALREADY_DONE', message: '该行动已完成' };
  }

  // action / repeatable: 每章只能选一个
  if (choiceType === 'action' || choiceType === 'repeatable') {
    const chapterActions = JSON.parse(player.chapter_actions_json || '{}');
    if (chapterActions[player.current_chapter]) {
      return { ok: false, code: 'ACTION_ALREADY_DONE', message: '本章已选择过调查行动' };
    }
    return { ok: true, choice };
  }

  // 已选过该选项
  if (routeHistory.includes(choiceKey))
    return { ok: false, code: 'ALREADY_CHOSEN', message: '你已经选择过这个选项' };

  // decision: 同组其他选项是否已被选择
  if (choiceType === 'decision' && decisionGroup) {
    const chosenInGroup = getChosenInDecisionGroup(player, decisionGroup, choiceKey);
    if (chosenInGroup.length > 0) {
      return { ok: false, code: 'DECISION_GROUP_LOCKED', message: '你已经在分歧中做出了选择，无法选择同一分歧的其他选项' };
    }
  }

  return { ok: true, choice };
}

function applyChoice(playerId, choiceKey) {
  const check = canChoose(playerId, choiceKey);
  if (!check.ok) return { error: { code: check.code, message: check.message } };

  const db = getDb();
  const player = playerService.getRaw(playerId);
  const choice = check.choice;
  const choiceType = choice.choice_type || 'progress';
  const decisionGroup = choice.decision_group || null;
  const hideAfterUse = !!(choice.hide_after_use);

  const effects = JSON.parse(choice.effects_json);
  const storyFlags = JSON.parse(player.story_flags_json);
  const permFlags = JSON.parse(player.permanent_flags_json);
  const stats = JSON.parse(player.stats_json);
  const relationships = JSON.parse(player.relationships_json);
  const routeHistory = JSON.parse(player.route_history_json);
  const sponsors = JSON.parse(player.sponsors_json);
  const currentTitles = JSON.parse(player.titles_json);
  const visitedNodes = JSON.parse(player.visited_nodes_json || '[]');
  const decisionHistory = JSON.parse(player.decision_history_json || '[]');
  const activityHistory = JSON.parse(player.activity_history_json || '[]');

  const newLogs = [];

  // ── stage_final: 阶段完成触发 ──
  if (choiceType === 'stage_final') {
    const objectiveCheck = chapterService.checkCurrentStageObjectives(playerId);
    if (!objectiveCheck.canComplete) {
      return {
        error: {
          code: 'STAGE_OBJECTIVES_NOT_MET',
          message: '阶段目标未完成: ' + (objectiveCheck.missing || []).map(o => o.label).join(', ')
        }
      };
    }

    // 应用效果
    applyEffects({ effects, storyFlags, permFlags, stats, relationships, sponsors });
    const playerFull = playerService.get(playerId);
    const coinMult = titleService.computeCoinMultiplier(playerFull);
    let coins = player.coins + Math.round((effects.coins || 0) * coinMult);
    let storyFragments = player.story_fragments + (effects.story_fragments || 0);

    // 记录到 routeHistory, visitedNodes, decisionHistory
    routeHistory.push(choiceKey);
    if (!visitedNodes.includes(player.current_chapter)) visitedNodes.push(player.current_chapter);
    decisionHistory.push({ choice_key: choiceKey, type: 'stage_final', time: new Date().toISOString() });

    // 阶段资源奖励
    chapterService.awardResource(playerId, 'storyFragments', effects.story_fragments || 0);
    awardStageResources(playerId, effects);

    const fields = {
      coins, story_fragments: storyFragments,
      stats_json: stats, relationships_json: relationships,
      route_history_json: routeHistory, story_flags_json: storyFlags,
      permanent_flags_json: permFlags, sponsors_json: sponsors,
      titles_json: currentTitles, visited_nodes_json: visitedNodes,
      decision_history_json: decisionHistory
    };
    playerService.update(playerId, fields);

    // 完成当前阶段
    chapterService.completeChapter(playerId);
    playerService.addLog(playerId, `阶段最终: ${choice.text}`);

    newLogs.push({ id: `choice_${choiceKey}_action`, type: 'stage', message: `【阶段最终】${choice.text}` });

    const unlockedTitles = titleService.checkUnlocks(playerId);
    for (const t of unlockedTitles) {
      playerService.addLog(playerId, `获得称号: ${t.name}`);
      newLogs.push({ id: `title_${t.title_key || t.name}`, type: 'reward', message: `获得称号: ${t.name}` });
    }

    if (effects.coins || effects.story_fragments) {
      const parts = [];
      if (effects.coins) parts.push(`硬币 +${Math.round(effects.coins * coinMult)}`);
      if (effects.story_fragments) parts.push(`故事碎片 +${effects.story_fragments}`);
      newLogs.push({ id: `choice_${choiceKey}_reward`, type: 'reward', message: `阶段奖励: ${parts.join(', ')}` });
    }

    const updatedPlayer = playerService.get(playerId);
    const currentStoryChapter = db.prepare('SELECT * FROM chapters WHERE chapter_key = ?').get(player.current_chapter);
    return {
      chapter: currentStoryChapter ? { chapter_key: currentStoryChapter.chapter_key, title: currentStoryChapter.title, summary: currentStoryChapter.summary, is_ending_chapter: !!currentStoryChapter.is_ending_chapter } : null,
      player: updatedPlayer,
      ending: null,
      unlocked_titles: unlockedTitles,
      stage_completed: true,
      needs_stage_advance: true,
      message: '当前阶段已完成，请满足条件后进入下一阶段',
      new_logs: newLogs
    };
  }

  // ── 跨阶段检查 ──
  const currentMain = getCurrentMainChapter(player);
  const nextMain = getStoryMainChapterKey(choice.next_chapter_key);
  if (nextMain && nextMain !== currentMain) {
    return {
      error: {
        code: 'CROSS_STAGE_NOT_ALLOWED',
        message: '不能通过剧情选择跨阶段。请先完成当前阶段目标，然后通过阶段推进进入下一阶段。'
      }
    };
  }

  // ── 应用效果 ──
  applyEffects({ effects, storyFlags, permFlags, stats, relationships, sponsors });
  const playerFull = playerService.get(playerId);
  const coinMult = titleService.computeCoinMultiplier(playerFull);
  let coins = player.coins + Math.round((effects.coins || 0) * coinMult);
  let storyFragments = player.story_fragments + (effects.story_fragments || 0);

  // ── routeHistory 记录 (action / repeatable 不记录) ──
  const entersHistory = choiceType !== 'action' && choiceType !== 'repeatable';
  if (entersHistory) {
    routeHistory.push(choiceKey);
  }

  // ── visitedNodes 记录 ──
  if (!visitedNodes.includes(player.current_chapter)) {
    visitedNodes.push(player.current_chapter);
  }

  // ── decisionHistory 记录 ──
  if (choiceType === 'decision') {
    decisionHistory.push({ choice_key: choiceKey, type: choiceType, group: decisionGroup, time: new Date().toISOString() });
  }

  // ── 一次性 action: 写入 activity_history ──
  if (hideAfterUse) {
    activityHistory.push({
      choice_key: choiceKey,
      type: choiceType,
      chapter_key: player.current_chapter,
      time: new Date().toISOString()
    });
  }

  // ── 阶段资源奖励 ──
  awardStageResources(playerId, effects);

  // ── 章节消耗: progress/decision/stage_final 消耗章节 ──
  // action/repeatable: 不消耗章节，记录本章已选调查
  const consumedChapters = JSON.parse(player.consumed_chapters_json || '[]');
  const chapterActions = JSON.parse(player.chapter_actions_json || '{}');
  const consumesChapter = choiceType === 'progress' || choiceType === 'decision' || choiceType === 'stage_final';
  const pendingNext = consumesChapter ? deriveNextChapterFromSequence(player, choice) : null;

  const fields = {
    coins, story_fragments: storyFragments,
    stats_json: stats, relationships_json: relationships,
    route_history_json: routeHistory, story_flags_json: storyFlags,
    permanent_flags_json: permFlags, sponsors_json: sponsors,
    titles_json: currentTitles, visited_nodes_json: visitedNodes,
    decision_history_json: decisionHistory
  };
  if (hideAfterUse) {
    fields.activity_history_json = activityHistory;
  }

  if (choiceType === 'action' || choiceType === 'repeatable') {
    // 调查类: 记录本章已选，不消耗章节
    chapterActions[player.current_chapter] = choiceKey;
    fields.chapter_actions_json = chapterActions;
  } else {
    // 剧情推进/决策/阶段最终: 消耗章节
    if (!consumedChapters.includes(player.current_chapter)) {
      consumedChapters.push(player.current_chapter);
      fields.consumed_chapters_json = consumedChapters;
    }
    if (pendingNext) {
      fields.pending_next_chapter = pendingNext;
    }
  }

  playerService.update(playerId, fields);
  playerService.addLog(playerId, `选择了: ${choice.text}`);

  // ── 构建 new_logs ──
  // 行动日志
  const logActionType = choiceType === 'action' ? 'action' : choiceType === 'decision' ? 'decision' : 'story';
  newLogs.push({ id: `choice_${choiceKey}_action`, type: logActionType, message: `【${getChoiceTypeLabel(choiceType)}】${choice.text}` });

  // 自定义log
  if (effects.log) {
    newLogs.push({ id: `choice_${choiceKey}_custom`, type: logActionType, message: effects.log });
  }

  // 奖励日志
  const rewardParts = [];
  if (effects.coins) rewardParts.push(`硬币 +${Math.round(effects.coins * coinMult)}`);
  if (effects.story_fragments) rewardParts.push(`故事碎片 +${effects.story_fragments}`);
  if (effects.stats) {
    const { statLabel: sl } = require('../utils/labels');
    for (const [k, v] of Object.entries(effects.stats)) {
      if (v > 0) rewardParts.push(`${sl(k)} +${v}`);
    }
  }
  if (effects.flags) {
    const { resolveChapterName: rcn } = require('../utils/labels');
    for (const k of Object.keys(effects.flags)) {
      rewardParts.push(`标记: ${rcn(k)}`);
    }
  }
  if (rewardParts.length > 0) {
    newLogs.push({ id: `choice_${choiceKey}_reward`, type: 'reward', message: `【获得】${rewardParts.join(', ')}` });
  }

  // ── 称号解锁 ──
  try {
    const broadcastService = require('./broadcastService');
    broadcastService.tryRecordContributions(playerId, [{ type: 'trigger_story', amount: 1, metadata: { choice_key: choiceKey, choice_type: choiceType } }]);
  } catch (e) { /* broadcast not critical */ }

  const unlockedTitles = titleService.checkUnlocks(playerId);
  for (const t of unlockedTitles) {
    playerService.addLog(playerId, `获得称号: ${t.name}`);
    newLogs.push({ id: `title_${t.title_key || t.name}`, type: 'reward', message: `获得称号: ${t.name}` });
  }

  // Check ending (deferred: will be checked when exploration advances the chapter)
  const currentChapterData = db.prepare('SELECT * FROM chapters WHERE chapter_key = ?').get(player.current_chapter);
  let ending = null;

  const updatedPlayer = playerService.get(playerId);
  return {
    chapter: currentChapterData ? { chapter_key: currentChapterData.chapter_key, title: currentChapterData.title, summary: currentChapterData.summary, is_ending_chapter: !!currentChapterData.is_ending_chapter } : null,
    player: updatedPlayer,
    ending,
    unlocked_titles: unlockedTitles,
    chapter_consumed: consumesChapter,
    action_chosen: !consumesChapter && (choiceType === 'action' || choiceType === 'repeatable'),
    pending_next_chapter: consumesChapter ? pendingNext : null,
    new_logs: newLogs
  };
}

// ---- 辅助函数 ----

function applyEffects({ effects, storyFlags, permFlags, stats, relationships, sponsors }) {
  if (effects.flags) Object.assign(storyFlags, effects.flags);
  if (effects.permanent_flags) Object.assign(permFlags, effects.permanent_flags);
  if (effects.stats) {
    for (const [k, v] of Object.entries(effects.stats)) {
      stats[k] = (stats[k] || 0) + v;
    }
  }
  if (effects.relationships) Object.assign(relationships, effects.relationships);
  if (effects.sponsors_add) {
    for (const s of effects.sponsors_add) {
      if (!sponsors.includes(s)) sponsors.push(s);
    }
  }
  if (effects.sponsors_clear) {
    sponsors.length = 0;
  }
  if (effects.unlock_locations) {
    for (const loc of effects.unlock_locations) {
      permFlags[`location_${loc}`] = true;
    }
  }
}

function awardStageResources(playerId, effects) {
  chapterService.awardResource(playerId, 'storyFragments', 1);

  if (effects.flags) {
    if (effects.flags.has_sponsor || effects.flags.negotiated_sponsor || effects.flags.allied_constellations) {
      chapterService.awardResource(playerId, 'constellationFavor', 1);
    }
    if (effects.flags.took_throne || effects.flags.stopped_yjh) {
      chapterService.awardResource(playerId, 'kingToken', 1);
    }
    if (effects.flags.demon_candidate || effects.flags.entered_underworld) {
      chapterService.awardResource(playerId, 'abyssMark', 1);
    }
  }
}

function flagsMatch(storyFlags, permFlags, required) {
  for (const [k, v] of Object.entries(required)) {
    const val = storyFlags[k] || permFlags[k];
    if (val !== v) return false;
  }
  return true;
}

function flagsBlocked(storyFlags, permFlags, blocked) {
  for (const [k, v] of Object.entries(blocked)) {
    const val = storyFlags[k] || permFlags[k];
    if (val === v) return false;
  }
  return true;
}

function deriveNextChapterFromSequence(player, choice) {
  const db = getDb();
  const choiceType = choice.choice_type || 'progress';

  // progress / decision / stage_final: 直接使用 choice.next_chapter_key
  if (choiceType === 'progress' || choiceType === 'decision' || choiceType === 'stage_final') {
    return choice.next_chapter_key;
  }

  // action / repeatable: 从 main_chapter 序列推导下一个 chapter
  const currentMain = getCurrentMainChapter(player);
  const mc = db.prepare('SELECT story_chapter_keys_json FROM main_chapters WHERE chapter_key = ?').get(currentMain);
  if (!mc) return null;

  const sequence = JSON.parse(mc.story_chapter_keys_json);
  const idx = sequence.indexOf(player.current_chapter);
  if (idx === -1 || idx >= sequence.length - 1) return null;
  return sequence[idx + 1];
}

function getChoiceTypeLabel(choiceType) {
  const { choiceTypeLabel } = require('../utils/labels');
  return choiceTypeLabel(choiceType);
}

function formatChoice(ch) {
  return {
    choice_key: ch.choice_key,
    text: ch.text,
    warning: ch.warning || null,
    is_irreversible: !!ch.is_irreversible,
    choice_type: ch.choice_type || 'progress',
    decision_group: ch.decision_group || null,
    is_repeatable: !!ch.is_repeatable,
    hide_after_use: !!ch.hide_after_use,
    effects: JSON.parse(ch.effects_json)
  };
}

module.exports = { getCurrentChapter, canChoose, applyChoice };
