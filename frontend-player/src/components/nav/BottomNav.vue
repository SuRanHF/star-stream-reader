<script setup lang="ts">
import { useGameStore } from '@/stores/gameStore';

const gameStore = useGameStore();

const emit = defineEmits<{
  select: [panel: string];
}>();

const items = [
  { key: 'chat', label: '星流频道', panel: 'chat' },
  { key: 'party', label: '队伍', panel: 'party' },
  { key: 'market', label: '鬼怪商店', panel: 'trade' },
  { key: 'martial', label: '化身战场', panel: 'pk' },
  { key: 'boss', label: '灾厄', panel: 'worldBoss' },
  { key: 'trial', label: '场景试炼', panel: 'quests' },
  { key: 'ranking', label: '化身排行', panel: 'ranking' },
  { key: 'book', label: '档案', panel: 'titles' },

  { key: 'secret', label: '隐藏场景', panel: 'hiddenScene' },
  { key: 'notice', label: '星流公告', panel: 'notice' },
  { key: 'abyss', label: '冥界', panel: 'underworld' },
  { key: 'help', label: '说明', panel: 'guide' },
  { key: 'history', label: '故事记录', panel: 'history' },
  { key: 'feedback', label: '鬼怪反馈', panel: 'feedback' },
];
</script>

<template>
  <nav class="ling-right-nav" aria-label="功能导航">
    <button
      v-for="item in items"
      :key="item.key"
      :class="{ active: gameStore.activePanel === item.panel }"
      @click="emit('select', item.panel)"
    >
      {{ item.label }}
    </button>
  </nav>
</template>

<style scoped>
button {
  min-height: 42px;
  min-width: 48px;
  padding: 8px 6px;
}
</style>
