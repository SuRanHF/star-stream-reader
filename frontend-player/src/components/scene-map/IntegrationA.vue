<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import StarChartMapCore from './StarChartMapCore.vue';

const router = useRouter();
const isMobile = ref(false);
const selectedNode = ref('');
const detailOpen = ref(false);

const containerStyle = computed(() => {
  if (isMobile.value) {
    return { maxWidth: '390px', margin: '0 auto', borderRadius: '12px', border: '2px solid #1e2a33' };
  }
  return {};
});

function onNodeSelect(name: string) {
  selectedNode.value = name;
  detailOpen.value = !!name;
}
</script>

<template>
  <div class="inta-root">
    <!-- 控制栏 -->
    <div class="inta-controls">
      <button class="inta-back" @click="router.push('/game')">← 返回游戏</button>
      <span class="inta-label">方案A · 响应式双模</span>
      <button class="inta-toggle" @click="isMobile = !isMobile">
        {{ isMobile ? '切换到桌面视图' : '切换到手机视图' }}
      </button>
    </div>

    <div class="inta-stage" :style="containerStyle">
      <!-- DESKTOP LAYOUT -->
      <template v-if="!isMobile">
        <div class="inta-desktop">
          <!-- 左侧玩家面板 -->
          <aside class="inta-left">
            <div class="inta-left-header">🦸 化身状态</div>
            <div class="inta-player-card">
              <div class="inta-avatar-row">
                <span class="inta-avatar-big">🦸</span>
                <div>
                  <div class="inta-pname">金独子</div>
                  <div class="inta-prank">C级 · 精英</div>
                </div>
              </div>
              <div class="inta-stat-row">
                <span class="inta-bar-label">HP</span>
                <div class="inta-bar"><div class="inta-bar-fill hp" style="width:85%"></div></div>
                <span class="inta-bar-num">85/100</span>
              </div>
              <div class="inta-stat-row">
                <span class="inta-bar-label">体力</span>
                <div class="inta-bar"><div class="inta-bar-fill stamina" style="width:77%"></div></div>
                <span class="inta-bar-num">62/80</span>
              </div>
              <div class="inta-stat-row">
                <span class="inta-bar-label">EXP</span>
                <div class="inta-bar"><div class="inta-bar-fill exp" style="width:45%"></div></div>
                <span class="inta-bar-num">450/1000</span>
              </div>
              <div class="inta-mini-grid">
                <div>⚔ ATK <b>48</b></div>
                <div>🛡 DEF <b>32</b></div>
                <div>💨 SPD <b>25</b></div>
                <div>💰 币 <b>1,280</b></div>
              </div>
            </div>
            <div class="inta-left-section">
              <div class="inta-section-title">📍 当前位置</div>
              <div class="inta-loc-badge">金湖站 · 第一卷</div>
            </div>
            <div class="inta-left-section">
              <div class="inta-section-title">🏷 已解锁场景</div>
              <div class="inta-unlock-count">8 / 20</div>
            </div>
          </aside>

          <!-- 中间星图 -->
          <main class="inta-center">
            <div class="inta-center-header">
              <span class="inta-chtitle">🗺 星流场景地图</span>
              <div class="inta-quick-filters">
                <button class="inta-qchip active">全部</button>
                <button class="inta-qchip">第一卷</button>
                <button class="inta-qchip">第二卷</button>
                <button class="inta-qchip">主线</button>
                <button class="inta-qchip">Boss</button>
              </div>
            </div>
            <StarChartMapCore @node-select="onNodeSelect" />
          </main>

          <!-- 右侧导航 -->
          <nav class="inta-right">
            <button class="inta-nav-btn active">🗺</button>
            <button class="inta-nav-btn">🎒</button>
            <button class="inta-nav-btn">⚔</button>
            <button class="inta-nav-btn">📜</button>
            <button class="inta-nav-btn">👥</button>
            <button class="inta-nav-btn">🏠</button>
          </nav>
        </div>
      </template>

      <!-- MOBILE LAYOUT -->
      <template v-else>
        <div class="inta-mobile">
          <header class="inta-mob-top">
            <button class="inta-mob-back" @click="router.push('/game')">←</button>
            <span class="inta-mob-name">金独子</span>
            <span class="inta-mob-hp">❤ 85</span>
            <span class="inta-mob-stamina">⚡ 62</span>
          </header>
          <main class="inta-mob-map">
            <StarChartMapCore @node-select="onNodeSelect" />
          </main>
          <!-- mobile bottom sheet -->
          <div v-if="detailOpen" class="inta-mob-sheet">
            <div class="inta-sheet-handle"></div>
            <div class="inta-sheet-title">{{ selectedNode }}</div>
            <div class="inta-sheet-meta">可进入 · Lv.3+ · 危险度 ★★</div>
            <button class="inta-sheet-enter">⚡ 传送至此处</button>
          </div>
          <footer class="inta-mob-bottom">
            <button class="inta-mob-act">🔄</button>
            <button class="inta-mob-act primary">⚡ 进入场景</button>
            <button class="inta-mob-act">📋</button>
          </footer>
        </div>
      </template>

      <!-- shared detail overlay -->
      <div v-if="detailOpen && !isMobile" class="inta-detail-overlay">
        <div class="inta-detail-card">
          <button class="inta-detail-close" @click="detailOpen = false">✕</button>
          <h3>{{ selectedNode }}</h3>
          <p class="inta-detail-meta">可进入 · Lv.3+ · 危险度 ★★</p>
          <p class="inta-detail-desc">场景描述信息。点击传送按钮进入此场景开始探索。</p>
          <button class="inta-detail-enter">⚡ 传送至此处</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.inta-root { display: flex; flex-direction: column; height: 100%; background: #080b0d; }
.inta-controls {
  display: flex; align-items: center; gap: 12px; padding: 8px 16px;
  border-bottom: 1px solid #1e2a33; background: #0c1318; flex-shrink: 0;
}
.inta-back {
  padding: 4px 12px; border: 1px solid #28343d; border-radius: 3px;
  background: transparent; color: #7e9292; font-size: 12px; cursor: pointer;
}
.inta-label { color: #caa86a; font-weight: 700; font-size: 13px; }
.inta-toggle {
  margin-left: auto; padding: 5px 14px; border: 1px solid #8db8d8; border-radius: 3px;
  background: rgba(141,184,216,0.08); color: #8db8d8; font-size: 12px; cursor: pointer;
}
.inta-stage { flex: 1; overflow: hidden; }

/* ── Desktop ── */
.inta-desktop { display: flex; height: 100%; }
.inta-left {
  width: 220px; min-width: 180px; border-right: 1px solid #1e2a33;
  background: #0c1318; overflow-y: auto; padding: 12px;
}
.inta-left-header { font-size: 13px; font-weight: 700; color: #caa86a; margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid #1a242b; }
.inta-player-card { margin-bottom: 14px; }
.inta-avatar-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.inta-avatar-big { font-size: 32px; }
.inta-pname { font-weight: 700; color: #c9d8d5; font-size: 15px; }
.inta-prank { font-size: 11px; color: #7ec4a1; }
.inta-stat-row { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.inta-bar-label { font-size: 10px; width: 24px; color: #7e9292; }
.inta-bar { flex: 1; height: 5px; background: #1a242b; border-radius: 2px; overflow: hidden; }
.inta-bar-fill { height: 100%; border-radius: 2px; }
.inta-bar-fill.hp { background: #d97b6c; }
.inta-bar-fill.stamina { background: #8db8d8; }
.inta-bar-fill.exp { background: #caa86a; }
.inta-bar-num { font-size: 10px; color: #7e9292; width: 50px; text-align: right; }
.inta-mini-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; margin-top: 8px; font-size: 11px; color: #7e9292; }
.inta-mini-grid b { color: #c9d8d5; }
.inta-left-section { margin-bottom: 12px; }
.inta-section-title { font-size: 11px; color: #7e9292; margin-bottom: 4px; }
.inta-loc-badge { font-size: 12px; color: #caa86a; padding: 3px 8px; border: 1px solid #2a3a25; border-radius: 3px; background: rgba(202,168,106,0.05); display: inline-block; }
.inta-unlock-count { font-size: 14px; color: #c9d8d5; font-weight: 700; }

.inta-center { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.inta-center-header { display: flex; align-items: center; justify-content: space-between; padding: 8px 14px; border-bottom: 1px solid #1e2a33; flex-shrink: 0; }
.inta-chtitle { font-weight: 700; color: #caa86a; font-size: 14px; }
.inta-quick-filters { display: flex; gap: 4px; }
.inta-qchip { padding: 2px 8px; border: 1px solid #28343d; border-radius: 2px; background: #0f1519; color: #7e9292; font-size: 10px; cursor: pointer; }
.inta-qchip.active { border-color: #caa86a; color: #caa86a; }

.inta-right { width: 40px; border-left: 1px solid #1e2a33; background: #0c1318; display: flex; flex-direction: column; padding: 4px 0; }
.inta-nav-btn { width: 100%; padding: 8px 0; border: none; background: transparent; color: #4a5a5d; font-size: 14px; cursor: pointer; }
.inta-nav-btn.active { color: #caa86a; }

/* detail overlay (desktop) */
.inta-detail-overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: flex-end; justify-content: center; z-index: 10; }
.inta-detail-card { background: #11181d; border: 1px solid #28343d; border-radius: 6px 6px 0 0; padding: 20px 24px; max-width: 500px; width: 100%; animation: slideUp 0.25s; }
@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
.inta-detail-close { float: right; border: none; background: transparent; color: #7e9292; cursor: pointer; font-size: 16px; }
.inta-detail-card h3 { margin: 0 0 6px; color: #caa86a; }
.inta-detail-meta { font-size: 12px; color: #7e9292; margin: 0 0 8px; }
.inta-detail-desc { font-size: 13px; color: #9aaca8; margin: 0 0 12px; }
.inta-detail-enter { padding: 8px 24px; border: 1px solid #caa86a; border-radius: 3px; background: rgba(202,168,106,0.12); color: #caa86a; font-size: 14px; font-weight: 700; cursor: pointer; width: 100%; }

/* ── Mobile ── */
.inta-mobile { display: flex; flex-direction: column; height: 100%; }
.inta-mob-top { display: flex; align-items: center; gap: 10px; padding: 8px 12px; background: rgba(12,19,24,0.95); border-bottom: 1px solid #1e2a33; }
.inta-mob-back { border: none; background: transparent; color: #7e9292; font-size: 18px; cursor: pointer; }
.inta-mob-name { font-weight: 700; color: #c9d8d5; font-size: 13px; }
.inta-mob-hp { font-size: 11px; color: #d97b6c; margin-left: auto; }
.inta-mob-stamina { font-size: 11px; color: #8db8d8; }
.inta-mob-map { flex: 1; overflow: hidden; }
.inta-mob-sheet { background: #11181d; border-top: 1px solid #28343d; padding: 12px 16px 16px; }
.inta-sheet-handle { width: 32px; height: 4px; background: #28343d; border-radius: 2px; margin: 0 auto 10px; }
.inta-sheet-title { font-weight: 700; color: #caa86a; font-size: 15px; margin-bottom: 4px; }
.inta-sheet-meta { font-size: 11px; color: #7e9292; margin-bottom: 10px; }
.inta-sheet-enter { width: 100%; padding: 8px; border: 1px solid #caa86a; border-radius: 3px; background: rgba(202,168,106,0.12); color: #caa86a; font-size: 13px; font-weight: 700; cursor: pointer; }
.inta-mob-bottom { display: flex; justify-content: space-around; padding: 8px 12px; border-top: 1px solid #1e2a33; background: rgba(12,19,24,0.95); }
.inta-mob-act { padding: 6px 20px; border: 1px solid #28343d; border-radius: 3px; background: #0f1519; color: #7e9292; font-size: 16px; cursor: pointer; }
.inta-mob-act.primary { border-color: #caa86a; color: #caa86a; font-size: 14px; font-weight: 700; }
</style>
