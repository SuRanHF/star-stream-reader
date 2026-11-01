<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import { exploreApi, type ChoiceStats } from '@/api/exploreApi';

// 公开的分享路线卡：只读快照，不显示任何玩家身份；不需要登录，也不让搜索引擎收录。
const route = useRoute();
const code = computed(() => String(route.params.code || ''));

const shareQuery = useQuery({
  queryKey: computed(() => ['route-share', code.value]),
  queryFn: () => exploreApi.getPublicRouteShare(code.value),
  enabled: computed(() => Boolean(code.value)),
  retry: false,
});

const snapshot = computed(() => shareQuery.data.value);
const steps = computed(() => (snapshot.value?.nodes || []).map((node) => ({
  ...node,
  choice: snapshot.value?.choices.find((choice) => choice.node_id === node.node_id) || null,
  isTip: node.node_id === snapshot.value?.current_tip,
})));

function statsText(stats?: ChoiceStats | null) {
  return stats ? `${stats.percent}% 的读者也这样选（${stats.sample_band}）` : '读者太少，暂无占比';
}

let robotsMeta: HTMLMetaElement | null = null;
onMounted(() => {
  robotsMeta = document.createElement('meta');
  robotsMeta.name = 'robots';
  robotsMeta.content = 'noindex, nofollow';
  document.head.appendChild(robotsMeta);
});
onBeforeUnmount(() => robotsMeta?.remove());
</script>

<template>
  <main class="rsv-root">
    <section class="rsv-card">
      <p class="rsv-kicker">星流读者 · 分享的路线</p>
      <h1 class="rsv-title">一位读者走过的路</h1>

      <p v-if="shareQuery.isLoading.value" class="rsv-empty">读取中…</p>
      <p v-else-if="shareQuery.isError.value || !snapshot" class="rsv-empty">
        这条分享不存在、已经过期或被撤销了。
      </p>
      <ol v-else class="rsv-steps">
        <li v-for="step in steps" :key="step.node_id" :class="['rsv-step', { 'is-tip': step.isTip }]">
          <span v-if="step.chapter_label" class="rsv-chapter">{{ step.chapter_label }}</span>
          <strong class="rsv-node">{{ step.title }}</strong>
          <div v-if="step.choice" class="rsv-choice">
            <span>选择：「{{ step.choice.choice_text.replace(/^["“「]|["”」]$/g, '') }}」</span>
            <small>{{ statsText(step.choice.stats) }}</small>
          </div>
          <span v-if="step.isTip" class="rsv-tip">待续 · 这条路会在之后的更新里继续生长</span>
        </li>
      </ol>

      <p class="rsv-note">这是分享时的快照，不会随作者之后的选择改变。</p>
      <RouterLink to="/login" class="rsv-cta">我也来读</RouterLink>
    </section>
  </main>
</template>

<style scoped>
.rsv-root {
  display: flex;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 16px;
  background: var(--lv-fidelity-void, var(--color-void));
}

.rsv-card {
  width: 100%;
  max-width: 560px;
}

.rsv-kicker {
  margin: 0;
  color: var(--color-muted);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.rsv-title {
  margin: 4px 0 20px;
  color: var(--color-star);
  font-size: 22px;
}

.rsv-steps {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0;
  padding: 0 0 0 18px;
  border-left: 2px solid color-mix(in srgb, var(--color-star) 45%, transparent);
  list-style: none;
}

.rsv-step {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rsv-chapter {
  color: var(--color-muted);
  font-size: 11px;
}

.rsv-node {
  color: var(--color-text);
  font-size: 15px;
}

.rsv-choice {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--color-text-dim);
  font-size: 13px;
}

.rsv-choice small {
  color: var(--color-muted);
  font-size: 11px;
}

.rsv-tip {
  color: var(--color-star);
  font-size: 12px;
}

.rsv-empty,
.rsv-note {
  color: var(--color-muted);
  font-size: 13px;
}

.rsv-note {
  margin-top: 20px;
}

.rsv-cta {
  display: inline-block;
  margin-top: 8px;
  padding: 8px 18px;
  border: 1px solid var(--color-star);
  border-radius: 6px;
  color: var(--color-star);
  text-decoration: none;
}
</style>
