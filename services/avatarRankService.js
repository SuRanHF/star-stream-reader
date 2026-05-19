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
    constellationFavor: resources.constellationFavor || 0,
    abyssMark: resources.abyssMark || 0
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
      current = Math.floor((resources.storyFragments || 0) / 10);
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
      description: nextRank.description,
      resourceCost: nextRank.resourceCost || null,
      breakthroughRate: nextRank.breakthroughRate !== undefined ? nextRank.breakthroughRate : null
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

  // Resource cost check
  var resourceCost = nextRank.resourceCost;
  if (resourceCost) {
    var raw = playerService.getRaw(playerId);
    var res = JSON.parse(raw.breakthrough_resources_json || '{}');
    var dbUpdates = {};
    for (var rk in resourceCost) {
      if (resourceCost.hasOwnProperty(rk)) {
        var have;
        if (rk === 'story_fragments') {
          have = raw.story_fragments || 0;
        } else {
          have = res[rk] || 0;
        }
        if (have < resourceCost[rk]) {
          return {
            success: false,
            error: {
              code: 'INSUFFICIENT_RESOURCES',
              message: '突破资源不足，需要 ' + rk + ' x' + resourceCost[rk] + '，当前: ' + have
            }
          };
        }
      }
    }
    // Consume resources
    for (var cr in resourceCost) {
      if (resourceCost.hasOwnProperty(cr)) {
        if (cr === 'story_fragments') {
          dbUpdates.story_fragments = (raw.story_fragments || 0) - resourceCost[cr];
        } else {
          res[cr] = (res[cr] || 0) - resourceCost[cr];
        }
      }
    }
    dbUpdates.breakthrough_resources_json = res;
    playerService.update(playerId, dbUpdates);
  }

  // Breakthrough probability roll (S+ ranks)
  var breakthroughRate = nextRank.breakthroughRate;
  var breakthroughSuccess = true;
  if (breakthroughRate !== undefined && breakthroughRate < 1.0) {
    var roll = Math.random();
    breakthroughSuccess = roll < breakthroughRate;
  }

  if (!breakthroughSuccess) {
    var failLog = '突破失败！你在晋升' + nextRank.displayName + '的过程中遭遇了世界线的抵抗。请再次尝试。';
    playerService.addLog(playerId, failLog);
    return {
      success: false,
      data: {
        rankedUp: false,
        breakthroughFailed: true,
        breakthroughRate: breakthroughRate,
        displayName: nextRank.displayName
      },
      error: {
        code: 'BREAKTHROUGH_FAILED',
        message: failLog
      }
    };
  }

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
  if (rewards.channelHeat) newStats.channelHeat = (newStats.channelHeat || 0) + rewards.channelHeat;

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
      breakthroughRate: breakthroughRate,
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
        var raw = playerService.getRaw(playerId);
        var br = JSON.parse(raw.breakthrough_resources_json || '{}');
        var neededFrags = req.required * 10;
        var currentFrags = player.story_fragments || 0;
        if (currentFrags < neededFrags) {
          updateFields.story_fragments = neededFrags;
        }
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

// Phase 6: 位阶限制表
var RANK_ACCESS = {
  F: { locations: 1, trade: false, party: false, skillSlots: 0 },
  E: { locations: 2, trade: false, party: false, skillSlots: 0 },
  D: { locations: 3, trade: false, party: false, skillSlots: 1 },
  C: { locations: 5, trade: true, party: false, skillSlots: 2 },
  B: { locations: 8, trade: true, party: true, skillSlots: 3 },
  A: { locations: 99, trade: true, party: true, skillSlots: 4 },
  S: { locations: 99, trade: true, party: true, skillSlots: 5 },
  SS: { locations: 99, trade: true, party: true, skillSlots: 5 },
  SSS: { locations: 99, trade: true, party: true, skillSlots: 5 }
};

function getRankAccess(player) {
  var rank = (player.stats && player.stats.avatarRank) || 'F';
  return RANK_ACCESS[rank] || RANK_ACCESS.F;
}

function checkAccess(player, feature) {
  var access = getRankAccess(player);
  switch (feature) {
    case 'location_count':
      return access.locations;
    case 'trade':
      return access.trade;
    case 'party':
      return access.party;
    case 'skill_slots':
      return access.skillSlots;
    default:
      return true;
  }
}

function prestige(playerId) {
  var player = playerService.get(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };
  var stats = player.stats;
  if (stats.avatarRank !== 'SSS') return { error: { code: 'NOT_SSS', message: '只有达到SSS位阶才能进行回归' } };

  var raw = playerService.getRaw(playerId);
  var prestigeLevel = (raw.prestige_level || 0) + 1;
  var bonus = JSON.parse(raw.prestige_bonus_json || '{}');

  // Permanent bonus: +2% all stats per prestige
  bonus.atkBonus = (bonus.atkBonus || 0) + 0.02;
  bonus.defBonus = (bonus.defBonus || 0) + 0.02;
  bonus.spdBonus = (bonus.spdBonus || 0) + 0.02;
  bonus.hpBonus = (bonus.hpBonus || 0) + 0.02;

  // Reset rank to F but keep equipment, inventory, coins, story_fragments
  var newStats = Object.assign({}, playerService.defaultStats);
  newStats.avatarRank = 'F';
  newStats.avatarRankName = '临时化身';
  newStats.storyGrade = 'ordinary';
  newStats.prestigeLevel = prestigeLevel;
  // Preserve constellation choice
  newStats.constellation = stats.constellation;

  playerService.update(playerId, {
    stats_json: newStats,
    prestige_level: prestigeLevel,
    prestige_bonus_json: bonus
  });

  playerService.addLog(playerId, '进行了第' + prestigeLevel + '次回归！位阶重置为F，获得永久加成。');

  return {
    success: true,
    data: {
      prestigeLevel: prestigeLevel,
      bonus: bonus,
      message: '回归完成！你已经历' + prestigeLevel + '次回归，所有属性永久+' + (prestigeLevel * 2) + '%'
    }
  };
}

module.exports = {
  getAvatarRankConfig: getAvatarRankConfig,
  getStarstreamTier: getStarstreamTier,
  getStoryGradeLabel: getStoryGradeLabel,
  normalizePlayerRankFields: normalizePlayerRankFields,
  getPlayerAvatarRank: getPlayerAvatarRank,
  calculateRankProgress: calculateRankProgress,
  rankUp: rankUp,
  prestige: prestige,
  getRankAccess: getRankAccess,
  checkAccess: checkAccess,
  getAvatarRankLeaderboard: getAvatarRankLeaderboard,
  adminFillRankRequirements: adminFillRankRequirements
};
