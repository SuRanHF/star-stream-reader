<script setup lang="ts">
import GameCard from '@/components/common/GameCard.vue';
import type { QuestSummary } from '@/types/bootstrap';

defineProps<{
  summary: QuestSummary;
}>();
</script>

<template>
  <GameCard title="任务摘要">
    <div class="grid grid-cols-3 gap-2 text-center text-sm">
      <div class="rounded border border-line p-2">
        <div class="text-xs text-muted">进行中</div>
        <div class="text-spirit">{{ summary.activeCount || 0 }}</div>
      </div>
      <div class="rounded border border-line p-2">
        <div class="text-xs text-muted">已完成</div>
        <div class="text-star">{{ summary.completedCount || 0 }}</div>
      </div>
      <div class="rounded border border-line p-2">
        <div class="text-xs text-muted">可领取</div>
        <div class="text-danger">{{ summary.claimableCount || 0 }}</div>
      </div>
    </div>
    <p v-if="summary.hasRealtimeUpdate" class="mt-3 text-xs text-star">{{ summary.lastCompletedQuest }}</p>
  </GameCard>
</template>
