<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { playerApi } from '@/api/playerApi';
import { useUiStore } from '@/stores/uiStore';
import type { PlayerStats } from '@/types/player';

const ui = useUiStore();

const props = defineProps<{
  visible: boolean;
  playerId: number;
  stats: PlayerStats;
}>();

const emit = defineEmits<{
  close: [];
  updated: [data: unknown];
}>();

const submitting = ref(false);

const alloc = ref({ atk: 0, def: 0, spd: 0, crit: 0 });

watch(() => props.visible, (v) => {
  if (v) {
    alloc.value = { atk: 0, def: 0, spd: 0, crit: 0 };
  }
});

function num(v: unknown): number {
  return typeof v === 'number' ? v : Number(v || 0);
}

const freePoints = computed(() => num(props.stats.freePoints));
const totalAlloc = computed(() => alloc.value.atk + alloc.value.def + alloc.value.spd + alloc.value.crit);
const canSubmit = computed(() => totalAlloc.value > 0 && !submitting.value);

const resetCost = computed(() => {
  const s = props.stats;
  const total = num(s.allocatedAtk) + num(s.allocatedDef) + num(s.allocatedSpd) + num(s.allocatedCrit);
  return Math.max(50, total * 20);
});

function clampStat(stat: 'atk' | 'def' | 'spd' | 'crit', v: number): number {
  if (!Number.isFinite(v) || v < 0) v = 0;
  const others = totalAlloc.value - alloc.value[stat];
  const maxForThis = freePoints.value - others;
  if (v > maxForThis) v = maxForThis;
  return v;
}

function onInputChange(stat: 'atk' | 'def' | 'spd' | 'crit', raw: string) {
  alloc.value[stat] = clampStat(stat, parseInt(raw, 10));
}

function add(stat: 'atk' | 'def' | 'spd' | 'crit') {
  if (totalAlloc.value >= freePoints.value) return;
  alloc.value[stat]++;
}

function sub(stat: 'atk' | 'def' | 'spd' | 'crit') {
  if (alloc.value[stat] <= 0) return;
  alloc.value[stat]--;
}

function close() {
  if (!submitting.value) emit('close');
}

async function submit() {
  if (!canSubmit.value) return;
  submitting.value = true;
  try {
    const updated = await playerApi.allocatePoints(
      props.playerId,
      alloc.value.atk,
      alloc.value.def,
      alloc.value.spd,
      alloc.value.crit,
    );
    emit('updated', updated);
    emit('close');
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '分配失败';
    await ui.showAlert('属性分配', msg);
  } finally {
    submitting.value = false;
  }
}

async function resetAlloc() {
  const cost = resetCost.value;
  const ok = await ui.showConfirm('重置属性', `重置全部属性分配需要消耗 ${cost} 星币，确定继续？`);
  if (!ok) return;
  submitting.value = true;
  try {
    const updated = await playerApi.resetAllocation(props.playerId);
    emit('updated', updated);
    emit('close');
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '重置失败';
    await ui.showAlert('属性分配', msg);
  } finally {
    submitting.value = false;
  }
}

const STAT_LABELS: Record<string, string> = {
  atk: '攻击',
  def: '防御',
  spd: '速度',
  crit: '暴击',
};

const statKeys = ['atk', 'def', 'spd', 'crit'] as const;
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="ap-backdrop" @click.self="close">
      <div class="ap-card">
        <h3 class="ap-title">属性加点</h3>

        <div class="ap-free-points">
          自由属性点: <b class="gold">{{ freePoints }}</b>
        </div>

        <div class="ap-rows">
          <div v-for="stat in statKeys" :key="stat" class="ap-row">
            <span class="ap-stat-label">{{ STAT_LABELS[stat] }}</span>
            <div class="ap-stepper">
              <button
                class="ap-step-btn"
                :disabled="alloc[stat] <= 0 || submitting"
                @click="sub(stat)"
              >−</button>
              <input
                type="number"
                class="ap-input"
                :value="alloc[stat]"
                min="0"
                :max="freePoints"
                :disabled="submitting"
                @input="onInputChange(stat, ($event.target as HTMLInputElement).value)"
              />
              <button
                class="ap-step-btn"
                :disabled="totalAlloc >= freePoints || submitting"
                @click="add(stat)"
              >+</button>
            </div>
          </div>
        </div>

        <div class="ap-summary">
          <span>已分配: <b class="gold">{{ num(props.stats.allocatedAtk) + num(props.stats.allocatedDef) + num(props.stats.allocatedSpd) + num(props.stats.allocatedCrit) }}</b></span>
          <span v-if="totalAlloc > 0">本次: <b class="green">+{{ totalAlloc }}</b></span>
          <button
            class="ap-reset-btn"
            :disabled="submitting || resetCost <= 0"
            @click="resetAlloc"
          >重置 ({{ resetCost }}币)</button>
        </div>

        <div class="ap-actions">
          <button class="ap-btn ap-btn-cancel" :disabled="submitting" @click="close">取消</button>
          <button class="ap-btn ap-btn-ok" :disabled="!canSubmit" @click="submit">
            {{ submitting ? '分配中...' : `确认加点 (+${totalAlloc})` }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ap-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(4, 8, 18, 0.7);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: apFadeIn 0.2s ease;
}
@keyframes apFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.ap-card {
  background: linear-gradient(180deg, rgba(13, 20, 48, 0.97), rgba(7, 11, 26, 0.98));
  border: 1px solid var(--color-system-border);
  border-radius: var(--radius-lg);
  padding: 28px 32px;
  min-width: 340px;
  max-width: 400px;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(74, 143, 231, 0.06);
  animation: apSlideIn 0.3s var(--ease-out-expo);
}
@keyframes apSlideIn {
  from { transform: translateY(16px) scale(0.96); opacity: 0; }
  to { transform: translateY(0) scale(1); opacity: 1; }
}
.ap-title {
  color: var(--color-system-bright);
  font-size: 17px;
  font-weight: 700;
  margin: 0 0 16px;
  text-shadow: 0 0 8px rgba(74, 143, 231, 0.2);
}
.ap-free-points {
  font-size: 13px;
  color: var(--color-text-dim);
  margin-bottom: 16px;
  padding: 10px 14px;
  background: rgba(7, 11, 26, 0.4);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
}
.gold { color: var(--color-star); }
.green { color: var(--color-spirit); }

.ap-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}
.ap-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ap-stat-label {
  font-size: 13px;
  color: #b0bcc8;
  min-width: 40px;
}
.ap-stepper {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
}
.ap-step-btn {
  width: 34px;
  height: 34px;
  border: none;
  background: rgba(13, 20, 48, 0.7);
  color: #a0b0c8;
  font-size: 18px;
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}
.ap-step-btn:hover:not(:disabled) {
  background: rgba(26, 38, 80, 0.6);
}
.ap-step-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.ap-input {
  width: 72px;
  height: 34px;
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  color: #d0d8f0;
  background: rgba(7, 11, 26, 0.6);
  border: none;
  border-left: 1px solid var(--color-border);
  border-right: 1px solid var(--color-border);
  outline: none;
  padding: 0 4px;
  -moz-appearance: textfield;
}
.ap-input::-webkit-inner-spin-button,
.ap-input::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.ap-input:disabled { opacity: 0.5; }
.ap-input:focus {
  background: rgba(13, 20, 48, 0.8);
  box-shadow: inset 0 0 0 1px var(--color-system);
}

.ap-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--color-muted);
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.ap-reset-btn {
  height: 24px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 5px;
  background: rgba(13, 20, 48, 0.6);
  color: var(--color-text-dim);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
  margin-left: auto;
}
.ap-reset-btn:hover:not(:disabled) {
  background: rgba(26, 38, 80, 0.5);
  border-color: var(--color-border-bright);
}
.ap-reset-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.ap-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.ap-btn {
  padding: 9px 24px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
}
.ap-btn-cancel {
  background: rgba(26, 38, 80, 0.4);
  color: var(--color-text-dim);
}
.ap-btn-cancel:hover:not(:disabled) {
  background: rgba(26, 38, 80, 0.6);
}
.ap-btn-ok {
  background: rgba(74, 143, 231, 0.12);
  border: 1px solid var(--color-system-border);
  color: var(--color-system-bright);
  font-weight: 600;
}
.ap-btn-ok:hover:not(:disabled) {
  background: rgba(74, 143, 231, 0.22);
  border-color: var(--color-system);
  box-shadow: var(--glow-system);
}
.ap-btn-ok:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
