import { describe, expect, it } from 'vitest';
import { sacrificeLossText, summarizeTitleEffects } from '@/utils/titleEffects';

describe('称号效果展示', () => {
  const entry = {
    titleKey: 'omniscient_reader',
    effects: { attack: 20, critRate: 0.1, insight: 5, maxHp: 50, mystery: 2 },
    inactiveEffects: ['insight'],
    uncertainEffects: ['maxHp'],
    effectScope: 'equipped',
    equipped: true,
  };

  it('按生效 / 待确认 / 未生效分组，比率显示为百分比，未知 key 原样显示', () => {
    const summary = summarizeTitleEffects(entry)!;
    expect(summary.active).toEqual(['攻击 +20', '暴击率 +10%', 'mystery +2']);
    expect(summary.uncertain).toEqual(['最大生命 +50']);
    expect(summary.inactive).toEqual(['洞察 +5']);
  });

  it('献祭损失只算正在生效的；佩戴才生效且未佩戴时什么也不失去', () => {
    expect(sacrificeLossText(summarizeTitleEffects(entry))).toBe('将失去：攻击 +20 · 暴击率 +10% · mystery +2');
    expect(sacrificeLossText(summarizeTitleEffects({ ...entry, equipped: false })))
      .toBe('它目前未佩戴，不会失去当前生效的效果。');
  });

  it('只有效果、没有审计字段的数据：全部按待确认显示，不当作生效', () => {
    const summary = summarizeTitleEffects({ titleKey: 'x', effects: { attack: 10, willpower: 4 } })!;
    expect(summary.active).toEqual([]);
    expect(summary.uncertain).toEqual(['攻击 +10', '意志力 +4']);
  });

  it('没有效果数据的旧接口不显示', () => {
    expect(summarizeTitleEffects({ titleKey: 'x' })).toBeNull();
    expect(sacrificeLossText(null)).toBe('');
  });
});
