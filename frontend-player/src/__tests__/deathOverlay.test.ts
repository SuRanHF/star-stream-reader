import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ revive: vi.fn(), getBootstrap: vi.fn() }));
vi.mock('@/api/playerApi', () => ({ playerApi: { revive: mocks.revive } }));
vi.mock('@/api/gameApi', () => ({ gameApi: { getBootstrap: mocks.getBootstrap } }));

import DeathOverlay from '@/components/player/DeathOverlay.vue';
import { useGameStore } from '@/stores/gameStore';

describe('DeathOverlay 献祭称号复活', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    const gameStore = useGameStore();
    gameStore.player = { id: 7, coins: 0, stats: { level: 3 } } as never;
    gameStore.titles = [{ titleKey: 'watcher', name: '守望者', rarity: '稀有' }] as never;
    gameStore.applyBootstrap = vi.fn() as never;
    mocks.revive.mockReset().mockResolvedValue({});
    mocks.getBootstrap.mockReset().mockResolvedValue({});
  });

  function mountOverlay() {
    return mount(DeathOverlay, { attachTo: document.body, global: { stubs: { Teleport: true } } });
  }

  it('选称号后要点两次才献祭，并带上 titleKey', async () => {
    const wrapper = mountOverlay();
    const sacrifice = () => wrapper.get('.do-sacrifice-btn');
    expect(sacrifice().attributes('disabled')).toBeDefined();

    await wrapper.get('.do-title-chip').trigger('click');
    expect(sacrifice().text()).toBe('献祭「守望者」并归来');

    await sacrifice().trigger('click');
    expect(mocks.revive).not.toHaveBeenCalled();
    expect(sacrifice().text()).toBe('再点一次：永久献祭「守望者」');

    await sacrifice().trigger('click');
    await flushPromises();
    expect(mocks.revive).toHaveBeenCalledWith(7, 'title', 'watcher');
    wrapper.unmount();
  });

  it('没有称号时不显示献祭按钮', () => {
    useGameStore().titles = [] as never;
    const wrapper = mountOverlay();
    expect(wrapper.find('.do-sacrifice-btn').exists()).toBe(false);
    expect(wrapper.text()).toContain('你还没有可以献上的称号');
    wrapper.unmount();
  });
});
