import { flushPromises, mount } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ getRouteCodex: vi.fn() }));
vi.mock('@/api/exploreApi', () => ({ exploreApi: { getRouteCodex: mocks.getRouteCodex } }));

import RouteCodexView from '@/components/story-tree/RouteCodexView.vue';

describe('RouteCodexView', () => {
  it('到过的显示标题与枝，未到达只给「？」与占比；区分「你在这里」与「你曾停在这里」', async () => {
    mocks.getRouteCodex.mockResolvedValue({
      growth: 2,
      total_tips: 3,
      my_tip_id: 't2',
      tips: [
        { tip_id: 't1', reached: true, branch: 'companion', title: '十王的会议', chapter_label: '第三章', stats: { percent: 40, sample_band: '20+' } },
        { tip_id: 't2', reached: true, branch: 'companion', title: '圆桌的主位', chapter_label: '第四章', stats: null },
        { tip_id: 't3', reached: false, stats: { percent: 8, sample_band: '50+' } },
      ],
    });
    const wrapper = mount(RouteCodexView, {
      props: { playerId: 7 },
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }]] },
    });
    await flushPromises();

    expect(mocks.getRouteCodex).toHaveBeenCalledWith(7);
    expect(wrapper.get('.rcv-summary').text()).toContain('已收集 2 / 3');
    const cards = wrapper.findAll('.rcv-card');
    expect(cards[0].text()).toContain('同行者 · 第三章');
    expect(cards[0].get('.rcv-badge').text()).toBe('你曾停在这里');
    expect(cards[1].get('.rcv-badge').text()).toBe('你在这里');
    expect(cards[1].text()).toContain('数据不足');
    expect(cards[2].get('.rcv-title').text()).toBe('？');
    expect(cards[2].text()).toContain('8% 的读者到过（50+）');
    expect(cards[2].text()).not.toContain('同行者');
  });
});
