<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import StarChartMapCore from './StarChartMapCore.vue';

const router = useRouter();
const filtersCollapsed = ref(false);
const showMobilePreview = ref(false);
const selectedNodeName = ref('');

function onNodeSelect(name: string) {
  selectedNodeName.value = name;
}
</script>

<template>
  <div class="intb-root" :class="{ 'is-mobile-preview': showMobilePreview }">
    <!-- 顶部状态浮层 -->
    <header class="intb-status-bar">
      <button class="intb-back" @click="router.push('/game')">← 返回游戏</button>
      <div class="intb-player-info">
        <span class="intb-avatar">🦸</span>
        <span class="intb-name">金独子</span>
        <span class="intb-rank">C级·精英</span>
      </div>
      <div class="intb-quick-stats">
        <span class="intb-stat hp">❤ HP 85/100</span>
        <span class="intb-stat stamina">⚡ 体力 62/80</span>
        <span class="intb-stat loc">📍 金湖站</span>
      </div>
      <button class="intb-mobile-toggle" @click="showMobilePreview = !showMobilePreview">
        {{ showMobilePreview ? '🖥 桌面' : '📱 手机' }}
      </button>
    </header>

    <!-- 可折叠筛选栏 -->
    <div class="intb-filters" :class="{ collapsed: filtersCollapsed }">
      <button class="intb-filter-toggle" @click="filtersCollapsed = !filtersCollapsed">
        {{ filtersCollapsed ? '▼ 展开筛选' : '▲ 收起筛选' }}
      </button>
      <div v-if="!filtersCollapsed" class="intb-filter-row">
        <span class="intb-filter-label">卷：</span>
        <button class="intb-chip active">全部</button>
        <button class="intb-chip">第一卷</button>
        <button class="intb-chip">第二卷</button>
        <button class="intb-chip">第三卷</button>
        <button class="intb-chip">第四卷</button>
        <span class="intb-sep">|</span>
        <button class="intb-chip active">全部</button>
        <button class="intb-chip">主线</button>
        <button class="intb-chip">支线</button>
        <button class="intb-chip">Boss</button>
        <button class="intb-chip">隐藏</button>
      </div>
    </div>

    <!-- 星图主体 -->
    <main class="intb-map-area">
      <StarChartMapCore @node-select="onNodeSelect" />
    </main>

    <!-- 底部操作栏 -->
    <footer class="intb-action-bar">
      <div class="intb-current-loc">
        <span v-if="selectedNodeName">已选择：<b>{{ selectedNodeName }}</b></span>
        <span v-else>点击星图节点选择场景</span>
      </div>
      <div class="intb-actions-right">
        <button class="intb-btn-reset">🔄 重置视图</button>
        <button class="intb-btn-enter" :disabled="!selectedNodeName">
          ⚡ 传送至此处
        </button>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.intb-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: radial-gradient(ellipse at 40% 50%, #11191f 0%, #080b0d 100%);
  color: #c9d8d5;
  font-size: 13px;
}

/* ── 状态栏 ── */
.intb-status-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 16px;
  background: rgba(12, 19, 24, 0.95);
  border-bottom: 1px solid #1e2a33;
  flex-shrink: 0;
  backdrop-filter: blur(8px);
}
.intb-back {
  padding: 4px 12px;
  border: 1px solid #28343d;
  border-radius: 3px;
  background: transparent;
  color: #7e9292;
  font-size: 12px;
  cursor: pointer;
}
.intb-player-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.intb-avatar { font-size: 22px; }
.intb-name { font-weight: 700; color: #caa86a; }
.intb-rank { font-size: 11px; color: #7ec4a1; border: 1px solid #2a3a35; padding: 1px 8px; border-radius: 2px; }
.intb-quick-stats {
  display: flex;
  gap: 14px;
  margin-left: auto;
}
.intb-quick-stats .intb-stat { font-size: 12px; }
.intb-quick-stats .hp { color: #d97b6c; }
.intb-quick-stats .stamina { color: #8db8d8; }
.intb-quick-stats .loc { color: #7e9292; }
.intb-mobile-toggle {
  padding: 3px 10px;
  border: 1px solid #28343d;
  border-radius: 3px;
  background: #0f1519;
  color: #7e9292;
  font-size: 12px;
  cursor: pointer;
}

/* ── 筛选栏 ── */
.intb-filters {
  flex-shrink: 0;
  border-bottom: 1px solid #1e2a33;
  transition: all 0.25s;
}
.intb-filter-toggle {
  width: 100%;
  padding: 4px;
  border: none;
  background: #0a0e10;
  color: #4a5a5d;
  font-size: 11px;
  cursor: pointer;
}
.intb-filter-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  flex-wrap: wrap;
}
.intb-filter-label { font-size: 11px; color: #7e9292; }
.intb-chip {
  padding: 2px 10px;
  border: 1px solid #28343d;
  border-radius: 3px;
  background: #0f1519;
  color: #7e9292;
  font-size: 11px;
  cursor: pointer;
}
.intb-chip.active { border-color: #caa86a; color: #caa86a; background: rgba(202,168,106,0.08); }
.intb-sep { color: #28343d; margin: 0 4px; }

/* ── 地图 ── */
.intb-map-area {
  flex: 1;
  overflow: hidden;
  position: relative;
}

/* ── 底部操作栏 ── */
.intb-action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  border-top: 1px solid #1e2a33;
  background: rgba(12, 19, 24, 0.95);
  flex-shrink: 0;
  backdrop-filter: blur(8px);
}
.intb-current-loc { font-size: 13px; color: #7e9292; }
.intb-current-loc b { color: #caa86a; }
.intb-actions-right { display: flex; gap: 10px; }
.intb-btn-reset {
  padding: 6px 16px;
  border: 1px solid #28343d;
  border-radius: 3px;
  background: transparent;
  color: #7e9292;
  font-size: 12px;
  cursor: pointer;
}
.intb-btn-enter {
  padding: 8px 24px;
  border: 1px solid #caa86a;
  border-radius: 3px;
  background: rgba(202,168,106,0.12);
  color: #caa86a;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.intb-btn-enter:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.intb-btn-enter:not(:disabled):hover { background: rgba(202,168,106,0.22); }

/* ── 手机模拟 ── */
.intb-root.is-mobile-preview {
  max-width: 390px;
  margin: 16px auto;
  border: 2px solid #28343d;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 0 40px rgba(0,0,0,0.5);
}
.intb-root.is-mobile-preview .intb-status-bar { padding: 6px 10px; gap: 8px; }
.intb-root.is-mobile-preview .intb-quick-stats { display: none; }
.intb-root.is-mobile-preview .intb-rank { display: none; }
.intb-root.is-mobile-preview .intb-filter-row { padding: 6px 8px; gap: 3px; }
.intb-root.is-mobile-preview .intb-chip { padding: 2px 6px; font-size: 10px; }
.intb-root.is-mobile-preview .intb-action-bar { padding: 8px 12px; }
.intb-root.is-mobile-preview .intb-btn-reset { display: none; }
</style>
