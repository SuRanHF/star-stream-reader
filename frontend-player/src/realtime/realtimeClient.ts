import { useChatStore } from '@/stores/chatStore';
import { useGameStore } from '@/stores/gameStore';
import { useQuestStore } from '@/stores/questStore';
import { useRealtimeStore } from '@/stores/realtimeStore';
import { useWorldBossStore } from '@/stores/worldBossStore';
import type { ApiRecord } from '@/types/api';
import type { RealtimeEvent, RealtimeHandler } from '@/types/realtime';

class RealtimeClient {
  private socket: WebSocket | null = null;
  private token = '';
  private shouldReconnect = true;
  private reconnectTimer: number | null = null;
  private handlers = new Map<string, Set<RealtimeHandler>>();

  connect(token: string) {
    this.token = token;
    this.shouldReconnect = true;
    this.clearReconnectTimer();

    if (!token) return;
    if (this.socket && this.socket.readyState === WebSocket.OPEN) return;

    const realtimeStore = useRealtimeStore();
    realtimeStore.setStatus('connecting');

    const base = import.meta.env.VITE_WS_BASE_URL || `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/game`;
    const url = `${base}?token=${encodeURIComponent(token)}`;
    this.socket = new WebSocket(url);

    this.socket.onopen = () => {
      useRealtimeStore().setStatus('connected');
    };

    this.socket.onmessage = (event) => {
      this.handleMessage(event.data);
    };

    this.socket.onerror = () => {
      useRealtimeStore().setError('WebSocket 连接异常');
    };

    this.socket.onclose = () => {
      const store = useRealtimeStore();
      store.setStatus(this.shouldReconnect ? 'reconnecting' : 'closed');
      if (this.shouldReconnect) {
        this.scheduleReconnect();
      }
    };
  }

  disconnect() {
    this.shouldReconnect = false;
    this.clearReconnectTimer();
    this.socket?.close();
    this.socket = null;
    useRealtimeStore().setStatus('closed');
  }

  reconnect() {
    this.disconnect();
    this.shouldReconnect = true;
    this.connect(this.token);
  }

  on(type: string, handler: RealtimeHandler) {
    const bucket = this.handlers.get(type) || new Set<RealtimeHandler>();
    bucket.add(handler);
    this.handlers.set(type, bucket);
  }

  off(type: string, handler: RealtimeHandler) {
    this.handlers.get(type)?.delete(handler);
  }

  handleMessage(raw: string) {
    try {
      const event = JSON.parse(raw) as RealtimeEvent;
      if (!event.type) return;

      console.debug('[realtime]', event);
      this.applyStoreUpdates(event);
      this.handlers.get(event.type)?.forEach((handler) => handler(event));
    } catch (error) {
      console.debug('[realtime] ignored invalid message', error);
    }
  }

  private scheduleReconnect() {
    const store = useRealtimeStore();
    store.bumpReconnectAttempts();
    const delay = Math.min(30000, 1000 * 2 ** Math.max(0, store.reconnectAttempts - 1));
    this.reconnectTimer = window.setTimeout(() => {
      this.connect(this.token);
    }, delay);
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private applyStoreUpdates(event: RealtimeEvent) {
    const data = (event.data || {}) as ApiRecord;
    const gameStore = useGameStore();

    if (event.type === 'online.summary') gameStore.setOnlineSummary(data);
    if (event.type === 'worldline.updated') gameStore.setWorldlineSummary(data);
    if (event.type === 'broadcast.progress.updated') gameStore.setBroadcastSummary(data);
    if (event.type === 'quest.completed') {
      gameStore.markQuestClaimable(data);
      useQuestStore().markClaimable();
    }
    if (event.type === 'worldBoss.hp.updated') {
      gameStore.setWorldBossSummary(data);
      useWorldBossStore().patchSummary(data);
    }
    if (event.type === 'chat.message') useChatStore().addMessage(data);
    if (event.type === 'system.message') useChatStore().addSystemMessage(data);
    if (event.type === 'stats.updated') gameStore.patchPlayer(data);
  }
}

export const realtimeClient = new RealtimeClient();
