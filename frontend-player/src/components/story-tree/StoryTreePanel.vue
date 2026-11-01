<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { exploreApi, type ChoiceStats, type StoryTreeEdge, type StoryTreeNode } from '@/api/exploreApi';
import { useGameStore } from '@/stores/gameStore';
import RouteCodexView from '@/components/story-tree/RouteCodexView.vue';
import { JUNCTION_OFFSET, LABEL_LINE_HEIGHT, layoutStoryTree, NODE_HEIGHT, NODE_WIDTH } from '@/utils/storyTreeLayout';

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
// 页签：流程图 / 图鉴（图鉴只在切过去时才请求）。
const activeTab = ref<'tree' | 'codex'>('tree');

// 分享这条路：生成 7 天有效的快照链接并复制；可以撤销。
const share = ref<{ url: string; expiresAt: string } | null>(null);
const shareBusy = ref(false);
const shareMessage = ref('');

async function createShare() {
  if (shareBusy.value || !playerId.value) return;
  shareBusy.value = true;
  shareMessage.value = '';
  try {
    const result = await exploreApi.createRouteShare(playerId.value);
    const url = `${window.location.origin}/share/${encodeURIComponent(result.code)}`;
    const expiresAt = new Date(result.expires_at).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' });
    share.value = { url, expiresAt };
    try {
      await navigator.clipboard.writeText(url);
      shareMessage.value = `链接已复制，${expiresAt}前有效。`;
    } catch {
      shareMessage.value = `${expiresAt}前有效，请手动复制下面的链接。`;
    }
  } catch (error) {
    shareMessage.value = error instanceof Error ? error.message : '生成分享失败';
  } finally {
    shareBusy.value = false;
  }
}

async function revokeShare() {
  if (shareBusy.value || !playerId.value) return;
  shareBusy.value = true;
  try {
    await exploreApi.revokeRouteShare(playerId.value);
    share.value = null;
    shareMessage.value = '分享已撤销，原链接不再能打开。';
  } catch (error) {
    shareMessage.value = error instanceof Error ? error.message : '撤销失败';
  } finally {
    shareBusy.value = false;
  }
}
const layout = computed(() => (tree.value ? layoutStoryTree(tree.value.nodes, tree.value.edges, tree.value.chapters) : null));

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

function fullLabel(edge: StoryTreeEdge) {
  return (edge.choice_label || '').replace(/^["“「]|["”」]$/g, '');
}

// 画布上截短，完整文字放在 <title>（悬停 / 读屏可见）。
function edgeLabel(edge: StoryTreeEdge) {
  const label = fullLabel(edge);
  return label.length > 14 ? `${label.slice(0, 13)}…` : label;
}

function edgePath(x1: number, y1: number, x2: number, y2: number) {
  const mid = (y1 + y2) / 2;
  return `M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}`;
}

// 打开或数据刷新后，把画布滚到「我现在在哪」：枝尖 > 等我选择 > 最深的已经历节点。
const canvasEl = ref<HTMLElement | null>(null);
const focusNode = computed(() => {
  const nodes = layout.value?.nodes || [];
  const pick = (state: string) => nodes.filter((p) => p.node.state === state);
  const candidates = pick('tip').length ? pick('tip') : pick('pending_choice').length ? pick('pending_choice') : pick('done');
  return candidates.sort((a, b) => b.row - a.row)[0] || null;
});
watch(focusNode, (target) => {
  if (!target) return;
  void nextTick(() => {
    const canvas = canvasEl.value;
    if (!canvas) return;
    canvas.scrollLeft = Math.max(0, target.x + NODE_WIDTH / 2 - canvas.clientWidth / 2);
    canvas.scrollTop = Math.max(0, target.y + NODE_HEIGHT / 2 - canvas.clientHeight / 2);
  });
}, { immediate: true });

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

    <div class="stp-tabs" role="tablist" aria-label="流程图与图鉴">
      <button type="button" role="tab" :aria-selected="activeTab === 'tree'" :class="['stp-tab', { active: activeTab === 'tree' }]" @click="activeTab = 'tree'">流程图</button>
      <button type="button" role="tab" :aria-selected="activeTab === 'codex'" :class="['stp-tab', { active: activeTab === 'codex' }]" @click="activeTab = 'codex'">图鉴</button>
      <span class="stp-tabs-spacer"></span>
      <button type="button" class="stp-share" :disabled="shareBusy" @click="createShare">分享这条路</button>
      <button v-if="share" type="button" class="stp-share stp-share--revoke" :disabled="shareBusy" @click="revokeShare">撤销分享</button>
    </div>
    <p v-if="shareMessage" class="stp-banner" role="status">
      {{ shareMessage }}
      <input v-if="share" class="stp-share-link" :value="share.url" readonly aria-label="分享链接" @focus="($event.target as HTMLInputElement).select()" />
    </p>

    <RouteCodexView v-if="activeTab === 'codex'" :player-id="playerId" />
    <template v-else>

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
    <div v-else-if="layout && layout.nodes.length" ref="canvasEl" class="stp-canvas">
      <svg
        :width="layout.width"
        :height="layout.height"
        :viewBox="`0 0 ${layout.width} ${layout.height}`"
        role="img"
        :aria-label="`剧情流程图，已经历 ${reachedCount} 个节点`"
      >
        <g class="stp-chapters" aria-hidden="true">
          <g v-for="band in layout.chapters" :key="band.stageKey" class="stp-chapter">
            <rect x="6" :y="band.y" width="4" :height="band.height" rx="2" />
            <text x="18" :y="band.y + 14">{{ band.label }}</text>
          </g>
        </g>
        <g class="stp-edges">
          <path
            v-for="(item, index) in layout.edges"
            :key="`e-${index}`"
            :d="edgePath(item.x1, item.y1, item.x2, item.y2)"
            :class="['stp-edge', { 'is-taken': item.edge.taken, 'is-faint': !item.edge.choice_label, 'is-and': item.kind === 'and' }]"
          />
          <!-- 标签贴在子节点正上方；同起终点的多个选项自下而上叠放 -->
          <g v-for="group in layout.labels" :key="group.key" class="stp-edge-label" :transform="`translate(${group.x}, ${group.y})`">
            <g
              v-for="(edge, index) in group.edges"
              :key="`${group.key}-${edge.choice_index}`"
              :transform="`translate(0, ${-(group.edges.length - 1 - index) * LABEL_LINE_HEIGHT})`"
            >
              <title>{{ fullLabel(edge) }}：{{ statsText(edge.stats) }}</title>
              <text text-anchor="middle" y="-5" :class="{ 'is-taken': edge.taken }">{{ edgeLabel(edge) }}</text>
              <text text-anchor="middle" y="10" class="stp-edge-stats">{{ statsText(edge.stats) }}</text>
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
    </template>
  </div>
</template>

<style scoped>
.stp-root {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
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

.stp-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--color-border);
}

.stp-tab {
  padding: 6px 12px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--color-muted);
  font-size: 13px;
  cursor: pointer;
}

.stp-tabs-spacer {
  flex: 1 1 auto;
}

.stp-share {
  align-self: center;
  padding: 3px 10px;
  border: 1px solid var(--color-star);
  border-radius: 999px;
  background: transparent;
  color: var(--color-star);
  font-size: 12px;
  cursor: pointer;
}

.stp-share--revoke {
  border-color: var(--color-border);
  color: var(--color-muted);
}

.stp-share:disabled {
  opacity: 0.5;
}

.stp-share-link {
  display: block;
  width: 100%;
  margin-top: 4px;
  padding: 3px 6px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-dim);
  font-size: 11px;
}

.stp-tab.active {
  border-bottom-color: var(--color-star);
  color: var(--color-star);
  font-weight: 600;
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
  min-width: 0;
  max-width: 100%;
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

.stp-chapter rect {
  fill: color-mix(in srgb, var(--color-star) 35%, transparent);
}

.stp-chapter text {
  fill: var(--color-star);
  font-size: 11px;
  font-weight: 600;
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
