<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue';
import type { LogEntry } from '@/types/bootstrap';
import { formatPersistedLogDetails } from '@/utils/gameLog';

const props = defineProps<{
  logs: LogEntry[];
  inventory?: Record<string, unknown>[];
  titles?: Record<string, unknown>[];
}>();

const emit = defineEmits<{
  'select-tab': [panel: string];
}>();

const containerRef = ref<HTMLElement>();
const followLatest = ref(true);
const unreadLogCount = ref(0);
let previousLogKeys = new Set<string>();
let hasRenderedInitialLogs = false;
const renderedLogs = computed(() => props.logs.flatMap((entry, index) => {
  const baseKey = entry.id ?? `${entry.createdAt ?? entry.timestamp ?? 'log'}-${index}`;
  const details = formatPersistedLogDetails(entry, props.inventory || [], props.titles || []);
  return [
    { entry, key: `${baseKey}-summary`, isDetail: false },
    ...details.map((detail, detailIndex) => ({
      entry: detail,
      key: `${baseKey}-detail-${detailIndex}`,
      isDetail: true,
    })),
  ];
}));

function scrollToLatest() {
  if (containerRef.value) containerRef.value.scrollTop = containerRef.value.scrollHeight;
}

function handleLogScroll() {
  const container = containerRef.value;
  if (!container) return;
  const distanceFromBottom = container.scrollHeight - container.clientHeight - container.scrollTop;
  followLatest.value = distanceFromBottom <= 24;
  if (followLatest.value) unreadLogCount.value = 0;
}

function jumpToLatest() {
  followLatest.value = true;
  unreadLogCount.value = 0;
  nextTick(scrollToLatest);
}

watch(renderedLogs, (rows) => {
  const addedCount = hasRenderedInitialLogs
    ? rows.reduce((count, row) => count + (previousLogKeys.has(row.key) ? 0 : 1), 0)
    : 0;
  previousLogKeys = new Set(rows.map((row) => row.key));
  hasRenderedInitialLogs = true;

  nextTick(() => {
    if (!containerRef.value) return;
    if (followLatest.value) {
      scrollToLatest();
      unreadLogCount.value = 0;
    } else if (addedCount > 0) {
      unreadLogCount.value += addedCount;
    }
  });
}, { immediate: true });

const TYPE_MAP: Record<string, string> = {
  info: '信息',
  exploration: '探索',
  story: '故事',
  friend: '同伴',
  battle: '战斗',
  combat: '战斗',
  pk: 'PK',
  explore: '探索',
  trade: '交易',
  trade_list: '上架',
  trade_sold: '售出',
  trade_cancel: '取消',
  trade_bought: '购买',
  party_create: '创建队伍',
  party_join: '加入队伍',
  party_leave: '离开队伍',
  party_kicked: '踢出队伍',
  party_leader: '转让队长',
  party_disband: '解散队伍',
  party_battle: '队伍战斗',
  party_quest: '队伍任务',
  equipment: '装备',
  equipment_acquire: '获得装备',
  equipment_equip: '穿戴',
  equipment_unequip: '卸下',
  equipment_repair: '修理',
  equipment_enhance: '强化',
  faction: '阵营',
  faction_join: '加入阵营',
  faction_leave: '离开阵营',
  faction_contribute: '阵营贡献',
  faction_war: '阵营战',
  skill: '技能',
  skill_learn: '学习技能',
  skill_unlock: '技能解锁',
  title_earn: '获得称号',
  title_equip: '装备称号',
  title_unequip: '卸下称号',
  rest_start: '开始待机',
  rest_stop: '结束行动',
  revive: '复活',
  peer_revive: '援救复活',
  constellation_change: '背后星更换',
  bounty_publish: '发布悬赏',
  bounty_accept: '接取悬赏',
  bounty_complete: '完成悬赏',
  achievement: '成就',
  milestone: '成长',
  income: '收益',
  reward: '奖励',
  cost: '消耗',
  loss: '损失',
  quest: '任务',
  worldBoss: '灾厄',
  world_boss_event: '灾厄事件',
  world_boss_defeated: '灾厄击败',
  world_boss_claim: '灾厄领取',
  system: '系统',
  admin: '管理',
  death: '死亡',
};

function typeLabel(type?: string): string {
  if (!type) return '消息';
  return TYPE_MAP[type] || type;
}

function logTime(log: LogEntry): string {
  const value = log.createdAt || log.timestamp;
  if (!value) return '';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '';
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(date);
}

function lineClass(type?: string) {
  if (type === 'battle' || type === 'combat' || type === 'loss' || type === 'cost') return 'is-danger';
  if (type === 'income' || type === 'reward') return 'is-income';
  if (type === 'admin') return 'is-admin';
  return 'is-normal';
}
</script>

<style scoped>
.log-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  background: var(--color-void);
  border-right: 1px solid var(--color-border);
}

.log-panel::before {
  content: '';
  position: absolute;
  z-index: 1;
  top: 20px;
  bottom: 24px;
  left: 20px;
  width: 2px;
  background: var(--color-star);
  pointer-events: none;
}

.log-mid-tabs {
  display: flex;
  flex: 0 0 auto;
  min-height: 34px;
  border-bottom: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-panel) 92%, transparent);
}

.log-mid-tab {
  position: relative;
  flex: 1;
  min-height: 34px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--color-muted);
  font-size: 12px;
  letter-spacing: 1px;
  cursor: pointer;
}

.log-mid-tab.active {
  color: var(--color-star);
  font-weight: 600;
  background: var(--color-card);
}

.log-mid-tab.active::after {
  content: '';
  position: absolute;
  left: 18%;
  right: 18%;
  bottom: -1px;
  height: 2px;
  border-radius: 2px 2px 0 0;
  background: var(--color-star);
}

.ling-log-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 22px 28px 34px;
}

.ling-log-line {
  min-height: 0;
  margin: 0;
  padding: 5px 0;
  border: 0;
  background: transparent !important;
  color: var(--color-text-dim);
  font-size: 13px;
  line-height: 1.65;
  animation: none !important;
}

.ling-log-line.is-log-detail {
  padding-left: 18px;
  color: var(--color-text);
  font-size: 12px;
}

.log-new-entries {
  position: absolute;
  z-index: 2;
  right: 16px;
  bottom: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border: 1px solid var(--color-star-border);
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-panel) 94%, transparent);
  color: var(--color-star-bright);
  box-shadow: 0 4px 14px rgb(0 0 0 / 24%);
  font-size: 11px;
  cursor: pointer;
  backdrop-filter: blur(8px);
}

.log-new-entries:hover {
  background: var(--color-star-bg);
  transform: translateY(-1px);
}

.log-new-entries:focus-visible {
  outline: 2px solid var(--color-star-bright);
  outline-offset: 2px;
}

.ling-log-type {
  display: inline;
  min-width: 0;
  margin-right: 8px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-system-bright);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0;
}

.ling-log-time {
  margin-right: 6px;
  color: var(--color-muted);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* 战斗类型 */
.is-danger .ling-log-type {
  color: var(--color-danger-bright);
}

/* 收益类型 */
.is-income .ling-log-type {
  color: var(--color-spirit-bright);
}

/* 管理类型 */
.is-admin .ling-log-type {
  color: var(--color-purple);
}

/* 空状态 */
.is-normal:only-child {
  text-align: left;
  color: var(--color-muted);
  font-style: normal;
  padding: 8px 0;
}

@media (min-width: 1025px) {
  .log-mid-tabs {
    display: none;
  }
}

@media (max-width: 760px) {
  .log-panel::before {
    top: 14px;
    bottom: 14px;
    left: 14px;
    width: 1px;
  }

  .ling-log-scroll {
    padding: 14px 12px 18px 26px;
  }

  .log-new-entries {
    right: 10px;
    bottom: 10px;
  }
}
</style>

<template>
  <section class="ling-log log-panel">
    <div class="log-mid-tabs" role="tablist" aria-label="游戏日志与星流频道">
      <button type="button" class="log-mid-tab active" role="tab" aria-selected="true">游戏日志</button>
      <button type="button" class="log-mid-tab" role="tab" aria-selected="false" @click="emit('select-tab', 'chat')">星流频道</button>
    </div>
    <div ref="containerRef" class="ling-log-scroll" @scroll.passive="handleLogScroll">
      <p v-if="renderedLogs.length === 0" class="ling-log-line is-normal">暂无日志。</p>
      <p
        v-for="(row, idx) in renderedLogs"
        :key="row.key"
        :class="['ling-log-line', lineClass(row.entry.type), { 'is-log-detail': row.isDetail }]"
        :style="{ animationDelay: `${Math.min(idx * 30, 300)}ms` }"
      >
        <time
          v-if="!row.isDetail && logTime(row.entry)"
          class="ling-log-time"
          :datetime="row.entry.createdAt || row.entry.timestamp"
        >[{{ logTime(row.entry) }}]</time>
        <span class="ling-log-type">[{{ typeLabel(row.entry.type) }}]</span>
        {{ row.entry.message || '未命名事件' }}
      </p>
    </div>
    <button
      v-if="unreadLogCount > 0"
      type="button"
      class="log-new-entries"
      aria-live="polite"
      :aria-label="`有 ${unreadLogCount} 条新日志，跳转到最新记录`"
      @click="jumpToLatest"
    >
      <span aria-hidden="true">↓</span>
      {{ unreadLogCount }} 条新日志
    </button>
  </section>
</template>
