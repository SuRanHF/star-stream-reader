const fs = require('fs');
let c = fs.readFileSync('public/admin.html', 'utf8');

// Find the start and end of the title management block
const startMarker = '  _titlePlayerId: null,';
const endMarker = '  async loadEditTitles(playerId)';

const start = c.indexOf(startMarker);
const end = c.indexOf(endMarker);

if (start < 0 || end < 0) {
  console.log('Markers not found! start:', start, 'end:', end);
  process.exit(1);
}

const newBlock = `  _allTitles: [],
  _playerTitles: [],

  async toggleTitles() {
    var panel = document.getElementById('titlesPanel');
    if (panel.style.display === 'none') {
      panel.style.display = 'block';
      await Promise.all([this.loadPlayersForSelect(), this.loadTitles()]);
    } else {
      panel.style.display = 'none';
    }
  },

  async loadPlayersForSelect() {
    try {
      var res = await fetch('/api/admin/players?limit=500', { headers: { Authorization: 'Bearer ' + this.token } });
      var data = await res.json();
      if (data.success) {
        var sel = document.getElementById('titlePlayerSelect');
        var currentVal = sel.value;
        sel.innerHTML = '<option value="">-- 未选择 --</option>' +
          data.data.players.map(function(p) {
            return '<option value="' + p.id + '">' + Admin.esc(p.player_name) + ' (ID:' + p.id + ')</option>';
          }).join('');
        if (currentVal) sel.value = currentVal;
      }
    } catch (e) {}
  },

  async loadTitles() {
    try {
      var res = await fetch('/api/admin/titles', { headers: { Authorization: 'Bearer ' + this.token } });
      var data = await res.json();
      if (data.success) {
        this._allTitles = data.data.titles;
        document.getElementById('titlesTotal').textContent = this._allTitles.length;
        this.renderTitles();
      }
    } catch (e) {}
  },

  renderTitles() {
    var grid = document.getElementById('titlesGrid');
    var titles = this._allTitles;
    var playerTitles = this._playerTitles;
    var playerId = document.getElementById('titlePlayerSelect').value;

    if (!titles || titles.length === 0) {
      grid.innerHTML = '<div style="color:#6a6a78;padding:16px;grid-column:1/-1;">暂无称号数据</div>';
      return;
    }

    var self = this;
    var rarityColors = { common: '#8a8a9a', uncommon: '#4db8a8', rare: '#5b8fcf', epic: '#b45bcf', legendary: '#c9a860' };

    grid.innerHTML = titles.map(function(t) {
      var rc = rarityColors[t.rarity] || '#8a8a9a';
      var hasTitle = playerTitles.indexOf(t.title_key) >= 0;
      var borderColor = !playerId ? '#2a2a3a' : (hasTitle ? '#4db8a8' : '#3a3a4a');
      var bg = hasTitle ? '#0f1a17' : '#14141c';

      var effParts = [];
      if (t.effects) {
        if (t.effects.stat_modifier) Object.keys(t.effects.stat_modifier).forEach(function(k) { effParts.push(k + '+' + t.effects.stat_modifier[k]); });
        if (t.effects.combat_bonus) Object.keys(t.effects.combat_bonus).forEach(function(k) { effParts.push(k + '+' + Math.round(t.effects.combat_bonus[k]*100) + '%'); });
        if (t.effects.narrative_tags) effParts.push(t.effects.narrative_tags.join(','));
      }

      var actionBtn = '';
      if (playerId) {
        if (hasTitle) {
          actionBtn = '<button class="admin-btn danger" onclick="Admin.revokeTitle(\\'' + t.title_key + '\\')" style="font-size:10px;padding:2px 8px;margin-top:4px;">移除</button>';
        } else {
          actionBtn = '<button class="admin-btn primary" onclick="Admin.grantTitle(\\'' + t.title_key + '\\')" style="font-size:10px;padding:2px 8px;margin-top:4px;background:#4db8a8;">授予</button>';
        }
      }

      return '<div style="background:' + bg + ';border:2px solid ' + borderColor + ';border-radius:6px;padding:10px;">' +
        '<div style="display:flex;justify-content:space-between;align-items:center;">' +
          '<span style="font-weight:bold;color:' + rc + ';">' + self.esc(t.name) + '</span>' +
          '<span style="font-size:10px;color:#6a6a78;">' + t.rarity + '</span>' +
        '</div>' +
        '<div style="font-size:10px;color:#6a6a78;">' + t.title_key + '</div>' +
        '<div style="font-size:11px;color:#9a9aaa;margin:2px 0;">' + self.esc(t.description || '').substring(0, 60) + '</div>' +
        '<div style="font-size:10px;color:#5a6a7a;">' + self.esc(effParts.join(' | ') || '无效果') + '</div>' +
        (hasTitle ? '<div style="font-size:10px;color:#4db8a8;margin-top:2px;">已拥有</div>' : '') +
        actionBtn +
      '</div>';
    }).join('');
  },

  async onTitlePlayerChange() {
    var playerId = document.getElementById('titlePlayerSelect').value;
    if (!playerId) {
      this._playerTitles = [];
      this.renderTitles();
      return;
    }
    try {
      var res = await fetch('/api/admin/players/' + playerId + '/titles', { headers: { Authorization: 'Bearer ' + this.token } });
      var data = await res.json();
      if (data.success) {
        this._playerTitles = data.data.titles.map(function(t) { return t.title_key; });
        this.renderTitles();
      }
    } catch (e) {}
  },

  async grantTitle(titleKey) {
    var playerId = document.getElementById('titlePlayerSelect').value;
    if (!playerId) { alert('请先选择玩家'); return; }
    try {
      var res = await fetch('/api/admin/players/' + playerId + '/titles', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + this.token },
        body: JSON.stringify({ action: 'grant', title_key: titleKey })
      });
      var data = await res.json();
      if (data.success) {
        this._playerTitles.push(titleKey);
        this.renderTitles();
      } else {
        alert((data.error && data.error.message) || '授予失败');
      }
    } catch (e) { alert('网络错误'); }
  },

  async revokeTitle(titleKey) {
    var playerId = document.getElementById('titlePlayerSelect').value;
    if (!playerId) return;
    try {
      var res = await fetch('/api/admin/players/' + playerId + '/titles', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + this.token },
        body: JSON.stringify({ action: 'revoke', title_key: titleKey })
      });
      var data = await res.json();
      if (data.success) {
        var idx = this._playerTitles.indexOf(titleKey);
        if (idx >= 0) this._playerTitles.splice(idx, 1);
        this.renderTitles();
      } else {
        alert((data.error && data.error.message) || '移除失败');
      }
    } catch (e) { alert('网络错误'); }
  },
`;

c = c.substring(0, start) + newBlock + c.substring(end);
fs.writeFileSync('public/admin.html', c);
console.log('Title management JS replaced successfully');
