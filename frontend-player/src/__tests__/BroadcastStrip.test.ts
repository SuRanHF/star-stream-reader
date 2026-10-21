import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { broadcastApi } from '@/api/broadcastApi';
import { useGameStore } from '@/stores/gameStore';
import BroadcastStrip from '@/components/broadcast/BroadcastStrip.vue';

vi.mock('@/api/broadcastApi', () => ({
  broadcastApi: { getRankings: vi.fn(), getMyRankingPosition: vi.fn(), getHistory: vi.fn() },
}));

describe('BroadcastStrip active objective progress', () => {
  let pinia: ReturnType<typeof createPinia>;

  beforeEach(() => {
    pinia = createPinia();
    setActivePinia(pinia);
    vi.mocked(broadcastApi.getRankings).mockReset();
    vi.mocked(broadcastApi.getMyRankingPosition).mockReset();
    vi.mocked(broadcastApi.getHistory).mockReset();
  });

  it('shows the active event aggregate and its individual objective counts', () => {
    const game = useGameStore();
    game.broadcastSummary = {
      activeCount: 1,
      activeEvents: [{
        eventKey: 'broadcast-spring',
        title: '星流探索令',
        currentValue: 5,
        targetValue: 23,
        objectives: [
          { label: '全服探索', current: 4, target: 20 },
          { label: '触发故事', current: 1, target: 3 },
        ],
      }],
    };

    const wrapper = mount(BroadcastStrip, { global: { plugins: [pinia] } });

    expect(wrapper.get('.ling-broadcast-text').text())
      .toBe('星流探索令 (22%) · 全服探索 4/20 · 触发故事 1/3');
    wrapper.unmount();
  });

  it('loads and presents rankings for the active event only', async () => {
    const game = useGameStore();
    game.player = { id: 17, playerName: '测试读者' } as NonNullable<typeof game.player>;
    game.broadcastSummary = {
      activeCount: 1,
      activeEvents: [{ eventKey: 'broadcast-spring', title: '星流探索令' }],
    };
    vi.mocked(broadcastApi.getRankings).mockResolvedValue([
      { player_id: 31, player_name: '星海旅人', total_contribution: 12 },
      { player_id: 17, player_name: '测试读者', total_contribution: 8 },
    ]);
    vi.mocked(broadcastApi.getMyRankingPosition).mockResolvedValue({
      participated: true,
      participantCount: 4,
      rank: 2,
      contribution: 7,
      pointsToOvertakeNext: 6,
    });

    const wrapper = mount(BroadcastStrip, { global: { plugins: [pinia] } });
    await wrapper.get('button').trigger('click');
    await flushPromises();

    expect(broadcastApi.getRankings).toHaveBeenCalledWith('broadcast-spring', 10);
    expect(broadcastApi.getMyRankingPosition).toHaveBeenCalledWith('broadcast-spring', 17);
    expect(wrapper.get('.ling-broadcast-rankings h2').text()).toBe('星流探索令');
    expect(wrapper.get('.ling-broadcast-my-position').text())
      .toContain('再积累 6 点可超过上一位');
    expect(wrapper.findAll('.ling-broadcast-rank-row').map((row) => row.text()))
      .toEqual(['01星海旅人 贡献 12', '02测试读者 你贡献 8']);
    expect(wrapper.findAll('.ling-broadcast-rank-row')[1].classes()).toContain('is-self');
    expect(wrapper.text()).toContain('不跨活动累计');

    await wrapper.get('button').trigger('click');
    expect(wrapper.find('.ling-broadcast-rankings').exists()).toBe(false);
    wrapper.unmount();
  });

  it('does not offer a contribution board when there is no active event key', () => {
    const game = useGameStore();
    game.broadcastSummary = { activeCount: 0, activeEvents: [] };

    const wrapper = mount(BroadcastStrip, { global: { plugins: [pinia] } });

    expect(wrapper.find('.ling-broadcast-rank-toggle').exists()).toBe(false);
    expect(wrapper.find('.ling-broadcast-history-toggle').exists()).toBe(true);
    wrapper.unmount();
  });

  it('closes and clears the previous board when the last active event ends', async () => {
    const game = useGameStore();
    game.player = { id: 17, playerName: '测试读者' } as NonNullable<typeof game.player>;
    game.broadcastSummary = {
      activeCount: 1,
      activeEvents: [{ eventKey: 'broadcast-spring', title: '星流探索令' }],
    };
    vi.mocked(broadcastApi.getRankings).mockResolvedValue([
      { rank: 1, player_id: 17, player_name: '测试读者', total_contribution: 8 },
    ]);
    vi.mocked(broadcastApi.getMyRankingPosition).mockResolvedValue({
      participated: true,
      participantCount: 1,
      rank: 1,
      contribution: 8,
    });

    const wrapper = mount(BroadcastStrip, { global: { plugins: [pinia] } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.get('.ling-broadcast-rankings h2').text()).toBe('星流探索令');

    game.broadcastSummary = { activeCount: 0, activeEvents: [] };
    await flushPromises();

    expect(wrapper.find('.ling-broadcast-rankings').exists()).toBe(false);
    expect(wrapper.find('.ling-broadcast-rank-toggle').exists()).toBe(false);
    wrapper.unmount();
  });

  it('lets players switch between simultaneous events and loads the selected event ranking', async () => {
    const game = useGameStore();
    game.player = { id: 17, playerName: '测试读者' } as NonNullable<typeof game.player>;
    game.broadcastSummary = {
      activeCount: 2,
      activeEvents: [
        { eventKey: 'broadcast-spring', title: '星流探索令' },
        { eventKey: 'broadcast-autumn', title: '世界线守望' },
      ],
    };
    vi.mocked(broadcastApi.getRankings).mockImplementation(async (eventKey) => [
      { rank: 1, player_id: 17, player_name: '测试读者', total_contribution: eventKey === 'broadcast-autumn' ? 9 : 3 },
    ]);
    vi.mocked(broadcastApi.getMyRankingPosition).mockImplementation(async (eventKey) => ({
      participated: true,
      participantCount: 1,
      rank: eventKey === 'broadcast-autumn' ? 1 : 2,
      contribution: eventKey === 'broadcast-autumn' ? 9 : 3,
      pointsToOvertakeNext: 0,
    }));

    const wrapper = mount(BroadcastStrip, { global: { plugins: [pinia] } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.get('.ling-broadcast-rankings h2').text()).toBe('星流探索令');

    await wrapper.get('select[aria-label="切换公共活动"]').setValue('broadcast-autumn');
    await flushPromises();

    expect(broadcastApi.getRankings).toHaveBeenNthCalledWith(2, 'broadcast-autumn', 10);
    expect(broadcastApi.getMyRankingPosition).toHaveBeenNthCalledWith(2, 'broadcast-autumn', 17);
    expect(wrapper.get('.ling-broadcast-rankings h2').text()).toBe('世界线守望');
    expect(wrapper.get('.ling-broadcast-text').text()).toContain('世界线守望');
    expect(wrapper.get('.ling-broadcast-rank-score').text()).toBe('贡献 9');
    wrapper.unmount();
  });

  it('isolates personal-position failures and allows retrying a failed public board', async () => {
    const game = useGameStore();
    game.player = { id: 17, playerName: '测试读者' } as NonNullable<typeof game.player>;
    game.broadcastSummary = {
      activeCount: 1,
      activeEvents: [{ eventKey: 'broadcast-spring', title: '星流探索令' }],
    };
    vi.mocked(broadcastApi.getRankings)
      .mockRejectedValueOnce(new Error('暂时离线'))
      .mockResolvedValueOnce([
        { rank: 1, player_id: 28, player_name: '夜航者', total_contribution: 8 },
      ]);
    vi.mocked(broadcastApi.getMyRankingPosition)
      .mockRejectedValueOnce(new Error('个人位置暂不可用'))
      .mockResolvedValueOnce({
        participated: true,
        participantCount: 2,
        rank: 2,
        contribution: 4,
        pointsToOvertakeNext: 5,
      });

    const wrapper = mount(BroadcastStrip, { global: { plugins: [pinia] } });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('[role="alert"]').map((alert) => alert.text()))
      .toEqual(expect.arrayContaining([
        expect.stringContaining('同行者榜暂时无法读取'),
        expect.stringContaining('贡献榜仍可查看'),
      ]));

    await wrapper.get('.ling-broadcast-retry').trigger('click');
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.get('.ling-broadcast-my-position').text()).toContain('第 2 位');
    expect(wrapper.get('.ling-broadcast-rank-name').text()).toContain('夜航者');
    wrapper.unmount();
  });

  it('shows a concrete participation prompt when the player has not contributed yet', async () => {
    const game = useGameStore();
    game.player = { id: 17, playerName: '测试读者' } as NonNullable<typeof game.player>;
    game.broadcastSummary = {
      activeCount: 1,
      activeEvents: [{ eventKey: 'broadcast-spring', title: '星流探索令' }],
    };
    vi.mocked(broadcastApi.getRankings).mockResolvedValue([
      { rank: 1, player_id: 28, player_name: '夜航者', total_contribution: 8 },
    ]);
    vi.mocked(broadcastApi.getMyRankingPosition).mockResolvedValue({
      participated: false,
      participantCount: 1,
      rank: null,
      contribution: 0,
      pointsToOvertakeNext: null,
    });

    const wrapper = mount(BroadcastStrip, { global: { plugins: [pinia] } });
    await wrapper.get('button').trigger('click');
    await flushPromises();

    expect(wrapper.get('.ling-broadcast-my-position').text()).toContain('尚未上榜');
    expect(wrapper.get('.ling-broadcast-my-position').text())
      .toContain('完成一次可计入本场目标的行动即可进入榜单');
    wrapper.unmount();
  });

  it('shows public event outcomes without personal reward details and switches panels cleanly', async () => {
    const game = useGameStore();
    game.broadcastSummary = {
      activeCount: 1,
      activeEvents: [{ eventKey: 'broadcast-live', title: '当前星流行动' }],
    };
    vi.mocked(broadcastApi.getRankings).mockResolvedValue([
      { rank: 1, player_id: 28, player_name: '夜航者', total_contribution: 8 },
    ]);
    vi.mocked(broadcastApi.getMyRankingPosition).mockResolvedValue({ rank: 0, contribution: 0 });
    vi.mocked(broadcastApi.getHistory).mockResolvedValue([
      {
        eventKey: 'broadcast-ended',
        title: '梦境回响',
        description: '同行者共同守住了梦境裂隙。',
        status: 'completed',
        currentValue: 20,
        targetValue: 20,
        progressPercent: 100,
        endedAt: '2026-10-01T07:10:00',
        worldlineEffectSummaries: ['世界线偏移 +4', '剧本压力 -2'],
      },
      {
        eventKey: 'broadcast-expired',
        title: '未竟的预兆',
        status: 'expired',
        currentValue: 6,
        targetValue: 10,
        progressPercent: 60,
        worldlineEffectSummaries: [],
      },
    ]);

    const wrapper = mount(BroadcastStrip, { global: { plugins: [pinia] } });
    await wrapper.get('.ling-broadcast-rank-toggle').trigger('click');
    await flushPromises();
    expect(wrapper.find('.ling-broadcast-rankings').exists()).toBe(true);

    await wrapper.get('.ling-broadcast-history-toggle').trigger('click');
    await flushPromises();

    expect(broadcastApi.getHistory).toHaveBeenCalledWith(12);
    expect(wrapper.find('.ling-broadcast-rankings').exists()).toBe(false);
    expect(wrapper.get('.ling-broadcast-history').text()).toContain('目标达成');
    expect(wrapper.get('.ling-broadcast-history').text()).toContain('世界线偏移 +4');
    expect(wrapper.get('.ling-broadcast-history').text()).toContain('剧本压力 -2');
    expect(wrapper.get('.ling-broadcast-history').text()).toContain('时限结束');
    expect(wrapper.get('.ling-broadcast-history').text())
      .toContain('没有触发本活动的结算效果');
    expect(wrapper.get('.ling-broadcast-history-head').text()).toContain('不含个人奖励');
    wrapper.unmount();
  });

  it('marks the latest completed activity as past and retries history failures', async () => {
    const game = useGameStore();
    game.broadcastSummary = {
      activeCount: 0,
      activeEvents: [],
      topEvents: [{ title: '群星旧约', status: 'completed', progressPercent: 100 }],
    };
    vi.mocked(broadcastApi.getHistory)
      .mockRejectedValueOnce(new Error('暂时离线'))
      .mockResolvedValueOnce([]);

    const wrapper = mount(BroadcastStrip, { global: { plugins: [pinia] } });
    expect(wrapper.get('.ling-broadcast-text').text()).toContain('近期回响 · 群星旧约');
    await wrapper.get('.ling-broadcast-history-toggle').trigger('click');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('过往活动暂时无法读取');

    await wrapper.get('.ling-broadcast-retry').trigger('click');
    await flushPromises();

    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.get('.ling-broadcast-state').text()).toContain('还没有结束的公共活动记录');
    wrapper.unmount();
  });
});
