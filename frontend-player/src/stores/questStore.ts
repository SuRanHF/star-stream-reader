import { ref } from 'vue';
import { defineStore } from 'pinia';

export const useQuestStore = defineStore('quest', () => {
  const hasClaimableRealtimeQuest = ref(false);

  function markClaimable() {
    hasClaimableRealtimeQuest.value = true;
  }

  function clearClaimable() {
    hasClaimableRealtimeQuest.value = false;
  }

  return { hasClaimableRealtimeQuest, markClaimable, clearClaimable };
});
