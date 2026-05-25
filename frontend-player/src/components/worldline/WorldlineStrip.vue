<script setup lang="ts">
import type { ApiRecord } from '@/types/api';

defineProps<{
  summary: ApiRecord;
}>();
</script>

<template>
  <div class="rounded border border-line bg-[#0c1115] px-3 py-2 text-xs text-spirit">
    世界线：{{ summary.stateName || summary.currentState || summary.status || '观测中' }}
  </div>
</template>
