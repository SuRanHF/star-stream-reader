import { ref } from 'vue';
import { defineStore } from 'pinia';
import type { ApiRecord } from '@/types/api';

export const useWorldBossStore = defineStore('worldBoss', () => {
  const summary = ref<ApiRecord>({});

  function patchSummary(data: ApiRecord) {
    summary.value = { ...summary.value, ...data };
  }

  return { summary, patchSummary };
});
