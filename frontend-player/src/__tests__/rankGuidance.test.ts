import { describe, expect, it } from 'vitest';
import { getRankGuidance } from '@/utils/rankGuidance';

describe('getRankGuidance', () => {
  it('解释 F 位格进入推荐 E 位格地点时的战斗压制', () => {
    const guidance = getRankGuidance('F', 'E');

    expect(guidance).toMatchObject({
      currentRank: 'F',
      recommendedRank: 'E',
      comparison: 'below',
      effectLabel: '战斗伤害 -15%',
      damageAdjustmentPercent: -15,
    });
    expect(guidance?.details).toContain('地点仍可进入');
  });

  it('位格相同不制造额外惩罚或加成', () => {
    expect(getRankGuidance(' e ', 'E')).toMatchObject({
      currentRank: 'E',
      recommendedRank: 'E',
      comparison: 'meets',
      effectLabel: '位格修正中性',
      damageAdjustmentPercent: 0,
    });
  });

  it('位格较高时展示对应加成并遵守服务端倍率上限', () => {
    expect(getRankGuidance('D', 'E')?.effectLabel).toBe('战斗伤害 +15%');
    expect(getRankGuidance('SSS', 'F')?.damageAdjustmentPercent).toBe(120);
    expect(getRankGuidance('F', 'SSS')?.damageAdjustmentPercent).toBe(-50);
  });

  it('未知位格不猜测比较结果', () => {
    expect(getRankGuidance('?', 'E')).toBeNull();
    expect(getRankGuidance('F', null)).toBeNull();
  });
});
