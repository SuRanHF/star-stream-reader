import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import EndingArchivePanel from '@/components/panels/EndingArchivePanel.vue';

describe('EndingArchivePanel', () => {
  it('renders the achieved personal endings returned by the server', () => {
    const wrapper = mount(EndingArchivePanel, {
      props: {
        endings: [{
          endingKey: 'end_continue_dream',
          name: '接续之梦：新的最古老之梦',
          description: '你成为新的最古老之梦。',
        }],
      },
    });

    expect(wrapper.get('[aria-label="个人终局档案"]').text()).toContain('1 项已达成');
    expect(wrapper.text()).toContain('接续之梦：新的最古老之梦');
    expect(wrapper.text()).toContain('已达成');
    expect(wrapper.text()).toContain('你成为新的最古老之梦。');
  });

  it('distinguishes an empty archive from a failed request', () => {
    const empty = mount(EndingArchivePanel, { props: { endings: [] } });
    const unavailable = mount(EndingArchivePanel, {
      props: { endings: [{ status: '接口错误', message: 'timeout' }] },
    });

    expect(empty.text()).toContain('个人终局仍在前方');
    expect(empty.text()).not.toContain('未命名');
    expect(unavailable.text()).toContain('终局档案暂时无法读取');
    expect(unavailable.text()).not.toContain('timeout');
  });
});
