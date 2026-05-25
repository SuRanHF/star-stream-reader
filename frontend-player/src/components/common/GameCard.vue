<script setup lang="ts">
defineProps<{
  title?: string;
  subtitle?: string;
}>();
</script>

<template>
  <section class="game-panel rounded-md p-4">
    <header v-if="title || subtitle" class="mb-3 flex items-baseline justify-between gap-3 border-b border-line pb-2">
      <h2 v-if="title" class="text-sm font-semibold tracking-wide text-star">{{ title }}</h2>
      <p v-if="subtitle" class="text-xs text-muted">{{ subtitle }}</p>
    </header>
    <slot />
  </section>
</template>
