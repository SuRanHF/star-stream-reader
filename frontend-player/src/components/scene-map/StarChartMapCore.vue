<script setup lang="ts">
import { ref, computed } from 'vue';
import type { MapNode } from '@/api/exploreApi';
import { dangerTier } from '@/utils/sceneUtils';

const props = defineProps<{
  nodes: MapNode[];
}>();

const VOLUME_COLORS: Record<number, string> = {
  1: '#a0b0f0',
  2: '#e07070',
  3: '#7ab0f7',
  4: '#5ec49e',
};

const TYPE_ICONS: Record<string, string> = {
  main: '◆',
  side: '◇',
  hidden: '◎',
  boss: '⬡',
};

const emit = defineEmits<{
  'node-select': [nodeName: string];
}>();

const svgEl = ref<SVGSVGElement>();
const viewBox = ref({ x: -100, y: -100, w: 1100, h: 900 });
const selectedId = ref<string | null>(null);
const filterVolume = ref(0);
const filterType = ref('all');

// pan state
const dragging = ref(false);
const dragStart = ref({ x: 0, y: 0 });
const offset = ref({ x: 0, y: 0 });

const filteredNodes = computed(() => {
  let list = props.nodes;
  if (filterVolume.value > 0) list = list.filter(n => n.volume === filterVolume.value);
  if (filterType.value !== 'all') list = list.filter(n => n.type === filterType.value);
  return list;
});

const edges = computed(() => {
  const nodeMap = new Map(props.nodes.map(n => [n.id, n]));
  const result: { from: MapNode; to: MapNode }[] = [];
  const seen = new Set<string>();
  for (const node of props.nodes) {
    for (const targetId of node.connectedTo) {
      const target = nodeMap.get(targetId);
      if (!target) continue;
      const key = [node.id, targetId].sort().join('::');
      if (seen.has(key)) continue;
      seen.add(key);
      result.push({ from: node, to: target });
    }
  }
  return result;
});

const visibleEdges = computed(() => {
  const nodeIds = new Set(filteredNodes.value.map(n => n.id));
  return edges.value.filter(e => nodeIds.has(e.from.id) && nodeIds.has(e.to.id));
});

function selectNode(id: string) {
  selectedId.value = selectedId.value === id ? null : id;
  const node = props.nodes.find(n => n.id === selectedId.value);
  if (node) emit('node-select', node.name);
  else emit('node-select', '');
}

function onWheel(e: WheelEvent) {
  e.preventDefault();
  const scale = e.deltaY > 0 ? 1.15 : 1 / 1.15;
  const vb = viewBox.value;
  const mx = vb.x + vb.w / 2;
  const my = vb.y + vb.h / 2;
  const nw = Math.max(300, Math.min(3000, vb.w * scale));
  const nh = Math.max(300, Math.min(3000, vb.h * scale));
  viewBox.value = { x: mx - nw / 2, y: my - nh / 2, w: nw, h: nh };
}

function onPanStart(e: MouseEvent | TouchEvent) {
  dragging.value = true;
  const pos = 'touches' in e ? { x: e.touches[0].clientX, y: e.touches[0].clientY } : { x: e.clientX, y: e.clientY };
  dragStart.value = pos;
  offset.value = { x: 0, y: 0 };
}

function onPanMove(e: MouseEvent | TouchEvent) {
  if (!dragging.value) return;
  const pos = 'touches' in e ? { x: e.touches[0].clientX, y: e.touches[0].clientY } : { x: e.clientX, y: e.clientY };
  offset.value = { x: pos.x - dragStart.value.x, y: pos.y - dragStart.value.y };
}

function onPanEnd() {
  if (!dragging.value) return;
  dragging.value = false;
  const vb = viewBox.value;
  const scaleX = vb.w / (svgEl.value?.clientWidth || 1000);
  const scaleY = vb.h / (svgEl.value?.clientHeight || 800);
  viewBox.value = { x: vb.x - offset.value.x * scaleX, y: vb.y - offset.value.y * scaleY, w: vb.w, h: vb.h };
}

function resetView() {
  viewBox.value = { x: -100, y: -100, w: 1100, h: 900 };
  selectedId.value = null;
  emit('node-select', '');
}

defineExpose({ resetView, setFilterVolume: (v: number) => { filterVolume.value = v; }, setFilterType: (t: string) => { filterType.value = t; } });
</script>

<template>
  <div class="score-wrap" @wheel="onWheel">
    <svg
      ref="svgEl"
      :viewBox="`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`"
      class="score-svg"
      @mousedown="onPanStart"
      @mousemove="onPanMove"
      @mouseup="onPanEnd"
      @mouseleave="onPanEnd"
      @touchstart="onPanStart"
      @touchmove="onPanMove"
      @touchend="onPanEnd"
    >
      <defs>
        <radialGradient id="glow-boss2" cx="50%" cy="50%">
          <stop offset="0%" stop-color="#d97b6c" stop-opacity="0.35" />
          <stop offset="100%" stop-color="#d97b6c" stop-opacity="0" />
        </radialGradient>
        <radialGradient id="glow-hidden2" cx="50%" cy="50%">
          <stop offset="0%" stop-color="#98c9bb" stop-opacity="0.4" />
          <stop offset="100%" stop-color="#98c9bb" stop-opacity="0" />
        </radialGradient>
        <filter id="star-glow2">
          <feGaussianBlur stdDeviation="3" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>

      <pattern id="grid2" width="60" height="60" patternUnits="userSpaceOnUse">
        <circle cx="30" cy="30" r="0.8" fill="#3a4a7a" opacity="0.35" />
      </pattern>
      <rect :x="viewBox.x - 200" :y="viewBox.y - 200" :width="viewBox.w + 400" :height="viewBox.h + 400" fill="url(#grid2)" />

      <text x="400" y="530" fill="#a0b0f0" opacity="0.1" font-size="48" font-weight="900">第一卷</text>
      <text x="100" y="160" fill="#e07070" opacity="0.1" font-size="48" font-weight="900">第二卷</text>
      <text x="520" y="620" fill="#7ab0f7" opacity="0.1" font-size="48" font-weight="900">第三卷</text>
      <text x="300" y="50" fill="#5ec49e" opacity="0.1" font-size="48" font-weight="900">第四卷</text>

      <line
        v-for="(edge, i) in visibleEdges"
        :key="'e' + i"
        :x1="edge.from.x" :y1="edge.from.y"
        :x2="edge.to.x" :y2="edge.to.y"
        :stroke="VOLUME_COLORS[edge.from.volume] || '#4a5a7a'"
        :stroke-opacity="edge.from.unlocked && edge.to.unlocked ? 0.5 : 0.15"
        :stroke-dasharray="edge.from.unlocked && edge.to.unlocked ? 'none' : '6 4'"
        stroke-width="1.5"
      />

      <g
        v-for="node in filteredNodes"
        :key="node.id"
        :transform="`translate(${node.x}, ${node.y})`"
        :class="{ 'snode-selected': node.id === selectedId, 'snode-locked': !node.unlocked }"
        class="snode"
        @click.stop="selectNode(node.id)"
      >
        <circle v-if="node.type === 'boss' || node.type === 'hidden'" r="28"
          :fill="node.type === 'boss' ? 'url(#glow-boss2)' : 'url(#glow-hidden2)'" class="snode-glow" />
        <!-- danger tier ring -->
        <circle r="19"
          :fill="'none'"
          :stroke="dangerTier(node.dangerLevel).color"
          :stroke-width="2"
          :stroke-opacity="node.unlocked ? 0.7 : 0.2" />
        <circle r="16"
          :fill="node.unlocked ? '#0d1428' : '#070b1a'"
          :stroke="VOLUME_COLORS[node.volume] || '#4a5a7a'"
          :stroke-width="node.id === selectedId ? 3 : 1.5"
          :stroke-opacity="node.unlocked ? 0.9 : 0.3"
          :filter="node.unlocked ? 'url(#star-glow2)' : ''" />
        <text text-anchor="middle" dy="5"
          :fill="node.unlocked ? (VOLUME_COLORS[node.volume] || '#6a7a9a') : '#4a5588'"
          font-size="14">{{ TYPE_ICONS[node.type] }}</text>
        <text text-anchor="middle" dy="-26"
          :fill="dangerTier(node.dangerLevel).color"
          :opacity="node.unlocked ? 0.9 : 0.3"
          font-size="9" font-weight="700">{{ dangerTier(node.dangerLevel).label }}</text>
        <text text-anchor="middle" dy="32"
          :fill="node.unlocked ? '#d0d8f0' : '#5a6688'"
          font-size="11" opacity="0.9">{{ node.name }}</text>
        <text v-if="node.completed" text-anchor="middle" dy="-22" fill="#5ec49e" font-size="11">✓</text>
        <text v-if="!node.unlocked" text-anchor="middle" dy="-22" fill="#5a6688" font-size="11">🔒</text>
      </g>
    </svg>
    <div class="score-hint">🖱 滚轮缩放 · 拖拽平移 · 点击节点选择场景</div>
  </div>
</template>

<style scoped>
.score-wrap {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
  cursor: grab;
  touch-action: none;
  background: radial-gradient(ellipse at 40% 50%, rgba(13, 20, 48, 0.6) 0%, #070b1a 100%);
}
.score-wrap:active { cursor: grabbing; }
.score-svg { width: 100%; height: 100%; display: block; }
.snode { cursor: pointer; transition: opacity 0.2s; }
.snode-locked { opacity: 0.4; }
.snode-locked:hover { opacity: 0.6; }
.snode-selected circle:nth-child(2) { stroke-width: 3; filter: drop-shadow(0 0 6px rgba(74, 143, 231, 0.4)); }
.score-hint {
  position: absolute; bottom: 8px; right: 12px;
  font-size: 10px; color: #4a5588; pointer-events: none;
}
</style>
