<script setup lang="ts">
import { computed } from 'vue';
import { cva } from 'class-variance-authority';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'ghost' | 'danger';
    type?: 'button' | 'submit' | 'reset';
    disabled?: boolean;
  }>(),
  {
    variant: 'primary',
    type: 'button',
    disabled: false,
  },
);

const buttonClass = computed(() => {
  const variants = cva(
    'inline-flex items-center justify-center gap-2 rounded border px-4 py-2 text-sm transition disabled:cursor-not-allowed disabled:opacity-50',
    {
      variants: {
        variant: {
          primary: 'border-star/60 bg-star/10 text-star hover:bg-star/20',
          ghost: 'border-line bg-panel text-muted hover:border-spirit/60 hover:text-spirit',
          danger: 'border-danger/60 bg-danger/10 text-danger hover:bg-danger/20',
        },
      },
    },
  );
  return twMerge(clsx(variants({ variant: props.variant })));
});
</script>

<template>
  <button :type="type" :disabled="disabled" :class="buttonClass">
    <slot />
  </button>
</template>
