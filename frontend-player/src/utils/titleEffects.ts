import type { ApiRecord } from '@/types/api';

// 称号效果展示（契约「称号效果可见化」v2）：服务端给出数值效果与哪些 key 未生效 / 待确认，
// 前端只负责翻译与分组；未知 key 原样显示，不隐藏。
const EFFECT_LABELS: Record<string, string> = {
  attack: '攻击',
  defense: '防御',
  speed: '速度',
  critRate: '暴击率',
  critDamage: '暴击伤害',
  maxHp: '最大生命',
  channelHeatGainRate: '热度收益',
  attackBonus: '攻击加成',
  channelHeat: '频道热度',
  channelHeatBonus: '频道热度加成',
  insight: '洞察',
  insightBonus: '洞察加成',
  narrativePressureBonus: '叙事压力',
  staminaBonus: '行动力',
  willpower: '意志力',
};

const PERCENT_KEYS = new Set(['critRate', 'critDamage', 'channelHeatGainRate', 'narrativePressureBonus']);

export interface TitleEffectSummary {
  active: string[];
  inactive: string[];
  uncertain: string[];
  scope: 'owned' | 'equipped' | '';
  equipped: boolean;
}

function formatEffect(key: string, value: number) {
  const label = EFFECT_LABELS[key] || key;
  const sign = value >= 0 ? '+' : '';
  const amount = PERCENT_KEYS.has(key) ? `${sign}${Math.round(value * 1000) / 10}%` : `${sign}${value}`;
  return `${label} ${amount}`;
}

export function summarizeTitleEffects(entry: ApiRecord | null | undefined): TitleEffectSummary | null {
  const effects = entry?.effects;
  if (!effects || typeof effects !== 'object') return null;
  // 没有审计字段的旧接口：不能假定生效，全部按「待确认」显示。
  const audited = Array.isArray(entry.inactiveEffects) && Array.isArray(entry.uncertainEffects);
  const inactive = new Set(audited ? (entry.inactiveEffects as unknown[]).map(String) : []);
  const uncertain = new Set(audited
    ? (entry.uncertainEffects as unknown[]).map(String)
    : Object.keys(effects as Record<string, unknown>));
  const summary: TitleEffectSummary = {
    active: [],
    inactive: [],
    uncertain: [],
    scope: entry.effectScope === 'owned' || entry.effectScope === 'equipped' ? entry.effectScope : '',
    equipped: entry.equipped === true,
  };
  for (const [key, raw] of Object.entries(effects as Record<string, unknown>)) {
    const value = Number(raw);
    if (!Number.isFinite(value)) continue;
    const text = formatEffect(key, value);
    if (inactive.has(key)) summary.inactive.push(text);
    else if (uncertain.has(key)) summary.uncertain.push(text);
    else summary.active.push(text);
  }
  return summary;
}

// 献祭会失去什么：只算当前真正生效的（佩戴才生效且未佩戴的，什么也不失去）。
export function sacrificeLossText(summary: TitleEffectSummary | null): string {
  if (!summary) return '';
  if (summary.scope === 'equipped' && !summary.equipped) return '它目前未佩戴，不会失去当前生效的效果。';
  if (!summary.active.length) return '它没有正在生效的效果。';
  return `将失去：${summary.active.join(' · ')}`;
}
