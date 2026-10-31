<script setup lang="ts">
import { ref, computed } from 'vue';
import { playerApi } from '@/api/playerApi';
import { gameApi } from '@/api/gameApi';
import { useGameStore } from '@/stores/gameStore';
import { useUiStore } from '@/stores/uiStore';

const gameStore = useGameStore();
const uiStore = useUiStore();

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

// 献祭称号：从已拥有的称号里选一个，永久失去；需要再点一次确认（遮罩层级高于通用确认框）。
const ownedTitles = computed(() => (gameStore.titles || [])
  .map((entry) => ({
    key: String(entry.titleKey || entry.title_key || ''),
    name: String(entry.name || entry.titleName || entry.titleKey || ''),
    rarity: String(entry.rarity || ''),
  }))
  .filter((title) => title.key));
const selectedTitleKey = ref('');
const sacrificeArmed = ref(false);
const selectedTitle = computed(() => ownedTitles.value.find((title) => title.key === selectedTitleKey.value));

function selectTitle(key: string) {
  selectedTitleKey.value = selectedTitleKey.value === key ? '' : key;
  sacrificeArmed.value = false;
}

async function sacrificeTitle() {
  if (!selectedTitle.value) return;
  if (!sacrificeArmed.value) {
    sacrificeArmed.value = true;
    return;
  }
  await revive('title', selectedTitle.value.key);
  sacrificeArmed.value = false;
}

async function revive(method: 'coins' | 'title' = 'coins', titleKey?: string) {
  if (reviving.value || !playerId.value) return;
  reviving.value = true;
  errorMsg.value = '';
  try {
    await playerApi.revive(playerId.value, method, titleKey);
  } catch (e: unknown) {
    errorMsg.value = e instanceof Error ? e.message : '复活失败';
    reviving.value = false;
    return;
  }
  // 服务端已结算（献祭不可逆）：先在本地标记已复活，遮罩随之关闭；
  // 之后的资料刷新失败只影响数字显示，不能再报「复活失败」诱导玩家重复献祭。
  gameStore.patchPlayer({ stats: { isDead: false } });
  try {
    gameStore.applyBootstrap(await gameApi.getBootstrap());
  } catch {
    void uiStore.showAlert('已从冥界归来', '复活已经生效，但角色资料同步失败，稍后会自动刷新。');
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
          @click="revive('coins')"
        >
          {{ reviving ? '赎回中...' : `赎回灵魂 (${reviveCost}币)` }}
        </button>

        <section class="do-sacrifice" aria-labelledby="doSacrificeTitle">
          <h3 id="doSacrificeTitle" class="do-sacrifice-title">或者，献上一个称号</h3>
          <p class="do-hint">被献祭的称号会<b class="red">永久失去</b>，它带来的加成也随之消失。</p>
          <p v-if="!ownedTitles.length" class="do-hint">你还没有可以献上的称号。</p>
          <div v-else class="do-title-list" role="radiogroup" aria-label="选择要献祭的称号">
            <button
              v-for="title in ownedTitles"
              :key="title.key"
              type="button"
              role="radio"
              :aria-checked="selectedTitleKey === title.key"
              :class="['do-title-chip', { active: selectedTitleKey === title.key }]"
              :disabled="reviving"
              @click="selectTitle(title.key)"
            >
              {{ title.name }}<small v-if="title.rarity">{{ title.rarity }}</small>
            </button>
          </div>
          <button
            v-if="ownedTitles.length"
            type="button"
            :class="['do-revive-btn', 'do-sacrifice-btn', { armed: sacrificeArmed }]"
            :disabled="!selectedTitle || reviving"
            @click="sacrificeTitle"
          >
            {{ !selectedTitle ? '先选择一个称号'
              : sacrificeArmed ? `再点一次：永久献祭「${selectedTitle.name}」`
                : `献祭「${selectedTitle.name}」并归来` }}
          </button>
        </section>

        <p v-if="!canAfford && !ownedTitles.length" class="do-hint">
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

.do-sacrifice {
  margin: 4px 0 14px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
  text-align: left;
}
.do-sacrifice-title {
  margin: 0 0 4px;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 700;
}
.do-title-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0 12px;
}
.do-title-chip {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill, 999px);
  background: transparent;
  color: var(--color-text-dim);
  font-size: 13px;
  cursor: pointer;
}
.do-title-chip small {
  color: var(--color-muted);
  font-size: 11px;
}
.do-title-chip.active {
  border-color: var(--color-star);
  color: var(--color-star);
}
.do-sacrifice-btn {
  font-size: 14px;
  margin-bottom: 0;
}
.do-sacrifice-btn.armed {
  border-color: var(--color-danger);
  color: var(--color-text-bright);
}
.do-hint {
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
}
</style>
