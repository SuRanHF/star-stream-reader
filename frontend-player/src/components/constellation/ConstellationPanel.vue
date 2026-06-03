<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { http } from '@/api/http';
import { useGameStore } from '@/stores/gameStore';
import { useUiStore } from '@/stores/uiStore';
import { playerApi } from '@/api/playerApi';
import { gameApi } from '@/api/gameApi';

const game = useGameStore();
const ui = useUiStore();

interface ConstellationEntry {
  constellationKey: string;
  nebulaKey: string;
  name: string;
  description: string;
  emoji: string;
  effects: Record<string, number>;
  isActive: boolean;
  favor: number;
}

const constellations = ref<ConstellationEntry[]>([]);
const changing = ref(false);

async function fetchConstellations() {
  try {
    const payload = await http.get<any, any>('/player/constellations');
    const list = payload?.constellations || payload?.data?.constellations || [];
    constellations.value = list.map((c: any) => ({
      constellationKey: c.constellationKey || c.id || '',
      nebulaKey: c.nebulaKey || '',
      name: c.name || '',
      description: c.description || '',
      emoji: c.emoji || '✦',
      effects: c.effects || {},
      isActive: c.isActive || c.active || false,
      favor: c.favor ?? c.constellationFavor ?? 0,
    }));
  } catch {
    // silent fail - constellation list will be empty
  }
}

watch(() => game.player?.id, (id) => {
  if (id) fetchConstellations();
}, { immediate: true });

onMounted(() => {
  if (game.player?.id) fetchConstellations();
});

const activeList = computed(() => constellations.value.filter(c => c.isActive));
const watchingList = computed(() => constellations.value.filter(c => !c.isActive));

function favorStars(n: number): string {
  const stars = Math.min(5, Math.max(0, Math.round(n / 200)));
  return '★'.repeat(stars) + '☆'.repeat(5 - stars);
}

function favorLabel(n: number): string {
  if (n >= 800) return '狂热';
  if (n >= 600) return '喜爱';
  if (n >= 400) return '关注';
  if (n >= 200) return '感兴趣';
  return '观望';
}

function effectsText(effects: Record<string, number>): string {
  const labels: Record<string, string> = {
    atk: '攻', def: '防', spd: '速', maxHp: '血',
    critRate: '暴击', luck: '幸运', insight: '洞察',
    worldLineShift: '世界线偏移',
  };
  return Object.entries(effects)
    .map(([k, v]) => {
      const label = labels[k] || k;
      const valStr = k === 'critRate' || k === 'luck' ? `${(v * 100).toFixed(0)}%` : `+${v}`;
      return `${label}${valStr}`;
    })
    .join(' ');
}

async function changeConstellation(key: string) {
  if (changing.value) return;
  const pid = Number(game.player?.id || game.player?.playerId || 0);
  if (!pid) return;

  const confirmed = await ui.showConfirm(
    '切换背後星',
    '切换星座需要消耗200故事碎片，并失去当前星座好感度的50%。确定要切换吗？'
  );
  if (!confirmed) return;

  changing.value = true;
  try {
    await http.post('/player/change-constellation', {
      playerId: pid,
      constellationKey: key,
    });
    const bootstrap = await gameApi.getBootstrap();
    game.applyBootstrap(bootstrap);
    await fetchConstellations();
    await ui.showAlert('成功', '背後星已切换');
  } catch (e: any) {
    await ui.showAlert('错误', e?.message || '切换失败');
  } finally {
    changing.value = false;
  }
}

const playerConst = computed(() => {
  const s = game.player?.stats as Record<string, unknown> | undefined;
  return String(s?.constellation || '');
});
</script>

<template>
  <div class="csp-root">
    <!-- 正在注视 -->
    <div class="csp-section">
      <div class="csp-section-title">正在注视</div>
      <div
        v-for="c in activeList"
        :key="c.constellationKey"
        class="csp-card is-active"
      >
        <div class="csp-card-top">
          <span class="csp-icon">{{ c.emoji }}</span>
          <span class="csp-name">{{ c.name }}</span>
          <span class="csp-favor" :title="favorLabel(c.favor)">{{ favorStars(c.favor) }}</span>
        </div>
        <p class="csp-comment">{{ c.description }}</p>
        <div class="csp-effects">{{ effectsText(c.effects) }}</div>
      </div>
    </div>

    <!-- 其他星座 -->
    <div class="csp-section">
      <div class="csp-section-title">星流观测者</div>
      <div
        v-for="c in watchingList"
        :key="c.constellationKey"
        class="csp-card"
      >
        <div class="csp-card-top">
          <span class="csp-icon dim">{{ c.emoji }}</span>
          <span class="csp-name dim">{{ c.name }}</span>
          <span class="csp-favor dim" :title="favorLabel(c.favor)">{{ favorStars(c.favor) }}</span>
        </div>
        <p class="csp-comment">{{ c.description }}</p>
        <div class="csp-effects dim">{{ effectsText(c.effects) }}</div>
        <button
          class="csp-switch-btn"
          :disabled="changing"
          @click="changeConstellation(c.constellationKey)"
        >切换至 {{ c.name }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.csp-root {
  padding: 2px 0;
}

.csp-section {
  margin-bottom: 16px;
}

.csp-section-title {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 3px;
  color: var(--color-muted);
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(26, 38, 80, 0.4);
}

.csp-card {
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
  background: rgba(7, 11, 26, 0.4);
  margin-bottom: 6px;
  transition: all 0.2s;
}

.csp-card.is-active {
  border-color: rgba(74, 143, 231, 0.25);
  background: rgba(74, 143, 231, 0.04);
}

.csp-card:hover {
  border-color: rgba(74, 143, 231, 0.3);
}

.csp-card-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.csp-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.csp-icon.dim {
  opacity: 0.5;
}

.csp-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-system-bright);
  flex: 1;
}

.csp-name.dim {
  color: var(--color-text-dim);
  font-weight: 400;
}

.csp-favor {
  font-size: 10px;
  color: #e0556a;
  letter-spacing: 1px;
  flex-shrink: 0;
}

.csp-favor.dim {
  opacity: 0.4;
}

.csp-comment {
  margin: 6px 0 0;
  font-size: 11px;
  color: #9088b8;
  line-height: 1.5;
  font-style: italic;
  padding-left: 24px;
}

.csp-effects {
  margin: 4px 0 0;
  padding-left: 24px;
  font-size: 10px;
  color: var(--color-system-bright);
  letter-spacing: 0.5px;
}

.csp-effects.dim {
  opacity: 0.5;
}

.csp-switch-btn {
  margin: 8px 0 0 24px;
  padding: 4px 12px;
  border: 1px solid var(--color-system);
  border-radius: 4px;
  background: rgba(74, 143, 231, 0.08);
  color: var(--color-system-bright);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.csp-switch-btn:hover:not(:disabled) {
  background: rgba(74, 143, 231, 0.18);
  border-color: var(--color-system-bright);
}

.csp-switch-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
