<script setup lang="ts">
import { twMerge } from 'tailwind-merge';

defineProps<{
  title?: string;
  subtitle?: string;
  class?: string;
}>();
</script>

<template>
  <section :class="twMerge('game-panel rounded-md p-5 transition-shadow duration-300 hover:shadow-[0_0_24px_var(--color-star-glow)]', $props.class)">
    <header v-if="title || subtitle" class="mb-4 flex items-baseline justify-between gap-3 border-b border-line/60 pb-3">
      <h2 v-if="title" class="text-sm font-semibold tracking-wider" style="color: var(--color-star-bright); text-shadow: 0 0 8px var(--color-star-glow);">{{ title }}</h2>
      <p v-if="subtitle" class="text-xs text-muted">{{ subtitle }}</p>
    </header>
    <slot />
  </section>
</template>
