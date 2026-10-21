import { describe, expect, it } from 'vitest';
import { WORLDLINE_HISTORY_DESCRIPTION, worldlineCauseDisplay } from '@/utils/worldlineDisplay';

describe('worldline history presentation', () => {
  it('prefers the server-resolved event title', () => {
    expect(worldlineCauseDisplay({
      cause: 'daily_story_broadcast',
      causeDisplay: '全服活动「故事共鸣」达成',
    })).toBe('全服活动「故事共鸣」达成');
  });

  it('explains older world-boss keys without showing internal identifiers', () => {
    expect(worldlineCauseDisplay({ cause: 'world_boss_WB-204' })).toBe('世界首领被击破');
    expect(worldlineCauseDisplay({ cause: 'world_boss_expired_WB-205' })).toBe('世界首领未能及时击破');
  });

  it('uses a readable fallback instead of exposing unknown internal keys', () => {
    expect(worldlineCauseDisplay({ cause: 'broadcast_spring_2026' })).toBe('全服活动结算的公共影响');
    expect(worldlineCauseDisplay({})).toBe('公共世界事件影响');
  });

  it('clarifies why natural decay is not listed as repeated history entries', () => {
    expect(WORLDLINE_HISTORY_DESCRIPTION).toContain('自然回落会影响当前数值');
    expect(WORLDLINE_HISTORY_DESCRIPTION).toContain('不逐次占用历史记录');
  });
});
