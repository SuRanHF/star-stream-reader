<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import type { LogEntry } from '@/types/bootstrap';

const props = defineProps<{
  logs: LogEntry[];
}>();

const containerRef = ref<HTMLElement>();

watch(() => props.logs.length, () => {
  nextTick(() => {
    if (containerRef.value) {
      containerRef.value.scrollTop = containerRef.value.scrollHeight;
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
  income: '收益',
  reward: '奖励',
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

function lineClass(type?: string) {
  if (type === 'battle' || type === 'combat') return 'is-danger';
  if (type === 'income' || type === 'reward') return 'is-income';
  if (type === 'admin') return 'is-admin';
  return 'is-normal';
}
</script>

<style scoped>
.log-panel {
  background: linear-gradient(
    175deg,
    rgba(7, 11, 26, 0.4) 0%,
    rgba(10, 15, 36, 0.6) 100%
  );
}

/* 新日志淡入 */
.ling-log-line {
  animation: logFadeIn 0.35s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes logFadeIn {
  from {
    opacity: 0;
    transform: translateX(-8px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* 类型标签增强 */
.ling-log-type {
  display: inline-block;
  min-width: 50px;
  padding: 1px 6px;
  margin-right: 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
  text-align: center;
  background: rgba(7, 11, 26, 0.5);
  border: 1px solid rgba(74, 143, 231, 0.2);
  color: var(--color-system-bright);
}

/* 战斗类型 */
.is-danger .ling-log-type {
  border-color: rgba(224, 85, 106, 0.35);
  color: #f09098;
  box-shadow: 0 0 6px rgba(224, 85, 106, 0.08);
}

/* 收益类型 */
.is-income .ling-log-type {
  border-color: rgba(94, 196, 158, 0.3);
  color: #80d4a8;
  box-shadow: 0 0 6px rgba(94, 196, 158, 0.08);
}

/* 管理类型 */
.is-admin .ling-log-type {
  border-color: rgba(160, 128, 224, 0.3);
  color: #c0b0e8;
}

/* 空状态 */
.is-normal:only-child {
  text-align: center;
  color: var(--color-muted);
  font-style: italic;
  border-left-color: transparent;
  padding: 32px 16px;
}
</style>

<template>
  <section ref="containerRef" class="ling-log log-panel">
    <p v-if="logs.length === 0" class="ling-log-line is-normal">暂无日志。</p>
    <p
      v-for="(log, idx) in logs"
      :key="log.id || log.createdAt || log.message"
      :class="['ling-log-line', lineClass(log.type)]"
      :style="{ animationDelay: `${Math.min(idx * 30, 300)}ms` }"
    >
      <span class="ling-log-type">[{{ typeLabel(log.type) }}]</span>
      {{ log.message || '未命名事件' }}
    </p>
  </section>
</template>
