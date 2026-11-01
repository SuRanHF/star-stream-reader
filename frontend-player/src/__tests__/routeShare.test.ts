import { flushPromises, mount } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ getPublicRouteShare: vi.fn() }));
vi.mock('@/api/exploreApi', () => ({ exploreApi: { getPublicRouteShare: mocks.getPublicRouteShare } }));
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { code: 'abc123' } }), RouterLink: { template: '<a><slot /></a>' } }));

import RouteShareView from '@/views/RouteShareView.vue';

function mountView() {
  return mount(RouteShareView, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }]],
      stubs: { RouterLink: { template: '<a class="rsv-cta"><slot /></a>' } },
    },
  });
}

describe('RouteShareView', () => {
  it('按顺序显示节点、所选选项与占比，标出待续；页面加 noindex', async () => {
    mocks.getPublicRouteShare.mockResolvedValue({
      nodes: [
        { node_id: 'n1', title: '第一个异常痕迹', chapter_label: '第一章' },
        { node_id: 'n2', title: '圆桌的主位', chapter_label: '第四章' },
      ],
      choices: [{ node_id: 'n1', choice_text: '"是的。我知道你是谁"', stats: { percent: 55, sample_band: '20+' } }],
      current_tip: 'n2',
    });
    const wrapper = mountView();
    await flushPromises();

    expect(mocks.getPublicRouteShare).toHaveBeenCalledWith('abc123');
    const steps = wrapper.findAll('.rsv-step');
    expect(steps[0].text()).toContain('选择：「是的。我知道你是谁」');
    expect(steps[0].text()).toContain('55% 的读者也这样选（20+）');
    expect(steps[1].classes()).toContain('is-tip');
    expect(document.head.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe('noindex, nofollow');
    wrapper.unmount();
    expect(document.head.querySelector('meta[name="robots"]')).toBeNull();
  });

  it('不存在 / 过期 / 撤销时给出说明', async () => {
    mocks.getPublicRouteShare.mockRejectedValue(new Error('404'));
    const wrapper = mountView();
    await flushPromises();
    expect(wrapper.text()).toContain('这条分享不存在、已经过期或被撤销了');
    wrapper.unmount();
  });
});
