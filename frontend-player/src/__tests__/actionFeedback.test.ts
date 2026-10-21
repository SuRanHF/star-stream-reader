import { describe, expect, it } from 'vitest';
import { formatItemUseSuccess, formatQuestClaimSuccess } from '@/utils/actionFeedback';

describe('formatQuestClaimSuccess', () => {
  it('summarizes resources, item quantities, and titles without exposing internal item keys', () => {
    expect(formatQuestClaimSuccess({
      questTitle: '每日探索',
      earned: {
        coins: 15,
        storyFragments: 2,
        exp: 30,
        channelHeat: 1,
        items: [{ itemKey: 'station_token', quantity: 2 }],
        titleKey: 'station_watcher',
      },
    })).toBe('已领取「每日探索」奖励：+15 星币、+2 故事碎片、+30 经验值、+1 频道热度、物品 ×2、称号奖励');
  });

  it('still confirms an empty reward claim without inventing rewards', () => {
    expect(formatQuestClaimSuccess({ questTitle: '初次相遇', earned: {} }))
      .toBe('已领取「初次相遇」奖励');
  });

  it('ignores malformed or non-positive reward amounts', () => {
    expect(formatQuestClaimSuccess({
      earned: { coins: -3, exp: 'not-a-number', items: [{ quantity: 0 }] },
    })).toBe('已领取任务奖励');
  });
});

describe('formatItemUseSuccess', () => {
  it('shows actual effects returned by the server', () => {
    expect(formatItemUseSuccess({
      itemName: '看门人的痕迹',
      appliedEffects: { storyFragments: 10, channelHeat: 8 },
    })).toBe('已使用「看门人的痕迹」：故事碎片 +10、频道热度 +8');
  });

  it('shows healing and stamina as restored values', () => {
    expect(formatItemUseSuccess({
      itemName: '急救绷带',
      appliedEffects: { hp: 35, stamina: 0 },
    })).toBe('已使用「急救绷带」：恢复生命 +35');
  });
});
