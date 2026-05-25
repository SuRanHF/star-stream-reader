<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import StarChartMapCore from './StarChartMapCore.vue';

const router = useRouter();
const mapRef = ref<InstanceType<typeof StarChartMapCore>>();
const selectedNode = ref('');
const sheetOpen = ref(false);
const leftCollapsed = ref(false);
const logExpanded = ref(false);

function onNodeSelect(name: string) {
  selectedNode.value = name;
  sheetOpen.value = !!name;
}

// Expose filter controls to parent's filter chips
function setVolume(v: number) { mapRef.value?.setFilterVolume(v); }
function setType(t: string) { mapRef.value?.setFilterType(t); }

const activeVolume = ref(0);
const activeType = ref('all');

function toggleVolume(v: number) {
  activeVolume.value = activeVolume.value === v ? 0 : v;
  setVolume(activeVolume.value);
}
function toggleType(t: string) {
  activeType.value = activeType.value === t ? 'all' : t;
  setType(activeType.value);
}
</script>

<template>
  <div class="intc-root">
    <!-- 控制栏 -->
    <div class="intc-topbar">
      <button class="intc-back" @click="router.push('/game')">← 返回</button>
      <span class="intc-brand">🗺 场景地图</span>
      <span class="intc-hint">方案C · 嵌入式</span>
      <button class="intc-toggle-left" @click="leftCollapsed = !leftCollapsed">
        {{ leftCollapsed ? '▶ 展开面板' : '◀ 折叠面板' }}
      </button>
    </div>

    <div class="intc-body">
      <!-- 左侧面板（可折叠） -->
      <aside v-if="!leftCollapsed" class="intc-left">
        <div class="intc-left-inner">
          <div class="intc-quick-player">
            <span class="intc-av">🦸</span>
            <div class="intc-pstats">
              <span class="intc-pname">金独子</span>
              <span class="intc-prank">C级 · 精英</span>
            </div>
          </div>
          <div class="intc-mini-bars">
            <div class="intc-mbar"><span>HP</span><div class="intc-mtrack"><div class="intc-mfill hp" style="width:85%"></div></div><span>85</span></div>
            <div class="intc-mbar"><span>体力</span><div class="intc-mtrack"><div class="intc-mfill sta" style="width:77%"></div></div><span>62</span></div>
          </div>
          <div class="intc-section">
            <div class="intc-sectitle">📍 当前位置</div>
            <div class="intc-loc-badge">金湖站</div>
          </div>
          <div class="intc-section">
            <div class="intc-sectitle">🔍 已探索</div>
            <div class="intc-explored">8 / 20</div>
          </div>
        </div>
      </aside>

      <!-- 中间主区 -->
      <main class="intc-main">
        <!-- 可折叠日志条 -->
        <div class="intc-log-strip" :class="{ expanded: logExpanded }" @click="logExpanded = !logExpanded">
          <span class="intc-log-icon">📜</span>
          <span class="intc-log-text" v-if="!logExpanded">最近日志：进入金湖站 · 获得星座碎片 · 击败守卫x3</span>
          <div v-else class="intc-log-full">
            <div>▶ 进入金湖站 — 发现了第一个避难所</div>
            <div>▶ 获得物品 — 星座碎片 x1</div>
            <div>▶ 战斗胜利 — 击败守卫 x3，HP -15</div>
            <div>▶ 遇到NPC — 李智慧加入了队伍</div>
          </div>
        </div>

        <!-- 水平筛选胶囊 -->
        <div class="intc-filter-strip">
          <span class="intc-flabel">卷</span>
          <button v-for="v in [1,2,3,4]" :key="'v'+v" :class="{ active: activeVolume === v }" class="intc-fchip" @click="toggleVolume(v)">第{{v}}卷</button>
          <span class="intc-fsep">|</span>
          <button v-for="t in ['主线','支线','Boss','隐藏']" :key="t" :class="{ active: activeType === t }" class="intc-fchip" @click="toggleType(t)">{{t}}</button>
        </div>

        <!-- 星图 -->
        <div class="intc-map-wrap">
          <StarChartMapCore ref="mapRef" @node-select="onNodeSelect" />
        </div>

        <!-- 底部Sheet（场景详情） -->
        <transition name="sheet">
          <div v-if="sheetOpen" class="intc-sheet">
            <div class="intc-sheet-bar">
              <span class="intc-sheet-name">📍 {{ selectedNode }}</span>
              <div class="intc-sheet-tags">
                <span class="intc-stag main">主线</span>
                <span class="intc-stag lv">Lv.3+</span>
                <span class="intc-stag danger">★★</span>
              </div>
            </div>
            <div class="intc-sheet-actions">
              <button class="intc-sbtn close" @click="sheetOpen = false">关闭</button>
              <button class="intc-sbtn track">标记追踪</button>
              <button class="intc-sbtn enter">⚡ 传送</button>
            </div>
          </div>
        </transition>
      </main>

      <!-- 右侧快捷导航（手机端隐藏） -->
      <nav class="intc-right">
        <button class="intc-rbtn active" title="场景地图">🗺</button>
        <button class="intc-rbtn" title="背包">🎒</button>
        <button class="intc-rbtn" title="装备">⚔</button>
        <button class="intc-rbtn" title="技能">📜</button>
        <button class="intc-rbtn" title="同伴">👥</button>
      </nav>
    </div>

    <!-- 底部操作栏 -->
    <footer class="intc-footer">
      <button class="intc-fbtn">休息</button>
      <button class="intc-fbtn primary">进入场景</button>
      <select class="intc-fsel"><option>1倍</option><option>5倍</option><option>10倍</option></select>
      <label class="intc-fcheck"><input type="checkbox">概率修正</label>
      <label class="intc-fcheck"><input type="checkbox">自动行动</label>
    </footer>
  </div>
</template>

<style scoped>
.intc-root { display: flex; flex-direction: column; height: 100%; background: #080b0d; color: #c9d8d5; font-size: 13px; }

/* topbar */
.intc-topbar { display: flex; align-items: center; gap: 12px; padding: 6px 14px; border-bottom: 1px solid #1e2a33; background: #0c1318; flex-shrink: 0; }
.intc-back { padding: 3px 10px; border: 1px solid #28343d; border-radius: 3px; background: transparent; color: #7e9292; font-size: 11px; cursor: pointer; }
.intc-brand { font-weight: 700; color: #caa86a; font-size: 14px; }
.intc-hint { font-size: 10px; color: #4a5a5d; margin-left: auto; }
.intc-toggle-left { padding: 3px 10px; border: 1px solid #28343d; border-radius: 3px; background: #0f1519; color: #7e9292; font-size: 11px; cursor: pointer; }

/* body */
.intc-body { display: flex; flex: 1; overflow: hidden; }

/* left panel */
.intc-left { width: 180px; min-width: 150px; border-right: 1px solid #1e2a33; background: #0c1318; overflow-y: auto; }
.intc-left-inner { padding: 10px; }
.intc-quick-player { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid #1a242b; }
.intc-av { font-size: 26px; }
.intc-pname { font-weight: 700; font-size: 14px; display: block; }
.intc-prank { font-size: 10px; color: #7ec4a1; }
.intc-mini-bars { margin-bottom: 10px; }
.intc-mbar { display: flex; align-items: center; gap: 4px; margin-bottom: 3px; font-size: 10px; color: #7e9292; }
.intc-mtrack { flex: 1; height: 4px; background: #1a242b; border-radius: 2px; overflow: hidden; }
.intc-mfill { height: 100%; border-radius: 2px; }
.intc-mfill.hp { background: #d97b6c; }
.intc-mfill.sta { background: #8db8d8; }
.intc-section { margin-bottom: 10px; }
.intc-sectitle { font-size: 10px; color: #7e9292; margin-bottom: 3px; }
.intc-loc-badge { font-size: 11px; color: #caa86a; padding: 2px 8px; border: 1px solid #2a3a25; border-radius: 2px; background: rgba(202,168,106,0.05); display: inline-block; }
.intc-explored { font-size: 13px; font-weight: 700; }

/* center */
.intc-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

.intc-log-strip {
  padding: 3px 12px; border-bottom: 1px solid #1a242b; background: #0a0e10;
  font-size: 11px; color: #4a5a5d; cursor: pointer; display: flex; align-items: flex-start; gap: 6px;
  flex-shrink: 0; transition: all 0.2s;
}
.intc-log-strip.expanded { max-height: 120px; overflow-y: auto; }
.intc-log-icon { flex-shrink: 0; }
.intc-log-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.intc-log-full { font-size: 11px; line-height: 1.6; }
.intc-log-full div { color: #7e9292; }

.intc-filter-strip { display: flex; align-items: center; gap: 4px; padding: 6px 12px; border-bottom: 1px solid #1e2a33; flex-shrink: 0; overflow-x: auto; }
.intc-flabel { font-size: 10px; color: #4a5a5d; }
.intc-fsep { color: #1e2a33; margin: 0 3px; }
.intc-fchip { padding: 2px 8px; border: 1px solid #28343d; border-radius: 3px; background: #0f1519; color: #7e9292; font-size: 10px; cursor: pointer; white-space: nowrap; }
.intc-fchip.active { border-color: #caa86a; color: #caa86a; }

.intc-map-wrap { flex: 1; overflow: hidden; position: relative; }

/* sheet */
.intc-sheet { border-top: 1px solid #28343d; background: #0f1519; padding: 10px 14px; flex-shrink: 0; }
.sheet-enter-active { animation: slideUp 0.2s; }
.sheet-leave-active { animation: slideUp 0.2s reverse; }
@keyframes slideUp { from { transform: translateY(100%); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
.intc-sheet-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.intc-sheet-name { font-weight: 700; font-size: 14px; color: #caa86a; }
.intc-sheet-tags { display: flex; gap: 5px; }
.intc-stag { font-size: 10px; padding: 1px 6px; border-radius: 2px; border: 1px solid; }
.intc-stag.main { color: #caa86a; border-color: #554b34; }
.intc-stag.lv { color: #8db8d8; border-color: #2d3a4a; }
.intc-stag.danger { color: #d97b6c; border-color: #3a2a28; }
.intc-sheet-actions { display: flex; gap: 8px; }
.intc-sbtn { padding: 4px 14px; border-radius: 3px; font-size: 11px; cursor: pointer; }
.intc-sbtn.close { border: 1px solid #28343d; background: transparent; color: #7e9292; }
.intc-sbtn.track { border: 1px solid #8db8d8; background: rgba(141,184,216,0.05); color: #8db8d8; }
.intc-sbtn.enter { border: 1px solid #caa86a; background: rgba(202,168,106,0.12); color: #caa86a; font-weight: 700; }

/* right nav */
.intc-right { width: 36px; border-left: 1px solid #1e2a33; background: #0c1318; display: flex; flex-direction: column; padding: 2px 0; }
.intc-rbtn { width: 100%; padding: 6px 0; border: none; background: transparent; color: #4a5a5d; font-size: 13px; cursor: pointer; }
.intc-rbtn.active { color: #caa86a; }

/* footer */
.intc-footer { display: flex; align-items: center; gap: 12px; padding: 6px 16px; border-top: 1px solid #1e2a33; background: rgba(12,19,24,0.95); flex-shrink: 0; }
.intc-fbtn { padding: 4px 16px; border: 1px solid #28343d; border-radius: 3px; background: #0f1519; color: #7e9292; font-size: 12px; cursor: pointer; }
.intc-fbtn.primary { border-color: #caa86a; color: #caa86a; font-weight: 700; font-size: 14px; padding: 6px 28px; }
.intc-fsel { padding: 3px 6px; border: 1px solid #28343d; border-radius: 3px; background: #0f1519; color: #7e9292; font-size: 11px; }
.intc-fcheck { font-size: 11px; color: #7e9292; display: flex; align-items: center; gap: 4px; cursor: pointer; }
</style>
