<script setup lang="ts">
import { ref, computed } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { useGameStore } from '@/stores/gameStore';
import { useUiStore } from '@/stores/uiStore';
import { playerApi } from '@/api/playerApi';
import { gameApi } from '@/api/gameApi';
import { exploreApi, type MapNode } from '@/api/exploreApi';
import { dangerTier } from '@/utils/sceneUtils';
import { getRankGuidance } from '@/utils/rankGuidance';
import StarChartMapCore from './StarChartMapCore.vue';
import { MapPin, RotateCcw, ScrollText, X, Zap } from 'lucide-vue-next';

const emit = defineEmits<{
  'close-panel': [];
}>();

const gameStore = useGameStore();
const uiStore = useUiStore();

const mapRef = ref<InstanceType<typeof StarChartMapCore>>();
const selectedNode = ref<MapNode | null>(null);

const activeVolume = ref(0);
const activeType = ref('all');
const switching = ref(false);

// P1: 从 API 获取真实星图节点数据
const playerId = computed(() => Number(gameStore.player?.id || gameStore.player?.playerId || 0));
const { data: mapNodesData } = useQuery({
  queryKey: ['map-nodes', playerId],
  queryFn: () => exploreApi.getMapNodes(playerId.value),
  enabled: computed(() => playerId.value > 0),
  staleTime: 10_000,
});
const starChartNodes = computed<MapNode[]>(() => mapNodesData.value?.nodes || []);

const volumeProgress = computed(() => {
  const nodes = starChartNodes.value;
  const vols = [1, 2, 3, 4];
  return vols.map(v => {
    const volNodes = nodes.filter(n => n.volume === v);
    const total = volNodes.length;
    const completed = volNodes.filter(n => n.completed).length;
    const pct = total > 0 ? Math.round((completed / total) * 100) : 0;
    return { volume: v, total, completed, pct };
  });
});

const selectedNodeData = computed(() => {
  if (!selectedNode.value) return null;
  return starChartNodes.value.find(n => n.id === selectedNode.value!.id) || null;
});

const selectedRankGuidance = computed(() => getRankGuidance(
  gameStore.player?.avatarRank,
  selectedNodeData.value?.recommendedRank,
));

const isCurrentLocation = computed(() => {
  if (!selectedNodeData.value) return false;
  const p = gameStore.player as Record<string, unknown> | null;
  const cur = gameStore.player?.currentLocation || gameStore.player?.currentLocationKey || p?.['current_location'] || '';
  return cur === selectedNodeData.value.name
    || cur === selectedNodeData.value.id;
});

function onNodeSelect(name: string) {
  if (!name) {
    selectedNode.value = null;
    return;
  }
  const node = starChartNodes.value.find(n => n.name === name);
  selectedNode.value = node || null;
}

function toggleVolume(v: number) {
  activeVolume.value = activeVolume.value === v ? 0 : v;
  mapRef.value?.setFilterVolume(activeVolume.value);
}
function toggleType(t: string) {
  activeType.value = activeType.value === t ? 'all' : t;
  mapRef.value?.setFilterType(activeType.value);
}

async function switchToScene() {
  const node = selectedNodeData.value;
  if (!node) return;
  const id = Number(gameStore.player?.id || gameStore.player?.playerId || 0);
  if (!id) {
    await uiStore.showAlert('错误', '无法获取玩家信息。');
    return;
  }
  switching.value = true;
  try {
    const locationKey = (node as any).locationKey || node.id;

    // 先从后端获取真实解锁状态
    let realUnlocked = node.unlocked; // fallback to mockData
    let eventInfo = '';
    try {
      const locData = await exploreApi.getLocations(id);
      const locations = (locData as any)?.locations || [];
      const backendLoc = locations.find((l: any) => l.location_key === locationKey);
      if (backendLoc) {
        realUnlocked = backendLoc.is_unlocked === true || backendLoc.is_unlocked === 'true';
        const totalStories = (backendLoc.total_stories ?? backendLoc.story_count ?? 0) as number;
        const triggered = (backendLoc.triggered_stories ?? 0) as number;
        const remaining = totalStories - triggered;
        if (totalStories > 0 && remaining > 0) {
          eventInfo = `\n📖 剧情事件: ${triggered}/${totalStories} 已触发 (剩${remaining}个)`;
        }
        if (backendLoc.stories_exhausted) {
          eventInfo = `\n✅ 剧情事件已全部探索完 (${totalStories}个)`;
        }
        const totalEvents = (backendLoc.total_events ?? backendLoc.event_count ?? 0) as number;
        if (totalEvents > 0) {
          eventInfo += ` | 总事件: ${totalEvents}个`;
        }
        if (!realUnlocked) {
          const minLv = (backendLoc.min_level ?? 0) as number;
          await uiStore.showAlert('未解锁', minLv > 0
            ? `「${node.name}」尚未解锁，需要等级 ${minLv}。（你当前等级 ${gameStore.player?.stats?.level || '?'}）`
            : `「${node.name}」尚未解锁，请先完成前置条件。`);
          return;
        }
      }
    } catch { /* ignore fetch failure, fall through to switch */ }

    // 如果后端数据获取失败但 mockData 显示锁住，也阻止
    if (!realUnlocked && !node.unlocked) {
      await uiStore.showAlert('未解锁', '该场景尚未解锁，请先完成前置条件。');
      return;
    }

    await playerApi.switchLocation(id, locationKey);
    const payload = await gameApi.getBootstrap();
    gameStore.applyBootstrap(payload);

    await uiStore.showAlert('切换成功', `已到达「${node.name}」，按底部"进入场景"开始探索。${eventInfo}`);
  } catch (error) {
    await uiStore.showAlert('切换失败', error instanceof Error ? error.message : '无法切换到此场景');
  } finally {
    switching.value = false;
  }
}
</script>

<template>
  <div class="smp-root gc-panel" data-tone="gold">
    <!-- 参考站地图侧栏的 gc-panel 头部；地图数据与切换逻辑仍由本组件负责。 -->
    <header class="smp-panel-head gc-panel-head">
      <div class="gc-rule" aria-hidden="true"></div>
      <div class="gc-title">场景地图</div>
      <div class="gc-panel-sub">{{ gameStore.player?.currentLocationName || gameStore.player?.currentLocation || '当前位置读取中' }}</div>
      <button class="smp-panel-close gc-panel-close" @click="emit('close-panel')" aria-label="关闭地图">×</button>
    </header>

    <!-- 日志条（可折叠） -->
    <div class="smp-log-strip">
      <ScrollText class="smp-log-icon" :size="14" :stroke-width="1.7" aria-hidden="true" />
      <span class="smp-log-text">
        <template v-if="gameStore.recentLogs.length">
          {{ gameStore.recentLogs[0]?.message || gameStore.recentLogs[0]?.['content'] || '最近日志' }}
        </template>
        <template v-else>点击地图节点查看场景信息，再使用底部“执行场景”开始探索。</template>
      </span>
    </div>

    <!-- 筛选胶囊 -->
    <div class="smp-filters">
      <button
        v-for="v in [1,2,3,4]" :key="'v'+v"
        :class="{ active: activeVolume === v }"
        class="smp-chip"
        @click="toggleVolume(v)"
      >第{{ v }}卷</button>
      <span class="smp-fsep">|</span>
      <button
        v-for="t in ['主线','支线','Boss','隐藏']" :key="t"
        :class="{ active: activeType === t }"
        class="smp-chip"
        @click="toggleType(t)"
      >{{ t }}</button>
      <button class="smp-reset" @click="mapRef?.resetView()">
        <RotateCcw :size="12" :stroke-width="1.7" aria-hidden="true" />
        <span>重置</span>
      </button>
    </div>

    <!-- 卷进度 -->
    <div class="smp-progress">
      <div v-for="vp in volumeProgress" :key="'vp'+vp.volume" class="smp-prog-item">
        <span class="smp-prog-label">第{{ vp.volume }}卷</span>
        <div class="smp-prog-bar-wrap">
          <div
            class="smp-prog-fill"
            :style="{
              width: vp.pct + '%',
              background: vp.volume === 1 ? 'var(--color-system-bright)' : vp.volume === 2 ? 'var(--color-danger)' : vp.volume === 3 ? 'var(--color-star)' : 'var(--color-spirit)',
            }"
          ></div>
        </div>
        <span class="smp-prog-stat">{{ vp.completed }}/{{ vp.total }}</span>
      </div>
    </div>

    <!-- 星图 -->
    <div class="smp-map">
      <StarChartMapCore ref="mapRef" :nodes="starChartNodes" @node-select="onNodeSelect" />
    </div>

    <!-- 底部详情Sheet -->
    <transition name="smp-sheet">
      <div v-if="selectedNodeData" class="smp-sheet">
        <div class="smp-sheet-top">
          <div class="smp-sheet-info">
            <span class="smp-sheet-name">{{ selectedNodeData.name }}</span>
            <span :class="['smp-tag', selectedNodeData.type]">
              {{ { main: '主线', side: '支线', hidden: '隐藏', boss: 'Boss' }[selectedNodeData.type] }}
            </span>
            <span class="smp-tag lv">Lv.{{ selectedNodeData.minLevel }}+</span>
            <span class="smp-tag danger" :style="{ color: dangerTier(selectedNodeData.dangerLevel).color, borderColor: dangerTier(selectedNodeData.dangerLevel).color + '66' }">
              <Zap :size="12" :stroke-width="1.8" aria-hidden="true" />{{ dangerTier(selectedNodeData.dangerLevel).label }}级
            </span>
            <span v-if="selectedNodeData.completed" class="smp-done-mark">✓ 已完成</span>
            <span v-else-if="!selectedNodeData.unlocked" class="smp-lock-mark">🔒 未解锁</span>
          </div>
          <div class="smp-sheet-actions">
            <button class="smp-btn-close" @click="selectedNode = null" aria-label="关闭详情">
              <X :size="14" :stroke-width="1.8" aria-hidden="true" />
            </button>
            <button
              v-if="isCurrentLocation"
              class="smp-btn-current"
              disabled
            ><MapPin :size="13" :stroke-width="1.8" aria-hidden="true" />已在此处</button>
            <button
              v-else-if="selectedNodeData.unlocked"
              class="smp-btn-enter"
              :disabled="switching"
              @click="switchToScene"
            >{{ switching ? '切换中...' : '切换到此场景' }}</button>
          </div>
        </div>
        <p class="smp-sheet-desc">{{ selectedNodeData.description }}</p>
        <div
          v-if="selectedRankGuidance"
          :class="['smp-rank-guidance', `is-${selectedRankGuidance.comparison}`]"
          role="note"
        >
          <div class="smp-rank-guidance-head">
            <span>当前 {{ selectedRankGuidance.currentRank }} 位格</span>
            <span class="smp-rank-guidance-arrow" aria-hidden="true">→</span>
            <span>建议 {{ selectedRankGuidance.recommendedRank }} 位格</span>
            <strong>{{ selectedRankGuidance.effectLabel }}</strong>
          </div>
          <p>{{ selectedRankGuidance.details }}</p>
        </div>
        <div v-if="selectedNodeData.keyItems.length" class="smp-sheet-items">
          <span v-for="item in selectedNodeData.keyItems" :key="item" class="smp-item-tag">{{ item }}</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.smp-root {
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  overflow: hidden;
  background: var(--color-panel);
}

.smp-panel-head {
  position: relative;
  flex: 0 0 auto;
  padding: 14px 48px 8px 16px;
  background: var(--color-panel);
  border-bottom: 1px solid var(--color-border);
}

.smp-panel-head .gc-rule {
  width: 36px;
  height: 3px;
  margin-bottom: 8px;
  border-radius: 999px;
  background: var(--color-star);
}

.smp-panel-head .gc-title {
  color: var(--color-text);
  font-family: "Noto Serif SC", "Source Han Serif SC", "STSong", serif;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 2px;
  line-height: 1.35;
}

.smp-panel-head .gc-panel-sub {
  margin-top: 4px;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 11px;
  letter-spacing: 1px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.smp-panel-close {
  position: absolute;
  top: 12px;
  right: 14px;
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 2px;
  background: transparent;
  color: var(--color-text-dim);
  font-size: 18px;
  cursor: pointer;
}

.smp-panel-close:hover {
  border-color: var(--color-danger);
  background: var(--color-danger-glow);
  color: var(--color-danger-bright);
}

/* log */
.smp-log-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 14px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-log);
  font-size: 11px;
  color: var(--color-muted);
  flex-shrink: 0;
}
.smp-log-icon { flex-shrink: 0; }
.smp-log-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* filters */
.smp-filters {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 14px;
  border-bottom: 1px solid var(--color-border);
  overflow-x: auto;
  flex-shrink: 0;
}
.smp-chip {
  padding: 2px 10px;
  border: 1px solid var(--color-border);
  border-radius: 3px;
  background: var(--color-input);
  color: var(--color-muted);
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.smp-chip:hover { border-color: var(--color-system-border); color: var(--color-text-dim); }
.smp-chip.active {
  border-color: var(--color-system);
  color: var(--color-system-bright);
  background: var(--color-system-bg);
}
.smp-fsep { color: var(--color-border); margin: 0 3px; font-size: 11px; }
.smp-reset {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  padding: 2px 10px;
  border: 1px solid var(--color-border);
  border-radius: 3px;
  background: transparent;
  color: var(--color-muted);
  font-size: 10px;
  cursor: pointer;
  white-space: nowrap;
}
.smp-close {
  padding: 2px 8px;
  border: 1px solid var(--color-border);
  border-radius: 3px;
  background: transparent;
  color: var(--color-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  line-height: 1;
}
.smp-close:hover {
  color: var(--color-danger-bright);
  border-color: var(--color-danger-glow);
  background: var(--color-danger-glow);
}

/* progress */
.smp-progress {
  display: flex;
  gap: 4px;
  padding: 3px 14px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-log);
  flex-shrink: 0;
  overflow-x: auto;
}
.smp-prog-item {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.smp-prog-label {
  font-size: 9px;
  color: var(--color-muted);
  white-space: nowrap;
  flex-shrink: 0;
}
.smp-prog-bar-wrap {
  flex: 1;
  height: 3px;
  background: var(--color-border);
  border-radius: 2px;
  overflow: hidden;
  min-width: 20px;
}
.smp-prog-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.5s;
}
.smp-prog-stat {
  font-size: 9px;
  color: var(--color-muted);
  flex-shrink: 0;
}

/* map */
.smp-map {
  flex: 1;
  overflow: hidden;
}

/* sheet */
.smp-sheet {
  border-top: 1px solid var(--color-border);
  background: linear-gradient(180deg, var(--color-panel-elevated), var(--color-void));
  padding: 12px 16px 14px;
  flex-shrink: 0;
}
.smp-sheet-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}
.smp-sheet-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.smp-sheet-name {
  font-weight: 700;
  font-size: 15px;
  color: var(--color-system-bright);
}
.smp-tag {
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 2px;
  border: 1px solid;
}
.smp-tag.main { color: var(--color-system-bright); border-color: var(--color-system-border); }
.smp-tag.side { color: var(--color-star); border-color: var(--color-star-border); }
.smp-tag.hidden { color: var(--color-spirit); border-color: var(--color-spirit-glow); }
.smp-tag.boss { color: var(--color-danger); border-color: var(--color-danger-glow); }
.smp-tag.lv { color: var(--color-star); border-color: var(--color-star-border); }
.smp-tag.danger {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--color-danger);
  border-color: var(--color-danger-glow);
  font-size: 9px;
}
.smp-done-mark { font-size: 12px; color: var(--color-spirit); }
.smp-lock-mark { font-size: 12px; color: var(--color-muted); }

.smp-sheet-actions { display: flex; gap: 8px; flex-shrink: 0; }
.smp-btn-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  min-height: 28px;
  padding: 4px 12px;
  border: 1px solid var(--color-border);
  border-radius: 3px;
  background: transparent;
  color: var(--color-muted);
  font-size: 12px;
  cursor: pointer;
}
.smp-btn-enter {
  padding: 6px 20px;
  border: 1px solid var(--color-system);
  border-radius: 3px;
  background: var(--color-system-bg);
  color: var(--color-system-bright);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}
.smp-btn-enter:hover {
  background: rgba(var(--color-system-rgb), 0.22);
  box-shadow: var(--glow-system);
}
.smp-btn-enter:disabled { opacity: 0.5; cursor: not-allowed; }
.smp-btn-current {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 20px;
  border: 1px solid var(--color-spirit);
  border-radius: 3px;
  background: var(--color-spirit-glow);
  color: var(--color-spirit);
  font-size: 13px;
  cursor: default;
}

.smp-sheet-desc {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--color-text-dim);
  line-height: 1.5;
}

.smp-rank-guidance {
  margin-top: 10px;
  padding: 9px 11px;
  border: 1px solid var(--color-system-border);
  border-left: 2px solid var(--color-system);
  background: var(--color-system-bg);
}

.smp-rank-guidance.is-below {
  border-color: var(--color-star-border);
  border-left-color: var(--color-star);
  background: var(--color-star-bg);
}

.smp-rank-guidance-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px 9px;
  color: var(--color-text);
  font-size: 11px;
  font-weight: 600;
}

.smp-rank-guidance.is-below .smp-rank-guidance-head strong {
  color: var(--color-star-bright);
}

.smp-rank-guidance-arrow {
  color: var(--color-muted);
}

.smp-rank-guidance p {
  margin: 5px 0 0;
  color: var(--color-text-dim);
  font-size: 11px;
  line-height: 1.55;
}
.smp-sheet-items { display: flex; gap: 6px; flex-wrap: wrap; }
.smp-item-tag {
  padding: 1px 8px;
  border: 1px solid var(--color-border);
  border-radius: 2px;
  font-size: 11px;
  color: var(--color-spirit);
  background: var(--color-spirit-glow);
}

/* transition */
.smp-sheet-enter-active { animation: smpSlideUp 0.2s ease-out; }
.smp-sheet-leave-active { animation: smpSlideUp 0.15s ease-in reverse; }
@keyframes smpSlideUp {
  from { transform: translateY(100%); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
</style>
