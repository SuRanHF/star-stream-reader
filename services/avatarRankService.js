// 化身位阶服务 — 升阶逻辑、进度计算、星流段位
const { getDb } = require('../db/database');
const playerService = require('./playerService');
const {
  AVATAR_RANKS, STARSTREAM_TIERS, STORY_GRADES,
  getAvatarRankConfig, getStarstreamTier, getStoryGradeLabel
} = require('../data/seedAvatarRanks');

function normalizePlayerRankFields(player) {
  if (!player.stats) return player;
  var s = player.stats;
  if (!s.avatarRank) s.avatarRank = 'F';
  if (!s.avatarRankName) s.avatarRankName = '临时化身';
  if (!s.storyGrade) s.storyGrade = 'ordinary';
  return player;
}

function getPlayerRankConfig(player) {
  var rk = player.stats.avatarRank || 'F';
  return AVATAR_RANKS.find(function(r) { return r.rankKey === rk; }) || AVATAR_RANKS[0];
}

function getNextRankConfig(currentRank) {
  if (!currentRank || !currentRank.nextRankKey) return null;
  return AVATAR_RANKS.find(function(r) { return r.rankKey === currentRank.nextRankKey; }) || null;
}

function getResources(playerId) {
  var player = playerService.getRaw(playerId);
  if (!player) return {};
  var resources = JSON.parse(player.breakthrough_resources_json || '{}');
  return {
    storyFragments: player.story_fragments || 0,
    scenarioProof: resources.scenarioProof || 0,
    constellationFavor: resources.constellationFavor || 0,
    kingToken: resources.kingToken || 0,
    abyssMark: resources.abyssMark || 0,
    finalPage: resources.finalPage || 0
  };
}

function getBroadcastContribution(playerId) {
  var db = getDb();
  var row = db.prepare(
    'SELECT COALESCE(SUM(amount), 0) as total FROM broadcast_contributions WHERE player_id = ?'
  ).get(playerId);
  return row ? row.total : 0;
}

function hasRareTitle(titlesJson) {
  var titles = typeof titlesJson === 'string' ? JSON.parse(titlesJson) : (titlesJson || []);
  return titles.some(function(t) { return t.rarity && t.rarity !== 'common'; });
}

function evaluateRequirement(req, player, playerId) {
  var stats = player.stats || {};
  var current = 0;
  var required = 0;

  switch (req.type) {
    case 'level_min':
      current = stats.level || 1;
      required = req.value;
      break;
    case 'story_fragments_min':
      current = player.story_fragments || 0;
      required = req.value;
      break;
    case 'explorations_by_location': {
      var sp = player.stage_progress || {};
      current = (sp.explorationsByLocation || {})[req.locationKey] || 0;
      required = req.count;
      break;
    }
    case 'stage_completed': {
      var completed = player.completed_chapters || [];
      current = completed.some(function(c) { return c === req.stageKey || c.startsWith(req.stageKey); }) ? 1 : 0;
      required = 1;
      break;
    }
    case 'scenario_proof_min': {
      var resources = getResources(playerId);
      current = resources.scenarioProof || 0;
      required = req.value;
      break;
    }
    case 'titles_count_min': {
      var titles = player.titles || [];
      current = titles.length;
      required = req.value;
      break;
    }
    case 'channel_heat_min':
      current = stats.channelHeat || 0;
      required = req.value;
      break;
    case 'pk_or_broadcast': {
      var pkOk = (stats.rating || 1000) >= (req.pkRating || 1000);
      var bcOk = getBroadcastContribution(playerId) >= (req.broadcastContribution || 100);
      current = (pkOk || bcOk) ? 1 : 0;
      required = 1;
      break;
    }
    case 'rare_title_required':
      current = hasRareTitle(player.titles) ? 1 : 0;
      required = 1;
      break;
    case 'story_grade_min': {
      var grade = STORY_GRADES.find(function(g) { return g.key === (stats.storyGrade || 'ordinary'); });
      var reqGrade = STORY_GRADES.find(function(g) { return g.key === req.value; });
      current = (grade && reqGrade && grade.order >= reqGrade.order) ? 1 : 0;
      required = 1;
      break;
    }
    default:
      return { type: req.type, label: req.label, current: 0, required: 1, completed: false };
  }

  return {
    type: req.type, label: req.label,
    current: current, required: required,
    completed: current >= required
  };
}

function calculateRankProgress(playerId) {
  var player = playerService.get(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  normalizePlayerRankFields(player);
  var currentRank = getPlayerRankConfig(player);
  var nextRank = getNextRankConfig(currentRank);

  if (!nextRank) {
    return {
      currentRank: formatRankDisplay(currentRank),
      storyGrade: formatStoryGrade(player.stats.storyGrade),
      starstreamTier: getStarstreamTier(player.stats.channelHeat || 0),
      nextRank: null, canRankUp: false, isMaxRank: true,
      requirements: [], rewards: null
    };
  }

  var requirements = nextRank.requirements.map(function(req) {
    return evaluateRequirement(req, player, playerId);
  });
  var canRankUp = requirements.every(function(r) { return r.completed; });

  return {
    currentRank: formatRankDisplay(currentRank),
    storyGrade: formatStoryGrade(player.stats.storyGrade),
    starstreamTier: getStarstreamTier(player.stats.channelHeat || 0),
    nextRank: {
      rankKey: nextRank.rankKey,
      displayName: nextRank.displayName,
      description: nextRank.description
    },
    canRankUp: canRankUp, isMaxRank: false,
    requirements: requirements, rewards: nextRank.rewards
  };
}

function getPlayerAvatarRank(playerId) {
  return calculateRankProgress(playerId);
}

function rankUp(playerId) {
  var progress = calculateRankProgress(playerId);
  if (progress.error) return progress;
  if (progress.isMaxRank) {
    return { success: false, error: { code: 'MAX_RANK', message: '已达到最高位阶' } };
  }
  if (!progress.canRankUp) {
    var missing = progress.requirements.filter(function(r) { return !r.completed; });
    return {
      success: false,
      error: {
        code: 'RANK_REQUIREMENTS_NOT_MET',
        message: '升阶条件未满足',
        missingRequirements: missing
      }
    };
  }

  var player = playerService.get(playerId);
  normalizePlayerRankFields(player);
  var currentRank = getPlayerRankConfig(player);
  var nextRank = getNextRankConfig(currentRank);
  var rewards = nextRank.rewards;
  var newStats = Object.assign({}, player.stats);

  if (rewards.stats) {
    var keys = Object.keys(rewards.stats);
    for (var i = 0; i < keys.length; i++) {
      var key = keys[i];
      newStats[key] = (newStats[key] || 0) + rewards.stats[key];
      if (key === 'maxHp') newStats.hp = (newStats.hp || 100) + rewards.stats[key];
    }
  }

  if (rewards.storyGrade) newStats.storyGrade = rewards.storyGrade;

  newStats.avatarRank = nextRank.rankKey;
  newStats.avatarRankName = nextRank.rankName;

  playerService.update(playerId, { stats_json: newStats });

  var logMsg = rewards.log || ('晋升为' + nextRank.displayName);
  playerService.addLog(playerId, logMsg);

  return {
    success: true,
    data: {
      rankedUp: true,
      from: currentRank.rankKey,
      to: nextRank.rankKey,
      displayName: nextRank.displayName,
      rewards: rewards.stats || {},
      storyGrade: newStats.storyGrade,
      log: logMsg
    }
  };
}

function getAvatarRankLeaderboard(limit) {
  limit = limit || 50;
  var db = getDb();
  var rows = db.prepare(
    'SELECT id, player_name, stats_json, titles_json FROM players ORDER BY id'
  ).all();

  var enriched = rows.map(function(r) {
    var stats = typeof r.stats_json === 'string' ? JSON.parse(r.stats_json) : (r.stats_json || {});
    var titles = typeof r.titles_json === 'string' ? JSON.parse(r.titles_json) : (r.titles_json || []);
    var rankCfg = AVATAR_RANKS.find(function(c) { return c.rankKey === (stats.avatarRank || 'F'); }) || AVATAR_RANKS[0];
    var tier = getStarstreamTier(stats.channelHeat || 0);
    var gradeLabel = getStoryGradeLabel(stats.storyGrade || 'ordinary');

    return {
      player_id: r.id, player_name: r.player_name,
      avatarRank: stats.avatarRank || 'F',
      avatarRankName: stats.avatarRankName || '临时化身',
      rankOrder: rankCfg.order,
      level: stats.level || 1,
      storyGrade: stats.storyGrade || 'ordinary',
      storyGradeLabel: gradeLabel,
      starstreamTierKey: tier.key,
      starstreamTierLabel: tier.label,
      channelHeat: stats.channelHeat || 0,
      pkRating: stats.rating || 1000,
      titles: titles.map(function(t) { return t.name || t.title_key; }).slice(0, 3)
    };
  });

  enriched.sort(function(a, b) {
    if (b.rankOrder !== a.rankOrder) return b.rankOrder - a.rankOrder;
    if (b.level !== a.level) return b.level - a.level;
    if (b.channelHeat !== a.channelHeat) return b.channelHeat - a.channelHeat;
    return b.pkRating - a.pkRating;
  });

  return enriched.slice(0, limit).map(function(item, i) {
    item.rank = i + 1;
    return item;
  });
}

function formatRankDisplay(rankCfg) {
  return {
    rankKey: rankCfg.rankKey,
    displayName: rankCfg.displayName,
    description: rankCfg.description,
    order: rankCfg.order
  };
}

function formatStoryGrade(gradeKey) {
  var g = STORY_GRADES.find(function(x) { return x.key === gradeKey; });
  return {
    key: gradeKey || 'ordinary',
    label: g ? g.label : '普通故事',
    order: g ? g.order : 1
  };
}

function adminFillRankRequirements(playerId) {
  var progress = calculateRankProgress(playerId);
  if (progress.error) return progress;
  if (progress.isMaxRank) return { success: false, error: { code: 'MAX_RANK', message: '已是最高位阶' } };

  var player = playerService.get(playerId);
  normalizePlayerRankFields(player);
  var stats = Object.assign({}, player.stats);
  var updateFields = {};
  var reqs = progress.requirements;

  for (var i = 0; i < reqs.length; i++) {
    var req = reqs[i];
    if (req.completed) continue;
    switch (req.type) {
      case 'level_min':
        stats.level = Math.max(stats.level || 1, req.required);
        break;
      case 'story_fragments_min':
        updateFields.story_fragments = Math.max(player.story_fragments || 0, req.required);
        break;
      case 'channel_heat_min':
        stats.channelHeat = Math.max(stats.channelHeat || 0, req.required);
        break;
      case 'explorations_by_location': {
        var sp = player.stage_progress || {};
        sp.explorationsByLocation = sp.explorationsByLocation || {};
        sp.explorationsByLocation[req.locationKey] = Math.max(
          sp.explorationsByLocation[req.locationKey] || 0, req.required
        );
        updateFields.stage_progress_json = sp;
        break;
      }
      case 'stage_completed': {
        var completed = player.completed_chapters || [];
        if (completed.indexOf(req.stageKey) < 0) {
          completed.push(req.stageKey);
          updateFields.completed_chapters_json = completed;
        }
        break;
      }
      case 'scenario_proof_min': {
        var resources = player.breakthrough_resources || {};
        resources.scenarioProof = Math.max(resources.scenarioProof || 0, req.required);
        updateFields.breakthrough_resources_json = resources;
        break;
      }
      case 'pk_or_broadcast':
        stats.rating = Math.max(stats.rating || 1000, req.pkRating || 1000);
        break;
    }
  }

  updateFields.stats_json = stats;
  playerService.update(playerId, updateFields);
  return { success: true, data: { message: '位阶条件已一键满足，请执行升阶。' } };
}

module.exports = {
  getAvatarRankConfig: getAvatarRankConfig,
  getStarstreamTier: getStarstreamTier,
  getStoryGradeLabel: getStoryGradeLabel,
  normalizePlayerRankFields: normalizePlayerRankFields,
  getPlayerAvatarRank: getPlayerAvatarRank,
  calculateRankProgress: calculateRankProgress,
  rankUp: rankUp,
  getAvatarRankLeaderboard: getAvatarRankLeaderboard,
  adminFillRankRequirements: adminFillRankRequirements
};
