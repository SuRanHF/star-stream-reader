<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { broadcastApi } from '@/api/broadcastApi';
import { useGameStore } from '@/stores/gameStore';
import type { ApiRecord } from '@/types/api';

const gameStore = useGameStore();

function asRecord(value: unknown): ApiRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as ApiRecord : {};
}

function objectiveSummary(event: ApiRecord): string[] {
  const progress = asRecord(event.progress);
  const metadata = asRecord(event.metadata);
  const rawObjectives = event.objectives ?? progress.objectives ?? metadata.objectives;
  if (!Array.isArray(rawObjectives)) return [];

  return rawObjectives.slice(0, 2).flatMap((value) => {
    const objective = asRecord(value);
    const label = String(objective.label || '').trim();
    const target = Number(objective.target);
    const current = Number(objective.current ?? 0);
    if (!label || !Number.isFinite(target) || target <= 0 || !Number.isFinite(current)) return [];
    return [`${label} ${Math.min(current, target)}/${target}`];
  });
}

const activeEvents = computed(() => {
  const raw = gameStore.broadcastSummary.activeEvents;
  return Array.isArray(raw) ? raw.map(asRecord) : [];
});
function eventKey(event: ApiRecord): string {
  return String(event.eventKey ?? event.eventId ?? event.id ?? '').trim();
}

function eventTitle(event: ApiRecord): string {
  return String(event.title ?? event.eventName ?? '星流放送');
}

const selectableEvents = computed(() => activeEvents.value.filter((event) => eventKey(event)));
const selectedEventKey = ref('');
watch(selectableEvents, (events) => {
  if (!events.some((event) => eventKey(event) === selectedEventKey.value)) {
    selectedEventKey.value = eventKey(events[0] ?? {});
  }
}, { immediate: true });

const activeEvent = computed(() => selectableEvents.value.find(
  (event) => eventKey(event) === selectedEventKey.value,
) ?? selectableEvents.value[0] ?? null);
const activeEventKey = computed(() => String(
  activeEvent.value ? eventKey(activeEvent.value) : '',
));
const activeEventTitle = computed(() => activeEvent.value ? eventTitle(activeEvent.value) : '星流放送');
const currentPlayerId = computed(() => Number(
  gameStore.player?.id ?? gameStore.player?.playerId ?? 0,
));

const summaryText = computed(() => {
  const s = gameStore.broadcastSummary;
  if (activeEvent.value) {
    const first = activeEvent.value;
    const progress = asRecord(first.progress);
    const current = Number(first.currentValue ?? progress.currentValue ?? 0);
    const target = Number(first.targetValue ?? progress.targetValue ?? 0);
    const pctValue = Number(first.progressPercent ?? progress.percentage
      ?? (target > 0 ? current * 100 / target : Number.NaN));
    const pct = Number.isFinite(pctValue) ? ` (${Math.round(pctValue)}%)` : '';
    const details = objectiveSummary(first);
    return `${activeEventTitle.value}${pct}${details.length ? ` · ${details.join(' · ')}` : ''}`;
  }

  const topEvents = s.topEvents as ApiRecord[] | undefined;
  if (topEvents && topEvents.length > 0 && topEvents[0]) {
    const ev = topEvents[0];
    const title = String(ev.title || ev.eventName || '');
    const pct = ev.progressPercent ?? ev.progress;
    const prefix = ev.status === 'completed' ? '近期回响 · ' : '';
    if (title) return typeof pct === 'number' ? `${prefix}${title} (${Math.round(pct)}%)` : `${prefix}${title}`;
  }

  const count = Number(s.activeCount || 0);
  return count > 0 ? `${count} 个活跃放送事件` : '暂无活跃事件';
});

const rankingsOpen = ref(false);
const rankingsLoading = ref(false);
const rankingsError = ref('');
const positionLoading = ref(false);
const positionError = ref('');
const rankingEventKey = ref('');
const rankingEventTitle = ref('');
const rankings = ref<ApiRecord[]>([]);
const myPosition = ref<ApiRecord | null>(null);
let rankingLoadSequence = 0;
const historyOpen = ref(false);
const historyLoading = ref(false);
const historyError = ref('');
const historyEvents = ref<ApiRecord[]>([]);
let historyLoadSequence = 0;

function historyStatusLabel(event: ApiRecord): string {
  return event.status === 'completed' ? '目标达成' : '时限结束';
}

function historyProgress(event: ApiRecord): string {
  const current = Math.max(0, Number(event.currentValue ?? 0));
  const target = Math.max(0, Number(event.targetValue ?? 0));
  const percent = target > 0 ? Math.min(100, Math.round(current * 100 / target)) : 0;
  return `${current.toLocaleString('zh-CN')} / ${target.toLocaleString('zh-CN')} · ${percent}%`;
}

function historyEndedAt(event: ApiRecord): string {
  const raw = String(event.endedAt ?? event.endAt ?? '').trim();
  if (!raw) return '';
  const parsed = new Date(raw);
  if (Number.isNaN(parsed.getTime())) return raw.replace('T', ' ');
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(parsed);
}

function historyEffects(event: ApiRecord): string[] {
  if (event.status !== 'completed') return [];
  const summaries = event.worldlineEffectSummaries;
  return Array.isArray(summaries)
    ? summaries.map((value) => String(value)).filter((value) => value.trim())
    : [];
}

async function loadHistory() {
  const loadSequence = ++historyLoadSequence;
  historyLoading.value = true;
  historyError.value = '';
  try {
    const result = await broadcastApi.getHistory(12);
    if (loadSequence !== historyLoadSequence) return;
    if (!Array.isArray(result)) throw new Error('公共活动回顾返回格式异常');
    historyEvents.value = result.map(asRecord);
  } catch {
    if (loadSequence === historyLoadSequence) {
      historyError.value = '过往活动暂时无法读取，请稍后重试。';
    }
  } finally {
    if (loadSequence === historyLoadSequence) historyLoading.value = false;
  }
}

async function loadActiveEventRankings() {
  const eventKey = activeEventKey.value;
  if (!eventKey) return;

  const loadSequence = ++rankingLoadSequence;
  rankingsLoading.value = true;
  rankingsError.value = '';
  positionLoading.value = currentPlayerId.value > 0;
  positionError.value = '';
  rankings.value = [];
  myPosition.value = null;
  rankingEventKey.value = eventKey;
  rankingEventTitle.value = activeEventTitle.value;

  const listRequest = broadcastApi.getRankings(eventKey, 10)
    .then((result) => {
      if (loadSequence !== rankingLoadSequence) return;
      if (!Array.isArray(result)) throw new Error('同行者榜返回格式异常');
      rankings.value = result;
    })
    .catch(() => {
      if (loadSequence === rankingLoadSequence) {
        rankingsError.value = '同行者榜暂时无法读取，请稍后重试。';
      }
    })
    .finally(() => {
      if (loadSequence === rankingLoadSequence) rankingsLoading.value = false;
    });

  const playerId = currentPlayerId.value;
  const positionRequest = playerId > 0
    ? broadcastApi.getMyRankingPosition(eventKey, playerId)
      .then((position) => {
        if (loadSequence === rankingLoadSequence) myPosition.value = asRecord(position);
      })
      .catch(() => {
        if (loadSequence === rankingLoadSequence) {
          positionError.value = '你的本场位置暂时无法读取，贡献榜仍可查看。';
        }
      })
      .finally(() => {
        if (loadSequence === rankingLoadSequence) positionLoading.value = false;
      })
    : Promise.resolve();

  await Promise.all([listRequest, positionRequest]);
}

watch(activeEventKey, (eventKey, previousEventKey) => {
  if (!eventKey) {
    rankingLoadSequence += 1;
    rankingsOpen.value = false;
    rankingsLoading.value = false;
    rankingsError.value = '';
    positionLoading.value = false;
    positionError.value = '';
    rankingEventKey.value = '';
    rankingEventTitle.value = '';
    rankings.value = [];
    myPosition.value = null;
    return;
  }

  if (rankingsOpen.value && eventKey && eventKey !== previousEventKey) {
    void loadActiveEventRankings();
  }
});

async function toggleRankings() {
  if (rankingsOpen.value) {
    rankingsOpen.value = false;
    return;
  }

  historyLoadSequence += 1;
  historyOpen.value = false;
  historyLoading.value = false;
  rankingsOpen.value = true;
  await loadActiveEventRankings();
}

async function toggleHistory() {
  if (historyOpen.value) {
    historyOpen.value = false;
    historyLoadSequence += 1;
    historyLoading.value = false;
    return;
  }

  rankingLoadSequence += 1;
  rankingsOpen.value = false;
  rankingsLoading.value = false;
  positionLoading.value = false;
  historyOpen.value = true;
  await loadHistory();
}

function rankingPlayerId(entry: ApiRecord): number {
  return Number(entry.player_id ?? entry.playerId ?? 0);
}

function rankingName(entry: ApiRecord): string {
  return String(entry.player_name ?? entry.playerName ?? '未留名的同行者');
}

function rankingPosition(entry: ApiRecord, index: number): number {
  const position = Number(entry.rank);
  return Number.isInteger(position) && position > 0 ? position : index + 1;
}

function personalRank(): number | null {
  const position = Number(myPosition.value?.rank);
  return Number.isInteger(position) && position > 0 ? position : null;
}

function personalContribution(): string {
  const value = Number(myPosition.value?.contribution ?? 0);
  return Number.isFinite(value) ? value.toLocaleString('zh-CN') : '0';
}

function rankingContribution(entry: ApiRecord): string {
  const value = Number(entry.total_contribution ?? entry.contributionValue ?? 0);
  return Number.isFinite(value) ? value.toLocaleString('zh-CN') : '0';
}
</script>

<template>
  <div class="ling-broadcast">
    <div class="ling-broadcast-strip">
      <span class="ling-broadcast-copy" role="status" aria-live="polite">
        <span class="ling-broadcast-label">星流放送</span>
        <span class="ling-broadcast-sep"> · </span>
        <span class="ling-broadcast-text">{{ summaryText }}</span>
      </span>
      <button
        v-if="activeEventKey"
        class="ling-broadcast-rank-toggle"
        type="button"
        aria-controls="ling-broadcast-rankings"
        :aria-expanded="rankingsOpen"
        @click="toggleRankings"
      >
        {{ rankingsOpen ? '收起本场榜单' : '本场贡献榜' }}
        <span aria-hidden="true">{{ rankingsOpen ? '−' : '↗' }}</span>
      </button>
      <button
        class="ling-broadcast-history-toggle"
        type="button"
        aria-controls="ling-broadcast-history"
        :aria-expanded="historyOpen"
        @click="toggleHistory"
      >
        {{ historyOpen ? '收起往期回响' : '往期回响' }}
        <span aria-hidden="true">{{ historyOpen ? '−' : '◷' }}</span>
      </button>
    </div>

    <section
      v-if="rankingsOpen"
      id="ling-broadcast-rankings"
      class="ling-broadcast-rankings"
      aria-label="星流放送本场贡献榜"
    >
      <header class="ling-broadcast-rankings-head">
        <div>
          <p class="ling-broadcast-kicker">同行者 · 本场记录</p>
          <h2>{{ rankingEventTitle }}</h2>
        </div>
        <div class="ling-broadcast-rankings-controls">
          <label v-if="selectableEvents.length > 1" class="ling-broadcast-event-picker">
            <span>查看活动</span>
            <select v-model="selectedEventKey" aria-label="切换公共活动">
              <option
                v-for="event in selectableEvents"
                :key="eventKey(event)"
                :value="eventKey(event)"
              >
                {{ eventTitle(event) }}
              </option>
            </select>
          </label>
          <p class="ling-broadcast-rankings-note">按本场贡献排序，不跨活动累计</p>
        </div>
      </header>

      <aside v-if="currentPlayerId > 0" class="ling-broadcast-my-position" aria-label="我的本场位置">
        <p v-if="positionLoading" class="ling-broadcast-personal-state" role="status">
          正在读取你的本场位置……
        </p>
        <p v-else-if="positionError" class="ling-broadcast-personal-state ling-broadcast-error" role="alert">
          {{ positionError }}
        </p>
        <template v-else-if="myPosition">
          <div class="ling-broadcast-my-rank">
            <span>我的本场位置</span>
            <strong v-if="personalRank()">第 {{ personalRank() }} 位</strong>
            <strong v-else>尚未上榜</strong>
          </div>
          <div class="ling-broadcast-my-progress">
            <span>本场贡献 <b>{{ personalContribution() }}</b></span>
            <span v-if="personalRank() === 1">你已在榜首</span>
            <span v-else-if="myPosition.pointsToOvertakeNext != null">
              再积累 {{ myPosition.pointsToOvertakeNext }} 点可超过上一位
            </span>
            <span v-else>完成一次可计入本场目标的行动即可进入榜单</span>
          </div>
        </template>
      </aside>

      <p v-if="rankingsLoading" class="ling-broadcast-state" role="status">正在读取同行者记录……</p>
      <div v-else-if="rankingsError" class="ling-broadcast-state ling-broadcast-error" role="alert">
        <span>{{ rankingsError }}</span>
        <button class="ling-broadcast-retry" type="button" @click="loadActiveEventRankings">重试</button>
      </div>
      <p v-else-if="rankings.length === 0" class="ling-broadcast-state">
        还没有同行者留下记录，成为第一个吧。
      </p>
      <ol v-else class="ling-broadcast-rank-list">
          <li
            v-for="(entry, index) in rankings"
            :key="String(entry.player_id ?? entry.playerId ?? `${rankingEventKey}-${index}`)"
            class="ling-broadcast-rank-row"
            :class="{
              'is-podium': rankingPosition(entry, index) <= 3,
              'is-self': currentPlayerId > 0 && rankingPlayerId(entry) === currentPlayerId,
            }"
          >
            <span class="ling-broadcast-rank-number">{{ String(rankingPosition(entry, index)).padStart(2, '0') }}</span>
            <span class="ling-broadcast-rank-name">
              {{ rankingName(entry) }}
              <em v-if="currentPlayerId > 0 && rankingPlayerId(entry) === currentPlayerId">你</em>
            </span>
            <span class="ling-broadcast-rank-score">贡献 {{ rankingContribution(entry) }}</span>
          </li>
      </ol>
    </section>

    <section
      v-if="historyOpen"
      id="ling-broadcast-history"
      class="ling-broadcast-history"
      aria-label="近期公共活动回顾"
    >
      <header class="ling-broadcast-history-head">
        <div>
          <p class="ling-broadcast-kicker">共同经历 · 公开结算</p>
          <h2>近期回响</h2>
        </div>
        <p class="ling-broadcast-rankings-note">只记录共同目标与已实际触发的世界线结算，不含个人奖励</p>
      </header>
      <p v-if="historyLoading" class="ling-broadcast-state" role="status">正在翻阅星流记录……</p>
      <div v-else-if="historyError" class="ling-broadcast-state ling-broadcast-error" role="alert">
        <span>{{ historyError }}</span>
        <button class="ling-broadcast-retry" type="button" @click="loadHistory">重试</button>
      </div>
      <p v-else-if="historyEvents.length === 0" class="ling-broadcast-state">
        还没有结束的公共活动记录。下一次共同行动，将从这里留下回响。
      </p>
      <ol v-else class="ling-broadcast-history-list">
        <li v-for="event in historyEvents" :key="String(event.eventKey ?? event.id)" class="ling-broadcast-history-item">
          <div class="ling-broadcast-history-title-row">
            <h3>{{ eventTitle(event) }}</h3>
            <span :class="['ling-broadcast-history-status', event.status === 'completed' ? 'is-completed' : 'is-expired']">
              {{ historyStatusLabel(event) }}
            </span>
          </div>
          <p v-if="event.description" class="ling-broadcast-history-description">{{ event.description }}</p>
          <div class="ling-broadcast-history-meta">
            <span>共同进度 {{ historyProgress(event) }}</span>
            <time v-if="historyEndedAt(event)">{{ historyEndedAt(event) }}</time>
          </div>
          <div v-if="historyEffects(event).length" class="ling-broadcast-history-effects" aria-label="实际触发的世界线变化">
            <span>结算回响</span>
            <b v-for="effect in historyEffects(event)" :key="effect">{{ effect }}</b>
          </div>
          <p v-else-if="event.status !== 'completed'" class="ling-broadcast-history-muted">
            目标未达成，因此没有触发本活动的结算效果。
          </p>
          <p v-else class="ling-broadcast-history-muted">本次结算未配置直接世界线变化。</p>
        </li>
      </ol>
    </section>
  </div>
</template>

<style scoped>
.ling-broadcast {
  position: relative;
  z-index: 4;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-void);
}

.ling-broadcast-strip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  min-height: 38px;
  padding: 0 20px;
  color: var(--color-star);
  font-family: var(--font-serif, "Songti SC", serif);
  font-size: 13px;
  letter-spacing: 0.45px;
}

.ling-broadcast-copy {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ling-broadcast-label {
  color: var(--color-star);
  font-weight: 600;
}

.ling-broadcast-sep {
  color: color-mix(in srgb, var(--color-star) 68%, transparent);
}

.ling-broadcast-text {
  color: var(--color-star);
}

.ling-broadcast-rank-toggle,
.ling-broadcast-history-toggle {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 7px;
  padding: 5px 10px;
  border: 1px solid color-mix(in srgb, var(--color-star) 36%, var(--color-border));
  border-radius: 2px;
  background: color-mix(in srgb, var(--color-star) 7%, var(--color-void));
  color: var(--color-star);
  font: inherit;
  font-size: 11px;
  letter-spacing: 0.6px;
  cursor: pointer;
  transition: background 160ms ease, border-color 160ms ease, transform 160ms ease;
}

.ling-broadcast-rank-toggle:hover,
.ling-broadcast-history-toggle:hover {
  border-color: var(--color-star);
  background: color-mix(in srgb, var(--color-star) 13%, var(--color-void));
  transform: translateY(-1px);
}

.ling-broadcast-rank-toggle:focus-visible,
.ling-broadcast-history-toggle:focus-visible {
  outline: 2px solid var(--color-star);
  outline-offset: 2px;
}

.ling-broadcast-rankings {
  width: min(620px, calc(100vw - 32px));
  margin: 8px auto 14px;
  padding: 17px 20px 12px;
  border: 1px solid color-mix(in srgb, var(--color-star) 35%, var(--color-border));
  background:
    linear-gradient(145deg, color-mix(in srgb, var(--color-star) 5%, var(--color-void)), var(--color-void) 58%);
  box-shadow: 0 16px 40px rgb(0 0 0 / 28%);
  color: var(--color-star);
}

.ling-broadcast-history {
  width: min(720px, calc(100vw - 32px));
  max-height: min(62vh, 540px);
  overflow: auto;
  margin: 8px auto 14px;
  padding: 17px 20px 12px;
  border: 1px solid color-mix(in srgb, var(--color-star) 35%, var(--color-border));
  background:
    linear-gradient(145deg, color-mix(in srgb, var(--color-star) 5%, var(--color-void)), var(--color-void) 58%);
  box-shadow: 0 16px 40px rgb(0 0 0 / 28%);
  color: var(--color-star);
  scrollbar-color: color-mix(in srgb, var(--color-star) 35%, var(--color-border)) transparent;
}

.ling-broadcast-history-head,
.ling-broadcast-history-title-row,
.ling-broadcast-history-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ling-broadcast-history-head {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
}

.ling-broadcast-history-head h2,
.ling-broadcast-history-title-row h3 {
  margin: 0;
  font-family: var(--font-serif, "Songti SC", serif);
  font-weight: 500;
}

.ling-broadcast-history-head h2 { font-size: 17px; letter-spacing: 1px; }
.ling-broadcast-history-title-row h3 { font-size: 14px; letter-spacing: 0.5px; }

.ling-broadcast-history-list {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ling-broadcast-history-item {
  padding: 14px 2px 13px;
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 70%, transparent);
}

.ling-broadcast-history-item:last-child { border-bottom: 0; }

.ling-broadcast-history-status {
  flex: none;
  padding: 3px 7px;
  border: 1px solid var(--color-border);
  color: color-mix(in srgb, var(--color-star) 72%, transparent);
  font-size: 10px;
  letter-spacing: 0.4px;
}

.ling-broadcast-history-status.is-completed {
  border-color: color-mix(in srgb, var(--color-star) 48%, var(--color-border));
  color: var(--color-star);
}

.ling-broadcast-history-status.is-expired { color: #c4a78f; }

.ling-broadcast-history-description,
.ling-broadcast-history-muted {
  margin: 7px 0;
  color: color-mix(in srgb, var(--color-star) 68%, transparent);
  font-size: 11px;
  line-height: 1.65;
}

.ling-broadcast-history-meta {
  color: color-mix(in srgb, var(--color-star) 78%, transparent);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.ling-broadcast-history-effects {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  margin-top: 8px;
  color: color-mix(in srgb, var(--color-star) 72%, transparent);
  font-size: 10px;
}

.ling-broadcast-history-effects b {
  padding: 3px 6px;
  border-left: 1px solid color-mix(in srgb, var(--color-star) 50%, var(--color-border));
  color: var(--color-star);
  font-weight: 500;
}

.ling-broadcast-rankings-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
}

.ling-broadcast-kicker {
  margin: 0 0 4px;
  color: color-mix(in srgb, var(--color-star) 62%, transparent);
  font-size: 10px;
  letter-spacing: 1.8px;
}

.ling-broadcast-rankings h2 {
  margin: 0;
  font-family: var(--font-serif, "Songti SC", serif);
  font-size: 17px;
  font-weight: 500;
  letter-spacing: 1px;
}

.ling-broadcast-rankings-note {
  margin: 0 0 2px;
  color: color-mix(in srgb, var(--color-star) 64%, transparent);
  font-size: 11px;
  text-align: right;
}

.ling-broadcast-rankings-controls {
  display: flex;
  align-items: end;
  flex-direction: column;
  gap: 7px;
}

.ling-broadcast-event-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  color: color-mix(in srgb, var(--color-star) 68%, transparent);
  font-size: 10px;
  letter-spacing: 0.6px;
}

.ling-broadcast-event-picker select {
  max-width: 240px;
  padding: 5px 24px 5px 9px;
  border: 1px solid color-mix(in srgb, var(--color-star) 34%, var(--color-border));
  border-radius: 2px;
  background: color-mix(in srgb, var(--color-star) 6%, var(--color-void));
  color: var(--color-star);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.ling-broadcast-event-picker select:focus-visible {
  outline: 2px solid var(--color-star);
  outline-offset: 2px;
}

.ling-broadcast-event-picker option {
  background: var(--color-void);
  color: var(--color-star);
}

.ling-broadcast-state {
  margin: 0;
  padding: 22px 8px;
  color: color-mix(in srgb, var(--color-star) 75%, transparent);
  font-size: 12px;
  text-align: center;
}

.ling-broadcast-my-position {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 10px 0 4px;
  padding: 10px 12px;
  border-left: 2px solid var(--color-star);
  background: color-mix(in srgb, var(--color-star) 7%, var(--color-void));
}

.ling-broadcast-personal-state {
  width: 100%;
  margin: 0;
  color: color-mix(in srgb, var(--color-star) 75%, transparent);
  font-size: 11px;
}

.ling-broadcast-my-rank,
.ling-broadcast-my-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  color: color-mix(in srgb, var(--color-star) 70%, transparent);
  font-size: 11px;
}

.ling-broadcast-my-rank strong {
  color: var(--color-star);
  font-family: var(--font-serif, "Songti SC", serif);
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.ling-broadcast-my-progress {
  justify-content: end;
  flex-wrap: wrap;
  text-align: right;
}

.ling-broadcast-my-progress b {
  color: var(--color-star);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.ling-broadcast-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #df9a82;
}

.ling-broadcast-retry {
  flex: none;
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, #df9a82 50%, var(--color-border));
  border-radius: 2px;
  background: transparent;
  color: #df9a82;
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.ling-broadcast-retry:hover,
.ling-broadcast-retry:focus-visible {
  border-color: #df9a82;
  background: color-mix(in srgb, #df9a82 9%, transparent);
}

.ling-broadcast-retry:focus-visible {
  outline: 2px solid #df9a82;
  outline-offset: 2px;
}

.ling-broadcast-rank-list {
  display: grid;
  gap: 2px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.ling-broadcast-rank-row {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding: 4px 8px;
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 65%, transparent);
  color: color-mix(in srgb, var(--color-star) 78%, transparent);
  font-size: 12px;
}

.ling-broadcast-rank-row:last-child {
  border-bottom: 0;
}

.ling-broadcast-rank-row.is-podium {
  color: var(--color-star);
}

.ling-broadcast-rank-row.is-self {
  border-left: 2px solid var(--color-star);
  background: color-mix(in srgb, var(--color-star) 7%, var(--color-void));
}

.ling-broadcast-rank-number {
  color: color-mix(in srgb, var(--color-star) 58%, transparent);
  font-family: var(--font-serif, "Songti SC", serif);
  font-size: 13px;
  letter-spacing: 1px;
}

.ling-broadcast-rank-row:nth-child(1) .ling-broadcast-rank-number {
  color: var(--color-star);
}

.ling-broadcast-rank-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ling-broadcast-rank-name em {
  margin-left: 6px;
  padding: 1px 5px;
  border: 1px solid color-mix(in srgb, var(--color-star) 40%, var(--color-border));
  color: var(--color-star);
  font-size: 9px;
  font-style: normal;
  letter-spacing: 0.5px;
}

.ling-broadcast-rank-score {
  color: color-mix(in srgb, var(--color-star) 72%, transparent);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .ling-broadcast-strip {
    justify-content: space-between;
    gap: 8px;
    min-height: 34px;
    padding: 0 10px;
    font-size: 11px;
  }

  .ling-broadcast-rank-toggle {
    gap: 4px;
    padding: 4px 7px;
    font-size: 10px;
  }

  .ling-broadcast-history-toggle {
    gap: 4px;
    padding: 4px 7px;
    font-size: 10px;
  }

  .ling-broadcast-rankings {
    width: calc(100vw - 20px);
    padding: 14px 12px 9px;
  }

  .ling-broadcast-history {
    width: calc(100vw - 20px);
    max-height: 60vh;
    padding: 14px 12px 9px;
  }

  .ling-broadcast-history-head {
    align-items: start;
    flex-direction: column;
    gap: 7px;
  }

  .ling-broadcast-history-meta {
    align-items: start;
    flex-direction: column;
    gap: 4px;
  }

  .ling-broadcast-rankings-head {
    align-items: start;
    flex-direction: column;
    gap: 6px;
  }

  .ling-broadcast-rankings-controls {
    align-items: start;
    width: 100%;
  }

  .ling-broadcast-event-picker {
    width: 100%;
    justify-content: space-between;
  }

  .ling-broadcast-event-picker select {
    max-width: 70%;
  }

  .ling-broadcast-rankings-note {
    text-align: left;
  }

  .ling-broadcast-my-position {
    align-items: start;
    flex-direction: column;
    gap: 5px;
  }

  .ling-broadcast-my-progress {
    justify-content: start;
    text-align: left;
  }
}
</style>
