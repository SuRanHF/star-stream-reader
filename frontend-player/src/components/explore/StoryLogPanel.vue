<script setup lang="ts">
import { ref, watch } from 'vue';
import { exploreApi, type StoryLogEntry } from '@/api/exploreApi';
import { useGameStore } from '@/stores/gameStore';

const game = useGameStore();
const loading = ref(false);
const logs = ref<StoryLogEntry[]>([]);
const expandedLoc = ref<string | null>(null);

const props = defineProps<{
  visible: boolean;
}>();

watch(() => props.visible, async (v) => {
  if (v) {
    await fetchLogs();
  }
});

async function fetchLogs() {
  const id = Number(game.player?.id || game.player?.playerId || 0);
  if (!id) return;
  loading.value = true;
  try {
    const payload = await exploreApi.getStoryLog(id);
    logs.value = payload.story_log || [];
  } catch {
    logs.value = [];
  } finally {
    loading.value = false;
  }
}

function toggleLoc(key: string) {
  expandedLoc.value = expandedLoc.value === key ? null : key;
}
</script>

<template>
  <div v-if="visible" class="slp-root">
    <div class="slp-header">
      <h3 class="slp-title">📖 故事回顾</h3>
      <span class="slp-subtitle">只读记录 · 你的旅程</span>
    </div>

    <div v-if="loading" class="slp-loading">加载中...</div>

    <div v-else-if="!logs.length" class="slp-empty">
      <p>暂无故事记录</p>
      <p class="slp-empty-hint">在每个场景中触发故事事件后，你的选择将记录在这里。</p>
    </div>

    <div v-else class="slp-list">
      <div
        v-for="grp in logs"
        :key="grp.location_key"
        class="slp-loc-group"
      >
        <button class="slp-loc-header" @click="toggleLoc(grp.location_key)">
          <span class="slp-loc-name">📍 {{ grp.location_name }}</span>
          <span class="slp-loc-count">{{ grp.stories.length }} 个故事</span>
          <span class="slp-chevron">{{ expandedLoc === grp.location_key ? '▾' : '▸' }}</span>
        </button>

        <div v-if="expandedLoc === grp.location_key" class="slp-stories">
          <div
            v-for="(s, i) in grp.stories"
            :key="i"
            class="slp-story-card"
          >
            <div class="slp-story-name">{{ s.event_name }}</div>
            <div class="slp-choice-line">
              <span class="slp-choice-label">你的选择：</span>
              <span class="slp-choice-text">{{ s.choice_label || '（未记录）' }}</span>
            </div>
            <div v-if="s.consequence_text" class="slp-cons-line">
              {{ s.consequence_text }}
            </div>
            <div class="slp-time">{{ s.created_at }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.slp-root {
  padding: 16px;
  height: 100%;
  overflow-y: auto;
}
.slp-header {
  margin-bottom: 16px;
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.slp-title {
  color: #caa86a;
  font-size: 16px;
  font-weight: 700;
  margin: 0;
}
.slp-subtitle {
  color: #64748b;
  font-size: 12px;
}
.slp-loading, .slp-empty {
  text-align: center;
  padding: 40px 0;
  color: #64748b;
  font-size: 13px;
}
.slp-empty-hint {
  font-size: 12px;
  color: #4a5555;
  margin-top: 6px;
}

.slp-loc-group {
  margin-bottom: 8px;
}
.slp-loc-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #28343d;
  border-radius: 8px;
  background: #0f1519;
  color: #cbd5e1;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.slp-loc-header:hover {
  border-color: #caa86a;
  background: rgba(202, 168, 106, 0.05);
}
.slp-loc-name {
  flex: 1;
  text-align: left;
  font-weight: 600;
}
.slp-loc-count {
  font-size: 12px;
  color: #64748b;
}
.slp-chevron {
  color: #64748b;
  font-size: 12px;
}

.slp-stories {
  padding: 8px 0 0 12px;
  border-left: 1px solid #28343d;
  margin-left: 8px;
}
.slp-story-card {
  padding: 10px 12px;
  margin-bottom: 6px;
  border: 1px solid #1e2a33;
  border-radius: 8px;
  background: rgba(15, 21, 25, 0.5);
}
.slp-story-name {
  color: #caa86a;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}
.slp-choice-line {
  display: flex;
  gap: 4px;
  font-size: 12px;
  margin-bottom: 4px;
}
.slp-choice-label {
  color: #64748b;
  flex-shrink: 0;
}
.slp-choice-text {
  color: #93c5fd;
}
.slp-cons-line {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
  margin-bottom: 4px;
}
.slp-time {
  font-size: 11px;
  color: #4a5555;
}
</style>
