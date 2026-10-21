import { describe, expect, it } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import GameLogPanel from '@/components/log/GameLogPanel.vue';

describe('GameLogPanel', () => {
  it('将升级里程碑显示为中文成长日志', () => {
    const wrapper = mount(GameLogPanel, {
      props: {
        logs: [{ id: 1, type: 'milestone', message: '你的修为提升至等级 2，获得 3 点自由属性点。' }],
      },
    });

    expect(wrapper.text()).toContain('[成长]');
    expect(wrapper.text()).toContain('你的修为提升至等级 2');
  });

  it('显示日志时间和对应事件类型', () => {
    const createdAt = '2026-09-23T14:32:00.000Z';
    const wrapper = mount(GameLogPanel, {
      props: {
        logs: [{ id: 1, type: 'battle', message: '击败了变异站鼠', createdAt }],
      },
    });

    const time = wrapper.get('time.ling-log-time');
    expect(time.attributes('datetime')).toBe(createdAt);
    expect(time.text()).toMatch(/^\[\d{2}:\d{2}\]$/);
    expect(wrapper.text()).toContain('[战斗]');
    expect(wrapper.text()).toContain('击败了变异站鼠');
  });

  it('没有有效时间时不显示空时间标签', () => {
    const wrapper = mount(GameLogPanel, {
      props: {
        logs: [
          { id: 1, type: 'reward', message: '获得经验 +10' },
          { id: 2, type: 'story', message: '向前走', createdAt: 'not-a-date' },
        ],
      },
    });

    expect(wrapper.findAll('time.ling-log-time')).toHaveLength(0);
    expect(wrapper.text()).toContain('[奖励]');
    expect(wrapper.text()).toContain('[故事]');
  });

  it('从服务端持久化 metadata 展开战斗回合与奖励，保留唯一摘要', () => {
    const summary = '击败了 变异站鼠（1回合）';
    const wrapper = mount(GameLogPanel, {
      props: {
        logs: [{
          id: 9,
          type: 'battle',
          message: summary,
          metadata: {
            kind: 'combat_result',
            result: 'win',
            monster: { monsterKey: 'station_rat', name: '变异站鼠' },
            totalRounds: 1,
            rounds: [{ round: 1, actions: [
              { actor: 'player', damage: 20, critical: true, targetHpAfter: 0 },
            ] }],
            rewards: { exp: 50, coins: 0, storyFragments: 0, channelHeat: 0 },
            drops: [],
            newLogs: [{ message: summary, type: 'battle' }],
          },
        }],
      },
    });

    expect(wrapper.text().match(/击败了 变异站鼠（1回合）/g)).toHaveLength(1);
    expect(wrapper.text()).toContain('第1回合：你对变异站鼠造成20点伤害（暴击），变异站鼠剩余生命 0。');
    expect(wrapper.text()).toContain('获得经验值 +50');
  });

  it('技能解锁日志显示实际消耗的资源', () => {
    const wrapper = mount(GameLogPanel, {
      props: {
        logs: [{
          id: 18,
          type: 'skill_unlock',
          message: '习得技能: 档案脉冲',
          metadata: {
            kind: 'skill_unlock',
            skillKey: 'archive_pulse',
            costs: { coins: 40, storyFragments: 3 },
          },
        }],
      },
    });

    expect(wrapper.text()).toContain('[技能解锁]');
    expect(wrapper.text()).toContain('消耗星币 40');
    expect(wrapper.text()).toContain('消耗故事碎片 3');
  });

  it('把任务称号奖励的内部标识翻译为已持有称号名', () => {
    const wrapper = mount(GameLogPanel, {
      props: {
        logs: [{
          id: 21,
          type: 'quest',
          message: '领取了任务「初露锋芒」的奖励',
          metadata: {
            kind: 'quest_reward',
            rewards: { titleKey: 'station_watcher' },
            messages: ['获得称号: station_watcher'],
          },
        }],
        titles: [{ titleKey: 'station_watcher', name: '车站守望者' }],
      },
    });

    expect(wrapper.text()).toContain('获得称号：车站守望者');
    expect(wrapper.text()).not.toContain('获得称号：station_watcher');
  });

  it('把未领取任务的过期结果展示为任务历程而非奖励', () => {
    const wrapper = mount(GameLogPanel, {
      props: {
        logs: [{
          id: 22,
          type: 'quest',
          message: '任务「探索世界」已过期，未领取奖励已失效',
          metadata: {
            kind: 'quest_expired',
            questKey: 'daily_explore_3',
            questTitle: '探索世界',
            cycleKey: '2026-09-24',
            progress: 3,
            targetValue: 3,
          },
        }],
      },
    });

    expect(wrapper.text()).toContain('任务周期：2026-09-24');
    expect(wrapper.text()).toContain('到期进度：3 / 3');
    expect(wrapper.text()).not.toContain('获得奖励');
  });

  it('翻阅旧日志时保留滚动位置，并可点击提示跳到最新记录', async () => {
    const logs = [{ id: 1, type: 'story', message: '旧的故事记录' }];
    const wrapper = mount(GameLogPanel, { props: { logs } });
    const scroll = wrapper.get('.ling-log-scroll').element as HTMLDivElement;
    Object.defineProperty(scroll, 'scrollHeight', { configurable: true, value: 900 });
    Object.defineProperty(scroll, 'clientHeight', { configurable: true, value: 180 });
    await nextTick();

    scroll.scrollTop = 72;
    await wrapper.get('.ling-log-scroll').trigger('scroll');
    await wrapper.setProps({ logs: [...logs, { id: 2, type: 'battle', message: '新的战斗记录' }] });
    await flushPromises();

    expect(scroll.scrollTop).toBe(72);
    expect(wrapper.get('.log-new-entries').text()).toContain('1 条新日志');

    await wrapper.get('.log-new-entries').trigger('click');
    await nextTick();
    expect(scroll.scrollTop).toBe(900);
    expect(wrapper.find('.log-new-entries').exists()).toBe(false);
  });

  it('玩家正在看最新记录时，新日志仍自动跟随到底部', async () => {
    const logs = [{ id: 1, type: 'story', message: '已有记录' }];
    const wrapper = mount(GameLogPanel, { props: { logs } });
    const scroll = wrapper.get('.ling-log-scroll').element as HTMLDivElement;
    Object.defineProperty(scroll, 'scrollHeight', { configurable: true, value: 900 });
    Object.defineProperty(scroll, 'clientHeight', { configurable: true, value: 180 });
    await nextTick();

    scroll.scrollTop = 712;
    await wrapper.get('.ling-log-scroll').trigger('scroll');
    await wrapper.setProps({ logs: [...logs, { id: 2, type: 'battle', message: '最新记录' }] });
    await flushPromises();

    expect(scroll.scrollTop).toBe(900);
    expect(wrapper.find('.log-new-entries').exists()).toBe(false);
  });
});
