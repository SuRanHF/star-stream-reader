// GameClient module - state machine (dark immersive RPG layout)
const GameClient = {
  state: 'INIT',
  playerId: null,
  _currentUser: null,

  async init() {
    this.state = 'LOADING';

    // Version check — block if client outdated
    const versionOk = await this.checkVersion();
    if (!versionOk) return;

    this.setupNavigation();
    this.setupModals();
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
      item.addEventListener('click', () => {
        const feature = item.dataset.feature;
        if (feature) this.openFeature(feature);
      });
    });
  },

  setupModals() {
    const modalOverlay = document.getElementById('modalOverlay');
    if (modalOverlay && modalOverlay.dataset.bound !== 'true') {
      modalOverlay.dataset.bound = 'true';
      modalOverlay.addEventListener('click', function(e) {
        if (e.target === this) UI.hideModal();
      });
    }

    const pkModalOverlay = document.getElementById('pkModalOverlay');
    if (pkModalOverlay && pkModalOverlay.dataset.bound !== 'true') {
      pkModalOverlay.dataset.bound = 'true';
      pkModalOverlay.addEventListener('click', function(e) {
        if (e.target === this) UI.dismissPopup('pkModalOverlay');
      });
    }
  },

  // ===== Feature Navigation =====
  openFeature(featureName) {
    UI.highlightNav(featureName);

    switch (featureName) {
      case 'story':
        UI.closeDrawer();
        // Close all popups (synchronous — avoid dismissPopup timer race)
        document.getElementById('storyPopupOverlay')?.classList.add('hidden');
        document.getElementById('explorePopupOverlay')?.classList.add('hidden');
        document.getElementById('mapOverlay')?.classList.add('hidden');
        const logEl = document.getElementById('logStream');
        if (logEl) logEl.scrollTop = logEl.scrollHeight;
        this.fetchChapter();
        break;
      case 'profile':
        this.loadDetailedStats();
        break;
      case 'inventory':
        this.loadInventory();
        break;
      case 'equipment':
        this.loadEquipment();
        break;
      case 'skills':
        this.loadSkills();
        break;
      case 'titles':
        this.loadTitles();
        break;
      case 'archive':
        this.openArchive();
        break;
      case 'pk':
        this.loadPK();
        break;
      case 'rankings':
        this.loadRankings();
        break;
      case 'broadcast':
        this.loadBroadcast();
        break;
      case 'settings':
        this.showSettings();
        break;
      case 'feedback':
        this.openFeedback();
        break;
    }
  },

  // Backward compat alias
  switchTab(featureName) {
    this.openFeature(featureName);
  },

  // ===== Create Player =====
  async showCreatePlayer() {
    UI.highlightNav('story');
    UI.closeDrawer();

    // Show create player in story popup
    const overlay = document.getElementById('storyPopupOverlay');
    if (overlay) {
      UI.setText('popupChapterTitle', '全知读者视角');
      const narrative = document.getElementById('popupNarrative');
      if (narrative) {
        narrative.innerHTML = '<p>在灭亡的世界中存活的三种方法。你是唯一知道结局的读者。</p><p style="margin-top:8px;">请输入你的名字，然后按下确认。</p>';
      }
      const choices = document.getElementById('popupChoices');
      if (choices) {
        choices.innerHTML = `
          <div style="display:flex;flex-direction:column;align-items:center;gap:12px;padding:16px;">
            <input class="app-input" id="createNameInput" placeholder="输入你的名字（留空则为"未命名读者"）" maxlength="20" style="max-width:320px;width:100%;">
            <button class="popup-continue-btn" id="createPlayerBtn" style="max-width:200px;">进入游戏</button>
          </div>
        `;
        document.getElementById('createPlayerBtn').onclick = async () => {
          const name = document.getElementById('createNameInput').value.trim();
          await this.createPlayer(name || undefined);
        };
      }
      document.getElementById('popupContinueBtn')?.classList.add('hidden');
      document.getElementById('popupStatGains')?.classList.add('hidden');
      overlay.classList.remove('hidden');
    }

    UI.setText('sceneName', '全知读者视角');
    UI.setText('sceneLocation', '');

    // Clear log and add welcome
    const stream = document.getElementById('logStream');
    if (stream) {
      stream.innerHTML = '';
      UI.addLog('欢迎来到全知读者视角。系统初始化完成，等待读者就绪...', 'system');
    }
  },

  async createPlayer(name) {
    try {
      const { player } = await API.createPlayer(name);
      Storage.setPlayerId(player.id);
      this.playerId = player.id;
      await this.loadGame(player);
    } catch (e) {
      console.error('Create player error:', e);
      UI.showError('创建玩家失败，请刷新页面重试。');
    }
  },

  async loadGame(player) {
    this.state = 'PLAYING';

    // Normalize player: API.me() returns raw row with stats_json string, not parsed stats object
    if (!player.stats && player.stats_json) {
      player.stats = typeof player.stats_json === 'string' ? JSON.parse(player.stats_json) : player.stats_json;
    }

    // Switch from auth page to game
    document.getElementById('authPage')?.classList.add('hidden');
    document.getElementById('gameWrapper')?.classList.remove('hidden');

    // Start polling for new versions
    this.startVersionPolling();

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

      UI.renderLeftPanel(data.player);
      UI.renderMainActionBar(data.player);

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
        UI.renderCurrentEventPanel(result.chapter, [], []);
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
        alert(result.message);
        return;
      }

      // Check for combat encounter
      if (result.result?.combat_encounter) {
        // Store bond for support rate display
        this._bond = (result.player?.stats?.bond) || 0;
        UI.renderLeftPanel(result.player);
        this.updateMainActionBar(result.player);
        UI.addLog(`遭遇${result.result.is_elite ? '精英' : ''}怪物: ${result.result.event_name}`, 'battle', { id: `encounter_${Date.now()}` });
        UI.showCombatPopup(result);
        return;
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
    } catch (e) {
      UI.addLog('探索出错: ' + (e.message || e), 'battle');
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
        const playerData = await API.getPlayer(this.playerId);
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
    // Use player.current_location, default to first unlocked map
    try {
      const { locations } = await API.getLocations(this.playerId);
      if (!locations || locations.length === 0) {
        UI.addLog('暂无可探索的地图，请先推进剧情解锁。', 'warning');
        return;
      }
      const playerData = await API.getPlayer(this.playerId);
      const player = playerData && playerData.player;
      if (!player) {
        UI.addLog('获取玩家数据失败，请刷新页面后重试。', 'warning');
        return;
      }
      const locKey = this._getExploreTarget(locations, player);
      const stamina = (player.stats && player.stats.stamina) || 0;
      if (stamina < 5) {
        UI.addLog(`体力不足 (${stamina})，请等待恢复或使用道具。`, 'warning');
        this.updateMainActionBar(player);
        return;
      }
      await this.doExplore(locKey);
    } catch (e) {
      UI.addLog('探索失败: ' + (e.message || e), 'warning');
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

  // Return to camp / safe zone
  async returnCamp() {
    if (this._isResting) {
      UI.addLog('你正在休息中。', 'system');
      return;
    }
    try {
      const result = await API.startRest(this.playerId);
      if (!result.error) {
        this._isResting = true;
        UI.addLog('你返回营地，进入休息状态。', 'system');
        UI.renderLeftPanel(result.player);
        this.updateMainActionBar(result.player);
        this._updateRestUI(true);
        this._startRecoveryLoop();
      }
    } catch (e) {
      UI.addLog('返回营地失败。', 'warning');
    }
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
        const { player } = await API.getPlayer(this.playerId);
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
    const exploreBtns = document.querySelectorAll('#btnContinueExplore, #btnQuickExplore, .ma-btn-primary, .ma-btn-explore');
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
      const { player } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('加载背包失败: ' + (e.message || e), 'warning');
    }
  },

  async useItem(itemKey) {
    try {
      const result = await API.useItem(this.playerId, itemKey);
      if (result.error) {
        alert(result.error.message || '使用失败');
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

  // ===== Equipment =====
  async loadEquipment() {
    try {
      const data = await API.getEquipment(this.playerId);
      const contentHTML = UI.renderEquipment(data.equipped, data.available);
      UI.openDrawer('装备', contentHTML);
      const { player } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
      UI.renderDescriptionPanel(player, 'equipment');
    } catch (e) {
      UI.addLog('加载装备失败: ' + (e.message || e), 'warning');
    }
  },

  async equipItem(equipmentKey) {
    try {
      const result = await API.equipItem(this.playerId, equipmentKey, null);
      if (result.error) {
        alert(result.error.message || '装备失败');
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
    try {
      const result = await API.unequipItem(this.playerId, slot);
      if (result.error) {
        alert(result.error.message || '卸下失败');
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
      const { skills } = await API.getSkills(this.playerId);
      const contentHTML = UI.renderSkills(skills);
      UI.openDrawer('技能', contentHTML);
      const { player } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('加载技能失败: ' + (e.message || e), 'warning');
    }
  },

  async unlockSkill(skillKey) {
    try {
      const result = await API.unlockSkill(this.playerId, skillKey);
      if (result.error) {
        alert(result.error.message || '解锁失败');
        return;
      }
      const skName = result.skill?.name || skillKey;
      UI.addLog(`解锁技能: ${skName}`, 'reward');
      this.loadSkills();
    } catch (e) {
      UI.addLog('解锁技能失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== Titles =====
  async loadTitles() {
    try {
      const { titles } = await API.getTitles(this.playerId);
      const contentHTML = UI.renderAllTitles(titles);
      UI.openDrawer('称号', contentHTML);
      const { player } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('加载称号失败: ' + (e.message || e), 'warning');
    }
  },

  async openArchive() {
    try {
      const { player } = await API.getPlayer(this.playerId);
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
      const contentHTML = opponentsHTML + '<div class="drawer-section-label" style="margin-top:16px;">PK记录</div>' + recordsHTML;
      UI.openDrawer('世界PK', contentHTML);
      const { player } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('加载PK失败: ' + (e.message || e), 'warning');
    }
  },

  async doPK(defenderId) {
    try {
      const result = await API.challengePlayer(this.playerId, defenderId);
      if (result.error) {
        alert(result.error.message || '挑战失败');
        return;
      }
      UI.renderPKResult(result);
      const opponentName = result.attacker_wins ? result.loser_name : result.winner_name;
      const ratingChg = result.rating_change?.attacker || 0;
      UI.addLog(`PK ${result.attacker_wins ? '胜利' : '失败'} vs ${opponentName} (评分 ${ratingChg > 0 ? '+' : ''}${ratingChg})`, 'pk');
      const { player } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('PK出错: ' + (e.message || e), 'pk');
    }
  },

  // ===== Rankings =====
  async loadRankings() {
    try {
      const data = await API.getRankings(50);
      const contentHTML = UI.renderRankings(data.rankings || []);
      UI.openDrawer('排行榜', contentHTML);
    } catch (e) {
      UI.addLog('加载排行失败: ' + (e.message || e), 'warning');
    }
  },

  // ===== Stage Panel =====
  async loadStageStatus() {
    try {
      const status = await API.getStageStatus(this.playerId);
      const contentHTML = UI.renderStagePanel(status);
      UI.openDrawer('主线阶段', contentHTML);
      const { player } = await API.getPlayer(this.playerId);
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
        alert(errMsg);
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
      const contentHTML = UI.renderBroadcast(activeEvents, history);
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
        alert(result.error.message || '参加失败');
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
        alert(result.error.message || '领奖失败');
        return;
      }
      UI.addLog('领取了星流放送奖励', 'broadcast');
      this.loadBroadcast();
      const { player } = await API.getPlayer(this.playerId);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('领奖失败: ' + (e.message || e), 'warning');
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
      const { player } = await API.getPlayer(this.playerId);
      UI.showMapOverlay(locations, player);
      UI.renderLeftPanel(player);
    } catch (e) {
      UI.addLog('加载地图失败: ' + (e.message || e), 'warning');
    }
  },

  travelToLocation(locationKey) {
    UI._currentMapLocation = locationKey;
    API.getLocations(this.playerId).then(({ locations }) => {
      API.getPlayer(this.playerId).then(({ player }) => {
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
      const { player } = await API.getPlayer(this.playerId);
      const contentHTML = UI.renderDetailedStats(player);
      UI.openDrawer('详细属性', contentHTML);
      UI.renderLeftPanel(player);
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
        alert((res.error && res.error.message) || '战斗操作失败');
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
        const { player } = await API.getPlayer(this.playerId);
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
    const { player } = await API.getPlayer(this.playerId);
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
    if (total <= 0) { alert('请分配至少1点属性'); return; }

    try {
      const result = await API.allocatePoints(this.playerId, atk, def, spd, crit);
      if (result.success) {
        UI.addLog(`属性分配成功: 攻击+${atk} 防御+${def} 速度+${spd} 暴击+${crit}`, 'reward');
        UI.renderLeftPanel(result.data.player);
        this.loadDetailedStats();
      } else {
        alert((result.error && result.error.message) || '分配失败');
      }
    } catch (e) {
      alert('分配失败: ' + (e.message || ''));
    }
  },

  async resetAllocation() {
    try {
      const { player } = await API.getPlayer(this.playerId);
      if (!player.stats && player.stats_json) {
        player.stats = typeof player.stats_json === 'string' ? JSON.parse(player.stats_json) : player.stats_json;
      }
      const s = player.stats || {};
      const totalAlloc = (s.allocatedAtk || 0) + (s.allocatedDef || 0) + (s.allocatedSpd || 0) + (s.allocatedCrit || 0);
      if (totalAlloc <= 0) { alert('没有已分配的属性点'); return; }
      const cost = Math.max(50, totalAlloc * 20);
      if (!confirm(`重置全部分配需要 ${cost} 枚硬币。已分配的 ${totalAlloc} 点属性将返还为自由点数。确认支付？`)) return;

      const result = await API.resetAllocation(this.playerId);
      if (result.success) {
        UI.addLog(`已重置全部分配，${totalAlloc} 点返还至自由属性，消耗 ${cost} 硬币`, 'system');
        UI.renderLeftPanel(result.data.player);
        this.loadDetailedStats();
      } else {
        alert((result.error && result.error.message) || '重置失败');
      }
    } catch (e) {
      alert('重置失败: ' + (e.message || ''));
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
        alert((result.error && result.error.message) || '选择失败');
      }
    } catch (e) {
      alert('选择背后星失败: ' + (e.message || ''));
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
          const { player } = await API.getPlayer(this.playerId);
          UI.renderLeftPanel(player);
          this.updateMainActionBar(player);
          await this.fetchChapter();
        });
      } else {
        alert((result.error && result.error.message) || '复活失败');
      }
    } catch (e) {
      alert('复活失败: ' + (e.message || ''));
    }
  },

  dismissUnderworld() {
    UI.dismissPopup('underworldPopupOverlay');
  },

  // ===== Settings =====
  showSettings() {
    const contentHTML = UI.renderSettings();
    UI.openDrawer('设置', contentHTML);
  },

  // ===== Feedback =====
  openFeedback() {
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
        // Version mismatch — show update overlay and block all operations
        console.log('[version] mismatch — client:', clientVersion, 'server:', serverVersion);
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
          var overlay = document.getElementById('versionUpdateOverlay');
          if (overlay) overlay.style.display = 'flex';
          document.getElementById('versionUpdateMsg').textContent =
            '游戏已更新到 v' + serverVersion + '，请刷新页面以获取最新内容。';
          clearInterval(self._versionPolling);
        }
      } catch (e) { /* silent */ }
    }, 60000);
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
  }
};
