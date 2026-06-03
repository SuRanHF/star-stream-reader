<script setup lang="ts">
import { computed, provide, ref, onBeforeUnmount, onMounted, watch } from 'vue';

import { useRouter } from 'vue-router';
import type { ApiRecord } from '@/types/api';
import { broadcastApi } from '@/api/broadcastApi';
import { exploreApi } from '@/api/exploreApi';
import { gameApi } from '@/api/gameApi';
import { playerApi } from '@/api/playerApi';
import GameLogPanel from '@/components/log/GameLogPanel.vue';
import BottomNav from '@/components/nav/BottomNav.vue';
import BroadcastStrip from '@/components/broadcast/BroadcastStrip.vue';
import ConnectedPanel from '@/components/panels/ConnectedPanel.vue';
import SceneMapPanel from '@/components/scene-map/SceneMapPanel.vue';
import GameConfirmDialog from '@/components/common/GameConfirmDialog.vue';
import ExploreResultDialog from '@/components/explore/ExploreResultDialog.vue';
import GameNotificationToast from '@/components/notification/GameNotificationToast.vue';
import OnboardingGuide from '@/components/guide/OnboardingGuide.vue';
import PlayerStatusPanel from '@/components/player/PlayerStatusPanel.vue';
import DeathOverlay from '@/components/player/DeathOverlay.vue';
import ScenarioPanel from '@/components/scenario/ScenarioPanel.vue';
import ConstellationPanel from '@/components/constellation/ConstellationPanel.vue';
import { realtimeClient } from '@/realtime/realtimeClient';
import { useAuthStore } from '@/stores/authStore';
import { useGameStore } from '@/stores/gameStore';
import { useUiStore } from '@/stores/uiStore';
import type { PlayerStats } from '@/types/player';

const router = useRouter();
const authStore = useAuthStore();
const gameStore = useGameStore();
const uiStore = useUiStore();

const isDead = computed(() => {
  const p = gameStore.player as Record<string, unknown> | null;
  if (!p) return false;
  const s = p.stats as Record<string, unknown> | undefined;
  if (s?.isDead === true || s?.isDead === 'true') return true;
  return p.isDead === true || p.isDead === 'true';
});

const isResting = computed(() => {
  const rs = gameStore.restState;
  if (!rs) return false;
  const val = (rs as Record<string, unknown>).isResting ?? rs['isResting'] ?? rs.resting;
  return val === true || val === 'true' || val === 1 || val === '1';
});

const RANK_MAP: Record<string, string> = {
  F: 'F级·新晋',
  E: 'E级·入门',
  D: 'D级·熟练',
  C: 'C级·精英',
  B: 'B级·专家',
  A: 'A级·大师',
  S: 'S级·传奇',
  SS: 'SS级·神话',
  SSS: 'SSS级·超越',
};

function avatarRankLabel(raw: unknown): string {
  if (!raw) return '普通化身';
  const s = String(raw).trim();
  if (RANK_MAP[s]) return RANK_MAP[s];
  return s;
}

const stats = computed<PlayerStats>(() => gameStore.player?.stats || gameStore.player?.statsJson || {});
const restHpInterval = computed(() => Math.max(1, Math.floor(7200 / (numberValue(stats.value.maxHp) || 100))));
const restStaminaInterval = computed(() => Math.max(1, Math.floor(10800 / (numberValue(stats.value.maxStamina) || 50))));

// 休息开始时的值，用于计算已恢复量（用后端真实数据而非本地估算）
const expAtRestStart = ref(0);
const hpAtRestStart = ref(0);
const staminaAtRestStart = ref(0);

watch(isResting, (resting) => {
  if (resting) {
    expAtRestStart.value = numberValue(stats.value.exp);
    hpAtRestStart.value = numberValue(stats.value.hp);
    staminaAtRestStart.value = numberValue(stats.value.stamina);
  }
});

const expRecovered = computed(() => Math.max(0, numberValue(stats.value.exp) - expAtRestStart.value));
const hpRecovered = computed(() => Math.max(0, numberValue(stats.value.hp) - hpAtRestStart.value));
const staminaRecovered = computed(() => Math.max(0, numberValue(stats.value.stamina) - staminaAtRestStart.value));

const hpPercent = computed(() => percent(stats.value.hp, stats.value.maxHp));
const staminaPercent = computed(() => percent(stats.value.stamina, stats.value.maxStamina));
const expPercent = computed(() => percent(stats.value.exp, stats.value.maxExp));

const mobileMetrics = computed(() => [
  { label: '血', value: `${hpPercent.value}%`, tone: 'red' },
  { label: '行', value: `${staminaPercent.value}%`, tone: 'cyan' },
  { label: '攻', value: compact(stats.value.attack), tone: 'gold' },
  { label: '防', value: compact(stats.value.defense), tone: 'gold' },
  { label: '速', value: compact(stats.value.speed), tone: 'cyan' },
  { label: '经', value: `${expPercent.value}%`, tone: 'muted' },
]);

function selectPanel(panel: string) {
  if (panel === 'logout') {
    void logout();
    return;
  }
  // 同面板再点一次 = 关闭
  if (gameStore.activePanel === panel) {
    gameStore.setActivePanel('');
    return;
  }
  gameStore.setActivePanel(panel);
}

function closePanel() {
  gameStore.setActivePanel('');
}

function currentLocationKey() {
  const p = gameStore.player as Record<string, unknown> | null;
  return String(gameStore.player?.currentLocationKey || gameStore.player?.currentLocation || p?.['current_location'] || '');
}

function currentLocationDisplayName() {
  return String(gameStore.player?.currentLocationName || gameStore.player?.currentLocationKey || gameStore.player?.currentLocation || '');
}

function numberValue(value: unknown) {
  return typeof value === 'number' ? value : Number(value || 0);
}

function percent(value: unknown, max: unknown) {
  const nextValue = numberValue(value);
  const nextMax = numberValue(max);
  if (!nextMax) return 0;
  return Math.max(0, Math.min(100, Math.round((nextValue / nextMax) * 100)));
}

function compact(value: unknown) {
  const next = numberValue(value);
  if (next >= 10000) return `${(next / 10000).toFixed(next >= 100000 ? 0 : 1)}万`;
  return String(next);
}

function worldlineValue(): string {
  const s = gameStore.player?.stats || {};
  return compact((s as Record<string, unknown>).worldLineShift);
}

async function logout() {
  realtimeClient.disconnect();
  authStore.logout();
  await router.push('/login');
}

// --- explore result dialog ---
const exploreResult = ref<any>(null);
const showExploreResult = ref(false);
const exploring = ref(false);

async function enterScene() {
  if (exploring.value) return;
  const id = Number(gameStore.player?.id || gameStore.player?.playerId || 0);
  const locationKey = currentLocationKey();
  if (!id || !locationKey) {
    selectPanel('scene-map');
    await uiStore.showAlert('提示', '请在星图上选择场景并点击「切换到此场景」。');
    return;
  }
  if (!(await uiStore.showConfirm('探索确认', `进入「${currentLocationDisplayName()}」探索？`))) return;
  exploring.value = true;
  try {
    const payload = await exploreApi.startExplore(id, locationKey);
    const bootstrap = await gameApi.getBootstrap();
    gameStore.applyBootstrap(bootstrap);
    exploreResult.value = payload;
    showExploreResult.value = true;
  } catch (error) {
    await uiStore.showAlert('错误', error instanceof Error ? error.message : '进入场景失败');
  } finally {
    exploring.value = false;
  }
}

function onExploreClose() {
  showExploreResult.value = false;
  exploreResult.value = null;
}

async function toggleRest() {
  const id = Number(gameStore.player?.id || gameStore.player?.playerId || 0);
  if (!id) return;
  try {
    if (isResting.value) {
      if (!(await uiStore.showConfirm('待机确认', '结束当前待机状态？'))) return;
      await playerApi.stopRest(id);
    } else {
      await playerApi.startRest(id);
    }
    const payload = await gameApi.getBootstrap();
    gameStore.applyBootstrap(payload);
  } catch (error) {
    await uiStore.showAlert('错误', error instanceof Error ? error.message : '操作失败');
  }
}

async function finishAction() {
  const id = Number(gameStore.player?.id || gameStore.player?.playerId || 0);
  if (!id) return;
  if (!(await uiStore.showConfirm('行动确认', '结束当前行动？'))) return;
  try {
    await playerApi.stopRest(id);
    const payload = await gameApi.getBootstrap();
    gameStore.applyBootstrap(payload);
  } catch (error) {
    await uiStore.showAlert('错误', error instanceof Error ? error.message : '结束行动失败');
  }
}

// ─── 左右面板拖拽调整宽度 ───
const isMobile = ref(window.innerWidth <= 760);
const leftPanelWidth = ref(240);
const rightPanelWidth = ref(72);
const resizingSide = ref<'left' | 'right' | null>(null);

// ─── 场景地图模式：日志面板高度拖拽 ───
const logPanelHeight = ref(180);
const resizingLogPanel = ref(false);
let logResizeStartY = 0;
let logResizeStartHeight = 0;

function checkMobile() {
  isMobile.value = window.innerWidth <= 760;
  if (isMobile.value) {
    leftPanelWidth.value = 240;
    rightPanelWidth.value = 72;
  }
}
window.addEventListener('resize', checkMobile);

const frameGridStyle = computed(() => {
  if (isMobile.value) return undefined;
  return { gridTemplateColumns: `${leftPanelWidth.value}px 8px minmax(0, 1fr) 8px ${rightPanelWidth.value}px` };
});

function startLeftResize(e: MouseEvent) {
  resizingSide.value = 'left';
  document.addEventListener('mousemove', doResize);
  document.addEventListener('mouseup', stopResize);
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
  e.preventDefault();
}

function startRightResize(e: MouseEvent) {
  resizingSide.value = 'right';
  document.addEventListener('mousemove', doResize);
  document.addEventListener('mouseup', stopResize);
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
  e.preventDefault();
}

function doResize(e: MouseEvent) {
  if (!resizingSide.value) return;
  if (resizingSide.value === 'left') {
    leftPanelWidth.value = Math.max(200, Math.min(500, e.clientX));
  } else {
    rightPanelWidth.value = Math.max(52, Math.min(300, window.innerWidth - e.clientX));
  }
}

function stopResize() {
  resizingSide.value = null;
  document.removeEventListener('mousemove', doResize);
  document.removeEventListener('mouseup', stopResize);
  document.body.style.cursor = '';
  document.body.style.userSelect = '';
}

function startLogResize(e: MouseEvent) {
  resizingLogPanel.value = true;
  logResizeStartY = e.clientY;
  logResizeStartHeight = logPanelHeight.value;
  document.addEventListener('mousemove', doLogResize);
  document.addEventListener('mouseup', stopLogResize);
  document.body.style.cursor = 'row-resize';
  document.body.style.userSelect = 'none';
  e.preventDefault();
}

function doLogResize(e: MouseEvent) {
  if (!resizingLogPanel.value) return;
  const delta = e.clientY - logResizeStartY;
  logPanelHeight.value = Math.max(80, Math.min(500, logResizeStartHeight + delta));
}

function stopLogResize() {
  resizingLogPanel.value = false;
  document.removeEventListener('mousemove', doLogResize);
  document.removeEventListener('mouseup', stopLogResize);
  document.body.style.cursor = '';
  document.body.style.userSelect = '';
}

// ─── 中间面板（ConnectedPanel）grid 列宽拖拽 → 日志和面板同步缩放 ───
const panelGridMaxPct = ref(0.42);
let panelResizeStartPct = 0;

function startPanelGridResize() {
  panelResizeStartPct = panelGridMaxPct.value;
}

function doPanelGridResize(deltaX: number, containerWidth: number) {
  const deltaPct = deltaX / (containerWidth || 1000);
  panelGridMaxPct.value = Math.max(0.25, Math.min(0.65, panelResizeStartPct - deltaPct));
}

provide('startPanelGridResize', startPanelGridResize);
provide('doPanelGridResize', doPanelGridResize);

const mainGridStyle = computed(() => {
  if (gameStore.activePanel === 'scene-map') return undefined;
  if (!gameStore.activePanel) {
    return { gridTemplateColumns: 'minmax(0, 1fr)' };
  }
  return {
    gridTemplateColumns: `minmax(0, 1fr) minmax(280px, ${panelGridMaxPct.value * 100}%)`,
  };
});

// ─── 轮询兜底（30秒刷新广播和世界Boss状态）───
let pollTimer: ReturnType<typeof setInterval> | null = null;

async function pollServerState() {
  try {
    const result = await broadcastApi.getActive();
    if (result && Array.isArray(result)) {
      gameStore.setBroadcastSummary({ activeEvents: result, activeCount: result.length });
    }
  } catch {
    // polling is a fallback, silence failures
  }
}

// ─── 休息期间轮询（5秒更新HP/体力恢复）───
let restPollTimer: ReturnType<typeof setInterval> | null = null;

async function pollRestState() {
  const id = Number(gameStore.player?.id || gameStore.player?.playerId || 0);
  if (!id || !isResting.value) return;
  try {
    const result = await playerApi.getRestState(id);
    gameStore.applyRestState(result);
  } catch {
    // polling fallback, silence failures
  }
}

watch(isResting, (resting) => {
  if (resting) {
    restPollTimer = setInterval(pollRestState, 5000);
  } else {
    if (restPollTimer) {
      clearInterval(restPollTimer);
      restPollTimer = null;
    }
  }
}, { immediate: true });

onMounted(() => {
  pollTimer = setInterval(pollServerState, 30000);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile);
  document.removeEventListener('mousemove', doResize);
  document.removeEventListener('mouseup', stopResize);
  document.removeEventListener('mousemove', doLogResize);
  document.removeEventListener('mouseup', stopLogResize);
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  if (restPollTimer) {
    clearInterval(restPollTimer);
    restPollTimer = null;
  }
});
</script>

<template>
  <div class="ling-shell gl-root">
    <OnboardingGuide />
    <header class="ling-topbar">
      <div class="ling-top-left">
        <button class="ling-mobile-role" @click="uiStore.toggleRightSummary">角色</button>
        <h1 class="ling-logo">星流</h1>
        <span class="ling-pill ling-realm">{{ avatarRankLabel(gameStore.player?.avatarRank || gameStore.player?.storyGrade) }}</span>
        <small class="ling-top-worldline">偏移 {{ worldlineValue() }}</small>
        <button :class="['ling-core-btn', { active: gameStore.activePanel === 'scene-map' }]" @click="selectPanel('scene-map')">场景地图</button>
        <button :class="['ling-core-btn', { active: gameStore.activePanel === 'inventory' }]" @click="selectPanel('inventory')">背包</button>
        <button :class="['ling-core-btn', { active: gameStore.activePanel === 'synthesis' }]" @click="selectPanel('synthesis')">合成</button>
        <button :class="['ling-core-btn', { active: gameStore.activePanel === 'equipment' }]" @click="selectPanel('equipment')">装备</button>
        <button :class="['ling-core-btn', { active: gameStore.activePanel === 'skills' }]" @click="selectPanel('skills')">技能</button>
        <button :class="['ling-core-btn', { active: gameStore.activePanel === 'friends' }]" @click="selectPanel('friends')">同伴</button>
        <button :class="['ling-core-btn', { active: gameStore.activePanel === 'faction' }]" @click="selectPanel('faction')">阵营</button>
        <button :class="['ling-core-btn', { active: gameStore.activePanel === 'constellation' }]" @click="selectPanel('constellation')">星座</button>
        <button :class="['ling-core-btn', { active: gameStore.activePanel === 'system' }]" @click="selectPanel('system')">设置</button>
      </div>
    </header>

    <BroadcastStrip />

    <div class="ling-mobile-stats">
      <span
        v-for="item in mobileMetrics"
        :key="item.label"
        :class="['ling-mobile-stat', `tone-${item.tone}`]"
      >
        <b>{{ item.label }}</b>{{ item.value }}
      </span>
    </div>

    <div class="ling-frame" :style="frameGridStyle">
      <aside class="ling-left" :class="{ 'is-open': uiStore.rightSummaryOpen }" :style="isMobile ? undefined : { width: leftPanelWidth + 'px' }">
        <PlayerStatusPanel :player="gameStore.player" @player-updated="(data: unknown) => { if (data) { gameStore.patchPlayer(data as ApiRecord) } else { gameStore.reloadPlayer() } }" />
      </aside>
      <div v-if="!isMobile" class="ling-resize-handle" @mousedown="startLeftResize"></div>

      <main class="ling-main" :style="mainGridStyle">
        <template v-if="gameStore.activePanel === 'scene-map'">
          <div class="ling-scene-map-wrap">
            <GameLogPanel
              class="ling-log--resizable"
              :logs="gameStore.recentLogs"
              :style="{ height: logPanelHeight + 'px', flexShrink: 0 }"
            />
            <div
              class="ling-log-resize-handle"
              @mousedown="startLogResize"
            ></div>
            <div class="ling-scene-map-body">
              <SceneMapPanel @close-panel="closePanel" />
            </div>
          </div>
        </template>
        <template v-else>
          <GameLogPanel :logs="gameStore.recentLogs" />
          <ConnectedPanel
            v-if="gameStore.activePanel"
            :panel="gameStore.activePanel"
            @select-panel="selectPanel"
            @close-panel="closePanel"
          />
          <div v-else class="ling-placeholder">
            <div class="ling-placeholder-inner">
              <ScenarioPanel />
              <ConstellationPanel v-if="currentLocationKey()" />
            </div>
          </div>
        </template>
      </main>

      <div v-if="!isMobile" class="ling-resize-handle" @mousedown="startRightResize"></div>
      <BottomNav :active="gameStore.activePanel" @select="selectPanel" />
    </div>

    <footer class="ling-bottom" :class="{ 'is-resting': isResting }">
      <div class="ling-action-row" :class="{ 'has-rest': isResting }">
        <button class="ling-action-primary" @click="toggleRest">{{ isResting ? '休息...' : '休息' }}</button>
        <button class="ling-action-main" :disabled="exploring" @click="enterScene">{{ exploring ? '执行中...' : '执行场景' }}</button>
        <select class="ling-select" aria-label="探索倍率">
          <option value="1">1倍</option>
          <option value="5">5倍</option>
          <option value="10">10倍</option>
        </select>
        <label class="ling-check"><input type="checkbox" /> 概率修正</label>
        <label class="ling-check"><input type="checkbox" /> 自动行动</label>
        <span class="ling-help">ⓘ</span>
      </div>
      <div v-if="isResting" class="ling-footer-status">
        <span class="ling-dot ling-dot-active"></span>
        <span>休息中</span>
        <span>恢复: HP <b>+1/{{ restHpInterval }}秒</b> 体力 <b>+1/{{ restStaminaInterval }}秒</b> 经验 <b>+1/30秒</b></span>
        <span>已恢复: HP <b>+{{ hpRecovered }}</b> 体力 <b>+{{ staminaRecovered }}</b> 经验 <b>+{{ expRecovered }}</b></span>
        <button class="ling-finish" @click="finishAction">结束行动</button>
      </div>
    </footer>

    <DeathOverlay v-if="isDead" />
    <GameNotificationToast />
    <GameConfirmDialog />
    <ExploreResultDialog
      :visible="showExploreResult"
      :result="exploreResult"
      @close="onExploreClose"
    />
  </div>
</template>

<style scoped>
/* ── Shell 边框微光 ── */
.gl-root {
  border-color: rgba(26, 38, 80, 0.5);
}

/* ── 顶栏增强 ── */
.gl-root :deep(.ling-topbar) {
  background: linear-gradient(
    180deg,
    rgba(13, 20, 48, 0.95) 0%,
    rgba(10, 15, 36, 0.92) 100%
  );
}

/* Logo — 蓝金渐变 */
.gl-root :deep(.ling-logo) {
  background: linear-gradient(180deg, #8ec8ff 0%, #6aafff 40%, #4a8fe7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: glLogoGlow 4s ease-in-out infinite;
}

@keyframes glLogoGlow {
  0%, 100% { filter: drop-shadow(0 0 8px rgba(74, 143, 231, 0.3)); }
  50% { filter: drop-shadow(0 0 16px rgba(74, 143, 231, 0.5)); }
}

/* ── 核心操作栏增强 ── */
.gl-root :deep(.ling-core-btn) {
  border-radius: 5px;
  letter-spacing: 1.5px;
}

/* ── 底部栏增强 ── */
.gl-root :deep(.ling-bottom) {
  background: linear-gradient(
    180deg,
    rgba(13, 20, 48, 0.95) 0%,
    rgba(10, 15, 36, 0.92) 100%
  );
}

/* 进入场景按钮 */
.gl-root :deep(.ling-action-main) {
  font-weight: 600;
  letter-spacing: 2px;
  border-radius: var(--radius-md, 6px);
  transition: all 0.25s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1));
}

.gl-root :deep(.ling-action-main:hover) {
  transform: translateY(-1px);
}

.gl-root :deep(.ling-action-main:active) {
  transform: scale(0.97);
}

/* 休息按钮 */
.gl-root :deep(.ling-action-primary) {
  border-radius: var(--radius-md, 6px);
  letter-spacing: 1px;
  transition: all 0.25s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1));
}

.gl-root :deep(.ling-action-primary:hover) {
  transform: translateY(-1px);
}

/* 下拉选择框 */
.gl-root :deep(.ling-select) {
  border-radius: 5px;
  cursor: pointer;
}

/* ── 休息状态栏 ── */
.gl-root :deep(.ling-dot) {
  box-shadow: 0 0 10px rgba(94, 196, 158, 0.5);
}

/* ── 拖拽手柄增强 ── */
.gl-root :deep(.ling-resize-handle:hover) {
  box-shadow: 0 0 8px rgba(74, 143, 231, 0.12);
}

/* ── 移动端左侧面板遮罩 ── */
.gl-root :deep(.ling-left.is-open) {
  box-shadow: 16px 0 40px rgba(0, 0, 0, 0.6);
}
</style>
