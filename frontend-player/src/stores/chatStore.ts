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

  return { messages, systemMessages, addMessage, addSystemMessage };
});
