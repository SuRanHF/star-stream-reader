import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, shallowMount } from '@vue/test-utils';
import { nextTick, reactive } from 'vue';

const mocks = vi.hoisted(() => ({
  gameStore: null as Record<string, any> | null,
  startExplore: vi.fn(),
  getPendingStories: vi.fn(),
  getPendingBattles: vi.fn(),
  getBootstrap: vi.fn(),
  applyBootstrap: vi.fn(),
  appendRecentLogs: vi.fn(),
  replaceTransientActionLogs: vi.fn(),
  patchPlayer: vi.fn(),
  showAlert: vi.fn(),
  showConfirm: vi.fn(),
  toggleRightSummary: vi.fn(),
  logout: vi.fn(),
  routerPush: vi.fn(),
  getActiveBroadcasts: vi.fn(),
  getRestState: vi.fn(),
  applyRestState: vi.fn(),
  setBroadcastSummary: vi.fn(),
  setActivePanel: vi.fn(),
  invalidateQueries: vi.fn(),
}));

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries }),
}));

vi.mock('@/api/exploreApi', () => ({
  exploreApi: {
    startExplore: mocks.startExplore,
    getPendingStories: mocks.getPendingStories,
    getPendingBattles: mocks.getPendingBattles,
  },
}));
vi.mock('@/api/gameApi', () => ({
  gameApi: { getBootstrap: mocks.getBootstrap },
}));
vi.mock('@/api/broadcastApi', () => ({
  broadcastApi: { getActive: mocks.getActiveBroadcasts },
}));
vi.mock('@/api/playerApi', () => ({
  playerApi: { getRestState: mocks.getRestState, startRest: vi.fn(), stopRest: vi.fn() },
}));
vi.mock('@/stores/gameStore', () => ({
  useGameStore: () => mocks.gameStore,
}));
vi.mock('@/stores/authStore', () => ({
  useAuthStore: () => ({ logout: mocks.logout }),
}));
vi.mock('@/stores/uiStore', () => ({
  useUiStore: () => ({
    rightSummaryOpen: false,
    showAlert: mocks.showAlert,
    showConfirm: mocks.showConfirm,
    toggleRightSummary: mocks.toggleRightSummary,
  }),
}));
vi.mock('@/realtime/realtimeClient', () => ({
  realtimeClient: { disconnect: vi.fn() },
}));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.routerPush }),
}));

import GameLayout from '@/layouts/GameLayout.vue';
import GameLogPanel from '@/components/log/GameLogPanel.vue';
import ExploreResultDialog from '@/components/explore/ExploreResultDialog.vue';
import type { ExploreResult } from '@/api/exploreApi';

function makeResult(overrides: Partial<ExploreResult> = {}): ExploreResult {
  return {
    result_type: 'resource',
    result: { event_key: 'ordinary', event_type: 'resource', name: '补给箱', description: '' },
    rewards: { coins: 12, items: [{ itemKey: 'old_key', quantity: 1 }] },
    progress_effects: {},
    new_titles: [],
    stories_exhausted: false,
    ...overrides,
  };
}

describe('GameLayout exploration-to-log integration', () => {
  const wrappers: Array<ReturnType<typeof shallowMount>> = [];

  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    mocks.gameStore = reactive({
      player: {
        id: 7,
        currentLocationKey: 'abandoned_station',
        currentLocationName: '废弃车站',
        stats: {
          hp: 100, maxHp: 100, stamina: 50, maxStamina: 50, exp: 0, maxExp: 100,
          constellation: 'morning_star',
        },
      },
      playerName: '测试读者',
      recentLogs: [] as Array<Record<string, unknown>>,
      inventorySummary: [{ itemKey: 'old_key', itemName: '旧钥匙', quantity: 1 }],
      titles: [{ titleKey: 'station_watcher', name: '车站守望者' }],
      restState: null,
      activePanel: '',
      setActivePanel: mocks.setActivePanel,
      applyBootstrap: mocks.applyBootstrap,
      appendRecentLogs: mocks.appendRecentLogs,
      replaceTransientActionLogs: mocks.replaceTransientActionLogs,
      patchPlayer: mocks.patchPlayer,
      setBroadcastSummary: mocks.setBroadcastSummary,
      applyRestState: mocks.applyRestState,
    });

    mocks.applyBootstrap.mockImplementation((payload: { recentLogs?: unknown[] }) => {
      mocks.gameStore!.recentLogs = payload.recentLogs || [];
    });
    mocks.appendRecentLogs.mockImplementation((entries: unknown[]) => {
      mocks.gameStore!.recentLogs = [...mocks.gameStore!.recentLogs, ...entries];
    });
    mocks.getBootstrap.mockResolvedValue({ recentLogs: [{ id: 20, type: 'exploration', message: '进入废弃车站' }] });
    mocks.startExplore.mockResolvedValue(makeResult());
    mocks.getPendingStories.mockResolvedValue({ pending_stories: [] });
    mocks.getPendingBattles.mockResolvedValue({ pending_battles: [] });
    mocks.getActiveBroadcasts.mockResolvedValue([]);
    mocks.invalidateQueries.mockResolvedValue(undefined);
  });

  afterEach(() => {
    for (const wrapper of wrappers.splice(0)) wrapper.unmount();
    document.body.innerHTML = '';
    localStorage.clear();
    sessionStorage.clear();
  });

  function mountLayout() {
    const wrapper = shallowMount(GameLayout);
    wrappers.push(wrapper);
    return wrapper;
  }

  it('新角色选择背后星前不读取待处理剧情或战斗，选择后才恢复队列', async () => {
    delete mocks.gameStore!.player.stats.constellation;
    mountLayout();
    await flushPromises();

    expect(mocks.getPendingStories).not.toHaveBeenCalled();
    expect(mocks.getPendingBattles).not.toHaveBeenCalled();

    mocks.gameStore!.player.stats.constellation = 'morning_star';
    await nextTick();
    await flushPromises();

    expect(mocks.getPendingStories).toHaveBeenCalledWith(7);
    expect(mocks.getPendingBattles).toHaveBeenCalledWith(7);
  });

  it('普通探索不弹结果窗，并把已结算奖励追加到主日志', async () => {
    const wrapper = mountLayout();

    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();

    expect(mocks.startExplore).toHaveBeenCalledWith(7, 'abandoned_station', expect.any(String));
    expect(sessionStorage.length).toBe(0);
    expect(mocks.getBootstrap).toHaveBeenCalledOnce();
    expect(mocks.applyBootstrap).toHaveBeenCalledOnce();
    expect(mocks.appendRecentLogs).toHaveBeenCalledWith([
      { message: '获得星币 +12', type: 'reward' },
      { message: '获得掉落：旧钥匙', type: 'reward' },
    ]);
    expect(wrapper.findComponent(ExploreResultDialog).props('visible')).toBe(false);
    expect(wrapper.findComponent(GameLogPanel).props('logs')).toEqual([
      { id: 20, type: 'exploration', message: '进入废弃车站' },
      { message: '获得星币 +12', type: 'reward' },
      { message: '获得掉落：旧钥匙', type: 'reward' },
    ]);
    expect(wrapper.findComponent(GameLogPanel).props('titles')).toEqual([
      { titleKey: 'station_watcher', name: '车站守望者' },
    ]);
  });

  it('探索结算后刷新仍打开的任务面板，让探索次数与可领取状态及时更新', async () => {
    mocks.gameStore!.activePanel = 'quests';
    const wrapper = mountLayout();

    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();

    expect(mocks.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['feature-panel', 'quests', 7],
    });
  });

  it('休息时明确提示先结束休息，并阻止发送探索请求', async () => {
    mocks.gameStore!.restState = { isResting: true };
    const wrapper = mountLayout();
    const executeButton = wrapper.get('.ling-action-main');

    expect(executeButton.attributes('title')).toBe('休息期间不能探索，请先结束休息');
    expect(executeButton.attributes('aria-label')).toBe('休息期间不能执行场景，请先结束休息');
    expect(wrapper.text()).toContain('结束休息后可继续探索');

    await executeButton.trigger('click');
    await flushPromises();

    expect(mocks.showAlert).toHaveBeenCalledWith(
      '休息中无法探索',
      '请先结束当前休息状态，再执行场景。休息期间生命、体力与经验仍会持续恢复。',
    );
    expect(mocks.startExplore).not.toHaveBeenCalled();
  });

  it('普通探索的持久日志已包含本次结果时不重复追加前端临时奖励', async () => {
    mocks.startExplore.mockResolvedValue(makeResult());
    mocks.getBootstrap.mockResolvedValue({ recentLogs: [{
      id: 21,
      type: 'exploration',
      message: '已探索补给箱',
      metadata: { kind: 'exploration_result', eventKey: 'ordinary' },
    }] });
    const wrapper = mountLayout();

    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();

    expect(mocks.applyBootstrap).toHaveBeenCalledOnce();
    expect(mocks.appendRecentLogs).not.toHaveBeenCalled();
  });

  it('探索请求超时后重试会复用同一个请求键，成功确认后才清除', async () => {
    mocks.startExplore
      .mockRejectedValueOnce(new Error('网络超时'))
      .mockResolvedValueOnce(makeResult());
    const wrapper = mountLayout();

    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();
    const firstRequestKey = mocks.startExplore.mock.calls[0]?.[2];
    expect(firstRequestKey).toEqual(expect.any(String));
    expect(sessionStorage.length).toBe(1);

    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();

    expect(mocks.startExplore.mock.calls[1]?.[2]).toBe(firstRequestKey);
    expect(sessionStorage.length).toBe(0);
  });

  it('切换场景不会复用其他场景的未确认请求键', async () => {
    mocks.startExplore
      .mockRejectedValueOnce(new Error('网络超时'))
      .mockRejectedValueOnce(new Error('网络超时'))
      .mockResolvedValueOnce(makeResult());
    const wrapper = mountLayout();

    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();
    const firstSceneKey = mocks.startExplore.mock.calls[0]?.[2];

    mocks.gameStore!.player.currentLocationKey = 'quiet_archive';
    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();
    const secondSceneKey = mocks.startExplore.mock.calls[1]?.[2];
    expect(secondSceneKey).not.toBe(firstSceneKey);

    mocks.gameStore!.player.currentLocationKey = 'abandoned_station';
    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();

    expect(mocks.startExplore.mock.calls[2]?.[2]).toBe(firstSceneKey);
    expect(sessionStorage.length).toBe(1);
  });

  it('服务端重放已确认的探索不会再次追加临时奖励日志', async () => {
    const replay = makeResult({ request_replayed: true });
    mocks.startExplore.mockResolvedValue(replay);
    mocks.gameStore!.recentLogs = [{ id: 20, type: 'exploration', message: '旧的探索记录' }];
    mocks.getBootstrap.mockResolvedValue({ recentLogs: [{
      id: 20,
      type: 'exploration',
      message: '旧的探索记录',
      metadata: { kind: 'exploration_result', eventKey: 'ordinary' },
    }] });
    const wrapper = mountLayout();

    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();

    expect(mocks.appendRecentLogs).not.toHaveBeenCalled();
  });

  it('探索已成功但 bootstrap 刷新失败时仍展示成功结果并保留摘要奖励', async () => {
    const explorePayload = makeResult({
      new_logs: [{ message: '翻开补给箱（消耗体力5）', type: 'exploration' }],
      player: { id: 7, coins: 12, stats: { stamina: 45 } },
    });
    mocks.startExplore.mockResolvedValue(explorePayload);
    mocks.getBootstrap.mockRejectedValue(new Error('网络暂时不可用'));
    const wrapper = mountLayout();

    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();

    expect(mocks.patchPlayer).toHaveBeenCalledWith(explorePayload.player);
    expect(mocks.appendRecentLogs).toHaveBeenCalledWith([
      { message: '翻开补给箱（消耗体力5）', type: 'exploration' },
      { message: '获得星币 +12', type: 'reward' },
      { message: '获得掉落：旧钥匙', type: 'reward' },
    ]);
    expect(mocks.showAlert).not.toHaveBeenCalled();
    expect(wrapper.findComponent(ExploreResultDialog).props('visible')).toBe(false);
  });

  it('bootstrap 刷新失败也不丢弃已成功探索得到的剧情决策', async () => {
    const explorePayload = makeResult({
      result_type: 'story',
      result: {
        event_key: 'branch_after_refresh_failure',
        event_type: 'story',
        name: '未完的选择',
        description: '故事仍在等待你的决定。',
        choices: [{ label: '继续', consequence_text: '' }],
      },
      new_logs: [{ message: '发现未完的选择（消耗体力5）', type: 'story' }],
    });
    mocks.startExplore.mockResolvedValue(explorePayload);
    mocks.getBootstrap.mockRejectedValue(new Error('网络暂时不可用'));
    const wrapper = mountLayout();

    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();

    const dialog = wrapper.findComponent(ExploreResultDialog);
    expect(dialog.props('visible')).toBe(true);
    expect(dialog.props('result')).toEqual(explorePayload);
    expect(mocks.appendRecentLogs).toHaveBeenCalledWith([
      { message: '发现未完的选择（消耗体力5）', type: 'story' },
      { message: '获得星币 +12', type: 'reward' },
      { message: '获得掉落：旧钥匙', type: 'reward' },
    ]);
    expect(mocks.showAlert).not.toHaveBeenCalled();
  });

  it('剧情选择事件保留决策弹窗，不把奖励提前重复追加到日志', async () => {
    const explorePayload = makeResult({
      result_type: 'story',
      result: {
        event_key: 'branch',
        event_type: 'story',
        name: '分岔路口',
        description: '前方出现两条道路。',
        choices: [{ label: '左转', consequence_text: '你走向左侧。' }],
      },
    });
    mocks.startExplore.mockResolvedValue(explorePayload);
    mocks.getBootstrap.mockResolvedValue({ recentLogs: [{
      id: 21,
      type: 'story',
      message: '分岔路口',
      metadata: { kind: 'exploration_result', eventKey: 'branch' },
    }] });

    const wrapper = mountLayout();
    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();

    const dialog = wrapper.findComponent(ExploreResultDialog);
    expect(dialog.props('visible')).toBe(true);
    const result = dialog.props('result');
    if (!result) throw new Error('剧情弹窗缺少探索结果');
    expect(result.result.event_key).toBe('branch');
    expect(mocks.appendRecentLogs).not.toHaveBeenCalled();
    expect(wrapper.findComponent(GameLogPanel).props('logs')).toEqual([
      {
        id: 21,
        type: 'story',
        message: '分岔路口',
        metadata: { kind: 'exploration_result', eventKey: 'branch' },
      },
    ]);

    dialog.vm.$emit('close');
    await flushPromises();
    expect(dialog.props('visible')).toBe(false);
    expect(wrapper.get('.ling-action-pending-story').text()).toContain('继续剧情');

    await wrapper.get('.ling-action-pending-story').trigger('click');
    expect(dialog.props('visible')).toBe(true);
    const resumedResult = dialog.props('result');
    if (!resumedResult) throw new Error('待选剧情恢复后弹窗缺少结果');
    expect(resumedResult.result.event_key).toBe('branch');
    expect(mocks.getPendingStories).toHaveBeenCalledTimes(1);

    mocks.getPendingStories.mockResolvedValue({ pending_stories: [] });
    dialog.vm.$emit('choice-made', 'branch');
    await flushPromises();
    expect(mocks.getPendingStories).toHaveBeenCalledTimes(2);
    expect(await mocks.getPendingStories.mock.results[1]?.value).toEqual({ pending_stories: [] });
    await nextTick();
    expect(wrapper.find('.ling-action-pending-story').exists()).toBe(false);
  });

  it('重新进入游戏后从服务端恢复待选剧情', async () => {
    const pendingStory = makeResult({
      result_type: 'story',
      result: {
        event_key: 'restored_branch',
        event_type: 'story',
        name: '未完的选择',
        description: '那本笔记仍在等待你的回应。',
        choices: [{ label: '继续阅读', consequence_text: '' }],
      },
      rewards: { coins: 18 },
    });
    mocks.getPendingStories.mockResolvedValue({ pending_stories: [pendingStory] });

    const wrapper = mountLayout();
    await flushPromises();

    expect(mocks.getPendingStories).toHaveBeenCalledWith(7);
    expect(wrapper.get('.ling-action-pending-story').text()).toContain('继续剧情');
    await wrapper.get('.ling-action-pending-story').trigger('click');

    const dialog = wrapper.findComponent(ExploreResultDialog);
    expect(dialog.props('visible')).toBe(true);
    const resumedResult = dialog.props('result');
    if (!resumedResult) throw new Error('恢复弹窗缺少待选剧情');
    expect(resumedResult.result.event_key).toBe('restored_branch');
    expect(resumedResult.rewards.coins).toBe(18);
  });

  it('剧情选择触发的战斗立即接续，不等待待选剧情列表刷新', async () => {
    const pendingBattle = makeResult({
      result_type: 'battle',
      request_key: 'story-battle-request',
      monster_key: 'iron_king_trial',
      result: {
        event_key: 'chungmuro_iron_king_story_choice_0',
        event_type: 'story_battle',
        name: '铁王的钢铁化试炼',
        description: '铁王的投影挡在前方。',
      },
    });
    let releasePendingStoryRefresh!: (value: { pending_stories: ExploreResult[] }) => void;
    const wrapper = mountLayout();
    await flushPromises();
    mocks.getPendingStories.mockImplementation(() => new Promise((resolve) => {
      releasePendingStoryRefresh = resolve;
    }));
    const dialog = wrapper.findComponent(ExploreResultDialog);

    dialog.vm.$emit('close');
    dialog.vm.$emit('choice-made', 'chungmuro_iron_king_story', pendingBattle);
    await nextTick();

    expect(dialog.props('visible')).toBe(true);
    expect(dialog.props('result')?.request_key).toBe('story-battle-request');
    expect(wrapper.find('.ling-action-pending-battle').exists()).toBe(false);

    releasePendingStoryRefresh({ pending_stories: [] });
    await flushPromises();
  });

  it('战斗遭遇事件只打开决策弹窗，不走普通奖励追加路径', async () => {
    const explorePayload = makeResult({
      result_type: 'battle',
      request_key: 'explore-battle-request',
      monster_key: 'station_rat',
      result: { event_key: 'battle', event_type: 'battle_placeholder', name: '变异站鼠', description: '' },
    });
    mocks.startExplore.mockResolvedValue(explorePayload);
    mocks.getBootstrap.mockResolvedValue({ recentLogs: [{
      id: 21,
      type: 'exploration',
      message: '发现变异站鼠',
      metadata: { kind: 'exploration_result', eventKey: 'battle' },
    }] });

    const wrapper = mountLayout();
    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();

    const dialog = wrapper.findComponent(ExploreResultDialog);
    expect(dialog.props('visible')).toBe(true);
    const result = dialog.props('result');
    if (!result) throw new Error('战斗弹窗缺少探索结果');
    expect(result.monster_key).toBe('station_rat');
    expect(mocks.appendRecentLogs).not.toHaveBeenCalled();
  });

  it('战斗确认结算后再次刷新任务面板，更新胜场任务进度', async () => {
    const explorePayload = makeResult({
      result_type: 'battle',
      request_key: 'explore-battle-request',
      monster_key: 'station_rat',
      result: { event_key: 'battle', event_type: 'battle_placeholder', name: '变异站鼠', description: '' },
    });
    mocks.startExplore.mockResolvedValue(explorePayload);
    mocks.gameStore!.activePanel = 'quests';
    const wrapper = mountLayout();

    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();
    expect(mocks.invalidateQueries).toHaveBeenCalledTimes(1);

    wrapper.findComponent(ExploreResultDialog).vm.$emit('battle-resolved', 'explore-battle-request', true);
    await flushPromises();

    expect(mocks.invalidateQueries).toHaveBeenCalledTimes(2);
    expect(mocks.invalidateQueries).toHaveBeenLastCalledWith({
      queryKey: ['feature-panel', 'quests', 7],
    });
  });

  it('刷新后从服务端恢复同一待处理战斗，不能通过再次探索跳过它', async () => {
    const pendingBattle = makeResult({
      result_type: 'battle',
      request_key: 'explore-pending-1',
      monster_key: 'station_rat',
      result: { event_key: 'station_rat_event', event_type: 'battle_placeholder', name: '变异站鼠', description: '' },
    });
    mocks.getPendingBattles.mockResolvedValue({ pending_battles: [pendingBattle] });

    const wrapper = mountLayout();
    await flushPromises();

    const dialog = wrapper.findComponent(ExploreResultDialog);
    expect(dialog.props('visible')).toBe(true);
    expect(dialog.props('result')).toEqual(pendingBattle);
    await wrapper.get('.ling-action-main').trigger('click');
    await flushPromises();
    expect(mocks.startExplore).not.toHaveBeenCalled();
    expect(dialog.props('visible')).toBe(true);
  });

  it('关闭战斗弹窗会保留遭遇，服务端确认后才从待处理列表移除', async () => {
    const pendingBattle = makeResult({
      result_type: 'battle',
      request_key: 'explore-pending-2',
      monster_key: 'station_rat',
      result: { event_key: 'station_rat_event_2', event_type: 'battle_placeholder', name: '变异站鼠', description: '' },
    });
    mocks.getPendingBattles.mockResolvedValue({ pending_battles: [pendingBattle] });

    const wrapper = mountLayout();
    await flushPromises();
    const dialog = wrapper.findComponent(ExploreResultDialog);
    dialog.vm.$emit('close');
    await nextTick();

    expect(wrapper.get('.ling-action-pending-battle').text()).toContain('继续遭遇');
    dialog.vm.$emit('battle-resolved', 'explore-pending-2', true);
    await nextTick();
    expect(wrapper.find('.ling-action-pending-battle').exists()).toBe(false);
  });
});
