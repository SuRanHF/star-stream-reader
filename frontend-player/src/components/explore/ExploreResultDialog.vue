<script setup lang="ts">
import { ref, computed, watch, shallowRef } from 'vue';
import { useUiStore } from '@/stores/uiStore';
import { useGameStore } from '@/stores/gameStore';
import { exploreApi, type ExploreResult, type ChoiceResult } from '@/api/exploreApi';
import { gameApi } from '@/api/gameApi';
import { combatApi, type CombatResult } from '@/api/combatApi';

const ui = useUiStore();
const game = useGameStore();

const props = defineProps<{
  visible: boolean;
  result: ExploreResult | null;
}>();

const emit = defineEmits<{
  close: [];
  choiceMade: [];
}>();

const chosen = ref<ChoiceResult | null>(null);
const choosing = ref(false);

// ── 战斗模式状态 ──
const battlePhase = ref<'idle' | 'fighting' | 'done'>('idle');
const battleResult = ref<CombatResult | null>(null);
const battling = ref(false);

const battleMode = computed(() =>
  props.result?.result_type === 'battle'
);

interface MonsterInfo { name: string; hp: number; attack: number; defense: number; speed: number; type: string; rarity: string; }
const monsterData = shallowRef<MonsterInfo | null>(null);

watch(battleMode, async (is) => {
  if (!is) { monsterData.value = null; return; }
  const mk = props.result?.monster_key;
  if (!mk) return;
  try {
    const list = await combatApi.getMonsters() as Record<string, unknown>[];
    const m = list.find((x: Record<string, unknown>) => x.monsterKey === mk) as Record<string, unknown> | undefined;
    if (m) {
      const s = (m.stats || {}) as Record<string, unknown>;
      monsterData.value = {
        name: String(m.name || ''),
        hp: Number(s.hp || 50), attack: Number(s.attack || 10),
        defense: Number(s.defense || 3), speed: Number(s.speed || 2),
        type: String(m.type || '?'), rarity: String(m.rarity || 'common'),
      };
    }
  } catch {}
});

const monsterName = computed(() =>
  props.result?.result?.name || '未知怪物'
);
const monsterDesc = computed(() =>
  props.result?.result?.description || ''
);

const storyMode = computed(() =>
  props.result?.result?.event_type === 'story' && props.result?.result?.choices?.length
);

// ── 评分计算 ──
interface GradeInfo {
  grade: string;
  label: string;
  color: string;
  glow: string;
  comment: string;
}

const gradeInfo = computed<GradeInfo>(() => {
  const r = props.result;
  if (!r) return { grade: '?', label: '无数据', color: '#5a6688', glow: '', comment: '' };

  // Base coins value for grading
  const coins = Number(r.rewards?.coins || 0);
  const exp = Number(r.rewards?.exp || 0);
  const fragments = Number(r.rewards?.storyFragments || 0);
  const hasItems = !!r.rewards?.items;
  const isStory = r.result?.event_type === 'story';
  const exhausted = r.stories_exhausted;

  if (exhausted) {
    return {
      grade: 'C', label: '场景耗尽', color: '#5a6688', glow: '',
      comment: '该区域已无可探索内容。'
    };
  }

  if (isStory && !exhausted) {
    if (fragments >= 2 && coins >= 1000) {
      return {
        grade: 'S', label: '完美通关', color: '#c0a0f0',
        glow: '0 0 20px rgba(192, 160, 240, 0.4)',
        comment: '背后的星座对你的表现十分满意。'
      };
    }
    if (coins >= 500 || fragments >= 1) {
      return {
        grade: 'A', label: '出色表现', color: '#4a8fe7',
        glow: '0 0 16px rgba(74, 143, 231, 0.4)',
        comment: '星座们点了点头。你做得不错。'
      };
    }
    return {
      grade: 'B', label: '标准通关', color: '#5ec49e',
      glow: '0 0 12px rgba(94, 196, 158, 0.3)',
      comment: '你完成了场景，但没有留下特别的印象。'
    };
  }

  // Non-story events
  if (coins >= 500 || hasItems) {
    return {
      grade: 'B', label: '有所收获', color: '#5ec49e',
      glow: '0 0 10px rgba(94, 196, 158, 0.2)',
      comment: '一次平稳的行动。'
    };
  }
  return {
    grade: 'D', label: '平淡无奇', color: '#8a96b8', glow: '',
    comment: '星流中没有激起任何涟漪。'
  };
});

// ── 随机星座评论 ──
const constComments = computed(() => {
  const grade = gradeInfo.value.grade;
  const pool: Record<string, string[]> = {
    'S': [
      '"精彩绝伦。你的故事值得被铭记。"',
      '"连我都忍不住鼓掌了。"',
      '"这一页必须被记录。"',
    ],
    'A': [
      '"不错。继续保持。"',
      '"还不算完美——但我看到了潜力。"',
      '"有趣的行动方式。"',
    ],
    'B': [
      '"......"',
      '"下次试着做得更好。"',
      '"勉勉强强。"',
    ],
    'C': [
      '"已经没有东西了。该离开了。"',
      '"浪费时间。"',
    ],
    'D': [
      '"......"',
      '"毫无波澜。"',
    ],
  };
  const comments = pool[grade] || pool['B'];
  const idx = Math.floor(Math.random() * comments.length);
  return comments[idx];
});

const constIcon = computed(() => {
  const icons = ['🔥', '✦', '📜', '✿', '⛓'];
  return icons[Math.floor(Math.random() * icons.length)];
});

// ── 奖励分线 ──
interface RewardLine {
  icon: string;
  label: string;
  amount: string;
  highlight: boolean;
}

const rewardLines = computed<RewardLine[]>(() => {
  const r = props.result?.rewards;
  if (!r) return [];
  const lines: RewardLine[] = [];
  if (r.coins) lines.push({ icon: '🪙', label: '星币', amount: `+${r.coins}`, highlight: Number(r.coins) >= 500 });
  if (r.exp) lines.push({ icon: '✨', label: '经验值', amount: `+${r.exp}`, highlight: false });
  if (r.storyFragments) lines.push({ icon: '📜', label: '寓言碎片', amount: `+${r.storyFragments}`, highlight: true });
  if (r.channelHeat) lines.push({ icon: '📡', label: '频道热度', amount: `+${r.channelHeat}`, highlight: false });
  if (r.items) lines.push({ icon: '🎒', label: '获得物品', amount: '1件', highlight: false });
  return lines;
});

function close() {
  chosen.value = null;
  battlePhase.value = 'idle';
  battleResult.value = null;
  emit('close');
}

async function startBattle() {
  const id = Number(game.player?.id || game.player?.playerId || 0);
  const monsterKey = props.result?.monster_key;
  if (!id || !monsterKey) return;

  battlePhase.value = 'fighting';
  battling.value = true;
  try {
    const result = await combatApi.startCombat(id, monsterKey);
    battleResult.value = result;
    battlePhase.value = 'done';
    const bootstrap = await gameApi.getBootstrap();
    game.applyBootstrap(bootstrap);
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '战斗启动失败';
    await ui.showAlert('错误', msg);
    battlePhase.value = 'idle';
  } finally {
    battling.value = false;
  }
}

async function fleeBattle() {
  const id = Number(game.player?.id || game.player?.playerId || 0);
  const monsterKey = props.result?.monster_key;
  if (!id || !monsterKey) return;

  battling.value = true;
  try {
    const result = await combatApi.resolveCombat(id, monsterKey, 'flee');
    battleResult.value = result;
    battlePhase.value = 'done';
  } catch {
    await ui.showAlert('逃跑失败', '无法逃跑，请尝试战斗。');
  } finally {
    battling.value = false;
  }
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
    <div v-if="visible && result" class="sst-backdrop" @click.self="close">
      <div class="sst-card">

        <!-- ═══ 战斗模式：怪物遭遇 ═══ -->
        <template v-if="battleMode">
          <div class="sst-battle-header">
            <div class="sst-battle-badge">⚔ BATTLE</div>
            <h3 class="sst-event-name">{{ monsterName }}</h3>
            <p class="sst-event-desc">{{ monsterDesc }}</p>
          </div>

          <!-- 战斗前：双方属性对比 -->
          <template v-if="battlePhase === 'idle'">
            <div class="sst-battle-preview">
              <div class="sst-battle-monster-icon">👾</div>
              <div class="sst-battle-vs">
                <div class="sst-battle-side">
                  <div class="sst-battle-side-title">🛡 我方</div>
                  <div class="sst-battle-side-stat">生命 {{ game.player?.hp || '?' }} / {{ game.player?.maxHp || '?' }}</div>
                  <div class="sst-battle-side-stat">攻击 {{ game.player?.attack || '?' }}</div>
                  <div class="sst-battle-side-stat">防御 {{ game.player?.defense || '?' }}</div>
                  <div class="sst-battle-side-stat">速度 {{ game.player?.speed || '?' }}</div>
                </div>
                <div class="sst-battle-vs-divider">VS</div>
                <div class="sst-battle-side">
                  <div class="sst-battle-side-title">👾 敌方</div>
                  <template v-if="monsterData">
                    <div class="sst-battle-side-stat name">{{ monsterData.name }}</div>
                    <div class="sst-battle-side-stat">生命 {{ monsterData.hp }}</div>
                    <div class="sst-battle-side-stat">攻击 {{ monsterData.attack }}</div>
                    <div class="sst-battle-side-stat">防御 {{ monsterData.defense }}</div>
                    <div class="sst-battle-side-stat">速度 {{ monsterData.speed }}</div>
                    <div class="sst-battle-side-stat desc">品质: {{ {common:'普通',uncommon:'稀有',rare:'罕见',epic:'史诗',legendary:'传说'}[monsterData.rarity] || monsterData.rarity }}</div>
                  </template>
                  <template v-else>
                    <div class="sst-battle-side-stat name">{{ monsterName }}</div>
                    <div class="sst-battle-side-stat desc">{{ monsterDesc }}</div>
                  </template>
                </div>
              </div>
              <p class="sst-battle-hint">准备迎战。你可以选择战斗或撤退。</p>
            </div>
            <div class="sst-actions sst-battle-actions">
              <button class="sst-btn sst-btn-fight" :disabled="battling" @click="startBattle">
                ⚔ 战斗
              </button>
              <button class="sst-btn sst-btn-flee" :disabled="battling" @click="fleeBattle">
                🏃 撤退
              </button>
              <button class="sst-btn sst-btn-close" @click="close">返回</button>
            </div>
          </template>

          <!-- 战斗中 -->
          <div v-if="battlePhase === 'fighting'" class="sst-battle-fighting">
            <div class="sst-battle-spinner"></div>
            <p class="sst-battle-status">战斗中...</p>
          </div>

          <!-- 战斗结果 -->
          <template v-if="battlePhase === 'done' && battleResult">
            <div class="sst-battle-result">
              <div class="sst-battle-outcome" :class="battleResult.result">
                {{ { win: '✌ 胜利！', lose: '💀 失败', escape: '🏃 逃跑成功', draw: '🤝 平局' }[battleResult.result] || '战斗结束' }}
              </div>
              <div class="sst-battle-stats">
                <div class="sst-battle-stat-row">
                  <span>敌方</span>
                  <span>{{ battleResult.monster.name }}</span>
                </div>
                <div class="sst-battle-stat-row">
                  <span>我方血量</span>
                  <span>{{ (battleResult as any).playerHpBefore || battleResult.hpBefore }} → {{ (battleResult as any).playerHpAfter || battleResult.hpAfter }}</span>
                </div>
                <div class="sst-battle-stat-row">
                  <span>回合数</span>
                  <span>{{ battleResult.totalRounds }}</span>
                </div>
              </div>
              <!-- 战斗过程 -->
              <div v-if="battleResult.rounds?.length" class="sst-battle-rounds">
                <div class="sst-battle-rounds-title">⚡ 战斗过程</div>
                <div v-for="r in battleResult.rounds" :key="r.round" class="sst-battle-round">
                  <div class="sst-battle-round-num">第 {{ r.round }} 回合</div>
                  <div v-for="(act, ai) in r.actions" :key="ai" class="sst-battle-action">
                    <span class="sst-battle-action-who">{{ {monster:'👾敌方',player:'🛡我方'}[act.actor] || act.actor }}</span>
                    <span class="sst-battle-action-what">
                      <template v-if="act.type === 'attack'">
                        攻击造成 <b>{{ act.damage }}</b> 伤害
                        <span v-if="act.critical" class="sst-battle-crit">暴击!</span>
                      </template>
                      <template v-else-if="act.type === 'narrative'">
                        {{ act.description }}
                      </template>
                      <template v-else>
                        {{ act.description || act.type }}
                      </template>
                    </span>
                  </div>
                </div>
              </div>
              <div v-if="battleResult.result === 'win'" class="sst-rewards">
                <div class="sst-rewards-title">战斗奖励</div>
                <div v-if="battleResult.rewards.exp" class="sst-reward-row">
                  <span class="sst-reward-icon">✨</span>
                  <span class="sst-reward-label">经验值</span>
                  <span class="sst-reward-amount">+{{ battleResult.rewards.exp }}</span>
                </div>
                <div v-if="battleResult.rewards.coins" class="sst-reward-row">
                  <span class="sst-reward-icon">🪙</span>
                  <span class="sst-reward-label">星币</span>
                  <span class="sst-reward-amount">+{{ battleResult.rewards.coins }}</span>
                </div>
                <div v-if="battleResult.rewards.storyFragments" class="sst-reward-row">
                  <span class="sst-reward-icon">📜</span>
                  <span class="sst-reward-label">故事碎片</span>
                  <span class="sst-reward-amount">+{{ battleResult.rewards.storyFragments }}</span>
                </div>
                <div v-if="battleResult.rewards.channelHeat" class="sst-reward-row">
                  <span class="sst-reward-icon">📡</span>
                  <span class="sst-reward-label">频道热度</span>
                  <span class="sst-reward-amount">+{{ battleResult.rewards.channelHeat }}</span>
                </div>
              </div>
            </div>
            <div class="sst-actions">
              <button class="sst-btn sst-btn-close" @click="close">返回</button>
            </div>
          </template>
        </template>

        <!-- ═══ 非故事模式：场景结算 ═══ -->
        <template v-else-if="!storyMode || chosen">
          <!-- 评分徽章 -->
          <div class="sst-grade-wrap">
            <div
              class="sst-grade"
              :style="{
                color: gradeInfo.color,
                textShadow: gradeInfo.glow,
                borderColor: gradeInfo.color,
              }"
            >
              {{ gradeInfo.grade }}
            </div>
            <div class="sst-grade-label" :style="{ color: gradeInfo.color }">
              {{ gradeInfo.label }}
            </div>
          </div>

          <!-- 系统公告 -->
          <div :class="['ling-system-msg', gradeInfo.grade === 'S' ? 'ling-system-ok' : '']">
            <div class="ling-system-msg-title">
              {{ gradeInfo.grade === 'S' ? 'SCENARIO CLEAR — PERFECT' : 'SCENARIO COMPLETE' }}
            </div>
            <div class="ling-system-msg-body">
              {{ gradeInfo.comment }}
            </div>
          </div>

          <!-- 事件内容 -->
          <div class="sst-event">
            <h3 class="sst-event-name">{{ result.result.name }}</h3>
            <p class="sst-event-desc">{{ result.result.description }}</p>
          </div>

          <!-- 选择后果 -->
          <div v-if="chosen" class="sst-consequence">
            <div class="sst-cons-header">
              <span class="sst-cons-label">你的选择</span>
              <span class="sst-cons-choice">{{ chosen.choice_label }}</span>
            </div>
            <p class="sst-cons-text">{{ chosen.consequence_text }}</p>
            <div v-if="chosen.unlock_locations?.length" class="sst-unlock">
              🗺️ 解锁新地点：{{ chosen.unlock_locations.join('、') }}
            </div>
            <div v-if="chosen.unlock_events?.length" class="sst-unlock">
              📖 解锁新故事事件
            </div>
          </div>

          <!-- 奖励明细 -->
          <div v-if="rewardLines.length" class="sst-rewards">
            <div class="sst-rewards-title">场景奖励明细</div>
            <div
              v-for="item in rewardLines"
              :key="item.label"
              :class="['sst-reward-row', { 'is-highlight': item.highlight }]"
            >
              <span class="sst-reward-icon">{{ item.icon }}</span>
              <span class="sst-reward-label">{{ item.label }}</span>
              <span class="sst-reward-amount">{{ item.amount }}</span>
            </div>
          </div>

          <!-- 星座评论 -->
          <div class="ling-const-comment">
            <span class="const-icon">{{ constIcon }}</span>
            <span class="const-name">星座的注视</span>
          </div>
          <p class="sst-const-quote">{{ constComments }}</p>

          <!-- 故事耗尽 -->
          <div v-if="result.stories_exhausted" class="sst-exhausted">
            ◆ 当前场景剧情已全部探索完毕。非剧情事件仍可继续触发。
          </div>

          <!-- 剧情屏蔽：低等级地点有未完成剧情 -->
          <div v-if="result.stories_blocked" class="sst-blocked">
            ◆ 低等级地点尚有未完成的剧情。
            <span v-if="result.lower_unfinished?.length">
              {{ result.lower_unfinished.join('、') }}
            </span>
            故事等待你的选择。完成低等级剧情前，当前地点不会触发故事事件。
          </div>

          <!-- 关闭 -->
          <div class="sst-actions">
            <button class="sst-btn sst-btn-close" @click="close">
              {{ chosen ? '继续执行' : '返回' }}
            </button>
          </div>
        </template>

        <!-- ═══ 故事模式：选项 ═══ -->
        <template v-else>
          <div class="sst-story-header">
            <div class="sst-story-badge">STORY EVENT</div>
            <h3 class="sst-event-name">{{ result.result.name }}</h3>
            <p class="sst-event-desc">{{ result.result.description }}</p>
          </div>

          <div v-if="rewardLines.length" class="sst-rewards sst-rewards--compact">
            <div
              v-for="item in rewardLines"
              :key="item.label"
              :class="['sst-reward-row', { 'is-highlight': item.highlight }]"
            >
              <span class="sst-reward-icon">{{ item.icon }}</span>
              <span class="sst-reward-label">{{ item.label }}</span>
              <span class="sst-reward-amount">{{ item.amount }}</span>
            </div>
          </div>

          <div class="sst-choices">
            <p class="sst-choices-hint">做出你的选择：</p>
            <button
              v-for="(c, i) in result.result.choices"
              :key="i"
              class="sst-choice-btn"
              :disabled="choosing"
              @click="selectChoice(i)"
            >{{ c.label }}</button>
          </div>

          <div class="sst-actions">
            <button class="sst-btn sst-btn-skip" @click="close">稍后再选</button>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* ── 遮罩 ── */
.sst-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(4, 8, 18, 0.75);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: sstFadeIn 0.2s ease;
}
@keyframes sstFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* ── 卡片 ── */
.sst-card {
  background: linear-gradient(180deg, rgba(13, 20, 48, 0.98), rgba(7, 11, 26, 0.99));
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  padding: 32px 36px;
  max-width: 520px;
  width: calc(100% - 40px);
  max-height: 88vh;
  overflow-y: auto;
  box-shadow: 0 16px 64px rgba(0, 0, 0, 0.7), 0 0 0 1px rgba(74, 143, 231, 0.06);
  animation: sstSlideIn 0.4s var(--ease-out-expo);
}

@keyframes sstSlideIn {
  from { transform: translateY(24px) scale(0.94); opacity: 0; }
  to { transform: translateY(0) scale(1); opacity: 1; }
}

/* ── 评分 ── */
.sst-grade-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 20px;
}

.sst-grade {
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid;
  border-radius: 50%;
  font-size: 36px;
  font-weight: 900;
  letter-spacing: 2px;
  animation: sstGradePop 0.5s var(--ease-out-back);
}

@keyframes sstGradePop {
  from { transform: scale(0) rotate(-10deg); opacity: 0; }
  to { transform: scale(1) rotate(0deg); opacity: 1; }
}

.sst-grade-label {
  margin-top: 8px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 3px;
}

/* ── 事件内容 ── */
.sst-event {
  margin-bottom: 16px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  border-left: 2px solid var(--color-system);
  background: rgba(74, 143, 231, 0.04);
}

.sst-event-name {
  color: #d0d8f0;
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 6px;
}

.sst-event-desc {
  color: #8898b8;
  font-size: 13px;
  line-height: 1.7;
  margin: 0;
}

.sst-story-header {
  margin-bottom: 18px;
}

.sst-story-badge {
  display: inline-block;
  padding: 4px 12px;
  border: 1px solid var(--color-purple);
  border-radius: 4px;
  color: var(--color-purple);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
  margin-bottom: 12px;
}

/* ── 奖励明细 ── */
.sst-rewards {
  margin-bottom: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.sst-rewards--compact {
  margin-bottom: 16px;
}

.sst-rewards-title {
  padding: 8px 14px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--color-system-bright);
  background: rgba(74, 143, 231, 0.06);
  border-bottom: 1px solid var(--color-border);
  text-transform: uppercase;
}

.sst-reward-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(26, 38, 80, 0.3);
  transition: background 0.15s;
}

.sst-reward-row:hover {
  background: rgba(74, 143, 231, 0.03);
}

.sst-reward-row:last-child {
  border-bottom: none;
}

.sst-reward-row.is-highlight {
  background: rgba(106, 175, 255, 0.04);
}

.sst-reward-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.sst-reward-label {
  flex: 1;
  font-size: 13px;
  color: var(--color-text-dim);
}

.sst-reward-amount {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-star);
}

/* ── 星座评论 ── */
.sst-const-quote {
  margin: 0 0 18px;
  padding-left: 24px;
  font-size: 13px;
  font-style: italic;
  color: #9088b8;
  line-height: 1.6;
}

/* ── 后果 ── */
.sst-consequence {
  margin-bottom: 18px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: rgba(7, 11, 26, 0.4);
}

.sst-cons-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}

.sst-cons-label {
  font-size: 11px;
  color: var(--color-muted);
  letter-spacing: 1px;
}

.sst-cons-choice {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-system-bright);
}

.sst-cons-text {
  color: #a0b0c0;
  font-size: 13px;
  line-height: 1.7;
  margin: 0;
}

.sst-unlock {
  font-size: 12px;
  color: var(--color-spirit);
  margin-top: 8px;
}

/* ── 故事耗尽 ── */
.sst-exhausted {
  padding: 8px 14px;
  border: 1px solid rgba(106, 175, 255, 0.25);
  border-radius: 6px;
  background: rgba(106, 175, 255, 0.04);
  color: var(--color-star);
  font-size: 12px;
  margin-bottom: 16px;
  line-height: 1.5;
}

/* ── 剧情屏蔽 ── */
.sst-blocked {
  padding: 10px 14px;
  border: 1px solid rgba(240, 180, 80, 0.3);
  border-radius: 6px;
  background: rgba(240, 180, 80, 0.05);
  color: #e0c070;
  font-size: 12px;
  margin-bottom: 16px;
  line-height: 1.6;
}

/* ── 按钮 ── */
.sst-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.sst-btn {
  padding: 12px 40px;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid;
  transition: all 0.2s;
  letter-spacing: 2px;
}

.sst-btn-close {
  border-color: var(--color-system-border);
  background: var(--color-system-bg);
  color: var(--color-system-bright);
}

.sst-btn-close:hover {
  background: rgba(74, 143, 231, 0.18);
  border-color: var(--color-system);
  box-shadow: var(--glow-system);
  transform: translateY(-1px);
}

.sst-btn-skip {
  border-color: var(--color-border);
  background: transparent;
  color: var(--color-muted);
}

.sst-btn-skip:hover {
  border-color: var(--color-border-bright);
  color: var(--color-text-dim);
}

/* ── 选项按钮 ── */
.sst-choices {
  margin-bottom: 20px;
}

.sst-choices-hint {
  color: var(--color-text-dim);
  font-size: 13px;
  margin: 0 0 10px;
}

.sst-choice-btn {
  display: block;
  width: 100%;
  padding: 13px 18px;
  margin-bottom: 8px;
  border: 1px solid var(--color-system-border);
  border-radius: 8px;
  background: rgba(74, 143, 231, 0.06);
  color: var(--color-system-bright);
  font-size: 14px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
  font-weight: 500;
}

.sst-choice-btn:hover:not(:disabled) {
  background: rgba(74, 143, 231, 0.14);
  border-color: var(--color-system);
  box-shadow: 0 0 12px rgba(74, 143, 231, 0.1);
  transform: translateX(4px);
}

.sst-choice-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ── 战斗模式 ── */
.sst-battle-header {
  margin-bottom: 20px;
}

.sst-battle-badge {
  display: inline-block;
  padding: 4px 12px;
  border: 1px solid #e07070;
  border-radius: 4px;
  color: #e07070;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
  margin-bottom: 12px;
}

.sst-battle-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 16px;
  margin-bottom: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: rgba(7, 11, 26, 0.4);
}

.sst-battle-monster-icon {
  font-size: 48px;
  animation: sstFloat 2s ease-in-out infinite;
}

@keyframes sstFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.sst-battle-hint {
  color: #8898b8;
  font-size: 13px;
  margin: 0;
}

.sst-battle-actions {
  gap: 8px;
}

.sst-btn-fight {
  border-color: #e07070;
  background: rgba(200, 80, 80, 0.12);
  color: #e07070;
}

.sst-btn-fight:hover:not(:disabled) {
  background: rgba(200, 80, 80, 0.22);
  box-shadow: 0 0 16px rgba(200, 80, 80, 0.2);
  transform: translateY(-1px);
}

.sst-btn-flee {
  border-color: var(--color-border);
  background: transparent;
  color: var(--color-muted);
}

.sst-btn-flee:hover:not(:disabled) {
  border-color: var(--color-border-bright);
  color: var(--color-text-dim);
}

.sst-battle-fighting {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 32px 16px;
}

.sst-battle-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--color-border);
  border-top-color: #e07070;
  border-radius: 50%;
  animation: sstSpin 0.8s linear infinite;
}

@keyframes sstSpin {
  to { transform: rotate(360deg); }
}

.sst-battle-status {
  color: #8898b8;
  font-size: 14px;
  margin: 0;
}

.sst-battle-outcome {
  text-align: center;
  font-size: 20px;
  font-weight: 900;
  margin-bottom: 16px;
  letter-spacing: 2px;
}

.sst-battle-outcome.win { color: #5ec49e; }
.sst-battle-outcome.lose { color: #e07070; }
.sst-battle-outcome.escape { color: #a0a0c0; }
.sst-battle-outcome.draw { color: #c0a0f0; }

.sst-battle-stats {
  margin-bottom: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.sst-battle-stat-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 14px;
  font-size: 13px;
  border-bottom: 1px solid rgba(26, 38, 80, 0.3);
}

.sst-battle-stat-row:last-child {
  border-bottom: none;
}

.sst-battle-stat-row span:first-child {
  color: var(--color-muted);
}

.sst-battle-stat-row span:last-child {
  color: var(--color-text-dim);
  font-weight: 600;
}

/* 战前属性对比 */
.sst-battle-vs {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 10px;
  margin-bottom: 12px;
}
.sst-battle-side {
  background: rgba(0,0,0,0.3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px;
}
.sst-battle-side-title {
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 8px;
  color: var(--color-star);
}
.sst-battle-side-stat {
  font-size: 12px;
  color: var(--color-text-dim);
  line-height: 1.8;
}
.sst-battle-side-stat.name { color: var(--color-danger); font-weight: 600; }
.sst-battle-side-stat.desc { color: #8898b8; font-size: 11px; line-height: 1.5; }
.sst-battle-vs-divider {
  display: flex; align-items: center;
  font-weight: 900; font-size: 16px;
  color: var(--color-danger);
}

/* 战斗过程 */
.sst-battle-rounds {
  margin-bottom: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px;
  max-height: 240px;
  overflow-y: auto;
}
.sst-battle-rounds-title {
  font-weight: 700; font-size: 13px;
  color: var(--color-star); margin-bottom: 10px;
}
.sst-battle-round {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(26,38,80,0.3);
}
.sst-battle-round:last-child { border-bottom: none; margin-bottom: 0; }
.sst-battle-round-num {
  font-size: 11px; font-weight: 700;
  color: var(--color-system-bright); margin-bottom: 4px;
}
.sst-battle-action {
  font-size: 12px; color: var(--color-text-dim);
  padding: 2px 0; display: flex; gap: 8px;
}
.sst-battle-action-who {
  color: var(--color-muted); min-width: 48px; flex-shrink: 0;
}
.sst-battle-action-what b { color: var(--color-danger); }
.sst-battle-crit {
  color: #ffaa00; font-weight: 700; margin-left: 4px;
}
</style>
