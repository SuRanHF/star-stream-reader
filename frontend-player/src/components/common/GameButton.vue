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
            'border-star/40 bg-star/10 text-star-bright hover:bg-star/20 hover:border-star/70 hover:shadow-[0_0_16px_var(--color-star-glow)] active:scale-[0.98]',
          ghost:
            'border-line bg-control/80 text-muted hover:border-spirit/50 hover:text-spirit hover:bg-spirit/5 active:scale-[0.98]',
          danger:
            'border-danger/50 bg-danger/8 text-danger hover:bg-danger/15 hover:border-danger/70 hover:shadow-[0_0_16px_var(--color-danger-glow)] active:scale-[0.98]',
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
    <span class="absolute inset-0 bg-[radial-gradient(ellipse_at_center,var(--color-star-glow)_0%,transparent_70%)] opacity-0 hover:opacity-100 transition-opacity duration-300" />
    <span class="relative z-10"><slot /></span>
  </button>
</template>
