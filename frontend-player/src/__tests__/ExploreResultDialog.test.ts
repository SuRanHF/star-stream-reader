import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const mocks = vi.hoisted(() => ({
  showAlert: vi.fn(),
  applyBootstrap: vi.fn(),
  appendRecentLogs: vi.fn(),
  replaceTransientActionLogs: vi.fn(),
  store: {
    recentLogs: [] as Array<Record<string, unknown>>,
    player: { id: 1, playerId: 1, player_name: 'Test', hp: 80, maxHp: 100, coins: 100, storyFragments: 10 },
  },
}));

vi.mock('@/api/exploreApi', () => ({
  exploreApi: { makeChoice: vi.fn(), acknowledgePendingBattle: vi.fn() },
}));
vi.mock('@/api/combatApi', () => ({
  combatApi: { startCombat: vi.fn(), resolveCombat: vi.fn() },
}));
vi.mock('@/api/gameApi', () => ({
  gameApi: { getBootstrap: vi.fn() },
}));
vi.mock('@/stores/uiStore', () => ({
  useUiStore: () => ({ showAlert: mocks.showAlert }),
}));
vi.mock('@/stores/gameStore', () => ({
  useGameStore: () => ({
    player: mocks.store.player,
    inventorySummary: [
      { itemKey: 'old_key', itemName: '旧钥匙' },
      { itemKey: 'scrap_metal', itemName: '废旧金属' },
    ],
    get recentLogs() { return mocks.store.recentLogs; },
    applyBootstrap: mocks.applyBootstrap,
    appendRecentLogs: mocks.appendRecentLogs,
    replaceTransientActionLogs: mocks.replaceTransientActionLogs,
  }),
}));

import ExploreResultDialog from '@/components/explore/ExploreResultDialog.vue';
import { ApiHttpError } from '@/api/http';
import { exploreApi, type ChoiceResult, type ExploreResult } from '@/api/exploreApi';
import { combatApi, type CombatResolveResponse, type CombatResult } from '@/api/combatApi';
import { gameApi } from '@/api/gameApi';
import { getPendingStoryChoiceRequest } from '@/utils/storyChoiceRequestKey';
import { getPendingCombatRequestKey } from '@/utils/pendingCombatRequestKey';
import { getPendingCombatAction } from '@/utils/pendingCombatAction';

function makeResult(overrides: Partial<ExploreResult> = {}): ExploreResult {
  return {
    result_type: 'resource',
    result: { event_key: 'test', event_type: 'resource', name: '测试', description: '' },
    rewards: {},
    progress_effects: {},
    new_titles: [],
    stories_exhausted: false,
    stories_blocked: false,
    ...overrides,
  };
}

function makeCombatResult(overrides: Partial<CombatResult> = {}): CombatResult {
  return {
    result: 'win',
    monster: {
      monsterKey: 'station_rat', name: '变异站鼠', type: 'monster', rarity: 'common',
      hpBefore: 20, hpAfter: 0,
    },
    playerStats: {
      level: 1, hp: 80, maxHp: 100, attack: 10, defense: 5, speed: 5,
      critRate: 0, critDamage: 1.5, bonuses: {},
    },
    hpBefore: 100,
    hpAfter: 80,
    totalRounds: 1,
    rounds: [{ round: 1, actions: [
      { actor: 'player', type: 'attack', damage: 20, critical: true, targetHpAfter: 0 },
    ] }],
    rewards: { exp: 50, coins: 20, storyFragments: 0, channelHeat: 0 },
    drops: [{ itemKey: 'scrap_metal', quantity: 2 }],
    newLogs: [{ message: '击败了 变异站鼠（1回合）', type: 'battle' }],
    ...overrides,
  };
}

function makeFleeResponse(): CombatResolveResponse {
  return {
    success: true,
    message: '你从变异站鼠面前撤退了。',
    battle: {
      result: 'escape',
      totalRounds: 0,
      playerHpRemaining: 80,
      monster: { monsterKey: 'station_rat', name: '变异站鼠' },
    },
  };
}

function makeStoryResult(): ExploreResult {
  return makeResult({
    result_type: 'story',
    result: {
      event_key: 'choice-reward',
      event_type: 'story',
      name: '分岔路口',
      description: '前方传来求救声。',
      choices: [
        { label: '救助幸存者', consequence_text: '你留下来。' },
        { label: '继续前进', consequence_text: '你离开了。' },
      ],
    },
    story_instance_key: 'story-instance-1',
    rewards: { coins: 500 },
  });
}

function makeChoiceResult(): ChoiceResult {
  return {
    event_key: 'choice-reward',
    event_name: '分岔路口',
    choice_index: 0,
    choice_label: '救助幸存者',
    consequence_text: '你救下了幸存者。',
    rewards: { coins: 100, storyFragments: 1, items: [{ itemKey: 'old_key' }] },
    unlock_locations: ['old_station'],
    unlock_events: ['hidden_signal'],
    new_titles: ['守望者'],
  };
}

describe('ExploreResultDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.sessionStorage.clear();
    window.localStorage.clear();
    mocks.store.recentLogs = [];
    mocks.store.player.coins = 100;
    mocks.store.player.storyFragments = 10;
    mocks.showAlert.mockResolvedValue(undefined);
    mocks.applyBootstrap.mockImplementation((payload: { recentLogs?: Array<Record<string, unknown>> }) => {
      mocks.store.recentLogs = payload.recentLogs || [];
    });
    vi.mocked(gameApi.getBootstrap).mockResolvedValue({} as never);
    vi.mocked(exploreApi.makeChoice).mockResolvedValue(makeChoiceResult());
    vi.mocked(exploreApi.acknowledgePendingBattle).mockResolvedValue({} as never);
    vi.mocked(combatApi.startCombat).mockResolvedValue(makeCombatResult());
    vi.mocked(combatApi.resolveCombat).mockResolvedValue(makeFleeResponse());
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  it('战斗遭遇只提供迎战、撤退或返回决策', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      stamina_cost_paid: 5,
      monster_key: 'station_rat',
      location_key: 'ruined_station',
      location_name: '废弃车站',
      result: { event_key: 'battle', event_type: 'battle_placeholder', name: '变异站鼠', description: '变异老鼠' },
    }) } });

    expect(document.body.textContent).toContain('变异站鼠');
    expect(document.body.textContent).toContain('遭遇场景：废弃车站');
    expect(document.body.textContent).toContain('本次探索已消耗体力 5 点');
    expect(document.body.textContent).toContain('迎战或撤退都不会再次扣除');
    expect(document.body.textContent).toContain('战斗');
    expect(document.body.textContent).toContain('撤退');
    expect(document.body.querySelector('.sst-battle-result')).toBeNull();
    expect(document.body.querySelector('[data-next-panel="inventory"]')).toBeNull();
  });

  it('战斗成功后刷新状态、把回合和掉落替换进日志并自动关闭', async () => {
    const wrapper = mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
      result: { event_key: 'battle', event_type: 'battle_placeholder', name: '变异站鼠', description: '' },
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();

    const requestKey = vi.mocked(combatApi.startCombat).mock.calls[0][2];
    expect(combatApi.startCombat).toHaveBeenCalledWith(
      1, 'station_rat', expect.any(String), expect.any(AbortSignal),
    );
    expect(gameApi.getBootstrap).toHaveBeenCalledOnce();
    expect(mocks.applyBootstrap).toHaveBeenCalledOnce();
    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledWith(
      expect.any(String),
      expect.arrayContaining([
        expect.objectContaining({ message: '第1回合：你对变异站鼠造成20点伤害（暴击），变异站鼠剩余生命 0。', type: 'battle' }),
        expect.objectContaining({ message: '获得经验值 +50', type: 'reward' }),
        expect.objectContaining({ message: '获得掉落：废旧金属 ×2', type: 'reward' }),
      ]),
    );
    expect(getPendingCombatAction(1, requestKey)).toBeNull();
    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(wrapper.emitted('selectPanel')).toBeUndefined();
    expect(document.body.querySelector('.sst-battle-result')).toBeNull();
  });

  it('战斗失败后也结束遭遇并在日志中区分败北损失与主动消耗', async () => {
    vi.mocked(combatApi.startCombat).mockResolvedValue(makeCombatResult({
      result: 'lose',
      hpAfter: 1,
      rewards: { exp: 0, coins: -10, storyFragments: 0, channelHeat: 0 },
      drops: [],
      rounds: [],
      newLogs: [{ message: '被 变异站鼠 击败了', type: 'battle' }],
    }));
    const wrapper = mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
      request_key: 'loss-explore-key',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();

    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledWith(
      expect.any(String),
      expect.arrayContaining([
        expect.objectContaining({ message: '被 变异站鼠 击败了', type: 'battle' }),
        expect.objectContaining({ message: '损失星币 10', type: 'loss' }),
      ]),
    );
    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(exploreApi.acknowledgePendingBattle).toHaveBeenCalledOnce();
    expect(mocks.showAlert).not.toHaveBeenCalled();
  });

  it('同一帧内连续点击迎战和撤退时只提交第一个战斗动作', async () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
      result: { event_key: 'battle', event_type: 'battle_placeholder', name: '变异站鼠', description: '' },
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    document.body.querySelector<HTMLButtonElement>('.sst-btn-flee')!.click();
    await flushPromises();

    expect(combatApi.startCombat).toHaveBeenCalledOnce();
    expect(combatApi.resolveCombat).not.toHaveBeenCalled();
  });

  it('战斗请求尚未返回时不能关闭遭遇弹窗', async () => {
    let settleRequest!: (result: CombatResult) => void;
    vi.mocked(combatApi.startCombat).mockImplementation(() => new Promise((resolve) => {
      settleRequest = resolve;
    }));
    const wrapper = mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    document.body.querySelector<HTMLButtonElement>('.sst-btn-close')!.click();
    await flushPromises();
    expect(wrapper.emitted('close')).toBeUndefined();

    settleRequest(makeCombatResult());
    await flushPromises();
    expect(wrapper.emitted('close')).toHaveLength(1);
  });

  it('战斗请求长期无响应时解除转圈、锁定原动作并安全复用请求键重试', async () => {
    vi.useFakeTimers();
    vi.mocked(combatApi.startCombat)
      .mockImplementationOnce(() => new Promise(() => {}))
      .mockResolvedValueOnce(makeCombatResult());
    const wrapper = mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await nextTick();
    const firstRequestKey = vi.mocked(combatApi.startCombat).mock.calls[0][2];
    expect(document.body.querySelector('.sst-battle-status')?.textContent).toContain('战斗中');

    await vi.advanceTimersByTimeAsync(12_000);
    await nextTick();
    expect(document.body.querySelector('.sst-battle-status')).toBeNull();
    expect(document.body.querySelector('.sst-battle-uncertain')?.textContent).toContain('尚未确认');
    expect(document.body.querySelector<HTMLButtonElement>('.sst-btn-flee')?.disabled).toBe(true);
    expect(mocks.showAlert).toHaveBeenCalledWith('战斗结果待确认', expect.any(String));

    vi.useRealTimers();
    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();

    expect(combatApi.startCombat).toHaveBeenCalledTimes(2);
    expect(vi.mocked(combatApi.startCombat).mock.calls[1][2]).toBe(firstRequestKey);
    expect(wrapper.emitted('close')).toHaveLength(1);
  });

  it('刷新恢复待处理遭遇时继续锁定上次尚未确认的动作', async () => {
    vi.useFakeTimers();
    vi.mocked(combatApi.startCombat).mockImplementationOnce(() => new Promise(() => {}));
    const result = makeResult({
      result_type: 'battle',
      request_key: 'explore-receipt-reload',
      monster_key: 'station_rat',
    });
    const firstMount = mount(ExploreResultDialog, { props: { visible: true, result } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await nextTick();
    await vi.advanceTimersByTimeAsync(12_000);
    await flushPromises();
    expect(document.body.querySelector('.sst-battle-uncertain')?.textContent).toContain('尚未确认');
    firstMount.unmount();

    const restoredMount = mount(ExploreResultDialog, { props: { visible: true, result } });
    await nextTick();

    expect(document.body.querySelector('.sst-battle-uncertain')?.textContent).toContain('上次迎战尚未确认');
    expect(document.body.querySelector<HTMLButtonElement>('.sst-btn-flee')?.disabled).toBe(true);
    expect(document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')?.textContent).toContain('重新确认战果');
    restoredMount.unmount();
  });

  it('服务端返回500时将战斗视为待确认，并用原请求键安全重试', async () => {
    vi.mocked(combatApi.startCombat)
      .mockRejectedValueOnce(new ApiHttpError('服务端暂时不可用', 500, 'SERVER_ERROR'))
      .mockResolvedValueOnce(makeCombatResult());
    const wrapper = mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();
    const firstRequestKey = vi.mocked(combatApi.startCombat).mock.calls[0][2];

    expect(document.body.querySelector('.sst-battle-status')).toBeNull();
    expect(document.body.querySelector('.sst-battle-uncertain')?.textContent).toContain('尚未确认');
    expect(document.body.querySelector<HTMLButtonElement>('.sst-btn-flee')?.disabled).toBe(true);
    expect(document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')?.textContent).toContain('重新确认战果');

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();

    expect(combatApi.startCombat).toHaveBeenCalledTimes(2);
    expect(vi.mocked(combatApi.startCombat).mock.calls[1][2]).toBe(firstRequestKey);
    expect(wrapper.emitted('close')).toHaveLength(1);
  });

  it('撤退请求长期无响应时锁定迎战，并能用原请求键安全重试撤退', async () => {
    vi.useFakeTimers();
    vi.mocked(combatApi.resolveCombat)
      .mockImplementationOnce(() => new Promise(() => {}))
      .mockResolvedValueOnce(makeFleeResponse());
    const wrapper = mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-flee')!.click();
    await nextTick();
    const firstRequestKey = vi.mocked(combatApi.resolveCombat).mock.calls[0][3];
    await vi.advanceTimersByTimeAsync(12_000);
    await nextTick();

    expect(document.body.querySelector('.sst-battle-status')).toBeNull();
    expect(document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')?.disabled).toBe(true);
    expect(document.body.querySelector<HTMLButtonElement>('.sst-btn-flee')?.textContent).toContain('重新确认撤退');
    expect(mocks.showAlert).toHaveBeenCalledWith('撤退结果待确认', expect.any(String));

    vi.useRealTimers();
    document.body.querySelector<HTMLButtonElement>('.sst-btn-flee')!.click();
    await flushPromises();

    expect(combatApi.resolveCombat).toHaveBeenCalledTimes(2);
    expect(vi.mocked(combatApi.resolveCombat).mock.calls[1][3]).toBe(firstRequestKey);
    expect(wrapper.emitted('close')).toHaveLength(1);
  });

  it('服务端明确拒绝战斗后解除转圈并允许重新确认', async () => {
    let dismissAlert!: () => void;
    mocks.showAlert.mockImplementation(() => new Promise<void>((resolve) => {
      dismissAlert = resolve;
    }));
    vi.mocked(combatApi.startCombat).mockRejectedValue(
      new ApiHttpError('战斗请求不符合当前状态', 400, 'BAD_REQUEST'),
    );
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();
    const requestKey = vi.mocked(combatApi.startCombat).mock.calls[0][2];

    expect(mocks.showAlert).toHaveBeenCalledOnce();
    expect(document.body.querySelector('.sst-battle-status')).toBeNull();
    expect(document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')?.disabled).toBe(false);
    expect(document.body.querySelector('.sst-battle-uncertain')).toBeNull();
    expect(getPendingCombatAction(1, requestKey)).toBeNull();

    dismissAlert();
    await flushPromises();
  });

  it('战斗已结算但 bootstrap 刷新失败时仍写日志并关闭，不显示战斗失败', async () => {
    vi.mocked(gameApi.getBootstrap).mockRejectedValue(new Error('网络暂时不可用'));
    const wrapper = mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();

    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledOnce();
    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(mocks.showAlert).not.toHaveBeenCalled();
  });

  it('战斗已结算但 bootstrap 刷新一直未返回时仍立即写日志并关闭', async () => {
    vi.mocked(gameApi.getBootstrap).mockImplementation(() => new Promise(() => {}));
    const wrapper = mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();

    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledOnce();
    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(mocks.showAlert).not.toHaveBeenCalled();
  });

  it('本地先反馈战斗结果，持久日志异步刷新后仍只有一条摘要', async () => {
    vi.mocked(gameApi.getBootstrap).mockResolvedValue({ recentLogs: [{
      id: 100,
      type: 'battle',
      message: '击败了 变异站鼠（1回合）',
      metadata: { kind: 'combat_result' },
    }] } as never);
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();

    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledOnce();
    expect(mocks.store.recentLogs).toHaveLength(1);
  });

  it('请求失败后重试同一遭遇时复用原请求键', async () => {
    vi.mocked(combatApi.startCombat)
      .mockRejectedValueOnce(new Error('网络暂时不可用'))
      .mockResolvedValueOnce(makeCombatResult());
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();
    const firstRequestKey = vi.mocked(combatApi.startCombat).mock.calls[0][2];

    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();

    expect(combatApi.startCombat).toHaveBeenCalledTimes(2);
    expect(vi.mocked(combatApi.startCombat).mock.calls[1][2]).toBe(firstRequestKey);
    expect(mocks.showAlert).toHaveBeenCalledOnce();
  });

  it('撤退结算也刷新状态、记录战斗结果并自动关闭', async () => {
    const wrapper = mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
    }) } });

    document.body.querySelector<HTMLButtonElement>('.sst-btn-flee')!.click();
    await flushPromises();

    expect(combatApi.resolveCombat).toHaveBeenCalledWith(
      1, 'station_rat', 'flee', expect.any(String), expect.any(AbortSignal),
    );
    expect(gameApi.getBootstrap).toHaveBeenCalledOnce();
    expect(mocks.applyBootstrap).toHaveBeenCalledOnce();
    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledWith(
      expect.any(String),
      [{ message: '你从变异站鼠面前撤退了。', type: 'battle' }],
    );
    expect(wrapper.emitted('close')).toHaveLength(1);
  });

  it('剧情弹窗只用于选择，不显示资源结果面板', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeStoryResult() } });

    expect(document.body.textContent).toContain('分岔路口');
    expect(document.body.textContent).toContain('救助幸存者');
    expect(document.body.textContent).toContain('继续前进');
    expect(document.body.querySelector('[data-next-panel="inventory"]')).toBeNull();
    expect(document.body.textContent).not.toContain('场景奖励明细');
  });

  it('只呈现服务端为当前玩家筛出的终局个人记忆回响', () => {
    const result = makeStoryResult();
    result.result.personal_contexts = ['你想起病房里的少年。'];
    mount(ExploreResultDialog, { props: { visible: true, result } });

    expect(document.body.textContent).toContain('你的记忆在此刻回响');
    expect(document.body.textContent).toContain('你想起病房里的少年。');
    expect(document.body.querySelectorAll('.sst-personal-context p')).toHaveLength(1);
  });

  it('显示两种资源成本并禁用付不起的选项，明确提示还差多少', () => {
    const result = makeStoryResult();
    result.result.choices![0].rewards_override = { coins: -50, storyFragments: -2 };
    result.result.choices![1].rewards_override = { coins: -20 };
    mocks.store.player.coins = 49;
    mocks.store.player.storyFragments = 1;
    mount(ExploreResultDialog, { props: { visible: true, result } });

    const choices = document.body.querySelectorAll<HTMLButtonElement>('.sst-choice-btn');
    expect(choices[0].textContent).toContain('消耗：50 星币、2 故事碎片');
    expect(choices[0].textContent).toContain('余额不足，还差 1 星币、1 故事碎片');
    expect(choices[0].disabled).toBe(true);
    expect(choices[1].disabled).toBe(false);

    choices[0].click();
    expect(exploreApi.makeChoice).not.toHaveBeenCalled();
  });

  it('余额恰好达到剧情成本时仍允许选择', () => {
    const result = makeStoryResult();
    result.result.choices![0].rewards_override = { coins: -50, storyFragments: -2 };
    mocks.store.player.coins = 50;
    mocks.store.player.storyFragments = 2;
    mount(ExploreResultDialog, { props: { visible: true, result } });

    expect(document.body.querySelectorAll<HTMLButtonElement>('.sst-choice-btn')[0].disabled).toBe(false);
  });

  it('剧情选择成功后记录选择、后果、选择奖励和解锁内容并自动关闭', async () => {
    const eventOrder: string[] = [];
    const wrapper = mount(ExploreResultDialog, { props: {
      visible: true,
      result: makeStoryResult(),
      onClose: () => eventOrder.push('close'),
      onChoiceMade: () => eventOrder.push('choice-made'),
    } });

    document.body.querySelector<HTMLButtonElement>('.sst-choice-btn')!.click();
    await flushPromises();

    expect(exploreApi.makeChoice).toHaveBeenCalledWith(
      1, 'choice-reward', 0, expect.any(String), 'story-instance-1',
    );
    expect(mocks.applyBootstrap).toHaveBeenCalledOnce();
    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledWith(
      expect.any(String),
      expect.arrayContaining([
      expect.objectContaining({ message: '你选择了「救助幸存者」', type: 'story' }),
      expect.objectContaining({ message: '你救下了幸存者。', type: 'story' }),
      expect.objectContaining({ message: '解锁地点：old_station', type: 'explore' }),
      expect.objectContaining({ message: '发现新的故事线索（1）', type: 'explore' }),
      expect.objectContaining({ message: '获得星币 +100', type: 'reward' }),
      expect.objectContaining({ message: '获得故事碎片 +1', type: 'reward' }),
      expect.objectContaining({ message: '获得掉落：旧钥匙', type: 'reward' }),
      expect.objectContaining({ message: '获得称号：守望者', type: 'reward' }),
      ]),
    );
    expect(mocks.appendRecentLogs).not.toHaveBeenCalled();
    expect(wrapper.emitted('choiceMade')).toHaveLength(1);
    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(eventOrder).toEqual(['close', 'choice-made']);
    expect(wrapper.emitted('selectPanel')).toBeUndefined();
  });

  it('剧情选项在提交前说明个人影响范围，但不泄露后续剧情键', () => {
    const result = makeStoryResult();
    result.result.choices![0] = {
      ...result.result.choices![0],
      set_story_flags: { route_has_clue: true },
      unlock_events: ['hidden_followup_event'],
      unlock_locations: ['secret_location'],
      title_bias: { title_truth_seeker: 3 },
      rewards_override: { exp: 25 },
    };
    mount(ExploreResultDialog, { props: { visible: true, result } });

    expect(document.body.querySelector('.sst-choice-scope')?.textContent)
      .toContain('只记入你的个人经历');
    const impact = document.body.querySelector('.sst-choice-impact')?.textContent || '';
    expect(impact).toContain('个人后续剧情');
    expect(impact).toContain('探索地点');
    expect(impact).toContain('称号判定倾向');
    expect(impact).toContain('本次资源结算');
    expect(impact).not.toContain('hidden_followup_event');
    expect(impact).not.toContain('secret_location');
    expect(impact).not.toContain('title_truth_seeker');
  });

  it('显示剧情风险，并把已结算伤害写入历程后再接续待战斗', async () => {
    const result = makeStoryResult();
    result.result.choices![0].risks = {
      damagePercent: 15,
      monsterKey: 'iron_king_trial',
      battleName: '铁王的钢铁化试炼',
    };
    vi.mocked(exploreApi.makeChoice).mockResolvedValue({
      ...makeChoiceResult(),
      applied_risks: { damagePercent: 15, damage: 15, hpBefore: 100, hpAfter: 85, nonLethal: true },
      pending_battle: makeResult({
        result_type: 'battle',
        request_key: 'iron-trial-request',
        monster_key: 'iron_king_trial',
        result: { event_key: 'choice-reward_choice_0', event_type: 'story_battle', name: '铁王的钢铁化试炼', description: '' },
      }),
    });
    const eventOrder: string[] = [];
    mount(ExploreResultDialog, { props: {
      visible: true,
      result,
      onClose: () => eventOrder.push('close'),
      onChoiceMade: (_eventKey, pendingBattle) => {
        expect(pendingBattle?.request_key).toBe('iron-trial-request');
        eventOrder.push('choice-made');
      },
    } });

    expect(document.body.querySelector('.sst-choice-risk')?.textContent)
      .toContain('约损失15%最大生命');
    expect(document.body.querySelector('.sst-choice-risk')?.textContent)
      .toContain('铁王的钢铁化试炼（可撤退）');
    document.body.querySelector<HTMLButtonElement>('.sst-choice-btn')!.click();
    await flushPromises();

    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledWith(
      expect.any(String),
      expect.arrayContaining([
        expect.objectContaining({
          message: '剧情风险造成 15 点伤害（生命 100→85）；此次伤害不会致命。',
          type: 'warning',
        }),
      ]),
    );
    expect(eventOrder).toEqual(['close', 'choice-made']);
  });

  it('bootstrap 刷新失败时剧情选择日志不重复探索阶段已记录的基础奖励', async () => {
    vi.mocked(gameApi.getBootstrap).mockRejectedValue(new Error('网络暂时不可用'));
    mocks.store.recentLogs = [
      { id: -1, type: 'story', message: '发现未完的选择（消耗体力5）' },
      { id: -2, type: 'reward', message: '获得星币 +500' },
    ];
    mount(ExploreResultDialog, { props: { visible: true, result: makeStoryResult() } });

    document.body.querySelector<HTMLButtonElement>('.sst-choice-btn')!.click();
    await flushPromises();

    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledWith(
      expect.any(String),
      expect.arrayContaining([expect.objectContaining({ message: '获得星币 +100', type: 'reward' })]),
    );
    const details = mocks.replaceTransientActionLogs.mock.calls[0][1] as Array<{ message: string }>;
    expect(details.map((entry) => entry.message)).not.toContain('获得星币 +500');
    expect(details.map((entry) => entry.message)).not.toContain('获得星币 +600');
  });

  it('同一帧内重复点击剧情选项时只提交一次并只生成一组日志', async () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeStoryResult() } });

    const choiceButton = document.body.querySelector<HTMLButtonElement>('.sst-choice-btn')!;
    choiceButton.click();
    choiceButton.click();
    await flushPromises();

    expect(exploreApi.makeChoice).toHaveBeenCalledOnce();
    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledOnce();
  });

  it('网络失败后恢复待选剧情时锁定原选项并复用幂等键', async () => {
    vi.mocked(exploreApi.makeChoice)
      .mockRejectedValueOnce(new Error('网络暂时不可用'))
      .mockResolvedValueOnce(makeChoiceResult());
    const result = makeStoryResult();
    result.result.choices![0].rewards_override = { coins: -50, storyFragments: -2 };
    const firstMount = mount(ExploreResultDialog, { props: { visible: true, result } });

    document.body.querySelectorAll<HTMLButtonElement>('.sst-choice-btn')[0].click();
    await flushPromises();
    const requestKey = vi.mocked(exploreApi.makeChoice).mock.calls[0][3];
    expect(window.sessionStorage.length).toBe(1);
    expect(document.body.querySelectorAll<HTMLButtonElement>('.sst-choice-btn')[1].disabled).toBe(true);

    // 服务端可能已经扣费但响应丢失，待确认的原选项仍须复用同一请求键重试。
    mocks.store.player.coins = 0;
    mocks.store.player.storyFragments = 0;
    firstMount.unmount();
    mount(ExploreResultDialog, { props: { visible: true, result } });
    await flushPromises();
    expect(getPendingStoryChoiceRequest(1, 'choice-reward', 'story-instance-1'))
      .toEqual({ requestKey, choiceIndex: 0 });
    const restoredChoices = document.body.querySelectorAll<HTMLButtonElement>('.sst-choice-btn');
    expect(restoredChoices).toHaveLength(2);
    expect(restoredChoices[0].disabled).toBe(false);
    expect(restoredChoices[1].disabled).toBe(true);
    restoredChoices[0].click();
    await flushPromises();

    expect(exploreApi.makeChoice).toHaveBeenCalledTimes(2);
    expect(vi.mocked(exploreApi.makeChoice).mock.calls[1][3]).toBe(requestKey);
    expect(vi.mocked(exploreApi.makeChoice).mock.calls[1][4]).toBe('story-instance-1');
    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledOnce();
  });

  it('服务端明确拒绝余额不足后清除待确认状态，允许改选其他可支付路线', async () => {
    vi.mocked(exploreApi.makeChoice).mockRejectedValueOnce(
      new ApiHttpError('故事碎片不足，无法选择该选项', 400, 'BAD_REQUEST'),
    );
    const result = makeStoryResult();
    result.result.choices![0].rewards_override = { storyFragments: -2 };
    result.result.choices![1].rewards_override = { coins: -20 };
    const firstMount = mount(ExploreResultDialog, { props: { visible: true, result } });

    document.body.querySelectorAll<HTMLButtonElement>('.sst-choice-btn')[0].click();
    await flushPromises();

    expect(getPendingStoryChoiceRequest(1, 'choice-reward', 'story-instance-1')).toBeNull();
    expect(mocks.showAlert).toHaveBeenCalledWith('错误', '故事碎片不足，无法选择该选项');

    mocks.store.player.coins = 30;
    mocks.store.player.storyFragments = 1;
    firstMount.unmount();
    mount(ExploreResultDialog, { props: { visible: true, result } });
    const choices = document.body.querySelectorAll<HTMLButtonElement>('.sst-choice-btn');
    expect(choices[0].disabled).toBe(true);
    expect(choices[1].disabled).toBe(false);
  });

  it('剧情选择接口成功但状态刷新失败时仍补日志并关闭', async () => {
    vi.mocked(gameApi.getBootstrap).mockRejectedValue(new Error('网络暂时不可用'));
    const wrapper = mount(ExploreResultDialog, { props: { visible: true, result: makeStoryResult() } });

    document.body.querySelector<HTMLButtonElement>('.sst-choice-btn')!.click();
    await flushPromises();

    expect(mocks.replaceTransientActionLogs).toHaveBeenCalledOnce();
    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(mocks.showAlert).not.toHaveBeenCalled();
  });

  it('刷新后收到本次剧情选择的持久日志时不再追加临时重复日志', async () => {
    vi.mocked(gameApi.getBootstrap).mockResolvedValue({ recentLogs: [{
      id: 101,
      type: 'story',
      message: '你选择了: 救助幸存者',
      metadata: { kind: 'story_choice', eventKey: 'choice-reward' },
    }] } as never);
    mount(ExploreResultDialog, { props: { visible: true, result: makeStoryResult() } });

    document.body.querySelector<HTMLButtonElement>('.sst-choice-btn')!.click();
    await flushPromises();

    expect(mocks.replaceTransientActionLogs).not.toHaveBeenCalled();
    expect(mocks.store.recentLogs).toHaveLength(1);
  });

  it('服务端恢复的遭遇跨弹窗重挂载复用探索请求键，并在战斗成功后确认回执', async () => {
    const result = makeResult({
      result_type: 'battle',
      request_key: 'explore-battle-stable-key',
      monster_key: 'station_rat',
      result: { event_key: 'stable-battle', event_type: 'battle_placeholder', name: '变异站鼠', description: '' },
    });
    const firstMount = mount(ExploreResultDialog, { props: { visible: true, result } });
    const firstKey = getPendingCombatRequestKey(1, result);
    firstMount.unmount();

    const secondMount = mount(ExploreResultDialog, { props: { visible: true, result } });
    document.body.querySelector<HTMLButtonElement>('.sst-btn-fight')!.click();
    await flushPromises();

    expect(vi.mocked(combatApi.startCombat).mock.calls[0][2]).toBe(firstKey);
    expect(vi.mocked(combatApi.startCombat).mock.calls[0][2]).toBe('explore-battle-stable-key');
    expect(exploreApi.acknowledgePendingBattle).toHaveBeenCalledWith(1, 'explore-battle-stable-key');
    expect(secondMount.emitted('battleResolved')).toEqual([['explore-battle-stable-key', true]]);
  });
});
