import { describe, expect, it } from 'vitest';
import {
  abilityTypeLabel,
  classifyAbilityType,
  supportsStandardSkillLifecycle,
} from '@/utils/abilityTaxonomy';

describe('ability taxonomy', () => {
  it('classifies the seeded generic skill kinds as skills, not star marks', () => {
    for (const type of ['passive', 'exploration', 'combat', 'narrative', 'active_placeholder']) {
      expect(classifyAbilityType(type)).toBe('skill');
      expect(abilityTypeLabel(type)).toBe('技能');
      expect(supportsStandardSkillLifecycle(type)).toBe(true);
    }
  });

  it('recognizes star-mark aliases without treating them as ordinary learn/equip skills', () => {
    for (const type of ['art', 'stigma', 'star_mark', 'star-mark', '星痕能力']) {
      expect(classifyAbilityType(type)).toBe('art');
      expect(abilityTypeLabel(type)).toBe('星痕');
      expect(supportsStandardSkillLifecycle(type)).toBe(false);
    }
  });

  it('keeps custom and unknown types distinct instead of defaulting to star marks', () => {
    expect(classifyAbilityType('custom')).toBe('custom');
    expect(abilityTypeLabel('custom')).toBe('自创');
    expect(supportsStandardSkillLifecycle('custom')).toBe(true);
    expect(classifyAbilityType('narrative_fragment')).toBe('other');
    expect(abilityTypeLabel('narrative_fragment')).toBe('其他能力');
    expect(abilityTypeLabel(null)).toBe('未分类');
    expect(supportsStandardSkillLifecycle('narrative_fragment')).toBe(false);
  });
});
