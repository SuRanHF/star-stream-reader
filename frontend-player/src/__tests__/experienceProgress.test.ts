import { describe, expect, it } from 'vitest';
import { getExperienceProgress } from '@/utils/experienceProgress';

describe('getExperienceProgress', () => {
  it('starts each new level at zero progress using cumulative XP thresholds', () => {
    expect(getExperienceProgress(100, 2)).toEqual({ current: 0, required: 300, percent: 0 });
    expect(getExperienceProgress(400, 3)).toEqual({ current: 0, required: 500, percent: 0 });
  });

  it('calculates progress relative to the current level threshold', () => {
    expect(getExperienceProgress(250, 2)).toEqual({ current: 150, required: 300, percent: 50 });
  });

  it('clamps inconsistent or invalid values safely', () => {
    expect(getExperienceProgress(900, 2)).toEqual({ current: 300, required: 300, percent: 100 });
    expect(getExperienceProgress(-10, 0)).toEqual({ current: 0, required: 100, percent: 0 });
    expect(getExperienceProgress(Number.NaN, Number.NaN)).toEqual({ current: 0, required: 100, percent: 0 });
  });
});
