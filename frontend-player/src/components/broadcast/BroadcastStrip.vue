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
  padding: 6px 20px;
  background: linear-gradient(90deg, rgba(74, 143, 231, 0.06) 0%, rgba(74, 143, 231, 0.02) 100%);
  border-bottom: 1px solid rgba(74, 143, 231, 0.1);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #8898b8;
  letter-spacing: 0.3px;
}
.ling-broadcast-icon {
  color: var(--color-system-bright);
  font-size: 13px;
  margin-right: 4px;
  text-shadow: 0 0 6px rgba(74, 143, 231, 0.4);
  animation: pulseGlow 3s ease-in-out infinite;
}
.ling-broadcast-label {
  font-weight: 700;
  color: var(--color-system-bright);
  letter-spacing: 0.5px;
  text-shadow: 0 0 4px rgba(74, 143, 231, 0.2);
}
.ling-broadcast-sep {
  color: rgba(74, 143, 231, 0.3);
  margin: 0 3px;
}
.ling-broadcast-text {
  color: #7a8ca8;
  overflow: hidden;
  text-overflow: ellipsis;
}
@keyframes pulseGlow {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
</style>
