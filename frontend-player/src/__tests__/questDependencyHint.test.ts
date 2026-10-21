import { describe, expect, it } from 'vitest';
import { questDependencyHint } from '@/utils/questDependencyHint';

describe('questDependencyHint', () => {
  it('explains that market tasks require another player to complete a trade', () => {
    expect(questDependencyHint({ quest_key: 'daily_trade_1' }))
      .toContain('不能购买自己的挂单');
    expect(questDependencyHint({ questKey: 'daily_trade_sell_1' }))
      .toContain('由其他玩家完成购买');
  });

  it('explains online acceptance for PvP and cooldown for world-boss tasks', () => {
    expect(questDependencyHint({ key: 'weekly_pk_5' }))
      .toContain('对方接受挑战后才计数');
    expect(questDependencyHint({ questKey: 'weekly_pk_win_3' }))
      .toContain('在线玩家');
    expect(questDependencyHint({ quest_key: 'weekly_boss_3' }))
      .toContain('30 秒冷却');
  });

  it('leaves tasks without known external dependencies unchanged', () => {
    expect(questDependencyHint({ questKey: 'daily_explore_3' })).toBe('');
  });
});
