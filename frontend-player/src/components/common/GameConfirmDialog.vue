<script setup lang="ts">
import { useUiStore } from '@/stores/uiStore';

const ui = useUiStore();
</script>

<template>
  <Teleport to="body">
    <div
      v-if="ui.dialog.visible"
      class="ling-dialog-backdrop"
      @click.self="ui.closeDialog(false)"
    >
      <div class="ling-dialog-box">
        <h3 class="ling-dialog-title">{{ ui.dialog.title }}</h3>
        <p v-if="ui.dialog.message" class="ling-dialog-msg">{{ ui.dialog.message }}</p>
        <ul v-if="ui.dialog.details?.length" class="ling-dialog-details">
          <li
            v-for="(d, i) in ui.dialog.details"
            :key="i"
            :class="d.completed ? 'ling-detail-done' : 'ling-detail-pending'"
          >
            <span class="ling-detail-check">{{ d.completed ? '✓' : '✗' }}</span>
            <span class="ling-detail-label">{{ d.label }}</span>
            <span class="ling-detail-progress">{{ d.current }}/{{ d.required }}</span>
          </li>
        </ul>
        <div class="ling-dialog-actions">
          <button
            v-if="ui.dialog.type === 'confirm'"
            class="ling-dialog-btn ling-dialog-btn-cancel"
            @click="ui.closeDialog(false)"
          >取消</button>
          <button
            class="ling-dialog-btn ling-dialog-btn-ok"
            @click="ui.closeDialog(true)"
          >{{ ui.dialog.type === 'confirm' ? '确定' : '知道了' }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ling-dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: 999;
  background: rgba(4, 8, 18, 0.7);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: lingDialogFadeIn 0.2s ease;
}
@keyframes lingDialogFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.ling-dialog-box {
  background: linear-gradient(180deg, rgba(13, 20, 48, 0.97), rgba(7, 11, 26, 0.98));
  border: 1px solid var(--color-system-border);
  border-radius: var(--radius-lg);
  padding: 26px 30px;
  min-width: 320px;
  max-width: 420px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(74, 143, 231, 0.08);
  animation: lingDialogSlideIn 0.25s var(--ease-out-expo);
}
@keyframes lingDialogSlideIn {
  from { transform: translateY(16px) scale(0.96); opacity: 0; }
  to { transform: translateY(0) scale(1); opacity: 1; }
}
.ling-dialog-title {
  color: var(--color-system-bright);
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 12px;
  text-shadow: 0 0 8px rgba(74, 143, 231, 0.2);
}
.ling-dialog-msg {
  color: var(--color-text-dim);
  font-size: 13px;
  line-height: 1.65;
  margin: 0 0 22px;
}
.ling-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.ling-dialog-btn {
  padding: 9px 24px;
  border-radius: var(--radius-md);
  font-size: 13px;
  cursor: pointer;
  border: 1px solid;
  transition: all var(--duration-fast);
  font-weight: 500;
}
.ling-dialog-btn-cancel {
  background: transparent;
  border-color: var(--color-border);
  color: var(--color-muted);
}
.ling-dialog-btn-cancel:hover {
  border-color: var(--color-border-bright);
  color: var(--color-text-dim);
  background: rgba(255,255,255,0.02);
}
.ling-dialog-btn-ok {
  background: rgba(74, 143, 231, 0.1);
  border-color: var(--color-system-border);
  color: var(--color-system-bright);
}
.ling-dialog-btn-ok:hover {
  background: rgba(74, 143, 231, 0.2);
  border-color: var(--color-system);
  box-shadow: var(--glow-system);
}
.ling-dialog-details {
  list-style: none;
  padding: 12px 14px;
  margin: 0 0 18px;
  background: rgba(7, 11, 26, 0.4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}
.ling-dialog-details li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 0;
  font-size: 12px;
  border-bottom: 1px solid rgba(26, 38, 80, 0.4);
}
.ling-dialog-details li:last-child { border-bottom: none; }
.ling-detail-check { width: 18px; font-weight: 700; flex-shrink: 0; }
.ling-detail-done .ling-detail-check { color: var(--color-spirit); }
.ling-detail-pending .ling-detail-check { color: var(--color-danger); }
.ling-detail-label { flex: 1; color: var(--color-text); }
.ling-detail-done .ling-detail-label { color: var(--color-text-dim); }
.ling-detail-progress { color: var(--color-muted); font-variant-numeric: tabular-nums; }
</style>
