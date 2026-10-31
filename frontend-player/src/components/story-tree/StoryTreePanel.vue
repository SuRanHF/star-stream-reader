<script setup lang="ts">
import { computed } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { exploreApi, type ChoiceStats, type StoryTreeEdge, type StoryTreeNode } from '@/api/exploreApi';
import { useGameStore } from '@/stores/gameStore';
import { JUNCTION_OFFSET, layoutStoryTree, NODE_HEIGHT, NODE_WIDTH } from '@/utils/storyTreeLayout';

// 剧情流程图：数据与节点状态全部来自服务端（不剧透：未到达的节点没有标题），这里只负责画。
const emit = defineEmits<{
  'close-panel': [];
}>();

const gameStore = useGameStore();
const playerId = computed(() => Number(gameStore.player?.id || gameStore.player?.playerId || 0));

const treeQuery = useQuery({
  queryKey: computed(() => ['story-tree', playerId.value]),
  queryFn: () => exploreApi.getStoryTree(playerId.value),
  enabled: computed(() => playerId.value > 0),
  retry: 1,
});

const tree = computed(() => treeQuery.data.value);
const layout = computed(() => (tree.value ? layoutStoryTree(tree.value.nodes, tree.value.edges) : null));

const reachedStates = new Set(['done', 'pending_choice', 'tip']);
const reachedCount = computed(() => (tree.value?.nodes || []).filter((node) => reachedStates.has(node.state)).length);
const isPending = computed(() => tree.value?.route_continuation?.status === 'to_be_continued');

function nodeLines(node: StoryTreeNode): [string, string] {
  switch (node.state) {
    case 'done':
    case 'pending_choice':
    case 'tip':
      return [node.title || '（无题）', node.location_name || ''];
    case 'available':
      return ['？', node.location_name ? `去${node.location_name}看看` : '现在就能遇到'];
    case 'closed':
      return ['未走的路', ''];
    default:
      return ['？', ''];
  }
}

function nodeAria(node: StoryTreeNode) {
  const [title, sub] = nodeLines(node);
  const stateText: Record<string, string> = {
    done: '已经历', pending_choice: '等待你的选择', tip: '待续', available: '现在可以遇到', locked: '尚未解锁', closed: '没有走的路',
  };
  return [stateText[node.state] || '', title !== '？' ? title : '', sub].filter(Boolean).join('，');
}

function statsText(stats?: ChoiceStats | null) {
  return stats ? `${stats.percent}% 的读者（${stats.sample_band}）` : '数据不足';
}

function edgeLabel(edge: StoryTreeEdge) {
  if (!edge.choice_label) return '';
  const label = edge.choice_label.replace(/^["“「]|["”」]$/g, '');
  return label.length > 14 ? `${label.slice(0, 13)}…` : label;
}

function edgePath(x1: number, y1: number, x2: number, y2: number) {
  const mid = (y1 + y2) / 2;
  return `M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}`;
}

const routeStatsText = computed(() => {
  const stats = tree.value?.route_stats;
  return stats ? `走过和你完全一样这条路的读者：${stats.percent}%（${stats.sample_band}）` : '';
});
</script>

<template>
  <div class="stp-root gc-panel" data-tone="gold">
    <header class="stp-head gc-panel-head">
      <div class="gc-rule" aria-hidden="true"></div>
      <div class="gc-title">剧情流程图</div>
      <div class="gc-panel-sub">已经历 {{ reachedCount }} 个节点</div>
      <button class="stp-close gc-panel-close" aria-label="关闭流程图" @click="emit('close-panel')">×</button>
    </header>

    <p v-if="isPending" class="stp-banner stp-banner--pending" role="status">◇ 你的主线停在「待续」。这棵树会在之后的更新里继续生长。</p>
    <p v-if="routeStatsText" class="stp-banner">{{ routeStatsText }}</p>

    <ul class="stp-legend" aria-label="图例">
      <li><i class="stp-swatch is-done"></i>已经历</li>
      <li><i class="stp-swatch is-tip"></i>待续</li>
      <li><i class="stp-swatch is-available"></i>现在能遇到</li>
      <li><i class="stp-swatch is-locked"></i>未解锁</li>
      <li><i class="stp-swatch is-closed"></i>未走的路</li>
      <li><i class="stp-line is-and"></i>还需要同时满足</li>
    </ul>

    <div v-if="treeQuery.isLoading.value" class="stp-empty">流程图读取中…</div>
    <div v-else-if="treeQuery.isError.value" class="stp-empty">
      流程图暂时读不到。<button type="button" class="stp-retry" @click="treeQuery.refetch()">重试</button>
    </div>
    <div v-else-if="layout && layout.nodes.length" class="stp-canvas">
      <svg
        :width="layout.width"
        :height="layout.height"
        :viewBox="`0 0 ${layout.width} ${layout.height}`"
        role="img"
        :aria-label="`剧情流程图，已经历 ${reachedCount} 个节点`"
      >
        <g class="stp-edges">
          <g v-for="(item, index) in layout.edges" :key="`e-${index}`">
            <path
              :d="edgePath(item.x1, item.y1, item.x2, item.y2)"
              :class="['stp-edge', { 'is-taken': item.edge.taken, 'is-faint': !item.edge.choice_label, 'is-and': item.kind === 'and' }]"
            />
            <g v-if="item.edge.choice_label" class="stp-edge-label" :transform="`translate(${(item.x1 + item.x2) / 2}, ${(item.y1 + item.y2) / 2})`">
              <text text-anchor="middle" y="-5" :class="{ 'is-taken': item.edge.taken }">{{ edgeLabel(item.edge) }}</text>
              <text text-anchor="middle" y="10" class="stp-edge-stats">{{ statsText(item.edge.stats) }}</text>
            </g>
          </g>
          <g v-for="(point, index) in layout.junctions" :key="`j-${index}`" class="stp-junction">
            <line :x1="point.x" :y1="point.y" :x2="point.x" :y2="point.y + JUNCTION_OFFSET" class="stp-edge is-and" />
            <circle :cx="point.x" :cy="point.y" r="7" />
            <text :x="point.x" :y="point.y + 3.5" text-anchor="middle">且</text>
          </g>
        </g>
        <g
          v-for="item in layout.nodes"
          :key="item.node.node_id"
          :class="['stp-node', `is-${item.node.state}`, `branch-${item.node.branch}`]"
          :transform="`translate(${item.x}, ${item.y})`"
        >
          <title>{{ nodeAria(item.node) }}</title>
          <rect :width="NODE_WIDTH" :height="NODE_HEIGHT" rx="8" />
          <text :x="NODE_WIDTH / 2" y="25" text-anchor="middle" class="stp-node-title">{{ nodeLines(item.node)[0] }}</text>
          <text :x="NODE_WIDTH / 2" y="44" text-anchor="middle" class="stp-node-sub">{{ nodeLines(item.node)[1] }}</text>
          <text v-if="item.node.state === 'tip'" :x="NODE_WIDTH - 8" y="14" text-anchor="end" class="stp-node-badge">待续</text>
        </g>
      </svg>
    </div>
    <div v-else class="stp-empty">还没有可以显示的剧情。</div>
  </div>
</template>

<style scoped>
.stp-root {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  min-height: 0;
  padding: 12px;
}

.stp-head {
  position: relative;
}

.stp-close {
  position: absolute;
  top: 0;
  right: 0;
}

.stp-banner {
  margin: 0;
  padding: 6px 10px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--color-star) 6%, transparent);
  color: var(--color-text-dim);
  font-size: 12px;
}

.stp-banner--pending {
  color: var(--color-star);
}

.stp-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 0;
  padding: 0;
  color: var(--color-muted);
  font-size: 11px;
  list-style: none;
}

.stp-legend li {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.stp-swatch {
  display: inline-block;
  width: 12px;
  height: 10px;
  border: 1px solid var(--color-border);
  border-radius: 3px;
}

.stp-swatch.is-done { background: color-mix(in srgb, var(--color-star) 18%, transparent); border-color: var(--color-star); }
.stp-swatch.is-tip { background: color-mix(in srgb, var(--color-star) 35%, transparent); border-color: var(--color-star); }
.stp-swatch.is-available { border-style: dashed; border-color: var(--color-spirit); }
.stp-swatch.is-closed { background: color-mix(in srgb, var(--color-text) 6%, transparent); }

.stp-canvas {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--lv-fidelity-void, var(--color-void));
}

.stp-empty {
  padding: 24px 12px;
  color: var(--color-muted);
  font-size: 13px;
  text-align: center;
}

.stp-retry {
  margin-left: 6px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: transparent;
  color: var(--color-star);
  cursor: pointer;
}

.stp-edge {
  fill: none;
  stroke: color-mix(in srgb, var(--color-text) 30%, transparent);
  stroke-width: 1.4;
}

.stp-edge.is-taken {
  stroke: var(--color-star);
  stroke-width: 2.2;
}

.stp-edge.is-faint {
  stroke-dasharray: 4 4;
  stroke: color-mix(in srgb, var(--color-text) 18%, transparent);
}

.stp-edge.is-and {
  stroke: color-mix(in srgb, var(--color-spirit) 55%, transparent);
  stroke-dasharray: 1 4;
  stroke-linecap: round;
  stroke-width: 1.6;
}

.stp-line {
  display: inline-block;
  width: 16px;
  border-top: 2px dotted color-mix(in srgb, var(--color-spirit) 70%, transparent);
}

.stp-junction circle {
  fill: var(--lv-fidelity-void, var(--color-void));
  stroke: var(--color-spirit);
  stroke-width: 1;
}

.stp-junction text {
  fill: var(--color-spirit);
  font-size: 9px;
}

.stp-edge-label text {
  fill: var(--color-text-dim);
  font-size: 11px;
  paint-order: stroke;
  stroke: var(--lv-fidelity-void, var(--color-void));
  stroke-width: 4px;
}

.stp-edge-label text.is-taken {
  fill: var(--color-star);
  font-weight: 600;
}

.stp-edge-label .stp-edge-stats {
  fill: var(--color-muted);
  font-size: 10px;
}

.stp-node rect {
  fill: var(--lv-fidelity-panel, var(--color-panel));
  stroke: var(--color-border);
  stroke-width: 1;
}

.stp-node-title {
  fill: var(--color-text);
  font-size: 13px;
  font-weight: 600;
}

.stp-node-sub {
  fill: var(--color-muted);
  font-size: 11px;
}

.stp-node.is-done rect,
.stp-node.is-pending_choice rect {
  fill: color-mix(in srgb, var(--color-star) 10%, var(--lv-fidelity-panel, var(--color-panel)));
  stroke: var(--color-star);
}

.stp-node.is-tip rect {
  fill: color-mix(in srgb, var(--color-star) 22%, var(--lv-fidelity-panel, var(--color-panel)));
  stroke: var(--color-star);
  stroke-width: 2;
}

.stp-node-badge {
  fill: var(--color-star);
  font-size: 10px;
  font-weight: 700;
}

.stp-node.is-available rect {
  stroke: var(--color-spirit);
  stroke-dasharray: 5 3;
}

.stp-node.is-available .stp-node-sub {
  fill: var(--color-spirit);
}

.stp-node.is-locked .stp-node-title,
.stp-node.is-closed .stp-node-title {
  fill: var(--color-muted);
  font-weight: 400;
}

.stp-node.is-closed rect {
  fill: color-mix(in srgb, var(--color-text) 5%, transparent);
  stroke-dasharray: 2 3;
}
</style>
