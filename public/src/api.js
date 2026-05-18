// API module - fetch wrappers
const API = {
  async request(method, url, body) {
    // Detect token overwritten by another tab
    if (!Storage.checkSession()) {
      GameClient.handleTokenConflict();
      throw { status: 0, error: { code: 'TOKEN_CONFLICT', message: '检测到账号切换，请重新登录' } };
    }
    const opts = {
      method,
      headers: { 'Content-Type': 'application/json' }
    };
    const token = Storage.getToken();
    if (token) {
      opts.headers['Authorization'] = 'Bearer ' + token;
    }
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(url, opts);
    const data = await res.json();
    if (!res.ok) {
      if (res.status === 401) {
        GameClient.handleAuthExpired();
      }
      throw { status: res.status, ...data };
    }
    return data;
  },

  // Auth
  register(username, email, password) {
    return this.request('POST', '/api/auth/register', { username, email, password });
  },
  login(usernameOrEmail, password) {
    return this.request('POST', '/api/auth/login', { usernameOrEmail, password });
  },
  me() {
    return this.request('GET', '/api/auth/me');
  },
  logout() {
    return this.request('POST', '/api/auth/logout');
  },
  createPlayerBound(playerName) {
    return this.request('POST', '/api/auth/create-player', { playerName });
  },

  // Player
  createPlayer(playerName) {
    return this.request('POST', '/api/player/create', { playerName }).then(r => r.data);
  },
  getPlayer(playerId) {
    return this.request('GET', `/api/player/${playerId}`).then(r => r.data);
  },
  resetPlayer(playerId) {
    return this.request('POST', `/api/player/reset/${playerId}`);
  },

  // Story
  getCurrentStory(playerId) {
    return this.request('GET', `/api/story/current/${playerId}`);
  },
  makeChoice(playerId, choiceKey) {
    return this.request('POST', '/api/story/choose', { playerId, choiceKey });
  },

  // Titles
  getTitles(playerId) {
    return this.request('GET', `/api/titles/${playerId}`);
  },

  // Endings
  getEndings(playerId) {
    return this.request('GET', `/api/endings/${playerId}`);
  },

  // Explore
  getLocations(playerId) {
    return this.request('GET', `/api/explore/locations/${playerId}`);
  },
  startExploration(playerId, locationKey, firstExplore) {
    return this.request('POST', '/api/explore/start', { playerId, locationKey, firstExplore: !!firstExplore });
  },

  // Inventory
  getInventory(playerId) {
    return this.request('GET', `/api/inventory/${playerId}`);
  },
  useItem(playerId, itemKey) {
    return this.request('POST', '/api/inventory/use', { playerId, itemKey });
  },

  // Equipment
  getEquipment(playerId) {
    return this.request('GET', `/api/equipment/${playerId}`);
  },
  equipItem(playerId, equipmentKey, slot) {
    return this.request('POST', '/api/equipment/equip', { playerId, equipmentKey, slot });
  },
  unequipItem(playerId, slot) {
    return this.request('POST', '/api/equipment/unequip', { playerId, slot });
  },

  // Skills
  getSkills(playerId) {
    return this.request('GET', `/api/skills/${playerId}`);
  },
  unlockSkill(playerId, skillKey) {
    return this.request('POST', '/api/skills/unlock', { playerId, skillKey });
  },

  // PK
  getPKOpponents(playerId) {
    return this.request('GET', `/api/pk/opponents/${playerId}`);
  },
  challengePlayer(attackerId, defenderId) {
    return this.request('POST', '/api/pk/challenge', { attackerId, defenderId });
  },
  getPKRankings() {
    return this.request('GET', '/api/pk/rankings');
  },
  getPKRecords(playerId) {
    return this.request('GET', `/api/pk/records/${playerId}`);
  },

  // Rankings
  getRankings(limit) {
    return this.request('GET', `/api/rankings?limit=${limit || 50}`);
  },
  getMyRank(playerId) {
    return this.request('GET', `/api/rankings/${playerId}`);
  },

  // Stage System (主线阶段)
  getStageStatus(playerId) {
    return this.request('GET', `/api/chapters/status/${playerId}`);
  },
  doStageAdvance(playerId, chapterKey) {
    return this.request('POST', '/api/chapters/advance', { playerId, chapterKey });
  },
  getStageResources(playerId) {
    return this.request('GET', `/api/chapters/resources/${playerId}`);
  },
  // 向后兼容
  getChapterStatus(playerId) { return this.getStageStatus(playerId); },
  doBreakthrough(playerId, chapterKey) { return this.doStageAdvance(playerId, chapterKey); },
  getBreakthroughResources(playerId) { return this.getStageResources(playerId); },

  // Broadcast (星流放送)
  getActiveBroadcast() {
    return this.request('GET', '/api/broadcast/active');
  },
  joinBroadcast(eventId, playerId) {
    return this.request('POST', `/api/broadcast/${eventId}/join`, { playerId });
  },
  getBroadcastProgress(eventId) {
    return this.request('GET', `/api/broadcast/${eventId}/progress`);
  },
  getMyContribution(eventId, playerId) {
    return this.request('GET', `/api/broadcast/${eventId}/my-contribution/${playerId}`);
  },
  claimBroadcastReward(eventId, playerId) {
    return this.request('POST', `/api/broadcast/${eventId}/claim`, { playerId });
  },
  getBroadcastRanking(eventId, limit) {
    return this.request('GET', `/api/broadcast/${eventId}/ranking?limit=${limit || 20}`);
  },
  getBroadcastHistory() {
    return this.request('GET', '/api/broadcast/history');
  },
  // Combat
  resolveCombat(playerId, monsterKey, action) {
    return this.request('POST', '/api/combat/resolve', { playerId, monsterKey, action });
  },

  // Attribute allocation
  allocatePoints(playerId, atk, def, spd, crit) {
    return this.request('POST', '/api/player/allocate-points', { playerId, atk, def, spd, crit });
  },

  resetAllocation(playerId) {
    return this.request('POST', '/api/player/reset-allocation', { playerId });
  },

  // Constellation (背后星)
  getConstellations() {
    return this.request('GET', '/api/player/constellations');
  },
  selectConstellation(playerId, constellationKey) {
    return this.request('POST', '/api/player/select-constellation', { playerId, constellationKey });
  },

  // Revival
  revivePlayer(playerId, method) {
    return this.request('POST', '/api/player/revive', { playerId, method });
  },

  // Rest / Recovery
  startRest(playerId) {
    return this.request('POST', '/api/player/rest/start', { playerId });
  },
  stopRest(playerId) {
    return this.request('POST', '/api/player/rest/stop', { playerId });
  },

  // Feedback
  submitFeedback(data) {
    return this.request('POST', '/api/feedback', data);
  },
};
