export interface SkillSlotGuidance {
  title: string;
  message: string;
  actionLabel: string;
}

/** Explains why a learned skill cannot be equipped yet without changing server rules. */
export function getSkillSlotGuidance(
  slotLimit: number | undefined,
  equippedCount: number | undefined,
): SkillSlotGuidance | null {
  if (slotLimit === undefined || !Number.isFinite(slotLimit)) return null;

  const limit = Math.max(0, Math.floor(slotLimit));
  const equipped = Number.isFinite(equippedCount) && equippedCount !== undefined
    ? Math.max(0, Math.floor(equippedCount))
    : 0;

  if (limit === 0) {
    return {
      title: '技能槽尚未开放',
      message: 'F/E 级暂时没有技能槽。可以先学习并保留技能，晋升至 D 级开放技能槽后再装备；技能效果只在装备后生效。',
      actionLabel: 'D 级开放',
    };
  }

  if (equipped >= limit) {
    return {
      title: '技能槽已满',
      message: `当前已占用 ${equipped}/${limit} 个技能槽。卸下一项已装备技能，或晋升后开放更多槽位，再装备其他技能。`,
      actionLabel: '槽位已满',
    };
  }

  return null;
}
