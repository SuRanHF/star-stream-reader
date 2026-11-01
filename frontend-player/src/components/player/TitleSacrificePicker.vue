<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { ApiRecord } from '@/types/api';
import { sacrificableTitles, sacrificeLossText, summarizeTitleEffects } from '@/utils/titleEffects';

// 献祭称号选择：只列可献祭的称号，写明实际会失去什么。选定后交给调用方做最终确认与请求。
const props = defineProps<{
  visible: boolean;
  titles: ApiRecord[];
  targetName: string;
}>();

const emit = defineEmits<{
  pick: [titleKey: string, titleName: string, lossText: string];
  close: [];
}>();

const options = computed(() => sacrificableTitles(props.titles).map((entry) => ({
  key: String(entry.titleKey || entry.title_key),
  name: String(entry.name || entry.titleName || entry.titleKey),
  loss: sacrificeLossText(summarizeTitleEffects(entry)),
})));
const selectedKey = ref('');
const selected = computed(() => options.value.find((option) => option.key === selectedKey.value));

watch(() => props.visible, (visible) => {
  if (visible) selectedKey.value = '';
});

function confirm() {
  if (!selected.value) return;
  emit('pick', selected.value.key, selected.value.name, selected.value.loss);
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="tsp-backdrop" @click.self="emit('close')" @keydown.esc="emit('close')">
      <div class="tsp-card" role="dialog" aria-modal="true" aria-labelledby="tspTitle">
        <h3 id="tspTitle" class="tsp-title">献上一个称号，援救 {{ targetName }}</h3>
        <p class="tsp-hint">被献祭的称号会<b>永久失去</b>，它带来的加成也随之消失。</p>
        <p v-if="!options.length" class="tsp-hint">你没有可以献上的称号。</p>
        <div v-else class="tsp-list" role="radiogroup" aria-label="选择要献祭的称号">
          <button
            v-for="option in options"
            :key="option.key"
            type="button"
            role="radio"
            :aria-checked="selectedKey === option.key"
            :class="['tsp-chip', { active: selectedKey === option.key }]"
            @click="selectedKey = option.key"
          >{{ option.name }}</button>
        </div>
        <p v-if="selected?.loss" class="tsp-loss">{{ selected.loss }}</p>
        <div class="tsp-actions">
          <button type="button" class="tsp-btn" @click="emit('close')">取消</button>
          <button type="button" class="tsp-btn tsp-btn--danger" :disabled="!selected" @click="confirm">
            {{ selected ? `献祭「${selected.name}」` : '先选择一个称号' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.tsp-backdrop {
  position: fixed;
  inset: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-overlay);
}

.tsp-card {
  width: calc(100% - 32px);
  max-width: 420px;
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-panel-elevated, var(--color-panel));
}

.tsp-title {
  margin: 0 0 6px;
  color: var(--color-text);
  font-size: 16px;
}

.tsp-hint {
  margin: 0 0 8px;
  color: var(--color-muted);
  font-size: 12px;
}

.tsp-hint b,
.tsp-loss {
  color: var(--color-danger-bright, var(--color-danger));
}

.tsp-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0;
}

.tsp-chip {
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: transparent;
  color: var(--color-text-dim);
  font-size: 13px;
  cursor: pointer;
}

.tsp-chip.active {
  border-color: var(--color-star);
  color: var(--color-star);
}

.tsp-loss {
  margin: 0 0 10px;
  font-size: 12px;
}

.tsp-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.tsp-btn {
  padding: 8px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-dim);
  cursor: pointer;
}

.tsp-btn--danger:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger-bright, var(--color-danger));
}

.tsp-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
