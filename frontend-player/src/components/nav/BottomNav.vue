<script setup lang="ts">
import { useGameStore } from '@/stores/gameStore';

const gameStore = useGameStore();

const emit = defineEmits<{
  select: [panel: string];
}>();

const items = [
  { key: 'chat', label: '星流频道', panel: 'chat' },
  { key: 'party', label: '队伍', panel: 'party' },
  { key: 'support', label: '悬赏支援', panel: 'support' },
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
nav {
  position: relative;
}

button {
  position: relative;
  min-height: 40px;
  min-width: 44px;
  padding: 6px 6px;
  font-size: 12px;
  letter-spacing: 1px;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

/* 活跃项左侧蓝色光条 */
button.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 2px;
  border-radius: 0 2px 2px 0;
  background: linear-gradient(180deg, transparent, var(--color-system), transparent);
  box-shadow: 0 0 6px rgba(74, 143, 231, 0.4);
  opacity: 0;
  transition: opacity 0.3s;
}

button.active::before {
  opacity: 1;
}

/* hover 时左侧微光 */
button:not(.active):hover::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 1px;
  border-radius: 0 1px 1px 0;
  background: rgba(74, 143, 231, 0.25);
  opacity: 1;
}
</style>
