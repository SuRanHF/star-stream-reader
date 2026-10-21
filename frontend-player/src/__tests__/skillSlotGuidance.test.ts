import { describe, expect, it } from 'vitest';
import { getSkillSlotGuidance } from '@/utils/skillSlotGuidance';

describe('skill slot guidance', () => {
  it('explains that F/E players may learn skills but need D rank to equip them', () => {
    expect(getSkillSlotGuidance(0, 0)).toEqual({
      title: '技能槽尚未开放',
      message: 'F/E 级暂时没有技能槽。可以先学习并保留技能，晋升至 D 级开放技能槽后再装备；技能效果只在装备后生效。',
      actionLabel: 'D 级开放',
    });
  });

  it('explains a full positive-capacity loadout', () => {
    expect(getSkillSlotGuidance(2, 2)).toEqual({
      title: '技能槽已满',
      message: '当前已占用 2/2 个技能槽。卸下一项已装备技能，或晋升后开放更多槽位，再装备其他技能。',
      actionLabel: '槽位已满',
    });
  });

  it('allows equipping when a slot is available and waits for summary data', () => {
    expect(getSkillSlotGuidance(2, 1)).toBeNull();
    expect(getSkillSlotGuidance(undefined, 0)).toBeNull();
  });
});
