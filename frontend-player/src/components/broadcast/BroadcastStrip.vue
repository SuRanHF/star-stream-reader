<script setup lang="ts">
import { computed } from 'vue';
import { useGameStore } from '@/stores/gameStore';
import type { ApiRecord } from '@/types/api';

const gameStore = useGameStore();

const summaryText = computed(() => {
  const s = gameStore.broadcastSummary;
  if (!s) return '暂无活跃事件';

  const topEvents = s.topEvents as ApiRecord[] | undefined;
  if (topEvents && topEvents.length > 0 && topEvents[0]) {
    const ev = topEvents[0];
    const title = String(ev.title || ev.eventName || '');
    const pct = ev.progressPercent ?? ev.progress;
    if (title) {
      return typeof pct === 'number' ? `${title} (${Math.round(pct)}%)` : title;
    }
  }

  const activeEvents = s.activeEvents as ApiRecord[] | undefined;
  if (activeEvents && activeEvents.length > 0 && activeEvents[0]) {
    const first = activeEvents[0];
    return String(first.title || first.eventName || '活跃事件进行中');
  }

  const count = Number(s.activeCount || 0);
  if (count > 0) return `${count} 个活跃放送事件`;

  return '暂无活跃事件';
});
</script>

<template>
  <div class="ling-broadcast-strip">
    <span class="ling-broadcast-icon">&#9733;</span>
    <span class="ling-broadcast-label">星流放送</span>
    <span class="ling-broadcast-sep">：</span>
    <span class="ling-broadcast-text">{{ summaryText }}</span>
  </div>
</template>

<style scoped>
.ling-broadcast-strip {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px 16px;
  background: rgba(180, 160, 120, 0.08);
  border-bottom: 1px solid rgba(180, 160, 120, 0.15);
  font-size: 12px;
  color: #c0b090;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ling-broadcast-icon {
  color: #c9a86c;
  font-size: 13px;
  margin-right: 4px;
}
.ling-broadcast-label {
  font-weight: 700;
  color: #c9a86c;
  letter-spacing: 0.5px;
}
.ling-broadcast-sep {
  color: rgba(180, 160, 120, 0.5);
  margin: 0 2px;
}
.ling-broadcast-text {
  color: #a0a8b8;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
