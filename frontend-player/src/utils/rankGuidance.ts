const RANK_VALUES: Record<string, number> = {
  F: 1,
  E: 2,
  D: 3,
  C: 4,
  B: 5,
  A: 6,
  S: 7,
  SS: 8,
  SSS: 9,
};

export type RankComparison = 'below' | 'meets' | 'above';

export interface RankGuidance {
  currentRank: string;
  recommendedRank: string;
  comparison: RankComparison;
  summary: string;
  effectLabel: string;
  damageAdjustmentPercent: number;
  details: string;
}

function normalizeRank(value: unknown): string {
  return String(value ?? '').trim().toUpperCase();
}

/** Keep the map and scene warning aligned with CombatServiceImpl's rank modifier. */
export function getRankGuidance(currentRankValue: unknown, recommendedRankValue: unknown): RankGuidance | null {
  const currentRank = normalizeRank(currentRankValue);
  const recommendedRank = normalizeRank(recommendedRankValue);
  const currentValue = RANK_VALUES[currentRank];
  const recommendedValue = RANK_VALUES[recommendedRank];

  if (!currentValue || !recommendedValue) return null;

  const difference = currentValue - recommendedValue;
  const multiplier = Math.max(0.5, Math.min(3, 1 + difference * 0.15));
  const damageAdjustmentPercent = Math.round((multiplier - 1) * 100);
  const comparison: RankComparison = difference < 0 ? 'below' : difference > 0 ? 'above' : 'meets';
  const summary = comparison === 'below'
    ? `当前 ${currentRank} 位格低于建议`
    : comparison === 'above'
      ? `当前 ${currentRank} 位格高于建议`
      : `当前 ${currentRank} 位格达到建议`;
  const effectLabel = damageAdjustmentPercent === 0
    ? '位格修正中性'
    : `战斗伤害 ${damageAdjustmentPercent > 0 ? '+' : ''}${damageAdjustmentPercent}%`;
  const details = comparison === 'below'
    ? `位格修正会使你对本地点敌人的伤害降低约 ${Math.abs(damageAdjustmentPercent)}%；地点仍可进入，遇到不利战斗可以撤退。`
    : comparison === 'above'
      ? `位格修正会使你对本地点敌人的伤害提高约 ${damageAdjustmentPercent}%；装备、技能和故事能力仍会影响实际战况。`
      : '位格与推荐相符；装备、技能和故事能力仍会影响实际战况。';

  return {
    currentRank,
    recommendedRank,
    comparison,
    summary,
    effectLabel,
    damageAdjustmentPercent,
    details,
  };
}
