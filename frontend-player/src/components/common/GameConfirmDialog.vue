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
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: lingDialogFadeIn 0.15s ease;
}
@keyframes lingDialogFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.ling-dialog-box {
  background: #1a1e2b;
  border: 1px solid #2e3345;
  border-radius: 12px;
  padding: 24px 28px;
  min-width: 320px;
  max-width: 420px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
  animation: lingDialogSlideIn 0.2s ease;
}
@keyframes lingDialogSlideIn {
  from { transform: translateY(12px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
.ling-dialog-title {
  color: #e2e8f0;
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 10px;
}
.ling-dialog-msg {
  color: #94a3b8;
  font-size: 14px;
  line-height: 1.6;
  margin: 0 0 22px;
}
.ling-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.ling-dialog-btn {
  padding: 8px 22px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.15s;
}
.ling-dialog-btn-cancel {
  background: #2e3345;
  color: #94a3b8;
}
.ling-dialog-btn-cancel:hover {
  background: #3d4258;
}
.ling-dialog-btn-ok {
  background: #3b82f6;
  color: #fff;
}
.ling-dialog-btn-ok:hover {
  background: #2563eb;
}
.ling-dialog-details {
  list-style: none;
  padding: 0;
  margin: 0 0 18px;
}
.ling-dialog-details li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}
.ling-detail-check {
  width: 16px;
  font-weight: 700;
  flex-shrink: 0;
}
.ling-detail-done .ling-detail-check {
  color: #22c55e;
}
.ling-detail-pending .ling-detail-check {
  color: #ef4444;
}
.ling-detail-label {
  flex: 1;
  color: #cbd5e1;
}
.ling-detail-done .ling-detail-label {
  color: #94a3b8;
}
.ling-detail-progress {
  color: #64748b;
  font-variant-numeric: tabular-nums;
}
</style>
