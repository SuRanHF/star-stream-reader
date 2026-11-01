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

describe('献祭筛选与重获状态', () => {
  it('只列仍拥有且可献祭的称号；旧数据没有这些字段时视为可献祭', async () => {
    const { sacrificableTitles } = await import('@/utils/titleEffects');
    const keys = sacrificableTitles([
      { titleKey: 'a' },
      { titleKey: 'b', owned: false, sacrificed: true, canSacrifice: false },
      { titleKey: 'c', owned: true, reacquired: true, canSacrifice: false },
      { titleKey: 'd', owned: true, canSacrifice: true },
    ]).map((entry) => entry.titleKey);
    expect(keys).toEqual(['a', 'd']);
  });

  it('重获文案：冷却中、进行中、不可重获、已重获', async () => {
    const { reacquireText } = await import('@/utils/titleEffects');
    expect(reacquireText({ sacrificed: true, reacquireTarget: 30, reacquireDaysRemaining: 3 }))
      .toBe('已献祭 · 3 天后开始重获（需新探索 30 次）');
    expect(reacquireText({ sacrificed: true, reacquireTarget: 30, reacquireProgress: 12, reacquireDaysRemaining: 0 }))
      .toBe('已献祭 · 重获进度 12 / 30');
    expect(reacquireText({ sacrificed: true, reacquireTarget: 0 })).toBe('已献祭 · 不可重获');
    expect(reacquireText({ owned: true, reacquired: true })).toBe('已重获 · 不可再献祭');
    expect(reacquireText({ owned: true })).toBe('');
  });
});
