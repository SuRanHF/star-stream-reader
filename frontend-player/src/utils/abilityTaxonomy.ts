export type AbilityCategory = 'art' | 'skill' | 'custom' | 'other';

const STAR_MARK_TYPES = new Set([
  'art',
  'stigma',
  'star_mark',
  'star-mark',
  'star mark',
  '星痕',
]);

const SKILL_TYPES = new Set([
  'skill',
  'passive',
  'active',
  'active_placeholder',
  'exploration',
  'combat',
  'narrative',
  '技能',
  '专用技能',
]);

export function classifyAbilityType(value: unknown): AbilityCategory {
  const type = String(value ?? '').trim().toLowerCase();
  if (!type) return 'other';

  if (STAR_MARK_TYPES.has(type) || type.includes('星痕') || type.startsWith('star_mark_')) {
    return 'art';
  }
  if (type === 'custom' || type.startsWith('custom_') || type.includes('自创')) {
    return 'custom';
  }
  if (SKILL_TYPES.has(type) || type.includes('skill') || type.includes('技能')) {
    return 'skill';
  }
  return 'other';
}

export function abilityTypeLabel(value: unknown): string {
  switch (classifyAbilityType(value)) {
    case 'art': return '星痕';
    case 'skill': return '技能';
    case 'custom': return '自创';
    default: return value == null || String(value).trim() === '' ? '未分类' : '其他能力';
  }
}

export function supportsStandardSkillLifecycle(value: unknown): boolean {
  const category = classifyAbilityType(value);
  return category === 'skill' || category === 'custom';
}
