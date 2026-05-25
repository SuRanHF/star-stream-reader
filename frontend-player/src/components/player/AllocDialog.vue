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
    <div v-if="visible" class="ad-backdrop" @click.self="close">
      <div class="ad-card">
        <h3 class="ad-title">属性加点</h3>

        <div class="ad-free-points">
          自由属性点: <b class="gold">{{ freePoints }}</b>
        </div>

        <div class="ad-rows">
          <div v-for="stat in statKeys" :key="stat" class="ad-row">
            <span class="ad-stat-label">{{ STAT_LABELS[stat] }}</span>
            <div class="ad-stepper">
              <button
                class="ad-step-btn"
                :disabled="alloc[stat] <= 0 || submitting"
                @click="sub(stat)"
              >−</button>
              <input
                type="number"
                class="ad-input"
                :value="alloc[stat]"
                min="0"
                :max="freePoints"
                :disabled="submitting"
                @input="onInputChange(stat, ($event.target as HTMLInputElement).value)"
              />
              <button
                class="ad-step-btn"
                :disabled="totalAlloc >= freePoints || submitting"
                @click="add(stat)"
              >+</button>
            </div>
          </div>
        </div>

        <div class="ad-summary">
          <span>已分配: <b class="gold">{{ num(props.stats.allocatedAtk) + num(props.stats.allocatedDef) + num(props.stats.allocatedSpd) + num(props.stats.allocatedCrit) }}</b></span>
          <span v-if="totalAlloc > 0">本次: <b class="green">+{{ totalAlloc }}</b></span>
          <button
            class="ad-reset-btn"
            :disabled="submitting || resetCost <= 0"
            @click="resetAlloc"
          >重置 ({{ resetCost }}币)</button>
        </div>

        <div class="ad-actions">
          <button class="ad-btn ad-btn-cancel" :disabled="submitting" @click="close">取消</button>
          <button class="ad-btn ad-btn-ok" :disabled="!canSubmit" @click="submit">
            {{ submitting ? '分配中...' : `确认加点 (+${totalAlloc})` }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ad-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: adFadeIn 0.15s ease;
}
@keyframes adFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.ad-card {
  background: #1a1e2b;
  border: 1px solid #2e3345;
  border-radius: 14px;
  padding: 28px 32px;
  min-width: 340px;
  max-width: 400px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
  animation: adSlideIn 0.25s ease;
}
@keyframes adSlideIn {
  from { transform: translateY(16px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
.ad-title {
  color: #e2e8f0;
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 16px;
}
.ad-free-points {
  font-size: 14px;
  color: #94a3b8;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: rgba(15, 23, 42, 0.5);
  border-radius: 8px;
  border: 1px solid #2e3345;
}
.gold { color: #caa86a; }
.green { color: #22c55e; }

.ad-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}
.ad-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ad-stat-label {
  font-size: 14px;
  color: #cbd5e1;
  min-width: 40px;
}
.ad-stepper {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid #334155;
  border-radius: 8px;
  overflow: hidden;
}
.ad-step-btn {
  width: 34px;
  height: 34px;
  border: none;
  background: #1e293b;
  color: #cbd5e1;
  font-size: 18px;
  cursor: pointer;
  transition: background 0.15s;
  flex-shrink: 0;
}
.ad-step-btn:hover:not(:disabled) {
  background: #334155;
}
.ad-step-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.ad-input {
  width: 72px;
  height: 34px;
  text-align: center;
  font-size: 16px;
  font-weight: 700;
  color: #e2e8f0;
  background: #0f172a;
  border: none;
  border-left: 1px solid #334155;
  border-right: 1px solid #334155;
  outline: none;
  padding: 0 4px;
  -moz-appearance: textfield;
}
.ad-input::-webkit-inner-spin-button,
.ad-input::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.ad-input:disabled {
  opacity: 0.5;
}
.ad-input:focus {
  background: #1a1f33;
  box-shadow: inset 0 0 0 1px #3b82f6;
}

.ad-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #6b7b88;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.ad-reset-btn {
  height: 24px;
  padding: 0 10px;
  border: 1px solid #334155;
  border-radius: 6px;
  background: #1e293b;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s;
  margin-left: auto;
}
.ad-reset-btn:hover:not(:disabled) {
  background: #334155;
}
.ad-reset-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.ad-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.ad-btn {
  padding: 9px 24px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
}
.ad-btn-cancel {
  background: #2e3345;
  color: #94a3b8;
}
.ad-btn-cancel:hover:not(:disabled) {
  background: #3d4258;
}
.ad-btn-ok {
  background: #3b82f6;
  color: #fff;
  font-weight: 600;
}
.ad-btn-ok:hover:not(:disabled) {
  background: #2563eb;
}
.ad-btn-ok:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
