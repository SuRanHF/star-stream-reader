<script setup lang="ts">
import { computed } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { exploreApi, type ChoiceStats } from '@/api/exploreApi';

// 路线图鉴：这棵树上所有「待续」枝尖，到过的亮起，没到过的只给「？」与读者占比（服务端不返回标题、枝、章节）。
const props = defineProps<{ playerId: number }>();

const codexQuery = useQuery({
  queryKey: computed(() => ['route-codex', props.playerId]),
  queryFn: () => exploreApi.getRouteCodex(props.playerId),
  enabled: computed(() => props.playerId > 0),
  retry: 1,
});

const codex = computed(() => codexQuery.data.value);
const reachedCount = computed(() => (codex.value?.tips || []).filter((tip) => tip.reached).length);

const BRANCH_LABELS: Record<string, string> = { companion: '同行者', nameless: '无名者', root: '主干' };

function statsText(stats?: ChoiceStats | null) {
  return stats ? `${stats.percent}% 的读者到过（${stats.sample_band}）` : '数据不足';
}
</script>

<template>
  <div class="rcv-root">
    <div v-if="codexQuery.isLoading.value" class="rcv-empty">图鉴读取中…</div>
    <div v-else-if="codexQuery.isError.value" class="rcv-empty">
      图鉴暂时读不到。<button type="button" class="rcv-retry" @click="codexQuery.refetch()">重试</button>
    </div>
    <template v-else-if="codex">
      <p class="rcv-summary">
        已收集 <b>{{ reachedCount }}</b> / {{ codex.total_tips }}
        <span>· 树每生长一次，图鉴就会多出新的格子；到过的格子永远保留。</span>
      </p>
      <ul class="rcv-grid" aria-label="路线图鉴">
        <li
          v-for="tip in codex.tips"
          :key="tip.tip_id"
          :class="['rcv-card', { 'is-reached': tip.reached, 'is-current': tip.tip_id === codex.my_tip_id }]"
        >
          <template v-if="tip.reached">
            <span class="rcv-meta">
              {{ BRANCH_LABELS[tip.branch || ''] || '' }}<template v-if="tip.chapter_label"> · {{ tip.chapter_label }}</template>
            </span>
            <strong class="rcv-title">{{ tip.title || '（无题）' }}</strong>
            <span v-if="tip.tip_id === codex.my_tip_id" class="rcv-badge">你在这里</span>
            <span v-else class="rcv-badge rcv-badge--past">你曾停在这里</span>
          </template>
          <template v-else>
            <span class="rcv-meta">未到达</span>
            <strong class="rcv-title rcv-title--unknown">？</strong>
          </template>
          <span class="rcv-stats">{{ statsText(tip.stats) }}</span>
        </li>
      </ul>
    </template>
  </div>
</template>

<style scoped>
.rcv-root {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow: auto;
}

.rcv-summary {
  margin: 0;
  color: var(--color-text-dim);
  font-size: 13px;
}

.rcv-summary b {
  color: var(--color-star);
  font-size: 16px;
}

.rcv-summary span {
  color: var(--color-muted);
  font-size: 12px;
}

.rcv-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.rcv-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 108px;
  padding: 12px;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-text) 3%, transparent);
}

.rcv-card.is-reached {
  border-style: solid;
  border-color: var(--color-star);
  background: color-mix(in srgb, var(--color-star) 8%, var(--lv-fidelity-panel, var(--color-panel)));
}

.rcv-card.is-current {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-star) 45%, transparent);
}

.rcv-meta {
  color: var(--color-muted);
  font-size: 11px;
}

.rcv-title {
  color: var(--color-text);
  font-size: 14px;
}

.rcv-title--unknown {
  color: var(--color-muted);
  font-size: 22px;
}

.rcv-badge {
  align-self: flex-start;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--color-star);
  color: var(--lv-fidelity-panel, var(--color-panel));
  font-size: 11px;
}

.rcv-badge--past {
  background: transparent;
  color: var(--color-star);
  border: 1px solid var(--color-star);
}

.rcv-stats {
  margin-top: auto;
  color: var(--color-muted);
  font-size: 11px;
}

.rcv-empty {
  padding: 24px 12px;
  color: var(--color-muted);
  font-size: 13px;
  text-align: center;
}

.rcv-retry {
  margin-left: 6px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: transparent;
  color: var(--color-star);
  cursor: pointer;
}
</style>
