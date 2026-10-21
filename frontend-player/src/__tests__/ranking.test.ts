import { describe, expect, it } from 'vitest';
import {
  exploreActionEmptyStateMessage,
  formatRankingProximity,
  formatRatingDelta,
  getExploreActionPositionState,
  getPkPerformancePositionState,
  getStoryDiscoveryPositionState,
  isHighAvatarRank,
  pkPerformanceEmptyStateMessage,
  storyDiscoveryEmptyStateMessage,
  withLeaderboardPositions,
} from '@/utils/ranking';

describe('recent exploration action ranking states', () => {
  it('distinguishes an unavailable position from no accepted actions', () => {
    expect(getExploreActionPositionState({ participated: false }, true)).toBe('unavailable');
    expect(getExploreActionPositionState({ participated: false })).toBe('no-actions');
    expect(getExploreActionPositionState({ participated: true })).toBe('participated');
  });

  it('separates an empty period from a failed leaderboard request', () => {
    expect(exploreActionEmptyStateMessage(true)).toBe('近 7 日探索行动榜暂时无法读取，请稍后重试。');
    expect(exploreActionEmptyStateMessage(false)).toBe('近 7 日还没有已接受的探索行动。');
  });
});

describe('story discovery ranking load states', () => {
  it('does not turn a failed personal-position request into an empty-progression state', () => {
    expect(getStoryDiscoveryPositionState({ ranked: false, discoveryCount: 0 }, true)).toBe('unavailable');
  });

  it('distinguishes ranked, unranked progress, and no discoveries', () => {
    expect(getStoryDiscoveryPositionState({ ranked: true, discoveryCount: 2 })).toBe('ranked');
    expect(getStoryDiscoveryPositionState({ ranked: false, discoveryCount: 1 })).toBe('unranked');
    expect(getStoryDiscoveryPositionState({ ranked: false, discoveryCount: 0 })).toBe('no-discoveries');
  });

  it('shows a retry message instead of an empty-board message after a request failure', () => {
    expect(storyDiscoveryEmptyStateMessage(true)).toBe('故事发现榜暂时无法读取，请稍后重试。');
    expect(storyDiscoveryEmptyStateMessage(false)).toBe('没有符合条件的榜单记录。');
  });
});

describe('recent PK performance ranking states', () => {
  it('distinguishes a missing position response from no recent matches', () => {
    expect(getPkPerformancePositionState({ participated: false }, true)).toBe('unavailable');
    expect(getPkPerformancePositionState({ participated: false })).toBe('no-matches');
    expect(getPkPerformancePositionState({ participated: true })).toBe('participated');
  });

  it.each([[24, '+24'], [-8, '-8'], [0, '0'], ['invalid', '0']])(
    'formats rating change %s as %s',
    (value, expected) => expect(formatRatingDelta(value)).toBe(expected),
  );

  it('separates an empty period from an unavailable leaderboard', () => {
    expect(pkPerformanceEmptyStateMessage(true)).toBe('近 7 日 PK 榜暂时无法读取，请稍后重试。');
    expect(pkPerformanceEmptyStateMessage(false)).toBe('近 7 日还没有已结算的 PK 战报。');
  });
});

describe('withLeaderboardPositions', () => {
  it('keeps global positions when rows are later filtered in the UI', () => {
    const ranked = withLeaderboardPositions([
      { playerName: '第一名' },
      { playerName: '第二名', rank: 8 },
      { playerName: '第三名' },
    ]);

    expect(ranked.map((entry) => entry.rank)).toEqual([1, 8, 3]);
  });
});

describe('isHighAvatarRank', () => {
  it.each(['S', 'SS', 'SSS', 'Immortal', 'Mythic', '神话', '超越'])('includes %s in the advanced scope', (rank) => {
    expect(isHighAvatarRank(rank)).toBe(true);
  });

  it.each(['F', 'E', 'D', 'C', 'B', 'A', '', null, undefined])('keeps %s in the regular scope', (rank) => {
    expect(isHighAvatarRank(rank)).toBe(false);
  });
});

describe('formatRankingProximity', () => {
  it('marks the current player without describing them as a rival', () => {
    expect(formatRankingProximity(7, 7, 12, 12, 980, 980)).toBe('你当前的位置');
  });

  it('shows the score gap to players ahead', () => {
    expect(formatRankingProximity(8, 7, 10, 12, 1040, 1000)).toBe('领先 2 位 · 高 40 分');
  });

  it('shows equal-score tie-break positions and lower-ranked players', () => {
    expect(formatRankingProximity(8, 7, 13, 12, 1000, 1000)).toBe('落后 1 位 · 同分');
    expect(formatRankingProximity(9, 7, 14, 12, 960, 1000)).toBe('落后 2 位 · 低 40 分');
  });
});
