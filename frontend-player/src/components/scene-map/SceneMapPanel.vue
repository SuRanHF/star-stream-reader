<script setup lang="ts">
import { ref, computed } from 'vue';
import { useGameStore } from '@/stores/gameStore';
import { useUiStore } from '@/stores/uiStore';
import { playerApi } from '@/api/playerApi';
import { gameApi } from '@/api/gameApi';
import { exploreApi } from '@/api/exploreApi';
import StarChartMapCore from './StarChartMapCore.vue';
import { starChartNodes, type SceneNode } from './mockData';

const emit = defineEmits<{
  'close-panel': [];
}>();

const gameStore = useGameStore();
const uiStore = useUiStore();

const mapRef = ref<InstanceType<typeof StarChartMapCore>>();
const selectedNode = ref<SceneNode | null>(null);

const activeVolume = ref(0);
const activeType = ref('all');
const switching = ref(false);

const selectedNodeData = computed(() => {
  if (!selectedNode.value) return null;
  const found = starChartNodes.find(n => n.id === selectedNode.value!.id);
  return found || null;
});

const isCurrentLocation = computed(() => {
  if (!selectedNodeData.value) return false;
  const p = gameStore.player as Record<string, unknown> | null;
  const cur = gameStore.player?.currentLocation || gameStore.player?.currentLocationKey || p?.['current_location'] || '';
  return cur === selectedNodeData.value.name
    || cur === selectedNodeData.value.id
    || cur === (selectedNodeData.value as any).locationKey;
});

function onNodeSelect(name: string) {
  if (!name) {
    selectedNode.value = null;
    return;
  }
  const node = starChartNodes.find(n => n.name === name);
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
    const locationKey = (node as any).locationKey || node.name;

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
  <div class="smp-root">
    <!-- 日志条（可折叠） -->
    <div class="smp-log-strip">
      <span class="smp-log-icon">📜</span>
      <span class="smp-log-text">
        <template v-if="gameStore.recentLogs.length">
          {{ gameStore.recentLogs[0]?.message || gameStore.recentLogs[0]?.['content'] || '最近日志' }}
        </template>
        <template v-else>点击星图节点选择场景，然后按底部"进入场景"开始探索</template>
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
      <button class="smp-reset" @click="mapRef?.resetView()">🔄 重置</button>
      <button class="smp-close" @click="emit('close-panel')" title="关闭场景地图">✕</button>
    </div>

    <!-- 星图 -->
    <div class="smp-map">
      <StarChartMapCore ref="mapRef" @node-select="onNodeSelect" />
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
            <span class="smp-tag danger">{{ '★'.repeat(selectedNodeData.dangerLevel) }}</span>
            <span v-if="selectedNodeData.completed" class="smp-done-mark">✓ 已完成</span>
            <span v-else-if="!selectedNodeData.unlocked" class="smp-lock-mark">🔒 未解锁</span>
          </div>
          <div class="smp-sheet-actions">
            <button class="smp-btn-close" @click="selectedNode = null">✕</button>
            <button
              v-if="isCurrentLocation"
              class="smp-btn-current"
              disabled
            >📍 已在此处</button>
            <button
              v-else-if="selectedNodeData.unlocked"
              class="smp-btn-enter"
              :disabled="switching"
              @click="switchToScene"
            >{{ switching ? '切换中...' : '切换到此场景' }}</button>
          </div>
        </div>
        <p class="smp-sheet-desc">{{ selectedNodeData.description }}</p>
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
  height: 100%;
  overflow: hidden;
}

/* log */
.smp-log-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 14px;
  border-bottom: 1px solid #1a242b;
  background: #0a0e10;
  font-size: 11px;
  color: #4a5a5d;
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
  border-bottom: 1px solid #1e2a33;
  overflow-x: auto;
  flex-shrink: 0;
}
.smp-chip {
  padding: 2px 10px;
  border: 1px solid #28343d;
  border-radius: 3px;
  background: #0f1519;
  color: #7e9292;
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.smp-chip:hover { border-color: #4a5a5d; color: #c9d8d5; }
.smp-chip.active {
  border-color: #caa86a;
  color: #caa86a;
  background: rgba(202,168,106,0.08);
}
.smp-fsep { color: #1e2a33; margin: 0 3px; font-size: 11px; }
.smp-reset {
  margin-left: auto;
  padding: 2px 10px;
  border: 1px solid #28343d;
  border-radius: 3px;
  background: transparent;
  color: #7e9292;
  font-size: 10px;
  cursor: pointer;
  white-space: nowrap;
}
.smp-close {
  padding: 2px 8px;
  border: 1px solid #28343d;
  border-radius: 3px;
  background: transparent;
  color: #7e9292;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  line-height: 1;
}
.smp-close:hover {
  color: #d97b6c;
  border-color: #3a2a28;
  background: rgba(217, 123, 108, 0.08);
}

/* map */
.smp-map {
  flex: 1;
  overflow: hidden;
}

/* sheet */
.smp-sheet {
  border-top: 1px solid #28343d;
  background: #0f1519;
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
  color: #caa86a;
}
.smp-tag {
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 2px;
  border: 1px solid;
}
.smp-tag.main { color: #caa86a; border-color: #554b34; }
.smp-tag.side { color: #8db8d8; border-color: #2d3a4a; }
.smp-tag.hidden { color: #98c9bb; border-color: #2a3a35; }
.smp-tag.boss { color: #d97b6c; border-color: #3a2a28; }
.smp-tag.lv { color: #8db8d8; border-color: #2d3a4a; }
.smp-tag.danger { color: #d97b6c; border-color: #3a2a28; font-size: 9px; }
.smp-done-mark { font-size: 12px; color: #98c9bb; }
.smp-lock-mark { font-size: 12px; color: #4a5555; }

.smp-sheet-actions { display: flex; gap: 8px; flex-shrink: 0; }
.smp-btn-close {
  padding: 4px 12px;
  border: 1px solid #28343d;
  border-radius: 3px;
  background: transparent;
  color: #7e9292;
  font-size: 12px;
  cursor: pointer;
}
.smp-btn-enter {
  padding: 6px 20px;
  border: 1px solid #caa86a;
  border-radius: 3px;
  background: rgba(202,168,106,0.12);
  color: #caa86a;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.smp-btn-enter:hover { background: rgba(202,168,106,0.22); }
.smp-btn-enter:disabled { opacity: 0.5; cursor: not-allowed; }
.smp-btn-current {
  padding: 6px 20px;
  border: 1px solid #98c9bb;
  border-radius: 3px;
  background: rgba(152,201,187,0.08);
  color: #98c9bb;
  font-size: 13px;
  cursor: default;
}

.smp-sheet-desc {
  margin: 0 0 6px;
  font-size: 12px;
  color: #9aaca8;
  line-height: 1.5;
}
.smp-sheet-items { display: flex; gap: 6px; flex-wrap: wrap; }
.smp-item-tag {
  padding: 1px 8px;
  border: 1px solid #28343d;
  border-radius: 2px;
  font-size: 11px;
  color: #98c9bb;
  background: rgba(152,201,187,0.05);
}

/* transition */
.smp-sheet-enter-active { animation: slideUp 0.2s ease-out; }
.smp-sheet-leave-active { animation: slideUp 0.15s ease-in reverse; }
@keyframes slideUp {
  from { transform: translateY(100%); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
</style>
