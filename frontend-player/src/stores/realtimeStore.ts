import { ref } from 'vue';
import { defineStore } from 'pinia';
import type { RealtimeStatus } from '@/types/realtime';

export const useRealtimeStore = defineStore('realtime', () => {
  const status = ref<RealtimeStatus>('idle');
  const lastError = ref<string>('');
  const reconnectAttempts = ref(0);
  const connectedAt = ref<string>('');

  function setStatus(nextStatus: RealtimeStatus) {
    status.value = nextStatus;
    if (nextStatus === 'connected') {
      connectedAt.value = new Date().toISOString();
      reconnectAttempts.value = 0;
      lastError.value = '';
    }
  }

  function setError(message: string) {
    status.value = 'error';
    lastError.value = message;
  }

  function bumpReconnectAttempts() {
    reconnectAttempts.value += 1;
  }

  return { status, lastError, reconnectAttempts, connectedAt, setStatus, setError, bumpReconnectAttempts };
});
