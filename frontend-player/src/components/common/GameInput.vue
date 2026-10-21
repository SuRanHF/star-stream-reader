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
        background: var(--color-input);
        color: var(--color-text);
      "
      :style="{
        '--focus-border': 'var(--color-star)',
        '--focus-glow': 'var(--color-system-glow-soft)',
      } as Record<string, string>"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      @focus="(e: FocusEvent) => {
        const el = e.target as HTMLInputElement;
        el.style.borderColor = 'var(--color-star)';
        el.style.boxShadow = '0 0 0 3px var(--color-focus-ring)';
        el.style.background = 'var(--color-surface)';
      }"
      @blur="(e: FocusEvent) => {
        const el = e.target as HTMLInputElement;
        el.style.borderColor = 'var(--color-border)';
        el.style.boxShadow = 'none';
        el.style.background = 'var(--color-input)';
      }"
    />
  </label>
</template>
