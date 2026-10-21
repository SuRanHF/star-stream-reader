import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const mocks = vi.hoisted(() => ({
  getEquipped: vi.fn(),
  repairAll: vi.fn(),
  getAvatarRankInfo: vi.fn(),
  rankUp: vi.fn(),
  showAlert: vi.fn(),
  showConfirm: vi.fn(),
}));

vi.mock('@/api/equipmentApi', () => ({
  equipmentApi: { getEquipped: mocks.getEquipped, repairAll: mocks.repairAll },
}));
vi.mock('@/api/rankingApi', () => ({
  rankingApi: { getAvatarRankInfo: mocks.getAvatarRankInfo, rankUp: mocks.rankUp },
}));
vi.mock('@/stores/uiStore', () => ({
  useUiStore: () => ({ showAlert: mocks.showAlert, showConfirm: mocks.showConfirm }),
}));

import PlayerStatusPanel from '@/components/player/PlayerStatusPanel.vue';

describe('PlayerStatusPanel combat stat breakdown', () => {
  afterEach(() => {
    vi.clearAllMocks();
    document.body.innerHTML = '';
  });

  it('explains level growth and equipped-title contributions in the attack tooltip', async () => {
    mocks.getEquipped.mockResolvedValue({});
    const wrapper = mount(PlayerStatusPanel, {
      props: {
        player: {
          id: 11,
          playerName: '测试读者',
          stats: {
            constellation: 'morning_star',
            level: 4,
            attack: 49,
            defense: 27,
            speed: 9,
            critRate: 0.15,
            maxHp: 190,
            hp: 100,
            combatProjection: true,
            bonusConstellationName: '钢铁之主',
            bonusAtkConstellation: 15,
            bonusAtkCombatBase: 18,
            bonusDefCombatBase: 9,
            bonusMaxHpCombatBase: 150,
            bonusMaxHpConstellation: 20,
            bonusAtkBase: 10,
            bonusAtkAlloc: 8,
            bonusAtkStoredGrowth: 0,
            bonusAtkLevelGrowth: 6,
            bonusAtkTitle: 25,
            bonusTitleName: '最初读者',
            bonusAtkTitleDetail: '最初读者 +25',
            bonusDefLevelGrowth: 3,
            bonusDefBase: 5,
            bonusDefAlloc: 0,
            bonusDefStoredGrowth: 4,
            bonusDefTitle: 15,
            bonusDefTitleDetail: '最初读者 +15',
            bonusMaxHpBase: 100,
            bonusMaxHpStoredGrowth: 50,
            bonusMaxHpTitle: 20,
            bonusMaxHpTitleDetail: '最初读者 +20',
          },
        },
      },
      global: { stubs: { AllocDialog: true, Teleport: true } },
    });

    const attackRow = wrapper.findAll('.stat-row').find((row) => row.find('.stat-label').text() === '攻击');
    expect(attackRow).toBeDefined();
    await attackRow!.find('.stat-value').trigger('click');

    const tooltip = wrapper.get('.ling-stats-tooltip-text').text();
    expect(tooltip).toContain('等级成长(+6)');
    expect(tooltip).toContain('称号·最初读者 +25');
    expect(tooltip).toContain('= 49');
    expect(tooltip).toContain('背后星·钢铁之主加成(+15)（当前战斗公式暂未计入）');

    const defenseRow = wrapper.findAll('.stat-row').find((row) => row.find('.stat-label').text() === '防御');
    expect(defenseRow).toBeDefined();
    await defenseRow!.find('.stat-value').trigger('click');
    expect(wrapper.get('.ling-stats-tooltip-text').text()).toContain('等级成长(+3)');
    expect(wrapper.get('.ling-stats-tooltip-text').text()).toContain('称号·最初读者 +15');

    const lifeRow = wrapper.findAll('.stat-row').find((row) => row.find('.stat-label').text() === '生命');
    expect(lifeRow).toBeDefined();
    await lifeRow!.find('.stat-value').trigger('click');
    expect(wrapper.get('.ling-stats-tooltip-text').text()).toContain('称号·最初读者 +20');
    expect(wrapper.get('.ling-stats-tooltip-text').text()).toContain('背后星·钢铁之主(+20)');
    expect(wrapper.get('.ling-stats-tooltip-text').text()).toContain('已计入最大生命上限及战斗结算');

    wrapper.unmount();
  });

  it('waits for a selected constellation before loading equipped equipment', async () => {
    mocks.getEquipped.mockResolvedValue({});
    const player = {
      id: 12,
      playerName: '尚未选星的读者',
      stats: { level: 1 },
    };
    const wrapper = mount(PlayerStatusPanel, {
      props: { player },
      global: { stubs: { AllocDialog: true, Teleport: true } },
    });

    expect(mocks.getEquipped).not.toHaveBeenCalled();

    await wrapper.setProps({
      player: {
        ...player,
        stats: { ...player.stats, constellation: 'morning_star' },
      },
    });

    expect(mocks.getEquipped).toHaveBeenCalledTimes(1);
    expect(mocks.getEquipped).toHaveBeenCalledWith(12);
    expect(mocks.getAvatarRankInfo).toHaveBeenCalledTimes(1);
    expect(mocks.getAvatarRankInfo).toHaveBeenCalledWith(12);
    wrapper.unmount();
  });

  it('shows next-rank progress and the concrete boss-clue source before calibration is clicked', async () => {
    mocks.getEquipped.mockResolvedValue({});
    mocks.getAvatarRankInfo.mockResolvedValue({
      currentRank: { key: 'E', displayName: 'E级·剧本幸存者' },
      nextDisplayName: 'D级·频道记录者',
      nextRank: { displayName: 'D级·频道记录者', description: '行动开始被频道记录。' },
      canRankUp: false,
      requirements: [
        { type: 'levelMin', label: '等级 6', current: 2, required: 6, completed: false },
        { type: 'storyEventsTriggeredMin', label: '主线事件', current: 0, required: 1, completed: false },
        {
          type: 'bossClue',
          reqKey: 'station_keeper',
          label: '车站看门人线索',
          current: 0,
          required: 1,
          completed: false,
        },
      ],
    });

    const wrapper = mount(PlayerStatusPanel, {
      props: {
        player: {
          id: 13,
          playerName: '成长中的读者',
          stats: { level: 2, avatarRank: 'E', constellation: 'morning_star' },
        },
      },
      global: { stubs: { AllocDialog: true, Teleport: true } },
    });
    await flushPromises();

    const goal = wrapper.get('[data-testid="rank-goal-panel"]');
    expect(goal.text()).toContain('D级·频道记录者');
    expect(goal.text()).toContain('等级 6');
    expect(goal.text()).toContain('2 / 6');
    expect(goal.text()).toContain('你的个人主线时间线');
    expect(goal.text()).toContain('线索来源：废弃车站 →「看门人的痕迹」');
    expect(mocks.getAvatarRankInfo).toHaveBeenCalledWith(13);

    wrapper.unmount();
  });

  it('refreshes rank requirements when personal story and boss-clue progress changes', async () => {
    mocks.getEquipped.mockResolvedValue({});
    const rankInfo = (storyCurrent: number, clueCurrent: number) => ({
      currentRank: { key: 'E', displayName: 'E级·剧本幸存者' },
      nextDisplayName: 'D级·频道记录者',
      nextRank: { displayName: 'D级·频道记录者', description: '行动开始被频道记录。' },
      canRankUp: storyCurrent === 1 && clueCurrent === 1,
      requirements: [
        { type: 'levelMin', label: '等级 6', current: 6, required: 6, completed: true },
        {
          type: 'storyEventsTriggeredMin',
          label: '主线事件',
          current: storyCurrent,
          required: 1,
          completed: storyCurrent === 1,
        },
        {
          type: 'bossClue',
          reqKey: 'station_keeper',
          label: '车站看门人线索',
          current: clueCurrent,
          required: 1,
          completed: clueCurrent === 1,
        },
      ],
    });
    mocks.getAvatarRankInfo
      .mockResolvedValueOnce(rankInfo(0, 0))
      .mockResolvedValueOnce(rankInfo(1, 0))
      .mockResolvedValueOnce(rankInfo(1, 1));

    const wrapper = mount(PlayerStatusPanel, {
      props: {
        player: {
          id: 14,
          playerName: '时间线读者',
          story_fragments: 0,
          stage_progress: { storyEventsTriggered: [], bossClues: {} },
          stats: { level: 6, avatarRank: 'E', constellation: 'morning_star' },
        },
      },
      global: { stubs: { AllocDialog: true, Teleport: true } },
    });
    await flushPromises();

    let goal = wrapper.get('[data-testid="rank-goal-panel"]');
    expect(goal.get('[data-requirement-type="storyEventsTriggeredMin"]')
      .find('.rank-goal-progress-text').text()).toBe('0 / 1');
    expect(goal.get('[data-requirement-type="bossClue"]')
      .find('.rank-goal-progress-text').text()).toBe('0 / 1');

    await wrapper.setProps({
      player: {
        id: 14,
        playerName: '时间线读者',
        story_fragments: 3,
        stage_progress: { storyEventsTriggered: ['station_first_trace'], bossClues: {} },
        stats: { level: 6, avatarRank: 'E', constellation: 'morning_star' },
      },
    });
    await flushPromises();

    goal = wrapper.get('[data-testid="rank-goal-panel"]');
    expect(goal.get('[data-requirement-type="storyEventsTriggeredMin"]')
      .find('.rank-goal-progress-text').text()).toBe('1 / 1');
    expect(goal.get('[data-requirement-type="bossClue"]')
      .find('.rank-goal-progress-text').text()).toBe('0 / 1');
    expect(mocks.getAvatarRankInfo).toHaveBeenCalledTimes(2);

    await wrapper.setProps({
      player: {
        id: 14,
        playerName: '时间线读者',
        story_fragments: 4,
        stage_progress: {
          storyEventsTriggered: ['station_first_trace'],
          bossClues: { station_keeper: 1 },
        },
        stats: { level: 6, avatarRank: 'E', constellation: 'morning_star' },
      },
    });
    await flushPromises();

    goal = wrapper.get('[data-testid="rank-goal-panel"]');
    expect(goal.get('[data-requirement-type="bossClue"]')
      .find('.rank-goal-progress-text').text()).toBe('1 / 1');
    expect(goal.text()).toContain('条件已齐备，可以提升成长阶。');
    expect(mocks.getAvatarRankInfo).toHaveBeenCalledTimes(3);
    wrapper.unmount();
  });

  it('shows implemented combat stats and the equipment slots defined by the game data', async () => {
    mocks.getEquipped.mockResolvedValue({
      weapon: {
        slot: 'weapon',
        name: '弯曲钢管',
        rarity: 'common',
        equipped: true,
        stats: { attack: 5, critRate: 0.01 },
        special_effects: { storyEventBonus: 0.02 },
        durability: 75,
        max_durability: 100,
      },
      armor: { slot: 'armor', name: '守卫外套', rarity: 'common', equipped: true },
      helmet: { slot: 'helmet', name: '旧头盔', rarity: 'uncommon', equipped: true },
      accessory: { slot: 'accessory', name: '记录仪', rarity: 'rare', equipped: true },
      relic: { slot: 'relic', name: '残页', rarity: 'epic', equipped: true },
    });

    const wrapper = mount(PlayerStatusPanel, {
      props: {
        player: {
          id: 15,
          playerName: '面板验收读者',
          stats: {
            constellation: 'morning_star',
            avatarRankName: 'F级·新晋读者',
            level: 2,
            exp: 150,
            hp: 70,
            maxHp: 100,
            stamina: 20,
            maxStamina: 50,
            attack: 12,
            defense: 7,
            speed: 4,
            critRate: 0.15,
            critDamage: 1.5,
            insight: 3,
            luck: 2,
            spiritRoot: '旧值不应展示',
            lifespan: 999,
            merit: 10,
          },
        },
      },
      global: { stubs: { AllocDialog: true, Teleport: true } },
    });
    await flushPromises();

    const attributes = wrapper.findAll('.panel-section').find(section => section.text().startsWith('属性'))!;
    expect(attributes.findAll('.stat-label').map(label => label.text())).toEqual([
      '生命', '行动力', '攻击', '防御', '速度', '暴击率', '暴击伤害', '战力', '洞察', '幸运',
    ]);
    expect(attributes.text()).toContain('15.0%');
    expect(attributes.text()).toContain('150.0%');
    expect(attributes.text()).not.toMatch(/灵根|寿元|功德|仙缘/);

    const insightRow = attributes.findAll('.stat-row')
      .find(row => row.find('.stat-label').text() === '洞察')!;
    await insightRow.find('.stat-value').trigger('click');
    expect(wrapper.get('.ling-stats-tooltip-text').text()).toContain('部分技能的学习条件会检查洞察');

    expect(wrapper.findAll('.equip-slot').map(slot => slot.find('.slot-name').text())).toEqual([
      '武器', '护甲', '头盔', '饰品', '遗物',
    ]);
    const weapon = wrapper.findAll('.equip-slot').find(slot => slot.find('.slot-name').text() === '武器')!;
    expect(weapon.get('.equip-meta-row').text()).toContain('攻击: 5');
    expect(weapon.get('.equip-meta-row').text()).toContain('暴击率: +1%');
    expect(weapon.get('.equip-meta-row').text()).toContain('故事事件权重加成: +2%');
    expect(weapon.get('.equip-meta-row').text()).toContain('耐久75/100');
    expect(weapon.get('.equip-meta-row').text()).not.toContain('耐久75%');
    expect(wrapper.text()).not.toMatch(/须弥戒|炼丹炉|法相穿搭/);
    wrapper.unmount();
  });
});
