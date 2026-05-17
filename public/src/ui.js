// UI module - DOM rendering (dark immersive RPG layout)

const LABELS = {
  stat: { attack:'攻击', defense:'防御', speed:'速度', hp:'生命', maxHp:'最大生命', critRate:'暴击率', critDamage:'暴击伤害', intelligence:'智慧', combat:'战斗能力', leadership:'领导力', bond:'羁绊', cruelty:'残酷', insight:'洞察', stamina:'体力', maxStamina:'最大体力', explorationPower:'探索力', luck:'幸运', dropRate:'掉落率', rating:'评分', pkWins:'PK胜', pkLosses:'PK负', pkStreak:'连胜', worldLineShift:'世界线偏移', channelHeat:'频道热度', freePoints:'自由点数', allocatedAtk:'已分配攻击', allocatedDef:'已分配防御', allocatedSpd:'已分配速度', allocatedCrit:'已分配暴击', level:'等级', exp:'经验值', atk:'攻击', def:'防御', spd:'速度' },
  rarity: { common:'普通', uncommon:'稀有', rare:'精良', epic:'史诗', legendary:'传说' },
  slot: { weapon:'武器', armor:'防具', accessory:'饰品', relic:'遗物' },
  eventType: { story:'主线剧情', side_story:'支线剧情', battle:'战斗', elite_battle:'精英战', boss_clue:'Boss线索', opportunity:'机遇', resource:'资源', hidden:'隐藏事件', nothing:'无事件' },
  broadcastStatus: { draft:'草稿', active:'进行中', completed:'已完成', failed:'失败', expired:'已过期', rewarded:'已发奖', cancelled:'已取消' },
  broadcastEventType: { world_boss:'世界Boss', exploration_drive:'探索驱动', story_hunt:'剧情狩猎', pk_tournament:'PK锦标赛', faction_conflict:'阵营冲突', disaster:'灾厄', opportunity_rain:'机遇放送', stage_support:'阶段支援' },
  skillType: { attack:'攻击', passive:'被动', defense:'防御', exploration:'探索', pk:'PK', story:'剧情' },
  itemType: { consumable:'消耗品', material:'材料', story_item:'剧情道具', equipment:'装备', key_item:'关键道具' },
  choiceType: { action:'行动', repeatable:'可重复', progress:'剧情', decision:'决策', stage_final:'阶段最终', locked:'锁定', special:'特殊' }
};

const UI = {
  // ===== Left Panel (精简版) =====
  renderLeftPanel(player) {
    if (!player) return;
    const s = player.stats || {};

    // Character header
    UI.setText('charName', player.player_name);
    UI.setText('charStage', player.stage_name || '初入星流');

    // HP bar
    const hp = s.hp || 0;
    const maxHp = s.maxHp || 100;
    UI.setBar('statHpBar', Math.max(0, Math.min(100, (hp / maxHp) * 100)));
    UI.setText('statHpVal', `${hp}/${maxHp}`);

    // Stamina bar
    const stamina = s.stamina || 0;
    const maxStamina = s.maxStamina || 50;
    UI.setBar('statStaBar', Math.max(0, Math.min(100, (stamina / maxStamina) * 100)));
    UI.setText('statStaVal', `${stamina}/${maxStamina}`);

    // Core stats row
    UI.setText('statLevel', s.level || 1);
    UI.setText('statCoins', player.coins || 0);
    UI.setText('statFragments', player.story_fragments || 0);

    // Current location
    UI.setText('currentLocationDisplay', player.current_location_name || player.current_location || '星之流观测站');

    // Titles
    const titleDetails = player.title_details || [];
    const titleList = document.getElementById('titleList');
    if (titleList) {
      titleList.innerHTML = titleDetails.slice(0, 4).map(t =>
        `<span class="title-badge ${t.rarity || 'common'}">${t.name}</span>`
      ).join('');
    }
    UI.setText('titleCount', `称号 ${titleDetails.length}`);

    // Main action bar
    this.renderMainActionBar(player);

    // Top status bar
    const topStatus = document.getElementById('topStatus');
    if (topStatus) {
      const stageName = player.stage_name || '初入星流';
      const wls = s.worldLineShift || 0;
      const chVal = s.channelHeat || 0;
      const heatLabel = chVal >= 70 ? '高热度' : chVal >= 40 ? '升温中' : '观测中';
      const now = new Date();
      const dateStr = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;
      topStatus.innerHTML = `${dateStr} | ${stageName} | 偏移 ${wls} | ${heatLabel}`;
    }

    Storage.cacheState(player);
  },

  // ===== Log Stream (center) =====
  logSeen: new Set(),
  _logIdx: 0,

  addLog(message, type, options = {}) {
    const stream = document.getElementById('logStream');
    if (!stream) return;

    // Dedup: skip if we've seen this exact message+type combo recently
    const key = options.id || `${type || 'system'}:${message}`;
    if (this.logSeen.has(key)) return;
    this.logSeen.add(key);
    // Prune seen set periodically
    if (this.logSeen.size > 500) {
      const entries = [...this.logSeen];
      this.logSeen = new Set(entries.slice(-200));
    }

    const entry = document.createElement('div');
    const cls = type ? `log-${type}` : 'log-system';
    entry.className = `log-entry ${cls}`;

    const now = new Date();
    const time = now.toTimeString().slice(0, 8);
    entry.innerHTML = `<span class="log-time">${time}</span><span class="log-msg">${message}</span>`;
    stream.appendChild(entry);

    // Trim old entries
    while (stream.children.length > 200) {
      stream.removeChild(stream.firstChild);
    }
    stream.scrollTop = stream.scrollHeight;
  },

  // ===== Story body to log stream =====
  storyBodyToLog(chapter) {
    if (!chapter) return;
    const text = chapter.summary || chapter.content || '';
    if (!text.trim()) return;
    UI.addLog(text.replace(/\n/g, '<br>'), 'story', { id: 'chapter:' + (chapter.chapter_key || '') });
  },

  // ===== Current Event Panel (日志流下方，事件卡区域) =====
  renderCurrentEventPanel(chapter, choices, lockedChoices) {
    if (!chapter) return;

    UI.setText('sceneName', chapter.title || chapter.chapter_name || '星之流观测站');
    UI.setText('sceneLocation', chapter.location_name || '');

    const panel = document.getElementById('currentEventPanel');
    if (!panel) return;
    panel.innerHTML = '';

    // Choice type config: label, tag class, event card class
    const choiceConfig = {
      action:      { label: '行动',   tagClass: 'tag-action',      cardClass: 'event-action' },
      repeatable:  { label: '可重复', tagClass: 'tag-repeatable',  cardClass: 'event-repeatable' },
      progress:    { label: '剧情',   tagClass: 'tag-progress',    cardClass: 'event-progress' },
      decision:    { label: '决策',   tagClass: 'tag-decision',    cardClass: 'event-decision' },
      stage_final: { label: '阶段最终', tagClass: 'tag-stage-final', cardClass: 'event-stage-final' },
      locked:      { label: '锁定',   tagClass: 'tag-locked',      cardClass: 'event-locked' },
      special:     { label: '特殊',   tagClass: 'tag-progress',    cardClass: 'event-special' }
    };

    // Render available choices as event cards
    if (choices && choices.length > 0) {
      choices.forEach(ch => {
        const choiceType = ch.choice_type || 'progress';
        const cfg = choiceConfig[choiceType] || choiceConfig.progress;

        const card = document.createElement('div');
        card.className = `event-card ${cfg.cardClass}`;

        // Card header with tag + title
        let html = `<div class="event-card-header"><span class="choice-tag ${cfg.tagClass}">${cfg.label}</span>`;
        html += `<span class="event-title">${ch.is_irreversible ? '⚠ ' + ch.text : ch.text}</span></div>`;

        // Description if available
        if (ch.description || ch.summary) {
          html += `<div class="event-desc">${ch.description || ch.summary || ''}</div>`;
        }

        // Action button(s)
        html += '<div class="event-actions">';
        const btnClass = choiceType === 'stage_final' ? 'btn-event-primary' : 'btn-event';
        html += `<button class="${btnClass}" onclick="`;
        if (ch.is_irreversible) {
          html += `UI.showWarning('${(ch.warning || '这个选择不可逆转，确定要继续吗？').replace(/'/g, "\\'")}', ()=>{GameClient.handleChoice('${ch.choice_key}')})`;
        } else {
          html += `GameClient.handleChoice('${ch.choice_key}')`;
        }
        html += `">${choiceType === 'stage_final' ? '进入阶段最终选择' : (choiceType === 'decision' ? '做出决策' : '执行')}</button>`;
        html += '</div>';

        card.innerHTML = html;
        panel.appendChild(card);
      });
    }

    // Render locked choices as dimmed cards
    if (lockedChoices && lockedChoices.length > 0) {
      lockedChoices.forEach(ch => {
        const choiceType = ch.choice_type || 'locked';
        const cfg = choiceConfig[choiceType] || choiceConfig.locked;

        const card = document.createElement('div');
        card.className = 'event-card event-locked';

        let html = `<div class="event-card-header"><span class="choice-tag ${cfg.tagClass}">${cfg.label}</span>`;
        html += `<span class="event-title">${ch.text}</span></div>`;
        if (ch.locked_reason) {
          html += `<div class="event-locked-reason">${ch.locked_reason}</div>`;
        }
        html += '<div class="event-actions"><button class="btn-event" disabled>未解锁</button></div>';

        card.innerHTML = html;
        panel.appendChild(card);
      });
    }

    // If no choices at all, show placeholder
    if ((!choices || choices.length === 0) && (!lockedChoices || lockedChoices.length === 0)) {
      panel.innerHTML = '<div class="event-placeholder">暂无事件，继续探索以寻找新的剧情线索。</div>';
    }

    panel.classList.remove('hidden');
  },

  // ===== Main Action Bar =====
  renderMainActionBar(player) {
    if (!player) return;
    const s = player.stats || {};
    const stamina = s.stamina || 0;
    const maxStamina = s.maxStamina || 50;

    UI.setText('bottomLocation', player.current_location_name || player.current_location || '星之流观测站');
    UI.setText('bottomStamina', `体力 ${stamina}/${maxStamina}`);
    UI.setText('bottomCoins', `◎ ${player.coins || 0}`);

    // Story pity
    const sp = player.stage_progress || {};
    const pity = sp.storyPity || 0;
    UI.setText('masPity', `故事沉淀 ${pity}`);

    // Daily explore count
    const dailyCount = player.daily_explore_count || 0;
    UI.setText('masExploreCount', `今日探索 ${dailyCount} 次`);
  },

  // ===== Social Action Bar (static placeholder) =====
  renderSocialActionBar() {
    // Static content — rendered in HTML, nothing dynamic needed for now
  },

  showSocialPlaceholder(featureName) {
    UI.addLog(`「${featureName}」功能开发中，敬请期待。`, 'system');
  },

  // Backward-compat alias
  renderStoryInline(chapter, choices, lockedChoices) {
    // Route to new system: body goes to log, choices go to event panel
    this.storyBodyToLog(chapter);
    this.renderCurrentEventPanel(chapter, choices, lockedChoices);
  },

  // ===== Ending Inline (center) =====
  renderEnding(ending) {
    if (!ending) return;
    const el = document.getElementById('endingInline');
    if (el) el.classList.remove('hidden');
    UI.setText('endingName', ending.name);
    UI.setText('endingDesc', ending.description);
  },

  // ===== Explore Result (renders to currentEventPanel) =====
  renderExploreResult(result) {
    const panel = document.getElementById('currentEventPanel');
    if (!panel) return;
    // Clear placeholder if present; keep existing event cards
    const placeholder = panel.querySelector('.event-placeholder');
    if (placeholder) placeholder.remove();

    const r = result.result || {};
    const typeConfig = {
      story:       { label: '主线剧情', cls: 'log-story' },
      side_story:  { label: '支线剧情', cls: 'log-side-story' },
      battle:      { label: '战斗',     cls: 'log-battle' },
      elite_battle:{ label: '精英战',   cls: 'log-elite-battle' },
      boss_clue:   { label: 'Boss线索', cls: 'log-boss-clue' },
      opportunity: { label: '机遇',     cls: 'log-opportunity' },
      resource:    { label: '资源',     cls: 'log-resource' },
      hidden:      { label: '隐藏事件', cls: 'log-hidden' },
      nothing:     { label: '一无所获', cls: 'log-system' }
    };
    const rt = result.result_type || 'nothing';
    const tc = typeConfig[rt] || { label: rt, cls: '' };

    let html = '<div class="event-card event-special" style="max-width:100%;">';
    html += `<div class="event-card-header"><span class="choice-tag tag-action">${tc.label}</span>`;
    html += `<span class="event-title">${r.event_name || '探索结果'}</span></div>`;

    if (r.description) {
      html += `<div class="event-desc">${r.description}</div>`;
    }

    // Battle result
    if (rt === 'battle' || rt === 'elite_battle') {
      const b = r.battle || {};
      if (b.result) {
        const won = b.result === 'win';
        html += `<div class="event-desc" style="color:${won ? 'var(--green)' : 'var(--red)'}">`;
        html += `战斗${won ? '胜利' : '失败'} | 回合: ${b.totalRounds || 0} | 剩余HP: ${b.playerHpRemaining || 0}</div>`;
      }
    }

    // Rewards
    if (r.rewards && Object.keys(r.rewards).length > 0) {
      html += '<div class="event-actions" style="flex-wrap:wrap;">';
      const rewards = r.rewards;
      if (rewards.coins) html += `<span style="font-size:12px;color:var(--green);">+${rewards.coins}硬币</span>`;
      if (rewards.story_fragments) html += `<span style="font-size:12px;color:var(--green);">+${rewards.story_fragments}碎片</span>`;
      if (rewards.scenarioProof) html += `<span style="font-size:12px;color:var(--green);">剧本证明 +${rewards.scenarioProof}</span>`;
      if (rewards.constellationFavor) html += `<span style="font-size:12px;color:var(--green);">星座垂青 +${rewards.constellationFavor}</span>`;
      if (rewards.kingToken) html += `<span style="font-size:12px;color:var(--green);">王者印记 +${rewards.kingToken}</span>`;
      if (rewards.abyssMark) html += `<span style="font-size:12px;color:var(--green);">深渊刻痕 +${rewards.abyssMark}</span>`;
      if (rewards.finalPage) html += `<span style="font-size:12px;color:var(--green);">终章钥匙 +${rewards.finalPage}</span>`;
      if (rewards.equipment) html += `<span style="font-size:12px;color:var(--green);">装备: ${rewards.equipment}</span>`;
      if (rewards.items) html += `<span style="font-size:12px;color:var(--green);">道具: ${rewards.items.join(', ')}</span>`;
      if (rewards.exp) html += `<span style="font-size:12px;color:var(--green);">EXP +${rewards.exp}</span>`;
      if (rewards.stats) {
        for (const [k, v] of Object.entries(rewards.stats)) {
          if (v > 0) html += `<span style="font-size:12px;color:var(--green);">${UI._labelStat(k)} +${v}</span>`;
        }
      }
      html += '</div>';
    }

    // Risks
    if (r.risks && Object.keys(r.risks).length > 0) {
      html += '<div class="event-actions" style="flex-wrap:wrap;">';
      const risks = r.risks;
      if (risks.hp_loss) html += `<span style="font-size:12px;color:var(--red);">HP -${risks.hp_loss}</span>`;
      if (risks.worldLineShift) html += `<span style="font-size:12px;color:var(--red);">世界线偏移 +${risks.worldLineShift}</span>`;
      if (risks.channelHeat) html += `<span style="font-size:12px;color:var(--red);">频道热度 +${risks.channelHeat}</span>`;
      html += '</div>';
    }

    if (r.is_final) {
      html += '<div class="event-desc" style="color:var(--gold);">阶段最终剧情已触发！现在可以完成阶段最终选择了。</div>';
    }

    html += `<div class="event-desc" style="font-size:11px;color:var(--text-dim);">剩余体力: ${result.remaining_stamina}</div>`;
    html += '</div>';

    // Prepend to panel so explore result appears first
    panel.insertAdjacentHTML('afterbegin', html);
    panel.classList.remove('hidden');
  },

  // ===== Drawer System =====
  openDrawer(title, contentHTML) {
    const drawer = document.getElementById('rightDrawer');
    if (!drawer) return;
    UI.setText('drawerTitle', title);
    const body = document.getElementById('drawerBody');
    if (body) body.innerHTML = contentHTML;
    drawer.classList.remove('hidden');
    // Trigger animation
    requestAnimationFrame(() => {
      drawer.classList.add('open');
    });
  },

  closeDrawer() {
    const drawer = document.getElementById('rightDrawer');
    if (!drawer) return;
    drawer.classList.remove('open');
    // Hide after transition
    setTimeout(() => {
      drawer.classList.add('hidden');
    }, 300);
    this.clearDescriptionPanel();
  },

  // ===== Drawer: Locations (Explore) =====
  renderLocations(locations) {
    if (!locations || locations.length === 0) {
      return '<p style="text-align:center;color:var(--text-secondary);padding:32px">暂无可探索的地图，请先推进剧情解锁。</p>';
    }
    return locations.map(loc => `
      <div class="drawer-card location-card">
        <div class="drawer-card-title">${loc.name}</div>
        <div class="drawer-card-desc">${loc.description}</div>
        <div class="drawer-card-stats">
          <span>最低 Lv.${loc.min_level}</span>
          <span class="${loc.danger_level >= 7 ? 'text-danger' : 'text-safe'}">危险度 ${loc.danger_level}</span>
          <span>掉率 ${Math.round((loc.drop_rate_modifier || 1) * 100)}%</span>
        </div>
        <div class="drawer-card-actions">
          <span style="font-size:12px;color:var(--text-secondary)">体力 -5</span>
          <button class="btn-action btn-sm" onclick="GameClient.doExplore('${loc.location_key}')">探索</button>
        </div>
      </div>
    `).join('');
  },

  // ===== Drawer: Inventory =====
  renderInventory(items) {
    if (!items || items.length === 0) {
      return '<p style="text-align:center;color:var(--text-secondary);padding:32px">背包是空的，去探索获取道具吧。</p>';
    }
    return items.map(item => `
      <div class="drawer-card">
        <div class="drawer-card-title">
          <span class="rarity-${item.rarity || 'common'}">${item.name}</span>
          <span style="font-size:11px;color:var(--text-secondary);margin-left:8px;">x${item.quantity}</span>
        </div>
        <div class="drawer-card-desc">${item.description}</div>
        <div class="drawer-card-stats">
          <span>类型: ${UI._labelItemType(item.type)}</span>
          <span>稀有度: ${UI._labelRarity(item.rarity)}</span>
        </div>
        <div class="drawer-card-actions">
          <span style="font-size:12px;color:var(--text-secondary)">售价: ${item.sell_price || 0}</span>
          ${item.type === 'consumable' ? `<button class="btn-action btn-sm" onclick="GameClient.useItem('${item.item_key}')">使用</button>` : ''}
        </div>
      </div>
    `).join('');
  },

  // ===== Drawer: Equipment =====
  renderEquipment(equipped, available) {
    const slotNames = { weapon: '武器', armor: '防具', accessory: '饰品', relic: '遗物' };
    const slotOrder = ['weapon', 'armor', 'accessory', 'relic'];
    const equippedMap = {};
    (equipped || []).forEach(e => { equippedMap[e.slot] = e; });

    let html = '<div class="drawer-section-label">已装备</div><div class="equip-slots-row">';
    slotOrder.forEach(slot => {
      const eq = equippedMap[slot];
      if (eq) {
        html += `<div class="equip-slot equipped">
          <div class="slot-name">${slotNames[slot]}</div>
          <div class="item-name">${eq.name}</div>
          <button class="btn-action btn-sm btn-cancel" onclick="GameClient.unequipItem('${slot}')" style="margin-top:4px;">卸下</button>
        </div>`;
      } else {
        html += `<div class="equip-slot empty-slot">
          <div class="slot-name">${slotNames[slot]}</div>
          <span style="font-size:12px;color:var(--text-secondary);">空</span>
        </div>`;
      }
    });
    html += '</div>';

    html += '<div class="drawer-section-label" style="margin-top:16px;">可用装备</div>';
    if (!available || available.length === 0) {
      html += '<p style="color:var(--text-secondary);padding:8px;">没有可用装备。</p>';
    } else {
      html += available.map(eq => {
        const stats = eq.stats || {};
        const statsStr = Object.entries(stats).map(([k, v]) => `${UI._labelStat(k)}: ${v > 0 ? '+' : ''}${v}`).join(' | ');
        return `<div class="drawer-card">
          <div class="drawer-card-title rarity-${eq.rarity || 'common'}">${eq.name}</div>
          <div class="drawer-card-desc">${eq.description}</div>
          <div class="drawer-card-stats">
            <span>${UI._labelSlot(eq.slot)} | Lv.${eq.required_level}+</span>
            <span>${statsStr}</span>
          </div>
          <div class="drawer-card-actions">
            <span style="font-size:12px;color:${eq.can_equip ? 'var(--green)' : 'var(--red)'}">${eq.can_equip ? '可装备' : '等级不足'}</span>
            ${eq.owned && eq.can_equip ? `<button class="btn-action btn-sm" onclick="GameClient.equipItem('${eq.equipment_key}')">装备</button>` : ''}
          </div>
        </div>`;
      }).join('');
    }
    return html;
  },

  // ===== Drawer: Skills =====
  renderSkills(skills) {
    if (!skills || skills.length === 0) {
      return '<p style="text-align:center;color:var(--text-secondary);padding:32px">暂未掌握任何技能。</p>';
    }
    const typeLabels = { attack: '攻击', passive: '被动', defense: '防御', exploration: '探索', pk: 'PK', story: '剧情' };
    return skills.map(sk => `
      <div class="drawer-card" style="${sk.unlocked ? '' : 'opacity:0.6;'}">
        <div class="drawer-card-title rarity-${sk.rarity || 'common'}">${sk.name}</div>
        <div class="drawer-card-desc">${sk.description}</div>
        <div class="drawer-card-stats">
          <span>类型: ${typeLabels[sk.skill_type] || sk.skill_type}</span>
          <span>冷却: ${sk.cooldown}回合</span>
          <span>碎片: ${sk.required_fragments}</span>
        </div>
        <div class="drawer-card-actions">
          <span style="font-size:12px;color:${sk.unlocked ? 'var(--green)' : 'var(--text-secondary)'}">${sk.unlocked ? '已解锁' : sk.can_unlock ? '可解锁' : '未满足条件'}</span>
          ${sk.can_unlock && !sk.unlocked ? `<button class="btn-action btn-sm" onclick="GameClient.unlockSkill('${sk.skill_key}')">解锁</button>` : ''}
        </div>
      </div>
    `).join('');
  },

  // ===== Drawer: Titles =====
  renderAllTitles(titles) {
    if (!titles || titles.length === 0) {
      return '<p style="text-align:center;color:var(--text-secondary);padding:32px">暂无称号。</p>';
    }
    return titles.map(t => {
      const effects = t.effects || {};
      let effStr = '';
      if (effects.stat_modifier) effStr += Object.entries(effects.stat_modifier).map(([k, v]) => `${UI._labelStat(k)}${v > 0 ? '+' : ''}${v}`).join(' ') + ' ';
      if (effects.combat_bonus) effStr += '[战斗加成] ';
      if (effects.exploration_bonus) effStr += '[探索加成] ';
      if (effects.pk_bonus) effStr += '[PK加成] ';
      if (effects.coin_multiplier) effStr += `硬币x${effects.coin_multiplier} `;
      return `<div class="drawer-card rarity-${t.rarity || 'common'}">
        <div class="drawer-card-title rarity-${t.rarity || 'common'}">${t.name}</div>
        <div class="drawer-card-desc">${t.description}</div>
        <div class="drawer-card-stats">${effStr || '无额外效果'}</div>
        <div class="drawer-card-actions"><span style="font-size:12px;">${UI._labelRarity(t.rarity)}</span></div>
      </div>`;
    }).join('');
  },

  // ===== Drawer: PK =====
  renderPKOpponents(opponents) {
    if (!opponents || opponents.length === 0) {
      return '<p style="text-align:center;color:var(--text-secondary);padding:32px">暂无可挑战的对手。</p>';
    }
    return opponents.map(o => `
      <div class="drawer-card">
        <div class="drawer-card-title">${o.player_name}</div>
        <div class="drawer-card-stats">
          <span>Lv.${o.level}</span>
          <span>评分: ${o.rating}</span>
          <span>战力: ${o.combat_power}</span>
          <span>胜/负: ${o.wins}/${o.losses}</span>
        </div>
        <div class="drawer-card-actions">
          <button class="btn-action btn-sm" onclick="GameClient.doPK(${o.id})">挑战</button>
        </div>
      </div>
    `).join('');
  },

  renderPKRecords(records) {
    if (!records || records.length === 0) {
      return '<p style="color:var(--text-secondary);padding:8px;">暂无PK记录。</p>';
    }
    return records.map(r => {
      const change = r.rating_change || {};
      return `<div class="drawer-record">
        ${r.attacker_name} vs ${r.defender_name} | 胜者ID: ${r.winner_id}
        <span style="font-size:11px;color:var(--text-secondary);"> (评分: 攻${change.attacker_change || 0}/防${change.defender_change || 0})</span>
        <span style="font-size:11px;color:var(--text-secondary);float:right;">${r.created_at}</span>
      </div>`;
    }).join('');
  },

  renderPKResult(result) {
    const modal = document.getElementById('pkModalOverlay');
    const content = document.getElementById('pkModalContent');
    if (!modal || !content) return;
    content.innerHTML = `
      <h3 class="modal-title">PK 结果</h3>
      <div class="modal-body">
        <p>胜者: <strong style="color:var(--gold)">${result.winner_name}</strong></p>
        <p>败者: <strong>${result.loser_name}</strong></p>
        <p>评分变化: 攻击方 ${result.rating_change?.attacker || 0} | 防御方 ${result.rating_change?.defender || 0}</p>
        <p>回合数: ${result.battle_log?.total_rounds || 0}</p>
      </div>
      <div class="modal-actions">
        <button class="btn-action" onclick="document.getElementById('pkModalOverlay').classList.add('hidden');GameClient.loadPK();">确定</button>
      </div>
    `;
    modal.classList.remove('hidden');
  },

  // ===== Drawer: Rankings =====
  renderRankings(rankings) {
    if (!rankings || rankings.length === 0) {
      return '<p style="text-align:center;color:var(--text-secondary);padding:32px">暂无排行数据。</p>';
    }
    let html = '<table class="drawer-table"><thead><tr><th>#</th><th>玩家</th><th>Lv</th><th>评分</th><th>胜</th><th>负</th></tr></thead><tbody>';
    html += rankings.map((r, i) => `
      <tr>
        <td class="${i < 3 ? 'rank-top' : ''}">${r.rank || i + 1}</td>
        <td>${r.player_name}</td>
        <td>${r.level}</td>
        <td>${r.rating}</td>
        <td>${r.wins}</td>
        <td>${r.losses}</td>
      </tr>
    `).join('');
    html += '</tbody></table>';
    return html;
  },

  // ===== Drawer: Stage Panel =====
  renderStagePanel(status) {
    if (!status || !status.chapters || status.chapters.length === 0) {
      return '<p style="text-align:center;color:var(--text-secondary);padding:32px">暂无阶段信息。</p>';
    }

    const currentChapter = status.chapters.find(c => c.status === 'current');
    const nextChapter = status.chapters.find(c => c.status === 'awaiting_advance' || c.status === 'ready_to_advance');

    let html = '';

    if (currentChapter) {
      html += `<div class="drawer-section-label">当前阶段</div>`;
      html += `<div class="drawer-card current-stage">
        <div class="drawer-card-title">第${currentChapter.order_index}阶段: ${currentChapter.chapter_name}</div>
        <div class="drawer-card-desc">${currentChapter.description}</div>`;
      const storyNodes = currentChapter.story_chapter_keys || [];
      if (storyNodes.length > 0) {
        html += `<div class="stage-nodes">${storyNodes.map(n => `<span class="stage-node">${n}</span>`).join(' ')}</div>`;
      }
      html += '</div>';
    }

    if (nextChapter) {
      html += `<div class="drawer-section-label" style="margin-top:16px;">下一阶段</div>`;
      if (nextChapter.status === 'ready_to_advance') {
        html += `<div class="drawer-card advance-ready">
          <div class="drawer-card-title">${nextChapter.chapter_name}</div>
          <div class="drawer-card-desc">${nextChapter.description}</div>
          <p style="color:var(--green);margin-top:4px;">所有条件已满足！</p>
          ${nextChapter.advance_text ? `<p style="font-style:italic;color:var(--gold);">"${nextChapter.advance_text}"</p>` : ''}
          <div class="drawer-card-actions">
            <button class="btn-action" onclick="GameClient.doStageAdvance('${nextChapter.chapter_key}')">进入下一阶段</button>
          </div>
        </div>`;
      } else {
        const missing = nextChapter.missing_requirements || [];
        const reqLines = missing.map(r => {
          if (r.type === 'resource') return `${r.label}: ${r.current}/${r.required}`;
          if (r.type === 'level') return `等级: ${r.current}/${r.required}`;
          if (r.type === 'boss_kill') return `击败: ${r.boss_name}`;
          if (r.type === 'flag') return `条件: ${r.flag}`;
          if (r.type === 'title') return `称号: ${r.title}`;
          return r.label;
        }).join(' | ');
        html += `<div class="drawer-card advance-locked">
          <div class="drawer-card-title">${nextChapter.chapter_name}</div>
          <div class="drawer-card-desc">${nextChapter.description}</div>
          <p style="color:var(--red);margin-top:4px;">缺少: ${reqLines || '未知条件'}</p>
        </div>`;
      }
    }

    // Exploration progress
    if (status.exploration_progress) {
      const ep = status.exploration_progress;
      html += `<div class="drawer-section-label" style="margin-top:16px;">探索进度</div>`;
      html += `<div class="stage-resources">
        <span>主线剧情: ${(ep.storyEventsTriggered || []).length}</span>
        <span>Boss线索: ${Object.keys(ep.bossClues || {}).length}</span>
        <span>机遇: ${(ep.opportunityEventsTriggered || []).length}</span>
        <span>保底: ${ep.storyPity || 0}/5</span>
      </div>`;
    }

    // Resources
    if (status.resources) {
      const res = status.resources;
      const labels = { storyFragments: '碎片', scenarioProof: '证明', constellationFavor: '垂青', kingToken: '王印', abyssMark: '深渊', finalPage: '钥匙' };
      html += `<div class="drawer-section-label" style="margin-top:16px;">持有资源</div>`;
      html += '<div class="stage-resources">';
      html += Object.entries(labels).map(([k, label]) =>
        `<span>${label}: ${res[k] || 0}</span>`
      ).join(' ');
      html += '</div>';
    }

    return html;
  },

  // ===== Drawer: Broadcast (星流放送) =====
  renderBroadcast(activeEvents, history) {
    let html = '';

    // Active events
    html += '<div class="drawer-section-label">当前放送</div>';
    if (!activeEvents || activeEvents.length === 0) {
      html += '<p style="color:var(--text-secondary);padding:8px;">当前星流安静，暂无临时剧本。</p>';
    } else {
      const event = activeEvents[0];
      const objectives = event.progress ? event.progress.objectives : [];
      const timeLeft = event.end_time ? Math.max(0, Math.floor((new Date(event.end_time) - Date.now()) / 60000)) : 0;

      html += `<div class="drawer-card broadcast-active">
        <div class="broadcast-header">
          <span class="broadcast-type-tag broadcast-type-${event.event_type}">${UI._labelBroadcastEventType(event.event_type)}</span>
          <span class="broadcast-title">${event.title}</span>
          <span style="font-size:12px;color:var(--text-secondary);">剩余 ${timeLeft} 分钟</span>
        </div>
        <p style="margin-top:8px;">${event.description || ''}</p>
        ${objectives.map(o => `
          <div class="broadcast-obj">
            <span>${o.label}</span>
            <div class="contribution-bar"><div class="contribution-fill" style="width:${Math.round((o.progress || 0) * 100)}%"></div></div>
            <span>${o.current || 0} / ${o.target}</span>
          </div>
        `).join('')}
        ${event.progress ? `<p style="font-size:12px;color:var(--text-secondary);">参与: ${event.progress.totalParticipants || 0} 人</p>` : ''}
        <div style="margin-top:12px;display:flex;gap:8px;">
          <button class="btn-action btn-sm" onclick="GameClient.doJoinBroadcast(${event.id})">参加</button>
          <button class="btn-action btn-sm" onclick="GameClient.doClaimBroadcastReward(${event.id})">领奖</button>
        </div>
      </div>`;

      // Progress & ranking containers
      html += '<div id="broadcastProgress" class="hidden"></div>';
      html += '<div id="broadcastRanking" class="hidden"></div>';
      html += '<div id="myContribution" class="hidden"></div>';
    }

    // History
    html += '<div class="drawer-section-label" style="margin-top:16px;">历史放送</div>';
    if (!history || history.length === 0) {
      html += '<p style="color:var(--text-secondary);padding:8px;">暂无历史记录。</p>';
    } else {
      const statusLabels = { completed: '已完成', failed: '失败', expired: '已过期', rewarded: '已发奖', cancelled: '已取消' };
      const typeLabels = { world_boss: '世界Boss', exploration_drive: '探索驱动', story_hunt: '剧情狩猎', pk_tournament: 'PK锦标赛', faction_conflict: '阵营冲突', disaster: '灾厄', opportunity_rain: '机遇放送', stage_support: '阶段支援' };
      html += history.map(e => `
        <div class="drawer-record">
          <span class="broadcast-type-tag broadcast-type-${e.event_type}">${typeLabels[e.event_type] || e.event_type}</span>
          <strong>${e.title}</strong>
          <span style="color:var(--text-secondary);float:right;">${statusLabels[e.status] || e.status}</span>
        </div>
      `).join('');
    }

    return html;
  },

  renderBroadcastProgress(progress) {
    const container = document.getElementById('broadcastProgress');
    if (!container || !progress || !progress.data) return;
    container.classList.remove('hidden');
    const data = progress.data;
    container.innerHTML = `
      <div class="drawer-section-label">全服进度</div>
      ${data.objectives.map(o => `
        <div class="broadcast-obj">
          <span>${o.label}</span>
          <div class="contribution-bar"><div class="contribution-fill" style="width:${Math.round((o.progress || 0) * 100)}%"></div></div>
          <span>${o.current || 0} / ${o.target}</span>
        </div>
      `).join('')}
      <p style="font-size:12px;color:var(--text-secondary);">参与: ${data.totalParticipants || 0} | ${data.allObjectivesMet ? '目标已达成' : '进行中'}</p>
    `;
  },

  renderContributionRanking(ranking) {
    const container = document.getElementById('broadcastRanking');
    if (!container || !ranking || !ranking.data || ranking.data.length === 0) return;
    container.classList.remove('hidden');
    container.innerHTML = `
      <div class="drawer-section-label">贡献排行</div>
      ${ranking.data.map(r => `
        <div class="ranking-row">
          <span style="font-weight:bold;width:30px;">#${r.rank}</span>
          <span style="flex:1;">${r.playerName}</span>
          <span style="color:var(--gold);">${r.score} 分</span>
        </div>
      `).join('')}
    `;
  },

  renderMyContribution(data) {
    const container = document.getElementById('myContribution');
    if (!container || !data || !data.data) {
      if (container) container.classList.add('hidden');
      return;
    }
    container.classList.remove('hidden');
    const d = data.data;
    container.innerHTML = `
      <div class="drawer-section-label">我的贡献</div>
      <p>贡献分: <span style="color:var(--gold);">${d.score || 0}</span></p>
      <p style="font-size:12px;color:var(--text-secondary);">奖励: ${d.claimedReward === 'none' ? '未领取' : '已领取: ' + d.claimedReward}</p>
    `;
  },

  // ===== Nav Highlight =====
  highlightNav(featureName) {
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    const item = document.querySelector(`.nav-item[data-feature="${featureName}"]`);
    if (item) item.classList.add('active');
  },

  // ===== Warning Modal =====
  showWarning(message, onConfirm) {
    const overlay = document.getElementById('warningOverlay');
    overlay.classList.remove('hidden');
    document.getElementById('warningMessage').textContent = message;
    document.getElementById('warningConfirm').onclick = () => {
      overlay.classList.add('hidden');
      if (onConfirm) onConfirm();
    };
    document.getElementById('warningCancel').onclick = () => {
      overlay.classList.add('hidden');
    };
  },

  showError(message) {
    UI.addLog(message, 'warning');
    const panel = document.getElementById('currentEventPanel');
    if (panel) {
      panel.innerHTML = `<div class="event-card event-special" style="max-width:100%;"><div class="event-desc" style="color:var(--red);text-align:center;">${message}</div></div>`;
    }
  },

  // ===== Helpers =====
  setText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
  },

  setBar(id, pct) {
    const el = document.getElementById(id);
    if (el) el.style.width = pct + '%';
  },

  show(id) {
    document.getElementById(id)?.classList.remove('hidden');
  },

  hide(id) {
    document.getElementById(id)?.classList.add('hidden');
  },

  hideModal() {
    UI.hide('modalOverlay');
  },

  // ===== Story Popup =====
  _currentMapLocation: null,

  showStoryPopup(chapter, choices, lockedChoices, player) {
    const overlay = document.getElementById('storyPopupOverlay');
    if (!overlay || !chapter) return;

    UI.setText('popupChapterTitle', chapter.title || chapter.chapter_name || '剧情');

    const narrative = document.getElementById('popupNarrative');
    if (narrative) {
      const text = chapter.summary || chapter.content || '';
      narrative.innerHTML = text ? text.replace(/\n/g, '<br>') : '<em style="color:var(--text-dim);">...</em>';
    }

    this._renderPopupChoices(choices, lockedChoices, chapter.chapter_consumed);
    document.getElementById('popupStatGains')?.classList.add('hidden');
    overlay.classList.remove('hidden');
  },

  _renderPopupChoices(choices, lockedChoices, chapterConsumed) {
    const container = document.getElementById('popupChoices');
    if (!container) return;
    container.innerHTML = '';

    const tagConfig = {
      action:      { label: '调查', cls: 'tag-action' },
      repeatable:  { label: '调查', cls: 'tag-repeatable' },
      progress:    { label: '剧情推进', cls: 'tag-progress' },
      decision:    { label: '决策', cls: 'tag-decision' },
      stage_final: { label: '阶段最终', cls: 'tag-stage-final' }
    };
    const cardClassMap = {
      action: 'card-action', repeatable: 'card-repeatable', progress: 'card-progress',
      decision: 'card-decision', stage_final: 'card-stage-final'
    };

    // Available choices
    if (choices && choices.length > 0) {
      choices.forEach(ch => {
        const cType = ch.choice_type || 'progress';
        const tag = tagConfig[cType] || tagConfig.progress;
        const cardCls = cardClassMap[cType] || 'card-progress';

        const card = document.createElement('div');
        card.className = `popup-choice-card ${cardCls}`;
        card.onclick = () => {
          if (ch.is_irreversible) {
            UI.showWarning(
              (ch.warning || '这个选择不可逆转，确定要继续吗？').replace(/'/g, "\\'"),
              () => { GameClient.handleChoiceFromPopup(ch.choice_key); }
            );
          } else {
            GameClient.handleChoiceFromPopup(ch.choice_key);
          }
        };

        card.innerHTML = `
          <span class="choice-tag ${tag.cls}">${tag.label}</span>
          <span class="choice-text">${ch.is_irreversible ? '⚠ ' + ch.text : ch.text}</span>
          <span class="choice-arrow">▶</span>
        `;
        container.appendChild(card);
      });
    }

    // Locked choices
    if (lockedChoices && lockedChoices.length > 0) {
      lockedChoices.forEach(ch => {
        const cType = ch.choice_type || 'locked';
        const tag = tagConfig[cType] || { label: '锁定', cls: 'tag-locked' };

        const card = document.createElement('div');
        card.className = 'popup-choice-card locked';
        let html = `<span class="choice-tag ${tag.cls}">${tag.label}</span>`;
        html += `<span class="choice-text">${ch.text}`;
        if (ch.locked_reason) {
          html += `<div class="choice-desc">${ch.locked_reason}</div>`;
        }
        html += '</span>';
        card.innerHTML = html;
        container.appendChild(card);
      });
    }

    this._updatePopupContinueBtn(choices, lockedChoices, chapterConsumed);
  },

  _updatePopupContinueBtn(choices, lockedChoices, chapterConsumed) {
    const btn = document.getElementById('popupContinueBtn');
    if (!btn) return;
    const hasAvailable = choices && choices.length > 0;
    if (chapterConsumed) {
      btn.textContent = '完成本章';
      btn.classList.remove('hidden');
    } else if (!hasAvailable) {
      btn.textContent = '继续';
      btn.classList.remove('hidden');
    } else {
      btn.classList.add('hidden');
    }
  },

  showStatGainsInPopup(gains) {
    const container = document.getElementById('popupStatGains');
    if (!container || !gains) return;
    container.innerHTML = '';
    container.classList.remove('hidden');

    const addItem = (text, cls) => {
      const el = document.createElement('span');
      el.className = `stat-gain-item ${cls}`;
      el.textContent = text;
      container.appendChild(el);
    };

    if (gains.coins) addItem(`◎ 硬币 +${gains.coins}`, 'coin-gain');
    if (gains.story_fragments) addItem(`◆ 碎片 +${gains.story_fragments}`, 'fragment-gain');
    if (gains.exp) addItem(`EXP +${gains.exp}`, 'attr-gain');

    if (gains.stats) {
      for (const [k, v] of Object.entries(gains.stats)) {
        if (v > 0) addItem(`${UI._labelStat(k)} +${v}`, 'attr-gain');
      }
    }
    if (gains.equipment) addItem(`装备: ${gains.equipment}`, 'attr-gain');
    if (gains.items && gains.items.length > 0) addItem(`道具: ${gains.items.join(', ')}`, 'attr-gain');

    // Auto-hide after 3s
    clearTimeout(this._statGainTimer);
    this._statGainTimer = setTimeout(() => {
      container.classList.add('hidden');
    }, 3000);
  },

  refreshStoryPopup(chapter, choices, lockedChoices) {
    if (!chapter) return;
    UI.setText('popupChapterTitle', chapter.title || chapter.chapter_name || '剧情');
    const narrative = document.getElementById('popupNarrative');
    if (narrative) {
      const text = chapter.summary || chapter.content || '';
      narrative.innerHTML = text ? text.replace(/\n/g, '<br>') : '<em style="color:var(--text-dim);">...</em>';
    }
    this._renderPopupChoices(choices, lockedChoices, chapter.chapter_consumed);
  },

  // ===== Explore Popup =====
  showExplorePopup(result) {
    const overlay = document.getElementById('explorePopupOverlay');
    if (!overlay) return;

    const typeConfig = {
      story:       { label: '主线剧情', cls: 'log-story' },
      side_story:  { label: '支线剧情', cls: 'log-side-story' },
      battle:      { label: '战斗',     cls: 'log-battle' },
      elite_battle:{ label: '精英战',   cls: 'log-elite-battle' },
      boss_clue:   { label: 'Boss线索', cls: 'log-boss-clue' },
      opportunity: { label: '机遇',     cls: 'log-opportunity' },
      resource:    { label: '资源',     cls: 'log-resource' },
      hidden:      { label: '隐藏事件', cls: 'log-hidden' },
      nothing:     { label: '一无所获', cls: 'log-system' }
    };
    const rt = result.result_type || 'nothing';
    const tc = typeConfig[rt] || { label: rt, cls: '' };
    const r = result.result || {};

    UI.setText('exploreEventType', tc.label);

    const body = document.getElementById('explorePopupBody');
    if (!body) return;
    let html = '';

    if (r.description) {
      html += `<div class="popup-narrative">${r.description}</div>`;
    }

    // Battle result
    if (rt === 'battle' || rt === 'elite_battle') {
      const b = r.battle || {};
      if (b.result) {
        const won = b.result === 'win';
        html += `<div class="popup-narrative" style="border-left-color:${won ? 'var(--green)' : 'var(--red)'};color:${won ? 'var(--green)' : 'var(--red)'};">`;
        html += `战斗${won ? '胜利' : '失败'} | 回合: ${b.totalRounds || 0} | 剩余HP: ${b.playerHpRemaining || 0}</div>`;
      }
    }

    // Rewards
    if (r.rewards && Object.keys(r.rewards).length > 0) {
      html += '<div class="popup-stat-gains" style="display:flex;">';
      const rewards = r.rewards;
      if (rewards.coins) html += `<span class="stat-gain-item coin-gain">◎ 硬币 +${rewards.coins}</span>`;
      if (rewards.story_fragments) html += `<span class="stat-gain-item fragment-gain">◆ 碎片 +${rewards.story_fragments}</span>`;
      if (rewards.exp) html += `<span class="stat-gain-item attr-gain">EXP +${rewards.exp}</span>`;
      if (rewards.equipment) html += `<span class="stat-gain-item attr-gain">装备: ${rewards.equipment}</span>`;
      if (rewards.items) html += `<span class="stat-gain-item attr-gain">道具: ${rewards.items.join(', ')}</span>`;
      if (rewards.stats) {
        for (const [k, v] of Object.entries(rewards.stats)) {
          if (v > 0) html += `<span class="stat-gain-item attr-gain">${UI._labelStat(k)} +${v}</span>`;
        }
      }
      html += '</div>';
    }

    // Risks
    if (r.risks && Object.keys(r.risks).length > 0) {
      html += '<div class="popup-stat-gains" style="display:flex;">';
      if (r.risks.hp_loss) html += `<span style="font-size:12px;color:var(--red);">HP -${r.risks.hp_loss}</span>`;
      if (r.risks.worldLineShift) html += `<span style="font-size:12px;color:var(--red);">世界线偏移 +${r.risks.worldLineShift}</span>`;
      if (r.risks.channelHeat) html += `<span style="font-size:12px;color:var(--red);">频道热度 +${r.risks.channelHeat}</span>`;
      html += '</div>';
    }

    if (r.is_final) {
      html += '<p style="text-align:center;color:var(--gold);margin-top:8px;">阶段最终剧情已触发！</p>';
    }
    if (result.chapter_advanced && result.new_chapter_key) {
      html += `<p style="text-align:center;color:var(--gold);margin-top:8px;">剧情推进至: ${result.new_chapter_name || result.new_chapter_key}</p>`;
    }

    html += `<p style="text-align:center;font-size:11px;color:var(--text-dim);margin-top:8px;">剩余体力: ${result.remaining_stamina}</p>`;
    body.innerHTML = html;
    overlay.classList.remove('hidden');
  },

  // ===== Combat Encounter Popup =====
  showCombatPopup(result) {
    const overlay = document.getElementById('combatPopupOverlay');
    if (!overlay) return;

    const r = result.result || {};
    const m = r.monster || {};
    const p = r.playerPower || {};
    const isElite = r.is_elite;

    UI.setText('combatEventType', isElite ? '精英遭遇' : '战斗遭遇');

    const spdDiff = (p.spd || 10) - (m.speed || 10);
    const firstLabel = spdDiff >= 0 ? '玩家先手' : '怪物先手';
    const firstCls = spdDiff >= 0 ? 'text-safe' : 'text-danger';

    let html = `
      <div class="combat-compare">
        <div class="combat-col player-col">
          <div class="combat-col-title">玩家</div>
          <div class="combat-stat-line"><span class="cs-label">等级</span><span class="cs-val">Lv.${p.level || 1}</span></div>
          <div class="combat-stat-line"><span class="cs-label">HP</span><span class="cs-val">${p.hp || 100}</span></div>
          <div class="combat-stat-line"><span class="cs-label">攻击</span><span class="cs-val">${p.atk || 10}</span></div>
          <div class="combat-stat-line"><span class="cs-label">防御</span><span class="cs-val">${p.def || 5}</span></div>
          <div class="combat-stat-line"><span class="cs-label">速度</span><span class="cs-val">${p.spd || 10}</span></div>
          <div class="combat-stat-line"><span class="cs-label">暴击</span><span class="cs-val">${Math.round((p.critRate || 0.05) * 100)}%</span></div>
        </div>
        <div class="combat-vs">VS</div>
        <div class="combat-col monster-col">
          <div class="combat-col-title">${m.name || '???'}</div>
          <div class="combat-stat-line"><span class="cs-label">等级</span><span class="cs-val">Lv.${m.level || 1}</span></div>
          <div class="combat-stat-line"><span class="cs-label">HP</span><span class="cs-val">${m.hp || 0}</span></div>
          <div class="combat-stat-line"><span class="cs-label">攻击</span><span class="cs-val">${m.attack || 0}</span></div>
          <div class="combat-stat-line"><span class="cs-label">防御</span><span class="cs-val">${m.defense || 0}</span></div>
          <div class="combat-stat-line"><span class="cs-label">速度</span><span class="cs-val">${m.speed || 0}</span></div>
          <div class="combat-stat-line"><span class="cs-label">暴击</span><span class="cs-val">5%</span></div>
        </div>
      </div>
      <p class="combat-first-label ${firstCls}">${firstLabel}</p>
      <div class="combat-actions">
        <button class="combat-action-btn fight-btn" onclick="GameClient.doCombatAction('${r.monster_key}', 'fight')">
          <span class="combat-btn-icon">⚔</span>
          <span>战斗</span>
        </button>
        <button class="combat-action-btn flee-btn" onclick="GameClient.doCombatAction('${r.monster_key}', 'flee')">
          <span class="combat-btn-icon">🏃</span>
          <span>逃跑</span>
          <span class="combat-rate">${Math.round(Math.min(90, Math.max(10, 30 + spdDiff * 5)))}%</span>
        </button>
        <button class="combat-action-btn support-btn" onclick="GameClient.doCombatAction('${r.monster_key}', 'support')">
          <span class="combat-btn-icon">🤝</span>
          <span>请求支援</span>
          <span class="combat-rate">${Math.round(Math.min(80, Math.max(20, (GameClient._bond || 0) * 8)))}%</span>
        </button>
      </div>
    `;

    document.getElementById('combatPopupBody').innerHTML = html;
    overlay.classList.remove('hidden');

    // Store coinMultiplier for elite bonus
    this._combatCoinMultiplier = r.coinMultiplier || 1;
    this._combatIsElite = !!isElite;
  },

  showCombatResult(data) {
    const body = document.getElementById('combatPopupBody');
    if (!body) return;

    const badge = data.success
      ? '<span class="choice-tag tag-action">成功</span>'
      : '<span class="choice-tag tag-locked">失败</span>';

    let html = `<div style="text-align:center;padding:16px 0;">${badge}<p style="margin-top:8px;font-size:15px;">${data.message || ''}</p>`;

    if (data.damage_taken) {
      html += `<p style="color:var(--red);margin-top:4px;">受到 ${data.damage_taken} 点伤害</p>`;
    }

    // Battle results
    if (data.battle) {
      const b = data.battle;
      const won = b.result === 'win';
      html += `<div class="combat-compare" style="margin-top:12px;">
        <div class="combat-col"><div class="combat-col-title">结果</div>
          <div style="font-size:18px;color:${won ? 'var(--green)' : 'var(--red)'};margin-top:8px;">${won ? '胜利' : '失败'}</div>
          <div style="font-size:12px;color:var(--text-dim);">回合: ${b.totalRounds || 0}</div>
          <div style="font-size:12px;">剩余HP: ${b.playerHpRemaining || 0}</div>
        </div>
      </div>`;

      if (b.rewards && won) {
        const rw = b.rewards;
        html += '<div class="popup-stat-gains" style="display:flex;justify-content:center;margin-top:8px;">';
        if (rw.exp) html += `<span class="stat-gain-item attr-gain">EXP +${rw.exp}</span>`;
        if (rw.coins > 0) html += `<span class="stat-gain-item coin-gain">◎ +${rw.coins}</span>`;
        if (rw.story_fragments) html += `<span class="stat-gain-item fragment-gain">◆ +${rw.story_fragments}</span>`;
        if (rw.items && rw.items.length > 0) html += `<span class="stat-gain-item attr-gain">道具: ${rw.items.join(', ')}</span>`;
        html += '</div>';
      } else if (b.rewards && b.rewards.coins < 0) {
        html += `<p style="color:var(--red);margin-top:8px;">硬币 ${b.rewards.coins}</p>`;
      }
    }

    html += '</div>';
    html += '<div style="text-align:center;margin-top:12px;"><button class="popup-continue-btn" onclick="GameClient.finishCombat()">继续</button></div>';

    body.innerHTML = html;
  },

  // ===== World Map Overlay =====
  showMapOverlay(locations, player) {
    const overlay = document.getElementById('mapOverlay');
    if (!overlay) return;

    const body = document.getElementById('mapBody');
    if (!body) return;

    const currentLoc = player ? (player.current_location || '') : '';
    if (!this._currentMapLocation) {
      this._currentMapLocation = currentLoc;
    }

    if (!locations || locations.length === 0) {
      body.innerHTML = '<p style="text-align:center;color:var(--text-dim);padding:32px;">暂无可用的地图节点。推进剧情以解锁更多区域。</p>';
    } else {
      body.innerHTML = locations.map(loc => {
        const isCurrent = loc.location_key === this._currentMapLocation;
        const isVisited = currentLoc === loc.location_key || isCurrent;
        let cls = 'map-location-node';
        if (isCurrent) cls += ' current';
        else if (isVisited) cls += ' visited';

        return `
          <div class="${cls}" onclick="GameClient.travelToLocation('${loc.location_key}')">
            <div class="loc-name">${loc.name}</div>
            <div class="loc-desc">${loc.description || ''}</div>
            <div class="loc-meta">
              <span>Lv.${loc.min_level || 1}+</span>
              <span>危险度 ${loc.danger_level || 0}</span>
            </div>
            ${isCurrent ? '<span class="loc-current-badge">当前位置</span>' : ''}
            ${isVisited && !isCurrent ? '<span class="loc-visited-badge">已探索</span>' : ''}
          </div>
        `;
      }).join('');
    }

    UI.setText('mapCurrentLoc', this._currentMapLocation || '未选择');
    overlay.classList.remove('hidden');
  },

  // ===== Detailed Stats in Drawer =====
  renderDetailedStats(player) {
    if (!player) return '<p style="text-align:center;color:var(--text-dim);padding:32px;">暂无角色数据。</p>';
    const s = player.stats || {};
    let html = '';

    // Combat attributes
    const allocatedAtk = s.allocatedAtk || 0;
    const allocatedDef = s.allocatedDef || 0;
    const allocatedSpd = s.allocatedSpd || 0;
    const allocatedCrit = s.allocatedCrit || 0;
    const freePoints = s.freePoints || 0;
    const totalAtk = (s.attack || 10) + allocatedAtk;
    const totalDef = (s.defense || 5) + allocatedDef;
    const totalSpd = (s.speed || 10) + allocatedSpd;
    const totalCrit = Math.round(((s.critRate || 0.05) + allocatedCrit * 0.02) * 100);

    html += '<div class="detailed-stats-section">';
    html += '<div class="ds-section-label">战斗属性</div>';
    html += '<div class="ds-attr-grid">';
    html += `<div class="ds-attr-item"><span class="ds-attr-key">Lv.</span><span class="ds-attr-val">${s.level || 1}</span></div>`;
    html += `<div class="ds-attr-item"><span class="ds-attr-key">攻击</span><span class="ds-attr-val">${totalAtk}</span><span class="ds-attr-sub">(+${allocatedAtk})</span></div>`;
    html += `<div class="ds-attr-item"><span class="ds-attr-key">防御</span><span class="ds-attr-val">${totalDef}</span><span class="ds-attr-sub">(+${allocatedDef})</span></div>`;
    html += `<div class="ds-attr-item"><span class="ds-attr-key">速度</span><span class="ds-attr-val">${totalSpd}</span><span class="ds-attr-sub">(+${allocatedSpd})</span></div>`;
    html += `<div class="ds-attr-item"><span class="ds-attr-key">暴击</span><span class="ds-attr-val">${totalCrit}%</span><span class="ds-attr-sub">(+${allocatedCrit * 2}%)</span></div>`;
    html += `<div class="ds-attr-item"><span class="ds-attr-key">评分</span><span class="ds-attr-val">${s.rating || 1000}</span></div>`;
    html += '</div>';

    // Free attribute points allocation
    html += `<div class="ds-section-label" style="margin-top:12px;">自由属性 <span style="color:var(--gold);">剩余: ${freePoints}</span></div>`;
    if (freePoints > 0) {
      html += '<div class="alloc-grid">';
      const allocRow = (label, id, key) => `
        <div class="alloc-row">
          <span class="alloc-label">${label}</span>
          <button class="alloc-btn" onclick="UI._adjustAlloc('${id}', -1)">-</button>
          <input class="alloc-input alloc-editable" id="${id}" type="number" value="0" min="0" max="${freePoints}" oninput="UI._validateAlloc(this)">
          <button class="alloc-btn" onclick="UI._adjustAlloc('${id}', 1)">+</button>
        </div>`;
      html += allocRow('攻击', 'allocAtk', 'atk');
      html += allocRow('防御', 'allocDef', 'def');
      html += allocRow('速度', 'allocSpd', 'spd');
      html += allocRow('暴击(2%)', 'allocCrit', 'crit');
      html += '</div>';
      html += `<button class="btn-action" onclick="GameClient.allocatePoints()" style="margin-top:8px;width:100%;">确认分配</button>`;
    }
    html += '</div>';

    // Story attributes
    html += '<div class="detailed-stats-section">';
    html += '<div class="ds-section-label">剧情能力</div>';
    html += '<div class="ds-attr-grid ds-attr-grid-3">';
    html += `<div class="ds-attr-item"><span class="ds-attr-key">智慧</span><span class="ds-attr-val">${s.intelligence || 0}</span></div>`;
    html += `<div class="ds-attr-item"><span class="ds-attr-key">战斗</span><span class="ds-attr-val">${s.combat || 0}</span></div>`;
    html += `<div class="ds-attr-item"><span class="ds-attr-key">领导</span><span class="ds-attr-val">${s.leadership || 0}</span></div>`;
    html += `<div class="ds-attr-item"><span class="ds-attr-key">羁绊</span><span class="ds-attr-val">${s.bond || 0}</span></div>`;
    html += `<div class="ds-attr-item"><span class="ds-attr-key">残酷</span><span class="ds-attr-val">${s.cruelty || 0}</span></div>`;
    html += `<div class="ds-attr-item"><span class="ds-attr-key">洞察</span><span class="ds-attr-val">${s.insight || 0}</span></div>`;
    html += '</div></div>';

    // EXP bar
    html += '<div class="detailed-stats-section">';
    html += '<div class="ds-section-label">经验值</div>';
    const exp = s.exp || 0;
    const expNeeded = Math.max(1, (s.level || 1) * 100);
    html += `<div class="stat-bar-row"><span class="stat-bar-label">EXP</span><div class="stat-bar-track"><div class="stat-bar-fill exp-fill" style="width:${Math.min(100, (exp/expNeeded)*100)}%"></div></div><span class="stat-bar-val">${exp}/${expNeeded}</span></div>`;
    html += '</div>';

    // World state
    html += '<div class="detailed-stats-section">';
    html += '<div class="ds-section-label">世界线状态</div>';
    const wls = s.worldLineShift || 0;
    const ch = s.channelHeat || 0;
    const chCls = ch >= 70 ? 'ds-world-danger' : ch >= 40 ? 'ds-world-warn' : '';
    html += `<div class="ds-world-row"><span>世界线偏移</span><span class="ds-world-val">${wls}</span></div>`;
    html += `<div class="ds-world-row"><span>频道热度</span><span class="ds-world-val ${chCls}">${ch}</span></div>`;
    html += '</div>';

    // Resources
    html += '<div class="detailed-stats-section">';
    html += '<div class="ds-section-label">持有资源</div>';
    html += '<div class="ds-res-grid">';
    const res = player.breakthrough_resources || {};
    html += `<div class="ds-res-item"><span class="ds-res-icon">◎</span><span class="ds-res-val">${player.coins || 0}</span><span class="ds-res-key">硬币</span></div>`;
    html += `<div class="ds-res-item"><span class="ds-res-icon">◆</span><span class="ds-res-val">${player.story_fragments || 0}</span><span class="ds-res-key">故事碎片</span></div>`;
    html += `<div class="ds-res-item"><span class="ds-res-icon">◇</span><span class="ds-res-val">${res.scenarioProof || 0}</span><span class="ds-res-key">剧本证明</span></div>`;
    html += `<div class="ds-res-item"><span class="ds-res-icon">☆</span><span class="ds-res-val">${res.constellationFavor || 0}</span><span class="ds-res-key">星座垂青</span></div>`;
    html += `<div class="ds-res-item"><span class="ds-res-icon">♛</span><span class="ds-res-val">${res.kingToken || 0}</span><span class="ds-res-key">王者印记</span></div>`;
    html += `<div class="ds-res-item"><span class="ds-res-icon">⬡</span><span class="ds-res-val">${res.abyssMark || 0}</span><span class="ds-res-key">深渊刻痕</span></div>`;
    html += `<div class="ds-res-item"><span class="ds-res-icon">◈</span><span class="ds-res-val">${res.finalPage || 0}</span><span class="ds-res-key">终章钥匙</span></div>`;
    html += '</div></div>';

    // Equipment
    html += '<div class="detailed-stats-section">';
    html += '<div class="ds-section-label">当前装备</div>';
    const eq = player.equipment || {};
    const slots = { weapon: '武器', armor: '防具', accessory: '饰品', relic: '遗物' };
    let eqHtml = '';
    for (const [slot, label] of Object.entries(slots)) {
      const item = eq[slot];
      eqHtml += `<div class="ds-equip-line"><span class="ds-equip-slot">${label}</span><span class="ds-equip-name">${item && item.name ? item.name : '—'}</span></div>`;
    }
    html += eqHtml || '<span class="ds-obj-none">未装备</span>';
    html += '</div>';

    // Stage objectives
    const sp = player.stage_progress || {};
    html += '<div class="detailed-stats-section">';
    html += '<div class="ds-section-label">阶段进度</div>';
    html += `<span class="ds-obj-item">剧情事件: ${(sp.storyEventsTriggered || []).length}</span>`;
    html += `<span class="ds-obj-item">Boss线索: ${Object.keys(sp.bossClues || {}).length}</span>`;
    html += `<span class="ds-obj-item">机遇事件: ${(sp.opportunityEventsTriggered || []).length}</span>`;
    html += `<span class="ds-obj-item">保底计数: ${sp.storyPity || 0}/5</span>`;
    const objectives = player.stage_objectives || [];
    if (objectives.length > 0) {
      html += objectives.map(o => `<span class="ds-obj-item">目标: ${o}</span>`).join('');
    }
    html += '</div>';

    return html;
  },

  getVal(id) {
    return document.getElementById(id)?.value || '';
  },

  // ===== Label Helpers =====
  _labelStat(k) { return LABELS.stat[k] || k; },
  _labelRarity(k) { return LABELS.rarity[k] || k; },
  _labelSlot(k) { return LABELS.slot[k] || k; },
  _labelEventType(k) { return LABELS.eventType[k] || k; },
  _labelBroadcastStatus(k) { return LABELS.broadcastStatus[k] || k; },
  _labelBroadcastEventType(k) { return LABELS.broadcastEventType[k] || k; },
  _labelSkillType(k) { return LABELS.skillType[k] || k; },
  _labelItemType(k) { return LABELS.itemType[k] || k; },
  _labelChoiceType(k) { return LABELS.choiceType[k] || k; },

  _adjustAlloc(inputId, delta) {
    const input = document.getElementById(inputId);
    if (!input) return;
    let val = parseInt(input.value) || 0;
    const newVal = val + delta;
    if (newVal < 0) return;

    // Sum all allocation inputs
    const ids = ['allocAtk', 'allocDef', 'allocSpd', 'allocCrit'];
    let currentTotal = 0;
    ids.forEach(id => {
      if (id === inputId) return;
      currentTotal += parseInt(document.getElementById(id)?.value) || 0;
    });
    currentTotal += newVal;

    // Read freePoints from the label text
    const label = document.querySelector('.ds-section-label span');
    if (!label) return;
    const match = label.textContent.match(/剩余:\s*(\d+)/);
    const maxPoints = match ? parseInt(match[1]) : 0;

    if (currentTotal <= maxPoints) {
      input.value = newVal;
    }
  },

  _validateAlloc(input) {
    const val = parseInt(input.value) || 0;
    if (val < 0) { input.value = 0; return; }

    const ids = ['allocAtk', 'allocDef', 'allocSpd', 'allocCrit'];
    let total = 0;
    ids.forEach(id => {
      total += parseInt(document.getElementById(id)?.value) || 0;
    });

    const label = document.querySelector('.ds-section-label span');
    const match = label ? label.textContent.match(/剩余:\s*(\d+)/) : null;
    const maxPoints = match ? parseInt(match[1]) : 0;

    const allInputs = ids.map(id => document.getElementById(id)).filter(Boolean);
    if (total > maxPoints) {
      allInputs.forEach(el => { el.style.borderColor = 'var(--red)'; el.style.boxShadow = '0 0 6px var(--red-dim)'; });
    } else {
      allInputs.forEach(el => { el.style.borderColor = ''; el.style.boxShadow = ''; });
    }
  },

  // ===== Constellation Picker =====
  showConstellationPicker(constellations) {
    const overlay = document.getElementById('constellationPopupOverlay');
    const body = document.getElementById('constellationPopupBody');
    if (!overlay || !body) return;

    const effectLabels = {
      atk: '攻击', def: '防御', spd: '速度', critRate: '暴击率', critDamage: '暴击伤害',
      luck: '幸运', dropRate: '掉落率', insight: '洞察', bond: '羁绊',
      reviveDiscount: '复活折扣', coinMultiplier: '硬币倍率'
    };

    body.innerHTML = `
      <p style="text-align:center;color:var(--text-dim);margin-bottom:12px;">
        在灭亡的世界中，背后星（星座）是你唯一的赞助者。<br>选择一位背后星，获得它的庇佑。<strong>此选择不可更改。</strong>
      </p>
      <div class="constellation-grid">
        ${constellations.map(c => {
          const effStr = Object.entries(c.effects).map(([k, v]) => {
            const label = effectLabels[k] || k;
            const sign = v > 0 ? '+' : '';
            if (k === 'reviveDiscount') return `${label}: ${Math.round(v * 100)}%`;
            if (k === 'coinMultiplier') return `${label}: +${Math.round(v * 100)}%`;
            if (k === 'critRate') return `${label}: +${Math.round(v * 100)}%`;
            return `${label} ${sign}${v}`;
          }).join(' | ');

          return `
          <div class="constellation-card" onclick="GameClient.pickConstellation('${c.key}')">
            <div class="constellation-card-name">${c.title}</div>
            <div class="constellation-card-sub">${c.name}</div>
            <div class="constellation-card-desc">${c.description}</div>
            <div class="constellation-card-effects">${effStr}</div>
          </div>`;
        }).join('')}
      </div>
    `;
    overlay.classList.remove('hidden');
  },

  // ===== Underworld Revival =====
  showUnderworldPopup(player) {
    const overlay = document.getElementById('underworldPopupOverlay');
    const body = document.getElementById('underworldPopupBody');
    if (!overlay || !body) return;

    const s = player.stats || {};
    const level = s.level || 1;
    const isQueen = s.constellation === 'queen_of_underworld';
    const coinCost = Math.round(100 * level * (isQueen ? 0.5 : 1));
    const titles = player.titles || [];
    const hasTitles = titles.length > 0;
    const lastTitle = hasTitles ? titles[titles.length - 1] : '';

    body.innerHTML = `
      <div class="underworld-scene">
        <p class="underworld-narrative">
          你的意识沉入无尽的黑暗之中...<br><br>
          当你再次睁开眼，发现自己站在一片灰暗的平原上。远处，一座巍峨的宫殿矗立于冥河之畔。<br><br>
          一位身披黑袍、头戴冠冕的女性身影缓缓降下——<strong style="color:var(--purple);">冥界女王·珀耳塞福涅</strong>。<br><br>
          <em>"读者，你的故事尚未完结。但死亡已为你敞开了冥界之门。"</em><br>
          <em>"若你想重返人间，需付出相应的代价。这是冥界的法则。"</em>
        </p>

        <div class="underworld-options">
          <div class="underworld-option-card" onclick="GameClient.doRevive('coins')">
            <div class="uo-title">💰 支付金币</div>
            <div class="uo-cost">${coinCost} 枚金币</div>
            <div class="uo-desc">向冥界女王支付金币，赎回你的灵魂。${isQueen ? '<br><span style="color:var(--gold);">（冥界女王的眷顾：费用减半）</span>' : ''}</div>
            <div class="uo-availability ${player.coins >= coinCost ? 'uo-can-afford' : 'uo-cannot-afford'}">
              ${player.coins >= coinCost ? `持有: ${player.coins} 金币 ✓` : `持有: ${player.coins} 金币 ✗`}
            </div>
          </div>

          <div class="underworld-option-card ${!hasTitles ? 'uo-disabled' : ''}" onclick="${hasTitles ? `GameClient.doRevive('title')` : ''}">
            <div class="uo-title">🏆 献祭称号</div>
            <div class="uo-cost">献祭称号「${hasTitles ? lastTitle : '无称号可献祭'}」</div>
            <div class="uo-desc">献祭一个传说级称号作为回归的代价，冥界女王将放回你的灵魂。</div>
            <div class="uo-availability ${hasTitles ? 'uo-can-afford' : 'uo-cannot-afford'}">
              ${hasTitles ? `可献祭称号: ${titles.length} 个 ✓` : '无称号可献祭 ✗'}
            </div>
          </div>
        </div>
      </div>
    `;
    overlay.classList.remove('hidden');
  },

  dismissUnderworld() {
    document.getElementById('underworldPopupOverlay')?.classList.add('hidden');
  },

  escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  },

  // ===== Settings Panel =====
  renderSettings() {
    var currentSettings = UI._loadAllSettings();
    var html = '';

    html += '<div class="drawer-section-label">显示设置</div>';

    // Text brightness
    html += '<div class="settings-group">';
    html += '<span class="settings-label">文字亮度</span>';
    html += '<span class="settings-desc">调整剧情文字的显示亮度（50%-150%）</span>';
    html += '<div style="display:flex;align-items:center;gap:8px;">';
    html += '<input type="range" class="settings-range" id="settingBrightness" min="50" max="150" value="' + currentSettings.textBrightness + '" oninput="UI._onSettingChange(\'textBrightness\', this.value)" style="flex:1;">';
    html += '<span class="settings-range-value" id="settingBrightnessVal">' + currentSettings.textBrightness + '%</span>';
    html += '</div></div>';

    // Font weight
    html += '<div class="settings-group">';
    html += '<span class="settings-label">字体粗细</span>';
    html += '<div class="settings-row">';
    html += '<span class="settings-desc" style="margin:0;">使用粗体显示剧情文字</span>';
    html += '<label class="settings-toggle">';
    html += '<input type="checkbox" id="settingFontBold" ' + (currentSettings.fontWeight === 'bold' ? 'checked' : '') + ' onchange="UI._onSettingChange(\'fontWeight\', this.checked ? \'bold\' : \'normal\')">';
    html += '<span class="settings-toggle-slider"></span>';
    html += '</label>';
    html += '</div></div>';

    // Font family
    html += '<div class="settings-group">';
    html += '<span class="settings-label">字体选择</span>';
    html += '<span class="settings-desc">仅影响剧情文字显示</span>';
    html += '<select class="settings-select" id="settingFontFamily" onchange="UI._onSettingChange(\'fontFamily\', this.value)" style="width:100%;">';
    var fonts = [
      { value: 'default', label: '默认系统' },
      { value: 'song', label: '宋体' },
      { value: 'kai', label: '楷体' },
      { value: 'yahei', label: '微软雅黑' }
    ];
    fonts.forEach(function(f) {
      html += '<option value="' + f.value + '"' + (currentSettings.fontFamily === f.value ? ' selected' : '') + '>' + f.label + '</option>';
    });
    html += '</select></div>';

    // Day/Night mode
    html += '<div class="settings-group">';
    html += '<span class="settings-label">日间模式</span>';
    html += '<div class="settings-row">';
    html += '<span class="settings-desc" style="margin:0;">切换明亮/暗黑主题</span>';
    html += '<label class="settings-toggle">';
    html += '<input type="checkbox" id="settingDayMode" ' + (currentSettings.dayMode ? 'checked' : '') + ' onchange="UI._onSettingChange(\'dayMode\', this.checked)">';
    html += '<span class="settings-toggle-slider"></span>';
    html += '</label>';
    html += '</div></div>';

    // Gameplay
    html += '<div class="drawer-section-label">游戏设置</div>';

    // Skip story popup
    html += '<div class="settings-group">';
    html += '<span class="settings-label">探索后跳过剧情弹窗</span>';
    html += '<span class="settings-desc">探索后如无可用选项，不弹出剧情窗口，改用阶段指示器显示进度</span>';
    html += '<div class="settings-row">';
    html += '<span class="settings-desc" style="margin:0;">默认开启</span>';
    html += '<label class="settings-toggle">';
    html += '<input type="checkbox" id="settingSkipStoryPopup" ' + (currentSettings.skipStoryPopup !== false ? 'checked' : '') + ' onchange="UI._onSettingChange(\'skipStoryPopup\', this.checked)">';
    html += '<span class="settings-toggle-slider"></span>';
    html += '</label>';
    html += '</div></div>';

    // Account
    html += '<div class="drawer-section-label">账号设置</div>';
    html += '<div style="padding:0;">';
    html += '<div style="margin-bottom:12px;font-size:14px;">当前账号：<strong>' + UI.escapeHtml(GameClient._currentUser ? GameClient._currentUser.username : '未知') + '</strong></div>';
    html += '<button class="btn-action" onclick="GameClient.doLogout()" style="background:#a33;">退出登录</button>';
    html += '</div>';

    return html;
  },

  // ===== Stage Indicator =====
  renderStageIndicator(player) {
    var el = document.getElementById('stageIndicator');
    var textEl = document.getElementById('stageIndicatorText');
    if (!el || !textEl) return;
    if (!player || !player.stage_name) {
      el.classList.add('hidden');
      return;
    }
    var order = player.stage_order || 1;
    var name = player.stage_name;
    var visited = player.stage_visited_nodes || 0;
    var total = player.stage_total_nodes || 0;
    textEl.textContent = '第' + order + '章 · ' + name + ' | 已体验 ' + visited + '/' + total + ' 剧情节点 | 探索推进中...';
    el.classList.remove('hidden');
  },

  // ── Settings persistence ──
  _loadAllSettings() {
    return {
      textBrightness: parseInt(localStorage.getItem('game_textBrightness') || '100'),
      fontWeight: localStorage.getItem('game_fontWeight') || 'normal',
      fontFamily: localStorage.getItem('game_fontFamily') || 'default',
      dayMode: localStorage.getItem('game_dayMode') === 'true',
      skipStoryPopup: localStorage.getItem('game_skipStoryPopup') !== 'false'
    };
  },

  _onSettingChange(key, value) {
    localStorage.setItem('game_' + key, String(value));
    switch (key) {
      case 'textBrightness':
        UI._applyTextBrightness(parseInt(value));
        var valEl = document.getElementById('settingBrightnessVal');
        if (valEl) valEl.textContent = value + '%';
        break;
      case 'fontWeight':
        UI._applyFontWeight(value);
        break;
      case 'fontFamily':
        UI._applyFontFamily(value);
        break;
      case 'dayMode':
        UI._applyDayMode(value === true || value === 'true');
        break;
    }
  },

  applyAllDisplaySettings() {
    var s = UI._loadAllSettings();
    UI._applyTextBrightness(s.textBrightness);
    UI._applyFontWeight(s.fontWeight);
    UI._applyFontFamily(s.fontFamily);
    UI._applyDayMode(s.dayMode);
  },

  _applyTextBrightness(value) {
    var pct = Math.max(50, Math.min(150, value)) / 100;
    var stream = document.getElementById('logStream');
    if (stream) stream.style.filter = 'brightness(' + pct + ')';
    var els = document.querySelectorAll('.popup-narrative, .popup-body');
    els.forEach(function(el) { el.style.filter = 'brightness(' + pct + ')'; });
  },

  _applyFontWeight(weight) {
    if (weight === 'bold') {
      document.body.classList.add('font-bold');
    } else {
      document.body.classList.remove('font-bold');
    }
  },

  _applyFontFamily(value) {
    document.body.classList.remove('font-family-song', 'font-family-kai', 'font-family-yahei');
    if (value && value !== 'default') {
      document.body.classList.add('font-family-' + value);
    }
  },

  _applyDayMode(enabled) {
    if (enabled) {
      document.body.classList.add('light-theme');
    } else {
      document.body.classList.remove('light-theme');
    }
  },

  // ===== Description Panel =====
  _descriptionPanelVisible: true,
  _currentPanelContext: null,

  updateDescriptionPanel(context, contentHTML) {
    this._currentPanelContext = context;
    var panel = document.getElementById('descriptionPanel');
    var body = document.getElementById('descPanelBody');
    var title = document.getElementById('descPanelTitle');
    if (!panel || !body) return;

    if (contentHTML) {
      var titles = {
        stats: '属性指南', equipment: '装备说明',
        combat: '敌人图鉴', explore: '区域情报',
        constellation: '星座宝典'
      };
      if (title) title.textContent = titles[context] || '观察笔记';
      body.innerHTML = contentHTML;
      panel.classList.remove('hidden');
    }
  },

  clearDescriptionPanel() {
    var panel = document.getElementById('descriptionPanel');
    if (panel) panel.classList.add('hidden');
    this._currentPanelContext = null;
  },

  toggleDescriptionPanel() {
    var panel = document.getElementById('descriptionPanel');
    var btn = document.querySelector('.desc-panel-collapse');
    if (!panel) return;
    if (panel.classList.contains('collapsed')) {
      panel.classList.remove('collapsed');
      if (btn) btn.textContent = '◀';
    } else {
      panel.classList.add('collapsed');
      if (btn) btn.textContent = '▶';
    }
  },

  renderDescriptionPanel(player, context) {
    if (!player || !player.stats) return;
    if (context === 'stats') {
      this.updateDescriptionPanel('stats', this._renderStatsDesc(player));
    } else if (context === 'equipment') {
      this.updateDescriptionPanel('equipment', this._renderEquipmentDesc(player));
    } else if (context === 'combat') {
      this.updateDescriptionPanel('combat', this._renderCombatDesc(player));
    } else if (context === 'constellation') {
      this.updateDescriptionPanel('constellation', this._renderConstellationLore(player));
    }
  },

  _renderStatsDesc(player) {
    var s = player.stats || {};
    var conKey = s.constellation;
    var html = '';

    // Build recommendation based on constellation
    var builds = {
      golden_sun: { primary: '攻击', secondary: '暴击', tip: '金乌眷顾高攻击高暴击的化身。灼热的光芒将贯穿一切防御。优先将点数分配给攻击和暴击。' },
      black_flame_dragon: { primary: '攻击', secondary: '速度', tip: '黑炎龙崇尚纯粹的攻击力。毁灭即是你的本质——防御自然偏低，以速度抢占先机。' },
      abyss_eye: { primary: '暴击', secondary: '攻击', tip: '深渊之眼赋予你看穿万物弱点的能力。暴击率是你的核心属性，精准命中要害。' },
      wheel_of_fate: { primary: '速度', secondary: '攻击', tip: '命运之轮眷顾灵活的化身。高速度确保先手，幸运提升掉落。在命运转动之前出击。' },
      queen_of_underworld: { primary: '防御', secondary: '攻击', tip: '冥界女王保护自己的信徒。高防御让你在持久战中屹立不倒，死亡也不再是终点。' },
      star_stream_watcher: { primary: '均衡', secondary: '攻击', tip: '星流观测者给予均衡的庇佑。各项属性均衡分配，侧重攻击即可。硬币获得量亦有提升。' }
    };

    var b = builds[conKey];
    if (b) {
      html += '<div class="desc-section-title">背后星指引</div>';
      html += '<div class="desc-tip">' + b.tip + '</div>';
      html += '<div class="desc-stat-row"><span class="desc-stat-key">推荐优先</span><span class="desc-stat-val">' + b.primary + '</span></div>';
      html += '<div class="desc-stat-row"><span class="desc-stat-key">次要属性</span><span class="desc-stat-val">' + b.secondary + '</span></div>';
    }

    html += '<div class="desc-section-title">属性说明</div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">攻击</span><span class="desc-stat-val">决定伤害输出</span></div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">防御</span><span class="desc-stat-val">减少受到的伤害</span></div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">速度</span><span class="desc-stat-val">决定出手顺序</span></div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">暴击(2%/点)</span><span class="desc-stat-val">增加暴击率</span></div>';

    // Low stat warning
    var lv = s.level || 1;
    var threshold = lv * 2;
    var totalAtk = (s.attack || 10) + (s.allocatedAtk || 0);
    var totalDef = (s.defense || 5) + (s.allocatedDef || 0);
    var totalSpd = (s.speed || 10) + (s.allocatedSpd || 0);
    var warnings = [];
    if (totalAtk < threshold) warnings.push('攻击');
    if (totalDef < threshold) warnings.push('防御');
    if (totalSpd < threshold) warnings.push('速度');
    if (warnings.length > 0) {
      html += '<div class="desc-section-title">⚠ 属性警告</div>';
      html += '<div class="desc-warning">以下属性低于推荐值（等级×2=' + threshold + '）：' + warnings.join('、') + '。当前阶段的敌人可能对你造成严重威胁。</div>';
    }

    return html;
  },

  _renderEquipmentDesc(player) {
    var html = '';
    html += '<div class="desc-section-title">装备栏位说明</div>';
    html += '<div class="desc-lore-text">装备是你在末日中生存的关键。每个栏位提供不同类型的属性加成。</div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">武器</span><span class="desc-stat-val">主要伤害来源</span></div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">防具</span><span class="desc-stat-val">提供防御与生命</span></div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">饰品</span><span class="desc-stat-val">均衡属性加成</span></div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">遗物</span><span class="desc-stat-val">稀有独特效果</span></div>';
    html += '<div class="desc-section-title">获取方式</div>';
    html += '<div class="desc-tip">通过探索、击败Boss、完成星流放送活动获取装备。高稀有度装备在后期探索中有更高几率掉落。</div>';
    return html;
  },

  _renderCombatDesc(player) {
    var html = '';
    html += '<div class="desc-section-title">战斗指南</div>';
    html += '<div class="desc-tip">战斗为回合制自动进行。攻击力决定输出，防御力减少伤害，速度决定先手。暴击造成1.5倍伤害。</div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">先手判定</span><span class="desc-stat-val">速度高者优先</span></div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">暴击机制</span><span class="desc-stat-val">暴击率×1.5倍伤害</span></div>';
    return html;
  },

  _renderConstellationLore(player) {
    var s = player.stats || {};
    var conKey = s.constellation;
    var lore = {
      golden_sun: { name: '烈日之金乌', story: '古老太阳的化身。在远古的星座战争中，金乌曾以一头三足乌鸦的形态降临，焚烧了整个入侵的异界。如今它已沉默数千年，只在极少数值得"燃烧"的读者面前展露光芒。它不关心正义或邪恶——它只关心你的名字能燃烧得多明亮。' },
      black_flame_dragon: { name: '深渊黑炎龙', story: '栖息于世界最深渊处的黑色巨龙。它的龙息能焚尽万物，它的鳞片比任何金属都坚硬。在无数世界线中，黑炎龙只眷顾那些敢于孤身面对毁灭的化身。毁灭即是新生——这是它唯一的信条。' },
      abyss_eye: { name: '全知深渊眼', story: '悬挂在世界线缝隙中的巨大眼睛。它注视一切，洞察一切。传说深渊之眼是一位陨落的古神遗留的最后感官，它寻找着能够"看穿故事真相"的读者。被它眷顾的人，能够看到敌人最脆弱的瞬间。' },
      wheel_of_fate: { name: '因果命运轮', story: '转动于因果之间的巨轮，刻满了无数世界线的走向。命运之轮没有意志，只有规律。它眷顾那些理解"概率"的人——在正确的时间出现在正确的地点，这就是最强的能力。' },
      queen_of_underworld: { name: '冥界之女王', story: '掌管冥界的高贵女王，万千亡魂的主宰。她并非冷酷无情——恰恰相反，她深知生命的价值。在她的眷顾下，死亡的边界变得模糊。冥界女王庇佑那些愿意为他人牺牲的灵魂。' },
      star_stream_watcher: { name: '星流观测者', story: '默默守护星流的古老存在，无人知晓它的真名。它不追求战争的胜利，不渴望荣耀的传播——它只是静静观看星流的流动，偶尔对那些有趣的读者投去一抹微光。均衡是它的哲学，持久是它的力量。' }
    };

    var l = lore[conKey];
    var html = '';
    if (l) {
      html += '<div class="desc-section-title">' + l.name + '</div>';
      html += '<div class="desc-lore-text">' + l.story + '</div>';
    }

    html += '<div class="desc-section-title">星座体系</div>';
    html += '<div class="desc-lore-text">在星流之中，星座由人类集体意识中的故事孕育而生。故事越知名，星座越强大。星座赞助化身（人类），化身传播星座的大名——这是一场横跨无数世界线的"故事交易"。</div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">传奇星座</span><span class="desc-stat-val">神话传说之主</span></div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">叙事级</span><span class="desc-stat-val">有一定知名度</span></div>';
    html += '<div class="desc-stat-row"><span class="desc-stat-key">普通星座</span><span class="desc-stat-val">小型传说</span></div>';
    html += '<div class="desc-section-title">星云</div>';
    html += '<div class="desc-tip">同一阵营的星座组成星云：奥林匹斯（希腊）、阿斯加德（北欧）、伊甸园（圣经）、吠陀（印度）等。星座之间的战争不仅取决于力量，更取决于"故事的相性"。</div>';

    return html;
  }
};
