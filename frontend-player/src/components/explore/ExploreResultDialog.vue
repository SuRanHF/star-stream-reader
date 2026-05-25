<script setup lang="ts">
import { ref, computed } from 'vue';
import { useUiStore } from '@/stores/uiStore';
import { useGameStore } from '@/stores/gameStore';
import { exploreApi, type ExploreResult, type ChoiceResult } from '@/api/exploreApi';
import { gameApi } from '@/api/gameApi';

const ui = useUiStore();
const game = useGameStore();

const dialogRef = ref<HTMLDivElement>();
const chosen = ref<ChoiceResult | null>(null);
const choosing = ref(false);

const props = defineProps<{
  visible: boolean;
  result: ExploreResult | null;
}>();

const emit = defineEmits<{
  close: [];
  choiceMade: [];
}>();

const storyMode = computed(() =>
  props.result?.result?.event_type === 'story' && props.result?.result?.choices?.length
);

function close() {
  chosen.value = null;
  emit('close');
}

async function selectChoice(index: number) {
  const id = Number(game.player?.id || game.player?.playerId || 0);
  const eventKey = props.result?.result?.event_key;
  if (!id || !eventKey) return;

  choosing.value = true;
  try {
    const payload = await exploreApi.makeChoice(id, eventKey, index);
    chosen.value = payload;
    emit('choiceMade');
    // Refresh player state
    const bootstrap = await gameApi.getBootstrap();
    game.applyBootstrap(bootstrap);
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '选择处理失败';
    await ui.showAlert('错误', msg);
  } finally {
    choosing.value = false;
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible && result"
      ref="dialogRef"
      class="erd-backdrop"
      @click.self="close"
    >
      <div class="erd-card">
        <!-- 事件标题 -->
        <h3 class="erd-title">{{ result.result.name }}</h3>
        <p class="erd-desc">{{ result.result.description }}</p>

        <!-- 奖励摘要 -->
        <div v-if="result.rewards && Object.keys(result.rewards).length" class="erd-rewards">
          <span
            v-if="result.rewards.coins"
            class="erd-reward-tag"
          >🪙 +{{ result.rewards.coins }}</span>
          <span
            v-if="result.rewards.exp"
            class="erd-reward-tag"
          >✨ +{{ result.rewards.exp }} 经验</span>
          <span
            v-if="result.rewards.storyFragments"
            class="erd-reward-tag"
          >📜 +{{ result.rewards.storyFragments }} 碎片</span>
          <span
            v-if="result.rewards.channelHeat"
            class="erd-reward-tag"
          >📡 +{{ result.rewards.channelHeat }} 热度</span>
          <span
            v-if="result.rewards.items"
            class="erd-reward-tag"
          >🎒 获得物品</span>
        </div>

        <!-- 故事耗尽提示 -->
        <div v-if="result.stories_exhausted" class="erd-exhausted">
          当前场景剧情已全部探索完毕。非剧情事件仍可继续触发。
        </div>

        <!-- 选项区域 (story 事件) -->
        <div v-if="storyMode && !chosen" class="erd-choices">
          <p class="erd-choices-hint">做出你的选择：</p>
          <button
            v-for="(c, i) in result.result.choices"
            :key="i"
            class="erd-choice-btn"
            :disabled="choosing"
            @click="selectChoice(i)"
          >{{ c.label }}</button>
        </div>

        <!-- 选择后果 -->
        <div v-if="chosen" class="erd-consequence">
          <p class="erd-cons-label">你选择了：{{ chosen.choice_label }}</p>
          <p class="erd-cons-text">{{ chosen.consequence_text }}</p>
          <div v-if="chosen.rewards && Object.keys(chosen.rewards).length" class="erd-rewards">
            <span
              v-if="chosen.rewards.coins"
              class="erd-reward-tag"
            >🪙 +{{ chosen.rewards.coins }}</span>
            <span
              v-if="chosen.rewards.exp"
              class="erd-reward-tag"
            >✨ +{{ chosen.rewards.exp }} 经验</span>
            <span
              v-if="chosen.rewards.storyFragments"
              class="erd-reward-tag"
            >📜 +{{ chosen.rewards.storyFragments }} 碎片</span>
            <span
              v-if="chosen.rewards.channelHeat"
              class="erd-reward-tag"
            >📡 +{{ chosen.rewards.channelHeat }} 热度</span>
            <span
              v-if="chosen.rewards.items"
              class="erd-reward-tag"
            >🎒 获得物品</span>
          </div>
          <div v-if="chosen.unlock_locations?.length" class="erd-unlock">
            🗺️ 解锁新地点：{{ chosen.unlock_locations.join('、') }}
          </div>
          <div v-if="chosen.unlock_events?.length" class="erd-unlock">
            📖 解锁新故事事件
          </div>
        </div>

        <!-- 底部按钮 -->
        <div class="erd-actions">
          <button
            v-if="storyMode && !chosen"
            class="erd-btn erd-btn-skip"
            @click="close"
          >稍后再选</button>
          <button
            class="erd-btn erd-btn-ok"
            @click="close"
          >{{ storyMode && !chosen ? '关闭' : (chosen ? '继续探索' : '知道了') }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.erd-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: erdFadeIn 0.15s ease;
}
@keyframes erdFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.erd-card {
  background: #1a1e2b;
  border: 1px solid #2e3345;
  border-radius: 14px;
  padding: 28px 32px;
  max-width: 520px;
  width: calc(100% - 40px);
  max-height: 85vh;
  overflow-y: auto;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
  animation: erdSlideIn 0.25s ease;
}
@keyframes erdSlideIn {
  from { transform: translateY(16px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
.erd-title {
  color: #caa86a;
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 14px;
  line-height: 1.3;
}
.erd-desc {
  color: #cbd5e1;
  font-size: 14px;
  line-height: 1.75;
  margin: 0 0 18px;
  white-space: pre-line;
}
.erd-rewards {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 18px;
}
.erd-reward-tag {
  padding: 3px 10px;
  border: 1px solid #334155;
  border-radius: 6px;
  font-size: 12px;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.3);
}

.erd-exhausted {
  padding: 8px 14px;
  border: 1px solid #554b34;
  border-radius: 8px;
  background: rgba(202, 168, 106, 0.06);
  color: #caa86a;
  font-size: 13px;
  margin-bottom: 18px;
  line-height: 1.5;
}

/* 选项 */
.erd-choices {
  margin-bottom: 18px;
}
.erd-choices-hint {
  color: #94a3b8;
  font-size: 13px;
  margin: 0 0 10px;
}
.erd-choice-btn {
  display: block;
  width: 100%;
  padding: 12px 16px;
  margin-bottom: 8px;
  border: 1px solid #3b82f6;
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.08);
  color: #93c5fd;
  font-size: 14px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
}
.erd-choice-btn:hover:not(:disabled) {
  background: rgba(59, 130, 246, 0.18);
  border-color: #60a5fa;
}
.erd-choice-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 后果 */
.erd-consequence {
  margin-bottom: 18px;
  padding: 14px 16px;
  border: 1px solid #334155;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.5);
}
.erd-cons-label {
  color: #caa86a;
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 8px;
}
.erd-cons-text {
  color: #cbd5e1;
  font-size: 13px;
  line-height: 1.7;
  margin: 0;
  white-space: pre-line;
}
.erd-unlock {
  font-size: 13px;
  color: #22c55e;
  margin-top: 8px;
}

/* 按钮 */
.erd-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.erd-btn {
  padding: 9px 24px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
}
.erd-btn-skip {
  background: #2e3345;
  color: #94a3b8;
}
.erd-btn-skip:hover {
  background: #3d4258;
}
.erd-btn-ok {
  background: #3b82f6;
  color: #fff;
  font-weight: 600;
}
.erd-btn-ok:hover {
  background: #2563eb;
}
</style>
