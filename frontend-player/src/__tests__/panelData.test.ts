import { describe, expect, it } from 'vitest';
import {
  displayMeta,
  formatEquipmentDetails,
  formatSkillEffectSummary,
  formatSummaryValue,
} from '@/utils/panelData';

describe('panelData', () => {
  it('shows equipment effects as percentages and marks unimplemented effects', () => {
    expect(displayMeta(
      {
        specialEffects: {
          storyEventBonus: 0.07,
          trueDamageBonus: 0.03,
          cooldownReduction: 0.05,
          timeRewindChance: 0.01,
          narrativePressureBonus: 0.025,
        },
      },
      [['特效', 'specialEffects']],
    )).toEqual([(
      '特效: 故事事件权重加成: +7%, '
      + '真实伤害加成（未接入）: +3%, '
      + '冷却缩减（尚无冷却机制）: +5%, '
      + '时间回溯概率（未接入）: +1%, '
      + '叙事压力加成（未接入）: +2.5%'
    )]);

    expect(displayMeta(
      { specialEffects: { storyEventBonus: -0.025 } },
      [['特效', 'specialEffects']],
    )).toEqual(['特效: 故事事件权重加成: -2.5%']);
  });

  it('formats the actual equipment VO fields and distinguishes configured-but-inactive bonuses', () => {
    expect(formatEquipmentDetails({
      stats: {
        attack: 22,
        critRate: 0.05,
        critDamage: 0.15,
        insight: 3,
      },
      special_effects: {
        storyEventBonus: 0.05,
        trueDamageBonus: 0.03,
        abyssPenetration: 0.05,
        damageReduction: 0.03,
      },
    })).toBe(
      '属性 攻击: 22, 暴击率: +5%, 暴击伤害加成: +15%, 洞察（装备加成未接入）: 3'
      + ' · 特效 故事事件权重加成: +5%, 真实伤害加成（未接入）: +3%'
      + ', 深渊穿透（未接入）: +5%, 伤害减免（未接入）: +3%',
    );
  });

  it('marks skill bonuses that are aggregated but not applied to character stats', () => {
    expect(formatSkillEffectSummary({
      insight: 1,
      worldLineShift: 1,
      storyEventBonus: 0.03,
      staminaCostReduce: 0.05,
      channelHeatGainRate: 0.03,
    })).toBe(
      '洞察（技能加成未接入）: 1, 世界线偏移（技能加成未接入）: 1, '
      + '故事事件权重加成: +3%, 行动消耗降低: +5%, 频道热度获取率: +3%',
    );
    expect(formatSkillEffectSummary({ critDamage: 0.1 })).toBe('暴击伤害加成: +10%');
    expect(formatSummaryValue({ critDamage: 1.5 })).toBe('暴击伤害: 1.5');
  });
});
