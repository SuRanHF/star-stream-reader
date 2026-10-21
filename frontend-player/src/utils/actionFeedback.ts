import type { ApiRecord } from '@/types/api';
import { formatCount } from '@/utils/panelData';

function asRecord(value: unknown): ApiRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as ApiRecord : {};
}

function positiveAmount(value: unknown): number {
  const amount = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(amount) && amount > 0 ? amount : 0;
}

/** Turn the quest-claim response into a short toast; the persistent game log keeps the full detail. */
export function formatQuestClaimSuccess(result: unknown): string {
  const response = asRecord(result);
  const earned = asRecord(response.earned);
  const rewards: string[] = [];
  const resourceLabels: Array<[string, string]> = [
    ['coins', '星币'],
    ['storyFragments', '故事碎片'],
    ['exp', '经验值'],
    ['channelHeat', '频道热度'],
  ];

  for (const [key, label] of resourceLabels) {
    const amount = positiveAmount(earned[key]);
    if (amount > 0) rewards.push(`+${formatCount(amount)} ${label}`);
  }

  const items = Array.isArray(earned.items) ? earned.items : [];
  const itemCount = items.reduce((total, item) => total + positiveAmount(asRecord(item).quantity), 0);
  if (itemCount > 0) rewards.push(`物品 ×${formatCount(itemCount)}`);
  if (typeof earned.titleKey === 'string' && earned.titleKey.trim()) rewards.push('称号奖励');

  const title = typeof response.questTitle === 'string' ? response.questTitle.trim() : '';
  const questLabel = title ? `「${title}」` : '任务';
  return `已领取${questLabel}奖励${rewards.length ? `：${rewards.join('、')}` : ''}`;
}

export function formatItemUseSuccess(result: unknown): string {
  const response = asRecord(result);
  const effects = asRecord(response.appliedEffects);
  const effectLabels: Array<[string, string]> = [
    ['hp', '恢复生命'],
    ['stamina', '恢复行动力'],
    ['storyFragments', '故事碎片'],
    ['channelHeat', '频道热度'],
    ['abyssMark', '深渊印记'],
    ['worldLineShift', '世界线偏移'],
  ];
  const details = effectLabels.flatMap(([key, label]) => {
    const amount = positiveAmount(effects[key]);
    return amount > 0 ? [`${label} +${formatCount(amount)}`] : [];
  });
  const itemName = typeof response.itemName === 'string' && response.itemName.trim()
    ? response.itemName.trim()
    : '物品';
  return `已使用「${itemName}」${details.length ? `：${details.join('、')}` : ''}`;
}
