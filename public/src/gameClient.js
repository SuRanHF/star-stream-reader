// ⚠️ UI 保护文件 — 非经确认不得擅自修改
// ⚠️ 禁止：修改 openFeature/handleNavigation/openDrawer/closeDrawer/setupModals
// ⚠️ 禁止：修改导航切换流程、弹窗打开关闭行为
// ⚠️ 允许：新增功能逻辑、API 调用、事件处理

// GameClient module - state machine (dark immersive RPG layout)
const GameClient = {
  state: 'INIT',
  playerId: null,
  _currentUser: null,
  _exploreMultiplier: 1,
  _autoExploreActive: false,
  _autoExploreTimer: null,
  _lastExploreCost: 5,

  async init() {
    this.state = 'LOADING';

    // Version check — block if client outdated
    const versionOk = await this.checkVersion();
    if (!versionOk) return;

    this.setupNavigation();
    this.setupModals();
    this.initMobileUI();
    this.checkChangelog();

    const token = Storage.getToken();

    if (token) {
      try {
        const result = await API.me();
        if (result.success && result.data) {
          this._currentUser = result.data.user;
          if (result.data.player) {
            // User has a bound player — load the game
            this.playerId = result.data.player.id;
            Storage.setPlayerId(this.playerId);
            await this.loadGame(result.data.player);
            return;
          } else {
            // Logged in but no player — show create player for this user
            await this.showPostLoginCreatePlayer();
            return;
          }
        }
      } catch (e) {
        console.error('Auto-login failed:', e);
        Storage.clearToken();
      }
    }

    // No valid token — show auth screen
    await this.showAuthScreen();
  },

  // ===== Auth =====
  async showAuthScreen() {
    this.state = 'AUTH';

    // Show auth page, hide game
    document.getElementById('authPage')?.classList.remove('hidden');
    document.getElementById('gameWrapper')?.classList.add('hidden');

    // Reset forms
    document.getElementById('loginForm')?.classList.remove('hidden');
    document.getElementById('registerForm')?.classList.add('hidden');
    document.getElementById('createPlayerBlock')?.classList.add('hidden');
    document.getElementById('authSwitchRow')?.classList.remove('hidden');
    document.getElementById('authLogoutRow')?.classList.add('hidden');
    document.getElementById('loginError')?.classList.add('hidden');
    document.getElementById('registerError')?.classList.add('hidden');
    document.getElementById('loginUsername').value = '';
    document.getElementById('loginPassword').value = '';

    // Generate stars
    this._generateAuthStars();

    // Bind events (only once)
    if (!this._authBound) {
      this._authBound = true;
      this._bindAuthEvents();
    }
  },

  _bindAuthEvents() {
    document.getElementById('loginSubmitBtn').onclick = () => {
      const username = document.getElementById('loginUsername').value.trim();
      const password = document.getElementById('loginPassword').value;
      this.handleLogin(username, password);
    };
    document.getElementById('loginPassword').onkeydown = (e) => {
      if (e.key === 'Enter') {
        const username = document.getElementById('loginUsername').value.trim();
        const password = document.getElementById('loginPassword').value;
        this.handleLogin(username, password);
      }
    };

    document.getElementById('registerSubmitBtn').onclick = () => {
      const username = document.getElementById('regUsername').value.trim();
      const email = document.getElementById('regEmail').value.trim();
      const password = document.getElementById('regPassword').value;
      this.handleRegister(username, email, password);
    };
    document.getElementById('regPassword').onkeydown = (e) => {
      if (e.key === 'Enter') {
        const username = document.getElementById('regUsername').value.trim();
        const email = document.getElementById('regEmail').value.trim();
        const password = document.getElementById('regPassword').value;
        this.handleRegister(username, email, password);
      }
    };

    document.getElementById('authSwitchBtn').onclick = () => {
      const loginForm = document.getElementById('loginForm');
      const regForm = document.getElementById('registerForm');
      const switchText = document.getElementById('authSwitchText');
      const switchBtn = document.getElementById('authSwitchBtn');
      if (!loginForm.classList.contains('hidden')) {
        loginForm.classList.add('hidden');
        regForm.classList.remove('hidden');
        switchText.textContent = '已有账号？';
        switchBtn.textContent = '立即登录';
        document.getElementById('loginError')?.classList.add('hidden');
      } else {
        regForm.classList.add('hidden');
        loginForm.classList.remove('hidden');
        switchText.textContent = '还没有账号？';
        switchBtn.textContent = '立即注册';
        document.getElementById('registerError')?.classList.add('hidden');
      }
    };

    document.getElementById('createPlayerBtn').onclick = () => {
      const name = document.getElementById('createNameInput').value.trim();
      this.createPlayerBound(name);
    };
    document.getElementById('createNameInput').onkeydown = (e) => {
      if (e.key === 'Enter') {
        const name = document.getElementById('createNameInput').value.trim();
        this.createPlayerBound(name);
      }
    };

    document.getElementById('logoutFromAuthBtn').onclick = () => {
      this.doLogout();
    };
  },

  _generateAuthStars() {
    const container = document.getElementById('authStars');
    if (!container || container.children.length > 0) return;
    for (let i = 0; i < 40; i++) {
      const star = document.createElement('span');
      star.className = 'auth-star';
      star.style.left = Math.random() * 100 + '%';
      star.style.top = Math.random() * 100 + '%';
      const size = Math.random() * 2.5 + 1;
      star.style.width = size + 'px';
      star.style.height = size + 'px';
      star.style.animationDelay = Math.random() * 3 + 's';
      star.style.animationDuration = (Math.random() * 3 + 2) + 's';
      container.appendChild(star);
    }
  },

  async handleLogin(usernameOrEmail, password) {
    const errorEl = document.getElementById('loginError');
    const btn = document.getElementById('loginSubmitBtn');
    if (!usernameOrEmail || !password) {
      if (errorEl) { errorEl.textContent = '请填写用户名/邮箱和密码'; errorEl.classList.remove('hidden'); }
      return;
    }
    try {
      if (btn) { btn.disabled = true; btn.textContent = '登录中...'; }
      const result = await API.login(usernameOrEmail, password);
      if (result.success) {
        Storage.setSession(result.data.token, result.data.user.username);
        this._currentUser = result.data.user;
        // Check if user has a player
        const meResult = await API.me();
        if (meResult.success && meResult.data && meResult.data.player) {
          this.playerId = meResult.data.player.id;
          Storage.setPlayerId(this.playerId);
          await this.loadGame(meResult.data.player);
        } else {
          await this.showPostLoginCreatePlayer();
        }
      } else {
        if (errorEl) { errorEl.textContent = (result.error && result.error.message) || '登录失败'; errorEl.classList.remove('hidden'); }
      }
    } catch (e) {
      if (errorEl) { errorEl.textContent = (e.error && e.error.message) || '网络错误，请重试'; errorEl.classList.remove('hidden'); }
    } finally {
      if (btn) { btn.disabled = false; btn.textContent = '登 录'; }
    }
  },

  async handleRegister(username, email, password) {
    const errorEl = document.getElementById('registerError');
    const btn = document.getElementById('registerSubmitBtn');
    if (!username || !email || !password) {
      if (errorEl) { errorEl.textContent = '请填写所有字段'; errorEl.classList.remove('hidden'); }
      return;
    }
    if (password.length < 6) {
      if (errorEl) { errorEl.textContent = '密码长度不能少于6位'; errorEl.classList.remove('hidden'); }
      return;
    }
    try {
      if (btn) { btn.disabled = true; btn.textContent = '注册中...'; }
      const result = await API.register(username, email, password);
      if (result.success) {
        Storage.setSession(result.data.token, result.data.user.username);
        this._currentUser = result.data.user;
        // New user — show create player
        await this.showPostLoginCreatePlayer();
      } else {
        if (errorEl) { errorEl.textContent = (result.error && result.error.message) || '注册失败'; errorEl.classList.remove('hidden'); }
      }
    } catch (e) {
      if (errorEl) { errorEl.textContent = (e.error && e.error.message) || '网络错误，请重试'; errorEl.classList.remove('hidden'); }
    } finally {
      if (btn) { btn.disabled = false; btn.textContent = '注 册'; }
    }
  },

  async showPostLoginCreatePlayer() {
    this.state = 'CREATE_PLAYER';

    // Stay on auth page, switch to create-player block
    document.getElementById('authPage')?.classList.remove('hidden');
    document.getElementById('gameWrapper')?.classList.add('hidden');

    document.getElementById('loginForm')?.classList.add('hidden');
    document.getElementById('registerForm')?.classList.add('hidden');
    document.getElementById('createPlayerBlock')?.classList.remove('hidden');
    document.getElementById('authSwitchRow')?.classList.add('hidden');
    document.getElementById('authLogoutRow')?.classList.remove('hidden');

    const username = this._currentUser ? this._currentUser.username : '';
    const greeting = document.getElementById('createPlayerGreeting');
    if (greeting) greeting.innerHTML = `欢迎，<strong>${UI.escapeHtml(username)}</strong>。请创建你的角色。`;

    document.getElementById('createNameInput').value = '';
    document.getElementById('createPlayerError')?.classList.add('hidden');
  },

  async createPlayerBound(name) {
    const errorEl = document.getElementById('createPlayerError');
    try {
      const result = await API.createPlayerBound(name || undefined);
      if (result.success && result.data && result.data.player) {
        const player = result.data.player;
        Storage.setPlayerId(player.id);
        this.playerId = player.id;
        await this.loadGame(player);
      } else {
        if (errorEl) { errorEl.textContent = (result.error && result.error.message) || '创建角色失败'; errorEl.classList.remove('hidden'); }
      }
    } catch (e) {
      if (errorEl) { errorEl.textContent = (e.error && e.error.message) || '创建角色失败，请重试'; errorEl.classList.remove('hidden'); }
    }
  },

  handleAuthExpired() {
    Storage.clearToken();
    this._currentUser = null;
    this.playerId = null;
    Storage.clear();
    // Stop any game loops
    this._stopRecoveryLoop();
    this.showAuthScreen();
  },

  handleTokenConflict() {
    Storage.clearToken();
    this._currentUser = null;
    this.playerId = null;
    Storage.clear();
    this._stopRecoveryLoop();
    this.showAuthScreen();
    const el = document.getElementById('loginError');
    if (el) { el.textContent = '检测到另一个标签页切换了账号，请重新登录。'; el.classList.remove('hidden'); }
  },

  doLogout() {
    Storage.clearToken();
    Storage.clear();
    this._currentUser = null;
    this.playerId = null;
    // Reload to clean state
    window.location.reload();
  },

  setupNavigation() {
    document.querySelectorAll('.nav-item').forEach(item => {
      if (item.dataset.bound === 'true') return;
      item.dataset.bound = 'true';
      item.addEventListener('click', async () => {
        const feature = item.dataset.feature;
        if (feature) await this.openFeature(feature);
      });
    });
  },

  // ===== Mobile UI Layout Switcher =====
  _mobileUIMode: 'default',

  initMobileUI() {
    const saved = Storage.get('mobileUIMode') || 'default';
    this.applyMobileUI(saved);
  },

  switchMobileUI(mode) {
    this.applyMobileUI(mode);
    Storage.set('mobileUIMode', mode);
  },

  applyMobileUI(mode) {
    this._mobileUIMode = mode;
    UI._applyMobileUi(mode);
  },

  setupModals() {
    var self = this;
    var overlayIds = [
      'modalOverlay', 'pkModalOverlay', 'warningOverlay',
      'storyPopupOverlay', 'explorePopupOverlay', 'combatPopupOverlay',
      'constellationPopupOverlay', 'challengePopupOverlay', 'mapOverlay',
      'feedbackOverlay', 'changelogOverlay'
    ];
    var dismissMap = {
      modalOverlay: 'modalOverlay',
      pkModalOverlay: 'pkModalOverlay',
      warningOverlay: 'warningOverlay',
      storyPopupOverlay: 'storyPopupOverlay',
      explorePopupOverlay: 'explorePopupOverlay',
      combatPopupOverlay: 'combatPopupOverlay',
      constellationPopupOverlay: 'constellationPopupOverlay',
      challengePopupOverlay: 'challengePopupOverlay',
      mapOverlay: 'mapOverlay',
      feedbackOverlay: 'feedbackOverlay',
      changelogOverlay: 'changelogOverlay'
    };
    overlayIds.forEach(function(id) {
      var el = document.getElementById(id);
      if (!el || el.dataset.bound === 'true') return;
      el.dataset.bound = 'true';
      el.addEventListener('click', function(e) {
        if (e.target !== this) return;
        if (id === 'warningOverlay') {
          var cancelBtn = document.getElementById('warningCancel');
          if (cancelBtn) cancelBtn.click();
          return;
        }
        var closeBtn = this.querySelector('.popup-close');
        if (closeBtn) { closeBtn.click(); return; }
        UI.dismissPopup(id);
      });
    });
  },

  // ===== Feature Navigation =====
  async openFeature(featureName) {
    UI.highlightNav(featureName);

    switch (featureName) {
      case 'story':
        UI.closeAllOverlays();
        const logEl = document.getElementById('logStream');
        if (logEl) logEl.scrollTop = logEl.scrollHeight;
        await this.fetchChapter();
        break;
      case 'profile':
        await this.loadDetailedStats();
        break;
      case 'inventory':
        await this.loadInventory();
        break;
      case 'equipment':
        await this.loadEquipment();
        break;
      case 'skills':
        await this.loadSkills();
        break;
      case 'titles':
        await this.loadTitles();
        break;
      case 'archive':
        this.openArchive();
        break;
      case 'pk':
        await this.loadPK();
        break;
      case 'rankings':
        await this.loadRankings();
        break;
      case 'broadcast':
        await this.loadBroadcast();
        break;
      case 'faction':
        await this.openFaction();
        break;
      case 'trade':
        this.openTrade();
        break;
      case 'party':
        this.openParty();
        break;
      case 'settings':
        this.showSettings();
        break;
      case 'feedback':
        this.openFeedback();
        break;
      case 'underworld':
        this.openUnderworld();
        break;
      case 'quests':
        this.loadQuests();
        break;
      case 'changelog':
        this.showChangelog();
        break;
    }
  },

  // Backward compat alias
  switchTab(featureName) {
    this.openFeature(featureName);
  },

  async loadGame(player) {
    this.state = 'PLAYING';
    this._currentPlayer = player;

    // Normalize player: API.me() returns raw row with stats_json string, not parsed stats object
    if (!player.stats && player.stats_json) {
      player.stats = typeof player.stats_json === 'string' ? JSON.parse(player.stats_json) : player.stats_json;
    }

    // Switch from auth page to game
    document.getElementById('authPage')?.classList.add('hidden');
    document.getElementById('gameWrapper')?.classList.remove('hidden');

    // Start polling for new versions + heartbeat
    this.startVersionPolling();
    this.startHeartbeat();

    this._isResting = !!(player.stats && player.stats.isResting);
    UI.renderLeftPanel(player);
    UI.highlightNav(null);
    UI.closeDrawer();

    UI.applyAllDisplaySettings();

    // Check if player needs to select a constellation
    const constellation = player.stats?.constellation;
    if (!constellation) {
      UI.addLog('你的背后星尚未选定。请从星空中选择一位赞助者。', 'system');
      try {
        const { data } = await API.getConstellations();
        if (data && data.constellations) {
          UI.showConstellationPicker(data.constellations);
        }
      } catch (e) { /* ignore */ }
    }

    // Check if player is dead
    if (player.stats?.isDead) {
      this._checkDead(player);
    }

    await this.fetchChapter({ isInitialLoad: true });
    this.refreshRankProgress();
    UI.renderSocialActionBar();
    // Description panel is shown on-demand when user opens features
    this._updateRestUI(this._isResting);
    if (this._isResting) {
      this._startRecoveryLoop();
    }
  },

  async fetchChapter(opts) {
    opts = opts || {};
    try {
      const data = await API.getCurrentStory(this.playerId);

      if (data.error) {
        UI.showError(data.message || '加载章节失败');
        return;
      }

      this.state = 'SHOWING_CHAPTER';
      // Push story narrative text to log stream (only first time)
      if (this._lastChapterKey !== data.chapter?.chapter_key) {
        this._lastChapterKey = data.chapter?.chapter_key;
        UI.storyBodyToLog(data.chapter);
      }

      // Show story popup if there are choices or chapter consumed
      const hasChapterContent = data.chapter && (data.chapter.summary || data.chapter.content);
      const hasAvailableChoices = data.choices && data.choices.length > 0;
      const hasChoices = hasAvailableChoices || (data.locked_choices && data.locked_choices.length > 0);
      const consumedDismissed = data.chapter_consumed && this._dismissedChapterKey === data.chapter?.chapter_key;
      const skipStoryPopup = localStorage.getItem('game_skipStoryPopup') !== 'false';
      let hasContent;
      if (opts.fromExplore && data.chapter_consumed && !hasAvailableChoices && skipStoryPopup) {
        hasContent = false;
        UI.renderStageIndicator(data.player);
      } else if (opts.isInitialLoad) {
        // On page refresh: never show story popup. If there's a pending main story,
        // it will appear during the next exploration. First exploration will be free.
        if (hasAvailableChoices) {
          this._firstExploreFreePending = true;
          UI.addLog('当前有主线剧情待推进，下次探索将触发（此次探索免费）', 'system');
        }
        hasContent = false;
      } else if (consumedDismissed) {
        // Already dismissed for this chapter — skip consumed/locked-choices popup
        hasContent = hasAvailableChoices || hasChapterContent;
      } else {
        hasContent = data.chapter_consumed || hasChoices || hasChapterContent;
      }

      if (hasContent) {
        data.chapter.chapter_consumed = data.chapter_consumed && !consumedDismissed;
        UI.showStoryPopup(data.chapter, data.choices, data.locked_choices, data.player);
      } else {
        // No content to show in popup — just close any existing
        document.getElementById('storyPopupOverlay')?.classList.add('hidden');
      }

      // Event panel removed — choices hidden from central area

      if (!opts.fromExplore) {
        UI.renderLeftPanel(data.player);
        UI.renderMainActionBar(data.player);
      }

      // Check if dead
      if (data.player?.stats?.isDead) {
        this._checkDead(data.player);
      }

      // Only replay logs on initial load, not on every chapter refresh
      if (this._initialLogsLoaded === undefined && data.player.logs && data.player.logs.length > 0) {
        this._initialLogsLoaded = true;
        const allLogs = data.player.logs.slice(-15);
        for (const l of allLogs) {
          const msg = typeof l === 'string' ? l : l.msg;
          UI.addLog(msg, 'system', { id: `init_${allLogs.indexOf(l)}` });
        }
      }

      // Fetch titles for left panel
      try {
        const { titles } = await API.getTitles(this.playerId);
        const titleCount = document.getElementById('titleCount');
        if (titleCount) titleCount.textContent = `称号 ${(titles || []).length}`;
      } catch (e) { /* ignore */ }

      if (data.endings && data.endings.length > 0) {
        this.state = 'SHOWING_ENDING';
        setTimeout(() => UI.renderEnding(data.endings[0]), 800);
      }
    } catch (e) {
      console.error('Fetch chapter error:', e);
      UI.showError('网络错误，请检查服务器连接。');
    }
  },

  async handleChoice(choiceKey) {
    if (this.state === 'CHOOSING') return;
    if (this._isResting) {
      UI.addLog('你正在休息，无法进行剧情选择。', 'warning');
      return;
    }
    this.state = 'CHOOSING';
    this._inHandlerError = false;

    try {
      const result = await API.makeChoice(this.playerId, choiceKey);

      if (result.error) {
        if (result.code === 'PLAYER_RESTING') {
          UI.addLog('你正在休息，无法进行该操作。', 'warning');
        } else {
          UI.showError(result.message || '选择失败');
        }
        this.state = 'SHOWING_CHAPTER';
        this._inHandlerError = true;
        return result;
      }

      // Process new_logs from backend — the primary source of log entries
      if (result.new_logs && result.new_logs.length > 0) {
        result.new_logs.forEach(log => {
          UI.addLog(log.message, log.type || 'story', { id: log.id });
        });
      }

      if (result.unlocked_titles && result.unlocked_titles.length > 0) {
        // Titles already logged via new_logs, skip duplicate
      }

      if (result.stage_completed) {
        UI.addLog('当前阶段剧情已完成。请在主线阶段面板中满足条件后进入下一阶段。', 'stage', { id: `stage_done_${Date.now()}` });
      }

      if (result.needs_stage_advance) {
        UI.addLog('请满足阶段推进条件后，在阶段面板中进入下一阶段。', 'stage', { id: `stage_adv_${Date.now()}` });
      }

      if (result.chapter_consumed) {
        UI.addLog('本章已结束，请通过探索推进剧情。', 'stage', { id: `consumed_${Date.now()}` });
      }

      if (result.ending) {
        this.state = 'SHOWING_ENDING';
        UI.renderLeftPanel(result.player);
        UI.renderMainActionBar(result.player);
        setTimeout(() => UI.renderEnding(result.ending), 800);
      } else if (!result.chapter_consumed) {
        await this.fetchChapter();
      } else {
        this.state = 'PLAYING';
        UI.renderLeftPanel(result.player);
        UI.renderMainActionBar(result.player);
        UI.renderStageIndicator(result.player);
        document.getElementById('storyPopupOverlay')?.classList.add('hidden');
        this._lastChapterKey = null;
      }

      return result;
    } catch (e) {
      console.error('Choice error:', e);
      UI.showError('操作失败: ' + (e.message || '请重试'));
      this.state = 'SHOWING_CHAPTER';
      this._inHandlerError = true;
    }
  },

  makeChoice(choiceKey) {
    this.handleChoice(choiceKey);
  },

  // ===== Explore =====
  async loadExplore() {
    await this.showMap();
  },

  async doExplore(locationKey) {
    this._lastExploredLocation = locationKey;
    try {
      const firstExplore = this._firstExploreFreePending || false;
      const result = await API.startExploration(this.playerId, locationKey, firstExplore);
      if (!result.code && firstExplore) {
        this._firstExploreFreePending = false;
      }
      if (result.code) {
        UI.addLog('探索失败: ' + result.message, 'warning');
        UI.addLog(result.message || '操作完成', 'system');
        return;
      }

      // Store actual stamina cost from server
      if (result.stamina_cost !== undefined) {
        this._lastExploreCost = result.stamina_cost;
      }

      // Check for combat encounter
      if (result.result?.combat_encounter) {
        // Store bond for support rate display
        this._bond = (result.player?.stats?.bond) || 0;
        UI.renderLeftPanel(result.player);
        this.updateMainActionBar(result.player);
        UI.addLog(`遭遇${result.result.is_elite ? '精英' : ''}怪物: ${result.result.event_name}`, 'battle', { id: `encounter_${Date.now()}` });
        UI.showCombatPopup(result);
        return 'combat';
      }

      // Show explore result in popup
      UI.showExplorePopup(result);
      UI.renderLeftPanel(result.player);
      this.updateMainActionBar(result.player);

      // Single log entry per exploration
      const typeLabels = { story: '主线剧情', side_story: '支线剧情', battle: '战斗', elite_battle: '精英战', boss_clue: 'Boss线索', opportunity: '机遇', resource: '资源', hidden: '隐藏事件', nothing: '无事件' };
      const logTypeMap = { story: 'story', side_story: 'side-story', battle: 'battle', elite_battle: 'elite-battle', boss_clue: 'boss-clue', opportunity: 'reward', resource: 'resource', hidden: 'hidden', nothing: 'system' };
      const resultName = result.result?.event_name || '';
      const logKey = `explore_${Date.now()}_${locationKey}`;
      UI.addLog(`${typeLabels[result.result_type] || result.result_type}: ${resultName}`, logTypeMap[result.result_type] || 'explore', { id: logKey });

      // Log rewards
      if (result.result?.rewards && Object.keys(result.result.rewards).length > 0) {
        const rew = result.result.rewards;
        const rewardParts = [];
        if (rew.coins) rewardParts.push(`硬币 +${rew.coins}`);
        if (rew.story_fragments) rewardParts.push(`碎片 +${rew.story_fragments}`);
        if (rew.exp) rewardParts.push(`EXP +${rew.exp}`);
        if (rew.equipment) rewardParts.push(`装备: ${rew.equipment_name || rew.equipment}`);
        if (rewardParts.length > 0) {
          UI.addLog(`获得: ${rewardParts.join(', ')}`, 'reward', { id: `${logKey}_rewards` });
        }
      }

      if (result.result?.is_final) {
        UI.addLog('阶段最终剧情已触发！', 'stage', { id: `${logKey}_final` });
      }

      if (result.result?.chapter_advanced) {
        const newChapterName = result.result?.new_chapter_name || result.result?.new_chapter_key;
        if (newChapterName) {
          UI.addLog(`剧情推进至新章节: ${newChapterName}`, 'stage', { id: `${logKey}_advance` });
        }
      }

      // Always refresh story after exploration
      await this.fetchChapter({ fromExplore: true });
      UI.renderStageIndicator(result.player);
      return result.result_type || 'nothing';
    } catch (e) {
      UI.addLog('探索出错: ' + (e.message || e), 'battle');
      return 'error';
    }
  },

  async quickExplore() {
    if (this._isResting) {
      UI.addLog('你正在休息，无法探索。请先停止休息。', 'warning');
      return;
    }
    if (!this.playerId) {
      UI.addLog('玩家数据未加载，请刷新页面。', 'warning');
      return;
    }
    // 3 consecutive explorations with stamina check
    try {
      const { locations } = await API.getLocations(this.playerId);
      if (!locations || locations.length === 0) {
        UI.addLog('暂无可探索的地图。', 'warning');
        return;
      }
      const locKey = this._getExploreTarget(locations);
      for (let i = 0; i < 3; i++) {
        const { data: playerData } = await API.getPlayer(this.playerId);
        const player = playerData && playerData.player;
        if (!player) {
          UI.addLog('获取玩家数据失败，快速探索已停止。', 'warning');
          break;
        }
        const stamina = (player.stats && player.stats.stamina) || 0;
        if (stamina < 5) {
          UI.addLog(`体力不足 (${stamina})，快速探索已停止。已完成 ${i}/3 次。`, 'warning');
          this.updateMainActionBar(player);
          break;
        }
        await this.doExplore(locKey);
      }
    } catch (e) {
      UI.addLog('快速探索失败: ' + (e.message || e), 'warning');
    }
  },

  async continueExplore() {
    if (this._isResting) {
      UI.addLog('你正在休息，无法探索。请先停止休息。', 'warning');
      return;
    }
    if (!this.playerId) {
      UI.addLog('玩家数据未加载，请刷新页面。', 'warning');
      return;
    }
    const mult = this._exploreMultiplier || 1;
    try {
      const { locations } = await API.getLocations(this.playerId);
      if (!locations || locations.length === 0) {
        UI.addLog('暂无可探索的地图，请先推进剧情解锁。', 'warning');
        return;
      }
      for (let i = 0; i < mult; i++) {
        const { data: playerData } = await API.getPlayer(this.playerId);
        const player = playerData && playerData.player;
        if (!player) {
          UI.addLog('获取玩家数据失败，探索已停止。', 'warning');
          break;
        }
        const stamina = (player.stats && player.stats.stamina) || 0;
        if (stamina < 5) {
          UI.addLog(`体力不足 (${stamina})，已完成 ${i}/${mult} 次探索。`, 'warning');
          this.updateMainActionBar(player);
          break;
        }
        const locKey = this._getExploreTarget(locations, player);
        const resultType = await this.doExplore(locKey);
        // Stop batch on major events (story, combat, etc.)
        if (resultType === 'combat' || resultType === 'story' || resultType === 'side_story' || resultType === 'boss_clue' || resultType === 'hidden') {
          if (mult > 1 && i < mult - 1) {
            UI.addLog(`批量探索遇到重要事件，已暂停 (${i + 1}/${mult})。`, 'plot');
          }
          break;
        }
        // Small delay between batch explores
        if (i < mult - 1) {
          await new Promise(r => setTimeout(r, 300));
        }
      }
    } catch (e) {
      UI.addLog('探索失败: ' + (e.message || e), 'warning');
    }
  },

  setExploreMultiplier(n, event) {
    this._exploreMultiplier = n;
    document.querySelectorAll('.ma-batch-btn').forEach(btn => {
      btn.classList.toggle('active', parseInt(btn.dataset.mult) === n);
    });
  },

  toggleAutoExplore(checked) {
    this._autoExploreActive = checked;
    if (checked) {
      UI.addLog('自动探索已开启，将连续探索直到遭遇重要事件或体力不足。', 'system');
      this._autoExploreLoop();
    } else {
      if (this._autoExploreTimer) {
        clearTimeout(this._autoExploreTimer);
        this._autoExploreTimer = null;
      }
      UI.addLog('自动探索已关闭。', 'system');
    }
  },

  async _autoExploreLoop() {
    if (!this._autoExploreActive) return;
    if (this._isResting) {
      UI.addLog('正在休息中，自动探索已暂停。', 'warning');
      this._autoExploreActive = false;
      const toggle = document.getElementById('autoExploreToggle');
      if (toggle) toggle.checked = false;
      return;
    }
    if (!this.playerId) {
      this._autoExploreActive = false;
      return;
    }
    try {
      const { data: playerData } = await API.getPlayer(this.playerId);
      const player = playerData && playerData.player;
      if (!player) { this._autoExploreActive = false; return; }
      const stamina = (player.stats && player.stats.stamina) || 0;
      if (stamina < 5) {
        UI.addLog(`体力不足 (${stamina})，自动探索已停止。`, 'warning');
        this._autoExploreActive = false;
        const toggle = document.getElementById('autoExploreToggle');
        if (toggle) toggle.checked = false;
        this.updateMainActionBar(player);
        return;
      }
      const { locations } = await API.getLocations(this.playerId);
      if (!locations || locations.length === 0) {
        this._autoExploreActive = false;
        return;
      }
      const locKey = this._getExploreTarget(locations, player);
      const resultType = await this.doExplore(locKey);
      if (resultType === 'combat' || resultType === 'story' || resultType === 'side_story' || resultType === 'boss_clue' || resultType === 'hidden') {
        UI.addLog('遭遇重要事件，自动探索已暂停。', 'plot');
        this._autoExploreActive = false;
        const toggle = document.getElementById('autoExploreToggle');
        if (toggle) toggle.checked = false;
        return;
      }
      // Auto-close minor explore popups
      const exploreOverlay = document.getElementById('explorePopupOverlay');
      if (exploreOverlay && !exploreOverlay.classList.contains('hidden')) {
        exploreOverlay.classList.add('hidden');
      }
      if (this._autoExploreActive) {
        this._autoExploreTimer = setTimeout(() => this._autoExploreLoop(), 600);
      }
    } catch (e) {
      UI.addLog('自动探索出错: ' + (e.message || e), 'warning');
      this._autoExploreActive = false;
      const toggle = document.getElementById('autoExploreToggle');
      if (toggle) toggle.checked = false;
    }
  },

  // Pick exploration target: map selection > current_location > first unlocked
  _getExploreTarget(locations, player) {
    const mapLoc = UI._currentMapLocation;
    if (mapLoc && locations.some(l => l.location_key === mapLoc)) {
      return mapLoc;
    }
    const currentLoc = (player && player.current_location) || this._lastExploredLocation || '';
    if (currentLoc && locations.some(l => l.location_key === currentLoc)) {
      return currentLoc;
    }
    return locations[0].location_key;
  },

  updateMainActionBar(player) {
    if (!player) return;
    UI.renderMainActionBar(player);
    // Flash stamina bar if low
    const stamina = (player.stats && player.stats.stamina) || 0;
    const staminaBar = document.getElementById('statStaBar');
    if (staminaBar && stamina < 10) {
      staminaBar.style.animation = 'none';
      staminaBar.offsetHeight;
      staminaBar.style.animation = 'staminaFlash 0.5s ease-in-out 3';
    }
  },

  // Backward compat
  updateBottomBar(player) { this.updateMainActionBar(player); },

  async doCamp() {
    if (this._isResting) {
      await this.stopRest();
    } else {
      await this.startRest();
    }
  },

  // Open map selection drawer for location switching
  async changeLocation() {
    if (this._isResting) {
      UI.addLog('你正在休息，无法切换地点。', 'warning');
      return;
    }
    await this.loadExplore();
  },

  doRest() {
    if (this._isResting) this.stopRest(); else this.startRest();
  },

  async startRest() {
    try {
      const result = await API.startRest(this.playerId);
      if (result.error) {
        UI.addLog(result.message || '无法休息', 'warning');
        return;
      }
      this._isResting = true;
      UI.addLog('你进入休息状态，生命与体力恢复速度提升。', 'system');
      UI.renderLeftPanel(result.player);
      this.updateMainActionBar(result.player);
      this._updateRestUI(true);
      this._startRecoveryLoop();
    } catch (e) {
      UI.addLog('休息失败: ' + (e.message || e), 'warning');
    }
  },

  async stopRest() {
    try {
      const result = await API.stopRest(this.playerId);
      if (result.error) {
        UI.addLog(result.message || '无法结束休息', 'warning');
        return;
      }
      this._isResting = false;
      UI.addLog('你结束休息，状态已恢复。', 'system');
      UI.renderLeftPanel(result.player);
      this.updateMainActionBar(result.player);
      this._updateRestUI(false);
      this._stopRecoveryLoop();
    } catch (e) {
      UI.addLog('结束休息失败: ' + (e.message || e), 'warning');
    }
  },

  _startRecoveryLoop() {
    if (this._recoveryInterval) return;
    this._recoveryInterval = setInterval(async () => {
      if (!this._isResting) {
        this._stopRecoveryLoop();
        return;
      }
      try {
        const { data: { player } } = await API.getPlayer(this.playerId);
        UI.renderLeftPanel(player);
        this.updateMainActionBar(player);
        this._isResting = !!(player.stats && player.stats.isResting);
        if (!this._isResting) this._stopRecoveryLoop();
      } catch (e) { /* ignore */ }
    }, 10000);
  },

  _stopRecoveryLoop() {
    if (this._recoveryInterval) {
      clearInterval(this._recoveryInterval);
      this._recoveryInterval = null;
    }
  },

  _updateRestUI(isResting) {
    const campBtn = document.getElementById('btnCamp');
    if (campBtn) {
      campBtn.textContent = isResting ? '停止休息' : '休息';
      if (isResting) {
        campBtn.classList.add('ma-btn-resting');
      } else {
        campBtn.classList.remove('ma-btn-resting');
      }
    }
    const exploreBtns = document.querySelectorAll('#btnContinueExplore, .ma-btn-primary, .ma-btn-explore');
    exploreBtns.forEach(btn => {
      if (isResting) {
        btn.setAttribute('disabled', 'disabled');
        btn.title = '休息中，无法行动';
      } else {
        btn.removeAttribute('disabled');
        btn.title = '';
      }
    });
    // Disable event buttons during rest
    document.querySelectorAll('.btn-event, .btn-event-primary').forEach(btn => {
      if (isResting) {
        btn.setAttribute('disabled', 'disabled');
        btn.title = '休息中，无法行动';
      } else {
        btn.removeAttribute('disabled');
        btn.title = '';
      }
    });
    // Update main action bar rest indicator
    const statusBar = document.querySelector('.main-action-status');
    if (statusBar) {
      let restEl = document.getElementById('restIndicator');
      if (isResting) {
        if (!restEl) {
          restEl = document.createElement('span');
          restEl.id = 'restIndicator';
          restEl.className = 'ma-rest-indicator';
          statusBar.prepend(restEl);
        }
        restEl.textContent = '休息中';
      } else if (restEl) {
        restEl.remove();
      }
    }
  },

  // ===== Inventory =====
  async loadInventory() {
    try {
      const { items } = await API.getInventory(this.playerId);
      const contentHTML = UI.renderInventory(items);
      UI.openDrawer('背包', contentHTML);
      const { data: { player } } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('加载背包失败: ' + (e.message || e), 'warning');
    }
  },

  async useItem(itemKey) {
    try {
      const result = await API.useItem(this.playerId, itemKey);
      if (result.error) {
        UI.addLog(result.error.message || '使用失败', 'warning');
        return;
      }
      UI.addLog(`使用了: ${result.used}`, 'reward');
      if (result.effects && result.effects.heal) {
        UI.addLog(`恢复了 ${result.effects.heal} HP`, 'reward');
      }
      this.loadInventory();
    } catch (e) {
      UI.addLog('使用道具失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== Mobile =====
  async openMapFromMobile() {
    UI.highlightNav('explore');
    await this.showMap();
  },

  // ===== Equipment =====
  async loadEquipment() {
    try {
      var data = await API.getEquipment(this.playerId);
      var activeSets = data.active_sets || [];
      var contentHTML = UI.renderEquipment(data.equipped, data.available, activeSets);
      UI.openDrawer('装备', contentHTML);
      var { data: playerResp } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(playerResp.player);
      UI.renderDescriptionPanel(playerResp.player, 'equipment');
    } catch (e) {
      UI.addLog('加载装备失败: ' + (e.message || e), 'warning');
    }
  },

  async equipItem(equipmentKey) {
    try {
      const result = await API.equipItem(this.playerId, equipmentKey, null);
      if (result.error) {
        UI.addLog(result.error.message || '装备失败', 'warning');
        return;
      }
      const eqName = (result.equipped && result.equipped.name) || equipmentKey;
      UI.addLog(`装备了: ${eqName}`, 'reward');
      this.loadEquipment();
    } catch (e) {
      UI.addLog('装备失败: ' + (e.message || e), 'warning');
    }
  },

  async unequipItem(slot) {
    if (!confirm(`确认卸下 ${UI._labelSlot(slot)} 栏位的装备？`)) return;
    try {
      const result = await API.unequipItem(this.playerId, slot);
      if (result.error) {
        UI.addLog(result.error.message || '卸下失败', 'warning');
        return;
      }
      UI.addLog(`卸下了 ${UI._labelSlot(slot)} 栏位的装备`, 'system');
      this.loadEquipment();
    } catch (e) {
      UI.addLog('卸下装备失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== Skills =====
  async loadSkills() {
    try {
      var skillsResp = await API.getSkills(this.playerId);
      var skills = skillsResp.skills || [];

      // Also fetch faction skills for faction tab
      var factionSkills = [];
      try {
        var fsResp = await API.getPlayerFactionSkills(this.playerId);
        factionSkills = fsResp.skills || [];
      } catch (e) { /* faction skills not critical */ }

      var contentHTML = UI.renderSkills(skills, factionSkills);
      UI.openDrawer('技能', contentHTML);
      var { data: playerResp } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(playerResp.player);
    } catch (e) {
      UI.addLog('加载技能失败: ' + (e.message || e), 'warning');
    }
  },

  async switchSkillsTab(tab) {
    UI._skillsTab = tab;
    await this.loadSkills();
  },

  async unlockSkill(skillKey) {
    try {
      var result = await API.unlockSkill(this.playerId, skillKey);
      if (result.error) {
        UI.addLog(result.error.message || '解锁失败', 'warning');
        return;
      }
      var skName = result.skill && result.skill.name || skillKey;
      UI.addLog('解锁技能: ' + skName, 'reward');
      this.loadSkills();
    } catch (e) {
      UI.addLog('解锁技能失败: ' + (e.message || e), 'warning');
    }
  },

  async learnFactionSkill(skillKey) {
    try {
      var result = await API.learnFactionSkill(this.playerId, skillKey);
      if (result.error) {
        UI.addLog(result.error.message || '学习失败', 'warning');
        return;
      }
      var skName = result.skill && result.skill.skill_name || skillKey;
      UI.addLog('习得阵营技能: ' + skName, 'reward');
      this.loadSkills();
    } catch (e) {
      UI.addLog('学习阵营技能失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== Titles =====
  async loadTitles() {
    try {
      const { titles } = await API.getTitles(this.playerId);
      const contentHTML = UI.renderAllTitles(titles);
      UI.openDrawer('称号', contentHTML);
      const { data: { player } } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('加载称号失败: ' + (e.message || e), 'warning');
    }
  },

  async openArchive() {
    try {
      const { data: { player } } = await API.getPlayer(this.playerId);
      if (!player.stats && player.stats_json) {
        player.stats = typeof player.stats_json === 'string' ? JSON.parse(player.stats_json) : player.stats_json;
      }
      const contentHTML = UI.renderArchive(player);
      UI.openDrawer('档案 · 星流秘典', contentHTML);
    } catch (e) {
      UI.addLog('加载档案失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== PK =====
  async loadPK() {
    try {
      const [oppData, recData] = await Promise.all([
        API.getPKOpponents(this.playerId),
        API.getPKRecords(this.playerId)
      ]);
      const opponentsHTML = UI.renderPKOpponents(oppData.opponents || []);
      const recordsHTML = UI.renderPKRecords(recData.records || []);
      const contentHTML = opponentsHTML + '<div class="drawer-section-label mt-16">PK记录</div>' + recordsHTML;
      UI.openDrawer('世界PK', contentHTML);
      const { data: { player } } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('加载PK失败: ' + (e.message || e), 'warning');
    }
  },

  async doPK(defenderId) {
    try {
      var result = await API.challengePlayer(this.playerId, defenderId);
      if (result.error) {
        UI.addLog(result.error.message || '挑战失败', 'warning');
        return;
      }
      if (result.success && result.data && result.data.message) {
        UI.addLog(result.data.message, 'system');
        UI.closeDrawer();
      }
    } catch (e) {
      UI.addLog('PK出错: ' + (e.message || e), 'pk');
    }
  },

  async doPKResolve(challengeId, accept) {
    try {
      var result = await API.resolveChallenge(challengeId, this.playerId, accept);
      if (result.error) {
        UI.addLog(result.error.message || '操作失败', 'warning');
        return;
      }
      if (accept && result.data && result.data.battle) {
        UI.renderPKResult(result.data.battle);
        var battle = result.data.battle;
        var opponentName = battle.attacker_wins ? battle.loser_name : battle.winner_name;
        var ratingChg = (battle.rating_change && battle.rating_change.attacker) || 0;
        UI.addLog('PK ' + (battle.attacker_wins ? '胜利' : '失败') + ' vs ' + opponentName + ' (评分 ' + (ratingChg > 0 ? '+' : '') + ratingChg + ')', 'pk');
        var resp = await API.getPlayer(this.playerId);
        if (resp && resp.data && resp.data.player) {
          UI.renderLeftPanel(resp.data.player);
          this._currentPlayer = resp.data.player;
        }
      } else if (!accept) {
        UI.addLog('你拒绝了PK挑战', 'system');
      }
      UI.dismissChallengePopup();
    } catch (e) {
      UI.addLog('PK回应失败: ' + (e.message || e), 'pk');
    }
  },

  // ===== Rankings =====
  _rankingTab: (function() { try { return localStorage.getItem('rsg_rankingTab') || 'hub'; } catch(e) { return 'hub'; } })(),
  async loadRankings() {
    var self = this;
    try {
      var tab = self._rankingTab;
      var contentHTML = '<div class="flex-row-wrap mb-12">' +
        '<button class="ma-btn ' + (tab === 'hub' ? 'primary' : '') + '" onclick="GameClient.loadRankingsTab(\'hub\')">总览</button>' +
        '<button class="ma-btn ' + (tab === 'pk' ? 'primary' : '') + '" onclick="GameClient.loadRankingsTab(\'pk\')">PK榜</button>' +
        '<button class="ma-btn ' + (tab === 'avatar' ? 'primary' : '') + '" onclick="GameClient.loadRankingsTab(\'avatar\')">位阶榜</button>' +
        '<button class="ma-btn ' + (tab === 'broadcast' ? 'primary' : '') + '" onclick="GameClient.loadRankingsTab(\'broadcast\')">贡献榜</button>' +
        '</div>';
      if (tab === 'hub') {
        contentHTML += await self._renderRankingsHub();
      } else if (tab === 'avatar') {
        var data = await API.getAvatarRankLeaderboard(50);
        contentHTML += UI.renderAvatarRankLeaderboard(data.rankings || []);
      } else if (tab === 'broadcast') {
        var bdata = await API.getBroadcastLeaderboard(50);
        contentHTML += UI.renderBroadcastLeaderboard(bdata.data || []);
      } else {
        var pkdata = await API.getRankings(50);
        contentHTML += UI.renderRankings(pkdata.rankings || []);
      }
      UI.openDrawer('排行榜', contentHTML);
    } catch (e) {
      UI.addLog('加载排行失败: ' + (e.message || e), 'warning');
    }
  },
  async loadRankingsTab(tab) {
    this._rankingTab = tab;
    try { localStorage.setItem('rsg_rankingTab', tab); } catch(e) {}
    await this.loadRankings();
  },
  async _renderRankingsHub() {
    var html = '';
    try {
      // Load all three leaderboards in parallel, show top 5 of each
      var pkData = await API.getRankings(5);
      var avatarData = await API.getAvatarRankLeaderboard(5);
      var broadcastData = await API.getBroadcastLeaderboard(5);

      html += '<div class="drawer-section-label">PK 排行榜 (Top 5)</div>';
      if (pkData.rankings && pkData.rankings.length > 0) {
        html += '<table class="drawer-table mb-16"><thead><tr><th>#</th><th>玩家</th><th>Lv</th><th>评分</th></tr></thead><tbody>';
        for (var i = 0; i < pkData.rankings.length; i++) {
          var r = pkData.rankings[i];
          html += '<tr><td class="' + (i < 3 ? 'rank-top' : '') + '">' + (r.rank || i + 1) + '</td><td>' + r.player_name + '</td><td>' + r.level + '</td><td>' + r.rating + '</td></tr>';
        }
        html += '</tbody></table>';
      } else {
        html += '<p class="text-secondary text-center px-12 py-8">暂无数据</p>';
      }

      html += '<div class="drawer-section-label">位阶排行榜 (Top 5)</div>';
      if (avatarData.rankings && avatarData.rankings.length > 0) {
        html += '<table class="drawer-table mb-16"><thead><tr><th>#</th><th>玩家</th><th>化身位阶</th><th>Lv</th></tr></thead><tbody>';
        for (var j = 0; j < avatarData.rankings.length; j++) {
          var ar = avatarData.rankings[j];
          html += '<tr><td class="' + (j < 3 ? 'rank-top' : '') + '">' + (ar.rank || j + 1) + '</td><td>' + ar.player_name + '</td><td class="text-gold">' + ar.avatarRank + '级·' + ar.avatarRankName + '</td><td>' + ar.level + '</td></tr>';
        }
        html += '</tbody></table>';
      } else {
        html += '<p class="text-secondary text-center px-12 py-8">暂无数据</p>';
      }

      html += '<div class="drawer-section-label">星流贡献榜 (Top 5)</div>';
      var bRankings = broadcastData.data || [];
      if (bRankings.length > 0) {
        html += '<table class="drawer-table mb-16"><thead><tr><th>#</th><th>玩家</th><th>Lv</th><th>贡献值</th></tr></thead><tbody>';
        for (var k = 0; k < bRankings.length; k++) {
          var br = bRankings[k];
          html += '<tr><td class="' + (k < 3 ? 'rank-top' : '') + '">' + (br.rank || k + 1) + '</td><td>' + br.player_name + '</td><td>' + br.level + '</td><td class="text-teal">' + br.total_contribution + '</td></tr>';
        }
        html += '</tbody></table>';
      } else {
        html += '<p class="text-secondary text-center px-12 py-8">暂无数据</p>';
      }

      html += '<div class="text-center mt-8">';
      html += '<span class="text-secondary fs-11">点击上方标签查看完整榜单</span>';
      html += '</div>';
    } catch (e) {
      html = '<p class="empty-state">加载排行总览失败。</p>';
    }
    return html;
  },

  // ===== Avatar Rank =====
  async refreshRankProgress() {
    try {
      const resp = await API.getAvatarRank(this.playerId);
      const rankData = resp && resp.data ? resp.data : resp;
      if (rankData) UI.setRankProgressInfo(rankData);
    } catch (e) { /* non-critical */ }
  },

  async openAvatarRank() {
    try {
      var resp = await API.getAvatarRank(this.playerId);
      var rankData = resp && resp.data ? resp.data : resp;
      var contentHTML = UI.renderAvatarRankPanel(rankData, this.playerId);
      UI.openDrawer('化身位阶', contentHTML);
    } catch (e) {
      UI.addLog('加载位阶失败: ' + (e.message || e), 'warning');
    }
  },
  async doRankUp(playerId) {
    try {
      var result = await API.rankUp(playerId);
      var rankResult = result && result.data ? result.data : result;
      if (rankResult && rankResult.rankedUp) {
        UI.addLog(rankResult.log, 'system');
        this.refreshRankProgress();
        var resp = await API.getPlayer(playerId);
        if (resp && resp.data && resp.data.player) {
          UI.renderLeftPanel(resp.data.player);
          this._currentPlayer = resp.data.player;
        }
        var rankResp = await API.getAvatarRank(playerId);
        var rankData = rankResp && rankResp.data ? rankResp.data : rankResp;
        var contentHTML = UI.renderAvatarRankPanel(rankData, playerId);
        UI.openDrawer('化身位阶', contentHTML);
      } else if (rankResult && rankResult.breakthroughFailed) {
        UI.addLog(rankResult.displayName + ' 突破失败！概率: ' + Math.round((rankResult.breakthroughRate || 0) * 100) + '%', 'warning');
        this.refreshRankProgress();
      }
    } catch (e) {
      UI.addLog('升阶失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== Stage Panel =====
  async loadStageStatus() {
    try {
      const status = await API.getStageStatus(this.playerId);
      const contentHTML = UI.renderStagePanel(status);
      UI.openDrawer('主线阶段', contentHTML);
      const { data: { player } } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      // Stage panel is non-critical; fail silently
    }
  },

  async doStageAdvance(chapterKey) {
    try {
      const response = await API.doStageAdvance(this.playerId, chapterKey);
      if (!response || !response.success) {
        const errMsg = (response && response.error && response.error.message) || '阶段推进失败';
        UI.addLog(errMsg, 'warning');
        await this.loadStageStatus();
        return;
      }
      const result = response.data;
      UI.addLog(`进入下一阶段: ${result.chapter.chapter_name}`, 'stage');
      UI.renderLeftPanel(result.player);
      const skillNames = result.unlocked_skill_names || result.unlocked_skills || [];
      if (skillNames.length > 0) {
        UI.addLog(`解锁技能: ${skillNames.join(', ')}`, 'reward');
      }
      UI.closeDrawer();
      await this.fetchChapter();
    } catch (e) {
      UI.addLog('阶段推进失败: ' + (e.message || e), 'warning');
    }
  },

  // Backward compat
  async loadBreakthrough() { return this.loadStageStatus(); },
  async doBreakthrough(chapterKey) { return this.doStageAdvance(chapterKey); },

  // ===== Broadcast (星流放送) =====
  async loadBroadcast() {
    try {
      const [activeResp, historyResp] = await Promise.all([
        API.getActiveBroadcast(),
        API.getBroadcastHistory()
      ]);
      const activeEvents = (activeResp && activeResp.data) || [];
      const history = (historyResp && historyResp.data) || [];
      const contentHTML = UI.renderBroadcast(activeEvents, history, this.playerId);
      UI.openDrawer('星流放送', contentHTML);

      if (activeEvents.length > 0) {
        const event = activeEvents[0];
        this.loadBroadcastProgress(event.id);
        if (this.playerId) this.loadMyContribution(event.id, this.playerId);
      }

    } catch (e) {
      // Broadcast is non-critical
    }
  },

  async doJoinBroadcast(eventId) {
    try {
      const result = await API.joinBroadcast(eventId, this.playerId);
      if (result.error) {
        UI.addLog(result.error.message || '参加失败', 'warning');
        return;
      }
      UI.addLog('参加了星流放送', 'broadcast');
      this.loadBroadcast();
    } catch (e) {
      UI.addLog('参加失败: ' + (e.message || e), 'warning');
    }
  },

  async doClaimBroadcastReward(eventId) {
    try {
      const result = await API.claimBroadcastReward(eventId, this.playerId);
      if (result.error) {
        UI.addLog(result.error.message || '领奖失败', 'warning');
        return;
      }
      UI.addLog('领取了星流放送奖励', 'broadcast');
      this.loadBroadcast();
      const { data: { player } } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('领奖失败: ' + (e.message || e), 'warning');
    }
  },

  async doSubmitBroadcastResource(eventId) {
    var resourceType = prompt('提交资源类型 (storyFragments/coins):');
    if (!resourceType) return;
    var amount = parseInt(prompt('提交数量:'));
    if (!amount || amount <= 0) return;
    try {
      var result = await API.submitBroadcastResource(eventId, {
        playerId: this.playerId,
        resourceType: resourceType,
        amount: amount
      });
      if (result.error) {
        UI.addLog(result.error.message || '提交失败', 'warning');
        return;
      }
      UI.addLog('已提交 ' + resourceType + ' ×' + amount, 'broadcast');
      this.loadBroadcast();
    } catch (e) {
      UI.addLog('提交资源失败: ' + (e.message || e), 'warning');
    }
  },

  async loadBroadcastProgress(eventId) {
    try {
      const progress = await API.getBroadcastProgress(eventId);
      UI.renderBroadcastProgress(progress);
      const ranking = await API.getBroadcastRanking(eventId, 10);
      UI.renderContributionRanking(ranking);
    } catch (e) { /* non-critical */ }
  },

  async loadMyContribution(eventId, playerId) {
    try {
      const data = await API.getMyContribution(eventId, playerId);
      UI.renderMyContribution(data);
    } catch (e) { /* non-critical */ }
  },

  // ===== Faction (阵营) =====
  async openFaction() {
    UI.openDrawer('星座阵营', '<div id="factionContent"><div class="loading-placeholder"><div class="loading-spinner"></div></div></div>');

    try {
      var listings = await API.getActiveListings();
      var myListings = this.playerId ? await API.getMyListings(this.playerId) : { data: [] };
      var content = document.getElementById('factionContent');
      if (content) {
        content.innerHTML = UI.renderTradePanel(this.playerId, (listings && listings.data) || [], (myListings && myListings.data) || []);
      }
    } catch (e) {
      var content = document.getElementById('factionContent');
      if (content) content.innerHTML = '<div class="error-state">加载失败: ' + UI.escapeHtml(e.message || e) + '</div>';
    }
  },

  async createTradeListing(itemKey, itemType, quantity, price) {
    try {
      var result = await API.createListing(this.playerId, itemKey, itemType, quantity, price);
      if (result.error) { UI.addLog(result.error.message, 'warning'); return; }
      UI.addLog('挂单发布成功', 'system');
      this.openTrade();
    } catch (e) { UI.addLog('发布失败: ' + (e.message || e), 'warning'); }
  },

  async buyListing(listingId) {
    try {
      var result = await API.buyListing(listingId, this.playerId);
      if (result.error) { UI.addLog(result.error.message, 'warning'); return; }
      UI.addLog('购买成功！', 'system');
      this.openTrade();
    } catch (e) { UI.addLog('购买失败: ' + (e.message || e), 'warning'); }
  },

  async cancelListing(listingId) {
    try {
      await API.cancelListing(listingId, this.playerId);
      UI.addLog('挂单已取消，物品已退回', 'system');
      this.openTrade();
    } catch (e) { UI.addLog('取消失败: ' + (e.message || e), 'warning'); }
  },

  // ===== Party (组队) =====
  async openParty() {
    UI.openDrawer('组队讨伐', '<div id="partyContent"><div class="loading-placeholder"><div class="loading-spinner"></div></div></div>');
    try {
      var parties = await API.getActiveParties();
      var myParty = this.playerId ? await API.getMyParty(this.playerId) : null;
      var content = document.getElementById('partyContent');
      if (content) {
        content.innerHTML = UI.renderPartyPanel(this.playerId, myParty ? myParty.data : null, (parties && parties.data) || []);
      }
    } catch (e) {
      var content = document.getElementById('partyContent');
      if (content) content.innerHTML = '<div class="error-state">加载失败: ' + UI.escapeHtml(e.message || e) + '</div>';
    }
  },

  async createParty(bossKey) {
    try {
      var result = await API.createParty(this.playerId, bossKey);
      if (result.error) { UI.addLog(result.error.message, 'warning'); return; }
      UI.addLog('队伍创建成功', 'system');
      this.openParty();
    } catch (e) { UI.addLog('创建失败: ' + (e.message || e), 'warning'); }
  },

  async joinParty(partyId) {
    try {
      var result = await API.joinParty(partyId, this.playerId);
      if (result.error) { UI.addLog(result.error.message, 'warning'); return; }
      UI.addLog('加入队伍成功', 'system');
      this.openParty();
    } catch (e) { UI.addLog('加入失败: ' + (e.message || e), 'warning'); }
  },

  async leaveParty(partyId) {
    try {
      await API.leaveParty(partyId, this.playerId);
      UI.addLog('已离开队伍', 'system');
      this.openParty();
    } catch (e) { UI.addLog('离开失败: ' + (e.message || e), 'warning'); }
  },

  async startPartyBossBattle(partyId) {
    try {
      var result = await API.startPartyBossBattle(partyId, this.playerId);
      if (result.error) { UI.addLog(result.error.message, 'warning'); return; }
      var data = result.data || result;
      if (data.bossDefeated) {
        UI.addLog('讨伐成功！Boss被击破！', 'story');
      } else {
        UI.addLog('讨伐失败...重新整队', 'warning');
      }
      this.openParty();
    } catch (e) { UI.addLog('讨伐失败: ' + (e.message || e), 'warning'); }
  },

  // ===== Chat (聊天频道) =====
  _chatChannel: 'global',
  _chatLastId: 0,
  _chatTimer: null,

  async openChat() {
    this._chatChannel = 'global';
    this._chatLastId = 0;
    UI.openDrawer('聊天频道', '<div id="chatContent"><div class="loading-placeholder"><div class="loading-spinner"></div></div></div>');
    await this.loadChatMessages();
    this._startChatPolling();
  },

  async loadChatMessages(silent) {
    try {
      var data = await API.getChatMessages(this._chatChannel, 50, this._chatLastId || null);
      var messages = (data && data.data) || [];
      if (messages.length > 0) {
        this._chatLastId = messages[messages.length - 1].id;
      }
      if (!silent) {
        var chatContent = document.getElementById('chatContent');
        if (chatContent) {
          chatContent.innerHTML = UI.renderChat(messages, this.playerId);
          this._scrollChatToBottom();
        }
      } else {
        // Append new messages only (skip ones we already have)
        var chatMsgs = document.getElementById('chatMessages');
        if (chatMsgs && messages.length > 0) {
          for (var i = 0; i < messages.length; i++) {
            var m = messages[i];
            var isMine = this.playerId && m.player_id === this.playerId;
            var div = document.createElement('div');
            div.className = 'chat-msg' + (isMine ? ' chat-msg-mine' : '');
            var html = '';
            if (!isMine) html += '<span class="chat-msg-author">' + UI.escapeHtml(m.player_name || '') + '</span>';
            html += '<span class="chat-msg-text">' + UI.escapeHtml(m.message || '') + '</span>';
            html += '<span class="chat-msg-time">' + (m.created_at || '').substr(11, 5) + '</span>';
            div.innerHTML = html;
            chatMsgs.appendChild(div);
          }
          this._scrollChatToBottom();
        }
      }
    } catch (e) { /* non-critical */ }
  },

  async sendChatMessage() {
    var input = document.getElementById('chatInput');
    if (!input) return;
    var message = input.value.trim();
    if (!message) return;
    try {
      await API.sendChatMessage(this.playerId, this._currentPlayer.player_name, message, this._chatChannel);
      input.value = '';
      await this.loadChatMessages();
    } catch (e) {
      UI.addLog('发送消息失败: ' + (e.message || e), 'warning');
    }
  },

  _startChatPolling() {
    var self = this;
    if (this._chatTimer) clearInterval(this._chatTimer);
    this._chatTimer = setInterval(function() {
      // Check if drawer is still open
      var overlay = document.getElementById('rightDrawer');
      if (!overlay || overlay.classList.contains('hidden')) {
        clearInterval(self._chatTimer);
        self._chatTimer = null;
        return;
      }
      var chatMsgs = document.getElementById('chatMessages');
      if (!chatMsgs) { clearInterval(self._chatTimer); self._chatTimer = null; return; }
      self.loadChatMessages(true);
    }, 3000);
  },

  _scrollChatToBottom() {
    var el = document.getElementById('chatMessages');
    if (el) el.scrollTop = el.scrollHeight;
  },

  // ===== Friends =====
  async openFriends() {
    await this.loadFriendData();
  },

  async loadFriendData() {
    try {
      var friendsData = await API.getFriendList(this.playerId);
      var requestsData = await API.getFriendRequests(this.playerId);
      var friends = (friendsData && friendsData.data) || [];
      var requests = (requestsData && requestsData.data) || [];
      var contentHTML = UI.renderFriendList(friends, requests);
      UI.openDrawer('好友', contentHTML);
    } catch (e) {
      UI.addLog('加载好友失败: ' + (e.message || e), 'warning');
    }
  },

  async addFriend() {
    var input = document.getElementById('friendIdInput');
    if (!input) return;
    var friendId = parseInt(input.value);
    if (!friendId) { UI.addLog('请输入有效的玩家 ID', 'warning'); return; }
    try {
      var result = await API.sendFriendRequest(this.playerId, friendId);
      if (result.error) { UI.addLog(result.error.message || '发送失败', 'warning'); return; }
      UI.addLog(result.data.message || '好友申请已发送', 'system');
      input.value = '';
      await this.loadFriendData();
    } catch (e) {
      UI.addLog('添加好友失败: ' + (e.message || e), 'warning');
    }
  },

  async acceptFriend(requestId) {
    try {
      var result = await API.acceptFriendRequest(this.playerId, requestId);
      if (result.error) { UI.addLog(result.error.message, 'warning'); return; }
      UI.addLog('已接受好友申请', 'system');
      await this.loadFriendData();
    } catch (e) {
      UI.addLog('操作失败: ' + (e.message || e), 'warning');
    }
  },

  async declineFriend(requestId) {
    try {
      await API.declineFriendRequest(this.playerId, requestId);
      await this.loadFriendData();
    } catch (e) { /* non-critical */ }
  },

  async removeFriend(friendId, friendName) {
    if (!confirm(`确认解除与「${friendName || '该玩家'}」的好友关系？`)) return;
    try {
      await API.removeFriend(this.playerId, friendId);
      UI.addLog('已删除好友', 'system');
      await this.loadFriendData();
    } catch (e) {
      UI.addLog('操作失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== Popup Choice Handler =====
  async handleChoiceFromPopup(choiceKey) {
    // Same logic as handleChoice, but with popup-specific post-processing
    const popupWasVisible = !document.getElementById('storyPopupOverlay')?.classList.contains('hidden');

    const result = await this.handleChoice(choiceKey);

    if (this._inHandlerError || !result) return;

    // Show stat gains in popup
    const effects = this._extractChoiceEffects(result);
    if (Object.keys(effects).length > 0 && popupWasVisible) {
      UI.showStatGainsInPopup(effects);
    }

    // Popup refresh logic for non-advancing choices
    if (!result.chapter_consumed && !result.ending) {
      UI.refreshStoryPopup(result.chapter, result.choices, result.locked_choices);
      UI.renderLeftPanel(result.player);
      UI.renderMainActionBar(result.player);
      if (!popupWasVisible) {
        document.getElementById('storyPopupOverlay')?.classList.add('hidden');
      }
    }
  },

  _extractChoiceEffects(result) {
    const effects = {};
    if (!result) return effects;

    // Prefer structured rewards data over log regex parsing
    if (result.rewards) {
      const r = result.rewards;
      if (r.coins) effects.coins = (effects.coins || 0) + r.coins;
      if (r.story_fragments) effects.story_fragments = (effects.story_fragments || 0) + r.story_fragments;
      if (r.exp) effects.exp = (effects.exp || 0) + r.exp;
      if (r.equipment) effects.equipment = r.equipment;
      if (r.items && r.items.length > 0) effects.items = r.items;
      if (r.stats) effects.stats = r.stats;
    }

    return effects;
  },

  dismissStoryPopup() {
    UI.dismissPopup('storyPopupOverlay');
    this._dismissedChapterKey = this._lastChapterKey;
  },

  dismissExplorePopup() {
    UI.dismissPopup('explorePopupOverlay');
  },

  // ===== World Map =====
  async showMap() {
    try {
      const { locations } = await API.getLocations(this.playerId);
      const { data: { player } } = await API.getPlayer(this.playerId);
      UI.showMapOverlay(locations, player);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('加载地图失败: ' + (e.message || e), 'warning');
    }
  },

  travelToLocation(locationKey) {
    UI._currentMapLocation = locationKey;
    API.getLocations(this.playerId).then(({ locations }) => {
      API.getPlayer(this.playerId).then(({ data: { player } }) => {
        UI.showMapOverlay(locations, player);
      });
    }).catch(() => {});
  },

  dismissMap() {
    UI.dismissPopup('mapOverlay');
  },

  async dismissMapAndExplore() {
    const locKey = UI._currentMapLocation;
    UI.dismissPopup('mapOverlay');
    if (locKey) {
      await this.doExplore(locKey);
    } else {
      UI.addLog('请先在地图上选择一个位置。', 'warning');
    }
  },

  // ===== Detailed Stats =====
  async loadDetailedStats() {
    try {
      const { data: { player } } = await API.getPlayer(this.playerId);
      let globalWLS = 0;
      try {
        const { data: wlStatus } = await API.getWorldlineStatus();
        globalWLS = wlStatus.worldLineShift || 0;
      } catch (e) { /* non-critical */ }
      const contentHTML = UI.renderDetailedStats(player, globalWLS);
      UI.openDrawer('详细属性', contentHTML);
      UI.renderLeftPanel(player, globalWLS);
      UI.renderDescriptionPanel(player, 'stats');
    } catch (e) {
      UI.addLog('加载角色详情失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== Combat Actions =====
  async doCombatAction(monsterKey, action) {
    try {
      const res = await API.resolveCombat(this.playerId, monsterKey, action);
      if (!res.success) {
        UI.addLog((res.error && res.error.message) || '战斗操作失败', 'warning');
        return;
      }
      const data = res.data;
      UI.showCombatResult(data);

      // Update player state
      if (data.player) {
        UI.renderLeftPanel(data.player);
        this.updateMainActionBar(data.player);
      } else {
        // Refresh player if not included
        const { data: { player } } = await API.getPlayer(this.playerId);
        UI.renderLeftPanel(player);
        this.updateMainActionBar(player);
      }
    } catch (e) {
      UI.addLog('战斗操作失败: ' + (e.message || e), 'battle');
    }
  },

  dismissCombatPopup() {
    UI.dismissPopup('combatPopupOverlay');
  },

  async finishCombat() {
    UI.dismissPopup('combatPopupOverlay');
    // Refresh player after combat
        const { data: { player } } = await API.getPlayer(this.playerId);

    UI.renderLeftPanel(player);
    this.updateMainActionBar(player);
  },

  // ===== Attribute Allocation =====
  async allocatePoints() {
    const atk = parseInt(document.getElementById('allocAtk')?.value) || 0;
    const def = parseInt(document.getElementById('allocDef')?.value) || 0;
    const spd = parseInt(document.getElementById('allocSpd')?.value) || 0;
    const crit = parseInt(document.getElementById('allocCrit')?.value) || 0;
    const total = atk + def + spd + crit;
    if (total <= 0) { UI.addLog('请分配至少1点属性', 'warning'); return; }

    try {
      const result = await API.allocatePoints(this.playerId, atk, def, spd, crit);
      if (result.success) {
        UI.addLog(`属性分配成功: 攻击+${atk} 防御+${def} 速度+${spd} 暴击+${crit}`, 'reward');
        UI.renderLeftPanel(result.data.player);
        this.loadDetailedStats();
      } else {
        UI.addLog((result.error && result.error.message) || '分配失败', 'warning');
      }
    } catch (e) {
      UI.addLog('分配失败: ' + (e.message || ''), 'warning');
    }
  },

  async resetAllocation() {
    try {
      const { data: { player } } = await API.getPlayer(this.playerId);
      if (!player.stats && player.stats_json) {
        player.stats = typeof player.stats_json === 'string' ? JSON.parse(player.stats_json) : player.stats_json;
      }
      const s = player.stats || {};
      const totalAlloc = (s.allocatedAtk || 0) + (s.allocatedDef || 0) + (s.allocatedSpd || 0) + (s.allocatedCrit || 0);
      if (totalAlloc <= 0) { UI.addLog('没有已分配的属性点', 'warning'); return; }
      const cost = Math.max(50, totalAlloc * 20);
      if (!confirm(`重置全部分配需要 ${cost} 枚硬币。已分配的 ${totalAlloc} 点属性将返还为自由点数。确认支付？`)) return;

      const result = await API.resetAllocation(this.playerId);
      if (result.success) {
        UI.addLog(`已重置全部分配，${totalAlloc} 点返还至自由属性，消耗 ${cost} 硬币`, 'system');
        UI.renderLeftPanel(result.data.player);
        this.loadDetailedStats();
      } else {
        UI.addLog((result.error && result.error.message) || '重置失败', 'warning');
      }
    } catch (e) {
      UI.addLog('重置失败: ' + (e.message || ''), 'warning');
    }
  },

  // ===== Constellation =====
  async pickConstellation(constellationKey) {
    try {
      const result = await API.selectConstellation(this.playerId, constellationKey);
      if (result.success) {
        const c = result.data.constellation;
        UI.dismissPopup('constellationPopupOverlay', () => {
          UI.addLog(`你选择了背后星: ${c.title}（${c.name}）`, 'system');
          this.fetchChapter();
        });
      } else {
        UI.addLog((result.error && result.error.message) || '选择失败', 'warning');
      }
    } catch (e) {
      UI.addLog('选择背后星失败: ' + (e.message || ''), 'warning');
    }
  },

  // ===== Dead / Revival =====
  _checkDead(player) {
    if (player && player.stats && player.stats.isDead) {
      UI.showUnderworldPopup(player);
    }
  },

  async doRevive(method) {
    try {
      const result = await API.revivePlayer(this.playerId, method);
      if (result.success) {
        UI.dismissPopup('underworldPopupOverlay', async () => {
          UI.addLog(result.data.message || '你从冥界归来了。', 'story');
          const { data: { player } } = await API.getPlayer(this.playerId);
          UI.renderLeftPanel(player);
          this.updateMainActionBar(player);
          await this.fetchChapter();
        });
      } else {
        UI.addLog((result.error && result.error.message) || '复活失败', 'warning');
      }
    } catch (e) {
      UI.addLog('复活失败: ' + (e.message || ''), 'warning');
    }
  },

  dismissUnderworld() {
    UI.dismissPopup('underworldPopupOverlay');
  },

  async openUnderworld() {
    try {
      var data = await API.getDeadPlayers();
      var deadList = (data && data.data) ? data.data : [];
      var contentHTML = UI.renderUnderworldPanel(deadList, this.playerId);
      UI.openDrawer('冥界', contentHTML);
    } catch (e) {
      UI.addLog('加载冥界失败: ' + (e.message || e), 'warning');
    }
  },

  async peerRevive(targetId, method) {
    var self = this;
    try {
      var result = await API.peerRevive(this.playerId, targetId, method);
      if (result.success) {
        UI.addLog(result.data.message || '你从冥界拉回了一位玩家。', 'story');
        UI.closeDrawer();
        var resp = await API.getPlayer(this.playerId);
        if (resp && resp.data && resp.data.player) {
          UI.renderLeftPanel(resp.data.player);
          this._currentPlayer = resp.data.player;
        }
      } else {
        UI.addLog((result.error && result.error.message) || '复活失败', 'warning');
      }
    } catch (e) {
      UI.addLog('复活失败: ' + (e.message || ''), 'warning');
    }
  },

  async showChangelog() {
    UI.closeAllOverlays();
    try {
      var resp = await fetch('/api/changelog');
      var data = await resp.json();
      var changelog = (data && data.data) ? data.data : [];
      var contentHTML = UI.renderChangelog(changelog);
      UI.openDrawer('更新历史', contentHTML);
    } catch (e) {
      UI.addLog('加载更新历史失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== Quests (日常/周常任务) =====
  _questTab: 'daily',

  async loadQuests() {
    var self = this;
    UI.openDrawer('任务', '<div id="questContent"><div class="loading-placeholder"><div class="loading-spinner"></div></div></div>');
    try {
      var resp = await API.getQuests(this.playerId);
      var data = (resp && resp.data) ? resp.data : null;
      if (data) {
        var content = document.getElementById('questContent');
        if (content) {
          content.innerHTML = UI.renderQuestPanel(data, self._questTab, this.playerId);
        }
      }
    } catch (e) {
      var content = document.getElementById('questContent');
      if (content) content.innerHTML = '<div class="error-state">加载任务失败: ' + UI.escapeHtml(e.message || e) + '</div>';
    }
  },

  async loadQuestsTab(tab) {
    this._questTab = tab;
    await this.loadQuests();
  },

  async claimQuestReward(questId) {
    var self = this;
    try {
      var result = await API.claimQuestReward(this.playerId, questId);
      if (result.error) {
        UI.addLog(result.error.message || '领取失败', 'warning');
        return;
      }
      if (result.data && result.data.data) {
        var d = result.data.data;
        var earned = d.earned;
        var rewardParts = [];
        if (earned.coins) rewardParts.push('金币 +' + earned.coins);
        if (earned.story_fragments) rewardParts.push('故事碎片 +' + earned.story_fragments);
        if (earned.constellationFavor) rewardParts.push('星座好感 +' + earned.constellationFavor);
        if (earned.items && earned.items.length > 0) rewardParts.push('物品: ' + earned.items.join(', '));
        UI.addLog('完成任务「' + d.quest_name + '」，获得: ' + rewardParts.join(', '), 'reward');
      } else if (result.success && result.data) {
        // Direct data path
        var d = result.data;
        var earned = d.earned;
        var rewardParts = [];
        if (earned.coins) rewardParts.push('金币 +' + earned.coins);
        if (earned.story_fragments) rewardParts.push('故事碎片 +' + earned.story_fragments);
        if (earned.constellationFavor) rewardParts.push('星座好感 +' + earned.constellationFavor);
        if (earned.items && earned.items.length > 0) rewardParts.push('物品: ' + earned.items.join(', '));
        UI.addLog('完成任务「' + d.quest_name + '」，获得: ' + rewardParts.join(', '), 'reward');
      }
      // Refresh quest panel and player stats
      await this.loadQuests();
      var resp = await API.getPlayer(this.playerId);
      if (resp && resp.data && resp.data.player) {
        UI.renderLeftPanel(resp.data.player);
      }
    } catch (e) {
      UI.addLog('领取奖励失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== Settings =====
  showSettings() {
    const contentHTML = UI.renderSettings();
    UI.openDrawer('设置', contentHTML);
  },

  // ===== Feedback =====
  openFeedback() {
    UI.closeAllOverlays();
    var overlay = document.getElementById('feedbackOverlay');
    if (!overlay) return;
    overlay.classList.remove('hidden');
    var nickname = document.getElementById('fbNickname');
    if (nickname && !nickname.value && this._currentUser?.username) {
      nickname.value = this._currentUser.username;
    }
    var resultEl = document.getElementById('fbResult');
    if (resultEl) { resultEl.style.display = 'none'; resultEl.textContent = ''; }
  },

  // ===== Friend Requests panel =====
  openFriendRequests() {
    var self = this;
    UI.openDrawer('好友申请', '<div class="loading-placeholder"><div class="loading-spinner"></div></div>');
    API.getFriendRequests(this.playerId).then(function(data) {
      var requests = (data && data.data) || [];
      var html = '';
      if (requests.length === 0) {
        html = '<p class="empty-state">暂无人申请加你为好友</p>';
      } else {
        html = '<div class="drawer-list">';
        for (var i = 0; i < requests.length; i++) {
          var r = requests[i];
          html += '<div class="drawer-card">';
          html += '<span class="fw-600">' + UI.escapeHtml(r.from_player_name || '未知玩家') + '</span>';
          html += '<span class="text-secondary fs-12"> 请求加为好友</span>';
          html += '<div class="mt-8 flex-row">';
          html += '<button class="btn-action" onclick="GameClient.acceptFriendRequest(' + r.id + ',\'' + (r.from_player_name || '').replace(/'/g, "\\'") + '\')">接受</button>';
          html += '<button class="btn-ma" onclick="GameClient.rejectFriendRequest(' + r.id + ')">拒绝</button>';
          html += '</div></div>';
        }
        html += '</div>';
      }
      UI.openDrawer('好友申请', html);
    }).catch(function(e) {
      UI.openDrawer('好友申请', '<div class="empty-state">加载失败</div>');
    });
  },

  // ===== Gift panel =====
  openGiftPanel() {
    var self = this;
    UI.openDrawer('赠送礼物', '<div class="loading-placeholder"><div class="loading-spinner"></div></div>');
    Promise.all([
      API.getFriendList(this.playerId),
      API.getInventory(this.playerId)
    ]).then(function(results) {
      var friends = (results[0] && results[0].data) || [];
      var inventory = (results[1] && results[1].data) || [];
      var html = '<div class="p-8">';
      if (friends.length === 0) {
        html += '<p class="empty-state p-16">暂无好友可赠送，先加个好友吧</p>';
      } else if (inventory.length === 0) {
        html += '<p class="empty-state">背包空空，无可赠送的物品</p>';
      } else {
        html += '<p class="mb-8">选择好友：</p>';
        html += '<select id="giftTarget" class="app-select mb-16">';
        for (var i = 0; i < friends.length; i++) {
          html += '<option value="' + friends[i].id + '">' + friends[i].player_name + '</option>';
        }
        html += '</select>';
        html += '<p class="mb-8">选择物品：</p>';
        html += '<div style="max-height:200px;overflow-y:auto;">';
        for (var j = 0; j < inventory.length; j++) {
          var item = inventory[j];
          html += '<div class="drawer-record-flex p-8 cursor-pointer border-bottom" onclick="GameClient.sendGift(\'' + item.item_key + '\')">';
          html += '<span class="dr-text">' + (item.item_name || item.item_key) + ' x' + (item.quantity || 1) + '</span>';
          html += '<span class="text-secondary fs-12 flex-shrink-0">赠送</span>';
          html += '</div>';
        }
        html += '</div>';
      }
      html += '</div>';
      UI.openDrawer('赠送礼物', html);
    }).catch(function(e) {
      UI.openDrawer('赠送礼物', '<div class="empty-state">加载失败</div>');
    });
  },

  sendGift(itemKey) {
    var targetId = document.getElementById('giftTarget')?.value;
    if (!targetId) return;
    var self = this;
    API.sendGift(this.playerId, targetId, itemKey).then(function(data) {
      if (data && data.success) {
        UI.addLog('礼物已送出！', 'system');
        UI.closeDrawer();
      } else {
        UI.addLog('赠送失败: ' + ((data && data.error && data.error.message) || '未知错误'), 'warning');
      }
    }).catch(function(e) {
      UI.addLog('赠送失败: ' + (e.message || e), 'warning');
    });
  },

  // ===== Recent interactions =====
  openRecentInteractions() {
    var self = this;
    UI.openDrawer('最近互动', '<div class="loading-placeholder"><div class="loading-spinner"></div></div>');
    API.getRecentInteractions(this.playerId).then(function(data) {
      var interactions = (data && data.data) || [];
      var html = '';
      if (interactions.length === 0) {
        html = '<p class="empty-state">暂无最近互动记录</p>';
      } else {
        html = '<div class="drawer-list">';
        for (var i = 0; i < interactions.length; i++) {
          var ix = interactions[i];
          html += '<div class="drawer-card fs-13">';
          html += '<span>' + (ix.description || ix.type || '互动') + '</span>';
          html += '<span class="text-secondary">' + (ix.created_at || '').substr(0,16) + '</span>';
          html += '</div>';
        }
        html += '</div>';
      }
      UI.openDrawer('最近互动', html);
    }).catch(function(e) {
      UI.openDrawer('最近互动', '<p class="empty-state">暂无最近互动记录</p>');
    });
  },

  async submitFeedback() {
    var content = document.getElementById('fbContent').value.trim();
    var resultEl = document.getElementById('fbResult');
    if (!content) {
      if (resultEl) {
        resultEl.textContent = '请输入反馈内容';
        resultEl.style.display = 'block';
      }
      return;
    }

    var data = {
      playerId: this.playerId || null,
      nickname: document.getElementById('fbNickname').value.trim() || (this._currentUser?.username) || null,
      type: document.getElementById('fbType').value,
      content: content
    };

    try {
      var res = await API.submitFeedback(data);
      if (res.success) {
        if (resultEl) {
          resultEl.textContent = '感谢反馈！';
          resultEl.style.display = 'block';
        }
        document.getElementById('fbNickname').value = '';
        document.getElementById('fbContent').value = '';
        document.getElementById('fbType').value = 'bug';
        setTimeout(function() {
          UI.dismissPopup('feedbackOverlay');
        }, 1500);
      } else {
        if (resultEl) {
          resultEl.textContent = (res.error && res.error.message) || '提交失败';
          resultEl.style.display = 'block';
        }
      }
    } catch (e) {
      if (resultEl) {
        resultEl.textContent = '网络错误，请重试';
        resultEl.style.display = 'block';
      }
    }
  },

  async newGame() {
    Storage.clear();
    UI.hide('endingInline');
    // Keep auth token, just create a new player
    if (Storage.getToken()) {
      await this.showPostLoginCreatePlayer();
    } else {
      await this.showAuthScreen();
    }
  },

  // ===== Changelog =====
  _latestChangelogVersion: null,

  // ===== Version Check — detect new deployments and force refresh =====
  async checkVersion() {
    try {
      const resp = await fetch('/api/version');
      const data = await resp.json();
      const serverVersion = data.version || '0.0.0';
      const clientVersion = localStorage.getItem('game_client_version');

      if (!clientVersion) {
        // First visit — store current version
        localStorage.setItem('game_client_version', serverVersion);
        console.log('[version] first visit, stored:', serverVersion);
        return true;
      }

      if (clientVersion !== serverVersion) {
        // Version mismatch — update localStorage first so reload doesn't loop
        console.log('[version] mismatch — client:', clientVersion, 'server:', serverVersion);
        localStorage.setItem('game_client_version', serverVersion);
        var overlay = document.getElementById('versionUpdateOverlay');
        if (overlay) overlay.style.display = 'flex';
        document.getElementById('versionUpdateMsg').textContent =
          '游戏已更新到 v' + serverVersion + '，请刷新页面以获取最新内容。';
        // Hide game UI
        var gw = document.getElementById('gameWrapper');
        if (gw) gw.classList.add('hidden');
        var auth = document.getElementById('authPage');
        if (auth) auth.classList.add('hidden');
        return false;
      }

      console.log('[version] up to date:', serverVersion);
      return true;
    } catch (e) {
      console.error('[version] check failed:', e);
      return true; // Allow on error — don't block the game
    }
  },

  // Start periodic version check (every 60s)
  startVersionPolling() {
    var self = this;
    if (this._versionPolling) return;
    this._versionPolling = setInterval(async function() {
      try {
        var resp = await fetch('/api/version?_=' + Date.now());
        var data = await resp.json();
        var serverVersion = data.version || '0.0.0';
        var clientVersion = localStorage.getItem('game_client_version');
        if (clientVersion && clientVersion !== serverVersion) {
          localStorage.setItem('game_client_version', serverVersion);
          var overlay = document.getElementById('versionUpdateOverlay');
          if (overlay) overlay.style.display = 'flex';
          document.getElementById('versionUpdateMsg').textContent =
            '游戏已更新到 v' + serverVersion + '，请刷新页面以获取最新内容。';
          clearInterval(self._versionPolling);
        }
      } catch (e) { /* silent */ }
    }, 60000);
  },

  // Heartbeat every 30s to mark online + poll for PK challenges
  startHeartbeat() {
    var self = this;
    if (this._heartbeatInterval) return;
    this._heartbeatInterval = setInterval(async function() {
      try {
        if (!self.playerId) return;
        var resp = await API.heartbeat(self.playerId);
        if (resp && resp.pendingChallenges && resp.pendingChallenges.length > 0) {
          UI.showChallengePopup(resp.pendingChallenges, self.playerId);
        }
      } catch (e) { /* silent */ }
    }, 30000);
    // Immediate first beat
    setTimeout(async function() {
      try {
        if (!self.playerId) return;
        await API.heartbeat(self.playerId);
      } catch (e) { /* silent */ }
    }, 2000);
  },

  async checkChangelog() {
    try {
      const resp = await fetch('/api/changelog');
      const data = await resp.json();
      console.log('[changelog] response:', data);
      if (!data.success || !data.data || data.data.length === 0) {
        console.log('[changelog] no data, skipping');
        return;
      }
      const latest = data.data[0];
      this._latestChangelogVersion = latest.version;
      const seenVersion = localStorage.getItem('changelog_seen');
      console.log('[changelog] latest:', latest.version, 'seen:', seenVersion);
      if (seenVersion === latest.version) {
        console.log('[changelog] already seen, skipping');
        return;
      }
      // Wait a tick to ensure DOM is fully settled
      await new Promise(function(r) { setTimeout(r, 500); });
      document.getElementById('changelogVersion').textContent = 'v' + latest.version + ' — ' + (latest.date || '');
      document.getElementById('changelogList').innerHTML = latest.changes.map(function(c) {
        return '<li>' + c.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') + '</li>';
      }).join('');
      document.getElementById('changelogOverlay').classList.remove('hidden');
      console.log('[changelog] popup shown');
    } catch (e) {
      console.error('[changelog] error:', e);
    }
  },

  closeChangelog() {
    UI.dismissPopup('changelogOverlay');
    if (this._latestChangelogVersion) {
      setTimeout(() => {
        localStorage.setItem('changelog_seen', this._latestChangelogVersion);
      }, 300);
    }
  },

  // ===== Faction Skills (阵营技能) =====
  async openFactionSkills() {
    var self = this;
    UI.openDrawer('阵营技能', '<div id="factionSkillsContent"><div class="loading-placeholder"><div class="loading-spinner"></div></div></div>');
    try {
      var skills = await API.getPlayerFactionSkills(this.playerId);
      var data = (skills && skills.data && skills.data.skills) || [];
      var playerRes = await API.getPlayer(this.playerId);
      var constellationKey = '';
      if (playerRes && playerRes.data && playerRes.data.player) {
        constellationKey = playerRes.data.player.stats.constellation || '';
      }
      var content = document.getElementById('factionSkillsContent');
      if (content) {
        content.innerHTML = UI.renderFactionSkillsPanel(this.playerId, data, constellationKey);
      }
    } catch (e) {
      var content = document.getElementById('factionSkillsContent');
      if (content) content.innerHTML = '<div class="error-state">加载阵营技能失败: ' + UI.escapeHtml(e.message || e) + '</div>';
    }
  },

  async learnFactionSkill(skillKey) {
    try {
      var result = await API.learnFactionSkill(this.playerId, skillKey);
      if (result.error) { UI.addLog(result.error.message || '学习失败', 'warning'); return; }
      UI.addLog('成功习得阵营技能！', 'system');
      this.openFactionSkills();
    } catch (e) { UI.addLog('学习技能失败: ' + (e.message || e), 'warning'); }
  }
};
