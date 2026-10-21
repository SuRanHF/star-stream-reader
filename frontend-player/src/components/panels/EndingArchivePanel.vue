<script setup lang="ts">
import { computed } from 'vue';
import { buildEndingArchiveEntries, hasEndingArchiveLoadError } from '@/utils/endingArchive';

const props = defineProps<{
  endings: unknown;
}>();

const entries = computed(() => buildEndingArchiveEntries(props.endings));
const unavailable = computed(() => hasEndingArchiveLoadError(props.endings));
</script>

<template>
  <section class="ling-ending-archive" aria-label="个人终局档案">
    <header class="ling-ending-archive-head">
      <h3>个人终局档案</h3>
      <span>{{ entries.length }} 项已达成</span>
    </header>
    <div v-if="entries.length" class="ling-ending-archive-list">
      <article v-for="ending in entries" :key="ending.key">
        <div class="ling-ending-archive-title">
          <strong>{{ ending.name }}</strong>
          <span>已达成</span>
        </div>
        <p v-if="ending.description">{{ ending.description }}</p>
        <small v-if="ending.isHidden">隐藏终局</small>
      </article>
    </div>
    <p v-else class="ling-history-ledger-empty">
      {{ unavailable ? '终局档案暂时无法读取，请稍后重试。' : '个人终局仍在前方。完成终局剧情选择后，已抵达的结局会收录于此。' }}
    </p>
  </section>
</template>
