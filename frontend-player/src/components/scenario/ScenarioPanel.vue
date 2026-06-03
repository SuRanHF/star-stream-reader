<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { exploreApi } from '@/api/exploreApi';
import { useGameStore } from '@/stores/gameStore';

const game = useGameStore();

interface LocationInfo {
  location_key: string;
  location_name: string;
  description: string;
  danger_level: number;
  min_level: number;
  recommended_rank: string;
  is_unlocked: boolean;
  stories_exhausted: boolean;
  total_stories: number;
  triggered_stories: number;
  event_rates?: Record<string, number>;
}

interface ScenarioState {
  name: string;
  difficulty: string;
  phase: number;
  totalPhases: number;
  phases: string[];
  currentObjective: string;
  objectives: { label: string; done: boolean }[];
  rewardPreview: string;
  failureCondition: string;
  locationName: string;
  dangerLevel: number;
  active: boolean;
}

const locations = ref<LocationInfo[]>([]);
const loading = ref(false);

const EVENT_TYPE_NAMES: Record<string, string> = {
  story: '主线故事',
  side_story: '支线剧情',
  resource: '资源采集',
  opportunity: '机遇事件',
  boss_clue: 'Boss线索',
  hidden: '隐藏剧情',
  empty: '探索空白',
};

async function fetchLocations() {
  const id = Number(game.player?.id || game.player?.playerId || 0);
  if (!id) return;
  loading.value = true;
  try {
    const payload = await exploreApi.getLocations(id);
    const raw = (payload as any)?.locations || (payload as any)?.data?.locations || [];
    locations.value = raw.map((loc: any) => ({
      location_key: loc.location_key || '',
      location_name: loc.location_name || loc.name || '',
      description: loc.description || '',
      danger_level: loc.danger_level ?? loc.dangerLevel ?? 1,
      min_level: loc.min_level ?? loc.minLevel ?? 1,
      recommended_rank: loc.recommended_rank || loc.recommendedRank || 'F',
      is_unlocked: loc.is_unlocked ?? loc.unlocked ?? false,
      stories_exhausted: loc.stories_exhausted ?? false,
      total_stories: loc.total_stories ?? 0,
      triggered_stories: loc.triggered_stories ?? 0,
      event_rates: loc.event_rates || loc.eventRates || {},
    }));
  } catch {
    locations.value = [];
  } finally {
    loading.value = false;
  }
}

watch(() => game.player?.id, (id) => {
  if (id) fetchLocations();
}, { immediate: true });

onMounted(() => {
  if (game.player?.id) fetchLocations();
});

function currentLocation(): LocationInfo | null {
  const locKey = String(game.player?.currentLocationName
    || game.player?.currentLocationKey
    || game.player?.currentLocation
    || '');
  if (!locKey) return null;
  return locations.value.find(l =>
    l.location_name === locKey || l.location_key === locKey
  ) || null;
}

function difficultyFromRank(rank: string): string {
  return rank || 'F';
}

function difficultyFromDanger(level: number): string {
  if (level >= 8) return 'S';
  if (level >= 6) return 'A';
  if (level >= 5) return 'B';
  if (level >= 4) return 'C';
  if (level >= 3) return 'D';
  if (level >= 2) return 'E';
  return 'F';
}

function genPhasesFromRates(rates: Record<string, number>): string[] {
  const phases: string[] = [];
  const ordered = ['story', 'side_story', 'resource', 'opportunity', 'boss_clue', 'hidden'];
  for (const key of ordered) {
    if (rates[key] && rates[key] > 0 && key !== 'empty') {
      phases.push(EVENT_TYPE_NAMES[key] || key);
    }
  }
  if (phases.length === 0) phases.push('探索', '收尾');
  return phases;
}

function genObjectives(phases: string[], triggeredStories: number, totalStories: number): { label: string; done: boolean }[] {
  const objs: { label: string; done: boolean }[] = [];
  const donePhases = Math.min(triggeredStories, phases.length);
  for (let i = 0; i < phases.length; i++) {
    objs.push({
      label: `完成 ${phases[i]} 阶段`,
      done: i < donePhases,
    });
  }
  if (totalStories > 0 && triggeredStories >= totalStories) {
    // All stories exhausted - mark everything complete
    return objs.map(o => ({ ...o, done: true }));
  }
  return objs;
}

const scenario = computed<ScenarioState | null>(() => {
  const loc = currentLocation();
  const locStr = String(game.player?.currentLocationName
    || game.player?.currentLocationKey
    || game.player?.currentLocation
    || '');

  if (!locStr) return null;

  const rates = loc?.event_rates || {};
  const phases = genPhasesFromRates(rates);
  const triggeredStories = loc?.triggered_stories ?? 0;
  const totalStories = loc?.total_stories ?? 0;
  const objectives = genObjectives(phases, triggeredStories, totalStories);
  const totalPhases = phases.length;
  const doneCount = objectives.filter(o => o.done).length;
  const currentPhase = Math.min(doneCount + 1, totalPhases);

  const dangerLevel = loc?.danger_level ?? 1;
  const difficulty = loc?.recommended_rank
    ? difficultyFromRank(loc.recommended_rank)
    : difficultyFromDanger(dangerLevel);

  const baseReward = dangerLevel * 800;
  const fragmentBonus = dangerLevel >= 3 ? ` + 寓言碎片×${dangerLevel - 2}` : '';
  const rewardPreview = `${baseReward} 星币${fragmentBonus}`;

  const scenarioName = loc
    ? `${loc.location_name}探索行动`
    : `${locStr}探索行动`;

  const currentObj = objectives.find(o => !o.done);
  const currentObjective = currentObj
    ? currentObj.label
    : '所有目标已完成 — 刷新场景或前往新地点';

  return {
    name: scenarioName,
    difficulty,
    phase: currentPhase,
    totalPhases,
    phases,
    currentObjective,
    objectives,
    rewardPreview,
    failureCondition: dangerLevel >= 4 ? '生命归零或场景事件耗尽' : '生命归零或放弃场景',
    locationName: locStr,
    dangerLevel,
    active: true,
  };
});

const difficultyColor = computed(() => {
  const d = scenario.value?.difficulty || 'F';
  const colors: Record<string, string> = {
    'F': '#5a6688',
    'E': '#8a96b8',
    'D': '#4a8fe7',
    'C': '#a080e0',
    'B': '#6aafff',
    'A': '#e0556a',
    'S': '#c0a0f0',
    'SS': '#e0556a',
    'SSS': '#e0556a',
  };
  return colors[d] || '#5a6688';
});

const phasePercent = computed(() => {
  if (!scenario.value) return 0;
  const done = scenario.value.objectives.filter(o => o.done).length;
  const total = scenario.value.objectives.length;
  return Math.round((done / total) * 100);
});
</script>

<template>
  <div v-if="scenario" class="scp-root">
    <!-- 场景公告 -->
    <div class="scp-header">
      <div class="scp-badge" :style="{ borderColor: difficultyColor, color: difficultyColor }">
        {{ scenario.difficulty }}级
      </div>
      <div class="scp-name">{{ scenario.name }}</div>
      <div class="scp-loc">📍 {{ scenario.locationName }}</div>
    </div>

    <!-- 阶段进度条 -->
    <div class="scp-phases">
      <div class="scp-phase-track">
        <div class="scp-phase-fill" :style="{ width: `${phasePercent}%` }"></div>
      </div>
      <div class="scp-phase-dots">
        <div
          v-for="(label, i) in scenario.phases"
          :key="i"
          :class="['scp-phase-dot', {
            'is-done': i < scenario.phase - 1,
            'is-current': i === scenario.phase - 1,
          }]"
        >
          <span class="scp-dot-circle"></span>
          <span class="scp-dot-label">{{ label }}</span>
        </div>
      </div>
    </div>

    <!-- 当前目标 -->
    <div class="ling-system-msg">
      <div class="ling-system-msg-body">
        <p class="scp-objective-text">{{ scenario.currentObjective }}</p>
      </div>
    </div>

    <!-- 目标清单 -->
    <div class="scp-objectives">
      <div
        v-for="(obj, i) in scenario.objectives"
        :key="i"
        :class="['scp-obj', { 'is-done': obj.done }]"
      >
        <span class="scp-obj-check">{{ obj.done ? '✓' : '○' }}</span>
        <span class="scp-obj-label">{{ obj.label }}</span>
      </div>
    </div>

    <!-- 探索进度 -->
    <div v-if="currentLocation()" class="scp-story-progress">
      <span class="scp-progress-label">探索进度</span>
      <span class="scp-progress-value">
        {{ currentLocation()!.triggered_stories }} / {{ currentLocation()!.total_stories || '?' }} 故事
      </span>
      <span v-if="currentLocation()!.stories_exhausted" class="scp-exhausted-badge">故事已耗尽</span>
    </div>

    <!-- 奖励 & 失败条件 -->
    <div class="scp-meta">
      <div class="scp-meta-item">
        <span class="scp-meta-label">🎁 奖励</span>
        <span class="scp-meta-value gold">{{ scenario.rewardPreview }}</span>
      </div>
      <div class="scp-meta-item">
        <span class="scp-meta-label">⚠ 失败</span>
        <span class="scp-meta-value red">{{ scenario.failureCondition }}</span>
      </div>
    </div>
  </div>

  <!-- 无活跃场景 -->
  <div v-else class="scp-empty">
    <div class="scp-empty-icon">◈</div>
    <p class="scp-empty-title">暂无活跃场景</p>
    <p class="scp-empty-hint">前往"场景地图"选择一个地点，然后点击"进入场景"开始探索。</p>
  </div>
</template>

<style scoped>
.scp-root {
  padding: 4px 0;
}

/* ── 头部 ── */
.scp-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.scp-badge {
  padding: 3px 10px;
  border: 1.5px solid;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  flex-shrink: 0;
}

.scp-name {
  color: #d0d8f0;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 2px;
  flex: 1;
  min-width: 0;
}

.scp-loc {
  font-size: 12px;
  color: var(--color-text-dim);
  flex-shrink: 0;
}

/* ── 阶段进度 ── */
.scp-phases {
  margin-bottom: 14px;
}

.scp-phase-track {
  height: 2px;
  background: rgba(26, 38, 80, 0.5);
  border-radius: 1px;
  margin-bottom: 10px;
}

.scp-phase-fill {
  height: 100%;
  background: var(--color-system);
  border-radius: 1px;
  transition: width 0.5s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1));
  box-shadow: 0 0 6px rgba(74, 143, 231, 0.4);
}

.scp-phase-dots {
  display: flex;
  justify-content: space-between;
}

.scp-phase-dot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  opacity: 0.35;
  transition: opacity 0.3s;
}

.scp-phase-dot.is-current,
.scp-phase-dot.is-done {
  opacity: 1;
}

.scp-dot-circle {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid var(--color-muted);
  background: transparent;
  transition: all 0.3s;
}

.scp-phase-dot.is-current .scp-dot-circle {
  border-color: var(--color-system);
  background: var(--color-system);
  box-shadow: 0 0 8px rgba(74, 143, 231, 0.5);
  animation: pulseGlow 2s ease-in-out infinite;
}

.scp-phase-dot.is-done .scp-dot-circle {
  border-color: var(--color-spirit);
  background: var(--color-spirit);
}

.scp-dot-label {
  font-size: 10px;
  color: var(--color-text-dim);
  letter-spacing: 1px;
  white-space: nowrap;
}

/* ── 目标文字 ── */
.scp-objective-text {
  color: var(--color-system-bright);
  font-size: 14px;
  font-weight: 600;
  text-align: center;
  letter-spacing: 1px;
}

/* ── 目标清单 ── */
.scp-objectives {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.scp-obj {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 5px;
  border: 1px solid var(--color-border);
  background: rgba(7, 11, 26, 0.4);
  transition: all 0.3s;
}

.scp-obj.is-done {
  border-color: rgba(94, 196, 158, 0.2);
  background: rgba(94, 196, 158, 0.04);
}

.scp-obj-check {
  font-size: 14px;
  flex-shrink: 0;
  color: var(--color-muted);
}

.scp-obj.is-done .scp-obj-check {
  color: var(--color-spirit);
}

.scp-obj-label {
  font-size: 13px;
  color: var(--color-text-dim);
}

.scp-obj.is-done .scp-obj-label {
  color: var(--color-muted);
  text-decoration: line-through;
}

/* ── 故事进度 ── */
.scp-story-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: 5px;
  border: 1px solid var(--color-border);
  background: rgba(7, 11, 26, 0.3);
}
.scp-progress-label {
  font-size: 11px;
  color: var(--color-muted);
}
.scp-progress-value {
  font-size: 12px;
  color: var(--color-system-bright);
  font-weight: 600;
  flex: 1;
}
.scp-exhausted-badge {
  font-size: 10px;
  color: var(--color-spirit);
  padding: 2px 6px;
  border: 1px solid rgba(94, 196, 158, 0.3);
  border-radius: 2px;
}

/* ── 奖励/失败 ── */
.scp-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 5px;
  border: 1px solid var(--color-border);
  background: rgba(7, 11, 26, 0.3);
}

.scp-meta-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.scp-meta-label {
  font-size: 12px;
  color: var(--color-muted);
  flex-shrink: 0;
}

.scp-meta-value {
  font-size: 12px;
  text-align: right;
}

.scp-meta-value.gold { color: var(--color-star); }
.scp-meta-value.red { color: var(--color-danger); }

/* ── 空状态 ── */
.scp-empty {
  text-align: center;
  padding: 40px 20px;
}

.scp-empty-icon {
  font-size: 36px;
  color: var(--color-muted);
  opacity: 0.5;
  margin-bottom: 12px;
}

.scp-empty-title {
  color: var(--color-text-dim);
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 8px;
  letter-spacing: 1px;
}

.scp-empty-hint {
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
}

@keyframes pulseGlow {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}
</style>
