<script setup lang="ts">
import { ref, computed, watch, shallowRef, nextTick } from 'vue';
import { useUiStore } from '@/stores/uiStore';
import { useGameStore } from '@/stores/gameStore';
import { exploreApi, type Choice, type ExploreResult, type ChoiceResult } from '@/api/exploreApi';
import { ApiHttpError } from '@/api/http';
import { clearPendingCombatRequestKey, getPendingCombatRequestKey } from '@/utils/pendingCombatRequestKey';
import {
  clearPendingCombatAction,
  getPendingCombatAction,
  setPendingCombatAction,
  type CombatAction,
} from '@/utils/pendingCombatAction';
import { gameApi } from '@/api/gameApi';
import { combatApi, type CombatResolveResponse, type CombatResult } from '@/api/combatApi';
import {
  clearPendingStoryChoiceRequest,
  getOrCreatePendingStoryChoiceRequest,
  getPendingStoryChoiceRequest,
} from '@/utils/storyChoiceRequestKey';
import {
  choiceSummaryMessage,
  combatSummaryMessage,
  formatChoiceOutcomeLogs,
  formatCombatLogEntries,
} from '@/utils/gameLog';

const ui = useUiStore();
const game = useGameStore();

const props = defineProps<{
  visible: boolean;
  result: ExploreResult | null;
}>();

const emit = defineEmits<{
  close: [];
  choiceMade: [eventKey: string, pendingBattle?: ExploreResult];
  battleResolved: [encounterKey: string, acknowledged: boolean];
}>();

const choosing = ref(false);
const pendingChoiceIndex = ref<number | null>(null);
const COMBAT_ACTION_WATCHDOG_MS = 12_000;

function asResourceInteger(value: unknown): number {
  if (typeof value !== 'number' && typeof value !== 'string') return 0;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.trunc(parsed) : 0;
}

function choiceCosts(choice: Choice): { coins: number; storyFragments: number } {
  const rewards = choice.rewards_override || {};
  return {
    coins: Math.max(0, -asResourceInteger(rewards.coins)),
    storyFragments: Math.max(0, -asResourceInteger(rewards.storyFragments)),
  };
}

function choiceCostLabel(choice: Choice): string {
  const costs = choiceCosts(choice);
  const items = [
    costs.coins > 0 ? `${costs.coins} 星币` : '',
    costs.storyFragments > 0 ? `${costs.storyFragments} 故事碎片` : '',
  ].filter(Boolean);
  return items.length ? `消耗：${items.join('、')}` : '';
}

function choiceRiskLabel(choice: Choice): string {
  const risk = choice.risks;
  if (!risk) return '';
  const parts: string[] = [];
  const damagePercent = Number(risk.damagePercent);
  if (Number.isFinite(damagePercent) && damagePercent > 0) {
    parts.push(`约损失${Math.round(damagePercent)}%最大生命`);
  }
  if (risk.monsterKey) parts.push(`${risk.battleName || '触发战斗'}（可撤退）`);
  return parts.length ? `风险：${parts.join('；')}` : '';
}

function choiceImpactLabel(choice: Choice): string {
  const impacts: string[] = [];
  const hasStoryFlags = Object.keys(choice.set_story_flags || {}).length > 0;
  if (hasStoryFlags || (choice.unlock_events?.length || 0) > 0) impacts.push('个人后续剧情');
  if ((choice.unlock_locations?.length || 0) > 0) impacts.push('探索地点');
  if (Object.keys(choice.title_bias || {}).length > 0) impacts.push('称号判定倾向');

  const hasPositiveReward = Object.entries(choice.rewards_override || {}).some(([key, value]) => {
    if (key === 'items' || key === 'equipment') return Array.isArray(value) && value.length > 0;
    const amount = asResourceInteger(value);
    return amount > 0;
  });
  if (hasPositiveReward) impacts.push('本次资源结算');

  return impacts.length ? `影响范围：${impacts.join('、')}` : '';
}

function choiceShortageLabel(choice: Choice, index: number): string {
  if (pendingChoiceIndex.value === index) return '';

  const costs = choiceCosts(choice);
  const coins = asResourceInteger(game.player?.coins);
  const storyFragments = asResourceInteger(
    game.player?.storyFragments ?? game.player?.story_fragments,
  );
  const shortfalls = [
    coins < costs.coins ? `${costs.coins - coins} 星币` : '',
    storyFragments < costs.storyFragments
      ? `${costs.storyFragments - storyFragments} 故事碎片`
      : '',
  ].filter(Boolean);
  return shortfalls.length ? `余额不足，还差 ${shortfalls.join('、')}` : '';
}

function canAffordChoice(choice: Choice): boolean {
  const costs = choiceCosts(choice);
  return asResourceInteger(game.player?.coins) >= costs.coins
    && asResourceInteger(game.player?.storyFragments ?? game.player?.story_fragments)
      >= costs.storyFragments;
}

function isChoiceDisabled(choice: Choice, index: number): boolean {
  if (choosing.value) return true;
  if (pendingChoiceIndex.value !== null) return pendingChoiceIndex.value !== index;
  return !canAffordChoice(choice);
}

watch(
  () => [
    props.visible,
    props.result?.result_type,
    props.result?.result?.event_key,
    props.result?.story_instance_key,
    Number(game.player?.id || game.player?.playerId || 0),
  ] as const,
  ([visible, resultType, eventKey, storyInstanceKey, playerId]) => {
    if (!visible || resultType !== 'story' || !eventKey || !playerId) {
      pendingChoiceIndex.value = null;
      return;
    }
    pendingChoiceIndex.value = getPendingStoryChoiceRequest(
      playerId, eventKey, storyInstanceKey,
    )?.choiceIndex ?? null;
  },
  { immediate: true },
);

function latestPersistentLogId(): number {
  const logs = Array.isArray(game.recentLogs) ? game.recentLogs : [];
  return Math.max(0, ...logs
    .map((entry) => Number(entry.id))
    .filter((value) => Number.isFinite(value) && value > 0));
}

function hasPersistedResultLog(message: string, kind: string, afterId: number): boolean {
  const logs = Array.isArray(game.recentLogs) ? game.recentLogs : [];
  return logs.some((entry) =>
    Number(entry.id) > afterId
    && entry.message === message
    && entry.metadata?.kind === kind,
  );
}

// ── 战斗模式状态 ──
const battlePhase = ref<'idle' | 'fighting' | 'settling'>('idle');
const battling = ref(false);
const battleRequestKey = ref('');
const battleActionUnconfirmed = ref<CombatAction | null>(null);

function withCombatActionWatchdog<T>(request: Promise<T>, controller: AbortController): Promise<T> {
  let timeoutId: number | undefined;
  const watchdog = new Promise<never>((_resolve, reject) => {
    timeoutId = window.setTimeout(() => {
      controller.abort();
      reject(new Error('战斗请求超过等待时限，服务端尚未确认结果'));
    }, COMBAT_ACTION_WATCHDOG_MS);
  });

  return Promise.race([request, watchdog]).finally(() => {
    if (timeoutId !== undefined) window.clearTimeout(timeoutId);
  });
}

function isCombatResponseUnconfirmed(error: unknown, signal: AbortSignal): boolean {
  if (signal.aborted) return true;
  if (error instanceof ApiHttpError && (error.status === 408 || error.status >= 500)) return true;
  const message = error instanceof Error ? error.message : '';
  return /timeout|timed out|network error|network request failed|网络|连接中断|请求超时/i.test(message);
}

watch(
  () => [props.visible, props.result] as const,
  ([visible, result], previous) => {
    const [wasVisible, previousResult] = previous || [false, null];
    if (!visible) {
      battleRequestKey.value = '';
      battleActionUnconfirmed.value = null;
      return;
    }
    if (result?.result_type === 'battle'
        && (!wasVisible || result !== previousResult || !battleRequestKey.value)) {
      const playerId = Number(game.player?.id || game.player?.playerId || 0);
      const requestKey = playerId ? getPendingCombatRequestKey(playerId, result) : '';
      battleRequestKey.value = requestKey;
      battleActionUnconfirmed.value = playerId && requestKey
        ? getPendingCombatAction(playerId, requestKey)
        : null;
    }
  },
  { immediate: true },
);

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

// 遭遇卡片打开时把焦点放到「迎战」，关闭后还给打开前的元素，方便键盘操作。
let focusBeforeEncounter: HTMLElement | null = null;
watch(() => Boolean(props.visible && battleMode.value), (open) => {
  if (open) {
    focusBeforeEncounter = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    void nextTick(() => document.querySelector<HTMLButtonElement>('.sst-card.is-encounter .enc-btn-fight:not(:disabled)')?.focus());
  } else if (focusBeforeEncounter?.isConnected) {
    focusBeforeEncounter.focus();
    focusBeforeEncounter = null;
  }
});

const RARITY_LABELS: Record<string, string> = { common: '普通', uncommon: '稀有', rare: '罕见', epic: '史诗', legendary: '传说' };
const monsterRarityLabel = computed(() => {
  const rarity = monsterData.value?.rarity || '';
  return RARITY_LABELS[rarity] || rarity;
});

// 玩家属性优先取 stats（服务端权威结构），旧数据退回扁平字段；取不到时为 null，界面显示「—」而不是误导性的 0。
function playerStat(key: 'hp' | 'maxHp' | 'attack' | 'defense' | 'speed'): number | null {
  const stats = (game.player?.stats || {}) as Record<string, unknown>;
  const raw = stats[key] ?? (game.player as Record<string, unknown> | null)?.[key];
  if (raw === undefined || raw === null || raw === '') return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

function showStat(value: number | null) {
  return value ?? '—';
}

// 属性条：怪物数值相对「怪物 + 你」的占比，一眼看出谁更强；你的数值缺失时不画对比。
const monsterStatRows = computed(() => {
  const m = monsterData.value;
  if (!m) return [];
  return ([
    ['生命', m.hp, playerStat('maxHp')],
    ['攻击', m.attack, playerStat('attack')],
    ['防御', m.defense, playerStat('defense')],
    ['速度', m.speed, playerStat('speed')],
  ] as const).map(([label, value, mine]) => ({
    label,
    value,
    fill: mine !== null && value + mine > 0 ? Math.round((value / (value + mine)) * 100) : 0,
  }));
});
const monsterDesc = computed(() =>
  props.result?.result?.description || ''
);

function emitClose() {
  battlePhase.value = 'idle';
  emit('close');
}

function close() {
  if (battling.value || choosing.value) return;
  emitClose();
}

function refreshBootstrapInBackground() {
  void gameApi.getBootstrap()
    .then((bootstrap) => game.applyBootstrap(bootstrap))
    .catch(() => {
      // Result feedback must not depend on the follow-up state refresh.
    });
}

function acknowledgeBattleEncounter() {
  const result = props.result;
  if (!result || result.result_type !== 'battle') return;
  const playerId = Number(game.player?.id || game.player?.playerId || 0);
  const requestKey = battleRequestKey.value;
  const encounterKey = result.request_key || result.result.event_key;
  if (playerId && result.request_key) {
    void exploreApi.acknowledgePendingBattle(playerId, result.request_key).catch(() => {
      // The committed battle receipt also suppresses recovery if this acknowledgement is lost.
    });
  }
  if (playerId && requestKey) clearPendingCombatRequestKey(playerId, result, requestKey);
  emit('battleResolved', encounterKey, true);
}

function clearPendingCombatActionForCurrentEncounter() {
  const playerId = Number(game.player?.id || game.player?.playerId || 0);
  if (playerId && battleRequestKey.value) {
    clearPendingCombatAction(playerId, battleRequestKey.value);
  }
  battleActionUnconfirmed.value = null;
}

async function finishCombat(result: CombatResult, afterLogId: number) {
  battlePhase.value = 'settling';
  clearPendingCombatActionForCurrentEncounter();
  refreshBootstrapInBackground();
  const summary = combatSummaryMessage(result);
  if (!hasPersistedResultLog(summary, 'combat_result', afterLogId)) {
    game.replaceTransientActionLogs(
      battleRequestKey.value,
      formatCombatLogEntries(result, game.inventorySummary),
    );
  }
  acknowledgeBattleEncounter();
  emitClose();
}

async function finishFlee(response: CombatResolveResponse, afterLogId: number) {
  battlePhase.value = 'settling';
  clearPendingCombatActionForCurrentEncounter();
  refreshBootstrapInBackground();
  const message = response.message?.trim() || `你从${monsterName.value}面前撤退了。`;
  if (!hasPersistedResultLog(message, 'combat_result', afterLogId)) {
    game.replaceTransientActionLogs(battleRequestKey.value, [{ message, type: 'battle' }]);
  }
  acknowledgeBattleEncounter();
  emitClose();
}

async function startBattle() {
  if (battling.value || battleActionUnconfirmed.value === 'flee') return;
  const id = Number(game.player?.id || game.player?.playerId || 0);
  const monsterKey = props.result?.monster_key;
  const resultSnapshot = props.result;
  if (!id || !monsterKey || !resultSnapshot) return;
  const requestKey = battleRequestKey.value || getPendingCombatRequestKey(id, resultSnapshot);
  battleRequestKey.value = requestKey;
  setPendingCombatAction(id, requestKey, 'fight');
  const previousLogId = latestPersistentLogId();
  const controller = new AbortController();

  battlePhase.value = 'fighting';
  battling.value = true;
  try {
    const result = await withCombatActionWatchdog(
      combatApi.startCombat(id, monsterKey, requestKey, controller.signal),
      controller,
    );
    battleActionUnconfirmed.value = null;
    await finishCombat(result, previousLogId);
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '战斗启动失败';
    const responseUnconfirmed = isCombatResponseUnconfirmed(e, controller.signal);
    battlePhase.value = 'idle';
    battling.value = false;
    if (responseUnconfirmed) {
      controller.abort();
      battleActionUnconfirmed.value = 'fight';
      await ui.showAlert(
        '战斗结果待确认',
        '服务端可能已经结算，但响应没有及时返回。请重试“迎战”以复用原请求确认结果；本次确认前暂不能改为撤退。',
      );
    } else {
      clearPendingCombatActionForCurrentEncounter();
      await ui.showAlert('错误', msg);
    }
  } finally {
    battling.value = false;
  }
}

async function fleeBattle() {
  if (battling.value || battleActionUnconfirmed.value === 'fight') return;
  const id = Number(game.player?.id || game.player?.playerId || 0);
  const monsterKey = props.result?.monster_key;
  const resultSnapshot = props.result;
  if (!id || !monsterKey || !resultSnapshot) return;
  const requestKey = battleRequestKey.value || getPendingCombatRequestKey(id, resultSnapshot);
  battleRequestKey.value = requestKey;
  setPendingCombatAction(id, requestKey, 'flee');
  const previousLogId = latestPersistentLogId();
  const controller = new AbortController();

  battling.value = true;
  battlePhase.value = 'fighting';
  try {
    const response = await withCombatActionWatchdog(
      combatApi.resolveCombat(id, monsterKey, 'flee', requestKey, controller.signal),
      controller,
    );
    battleActionUnconfirmed.value = null;
    await finishFlee(response, previousLogId);
  } catch (e: unknown) {
    const responseUnconfirmed = isCombatResponseUnconfirmed(e, controller.signal);
    battlePhase.value = 'idle';
    battling.value = false;
    if (responseUnconfirmed) {
      controller.abort();
      battleActionUnconfirmed.value = 'flee';
      await ui.showAlert(
        '撤退结果待确认',
        '服务端可能已经处理撤退，但响应没有及时返回。请重试“撤退”以复用原请求确认结果；本次确认前暂不能改为迎战。',
      );
    } else {
      clearPendingCombatActionForCurrentEncounter();
      await ui.showAlert('逃跑失败', '无法逃跑，请尝试战斗。');
    }
  } finally {
    battling.value = false;
  }
}

async function selectChoice(index: number) {
  if (choosing.value) return;
  const id = Number(game.player?.id || game.player?.playerId || 0);
  const eventKey = props.result?.result?.event_key;
  if (!id || !eventKey) return;

  const storyInstanceKey = props.result?.story_instance_key;
  const existingRequest = getPendingStoryChoiceRequest(id, eventKey, storyInstanceKey);
  if (existingRequest && existingRequest.choiceIndex !== index) {
    pendingChoiceIndex.value = existingRequest.choiceIndex;
    await ui.showAlert('选择尚未确认', '请重试刚才的同一选项，系统会复用原请求并避免重复结算。');
    return;
  }
  const pendingRequest = getOrCreatePendingStoryChoiceRequest(
    id, eventKey, storyInstanceKey, index,
  );
  pendingChoiceIndex.value = pendingRequest.choiceIndex;
  if (pendingRequest.choiceIndex !== index) return;

  const previousLogId = latestPersistentLogId();
  choosing.value = true;
  try {
    let payload: ChoiceResult;
    try {
      payload = await exploreApi.makeChoice(
        id, eventKey, index, pendingRequest.requestKey, storyInstanceKey,
      );
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '选择处理失败';
      if (e instanceof ApiHttpError && e.status === 400) {
        clearPendingStoryChoiceRequest(
          id, eventKey, storyInstanceKey, pendingRequest.requestKey,
        );
        pendingChoiceIndex.value = null;
      }
      await ui.showAlert('错误', msg);
      return;
    }

    clearPendingStoryChoiceRequest(
      id, eventKey, storyInstanceKey, pendingRequest.requestKey,
    );
    pendingChoiceIndex.value = null;

    try {
      const bootstrap = await gameApi.getBootstrap();
      game.applyBootstrap(bootstrap);
    } catch {
      // 选择已由服务端处理，日志仍需落到当前会话。
    }
    const summary = choiceSummaryMessage(payload.choice_label);
    if (!hasPersistedResultLog(summary, 'story_choice', previousLogId)) {
      game.replaceTransientActionLogs(
        pendingRequest.requestKey,
        formatChoiceOutcomeLogs(
          payload,
          game.inventorySummary,
        ),
      );
    }
    // Close the story result before the parent replaces it with a pending battle.
    emitClose();
    emit('choiceMade', eventKey, payload.pending_battle);
  } finally {
    choosing.value = false;
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible && result" class="sst-backdrop" @click.self="close">
      <div :class="['sst-card', { 'is-encounter': battleMode }]" :role="battleMode ? 'dialog' : undefined" :aria-label="battleMode ? `遭遇 ${monsterName}` : undefined">

        <!-- ═══ 战斗模式：怪物遭遇（参考站遭遇卡片：标题 / 属性条 / 地点 / 主次操作 / 提示） ═══ -->
        <template v-if="battleMode">
          <header class="enc-head">
            <div class="enc-rule" aria-hidden="true"></div>
            <div class="enc-kicker">遭遇鬼怪</div>
            <div class="enc-head-row">
              <div class="enc-id">
                <h3 class="enc-name">{{ monsterName }}</h3>
                <p v-if="result.location_name" class="enc-realm">遭遇场景：{{ result.location_name }}</p>
              </div>
              <span v-if="monsterRarityLabel" :class="['enc-tier', `is-${monsterData?.rarity || 'common'}`]">{{ monsterRarityLabel }}</span>
            </div>
          </header>

          <!-- 战斗前：怪物属性与你的对比 -->
          <template v-if="battlePhase === 'idle'">
            <div class="enc-body">
              <p v-if="monsterDesc" class="enc-desc">{{ monsterDesc }}</p>

              <section v-if="monsterStatRows.length" class="enc-stats" aria-label="怪物属性">
                <div class="enc-card-label">怪物属性<span>条长 = 相对你的强弱</span></div>
                <div v-for="row in monsterStatRows" :key="row.label" class="enc-stat">
                  <span class="enc-stat-k">{{ row.label }}</span>
                  <div class="enc-stat-bar" role="presentation"><i :style="{ width: `${row.fill}%` }" :class="{ 'is-strong': row.fill > 50 }"></i></div>
                  <span class="enc-stat-v">{{ row.value }}</span>
                </div>
              </section>

              <section class="enc-vs" aria-label="生命对比">
                <div class="enc-vs-side">
                  <span>怪物生命</span>
                  <b class="is-monster">{{ monsterData?.hp ?? '—' }}</b>
                </div>
                <span class="enc-vs-mark">VS</span>
                <div class="enc-vs-side is-right">
                  <span>你的生命</span>
                  <b>{{ showStat(playerStat('hp')) }} / {{ showStat(playerStat('maxHp')) }}</b>
                </div>
              </section>

              <p class="enc-assess">你的属性：攻击 {{ showStat(playerStat('attack')) }} · 防御 {{ showStat(playerStat('defense')) }} · 速度 {{ showStat(playerStat('speed')) }}</p>

              <div class="enc-meta">
                <span>{{ result.location_name || '当前场景' }}</span>
                <span v-if="result?.stamina_cost_paid != null">本次探索已消耗体力 <b>{{ result.stamina_cost_paid }}</b> 点</span>
              </div>

              <p v-if="battleActionUnconfirmed" class="sst-battle-uncertain" role="status">
                {{ battleActionUnconfirmed === 'fight'
                  ? '上次迎战尚未确认。重试迎战会复用原请求；暂不要改选撤退。'
                  : '上次撤退尚未确认。重试撤退会复用原请求；暂不要改选迎战。' }}
              </p>

              <div class="enc-actions">
                <button
                  type="button"
                  class="enc-btn enc-btn-fight sst-btn-fight"
                  :disabled="battling || battleActionUnconfirmed === 'flee'"
                  @click="startBattle"
                >{{ battleActionUnconfirmed === 'fight' ? '重新确认战果' : '迎战' }}</button>
                <button
                  type="button"
                  class="enc-btn sst-btn-flee"
                  :disabled="battling || battleActionUnconfirmed === 'fight'"
                  @click="fleeBattle"
                >{{ battleActionUnconfirmed === 'flee' ? '重新确认撤退' : '撤退' }}</button>
                <button type="button" class="enc-btn enc-btn-secondary sst-btn-close" :disabled="battling" @click="close">稍后处理</button>
              </div>

              <p class="enc-foot">撤退必定成功，不会受伤，也没有战斗奖励；迎战或撤退都不会再次扣除体力。</p>
            </div>
          </template>

          <!-- 战斗中与结算同步中：结果细节统一写入中间日志 -->
          <div v-else class="sst-battle-fighting">
            <div class="sst-battle-spinner"></div>
            <p class="sst-battle-status">{{ battlePhase === 'settling' ? '战斗结束，正在整理记录...' : '战斗中...' }}</p>
          </div>
        </template>

        <!-- ═══ 故事模式：选项 ═══ -->
        <template v-else>
          <div class="sst-story-header">
            <div class="sst-story-badge">STORY EVENT</div>
            <h3 class="sst-event-name">{{ result.result.name }}</h3>
            <p class="sst-event-desc">{{ result.result.description }}</p>
            <aside
              v-if="result.result.personal_contexts?.length"
              class="sst-personal-context"
              aria-label="与本次故事相关的个人记忆"
            >
              <span class="sst-personal-context-label">你的记忆在此刻回响</span>
              <p v-for="context in result.result.personal_contexts" :key="context">{{ context }}</p>
            </aside>
          </div>

          <div class="sst-choices">
            <p v-if="pendingChoiceIndex !== null" class="sst-choices-hint" role="status">
              上次选择尚未确认，请重试同一选项；重试会复用原请求，避免重复结算。
            </p>
            <p v-else class="sst-choices-hint">做出你的选择：</p>
            <p
              v-if="result.result.event_type === 'story' || result.result.event_type === 'side_story'"
              class="sst-choice-scope"
              role="note"
            >
              该选择只记入你的个人经历，不会替其他玩家决定剧情。
            </p>
            <button
              v-for="(c, i) in result.result.choices || []"
              :key="i"
              class="sst-choice-btn"
              :disabled="isChoiceDisabled(c, i)"
              @click="selectChoice(i)"
            >
              <span>{{ c.label }}</span>
              <span v-if="choiceImpactLabel(c)" class="sst-choice-impact">
                {{ choiceImpactLabel(c) }}
              </span>
              <span v-if="choiceCostLabel(c)" class="sst-choice-cost">{{ choiceCostLabel(c) }}</span>
              <span v-if="choiceRiskLabel(c)" class="sst-choice-risk">{{ choiceRiskLabel(c) }}</span>
              <span v-if="choiceShortageLabel(c, i)" class="sst-choice-shortage">
                {{ choiceShortageLabel(c, i) }}
              </span>
            </button>
          </div>

          <div class="sst-actions">
            <button class="sst-btn sst-btn-skip" :disabled="choosing" @click="close">稍后再选</button>
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
  background: var(--color-overlay);
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
  background: linear-gradient(180deg, var(--color-panel-elevated), var(--color-void));
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  padding: 32px 36px;
  max-width: 520px;
  width: calc(100% - 40px);
  max-height: 88vh;
  overflow-y: auto;
  box-shadow: var(--shadow-float), 0 0 0 1px var(--color-star-glow);
  animation: sstSlideIn 0.4s var(--ease-out-expo);
}

/* ── 遭遇卡片（参考站 encounter：392px 宽、纸感底、分区小卡、两颗主按钮） ── */
.sst-card.is-encounter {
  max-width: 400px;
  padding: 20px 18px 16px;
  border-radius: var(--radius-lg, 12px);
  background: var(--lv-fidelity-void, var(--color-panel-elevated));
  box-shadow: 0 18px 48px color-mix(in srgb, #000 28%, transparent);
}

.enc-head {
  margin-bottom: 12px;
}

.enc-rule {
  width: 36px;
  height: 3px;
  margin-bottom: 10px;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--color-star), color-mix(in srgb, var(--color-star) 30%, transparent));
}

.enc-kicker {
  color: var(--color-star);
  font-family: var(--font-display, KaiTi, STKaiti, serif);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}

.enc-head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.enc-name {
  margin: 2px 0 0;
  color: var(--color-text-bright);
  font-size: 18px;
  font-weight: 700;
}

.enc-realm {
  margin: 4px 0 0;
  color: var(--color-muted);
  font-size: 12px;
}

.enc-tier {
  flex: 0 0 auto;
  margin-top: 22px;
  padding: 2px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-dim);
  font-size: 12px;
  font-weight: 600;
}

.enc-tier.is-uncommon { border-color: color-mix(in srgb, var(--color-spirit) 50%, transparent); color: var(--color-spirit); }
.enc-tier.is-rare { border-color: color-mix(in srgb, var(--color-system) 50%, transparent); color: var(--color-system); }
.enc-tier.is-epic,
.enc-tier.is-legendary { border-color: color-mix(in srgb, var(--color-star) 55%, transparent); color: var(--color-star); }

.enc-body {
  display: grid;
  gap: 10px;
}

.enc-desc,
.enc-stats,
.enc-vs {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  background: var(--lv-fidelity-panel, var(--color-panel));
}

.enc-desc {
  color: var(--color-text-dim);
  font-size: 13px;
  line-height: 1.6;
}

.enc-card-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--color-text-dim);
  font-size: 11px;
  font-weight: 600;
}

.enc-card-label span {
  color: var(--color-muted);
  font-weight: 400;
}

.enc-stat {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) 44px;
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.enc-stat-k {
  color: var(--color-muted);
  font-size: 12px;
}

.enc-stat-bar {
  height: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: color-mix(in srgb, var(--color-text) 10%, transparent);
}

.enc-stat-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: color-mix(in srgb, var(--color-spirit) 70%, transparent);
}

.enc-stat-bar i.is-strong {
  background: color-mix(in srgb, var(--color-danger, #a84842) 75%, transparent);
}

.enc-stat-v {
  color: var(--color-text);
  font-size: 12px;
  font-weight: 700;
  text-align: right;
}

.enc-vs {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
}

.enc-vs-side {
  display: grid;
  gap: 2px;
}

.enc-vs-side.is-right {
  text-align: right;
}

.enc-vs-side span {
  color: var(--color-muted);
  font-size: 11px;
}

.enc-vs-side b {
  color: var(--color-star);
  font-size: 18px;
}

.enc-vs-side b.is-monster {
  color: var(--color-danger, #a84842);
}

.enc-vs-mark {
  color: var(--color-muted);
  font-family: var(--font-display, KaiTi, serif);
  font-size: 14px;
  font-weight: 700;
}

.enc-assess {
  margin: 0;
  color: var(--color-text-dim);
  font-size: 12px;
}

.enc-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--color-muted);
  font-size: 12px;
}

.enc-meta b {
  color: var(--color-star);
}

.enc-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.sst-card.is-encounter .enc-btn {
  height: 40px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  background: transparent;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  cursor: pointer;
  transition: background 0.16s, border-color 0.16s;
}

.sst-card.is-encounter .enc-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--color-star) 50%, var(--color-border));
  background: color-mix(in srgb, var(--color-star) 8%, transparent);
}

.sst-card.is-encounter .enc-btn-fight {
  border-color: color-mix(in srgb, var(--color-danger, #a84842) 45%, var(--color-border));
  background: color-mix(in srgb, var(--color-danger, #a84842) 10%, transparent);
  color: var(--color-danger, #a84842);
}

.sst-card.is-encounter .enc-btn-fight:hover:not(:disabled) {
  border-color: var(--color-danger, #a84842);
  background: color-mix(in srgb, var(--color-danger, #a84842) 16%, transparent);
}

.sst-card.is-encounter .enc-btn-secondary {
  grid-column: 1 / -1;
  height: 34px;
  color: var(--color-text-dim);
  font-size: 13px;
  font-weight: 500;
}

.sst-card.is-encounter .enc-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.sst-card.is-encounter .enc-btn:focus-visible {
  outline: 2px solid var(--color-star);
  outline-offset: 2px;
}

.enc-foot {
  margin: 0;
  color: var(--color-muted);
  font-size: 11px;
  line-height: 1.5;
  text-align: center;
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
  background: var(--color-star-bg);
}

.sst-event-name {
  color: var(--color-text-dim);
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 6px;
}

.sst-event-desc {
  color: var(--color-text-dim);
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
  background: var(--color-system-bg);
  border-bottom: 1px solid var(--color-border);
  text-transform: uppercase;
}

.sst-reward-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-border);
  transition: background 0.15s;
}

.sst-reward-row:hover {
  background: var(--color-system-glow-soft);
}

.sst-reward-row:last-child {
  border-bottom: none;
}

.sst-reward-row.is-highlight {
  background: var(--color-star-bg);
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

/* ── 执行资源的下一步去向 ── */
.sst-next-steps {
  margin: 0 0 18px;
  border: 1px solid var(--color-system-border);
  border-radius: var(--radius-md);
  background: var(--color-log);
  overflow: hidden;
}

.sst-next-steps-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-system-bg);
}

.sst-next-steps-kicker {
  display: block;
  margin-bottom: 3px;
  color: var(--color-system);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1.6px;
}

.sst-next-steps-head h4 {
  margin: 0;
  color: var(--color-system-bright);
  font-size: 14px;
}

.sst-next-steps-head p {
  max-width: 48%;
  margin: 0;
  color: var(--color-text-dim);
  font-size: 11px;
  line-height: 1.5;
  text-align: right;
}

.sst-next-step-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 10px;
}

.sst-next-step-card {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  align-items: start;
  gap: 6px 9px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-panel-elevated);
}

.sst-next-step-icon {
  grid-row: span 2;
  font-size: 18px;
  line-height: 1.2;
}

.sst-next-step-copy {
  min-width: 0;
}

.sst-next-step-copy strong {
  color: var(--color-text);
  font-size: 12px;
}

.sst-next-step-copy p {
  margin: 3px 0 0;
  color: var(--color-text-dim);
  font-size: 11px;
  line-height: 1.5;
}

.sst-next-step-action,
.sst-next-steps-footer button {
  justify-self: start;
  padding: 3px 0;
  border: 0;
  background: transparent;
  color: var(--color-system-bright);
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.sst-next-step-action:hover,
.sst-next-steps-footer button:hover {
  color: var(--color-star-bright);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.sst-next-steps-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 14px;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-dim);
  font-size: 11px;
}

.sst-next-steps-footer button {
  flex-shrink: 0;
}

@media (max-width: 560px) {
  .sst-next-steps-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px;
  }

  .sst-next-steps-head p {
    max-width: none;
    text-align: left;
  }

  .sst-next-step-grid {
    grid-template-columns: 1fr;
  }

  .sst-next-steps-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}

/* ── 星座评论 ── */
.sst-const-quote {
  margin: 0 0 18px;
  padding-left: 24px;
  font-size: 13px;
  font-style: italic;
  color: var(--color-text-dim);
  line-height: 1.6;
}

/* ── 后果 ── */
.sst-consequence {
  margin-bottom: 18px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-log);
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
  color: var(--color-text-dim);
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
  border: 1px solid var(--color-star-border);
  border-radius: 6px;
  background: var(--color-star-bg);
  color: var(--color-star);
  font-size: 12px;
  margin-bottom: 16px;
  line-height: 1.5;
}

/* ── 剧情屏蔽 ── */
.sst-blocked {
  padding: 10px 14px;
  border: 1px solid var(--color-star-border);
  border-radius: 6px;
  background: var(--color-star-bg);
  color: var(--color-star-bright);
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
  background: var(--color-system-glow);
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

.sst-choice-scope {
  margin: -3px 0 12px;
  padding-left: 9px;
  border-left: 1px solid var(--color-system-border);
  color: var(--color-text-dim);
  font-size: 12px;
  line-height: 1.5;
}

.sst-personal-context {
  margin-top: 12px;
  padding: 10px 13px;
  border-left: 2px solid var(--color-star-bright);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: color-mix(in srgb, var(--color-star-bright) 7%, transparent);
}

.sst-personal-context-label {
  display: block;
  margin-bottom: 5px;
  color: var(--color-star-bright);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.sst-personal-context p {
  margin: 0;
  color: var(--color-text-dim);
  font-size: 12px;
  line-height: 1.65;
}

.sst-personal-context p + p {
  margin-top: 5px;
}

.sst-choice-btn {
  display: block;
  width: 100%;
  padding: 13px 18px;
  margin-bottom: 8px;
  border: 1px solid var(--color-system-border);
  border-radius: var(--radius-md);
  background: var(--color-system-bg);
  color: var(--color-system-bright);
  font-size: 14px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
  font-weight: 500;
}

.sst-choice-btn:hover:not(:disabled) {
  background: var(--color-system-glow);
  border-color: var(--color-system);
  box-shadow: var(--glow-system);
  transform: translateX(4px);
}

.sst-choice-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.sst-choice-cost,
.sst-choice-risk,
.sst-choice-shortage,
.sst-choice-impact {
  display: block;
  margin-top: 5px;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.4;
}

.sst-choice-cost {
  color: var(--color-text-dim);
}

.sst-choice-risk {
  color: var(--color-danger-bright);
}

.sst-choice-impact {
  padding-left: 8px;
  border-left: 1px solid var(--color-system-border);
  color: var(--color-text-dim);
}

.sst-choice-shortage {
  color: var(--color-danger-bright);
}

/* ── 战斗模式 ── */
.sst-battle-header {
  margin-bottom: 20px;
}

.sst-battle-origin {
  color: var(--color-purple);
  font-size: 12px;
  margin: 4px 0 0;
}

.sst-battle-badge {
  display: inline-block;
  padding: 4px 12px;
  border: 1px solid var(--color-danger);
  border-radius: 4px;
  color: var(--color-danger);
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
  background: var(--color-log);
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
  color: var(--color-text-dim);
  font-size: 13px;
  margin: 0;
}

.sst-battle-actions {
  gap: 8px;
}

.sst-btn-fight {
  border-color: var(--color-danger);
  background: var(--color-danger-glow);
  color: var(--color-danger);
}

.sst-btn-fight:hover:not(:disabled) {
  background: rgba(var(--color-danger-rgb), 0.22);
  box-shadow: var(--glow-danger);
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
  border-top-color: var(--color-danger);
  border-radius: 50%;
  animation: sstSpin 0.8s linear infinite;
}

@keyframes sstSpin {
  to { transform: rotate(360deg); }
}

.sst-battle-status {
  color: var(--color-text-dim);
  font-size: 14px;
  margin: 0;
}

.sst-battle-uncertain {
  margin: 0 0 14px;
  padding: 10px 12px;
  border: 1px solid var(--color-star-border);
  border-radius: var(--radius-md);
  background: var(--color-star-bg);
  color: var(--color-star-bright);
  font-size: 12px;
  line-height: 1.6;
}

.sst-battle-outcome {
  text-align: center;
  font-size: 20px;
  font-weight: 900;
  margin-bottom: 16px;
  letter-spacing: 2px;
}

.sst-battle-outcome.win { color: var(--color-spirit); }
.sst-battle-outcome.lose { color: var(--color-danger); }
.sst-battle-outcome.escape { color: var(--color-system); }
.sst-battle-outcome.draw { color: var(--color-purple); }

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
  background: var(--color-log);
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
.sst-battle-side-stat.desc { color: var(--color-text-dim); font-size: 11px; line-height: 1.5; }
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
  border-bottom: 1px solid var(--color-border);
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
  color: var(--color-star-bright); font-weight: 700; margin-left: 4px;
}
</style>
