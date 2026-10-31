import { ref } from 'vue';
import { defineStore } from 'pinia';
import type { RealtimeStatus } from '@/types/realtime';

export const useRealtimeStore = defineStore('realtime', () => {
  const status = ref<RealtimeStatus>('idle');
  const lastError = ref<string>('');
  const reconnectAttempts = ref(0);
  const connectedAt = ref<string>('');
  // 服务端在连接欢迎消息和 online.summary 事件里推送；未连接时为 null。
  const onlineCount = ref<number | null>(null);

  function setOnlineCount(value: unknown) {
    const next = typeof value === 'number' ? value : typeof value === 'string' && value.trim() ? Number(value) : NaN;
    onlineCount.value = Number.isFinite(next) && next >= 0 ? next : null;
  }

  function setStatus(nextStatus: RealtimeStatus) {
    status.value = nextStatus;
    // 断线后不再展示上一次的在线人数，重连后由 welcome 重新填入。
    if (nextStatus !== 'connected') onlineCount.value = null;
    if (nextStatus === 'connected') {
      connectedAt.value = new Date().toISOString();
      reconnectAttempts.value = 0;
      lastError.value = '';
    }
  }

  function setError(message: string) {
    status.value = 'error';
    onlineCount.value = null;
    lastError.value = message;
  }

  function bumpReconnectAttempts() {
    reconnectAttempts.value += 1;
  }

  return { status, lastError, reconnectAttempts, connectedAt, onlineCount, setStatus, setError, setOnlineCount, bumpReconnectAttempts };
});
