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
    'inline-flex items-center justify-center gap-2 rounded-md border px-5 py-2.5 text-sm font-medium transition-all duration-200 disabled:cursor-not-allowed disabled:opacity-40 relative overflow-hidden',
    {
      variants: {
        variant: {
          primary:
            'border-blue-400/40 bg-blue-500/8 text-blue-300 hover:bg-blue-500/15 hover:border-blue-400/60 hover:shadow-[0_0_16px_rgba(74,143,231,0.25)] active:scale-[0.98]',
          ghost:
            'border-line bg-panel/80 text-muted hover:border-spirit/50 hover:text-spirit hover:bg-spirit/5 active:scale-[0.98]',
          danger:
            'border-danger/50 bg-danger/8 text-danger hover:bg-danger/15 hover:border-danger/70 hover:shadow-[0_0_16px_rgba(217,123,108,0.2)] active:scale-[0.98]',
        },
      },
    },
  );
  return twMerge(clsx(variants({ variant: props.variant })));
});
</script>

<template>
  <button :type="type" :disabled="disabled" :class="buttonClass">
    <!-- 发光底部层 -->
    <span class="absolute inset-0 bg-[radial-gradient(ellipse_at_center,rgba(74,143,231,0.14)_0%,transparent_70%)] opacity-0 hover:opacity-100 transition-opacity duration-300" />
    <span class="relative z-10"><slot /></span>
  </button>
</template>
