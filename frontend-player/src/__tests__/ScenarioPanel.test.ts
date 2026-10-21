import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { flushPromises, mount } from '@vue/test-utils';
import { useGameStore } from '@/stores/gameStore';
import { exploreApi } from '@/api/exploreApi';
import ScenarioPanel from '@/components/scenario/ScenarioPanel.vue';

vi.mock('@/api/exploreApi', () => ({
  exploreApi: {
    getLocations: vi.fn(),
  },
}));

describe('ScenarioPanel 位格提示', () => {
  beforeEach(() => {
    vi.mocked(exploreApi.getLocations).mockResolvedValue({
      locations: [{
        location_key: 'chungmuro',
        location_name: '忠武路',
        description: '十王控制的地铁站。',
        danger_level: 4,
        min_level: 5,
        recommended_rank: 'E',
        is_unlocked: true,
        stories_exhausted: false,
        total_stories: 3,
        triggered_stories: 0,
        event_rates: { story: 20, battle_placeholder: 12 },
      }],
    } as never);
  });

  it('把旧的模糊“E级”改成推荐位格，并提示 F 位格的实际压制', async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const game = useGameStore();
    game.player = {
      id: 7,
      avatarRank: 'F',
      currentLocationKey: 'chungmuro',
      currentLocationName: '忠武路',
    };

    const wrapper = mount(ScenarioPanel, { global: { plugins: [pinia] } });
    await flushPromises();

    expect(wrapper.text()).toContain('建议 E 位格');
    expect(wrapper.text()).toContain('当前 F 位格低于建议');
    expect(wrapper.text()).toContain('战斗伤害 -15%');
    expect(wrapper.text()).toContain('地点仍可进入');
  });
});
