import { ref } from 'vue';
import { defineStore } from 'pinia';
import type { ApiRecord } from '@/types/api';

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ApiRecord[]>([]);
  const systemMessages = ref<ApiRecord[]>([]);

  function addMessage(message: ApiRecord) {
    messages.value = [...messages.value.slice(-80), message];
  }

  function addSystemMessage(message: ApiRecord) {
    systemMessages.value = [...systemMessages.value.slice(-40), message];
  }

  // 服务端历史是当前归属的权威结果：同频道中比最新历史还旧、却不在历史里的实时消息
  // 来自之前的队伍 / 阵营（或已被服务端排除），一并剔除。
  function pruneChannel(channel: string, historyIds: number[]) {
    if (!historyIds.length) {
      messages.value = messages.value.filter((entry) => String(entry.channel || '').toLowerCase() !== channel);
      return;
    }
    const keep = new Set(historyIds);
    const newest = Math.max(...historyIds);
    messages.value = messages.value.filter((entry) => {
      if (String(entry.channel || '').toLowerCase() !== channel) return true;
      const id = Number(entry.id);
      return keep.has(id) || id > newest;
    });
  }

  // 切换玩家或退出登录时调用，避免把上一个身份收到的消息带过来。
  function clear() {
    messages.value = [];
    systemMessages.value = [];
  }

  return { messages, systemMessages, addMessage, addSystemMessage, pruneChannel, clear };
});
