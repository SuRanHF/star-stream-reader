<script setup lang="ts">
defineProps<{
  modelValue: string;
  label?: string;
  type?: string;
  placeholder?: string;
  autocomplete?: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();
</script>

<template>
  <label class="block">
    <span v-if="label" class="mb-1.5 block text-xs tracking-wide" style="color: var(--color-text-dim)">{{ label }}</span>
    <input
      :value="modelValue"
      :type="type || 'text'"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      class="w-full rounded-md border px-3.5 py-2.5 text-sm outline-none transition-all duration-200 placeholder:text-muted/50"
      style="
        border-color: var(--color-border);
        background: rgba(6, 10, 14, 0.7);
        color: var(--color-text);
      "
      :style="{
        '--focus-border': 'var(--color-star)',
        '--focus-glow': 'rgba(74, 143, 231, 0.15)',
      } as Record<string, string>"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      @focus="(e: FocusEvent) => {
        const el = e.target as HTMLInputElement;
        el.style.borderColor = 'var(--color-star)';
        el.style.boxShadow = '0 0 0 3px rgba(74, 143, 231, 0.1)';
        el.style.background = 'rgba(8, 14, 18, 0.9)';
      }"
      @blur="(e: FocusEvent) => {
        const el = e.target as HTMLInputElement;
        el.style.borderColor = 'var(--color-border)';
        el.style.boxShadow = 'none';
        el.style.background = 'rgba(6, 10, 14, 0.7)';
      }"
    />
  </label>
</template>
