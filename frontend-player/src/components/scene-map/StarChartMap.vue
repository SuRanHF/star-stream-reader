<script setup lang="ts">
import { ref, computed } from 'vue';
import { starChartNodes, type SceneNode } from './mockData';

const VOLUME_COLORS: Record<number, string> = {
  1: '#caa86a',
  2: '#d97b6c',
  3: '#8db8d8',
  4: '#98c9bb',
};

const TYPE_ICONS: Record<string, string> = {
  main: '◆',
  side: '◇',
  hidden: '◎',
  boss: '⬡',
};

const container = ref<HTMLDivElement>();
const svgEl = ref<SVGSVGElement>();

const viewBox = ref({ x: -100, y: -100, w: 1100, h: 900 });
const offset = ref({ x: 0, y: 0 });
const dragging = ref(false);
const dragStart = ref({ x: 0, y: 0 });
const selectedId = ref<string | null>(null);
const filterVolume = ref(0);
const filterType = ref('all');

const volumes = [
  { id: 1, label: '第一卷·灭世之后' },
  { id: 2, label: '第二卷·恶魔世界' },
  { id: 3, label: '第三卷·星流战争' },
  { id: 4, label: '第四卷·终章之战' },
];

const filteredNodes = computed(() => {
  let nodes = starChartNodes;
  if (filterVolume.value > 0) {
    nodes = nodes.filter(n => n.volume === filterVolume.value);
  }
  if (filterType.value !== 'all') {
    nodes = nodes.filter(n => n.type === filterType.value);
  }
  return nodes;
});

const selectedNode = computed(() =>
  starChartNodes.find(n => n.id === selectedId.value) || null,
);

const edges = computed(() => {
  const nodeMap = new Map(starChartNodes.map(n => [n.id, n]));
  const result: { from: SceneNode; to: SceneNode }[] = [];
  const seen = new Set<string>();
  for (const node of starChartNodes) {
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

function nodeClass(node: SceneNode) {
  return {
    'star-node': true,
    'is-selected': node.id === selectedId.value,
    'is-unlocked': node.unlocked,
    'is-locked': !node.unlocked,
    'is-completed': node.completed,
  };
}

function selectNode(id: string) {
  selectedId.value = selectedId.value === id ? null : id;
}

// Pan & zoom via wheel
function onWheel(e: WheelEvent) {
  e.preventDefault();
  const scale = e.deltaY > 0 ? 1.15 : 1 / 1.15;
  const vb = viewBox.value;
  const mx = vb.x + vb.w / 2;
  const my = vb.y + vb.h / 2;
  const nw = vb.w * scale;
  const nh = vb.h * scale;
  viewBox.value = {
    x: mx - nw / 2,
    y: my - nh / 2,
    w: Math.max(300, Math.min(3000, nw)),
    h: Math.max(200, Math.min(3000, nh)),
  };
}

function onPanStart(e: MouseEvent) {
  dragging.value = true;
  dragStart.value = { x: e.clientX, y: e.clientY };
  offset.value = { x: 0, y: 0 };
}

function onPanMove(e: MouseEvent) {
  if (!dragging.value) return;
  offset.value = {
    x: e.clientX - dragStart.value.x,
    y: e.clientY - dragStart.value.y,
  };
}

function onPanEnd() {
  if (!dragging.value) return;
  dragging.value = false;
  const vb = viewBox.value;
  const scaleX = vb.w / (svgEl.value?.clientWidth || 1000);
  const scaleY = vb.h / (svgEl.value?.clientHeight || 800);
  viewBox.value = {
    x: vb.x - offset.value.x * scaleX,
    y: vb.y - offset.value.y * scaleY,
    w: vb.w,
    h: vb.h,
  };
}

function resetView() {
  viewBox.value = { x: -100, y: -100, w: 1100, h: 900 };
  selectedId.value = null;
}

</script>

<template>
  <div class="star-chart-root">
    <!-- filters -->
    <div class="sc-filters">
      <button
        v-for="v in volumes" :key="v.id"
        :class="{ active: filterVolume === v.id }"
        @click="filterVolume = filterVolume === v.id ? 0 : v.id"
      >
        {{ v.label }}
      </button>
      <span class="sep">|</span>
      <button :class="{ active: filterType === 'all' }" @click="filterType = 'all'">全部</button>
      <button :class="{ active: filterType === 'main' }" @click="filterType = 'main'">主线</button>
      <button :class="{ active: filterType === 'side' }" @click="filterType = 'side'">支线</button>
      <button :class="{ active: filterType === 'hidden' }" @click="filterType = 'hidden'">隐藏</button>
      <button :class="{ active: filterType === 'boss' }" @click="filterType = 'boss'">Boss</button>
      <button class="sc-reset" @click="resetView">重置视图</button>
    </div>

    <div
      ref="container"
      class="sc-canvas-wrap"
      @wheel="onWheel"
    >
      <svg
        ref="svgEl"
        :viewBox="`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`"
        class="sc-svg"
        @mousedown="onPanStart"
        @mousemove="onPanMove"
        @mouseup="onPanEnd"
        @mouseleave="onPanEnd"
      >
        <!-- background grid -->
        <defs>
          <radialGradient id="glow-main" cx="50%" cy="50%">
            <stop offset="0%" stop-color="#caa86a" stop-opacity="0.3" />
            <stop offset="100%" stop-color="#caa86a" stop-opacity="0" />
          </radialGradient>
          <radialGradient id="glow-boss" cx="50%" cy="50%">
            <stop offset="0%" stop-color="#d97b6c" stop-opacity="0.35" />
            <stop offset="100%" stop-color="#d97b6c" stop-opacity="0" />
          </radialGradient>
          <radialGradient id="glow-hidden" cx="50%" cy="50%">
            <stop offset="0%" stop-color="#98c9bb" stop-opacity="0.4" />
            <stop offset="100%" stop-color="#98c9bb" stop-opacity="0" />
          </radialGradient>
          <filter id="star-glow">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
        </defs>

        <!-- grid pattern -->
        <pattern id="grid" width="60" height="60" patternUnits="userSpaceOnUse">
          <circle cx="30" cy="30" r="0.8" fill="#28343d" opacity="0.5" />
        </pattern>
        <rect :x="viewBox.x - 200" :y="viewBox.y - 200" :width="viewBox.w + 400" :height="viewBox.h + 400" fill="url(#grid)" />

        <!-- volume zone labels -->
        <text x="400" y="530" fill="#caa86a" opacity="0.15" font-size="48" font-weight="900">第一卷</text>
        <text x="100" y="160" fill="#d97b6c" opacity="0.15" font-size="48" font-weight="900">第二卷</text>
        <text x="520" y="620" fill="#8db8d8" opacity="0.15" font-size="48" font-weight="900">第三卷</text>
        <text x="300" y="50" fill="#98c9bb" opacity="0.15" font-size="48" font-weight="900">第四卷</text>

        <!-- edges -->
        <line
          v-for="(edge, i) in visibleEdges"
          :key="'e' + i"
          :x1="edge.from.x" :y1="edge.from.y"
          :x2="edge.to.x" :y2="edge.to.y"
          :stroke="VOLUME_COLORS[edge.from.volume] || '#4a5a5d'"
          :stroke-opacity="edge.from.unlocked && edge.to.unlocked ? 0.5 : 0.15"
          :stroke-dasharray="edge.from.unlocked && edge.to.unlocked ? 'none' : '6 4'"
          stroke-width="1.5"
        />

        <!-- nodes -->
        <g
          v-for="node in filteredNodes"
          :key="node.id"
          :class="nodeClass(node)"
          :transform="`translate(${node.x}, ${node.y})`"
          @click.stop="selectNode(node.id)"
        >
          <!-- glow circle -->
          <circle
            v-if="node.type === 'boss' || node.type === 'hidden'"
            r="28"
            :fill="node.type === 'boss' ? 'url(#glow-boss)' : 'url(#glow-hidden)'"
            class="node-glow"
          />
          <!-- main body -->
          <circle
            r="16"
            :fill="node.unlocked ? '#11181d' : '#0a0e10'"
            :stroke="VOLUME_COLORS[node.volume] || '#4a5a5d'"
            :stroke-width="node.id === selectedId ? 3 : 1.5"
            :stroke-opacity="node.unlocked ? 0.9 : 0.3"
            :filter="node.unlocked ? 'url(#star-glow)' : ''"
          />
          <!-- type icon -->
          <text
            text-anchor="middle"
            dy="5"
            :fill="node.unlocked ? (VOLUME_COLORS[node.volume] || '#7e9292') : '#3a4545'"
            font-size="14"
          >{{ TYPE_ICONS[node.type] }}</text>
          <!-- label -->
          <text
            text-anchor="middle"
            dy="32"
            :fill="node.unlocked ? '#c9d8d5' : '#4a5555'"
            font-size="11"
            opacity="0.9"
          >{{ node.name }}</text>
          <!-- completed check -->
          <text
            v-if="node.completed"
            text-anchor="middle"
            dy="-22"
            fill="#98c9bb"
            font-size="11"
          >✓</text>
        </g>
      </svg>

      <div class="sc-zoom-hint">🖱 滚轮缩放 | 拖拽平移 | 点击节点查看详情</div>
    </div>

    <!-- side detail panel -->
    <aside v-if="selectedNode" class="sc-detail">
      <h3 :style="{ color: VOLUME_COLORS[selectedNode.volume] || '#caa86a' }">
        {{ TYPE_ICONS[selectedNode.type] }} {{ selectedNode.name }}
      </h3>
      <div class="sc-meta">
        <span :class="['sc-badge', selectedNode.type]">{{ { main: '主线', side: '支线', hidden: '隐藏', boss: 'Boss' }[selectedNode.type] }}</span>
        <span class="sc-volume-tag">第{{ selectedNode.volume }}卷</span>
        <span class="sc-level">Lv.{{ selectedNode.minLevel }}+</span>
        <span class="sc-danger">危险度 {{ '★'.repeat(selectedNode.dangerLevel) }}</span>
      </div>
      <p class="sc-desc">{{ selectedNode.description }}</p>
      <div class="sc-chapter-info">
        <span>📖 第{{ selectedNode.chapter }}章：{{ selectedNode.chapterName }}</span>
      </div>
      <div v-if="selectedNode.keyItems.length" class="sc-items">
        <span class="sc-items-label">可获取：</span>
        <span v-for="item in selectedNode.keyItems" :key="item" class="sc-item-tag">{{ item }}</span>
      </div>
      <div class="sc-status">
        <span v-if="selectedNode.completed" class="sc-done">✓ 已完成</span>
        <span v-else-if="selectedNode.unlocked" class="sc-available">⚡ 可进入</span>
        <span v-else class="sc-locked">🔒 未解锁</span>
      </div>
      <button v-if="selectedNode.unlocked && !selectedNode.completed" class="sc-enter-btn">
        传送至此处
      </button>
    </aside>
  </div>
</template>

<style scoped>
.star-chart-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.sc-filters {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-bottom: 1px solid #1e2a33;
  flex-wrap: wrap;
}

.sc-filters button {
  padding: 3px 12px;
  border: 1px solid #28343d;
  border-radius: 3px;
  background: #0f1519;
  color: #7e9292;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.sc-filters button:hover {
  border-color: #4a5a5d;
  color: #c9d8d5;
}
.sc-filters button.active {
  border-color: #caa86a;
  color: #caa86a;
  background: rgba(202, 168, 106, 0.08);
}
.sc-filters .sep {
  color: #28343d;
  margin: 0 4px;
}
.sc-filters .sc-reset {
  margin-left: auto;
  color: #d97b6c;
  border-color: #3a2a28;
}

.sc-canvas-wrap {
  flex: 1;
  position: relative;
  overflow: hidden;
  cursor: grab;
  background: radial-gradient(ellipse at 40% 50%, #151b20 0%, #080b0d 100%);
}
.sc-canvas-wrap:active {
  cursor: grabbing;
}

.sc-svg {
  width: 100%;
  height: 100%;
  display: block;
}

.star-node {
  cursor: pointer;
  transition: opacity 0.2s;
}
.star-node.is-locked {
  opacity: 0.45;
}
.star-node.is-locked:hover {
  opacity: 0.65;
}
.star-node.is-unlocked:hover circle:first-of-type {
  filter: url(#star-glow);
}

.sc-zoom-hint {
  position: absolute;
  bottom: 10px;
  right: 14px;
  font-size: 11px;
  color: #3a4545;
  pointer-events: none;
}

.sc-detail {
  border-top: 1px solid #1e2a33;
  background: #0f1519;
  padding: 16px 20px;
  max-height: 220px;
  overflow-y: auto;
}
.sc-detail h3 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
}
.sc-meta {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.sc-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 2px;
  border: 1px solid;
}
.sc-badge.main { color: #caa86a; border-color: #554b34; }
.sc-badge.side { color: #8db8d8; border-color: #2d3a4a; }
.sc-badge.hidden { color: #98c9bb; border-color: #2a3a35; }
.sc-badge.boss { color: #d97b6c; border-color: #3a2a28; }
.sc-volume-tag { font-size: 12px; color: #7e9292; }
.sc-level { font-size: 12px; color: #caa86a; }
.sc-danger { font-size: 11px; color: #d97b6c; }
.sc-desc {
  margin: 0 0 10px;
  font-size: 13px;
  color: #9aaca8;
  line-height: 1.6;
}
.sc-chapter-info {
  font-size: 12px;
  color: #7e9292;
  margin-bottom: 8px;
}
.sc-items { margin-bottom: 10px; }
.sc-items-label { font-size: 12px; color: #7e9292; }
.sc-item-tag {
  display: inline-block;
  padding: 1px 8px;
  margin: 2px 4px 2px 0;
  border: 1px solid #28343d;
  border-radius: 2px;
  font-size: 11px;
  color: #98c9bb;
  background: rgba(152, 201, 187, 0.05);
}
.sc-status { margin-bottom: 10px; font-size: 13px; }
.sc-done { color: #98c9bb; }
.sc-available { color: #caa86a; }
.sc-locked { color: #4a5555; }
.sc-enter-btn {
  padding: 6px 24px;
  border: 1px solid #caa86a;
  border-radius: 3px;
  background: rgba(202, 168, 106, 0.1);
  color: #caa86a;
  font-size: 13px;
  cursor: pointer;
}
.sc-enter-btn:hover {
  background: rgba(202, 168, 106, 0.2);
}
</style>
