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
    <span v-if="label" class="mb-2 block text-xs text-muted">{{ label }}</span>
    <input
      :value="modelValue"
      :type="type || 'text'"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      class="w-full rounded border border-line bg-[#0c1115] px-3 py-2 text-sm text-text outline-none transition placeholder:text-muted/60 focus:border-star"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
  </label>
</template>
