import type { CombatResult } from '@/api/combatApi';
import type { ChoiceResult, ExploreResult } from '@/api/exploreApi';
import type { LogEntry } from '@/types/bootstrap';
import { describeStoryFlagEffects } from './storyFlags';

type RecordValue = Record<string, unknown>;
type NegativeResourceLabel = 'cost' | 'loss';

interface RewardLogOptions {
  negativeResourceLabel?: NegativeResourceLabel;
}

function asRecord(value: unknown): RecordValue {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as RecordValue
    : {};
}

function asText(value: unknown): string {
  return typeof value === 'string' || typeof value === 'number' ? String(value).trim() : '';
}

function log(message: string, type: string): LogEntry {
  return { message, type };
}

function amountText(value: number): string {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(Math.abs(value));
}

function titleName(value: unknown): string {
  if (typeof value === 'string') return value.trim();
  const title = asRecord(value);
  return asText(title.titleName || title.title_name || title.name || title.title || title.key);
}

function titleKey(value: unknown): string {
  const title = asRecord(value);
  return asText(title.titleKey || title.title_key || title.key);
}

function readDrop(
  value: unknown,
  inventory: ReadonlyArray<Record<string, unknown>>,
  sourceType: 'item' | 'equipment' | 'drop',
  index: number,
): { key: string; name: string; quantity: number } | null {
  const drop = asRecord(value);
  const equipmentKey = asText(drop.equipmentKey || drop.equipment_key);
  const itemKey = asText(drop.itemKey || drop.item_key || equipmentKey)
    || (typeof value === 'string' ? value.trim() : '');
  const isEquipment = sourceType === 'equipment' || Boolean(equipmentKey);
  const type = isEquipment ? 'equipment' : 'item';
  const catalogItem = inventory.find((entry) =>
    asText(entry.itemKey || entry.item_key || entry.equipmentKey || entry.equipment_key) === itemKey,
  );
  const knownName = asText(drop.itemName || drop.item_name || drop.equipmentName || drop.equipment_name || drop.name)
    || asText(catalogItem?.itemName || catalogItem?.item_name || catalogItem?.equipmentName
      || catalogItem?.equipment_name || catalogItem?.name);
  const name = knownName || (isEquipment ? '未知装备' : '未知物品');
  const rawQuantity = drop.quantity ?? drop.count;
  const quantity = rawQuantity == null ? 1 : Number(rawQuantity);
  if (!Number.isFinite(quantity) || quantity <= 0) return null;

  const identity = itemKey || (knownName ? name.toLocaleLowerCase('zh-CN') : `unknown-${index}`);
  return { key: `${type}:${identity}`, name, quantity };
}

export function formatRewardLogs(
  rewards: Record<string, unknown> | null | undefined,
  inventory: ReadonlyArray<Record<string, unknown>> = [],
  options: RewardLogOptions = {},
): LogEntry[] {
  const source = rewards || {};
  const entries: LogEntry[] = [];
  const resources: Array<[string, string]> = [
    ['exp', '经验值'],
    ['coins', '星币'],
    ['storyFragments', '故事碎片'],
    ['channelHeat', '频道热度'],
    ['worldLineShift', '世界线偏移'],
  ];

  for (const [key, label] of resources) {
    const value = source[key];
    if (typeof value !== 'number' && !(typeof value === 'string' && value.trim() !== '' && Number.isFinite(Number(value)))) continue;
    const amount = Number(value);
    if (!amount) continue;
    if (amount < 0 && (key === 'coins' || key === 'storyFragments')
        && options.negativeResourceLabel === 'cost') {
      entries.push(log('消耗' + label + ' ' + amountText(amount), 'cost'));
      continue;
    }
    if (key === 'worldLineShift') {
      entries.push(log(`世界线偏移 ${amount > 0 ? '+' : '-'}${amountText(amount)}`, 'reward'));
      continue;
    }
    entries.push(log(
      amount > 0 ? `获得${label} +${amountText(amount)}` : `损失${label} ${amountText(amount)}`,
      amount < 0 ? 'loss' : 'reward',
    ));
  }

  const dropGroups = new Map<string, { name: string; quantity: number }>();
  const dropSources: Array<{ values: unknown[]; type: 'item' | 'equipment' | 'drop' }> = [
    { values: Array.isArray(source.items) ? source.items : [], type: 'item' },
    { values: Array.isArray(source.equipment) ? source.equipment : [], type: 'equipment' },
    { values: Array.isArray(source.drops) ? source.drops : [], type: 'drop' },
  ];
  let dropIndex = 0;
  for (const dropSource of dropSources) {
    for (const value of dropSource.values) {
      const drop = readDrop(value, inventory, dropSource.type, dropIndex++);
      if (!drop) continue;
      const existing = dropGroups.get(drop.key);
      if (existing) existing.quantity += drop.quantity;
      else dropGroups.set(drop.key, { name: drop.name, quantity: drop.quantity });
    }
  }
  for (const drop of dropGroups.values()) {
    entries.push(log(
      `获得掉落：${drop.name}${drop.quantity > 1 ? ` ×${amountText(drop.quantity)}` : ''}`,
      'reward',
    ));
  }

  return entries;
}

export function isInteractiveExploreResult(result: ExploreResult | null | undefined): boolean {
  if (!result) return false;
  if (result.result_type === 'battle') return true;
  return result.result?.event_type === 'story'
    && Array.isArray(result.result.choices)
    && result.result.choices.length > 0;
}

export function formatExploreRewardLogs(
  result: ExploreResult,
  inventory: ReadonlyArray<Record<string, unknown>> = [],
): LogEntry[] {
  const entries = formatRewardLogs(result.rewards, inventory);
  for (const title of result.new_titles || []) {
    const name = titleName(title);
    if (name) entries.push(log(`获得称号：${name}`, 'reward'));
  }
  return entries;
}

export function formatExploreResultLogs(
  result: ExploreResult,
  inventory: ReadonlyArray<Record<string, unknown>> = [],
): LogEntry[] {
  const summaries = (result.new_logs || []).flatMap((value) => {
    const entry = asRecord(value);
    const message = asText(entry.message);
    return message ? [log(message, asText(entry.type) || 'exploration')] : [];
  });
  return [...summaries, ...formatExploreRewardLogs(result, inventory)];
}

export function choiceSummaryMessage(label: string): string {
  return `你选择了: ${label}`;
}

export function formatChoiceOutcomeLogs(
  choice: ChoiceResult,
  inventory: ReadonlyArray<Record<string, unknown>> = [],
): LogEntry[] {
  const entries: LogEntry[] = [log(`你选择了「${choice.choice_label}」`, 'story')];
  if (choice.consequence_text?.trim()) entries.push(log(choice.consequence_text.trim(), 'story'));
  entries.push(...describeStoryFlagEffects(choice.story_flags_updated)
    .map((effect) => log(effect, 'story')));

  const appliedRisk = choice.applied_risks || {};
  const damage = Number(appliedRisk.damage);
  if (Number.isFinite(damage) && damage > 0) {
    const hpBefore = Number(appliedRisk.hpBefore);
    const hpAfter = Number(appliedRisk.hpAfter);
    const hpTransition = Number.isFinite(hpBefore) && Number.isFinite(hpAfter)
      ? `（生命 ${hpBefore}→${hpAfter}）`
      : '';
    entries.push(log(`剧情风险造成 ${damage} 点伤害${hpTransition}；此次伤害不会致命。`, 'warning'));
  }
  if (choice.pending_battle?.result_type === 'battle') {
    const encounterName = choice.pending_battle.result?.name || '新的战斗遭遇';
    entries.push(log(`待处理战斗：${encounterName}，可迎战或撤退。`, 'battle'));
  }

  const unlockedLocations = (choice as ChoiceResult & { unlock_location_names?: string[] }).unlock_location_names
    || choice.unlock_locations;
  if (unlockedLocations?.length) entries.push(log(`解锁地点：${unlockedLocations.join('、')}`, 'explore'));
  if (choice.unlock_events?.length) entries.push(log(`发现新的故事线索（${choice.unlock_events.length}）`, 'explore'));

  entries.push(...formatRewardLogs(choice.rewards || {}, inventory, {
    negativeResourceLabel: 'cost',
  }));
  for (const title of choice.new_titles || []) {
    const name = titleName(title);
    if (name) entries.push(log(`获得称号：${name}`, 'reward'));
  }
  return entries;
}

function messageFromUnknown(value: unknown): string {
  if (typeof value === 'string') return value.trim();
  return asText(asRecord(value).message);
}

function combatSummary(result: CombatResult, monsterName: string): string {
  const rounds = Number(result.totalRounds || result.rounds?.length || 0);
  if (result.result === 'win') return `击败了 ${monsterName}（${rounds}回合）`;
  if (result.result === 'lose') return `被 ${monsterName} 击败了`;
  if (result.result === 'escape') return `从 ${monsterName} 面前撤退了`;
  return `与 ${monsterName} 的战斗结束`;
}

export function combatSummaryMessage(result: CombatResult): string {
  const persisted = (result.newLogs || []).map(messageFromUnknown).find(Boolean);
  return persisted || combatSummary(result, result.monster?.name || '未知目标');
}

export function formatCombatLogEntries(
  result: CombatResult,
  inventory: ReadonlyArray<Record<string, unknown>> = [],
): LogEntry[] {
  const monsterName = result.monster?.name || '未知目标';
  const entries: LogEntry[] = [];

  for (const round of result.rounds || []) {
    for (const actionValue of round.actions || []) {
      const action = asRecord(actionValue);
      const actor = asText(action.actor);
      const damage = Number(action.damage);
      const targetHp = Number(action.targetHpAfter);
      const critical = action.critical ? '（暴击）' : '';
      const note = asText(action.suppressionNote);
      const isMonster = actor === 'monster';
      const source = isMonster ? monsterName : '你';
      const target = isMonster ? '你' : monsterName;
      const damageText = Number.isFinite(damage) ? `${amountText(damage)}点伤害` : '造成伤害';
      const hpText = Number.isFinite(targetHp) ? `，${target}剩余生命 ${amountText(targetHp)}` : '';
      const suffix = [critical, note ? `（${note}）` : ''].filter(Boolean).join('');
      entries.push(log(`第${round.round}回合：${source}对${target}造成${damageText}${suffix}${hpText}。`, 'battle'));
    }
  }

  const resultLogs = (result.newLogs || []).map((entry) => {
    const record = asRecord(entry);
    const message = messageFromUnknown(entry);
    return message ? log(message, asText(record.type) || 'battle') : null;
  }).filter((entry): entry is LogEntry => entry !== null);
  entries.push(...(resultLogs.length ? resultLogs : [log(combatSummary(result, monsterName), 'battle')]));
  entries.push(...formatRewardLogs({
    ...(result.rewards as unknown as Record<string, unknown> || {}),
    drops: result.drops || [],
  }, inventory));
  return entries;
}

/** Turn durable server-side settlement metadata into readable rows under its summary log. */
export function formatPersistedLogDetails(
  entry: LogEntry,
  inventory: ReadonlyArray<Record<string, unknown>> = [],
  titleCatalog: ReadonlyArray<Record<string, unknown>> = [],
): LogEntry[] {
  const metadata = asRecord(entry.metadata);
  const titles = Array.isArray(metadata.newTitles) ? metadata.newTitles : [];

  if (metadata.kind === 'exploration_result') {
    const details = formatRewardLogs(asRecord(metadata.rewards), inventory);
    for (const title of titles) {
      const name = titleName(title);
      if (name) details.push(log(`获得称号：${name}`, 'reward'));
    }
    return details;
  }

  if (metadata.kind === 'story_choice') {
    const details: LogEntry[] = [];
    const consequence = asText(metadata.consequenceText);
    if (consequence) details.push(log(consequence, 'story'));
    details.push(...describeStoryFlagEffects(metadata.storyFlagsUpdated)
      .map((effect) => log(effect, 'story')));

    const locations = Array.isArray(metadata.unlockLocations)
      ? metadata.unlockLocations.map(asText).filter(Boolean)
      : [];
    if (locations.length) details.push(log(`解锁地点：${locations.join('、')}`, 'explore'));

    const unlockedEvents = Array.isArray(metadata.unlockEvents) ? metadata.unlockEvents : [];
    if (unlockedEvents.length) details.push(log(`发现新的故事线索（${unlockedEvents.length}）`, 'explore'));

    details.push(...formatRewardLogs(asRecord(metadata.rewards), inventory, {
      negativeResourceLabel: 'cost',
    }));
    for (const title of titles) {
      const name = titleName(title);
      if (name) details.push(log(`获得称号：${name}`, 'reward'));
    }
    return details;
  }

  if (metadata.kind === 'skill_unlock') {
    const costs = asRecord(metadata.costs);
    const coinsCost = Number(costs.coins ?? 0);
    const fragmentsCost = Number(costs.storyFragments ?? 0);
    return formatRewardLogs({
      coins: Number.isFinite(coinsCost) ? -Math.abs(coinsCost) : 0,
      storyFragments: Number.isFinite(fragmentsCost) ? -Math.abs(fragmentsCost) : 0,
    }, inventory, { negativeResourceLabel: 'cost' });
  }

  if (metadata.kind === 'quest_reward') {
    const rewards = asRecord(metadata.rewards);
    const details = formatRewardLogs(rewards, inventory);
    const cycleKey = asText(metadata.cycleKey);
    if (cycleKey && cycleKey !== 'none') details.unshift(log(`任务周期：${cycleKey}`, 'quest'));
    const unlockedTitleKey = asText(rewards.titleKey || rewards.title_key);
    if (unlockedTitleKey) {
      const unlockedTitle = titleCatalog.find((title) => titleKey(title) === unlockedTitleKey);
      details.push(log(`获得称号：${titleName(unlockedTitle) || unlockedTitleKey}`, 'reward'));
    }

    const messages = Array.isArray(metadata.messages) ? metadata.messages.map(asText).filter(Boolean) : [];
    for (const message of messages) {
      if (unlockedTitleKey && message.startsWith('获得称号')) continue;
      details.push(log(message, 'reward'));
    }
    return details;
  }

  if (metadata.kind === 'chapter_reward') {
    return formatRewardLogs(asRecord(metadata.rewards), inventory);
  }

  if (metadata.kind === 'quest_expired') {
    const details: LogEntry[] = [];
    const cycleKey = asText(metadata.cycleKey);
    if (cycleKey && cycleKey !== 'none') details.push(log(`任务周期：${cycleKey}`, 'quest'));
    const progress = Number(metadata.progress);
    const target = Number(metadata.targetValue);
    if (Number.isFinite(progress) && Number.isFinite(target) && target > 0) {
      details.push(log(`到期进度：${Math.max(0, progress)} / ${target}`, 'quest'));
    }
    return details;
  }

  if (metadata.kind === 'item_use') {
    const effects = asRecord(metadata.appliedEffects);
    const details: LogEntry[] = [];
    for (const [key, label] of [['hp', '生命'], ['stamina', '行动力']] as const) {
      const amount = Number(effects[key]);
      if (Number.isFinite(amount) && amount > 0) {
        details.push(log(`恢复${label} +${amountText(amount)}`, 'reward'));
      }
    }
    const resourceDetails = formatRewardLogs({
      storyFragments: effects.storyFragments,
      channelHeat: effects.channelHeat,
      worldLineShift: effects.worldLineShift,
    }, inventory);
    details.push(...resourceDetails);
    const abyssMark = Number(effects.abyssMark);
    if (Number.isFinite(abyssMark) && abyssMark > 0) {
      details.push(log(`获得深渊印记 +${amountText(abyssMark)}`, 'reward'));
    }
    return details;
  }

  if (metadata.kind === 'combat_result') {
    return formatCombatLogEntries(metadata as unknown as CombatResult, inventory)
      .filter((detail) => detail.message !== entry.message);
  }

  return [];
}
