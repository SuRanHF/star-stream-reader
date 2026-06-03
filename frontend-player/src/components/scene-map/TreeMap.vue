<script setup lang="ts">
import { ref } from 'vue';
import { volumeTreeData, type ChapterInfo, type SceneInfo } from './mockData';

const VOLUME_COLORS: Record<number, string> = {
  1: '#a0b0f0',
  2: '#e07070',
  3: '#7ab0f7',
  4: '#5ec49e',
};

const TYPE_LABELS: Record<string, string> = {
  main: '主线',
  side: '支线',
  hidden: '隐藏',
  boss: 'Boss',
};

const expandedVolumes = ref<Set<number>>(new Set([1]));
const expandedChapters = ref<Set<string>>(new Set());
const selectedScene = ref<SceneInfo | null>(null);

function toggleVolume(vol: number) {
  if (expandedVolumes.value.has(vol)) {
    expandedVolumes.value.delete(vol);
  } else {
    expandedVolumes.value.add(vol);
  }
  expandedVolumes.value = new Set(expandedVolumes.value);
}

function toggleChapter(key: string) {
  if (expandedChapters.value.has(key)) {
    expandedChapters.value.delete(key);
  } else {
    expandedChapters.value.add(key);
  }
  expandedChapters.value = new Set(expandedChapters.value);
}

function selectScene(scene: SceneInfo, _chapter: ChapterInfo) {
  selectedScene.value = scene;
}

function sceneDotClass(scene: SceneInfo) {
  return {
    'tc-dot': true,
    [scene.type]: true,
    completed: scene.completed,
    unlocked: scene.unlocked,
    locked: !scene.unlocked,
  };
}
</script>

<template>
  <div class="tree-map-root">
    <!-- left: tree -->
    <nav class="tm-tree">
      <div class="tm-tree-header">场景目录</div>
      <div class="tm-tree-body">
        <div
          v-for="vol in volumeTreeData"
          :key="'v' + vol.volume"
          class="tm-volume"
        >
          <button
            class="tm-vol-btn"
            :style="{ borderLeftColor: VOLUME_COLORS[vol.volume] || '#4a5a7a' }"
            @click="toggleVolume(vol.volume)"
          >
            <span class="tm-arrow">{{ expandedVolumes.has(vol.volume) ? '▾' : '▸' }}</span>
            <span class="tm-vol-num" :style="{ color: VOLUME_COLORS[vol.volume] || '#6a7a9a' }">
              第{{ vol.volume }}卷
            </span>
            <span class="tm-vol-title">{{ vol.title }}</span>
            <span class="tm-vol-sub">{{ vol.subtitle }}</span>
          </button>

          <div v-if="expandedVolumes.has(vol.volume)" class="tm-chapters">
            <div
              v-for="ch in vol.chapters"
              :key="ch.key"
              class="tm-chapter"
            >
              <button
                class="tm-ch-btn"
                :class="{ locked: !ch.unlocked }"
                @click="ch.unlocked && toggleChapter(ch.key)"
              >
                <span class="tm-arrow-sm">
                  {{ !ch.unlocked ? '🔒' : expandedChapters.has(ch.key) ? '▾' : '▸' }}
                </span>
                <span class="tm-ch-num">第{{ ch.chapter }}章</span>
                <span class="tm-ch-title">{{ ch.title }}</span>
                <span v-if="ch.completed" class="tm-ch-done">✓</span>
                <span v-else-if="ch.unlocked" class="tm-ch-open">进行中</span>
              </button>

              <div
                v-if="expandedChapters.has(ch.key) && ch.unlocked"
                class="tm-scenes"
              >
                <button
                  v-for="scene in ch.scenes"
                  :key="scene.key"
                  :class="sceneDotClass(scene)"
                  @click="selectScene(scene, ch)"
                >
                  <span class="tc-dot-icon">
                    {{ { main: '◆', side: '◇', hidden: '◎', boss: '⬡' }[scene.type] }}
                  </span>
                  <span class="tc-scene-name">{{ scene.name }}</span>
                  <span class="tc-scene-type">{{ TYPE_LABELS[scene.type] }}</span>
                  <span class="tc-scene-lv">Lv.{{ scene.minLevel }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </nav>

    <!-- right: detail -->
    <main class="tm-detail">
      <template v-if="selectedScene">
        <div class="tm-detail-header">
          <h3 :style="{ color: VOLUME_COLORS[1] || 'var(--color-system-bright, #7ab0f7)' }">
            {{ { main: '◆', side: '◇', hidden: '◎', boss: '⬡' }[selectedScene.type] }}
            {{ selectedScene.name }}
          </h3>
          <div class="tm-badges">
            <span :class="['tm-badge', selectedScene.type]">{{ TYPE_LABELS[selectedScene.type] }}</span>
            <span class="tm-badge-lv">推荐等级 Lv.{{ selectedScene.minLevel }}+</span>
            <span class="tm-badge-danger">危险度 {{ '★'.repeat(selectedScene.dangerLevel) }}</span>
          </div>
        </div>
        <p class="tm-desc">{{ selectedScene.description }}</p>
        <div class="tm-items" v-if="selectedScene.keyItems.length">
          <span class="tm-items-label">可获得物品：</span>
          <span v-for="item in selectedScene.keyItems" :key="item" class="tm-item">{{ item }}</span>
        </div>
        <div class="tm-status-area">
          <div v-if="selectedScene.completed" class="tm-status done">✓ 已完成探索</div>
          <div v-else-if="selectedScene.unlocked" class="tm-status available">⚡ 可以进入</div>
          <div v-else class="tm-status locked">🔒 尚未解锁 — 完成前置场景后开启</div>
        </div>
        <div class="tm-actions">
          <button v-if="selectedScene.unlocked && !selectedScene.completed" class="tm-enter">
            传送到 {{ selectedScene.name }}
          </button>
          <button class="tm-track">标记追踪</button>
        </div>

        <!-- progress bar -->
        <div class="tm-progress-section">
          <div class="tm-progress-label">
            当前卷探索进度
            <span class="tm-progress-pct">3 / 10 场景</span>
          </div>
          <div class="tm-progress-bar">
            <div class="tm-progress-fill" style="width: 30%"></div>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="tm-empty">
          <div class="tm-empty-icon">🗺</div>
          <p>从左侧目录选择一个场景查看详情</p>
          <p class="tm-empty-hint">展开卷 → 展开章节 → 点击场景</p>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.tree-map-root {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ── left tree ── */
.tm-tree {
  width: 320px;
  min-width: 260px;
  border-right: 1px solid rgba(26, 38, 80, 0.4);
  background: rgba(7, 11, 26, 0.7);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.tm-tree-header {
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-system-bright, #7ab0f7);
  border-bottom: 1px solid rgba(26, 38, 80, 0.4);
  letter-spacing: 2px;
}
.tm-tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.tm-volume { border-bottom: 1px solid rgba(26, 38, 80, 0.25); }
.tm-vol-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 10px 12px 10px 8px;
  border: none;
  border-left: 3px solid transparent;
  background: transparent;
  color: #d0d8f0;
  font-size: 13px;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}
.tm-vol-btn:hover { background: rgba(74, 143, 231, 0.04); }
.tm-arrow { font-size: 10px; color: #5a6688; width: 14px; text-align: center; flex-shrink: 0; }
.tm-vol-num { font-weight: 600; white-space: nowrap; }
.tm-vol-title { font-weight: 700; white-space: nowrap; }
.tm-vol-sub { font-size: 10px; color: #5a6688; margin-left: auto; font-style: italic; }

.tm-chapters { padding-left: 0; }
.tm-chapter { border-top: 1px solid rgba(26, 38, 80, 0.2); }

.tm-ch-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px 8px 22px;
  border: none;
  background: transparent;
  color: #8898b8;
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s;
}
.tm-ch-btn:hover { background: rgba(74, 143, 231, 0.04); }
.tm-ch-btn.locked { opacity: 0.4; cursor: not-allowed; }
.tm-arrow-sm { font-size: 9px; width: 16px; text-align: center; flex-shrink: 0; }
.tm-ch-num { color: #6a7a9a; white-space: nowrap; }
.tm-ch-title { font-weight: 600; }
.tm-ch-done { color: #5ec49e; margin-left: auto; font-size: 12px; }
.tm-ch-open { color: var(--color-system-bright, #7ab0f7); margin-left: auto; font-size: 10px; }

.tm-scenes { padding-left: 0; }

.tc-dot {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 12px 6px 38px;
  border: none;
  background: transparent;
  color: #6a7a9a;
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
  border-left: 2px solid transparent;
}
.tc-dot:hover { background: rgba(74, 143, 231, 0.05); color: #b0bdd8; }
.tc-dot.locked { opacity: 0.35; cursor: not-allowed; }
.tc-dot.main { border-left-color: var(--color-system-bright, #7ab0f7); }
.tc-dot.side { border-left-color: #7ab0f7; }
.tc-dot.hidden { border-left-color: #5ec49e; }
.tc-dot.boss { border-left-color: #e07070; }
.tc-dot.completed .tc-scene-name { color: #5ec49e; }

.tc-dot-icon { font-size: 8px; width: 14px; text-align: center; flex-shrink: 0; }
.tc-dot.main .tc-dot-icon { color: var(--color-system-bright, #7ab0f7); }
.tc-dot.side .tc-dot-icon { color: #7ab0f7; }
.tc-dot.hidden .tc-dot-icon { color: #5ec49e; }
.tc-dot.boss .tc-dot-icon { color: #e07070; }

.tc-scene-name { font-weight: 600; flex: 1; }
.tc-scene-type { font-size: 10px; color: #5a6688; }
.tc-scene-lv { font-size: 10px; color: #6a7a9a; }

/* ── right detail ── */
.tm-detail {
  flex: 1;
  padding: 24px 28px;
  overflow-y: auto;
  background: rgba(7, 11, 26, 0.5);
}

.tm-detail-header h3 {
  margin: 0 0 10px;
  font-size: 22px;
  font-weight: 700;
}
.tm-badges {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.tm-badge {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 2px;
  border: 1px solid;
}
.tm-badge.main { color: var(--color-system-bright, #7ab0f7); border-color: rgba(74, 143, 231, 0.3); }
.tm-badge.side { color: #7ab0f7; border-color: rgba(74, 143, 231, 0.3); }
.tm-badge.hidden { color: #5ec49e; border-color: rgba(94, 196, 158, 0.3); }
.tm-badge.boss { color: #e07070; border-color: rgba(200, 80, 80, 0.3); }
.tm-badge-lv { font-size: 12px; color: var(--color-system-bright, #7ab0f7); }
.tm-badge-danger { font-size: 11px; color: #e07070; }

.tm-desc {
  font-size: 14px;
  color: #8898b8;
  line-height: 1.8;
  margin: 0 0 18px;
}

.tm-items { margin-bottom: 16px; }
.tm-items-label { font-size: 12px; color: #6a7a9a; margin-right: 6px; }
.tm-item {
  display: inline-block;
  padding: 2px 10px;
  margin: 2px 6px 2px 0;
  border: 1px solid rgba(38, 56, 120, 0.3);
  border-radius: 3px;
  font-size: 12px;
  color: #5ec49e;
  background: rgba(94, 196, 158, 0.05);
}

.tm-status-area { margin-bottom: 16px; }
.tm-status { font-size: 14px; font-weight: 600; }
.tm-status.done { color: #5ec49e; }
.tm-status.available { color: var(--color-system-bright, #7ab0f7); }
.tm-status.locked { color: #5a6688; }

.tm-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 24px;
}
.tm-enter {
  padding: 8px 24px;
  border: 1px solid var(--color-system, #4a8fe7);
  border-radius: 3px;
  background: rgba(74, 143, 231, 0.1);
  color: var(--color-system-bright, #7ab0f7);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.tm-enter:hover { background: rgba(74, 143, 231, 0.2); box-shadow: 0 0 12px rgba(74, 143, 231, 0.12); }
.tm-track {
  padding: 8px 18px;
  border: 1px solid rgba(38, 56, 120, 0.3);
  border-radius: 3px;
  background: transparent;
  color: #6a7a9a;
  font-size: 13px;
  cursor: pointer;
}
.tm-track:hover { border-color: rgba(74, 143, 231, 0.35); color: #b0bdd8; }

.tm-progress-section {
  border-top: 1px solid rgba(26, 38, 80, 0.4);
  padding-top: 16px;
}
.tm-progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6a7a9a;
  margin-bottom: 6px;
}
.tm-progress-pct { color: var(--color-system-bright, #7ab0f7); }
.tm-progress-bar {
  height: 4px;
  background: rgba(26, 38, 80, 0.3);
  border-radius: 2px;
  overflow: hidden;
}
.tm-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4a8fe7, #7ab0f7);
  border-radius: 2px;
  transition: width 0.5s;
}

.tm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #5a6688;
}
.tm-empty-icon { font-size: 48px; margin-bottom: 12px; opacity: 0.5; }
.tm-empty p { margin: 4px 0; font-size: 14px; }
.tm-empty-hint { font-size: 12px; color: #4a558a; }
</style>
