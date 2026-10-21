import { beforeEach, describe, expect, it, vi } from 'vitest';
import { http } from '@/api/http';
import { rankingApi } from '@/api/rankingApi';

vi.mock('@/api/http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('rankingApi story discovery leaderboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('requests only the aggregate public leaderboard with a bounded limit', async () => {
    const get = vi.mocked(http.get);
    get.mockResolvedValue([]);

    await rankingApi.getStoryDiscoveries();

    expect(get).toHaveBeenCalledWith('/rankings/story-discoveries', { params: { limit: 100 } });
  });

  it('requests the signed-in player position through the ownership-checked route', async () => {
    const get = vi.mocked(http.get);
    get.mockResolvedValue({ rank: 8, discoveryCount: 3 });

    await rankingApi.getStoryDiscoveryPosition(42);

    expect(get).toHaveBeenCalledWith('/rankings/story-discoveries/42');
  });
});

describe('rankingApi recent PK performance leaderboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('requests the bounded public rolling-period leaderboard', async () => {
    const get = vi.mocked(http.get);
    get.mockResolvedValue([]);

    await rankingApi.getPkPerformanceRankings();

    expect(get).toHaveBeenCalledWith('/rankings/pk-performance', { params: { limit: 100 } });
  });

  it('requests the signed-in player position through the ownership-checked route', async () => {
    const get = vi.mocked(http.get);
    get.mockResolvedValue({ rank: 2, ratingDelta: 12 });

    await rankingApi.getPkPerformancePosition(42);

    expect(get).toHaveBeenCalledWith('/rankings/pk-performance/42');
  });
});

describe('rankingApi recent exploration action leaderboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('requests the bounded public rolling-period leaderboard', async () => {
    const get = vi.mocked(http.get);
    get.mockResolvedValue([]);

    await rankingApi.getExploreActionRankings();

    expect(get).toHaveBeenCalledWith('/rankings/exploration-actions', { params: { limit: 100 } });
  });

  it('requests the signed-in player position through the ownership-checked route', async () => {
    const get = vi.mocked(http.get);
    get.mockResolvedValue({ rank: 2, actionCount: 6 });

    await rankingApi.getExploreActionPosition(42);

    expect(get).toHaveBeenCalledWith('/rankings/exploration-actions/42');
  });
});
