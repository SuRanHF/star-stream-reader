<script setup lang="ts">
const props = defineProps<{
  label: string;
  value?: number;
  max?: number;
  tone?: 'hp' | 'stamina' | 'exp';
}>();

const percent = () => {
  if (!props.max || props.max <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round(((props.value || 0) / props.max) * 100)));
};
</script>

<template>
  <div>
    <div class="mb-1 flex items-center justify-between text-xs">
      <span class="text-muted">{{ label }}</span>
      <span class="text-spirit">{{ value || 0 }} / {{ max || 0 }}</span>
    </div>
    <div class="h-2 overflow-hidden rounded bg-[#0a0e11] ring-1 ring-line">
      <div
        class="h-full rounded"
        :class="{
          'bg-danger': tone === 'hp',
          'bg-spirit': tone === 'stamina',
          'bg-star': tone === 'exp' || !tone,
        }"
        :style="{ width: `${percent()}%` }"
      />
    </div>
  </div>
</template>
