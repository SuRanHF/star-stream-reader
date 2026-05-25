<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { realtimeClient } from '@/realtime/realtimeClient';
import type { RealtimeEvent } from '@/types/realtime';

interface Toast {
  id: number;
  title: string;
  content: string;
  type: string;
  createdAt: number;
}

const toasts = ref<Toast[]>([]);
let nextId = 1;
const MAX_TOASTS = 5;
const DISPLAY_MS = 6000;

const SYSTEM_LABELS: Record<string, string> = {
  'broadcast.progress.updated': '星流放送',
  'worldBoss.hp.updated': '世界Boss',
  'worldline.updated': '世界线',
  'quest.completed': '任务完成',
  'system.message': '系统',
  'chat.message': '频道',
  'online.summary': '在线',
};

function addToast(title: string, content: string, type: string) {
  const id = nextId++;
  toasts.value.push({ id, title, content, type, createdAt: Date.now() });
  if (toasts.value.length > MAX_TOASTS) toasts.value.shift();
  setTimeout(() => {
    toasts.value = toasts.value.filter((t) => t.id !== id);
  }, DISPLAY_MS);
}

function handleSystemMessage(event: RealtimeEvent) {
  const data = event.data as Record<string, unknown> | undefined;
  if (!data) return;
  const label = SYSTEM_LABELS[event.type] || event.type;
  const content = String(data.content || data.message || data.text || '');
  if (content) {
    addToast(label, content, event.type);
  }
}

function handleBroadcastUpdate(event: RealtimeEvent) {
  const data = event.data as Record<string, unknown> | undefined;
  if (!data) return;
  const activeEvents = data.activeEvents;
  if (Array.isArray(activeEvents) && activeEvents.length > 0) {
    const first = activeEvents[0] as Record<string, unknown>;
    const title = String(first.title || '星流放送');
    const status = String(first.status || '');
    addToast('星流放送', `"${title}" 状态: ${status}`, event.type);
  } else {
    const count = data.activeCount;
    addToast('星流放送', `活跃事件: ${count ?? 0} 个`, event.type);
  }
}

function handleWorldBossUpdate(event: RealtimeEvent) {
  addToast('世界Boss', '世界Boss状态已更新', event.type);
}

function handleWorldlineUpdate(event: RealtimeEvent) {
  addToast('世界线', '世界线偏移值发生变化', event.type);
}

function handleQuestCompleted(event: RealtimeEvent) {
  addToast('任务', '有新的任务已完成，请前往领取奖励！', event.type);
}

function handleEvent(event: RealtimeEvent) {
  switch (event.type) {
    case 'system.message': handleSystemMessage(event); break;
    case 'broadcast.progress.updated': handleBroadcastUpdate(event); break;
    case 'worldBoss.hp.updated': handleWorldBossUpdate(event); break;
    case 'worldline.updated': handleWorldlineUpdate(event); break;
    case 'quest.completed': handleQuestCompleted(event); break;
  }
}

onMounted(() => {
  realtimeClient.on('system.message', handleEvent);
  realtimeClient.on('broadcast.progress.updated', handleEvent);
  realtimeClient.on('worldBoss.hp.updated', handleEvent);
  realtimeClient.on('worldline.updated', handleEvent);
  realtimeClient.on('quest.completed', handleEvent);
});

onBeforeUnmount(() => {
  realtimeClient.off('system.message', handleEvent);
  realtimeClient.off('broadcast.progress.updated', handleEvent);
  realtimeClient.off('worldBoss.hp.updated', handleEvent);
  realtimeClient.off('worldline.updated', handleEvent);
  realtimeClient.off('quest.completed', handleEvent);
});
</script>

<template>
  <Teleport to="body">
    <div class="ling-notification-stack">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="ling-notification-toast"
      >
        <div class="ling-notification-head">
          <span class="ling-notification-type">{{ toast.title }}</span>
        </div>
        <div class="ling-notification-body">{{ toast.content }}</div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ling-notification-stack {
  position: fixed;
  top: 72px;
  right: 24px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
  max-width: 340px;
}
.ling-notification-toast {
  background: rgba(10, 14, 28, 0.92);
  border: 1px solid rgba(180, 160, 120, 0.35);
  border-radius: 8px;
  padding: 10px 14px;
  animation: ling-toast-in 0.35s ease-out, ling-toast-fade 0.5s ease-out 5.5s forwards;
  backdrop-filter: blur(8px);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.45);
}
.ling-notification-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.ling-notification-type {
  font-size: 11px;
  font-weight: 700;
  color: #c9a86c;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.ling-notification-body {
  font-size: 13px;
  color: #d0d0d0;
  line-height: 1.4;
}
@keyframes ling-toast-in {
  0% { opacity: 0; transform: translateX(40px); }
  100% { opacity: 1; transform: translateX(0); }
}
@keyframes ling-toast-fade {
  0% { opacity: 1; }
  100% { opacity: 0; }
}
</style>
