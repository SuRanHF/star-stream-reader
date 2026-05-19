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
    let data;
    try {
      data = await res.json();
    } catch (e) {
      throw { status: res.status, error: { code: 'INVALID_JSON', message: '服务器返回了无效数据，请刷新页面后重试' } };
    }
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

  // Worldline
  getWorldlineStatus() {
    return this.request('GET', '/api/worldline/status').then(r => r.data);
  },
  getWorldlineHistory(limit) {
    return this.request('GET', `/api/worldline/history?limit=${limit || 20}`).then(r => r.data);
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

  // Faction Skills
  getFactionSkills(constellationKey) {
    return this.request('GET', `/api/skills/faction-catalog/${constellationKey}`);
  },
  getPlayerFactionSkills(playerId) {
    return this.request('GET', `/api/skills/faction/player/${playerId}`);
  },
  learnFactionSkill(playerId, skillKey) {
    return this.request('POST', '/api/skills/faction/learn', { playerId, skillKey });
  },

  // Heartbeat
  heartbeat(playerId) {
    return this.request('POST', '/api/heartbeat', { playerId });
  },

  // PK
  getPKOpponents(playerId) {
    return this.request('GET', `/api/pk/opponents/${playerId}`);
  },
  challengePlayer(attackerId, defenderId) {
    return this.request('POST', '/api/pk/challenge', { attackerId, defenderId });
  },
  resolveChallenge(challengeId, playerId, accept) {
    return this.request('POST', '/api/pk/challenge/resolve', { challengeId, playerId, accept });
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
  getBroadcastLeaderboard(limit) {
    return this.request('GET', `/api/broadcast/leaderboard?limit=${limit || 50}`);
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
  peerRevive(reviverId, targetId, method) {
    return this.request('POST', '/api/player/peer-revive', { reviverId, targetId, method });
  },
  getDeadPlayers() {
    return this.request('GET', '/api/player/dead-list');
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

  // Avatar Rank
  getAvatarRank(playerId) {
    return this.request('GET', '/api/avatar-rank/' + playerId);
  },
  rankUp(playerId) {
    return this.request('POST', '/api/avatar-rank/' + playerId + '/rank-up');
  },
  getAvatarRankLeaderboard(limit) {
    return this.request('GET', '/api/avatar-rank/leaderboard?limit=' + (limit || 50));
  },
  prestige(playerId) {
    return this.request('POST', '/api/avatar-rank/' + playerId + '/prestige');
  },

  // Chat
  sendChatMessage(playerId, playerName, message, channel) {
    return this.request('POST', '/api/chat/send', { playerId, playerName, message, channel: channel || 'global' });
  },
  getChatMessages(channel, limit, sinceId) {
    var url = '/api/chat/messages/' + (channel || 'global') + '?limit=' + (limit || 50);
    if (sinceId) url += '&since=' + sinceId;
    return this.request('GET', url);
  },
  getActiveChatters(channel, minutes) {
    return this.request('GET', '/api/chat/active/' + (channel || 'global') + '?minutes=' + (minutes || 10));
  },

  // Factions (阵营)
  getFactions() {
    return this.request('GET', '/api/factions');
  },
  getFactionDetail(constellationKey) {
    return this.request('GET', '/api/factions/' + constellationKey);
  },
  getFactionMembers(constellationKey, limit) {
    return this.request('GET', '/api/factions/' + constellationKey + '/members?limit=' + (limit || 50));
  },
  getFactionLeaderboard() {
    return this.request('GET', '/api/factions/leaderboard');
  },
  getMyFaction(playerId) {
    return this.request('GET', '/api/factions/my/' + playerId);
  },
  getWeeklyWar() {
    return this.request('GET', '/api/factions/war/current');
  },

  // Trade (交易市场)
  getActiveListings(itemType) {
    var url = '/api/trade/listings';
    if (itemType) url += '?type=' + itemType;
    return this.request('GET', url);
  },
  getMyListings(playerId) {
    return this.request('GET', '/api/trade/listings/my/' + playerId);
  },
  createListing(sellerId, itemKey, itemType, quantity, price) {
    return this.request('POST', '/api/trade/listings', { sellerId, itemKey, itemType, quantity, price });
  },
  buyListing(listingId, buyerId) {
    return this.request('POST', '/api/trade/listings/' + listingId + '/buy', { buyerId });
  },
  cancelListing(listingId, playerId) {
    return this.request('POST', '/api/trade/listings/' + listingId + '/cancel', { playerId });
  },

  // Party (组队)
  getActiveParties() {
    return this.request('GET', '/api/party');
  },
  getMyParty(playerId) {
    return this.request('GET', '/api/party/my/' + playerId);
  },
  createParty(leaderId, bossKey) {
    return this.request('POST', '/api/party/create', { leaderId, bossKey: bossKey || null });
  },
  joinParty(partyId, playerId) {
    return this.request('POST', '/api/party/' + partyId + '/join', { playerId });
  },
  leaveParty(partyId, playerId) {
    return this.request('POST', '/api/party/' + partyId + '/leave', { playerId });
  },
  setReady(partyId, playerId, ready) {
    return this.request('POST', '/api/party/' + partyId + '/ready', { playerId, ready: ready });
  },
  startPartyBossBattle(partyId, playerId) {
    return this.request('POST', '/api/party/' + partyId + '/start-battle', { playerId });
  },

  // Narrative (碎片化叙事)
  getItemMemories(itemKey) {
    return this.request('GET', '/api/narrative/item-memories/' + (itemKey || 'all'));
  },
  getLocationEchoes(locationKey) {
    return this.request('GET', '/api/narrative/location-echoes/' + locationKey);
  },
  checkGhostEncounter(playerId, locationKey) {
    return this.request('GET', '/api/narrative/ghost-check/' + playerId + '/' + locationKey);
  },
  processGhostEncounter(playerId, ghostKey, nodeIndex, choiceIndex) {
    return this.request('POST', '/api/narrative/ghost-encounter', { playerId, ghostKey, nodeIndex, choiceIndex });
  },

  // Friends
  getFriendList(playerId) {
    return this.request('GET', '/api/friends/list/' + playerId);
  },
  getFriendRequests(playerId) {
    return this.request('GET', '/api/friends/requests/' + playerId);
  },
  sendFriendRequest(playerId, friendId) {
    return this.request('POST', '/api/friends/request', { playerId, friendId });
  },
  acceptFriendRequest(playerId, requestId) {
    return this.request('POST', '/api/friends/accept', { playerId, requestId });
  },
  declineFriendRequest(playerId, requestId) {
    return this.request('POST', '/api/friends/decline', { playerId, requestId });
  },
  rejectFriendRequest(playerId, requestId) {
    return this.request('POST', '/api/friends/decline', { playerId, requestId });
  },
  removeFriend(playerId, friendId) {
    return this.request('POST', '/api/friends/remove', { playerId, friendId });
  },
  // Gift
  sendGift(playerId, targetId, itemKey) {
    return this.request('POST', '/api/friends/gift', { playerId, targetId, itemKey });
  },
  // Recent interactions
  getRecentInteractions(playerId) {
    return this.request('GET', '/api/friends/recent/' + playerId);
  },

  // Quests (日常/周常任务)
  getQuests(playerId) {
    return this.request('GET', '/api/quests/all/' + playerId);
  },
  claimQuestReward(playerId, questId) {
    return this.request('POST', '/api/quests/claim/' + playerId, { questId });
  },

  // Faction Skills (阵营技能)
  getFactionSkills(constellationKey) {
    return this.request('GET', '/api/skills/faction/' + constellationKey);
  },
  getMyFactionSkills(playerId) {
    return this.request('GET', '/api/skills/faction/player/' + playerId);
  },
  learnFactionSkill(playerId, skillKey) {
    return this.request('POST', '/api/skills/faction/learn', { playerId, skillKey });
  },

  // Equipment Sets (装备套装)
  getEquipmentSets() {
    return this.request('GET', '/api/equipment/sets');
  },
  getMySetBonuses(playerId) {
    return this.request('GET', '/api/equipment/sets/my/' + playerId);
  },
};
