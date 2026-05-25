<script setup lang="ts">
import { ref, computed } from 'vue';

import SceneMapPanel from '@/components/scene-map/SceneMapPanel.vue';

const leftPanelWidth = ref(240);
const rightPanelWidth = ref(72);

// mock player data
const mockPlayer = {
  playerName: '金独子',
  avatarRank: 'C',
  currentLocation: '金湖站',
  stats: { hp: 85, maxHp: 100, stamina: 62, maxStamina: 80, exp: 450, maxExp: 1000, attack: 48, defense: 32, speed: 25 },
  coins: 1280,
};

const isMobile = ref(window.innerWidth <= 760);
window.addEventListener('resize', () => { isMobile.value = window.innerWidth <= 760; });

const frameGridStyle = computed(() => {
  if (isMobile.value) return undefined;
  return { gridTemplateColumns: `${leftPanelWidth.value}px 8px minmax(0, 1fr) 8px ${rightPanelWidth.value}px` };
});
</script>

<template>
  <div class="ling-shell">
    <!-- 顶栏 — 和真实GameLayout一致 -->
    <header class="ling-topbar">
      <div class="ling-top-left">
        <h1 class="ling-logo">星流</h1>
        <span class="ling-pill ling-realm">C级·精英</span>
        <button class="ling-core-btn active">场景地图</button>
        <button class="ling-core-btn">背包</button>
        <button class="ling-core-btn">装备</button>
        <button class="ling-core-btn">技能</button>
        <button class="ling-core-btn">同伴</button>
        <button class="ling-core-btn">阵营</button>
        <button class="ling-core-btn">星座</button>
        <button class="ling-core-btn">设置</button>
      </div>
    </header>

    <!-- 广播条 — 模拟 -->
    <div class="ling-broadcast-strip" style="border-bottom:1px solid #1e2a33;padding:3px 16px;font-size:11px;color:#4a5a5d;background:#0c1318;">
      📡 星流广播 · 星座"深渊的黑焰龙"正在观战 · 全服事件：灭世后的第三日
    </div>

    <!-- 三栏布局 -->
    <div class="ling-frame" :style="frameGridStyle">
      <!-- 左侧玩家面板 — 模拟 -->
      <aside class="ling-left" :style="isMobile ? undefined : { width: leftPanelWidth + 'px' }">
        <div style="padding:12px;overflow-y:auto;height:100%;">
          <div style="display:flex;align-items:center;gap:10px;margin-bottom:12px;padding-bottom:8px;border-bottom:1px solid #1a242b;">
            <span style="font-size:32px;">🦸</span>
            <div>
              <div style="font-weight:700;font-size:15px;">{{ mockPlayer.playerName }}</div>
              <div style="font-size:11px;color:#7ec4a1;">C级 · 精英</div>
            </div>
          </div>
          <div v-for="bar in [
            {label:'HP', pct:85, color:'#d97b6c', val:'85/100'},
            {label:'体力', pct:77, color:'#8db8d8', val:'62/80'},
            {label:'EXP', pct:45, color:'#caa86a', val:'450/1000'},
          ]" :key="bar.label" style="display:flex;align-items:center;gap:6px;margin-bottom:4px;font-size:10px;">
            <span style="width:24px;color:#7e9292;">{{ bar.label }}</span>
            <div style="flex:1;height:5px;background:#1a242b;border-radius:2px;overflow:hidden;">
              <div :style="{width:bar.pct+'%',height:'100%',background:bar.color,borderRadius:'2px'}"></div>
            </div>
            <span style="width:44px;text-align:right;color:#7e9292;">{{ bar.val }}</span>
          </div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:4px;margin-top:8px;font-size:11px;color:#7e9292;">
            <div>⚔ ATK <b style="color:#c9d8d5;">48</b></div>
            <div>🛡 DEF <b style="color:#c9d8d5;">32</b></div>
            <div>💨 SPD <b style="color:#c9d8d5;">25</b></div>
            <div>💰 币 <b style="color:#c9d8d5;">1,280</b></div>
          </div>
          <div style="margin-top:14px;font-size:10px;color:#7e9292;">
            <div style="margin-bottom:3px;">📍 当前位置</div>
            <div style="color:#caa86a;font-size:11px;padding:2px 8px;border:1px solid #2a3a25;border-radius:2px;display:inline-block;background:rgba(202,168,106,0.05);">金湖站 · 第一卷</div>
          </div>
        </div>
      </aside>

      <!-- 拖拽手柄 -->
      <div v-if="!isMobile" class="ling-resize-handle"></div>

      <!-- 中间主区：星图 -->
      <main class="ling-main">
        <SceneMapPanel />
      </main>

      <!-- 拖拽手柄 -->
      <div v-if="!isMobile" class="ling-resize-handle"></div>

      <!-- 右侧导航 — 模拟 -->
      <nav v-if="!isMobile" style="width:72px;border-left:1px solid #1e2a33;background:#0c1318;display:flex;flex-direction:column;padding:4px 0;">
        <button style="width:100%;padding:8px 0;border:none;background:transparent;color:#caa86a;font-size:14px;cursor:pointer;">🗺</button>
        <button style="width:100%;padding:8px 0;border:none;background:transparent;color:#4a5a5d;font-size:14px;cursor:pointer;">🎒</button>
        <button style="width:100%;padding:8px 0;border:none;background:transparent;color:#4a5a5d;font-size:14px;cursor:pointer;">⚔</button>
        <button style="width:100%;padding:8px 0;border:none;background:transparent;color:#4a5a5d;font-size:14px;cursor:pointer;">📜</button>
        <button style="width:100%;padding:8px 0;border:none;background:transparent;color:#4a5a5d;font-size:14px;cursor:pointer;">👥</button>
      </nav>
    </div>

    <!-- 底部操作栏 — 和真实GameLayout一致 -->
    <footer style="display:flex;align-items:center;gap:12px;padding:6px 16px;border-top:1px solid #1e2a33;background:rgba(12,19,24,0.95);flex-shrink:0;">
      <button style="padding:4px 16px;border:1px solid #28343d;border-radius:3px;background:#0f1519;color:#7e9292;font-size:12px;cursor:pointer;">休息</button>
      <button style="padding:6px 28px;border:1px solid #caa86a;border-radius:3px;background:rgba(202,168,106,0.12);color:#caa86a;font-size:14px;font-weight:700;cursor:pointer;">进入场景</button>
      <select style="padding:3px 6px;border:1px solid #28343d;border-radius:3px;background:#0f1519;color:#7e9292;font-size:11px;">
        <option>1倍</option><option>5倍</option><option>10倍</option>
      </select>
      <label style="font-size:11px;color:#7e9292;display:flex;align-items:center;gap:4px;cursor:pointer;">
        <input type="checkbox">概率修正
      </label>
      <label style="font-size:11px;color:#7e9292;display:flex;align-items:center;gap:4px;cursor:pointer;">
        <input type="checkbox">自动行动
      </label>
    </footer>
  </div>
</template>

<style scoped>
/* 复用 GameLayout 的核心结构样式 */
.ling-shell {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  border: 3px solid #25303a;
  background: #11181d;
  color: #b8c8c6;
  font-size: 14px;
  display: flex;
  flex-direction: column;
}
.ling-topbar {
  height: 64px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #26323a;
  background: #11181d;
  padding: 0 20px;
  flex-shrink: 0;
}
.ling-top-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.ling-logo {
  margin: 0;
  color: #cba66a;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 8px;
  line-height: 1;
}
.ling-pill {
  height: 26px;
  display: inline-flex;
  align-items: center;
  border: 1px solid #2d3a42;
  border-radius: 3px;
  background: #0f1519;
  padding: 0 14px;
  font-size: 13px;
  white-space: nowrap;
  color: #7ec4a1;
}
.ling-core-btn {
  padding: 6px 14px;
  border: none;
  background: transparent;
  color: #7e9292;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
  border-radius: 3px;
}
.ling-core-btn:hover { color: #c9d8d5; }
.ling-core-btn.active { color: #caa86a; background: rgba(202,168,106,0.08); }
.ling-frame {
  flex: 1;
  display: grid;
  grid-template-columns: 240px 8px minmax(0, 1fr) 8px 72px;
  overflow: hidden;
}
.ling-left {
  overflow-y: auto;
  border-right: 1px solid #1e2a33;
  background: #0c1318;
}
.ling-main {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.ling-resize-handle {
  width: 8px;
  cursor: col-resize;
  background: transparent;
}
.ling-resize-handle:hover { background: rgba(202,168,106,0.15); }
</style>
