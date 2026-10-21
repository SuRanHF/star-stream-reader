<script setup lang="ts">
import { useGameStore } from '@/stores/gameStore';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  BookOpen,
  CircleHelp,
  Flame,
  History,
  Map,
  Megaphone,
  MessageCircle,
  MessageSquareWarning,
  MoreHorizontal,
  ScrollText,
  Shield,
  Skull,
  Store,
  Swords,
  Trophy,
  Users,
} from 'lucide-vue-next';

const gameStore = useGameStore();
const overflowOpen = ref(false);
const navElement = ref<HTMLElement | null>(null);
const visibleCount = ref(7);
const DESKTOP_LAYOUT_MIN_WIDTH = 888;
const isDesktopLayout = ref(window.innerWidth >= DESKTOP_LAYOUT_MIN_WIDTH);

const emit = defineEmits<{
  select: [panel: string];
}>();

// 沿用星流读者原有的功能与名称，只使用参考站式图标底栏呈现。
const compactItems = [
  { key: 'chat', label: '星流频道', icon: MessageCircle, panel: 'chat' },
  { key: 'party', label: '队伍', icon: Users, panel: 'party' },
  { key: 'support', label: '悬赏支援', icon: Shield, panel: 'support' },
  { key: 'market', label: '鬼怪商店', icon: Store, panel: 'trade' },
  { key: 'martial', label: '化身战场', icon: Swords, panel: 'pk' },
  { key: 'boss', label: '灾厄', icon: Flame, panel: 'worldBoss' },
  { key: 'trial', label: '场景试炼', icon: ScrollText, panel: 'quests' },
  { key: 'ranking', label: '化身排行', icon: Trophy, panel: 'ranking' },
  { key: 'book', label: '档案', icon: BookOpen, panel: 'titles' },
  { key: 'secret', label: '隐藏场景', icon: Map, panel: 'hiddenScene' },
  { key: 'notice', label: '星流公告', icon: Megaphone, panel: 'notice' },
  { key: 'abyss', label: '冥界', icon: Skull, panel: 'underworld' },
  { key: 'help', label: '说明', icon: CircleHelp, panel: 'guide' },
  { key: 'history', label: '故事记录', icon: History, panel: 'history' },
  { key: 'feedback', label: '鬼怪反馈', icon: MessageSquareWarning, panel: 'feedback' },
];
const desktopItems = compactItems;
const items = computed(() => isDesktopLayout.value ? desktopItems : compactItems);

// 手机/紧凑操作栏沿用原来的常用入口数量；桌面横向底栏按实际宽度收纳。
const COMPACT_VISIBLE_COUNT = 7;
const visibleItems = computed(() => items.value.slice(0, visibleCount.value));
const overflowItems = computed(() => items.value.slice(visibleCount.value));

function updateVisibleCount() {
  if (window.innerWidth < DESKTOP_LAYOUT_MIN_WIDTH) {
    isDesktopLayout.value = false;
    visibleCount.value = Math.min(COMPACT_VISIBLE_COUNT, items.value.length);
    return;
  }

  isDesktopLayout.value = true;
  const nav = navElement.value;
  const button = nav?.querySelector<HTMLElement>('.fn-btn');
  if (!nav || !button) return;

  const buttonWidth = button.getBoundingClientRect().width;
  const gap = Number.parseFloat(getComputedStyle(nav).columnGap) || 0;
  if (buttonWidth <= 0) return;

  const slots = Math.floor((nav.clientWidth + gap) / (buttonWidth + gap));
  const nextCount = slots >= items.value.length ? items.value.length : Math.max(0, slots - 1);
  visibleCount.value = nextCount;
  if (nextCount === items.value.length) overflowOpen.value = false;
}

let navResizeObserver: ResizeObserver | null = null;

onMounted(() => {
  updateVisibleCount();
  navResizeObserver = new ResizeObserver(updateVisibleCount);
  if (navElement.value) navResizeObserver.observe(navElement.value);
  window.addEventListener('resize', updateVisibleCount);
});

onBeforeUnmount(() => {
  navResizeObserver?.disconnect();
  window.removeEventListener('resize', updateVisibleCount);
});

function selectOverflow(panel: string) {
  overflowOpen.value = false;
  emit('select', panel);
}
</script>

<template>
  <nav ref="navElement" class="ling-right-nav fn-grid" aria-label="功能导航">
    <button
      v-for="item in visibleItems"
      :key="item.key"
      :class="['fn-btn', `fn-btn--${item.key}`, { active: gameStore.activePanel === item.panel }]"
      @click="emit('select', item.panel)"
    >
      <component :is="item.icon" class="fn-ic" :size="16" :stroke-width="1.7" aria-hidden="true" />
      <span class="fn-label">{{ item.label }}</span>
    </button>
    <button
      v-if="overflowItems.length"
      type="button"
      :class="['fn-btn', 'fn-btn--more', { active: overflowOpen || overflowItems.some((item) => gameStore.activePanel === item.panel) }]"
      :aria-expanded="overflowOpen"
      aria-haspopup="menu"
      @click="overflowOpen = !overflowOpen"
    >
      <MoreHorizontal class="fn-ic" :size="16" :stroke-width="1.7" aria-hidden="true" />
      <span class="fn-label">更多</span>
    </button>
    <Teleport to="body">
      <div v-if="overflowOpen" class="fn-overflow" role="menu">
        <button
          v-for="item in overflowItems"
          :key="item.key"
          type="button"
          :class="['fn-overflow-item', { active: gameStore.activePanel === item.panel }]"
          role="menuitem"
          @click="selectOverflow(item.panel)"
        >
          <component :is="item.icon" class="fn-overflow-ic" :size="15" :stroke-width="1.7" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </button>
      </div>
    </Teleport>
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
  box-shadow: var(--glow-star);
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
  background: var(--color-star-border);
  opacity: 1;
}

.fn-overflow {
  position: fixed;
  right: 16px;
  bottom: 68px;
  z-index: 1200;
  display: grid;
  grid-template-columns: repeat(3, minmax(72px, 1fr));
  gap: 5px;
  width: min(250px, calc(100vw - 20px));
  padding: 8px;
  border: 1px solid var(--color-star-border);
  border-radius: var(--radius-md);
  background: var(--color-panel-elevated);
  box-shadow: var(--shadow-float);
}

.fn-overflow-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 0;
  min-height: 34px;
  padding: 5px 7px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-dim);
  font-size: 11px;
  letter-spacing: 0;
}

.fn-overflow-item:hover,
.fn-overflow-item.active {
  border-color: var(--color-star-border);
  background: var(--color-star-bg);
  color: var(--color-star-bright);
}

.fn-overflow-ic {
  flex: 0 0 auto;
}

/* 主区被侧栏压窄时，更多仍固定在功能格右端，不能随横向入口一起被藏掉。 */
.fn-btn--more {
  position: sticky;
  right: 0;
  z-index: 3;
  border-color: var(--color-star-border) !important;
  background: var(--color-panel) !important;
  box-shadow: -8px 0 12px color-mix(in srgb, var(--color-panel) 88%, transparent);
}

.fn-btn--more.active,
.fn-btn--more:hover {
  background: var(--color-star-bg) !important;
}

@media (max-width: 760px) {
  .fn-overflow {
    right: 10px;
    bottom: 270px;
  }
}
</style>
