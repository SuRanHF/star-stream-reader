import { asArray, asRecord, pickText } from './panelData';
import { describeStoryFlagEffects } from './storyFlags';

export type StoryRecapSource = '地点故事' | 'NPC 残影';

export interface StoryRecapEntry {
  id: string;
  source: StoryRecapSource;
  location: string;
  title: string;
  choice: string;
  consequence: string;
  effects: string[];
  time: string;
}

function finiteNumber(value: unknown): number | undefined {
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : undefined;
}

function formatSignedAmount(value: number): string {
  const amount = Number.isInteger(value) ? String(Math.abs(value)) : String(Number(Math.abs(value).toFixed(2)));
  return `${value > 0 ? '+' : '−'}${amount}`;
}

function stringList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
      .map((item) => item.trim())
    : [];
}

/** Summarize only versioned results captured after server-side effects were applied. */
function summarizeAppliedOutcomes(value: unknown): string[] {
  const outcome = asRecord(value);
  if (finiteNumber(outcome.snapshot_version) !== 2) return [];

  const effects: string[] = [];
  const rewards = asRecord(outcome.applied_rewards);
  const resources: Array<[string, string]> = [
    ['coins', '星币'],
    ['storyFragments', '故事碎片'],
    ['exp', '经验'],
    ['channelHeat', '频道热度'],
    ['worldLineShift', '世界线偏移'],
  ];

  for (const [key, label] of resources) {
    const amount = finiteNumber(rewards[key]);
    if (amount !== undefined && amount !== 0) effects.push(`${label} ${formatSignedAmount(amount)}`);
  }

  for (const item of asArray(rewards.items)) {
    const quantity = finiteNumber(item.quantity);
    if (quantity === undefined || quantity <= 0) continue;
    const name = pickText(item, ['itemName', 'item_name'], '');
    effects.push(`获得物品${name ? `「${name}」` : ''} ×${quantity}`);
  }

  for (const equipment of asArray(rewards.equipment)) {
    const name = pickText(equipment, ['equipmentName', 'equipment_name'], '');
    effects.push(`获得装备${name ? `「${name}」` : ''}`);
  }

  const risk = asRecord(outcome.applied_risks);
  const damage = finiteNumber(risk.damage);
  if (damage !== undefined && damage > 0) {
    const before = finiteNumber(risk.hpBefore);
    const after = finiteNumber(risk.hpAfter);
    effects.push(before !== undefined && after !== undefined
      ? `受到伤害 ${damage}（生命 ${before}→${after}）`
      : `受到伤害 ${damage}`);
  }

  const locations = stringList(outcome.unlocked_locations);
  if (locations.length) effects.push(`解锁地点：${locations.join('、')}`);

  const unlockedEvents = stringList(outcome.unlock_events);
  if (unlockedEvents.length) effects.push(`开启后续剧情 ×${unlockedEvents.length}`);

  effects.push(...describeStoryFlagEffects(outcome.story_flags_updated));

  if (outcome.pending_battle === true) effects.push('触发战斗遭遇');
  return effects;
}

/** Merge the two personal narrative ledgers into one chronological recap. */
export function buildStoryRecapEntries(storyLog: unknown, npcEncounters: unknown): StoryRecapEntry[] {
  const locationStories = asArray(storyLog).flatMap((group, groupIndex) => {
    const locationKey = pickText(group, ['location_key', 'locationKey'], `location-${groupIndex}`);
    const location = pickText(group, ['location_name', 'locationName', 'location_key', 'locationKey'], '未知地点');
    return asArray(group.stories).map((story, storyIndex) => {
      const eventKey = pickText(story, ['event_key', 'eventKey', 'id'], `${locationKey}-${storyIndex}`);
      return {
        id: `location:${locationKey}:${eventKey}:${storyIndex}`,
        source: '地点故事' as const,
        location,
        title: pickText(story, ['event_name', 'eventName', 'event_key', 'eventKey'], '未命名事件'),
        choice: pickText(story, ['choice_label', 'choiceLabel'], '未选择'),
        consequence: pickText(story, ['consequence_text', 'consequenceText'], ''),
        effects: summarizeAppliedOutcomes(story.applied_outcomes),
        time: pickText(story, ['created_at', 'createdAt'], ''),
      };
    });
  });

  const ghostEncounters = asArray(npcEncounters)
    .filter((encounter) => pickText(encounter, ['status'], '') !== '接口错误')
    .map((encounter, index) => {
      const ghostKey = pickText(encounter, ['ghostKey', 'ghost_key'], `ghost-${index}`);
      const time = pickText(encounter, ['createdAt', 'created_at'], '');
      const encounterId = pickText(encounter, ['id'], `${ghostKey}-${time || index}`);
      const ghostName = pickText(encounter, ['ghostName', 'ghost_name'], ghostKey);
      return {
        id: `ghost:${encounterId}`,
        source: 'NPC 残影' as const,
        location: ghostName,
        title: `与${ghostName}的对话`,
        choice: pickText(encounter, ['choiceMade', 'choice_made'], '未记录你的选择'),
        consequence: pickText(encounter, ['outcome'], ''),
        effects: [],
        time,
      };
    });

  return [...locationStories, ...ghostEncounters].sort((left, right) => {
    const leftTime = Date.parse(left.time);
    const rightTime = Date.parse(right.time);
    const normalizedLeft = Number.isFinite(leftTime) ? leftTime : Number.MAX_SAFE_INTEGER;
    const normalizedRight = Number.isFinite(rightTime) ? rightTime : Number.MAX_SAFE_INTEGER;
    return normalizedLeft - normalizedRight;
  });
}

export function hasStoryRecapLoadError(npcEncounters: unknown): boolean {
  return asArray(npcEncounters).some((encounter) => pickText(asRecord(encounter), ['status'], '') === '接口错误');
}
