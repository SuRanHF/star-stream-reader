<script setup lang="ts">
import { ref, computed } from 'vue';
import { playerApi } from '@/api/playerApi';
import { gameApi } from '@/api/gameApi';
import { useGameStore } from '@/stores/gameStore';

const gameStore = useGameStore();

const reviving = ref(false);
const errorMsg = ref('');

const playerId = computed(() => Number(gameStore.player?.id || gameStore.player?.playerId || 0));
const level = computed(() => {
  const s = gameStore.player?.stats || {};
  return Number((s as Record<string, unknown>).level || 1);
});
const reviveCost = computed(() => 100 * level.value);
const coins = computed(() => Number(gameStore.player?.coins || 0));
const canAfford = computed(() => coins.value >= reviveCost.value);

async function revive() {
  if (reviving.value || !playerId.value) return;
  reviving.value = true;
  errorMsg.value = '';
  try {
    await playerApi.revive(playerId.value, 'coins');
    const bootstrap = await gameApi.getBootstrap();
    gameStore.applyBootstrap(bootstrap);
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : '复活失败';
  } finally {
    reviving.value = false;
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="do-backdrop">
      <div class="do-card">
        <div class="do-sigil">✿</div>
        <h2 class="do-title">灵魂坠入深渊</h2>

        <div class="do-narrative">
          <p>你的意识从身体中剥离，坠入无尽的黑暗。</p>
          <p>在深渊的底部——一座黑色水晶宫殿中——<b class="gold">最黑暗春天的女王</b> 端坐在藤蔓缠绕的王座上。她的双眼被丝带蒙住，但你能感到她的目光穿过了你的灵魂。</p>
          <p class="do-quote">"又一个在春天到来前倒下的灵魂。"她的声音像冰层下的流水。"我说过——不是所有人都能活着走到我的宫殿。但你来了。以死亡的方式。"</p>
          <p>她伸出手——掌心浮现着微弱的光：那是春天来临前的第一缕暖意，也是你返回人间的唯一钥匙。</p>
          <p class="do-quote">"想回去？当然可以。春天的规则很简单：<b>一切都需要代价</b>。你的灵魂现在属于深渊——赎回它需要 <b class="gold">{{ reviveCost }}</b> 星币。或者——你可以永远留在这里，成为我花园里一朵黑色的花。"</p>
        </div>

        <div class="do-cost-box">
          <div class="do-cost-row">
            <span>赎回灵魂的代价</span>
            <b class="gold">{{ reviveCost }} 星币</b>
          </div>
          <div class="do-cost-row">
            <span>你携带的财富</span>
            <b :class="canAfford ? 'gold' : 'red'">{{ coins }} 星币</b>
          </div>
        </div>

        <p v-if="errorMsg" class="do-error">{{ errorMsg }}</p>

        <button
          class="do-revive-btn"
          :disabled="!canAfford || reviving"
          @click="revive"
        >
          {{ reviving ? '赎回中...' : `赎回灵魂 (${reviveCost}币)` }}
        </button>

        <p v-if="!canAfford" class="do-hint">
          星币不足，无法赎回灵魂。也许会有其他读者向女神献上代价，换你归来。
        </p>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.do-backdrop {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: var(--color-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: doFadeIn 0.4s ease;
}
@keyframes doFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.do-card {
  background: linear-gradient(180deg, var(--color-panel-elevated), var(--color-void));
  border: 1px solid var(--color-danger-glow);
  border-radius: var(--radius-lg);
  padding: 36px 40px;
  text-align: center;
  max-width: 480px;
  width: calc(100% - 40px);
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: var(--shadow-float), var(--glow-danger);
  animation: doSlideIn 0.5s ease;
}
@keyframes doSlideIn {
  from { transform: translateY(32px) scale(0.92); opacity: 0; }
  to { transform: translateY(0) scale(1); opacity: 1; }
}
.do-sigil {
  font-size: 42px;
  margin-bottom: 8px;
  filter: grayscale(0.3);
}
.do-title {
  color: var(--color-danger-bright);
  font-size: 22px;
  font-weight: 800;
  margin: 0 0 20px;
  letter-spacing: 0.05em;
}

.do-narrative {
  text-align: left;
  margin-bottom: 20px;
}
.do-narrative p {
  color: var(--color-text-dim);
  font-size: 13px;
  line-height: 2;
  margin: 0 0 10px;
}
.do-narrative p:last-child {
  margin-bottom: 0;
}
.do-quote {
  color: var(--color-danger-bright) !important;
  padding-left: 12px;
  border-left: 2px solid var(--color-danger);
  font-style: italic;
}
.gold { color: var(--color-star); }
.red { color: var(--color-danger); }

.do-cost-box {
  background: var(--color-log);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px 18px;
  margin-bottom: 20px;
}
.do-cost-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 14px;
  color: var(--color-text-dim);
}
.do-cost-row + .do-cost-row {
  border-top: 1px solid rgba(255,255,255,0.04);
}

.do-error {
  color: var(--color-danger);
  font-size: 13px;
  margin: 0 0 12px;
}

.do-revive-btn {
  width: 100%;
  padding: 14px 0;
  border: 1px solid var(--color-danger-border, var(--color-danger-glow));
  border-radius: var(--radius-md);
  background: var(--color-danger-glow);
  color: var(--color-danger-bright);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 12px;
  letter-spacing: 0.03em;
}
.do-revive-btn:hover:not(:disabled) {
  background: rgba(var(--color-danger-rgb), 0.22);
  color: var(--color-text-bright);
  border-color: var(--color-danger);
}
.do-revive-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.do-hint {
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
}
</style>
