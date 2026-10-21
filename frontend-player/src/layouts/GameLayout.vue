<script setup lang="ts">
import { computed, nextTick, provide, ref, onBeforeUnmount, onMounted, watch } from 'vue';
import { useQueryClient } from '@tanstack/vue-query';
import { BookOpen, Map, Settings, Sparkles } from 'lucide-vue-next';

import { useRouter } from 'vue-router';
import type { ApiRecord } from '@/types/api';
import { broadcastApi } from '@/api/broadcastApi';
import { exploreApi, type ExploreResult } from '@/api/exploreApi';
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
import { realtimeClient } from '@/realtime/realtimeClient';
import { useAuthStore } from '@/stores/authStore';
import { useGameStore } from '@/stores/gameStore';
import { useUiStore } from '@/stores/uiStore';
import type { PlayerStats } from '@/types/player';
import { getExperienceProgress } from '@/utils/experienceProgress';
import { clearPendingExploreRequestKey, getPendingExploreRequestKey } from '@/utils/exploreRequestKey';
import { formatExploreResultLogs, isInteractiveExploreResult } from '@/utils/gameLog';

const router = useRouter();
const authStore = useAuthStore();
const gameStore = useGameStore();
const uiStore = useUiStore();
const queryClient = useQueryClient();

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
const expPercent = computed(() => getExperienceProgress(stats.value.exp, stats.value.level).percent);

const mobileMetrics = computed(() => [
  { label: '血', value: `${hpPercent.value}%`, tone: 'red' },
  { label: '行', value: `${staminaPercent.value}%`, tone: 'cyan' },
  { label: '攻', value: compact(stats.value.attack), tone: 'gold' },
  { label: '防', value: compact(stats.value.defense), tone: 'gold' },
  { label: '速', value: compact(stats.value.speed), tone: 'cyan' },
  { label: '经', value: `${expPercent.value}%`, tone: 'muted' },
]);

function selectPanel(panel: string) {
  headerMoreOpen.value = false;
  const isClosing = gameStore.activePanel === panel;
  if (isMobile.value) {
    // 手机上打开功能面板时收起操作栏，把可视高度让给面板内容；再次点击同一入口则恢复操作栏。
    actionBarCollapsed.value = !isClosing;
  }
  if (panel === 'logout') {
    void logout();
    return;
  }
  // 同面板再点一次 = 关闭
  if (isClosing) {
    gameStore.setActivePanel('');
    return;
  }
  gameStore.setActivePanel(panel);
}

function closePanel() {
  gameStore.setActivePanel('');
  if (isMobile.value) actionBarCollapsed.value = false;
}

function currentLocationKey() {
  const p = gameStore.player as Record<string, unknown> | null;
  return String(gameStore.player?.currentLocationKey || gameStore.player?.currentLocation || p?.['current_location'] || '');
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

type HeaderNavItem = {
  key: string;
  label: string;
  panel: string;
  title?: string;
};

// 只借用参考站的分组布局；按钮名称和功能都来自星流读者现有面板。
const headerPrimaryItems: HeaderNavItem[] = [
  { key: 'map', label: '场景地图', panel: 'scene-map' },
  { key: 'hidden-scene', label: '隐藏场景', panel: 'hiddenScene' },
  { key: 'world-boss', label: '灾厄', panel: 'worldBoss' },
  { key: 'ranking', label: '化身排行', panel: 'ranking' },
  { key: 'story', label: '故事记录', panel: 'history' },
  { key: 'faction', label: '星座阵营', panel: 'faction' },
];

const headerSystemItems: HeaderNavItem[] = [
  { key: 'guide', label: '说明', panel: 'guide' },
  { key: 'chat', label: '星流频道', panel: 'chat' },
  { key: 'trade', label: '鬼怪商店', panel: 'trade' },
  { key: 'skills', label: '技能图谱', panel: 'skills' },
  { key: 'pk', label: '化身战场', panel: 'pk' },
  { key: 'quests', label: '场景试炼', panel: 'quests' },
  { key: 'party', label: '队伍', panel: 'party' },
  { key: 'support', label: '悬赏支援', panel: 'support' },
];

const headerExtraItems: HeaderNavItem[] = [
  { key: 'inventory', label: '背包', panel: 'inventory' },
  { key: 'synthesis', label: '合成', panel: 'synthesis' },
  { key: 'equipment', label: '装备', panel: 'equipment' },
  { key: 'friends', label: '私信', panel: 'friends' },
  { key: 'titles', label: '档案', panel: 'titles' },
  { key: 'constellation', label: '星座', panel: 'constellation' },
  { key: 'notice', label: '星流公告', panel: 'notice' },
  { key: 'underworld', label: '冥界', panel: 'underworld' },
  { key: 'feedback', label: '鬼怪反馈', panel: 'feedback' },
  { key: 'system', label: '系统', panel: 'system' },
];

const headerNavItems = [...headerPrimaryItems, ...headerSystemItems, ...headerExtraItems];
const headerVisibleCount = ref(headerNavItems.length);
const headerNavElement = ref<HTMLElement | null>(null);
const headerMeasureElement = ref<HTMLElement | null>(null);
const visibleHeaderKeys = computed(() => new Set(
  headerNavItems.slice(0, headerVisibleCount.value).map((item) => item.key),
));
const visibleHeaderPrimaryItems = computed(() => headerPrimaryItems.filter((item) => visibleHeaderKeys.value.has(item.key)));
const visibleHeaderSystemItems = computed(() => headerSystemItems.filter((item) => visibleHeaderKeys.value.has(item.key)));
const visibleHeaderExtraItems = computed(() => headerExtraItems.filter((item) => visibleHeaderKeys.value.has(item.key)));
const headerOverflowItems = computed(() => headerNavItems.slice(headerVisibleCount.value));

const headerMoreSectionCatalog: Array<{ label: string; items: HeaderNavItem[] }> = [
  {
    label: '探索',
    items: [
      { key: 'more-map', label: '场景地图', panel: 'scene-map' },
      { key: 'more-hidden-scene', label: '隐藏场景', panel: 'hiddenScene' },
      { key: 'more-world-boss', label: '灾厄', panel: 'worldBoss' },
      { key: 'more-ranking', label: '化身排行', panel: 'ranking' },
      { key: 'more-history', label: '故事记录', panel: 'history' },
      { key: 'more-pk', label: '化身战场', panel: 'pk' },
      { key: 'more-quests', label: '场景试炼', panel: 'quests' },
    ],
  },
  {
    label: '角色',
    items: [
      { key: 'more-inventory', label: '背包', panel: 'inventory' },
      { key: 'more-equipment', label: '装备', panel: 'equipment' },
      { key: 'more-synthesis', label: '合成', panel: 'synthesis' },
      { key: 'more-skills', label: '技能图谱', panel: 'skills' },
      { key: 'more-faction', label: '星座阵营', panel: 'faction' },
      { key: 'more-constellation', label: '星座', panel: 'constellation' },
      { key: 'more-titles', label: '档案', panel: 'titles' },
    ],
  },
  {
    label: '社交',
    items: [
      { key: 'more-chat', label: '星流频道', panel: 'chat' },
      { key: 'more-friends', label: '私信', panel: 'friends' },
      { key: 'more-party', label: '队伍', panel: 'party' },
      { key: 'more-support', label: '悬赏支援', panel: 'support' },
      { key: 'more-trade', label: '鬼怪商店', panel: 'trade' },
    ],
  },
  {
    label: '其他',
    items: [
      { key: 'more-notice', label: '星流公告', panel: 'notice' },
      { key: 'more-underworld', label: '冥界', panel: 'underworld' },
      { key: 'more-feedback', label: '鬼怪反馈', panel: 'feedback' },
      { key: 'more-guide', label: '说明', panel: 'guide' },
      { key: 'more-system', label: '系统', panel: 'system' },
    ],
  },
];

// 顶栏收纳项只在对应入口被折叠后留在“更多”中；角色专属的其他快捷入口仍保留。
const headerMoreSections = computed(() => {
  const overflowKeys = new Set(headerOverflowItems.value.map((item) => item.key));
  return headerMoreSectionCatalog
    .map((section) => ({
      ...section,
      items: section.items.filter((menuItem) => {
        const headerItem = headerNavItems.find((item) => item.label === menuItem.label && item.panel === menuItem.panel);
        return !headerItem || overflowKeys.has(headerItem.key);
      }),
    }))
    .filter((section) => section.items.length > 0);
});

function updateHeaderVisibleCount() {
  if (window.innerWidth <= 760) return;
  const nav = headerNavElement.value;
  const measure = headerMeasureElement.value;
  if (!nav || !measure || nav.clientWidth <= 0) return;

  const groups = Array.from(measure.children).filter((element): element is HTMLElement => element instanceof HTMLElement);
  const navGap = Number.parseFloat(getComputedStyle(nav).columnGap) || 0;
  const groupMetrics = groups.map((group) => {
    const style = getComputedStyle(group);
    const buttons = Array.from(group.querySelectorAll<HTMLElement>(':scope > .ling-core-btn'));
    return {
      widths: buttons.map((button) => button.getBoundingClientRect().width),
      gap: Number.parseFloat(style.columnGap) || 0,
      shell: (Number.parseFloat(style.paddingLeft) || 0)
        + (Number.parseFloat(style.paddingRight) || 0)
        + (Number.parseFloat(style.borderLeftWidth) || 0)
        + (Number.parseFloat(style.borderRightWidth) || 0),
    };
  });

  const requiredWidth = (count: number) => {
    let offset = 0;
    let visibleGroups = 0;
    let width = 0;
    for (const group of groupMetrics) {
      const visibleInGroup = Math.max(0, Math.min(group.widths.length, count - offset));
      offset += group.widths.length;
      if (visibleInGroup === 0) continue;
      visibleGroups += 1;
      width += group.shell
        + group.widths.slice(0, visibleInGroup).reduce((total, buttonWidth) => total + buttonWidth, 0)
        + group.gap * (visibleInGroup - 1);
    }
    return width + navGap * Math.max(0, visibleGroups - 1);
  };

  let nextCount = headerNavItems.length;
  while (nextCount > 0 && requiredWidth(nextCount) > nav.clientWidth + 0.5) nextCount -= 1;
  headerVisibleCount.value = nextCount;
}

let headerResizeObserver: ResizeObserver | null = null;

async function logout() {
  realtimeClient.disconnect();
  authStore.logout();
  await router.push('/login');
}

// --- explore result dialog ---
const exploreResult = ref<any>(null);
const showExploreResult = ref(false);
const exploring = ref(false);
const pendingStories = ref<ExploreResult[]>([]);
let pendingStoryRefreshId = 0;
const pendingBattles = ref<ExploreResult[]>([]);
const pendingBattlesLoaded = ref(false);
let pendingBattleRefreshId = 0;

async function refreshPendingStories() {
  const id = Number(gameStore.player?.id || gameStore.player?.playerId || 0);
  if (!id) return;
  const refreshId = ++pendingStoryRefreshId;
  try {
    const response = await exploreApi.getPendingStories(id);
    if (refreshId !== pendingStoryRefreshId) return;
    pendingStories.value = response.pending_stories || [];
  } catch {
    // 待选剧情读取失败不影响当前探索；服务端状态会在下次刷新时重新读取。
  }
}

function queuePendingStory(result: ExploreResult) {
  if (result.result_type !== 'story' || !result.result.event_key || !result.result.choices?.length) return;
  // 忽略较早发出的启动查询，避免它的旧快照覆盖刚触发的新剧情。
  pendingStoryRefreshId += 1;
  const existingIndex = pendingStories.value.findIndex((entry) => entry.result.event_key === result.result.event_key);
  if (existingIndex >= 0) pendingStories.value.splice(existingIndex, 1, result);
  else pendingStories.value.push(result);
}

function resumePendingStory() {
  const pending = pendingStories.value[0];
  if (!pending) return;
  exploreResult.value = pending;
  showExploreResult.value = true;
}

async function refreshPendingBattles() {
  const id = Number(gameStore.player?.id || gameStore.player?.playerId || 0);
  if (!id) return;
  const refreshId = ++pendingBattleRefreshId;
  try {
    const response = await exploreApi.getPendingBattles(id);
    if (refreshId !== pendingBattleRefreshId) return;
    pendingBattles.value = (response.pending_battles || [])
      .filter((entry) => entry.result_type === 'battle' && Boolean(entry.monster_key));
    if (!exploreResult.value && pendingBattles.value.length) {
      exploreResult.value = pendingBattles.value[0];
      showExploreResult.value = true;
    }
  } catch {
    // Pending combat recovery is best-effort; a later scene action retries it.
  } finally {
    if (refreshId === pendingBattleRefreshId) pendingBattlesLoaded.value = true;
  }
}

function queuePendingBattle(result: ExploreResult) {
  if (result.result_type !== 'battle' || !result.monster_key) return;
  // Invalidate a stale startup snapshot so it cannot erase this just-received encounter.
  pendingBattleRefreshId += 1;
  pendingBattlesLoaded.value = true;
  const existingIndex = pendingBattles.value.findIndex((entry) =>
    result.request_key
      ? entry.request_key === result.request_key
      : entry.result.event_key === result.result.event_key,
  );
  if (existingIndex >= 0) pendingBattles.value.splice(existingIndex, 1, result);
  else pendingBattles.value.push(result);
}

function resumePendingBattle() {
  const pending = pendingBattles.value[0];
  if (!pending) return;
  exploreResult.value = pending;
  showExploreResult.value = true;
}

function onBattleResolved(encounterKey: string, acknowledged: boolean) {
  if (!acknowledged) return;
  pendingBattles.value = pendingBattles.value.filter((entry) =>
    entry.request_key !== encounterKey && entry.result.event_key !== encounterKey,
  );
  invalidateQuestPanel();
}

function onStoryChoiceMade(eventKey: string, pendingBattle?: ExploreResult) {
  pendingStories.value = pendingStories.value.filter((entry) => entry.result.event_key !== eventKey);
  if (pendingBattle?.result_type === 'battle' && pendingBattle.monster_key) {
    queuePendingBattle(pendingBattle);
    exploreResult.value = pendingBattle;
    showExploreResult.value = true;
  }
  void refreshPendingStories();
}

function selectedConstellationKey() {
  const stats = gameStore.player?.stats || gameStore.player?.statsJson || {};
  const constellation = stats.constellation;
  return typeof constellation === 'string' ? constellation.trim() : '';
}

watch(selectedConstellationKey, (constellationKey) => {
  if (!constellationKey) {
    pendingBattlesLoaded.value = false;
    return;
  }
  void refreshPendingStories();
  void refreshPendingBattles();
}, { immediate: true });

async function enterScene() {
  if (exploring.value) return;
  if (isResting.value) {
    await uiStore.showAlert('休息中无法探索', '请先结束当前休息状态，再执行场景。休息期间生命、体力与经验仍会持续恢复。');
    return;
  }
  if (!pendingBattlesLoaded.value) await refreshPendingBattles();
  if (pendingBattles.value.length) {
    resumePendingBattle();
    return;
  }
  const id = Number(gameStore.player?.id || gameStore.player?.playerId || 0);
  const locationKey = currentLocationKey();
  if (!id || !locationKey) {
    selectPanel('scene-map');
    await uiStore.showAlert('提示', '请在星图上选择场景并点击「切换到此场景」。');
    return;
  }
  const previousLatestLogId = Math.max(0, ...gameStore.recentLogs
    .map((entry) => Number(entry.id))
    .filter((value) => Number.isFinite(value) && value > 0));
  const requestKey = getPendingExploreRequestKey(id, locationKey);
  exploring.value = true;
  try {
    const payload = await exploreApi.startExplore(id, locationKey, requestKey);
    clearPendingExploreRequestKey(id, locationKey, requestKey);
    invalidateQuestPanel();
    if (payload.player) gameStore.patchPlayer(payload.player);

    let bootstrapRefreshed = false;
    try {
      const bootstrap = await gameApi.getBootstrap();
      gameStore.applyBootstrap(bootstrap);
      bootstrapRefreshed = true;
    } catch {
      // 探索接口已经结算；刷新失败不能把已扣体力的行动误报为失败。
    }

    const eventKey = payload.result?.event_key;
    const persistedResult = bootstrapRefreshed && eventKey
      ? [...gameStore.recentLogs].reverse().find((entry) =>
        entry.metadata?.kind === 'exploration_result'
        && entry.metadata.eventKey === eventKey
        && Number(entry.id) > previousLatestLogId,
      )
      : undefined;
    if (!payload.request_replayed && !persistedResult) {
      gameStore.appendRecentLogs(formatExploreResultLogs(payload, gameStore.inventorySummary));
    }

    if (isInteractiveExploreResult(payload)) {
      exploreResult.value = payload;
      showExploreResult.value = true;
      queuePendingBattle(payload);
      queuePendingStory(payload);
    } else {
      exploreResult.value = null;
      showExploreResult.value = false;
    }
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

function invalidateQuestPanel() {
  const playerId = Number(gameStore.player?.id || gameStore.player?.playerId || 0);
  if (!playerId) return;
  // 桌面任务面板可与探索同时打开；刷新任务查询以同步进度和可领取状态。
  void queryClient.invalidateQueries({ queryKey: ['feature-panel', 'quests', playerId] });
}

async function toggleRest() {
  const id = Number(gameStore.player?.id || gameStore.player?.playerId || 0);
  if (!id) return;
  try {
    if (isResting.value) {
      if (!(await uiStore.showConfirm('休息确认', '结束当前休息状态？'))) return;
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
  if (!(await uiStore.showConfirm('休息确认', '结束当前休息状态？'))) return;
  try {
    await playerApi.stopRest(id);
    const payload = await gameApi.getBootstrap();
    gameStore.applyBootstrap(payload);
  } catch (error) {
    await uiStore.showAlert('错误', error instanceof Error ? error.message : '结束休息失败');
  }
}

// ─── 左右面板拖拽调整宽度 ───
const isMobile = ref(window.innerWidth <= 760);
const leftPanelWidth = ref(240);
const desktopPlayerPanelCollapsed = ref(false);
const resizingSide = ref<'left' | null>(null);
const headerMoreOpen = ref(false);
const actionBarCollapsed = ref(false);
const isLightTheme = ref(document.documentElement.dataset.theme === 'light');
const isInlineChat = ref(window.innerWidth >= 1025);

function checkMobile() {
  isMobile.value = window.innerWidth <= 760;
  isInlineChat.value = window.innerWidth >= 1025;
  if (isMobile.value) {
    leftPanelWidth.value = 240;
  }
}
window.addEventListener('resize', checkMobile);

function toggleRolePanel() {
  if (isMobile.value) {
    uiStore.toggleRightSummary();
    return;
  }
  desktopPlayerPanelCollapsed.value = !desktopPlayerPanelCollapsed.value;
}

const frameGridStyle = computed(() => {
  if (isMobile.value) return undefined;
  const hasSidePanel = Boolean(gameStore.activePanel);
  const sidePanelWidth = panelGridResized.value
    ? `minmax(260px, min(${panelGridMaxPct.value * 100}vw, 520px))`
    : isInlineChat.value
      ? 'clamp(360px, 24vw, 520px)'
      : '360px';
  return {
    '--ling-left-width': desktopPlayerPanelCollapsed.value ? '0px' : `${leftPanelWidth.value}px`,
    '--ling-resize-width': desktopPlayerPanelCollapsed.value ? '0px' : '8px',
    ...(hasSidePanel ? { '--ling-side-width': sidePanelWidth } : {}),
  };
});

function startLeftResize(e: MouseEvent) {
  resizingSide.value = 'left';
  document.addEventListener('mousemove', doResize);
  document.addEventListener('mouseup', stopResize);
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
  e.preventDefault();
}

function doResize(e: MouseEvent) {
  if (!resizingSide.value) return;
  leftPanelWidth.value = Math.max(200, Math.min(500, e.clientX));
}

function stopResize() {
  resizingSide.value = null;
  document.removeEventListener('mousemove', doResize);
  document.removeEventListener('mouseup', stopResize);
  document.body.style.cursor = '';
  document.body.style.userSelect = '';
}

function toggleHeaderMore() {
  headerMoreOpen.value = !headerMoreOpen.value;
}

function closeHeaderMoreOnOutsideClick(event: MouseEvent) {
  if (!headerMoreOpen.value) return;
  const menuWrap = document.querySelector('.ling-header-more-wrap');
  const target = event.target;
  if (menuWrap && target instanceof Node && !menuWrap.contains(target)) {
    headerMoreOpen.value = false;
  }
}

function closeHeaderMoreOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') headerMoreOpen.value = false;
}

function toggleTheme() {
  isLightTheme.value = !isLightTheme.value;
  const nextTheme = isLightTheme.value ? 'light' : 'dark';
  document.documentElement.dataset.theme = nextTheme;
  localStorage.setItem('lingverse.player.theme', nextTheme);
  headerMoreOpen.value = false;
}

function toggleActionBar() {
  actionBarCollapsed.value = !actionBarCollapsed.value;
}

// ─── 中间面板（ConnectedPanel）grid 列宽拖拽 → 日志和面板同步缩放 ───
// 普通功能抽屉对齐灵界桌面宽度；336px 仅用于传音栏和紧凑断点。
const panelGridMaxPct = ref(0.24);
const panelGridResized = ref(false);
let panelResizeStartPct = 0;

function startPanelGridResize() {
  const defaultWidth = isInlineChat.value
    ? Math.max(360, Math.min(window.innerWidth * 0.24, 520))
    : 360;
  panelResizeStartPct = panelGridResized.value
    ? panelGridMaxPct.value
    : defaultWidth / window.innerWidth;
}

function doPanelGridResize(deltaX: number, containerWidth: number) {
  if (isMobile.value) return;
  const viewportWidth = window.innerWidth || containerWidth || 1000;
  const deltaPct = deltaX / (containerWidth || viewportWidth);
  panelGridMaxPct.value = Math.max(
    260 / viewportWidth,
    Math.min(520 / viewportWidth, panelResizeStartPct - deltaPct),
  );
  panelGridResized.value = true;
}

provide('startPanelGridResize', startPanelGridResize);
provide('doPanelGridResize', doPanelGridResize);

const mainGridStyle = computed(() => {
  if (isInlineChat.value && !gameStore.activePanel) {
    return { gridTemplateColumns: 'minmax(0, 1fr) minmax(300px, 336px)' };
  }
  return { gridTemplateColumns: 'minmax(0, 1fr)' };
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
  void pollServerState();
  pollTimer = setInterval(pollServerState, 30000);
  document.addEventListener('click', closeHeaderMoreOnOutsideClick);
  document.addEventListener('keydown', closeHeaderMoreOnEscape);
  window.addEventListener('resize', updateHeaderVisibleCount);
  void nextTick(() => {
    updateHeaderVisibleCount();
    if (typeof ResizeObserver !== 'undefined') {
      headerResizeObserver = new ResizeObserver(updateHeaderVisibleCount);
      if (headerNavElement.value) headerResizeObserver.observe(headerNavElement.value);
      const header = headerNavElement.value?.closest('.ling-topbar');
      if (header) headerResizeObserver.observe(header);
    }
    if ('fonts' in document) void document.fonts.ready.then(updateHeaderVisibleCount);
  });
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile);
  window.removeEventListener('resize', updateHeaderVisibleCount);
  headerResizeObserver?.disconnect();
  headerResizeObserver = null;
  document.removeEventListener('click', closeHeaderMoreOnOutsideClick);
  document.removeEventListener('keydown', closeHeaderMoreOnEscape);
  document.removeEventListener('mousemove', doResize);
  document.removeEventListener('mouseup', stopResize);
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
  <div class="ling-shell game-page gl-root lingverse-replica-shell">
    <OnboardingGuide />
    <header class="ling-topbar game-header lingverse-replica-header">
      <div class="ling-top-left">
        <button
          class="ling-role-toggle btn-icon"
          :aria-expanded="isMobile ? uiStore.rightSummaryOpen : !desktopPlayerPanelCollapsed"
          aria-controls="playerPanel"
          :title="isMobile ? '切换角色面板' : (desktopPlayerPanelCollapsed ? '展开角色面板' : '收起角色面板')"
          @click="toggleRolePanel"
        >角色</button>
        <div class="ling-logo-block">
          <span class="ling-logo-mark" aria-hidden="true"></span>
          <h1 class="ling-logo">星流</h1>
        </div>
        <div class="ling-header-meta">
          <span class="ling-pill ling-realm">{{ avatarRankLabel(gameStore.player?.avatarRank || gameStore.player?.storyGrade) }}</span>
          <small class="ling-top-worldline">偏移 {{ worldlineValue() }}</small>
        </div>
      </div>

      <div class="ling-top-right">
        <div class="ling-header-player-card">
          <span class="ling-header-player" :title="String(gameStore.playerName || '未命名化身')">{{ gameStore.playerName || '未命名化身' }}</span>
        </div>
        <nav ref="headerNavElement" class="ling-header-nav" aria-label="游戏入口">
          <div v-if="visibleHeaderPrimaryItems.length" class="ling-header-group ling-header-group-primary" aria-label="主线入口">
            <button
              v-for="item in visibleHeaderPrimaryItems"
              :key="item.key"
              :class="['ling-core-btn', { active: gameStore.activePanel === item.panel }]"
              :title="item.title || item.label"
              @click="selectPanel(item.panel)"
            >{{ item.label }}</button>
          </div>
          <div v-if="visibleHeaderSystemItems.length" class="ling-header-group ling-header-group-systems" aria-label="常用系统">
            <button
              v-for="item in visibleHeaderSystemItems"
              :key="item.key"
              :class="['ling-core-btn', { active: gameStore.activePanel === item.panel }]"
              :title="item.title || item.label"
              @click="selectPanel(item.panel)"
            >{{ item.label }}</button>
          </div>
          <div v-if="visibleHeaderExtraItems.length" class="ling-header-group ling-header-group-extra" aria-label="辅助入口">
            <button
              v-for="item in visibleHeaderExtraItems"
              :key="item.key"
              :class="['ling-core-btn', { active: gameStore.activePanel === item.panel }]"
              :title="item.title || item.label"
              @click="selectPanel(item.panel)"
            >{{ item.label }}</button>
          </div>
        </nav>
        <div ref="headerMeasureElement" class="ling-header-nav ling-header-measure" aria-hidden="true">
          <div class="ling-header-group ling-header-group-primary">
            <button v-for="item in headerPrimaryItems" :key="item.key" class="ling-core-btn">{{ item.label }}</button>
          </div>
          <div class="ling-header-group ling-header-group-systems">
            <button v-for="item in headerSystemItems" :key="item.key" class="ling-core-btn">{{ item.label }}</button>
          </div>
          <div class="ling-header-group ling-header-group-extra">
            <button v-for="item in headerExtraItems" :key="item.key" class="ling-core-btn">{{ item.label }}</button>
          </div>
        </div>
        <div class="ling-header-more-wrap">
          <button class="ling-core-btn ling-header-more" :aria-expanded="headerMoreOpen" @click="toggleHeaderMore">更多 ▾</button>
          <div v-if="headerMoreOpen" class="ling-header-more-menu" role="menu">
            <template v-for="section in headerMoreSections" :key="section.label">
              <span class="ling-header-more-section" role="separator">{{ section.label }}</span>
              <button
                v-for="item in section.items"
                :key="item.key"
                class="ling-core-btn"
                :title="item.title || item.label"
                @click="selectPanel(item.panel)"
              >{{ item.label }}</button>
            </template>
            <span class="ling-header-more-section" role="separator">显示</span>
            <button class="ling-core-btn" @click="toggleTheme">{{ isLightTheme ? '夜间' : '日间' }}</button>
            <button class="ling-core-btn ling-header-logout" @click="selectPanel('logout')">退出</button>
          </div>
        </div>
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

    <div :class="['ling-frame', 'game-body', { 'has-side-panel': !isMobile && Boolean(gameStore.activePanel) }]" :style="frameGridStyle">
      <aside
        id="playerPanel"
        class="ling-left player-panel"
        :class="{ 'is-open': uiStore.rightSummaryOpen, 'is-collapsed': !isMobile && desktopPlayerPanelCollapsed }"
        :style="isMobile ? undefined : { width: desktopPlayerPanelCollapsed ? '0px' : leftPanelWidth + 'px' }"
      >
        <PlayerStatusPanel
          :player="gameStore.player"
          @player-updated="(data: unknown) => { if (data) { gameStore.patchPlayer(data as ApiRecord) } else { gameStore.reloadPlayer() } }"
          @open-panel="selectPanel"
        />
      </aside>
      <div v-if="!isMobile && !desktopPlayerPanelCollapsed" class="ling-resize-handle" @mousedown="startLeftResize"></div>

      <main :class="['ling-main', 'main-panel', { 'has-feature-panel': Boolean(gameStore.activePanel) }]" :style="mainGridStyle">
        <GameLogPanel
          v-if="!isMobile || !gameStore.activePanel"
          class="log-area log-area--log"
          :logs="gameStore.recentLogs"
          :inventory="gameStore.inventorySummary"
          :titles="gameStore.titles"
          @select-tab="selectPanel"
        />
        <ConnectedPanel
          v-if="isInlineChat && !gameStore.activePanel"
          class="ling-inline-chat"
          panel="chat"
          @select-panel="selectPanel"
          @close-panel="closePanel"
        />
        <div v-if="isMobile && gameStore.activePanel === 'scene-map'" class="ling-mobile-scene-map">
          <SceneMapPanel @close-panel="closePanel" />
        </div>
        <ConnectedPanel
          v-else-if="isMobile && gameStore.activePanel"
          :panel="gameStore.activePanel"
          @select-panel="selectPanel"
          @close-panel="closePanel"
        />
        <footer
          v-if="!isMobile || !gameStore.activePanel"
          class="ling-bottom action-bar"
          :class="{ 'is-resting': isResting, 'is-collapsed': actionBarCollapsed }"
        >
      <button
        type="button"
        class="action-bar-collapse"
        :aria-expanded="!actionBarCollapsed"
        aria-controls="playerActionBarBody"
        @click="toggleActionBar"
      >
        <span>{{ actionBarCollapsed ? '展开操作' : '收起操作' }}</span>
        <span class="action-bar-collapse-ic" aria-hidden="true"></span>
      </button>

      <div v-show="!actionBarCollapsed" id="playerActionBarBody" class="action-bar-body">
        <div class="action-bar-controls">
          <div class="action-group action-group--primary">
            <button class="ling-action-primary btn-action--primary" @click="toggleRest">
              <Sparkles class="action-icon" :size="15" :stroke-width="1.8" aria-hidden="true" />
              <span>{{ isResting ? '休息中...' : '休息' }}</span>
            </button>
            <button
              class="ling-action-main btn-action--primary"
              :disabled="exploring"
              :title="isResting ? '休息期间不能探索，请先结束休息' : undefined"
              :aria-label="isResting ? '休息期间不能执行场景，请先结束休息' : undefined"
              @click="enterScene"
            >
              <Map class="action-icon" :size="15" :stroke-width="1.8" aria-hidden="true" />
              <span>{{ exploring ? '执行中...' : '执行场景' }}</span>
            </button>
            <button
              v-if="pendingStories.length"
              type="button"
              class="ling-action-primary btn-action--primary ling-action-pending-story"
              :aria-label="`继续处理待选剧情，${pendingStories.length} 个`"
              @click="resumePendingStory"
            >
              <BookOpen class="action-icon" :size="15" :stroke-width="1.8" aria-hidden="true" />
              <span>继续剧情 · {{ pendingStories.length }}</span>
            </button>
            <button
              v-if="pendingBattles.length && !showExploreResult"
              type="button"
              class="ling-action-primary btn-action--primary ling-action-pending-battle"
              :aria-label="`继续处理待战斗遭遇，${pendingBattles.length} 个`"
              @click="resumePendingBattle"
            >
              <Sparkles class="action-icon" :size="15" :stroke-width="1.8" aria-hidden="true" />
              <span>继续遭遇 · {{ pendingBattles.length }}</span>
            </button>
          </div>
          <div class="action-group action-group--tools">
            <select class="ling-select explore-multiplier-trigger" aria-label="探索倍率">
              <option value="1">×1</option>
              <option value="5">×5</option>
              <option value="10">×10</option>
            </select>
            <label class="ling-check"><input type="checkbox" /> 概率修正</label>
            <label class="ling-check"><input type="checkbox" /> 自动行动</label>
            <button type="button" class="btn-setting" title="自动处理设置" aria-label="自动处理设置"><Settings :size="15" :stroke-width="1.8" aria-hidden="true" /></button>
          </div>
        </div>
        <BottomNav :active="gameStore.activePanel" @select="selectPanel" />
      </div>
      <div v-if="isResting && !isMobile" class="ling-footer-status ling-rest-card" aria-live="polite">
        <span class="ling-rest-orb" aria-hidden="true"><Sparkles :size="18" :stroke-width="1.7" /></span>
        <div class="ling-rest-body">
          <div class="ling-rest-title-row">
            <div class="ling-rest-title">
              <strong>休息中</strong>
              <span>生命、体力与经验持续恢复 · 结束休息后可继续探索</span>
            </div>
            <button class="ling-finish" @click="finishAction">结束休息</button>
          </div>
          <div class="ling-rest-stats">
            <span class="ling-rest-stat">生命 <b>+1/{{ restHpInterval }}秒</b></span>
            <span class="ling-rest-stat">体力 <b>+1/{{ restStaminaInterval }}秒</b></span>
            <span class="ling-rest-stat">经验 <b>+1/30秒</b></span>
            <span class="ling-rest-stat ling-rest-stat-total">累计：生命 <b>+{{ hpRecovered }}</b> · 体力 <b>+{{ staminaRecovered }}</b> · 经验 <b>+{{ expRecovered }}</b></span>
          </div>
        </div>
      </div>
      <div v-else-if="isResting" class="ling-footer-status">
        <span class="ling-dot ling-dot-active"></span>
        <span>休息中</span>
        <span>恢复: HP <b>+1/{{ restHpInterval }}秒</b> 体力 <b>+1/{{ restStaminaInterval }}秒</b> 经验 <b>+1/30秒</b></span>
        <span>已恢复: HP <b>+{{ hpRecovered }}</b> 体力 <b>+{{ staminaRecovered }}</b> 经验 <b>+{{ expRecovered }}</b></span>
        <span>结束休息后可继续探索</span>
        <button class="ling-finish" @click="finishAction">结束休息</button>
      </div>
      <div v-else class="ling-footer-status is-idle">
        <span class="ling-dot ling-dot-active"></span>
        <span>行动就绪</span>
        <span>位置：{{ gameStore.player?.currentLocationName || gameStore.player?.currentLocation || '未知场景' }} · 可开始探索或打开功能面板</span>
      </div>
        </footer>
      </main>

      <aside v-if="!isMobile && gameStore.activePanel" class="ling-side-panel">
        <SceneMapPanel
          v-if="gameStore.activePanel === 'scene-map'"
          @close-panel="closePanel"
        />
        <ConnectedPanel
          v-else
          :panel="gameStore.activePanel"
          @select-panel="selectPanel"
          @close-panel="closePanel"
        />
      </aside>

    </div>

    <DeathOverlay v-if="isDead" />
    <GameNotificationToast />
    <GameConfirmDialog />
    <ExploreResultDialog
      :visible="showExploreResult"
      :result="exploreResult"
      @close="onExploreClose"
      @choice-made="onStoryChoiceMade"
      @battle-resolved="onBattleResolved"
    />
  </div>
</template>

<style scoped>
/* 这层只保留组件自身的状态，不再给原站式外壳叠加渐变、浮起或呼吸动画。 */
.gl-root :deep(.ling-logo) {
  background: none !important;
  color: var(--color-star) !important;
  -webkit-text-fill-color: currentColor;
  animation: none !important;
  filter: none !important;
  text-shadow: none !important;
}

.gl-root :deep(.ling-action-main),
.gl-root :deep(.ling-action-primary),
.gl-root :deep(.ling-core-btn) {
  transition: border-color 120ms ease, background 120ms ease, color 120ms ease;
}

.gl-root :deep(.ling-action-main:hover),
.gl-root :deep(.ling-action-primary:hover) {
  transform: none;
}

@media (min-width: 761px) {
  .gl-root :deep(.ling-role-toggle) { display: inline-flex; }
}
</style>
