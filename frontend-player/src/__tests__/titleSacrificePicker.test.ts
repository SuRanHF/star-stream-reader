import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import TitleSacrificePicker from '@/components/player/TitleSacrificePicker.vue';

describe('TitleSacrificePicker', () => {
  const titles = [
    { titleKey: 'watcher', name: '守望者', owned: true, canSacrifice: true, equipped: true, effectScope: 'equipped',
      effects: { defense: 5 }, inactiveEffects: [], uncertainEffects: [] },
    { titleKey: 'gone', name: '已献祭的', owned: false, sacrificed: true, canSacrifice: false },
  ];

  it('只列可献祭的称号，选中后带出损失说明并发出 pick', async () => {
    const wrapper = mount(TitleSacrificePicker, {
      props: { visible: true, titles, targetName: '某读者' },
      global: { stubs: { Teleport: true } },
    });
    const chips = wrapper.findAll('.tsp-chip');
    expect(chips.map((chip) => chip.text())).toEqual(['守望者']);
    await chips[0].trigger('click');
    expect(wrapper.get('.tsp-loss').text()).toBe('将失去：防御 +5');
    await wrapper.get('.tsp-btn--danger').trigger('click');
    expect(wrapper.emitted('pick')?.[0]).toEqual(['watcher', '守望者', '将失去：防御 +5']);
  });
});
