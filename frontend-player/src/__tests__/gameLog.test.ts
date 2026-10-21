import { describe, expect, it } from 'vitest';
import type { CombatResult } from '@/api/combatApi';
import type { ChoiceResult, ExploreResult } from '@/api/exploreApi';
import {
  formatChoiceOutcomeLogs,
  formatCombatLogEntries,
  formatExploreResultLogs,
  formatPersistedLogDetails,
  choiceSummaryMessage,
  formatExploreRewardLogs,
  isInteractiveExploreResult,
} from '@/utils/gameLog';

function makeExploreResult(overrides: Partial<ExploreResult> = {}): ExploreResult {
  return {
    result_type: 'resource',
    result: { event_key: 'resource-event', event_type: 'resource', name: '资源点', description: '' },
    rewards: {},
    progress_effects: {},
    new_titles: [],
    stories_exhausted: false,
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
      { actor: 'monster', type: 'attack', damage: 5, targetHpAfter: 80, suppressionNote: '防御减伤' },
    ] }],
    rewards: { exp: 50, coins: 20, storyFragments: 1, channelHeat: 2 },
    drops: [{ itemKey: 'scrap_metal', quantity: 2 }],
    newLogs: [{ message: '击败了 变异站鼠（1回合）', type: 'battle' }],
    ...overrides,
  };
}

describe('gameLog', () => {
  it('只把战斗和带选项的故事识别为需要玩家决策的弹窗', () => {
    expect(isInteractiveExploreResult(null)).toBe(false);
    expect(isInteractiveExploreResult(makeExploreResult())).toBe(false);
    expect(isInteractiveExploreResult(makeExploreResult({
      result_type: 'battle',
    }))).toBe(true);
    expect(isInteractiveExploreResult(makeExploreResult({
      result: { event_key: 'story', event_type: 'story', name: '故事', description: '' },
    }))).toBe(false);
    expect(isInteractiveExploreResult(makeExploreResult({
      result: {
        event_key: 'story', event_type: 'story', name: '故事', description: '',
        choices: [{ label: '前进', consequence_text: '' }],
      },
    }))).toBe(true);
  });

  it('把探索资源、物品和称号格式化成日志条目', () => {
    const entries = formatExploreRewardLogs(makeExploreResult({
      rewards: {
        exp: 12,
        coins: 100,
        storyFragments: 1,
        worldLineShift: 2,
        items: [{ itemName: '旧钥匙', quantity: 2 }],
        equipment: [{ equipmentKey: 'starfall_blade', equipmentName: '星陨之刃' }],
      },
      new_titles: [{ titleName: '拾荒者' }],
    }), [{ itemKey: 'old_key', itemName: '旧钥匙' }]);

    expect(entries.map((entry) => entry.message)).toEqual([
      '获得经验值 +12',
      '获得星币 +100',
      '获得故事碎片 +1',
      '世界线偏移 +2',
      '获得掉落：旧钥匙 ×2',
      '获得掉落：星陨之刃',
      '获得称号：拾荒者',
    ]);
    expect(entries.every((entry) => entry.type === 'reward')).toBe(true);
  });

  it('按物品和装备标识合并重复发放数量，不吞掉同名掉落', () => {
    const entries = formatExploreRewardLogs(makeExploreResult({
      rewards: {
        items: [
          { itemKey: 'herb', quantity: 1 },
          { item_key: 'herb', count: 2 },
          { itemKey: 'no-longer-available', quantity: 0 },
        ],
        equipment: [
          { equipmentKey: 'starfall_blade' },
          { equipment_key: 'starfall_blade' },
        ],
      },
    }), [
      { itemKey: 'herb', itemName: '草药' },
      { equipmentKey: 'starfall_blade', equipmentName: '星陨之刃' },
    ]);

    expect(entries.map((entry) => entry.message)).toEqual([
      '获得掉落：草药 ×3',
      '获得掉落：星陨之刃 ×2',
    ]);
  });

  it('bootstrap 不可用时也能用探索响应恢复摘要和奖励日志', () => {
    const entries = formatExploreResultLogs(makeExploreResult({
      new_logs: [{ message: '翻开补给箱（消耗体力5）', type: 'exploration' }],
      rewards: { coins: 12 },
    }));

    expect(entries).toEqual([
      { message: '翻开补给箱（消耗体力5）', type: 'exploration' },
      { message: '获得星币 +12', type: 'reward' },
    ]);
  });

  it('把回合动作、战斗结算和掉落写为日志条目', () => {
    const entries = formatCombatLogEntries(makeCombatResult(), [
      { itemKey: 'scrap_metal', itemName: '废旧金属' },
    ]);

    expect(entries.map((entry) => entry.message)).toEqual([
      '第1回合：你对变异站鼠造成20点伤害（暴击），变异站鼠剩余生命 0。',
      '第1回合：变异站鼠对你造成5点伤害（防御减伤），你剩余生命 80。',
      '击败了 变异站鼠（1回合）',
      '获得经验值 +50',
      '获得星币 +20',
      '获得故事碎片 +1',
      '获得频道热度 +2',
      '获得掉落：废旧金属 ×2',
    ]);
    expect(entries[0].type).toBe('battle');
    expect(entries[2].type).toBe('battle');
    expect(entries.slice(3).every((entry) => entry.type === 'reward')).toBe(true);
  });

  it('把有个人线索依据的故事战斗减伤写进对应回合日志', () => {
    const entries = formatCombatLogEntries(makeCombatResult({
      rounds: [{ round: 1, actions: [
        { actor: 'monster', type: 'attack', damage: 12, targetHpAfter: 88,
          suppressionNote: '你想起渡船人的提醒，稳住了自己的故事（减伤 4 点）' },
      ] }],
      rewards: { exp: 0, coins: 0, storyFragments: 0, channelHeat: 0 },
      drops: [],
      newLogs: [],
    }));

    expect(entries[0].message).toBe(
      '第1回合：变异站鼠对你造成12点伤害（你想起渡船人的提醒，稳住了自己的故事（减伤 4 点）），你剩余生命 88。',
    );
  });

  it('即使没有回合和后端摘要，也会记录战斗结局及奖励', () => {
    const entries = formatCombatLogEntries(makeCombatResult({
      result: 'escape',
      totalRounds: 0,
      rounds: [],
      newLogs: [],
      rewards: { exp: 0, coins: 0, storyFragments: 0, channelHeat: 0 },
      drops: [],
    }));

    expect(entries).toEqual([{ message: '从 变异站鼠 面前撤退了', type: 'battle' }]);
  });

  it('把剧情选择、后果、合并奖励、解锁和称号写到日志', () => {
    const choice: ChoiceResult = {
      event_key: 'branch',
      event_name: '分岔路口',
      choice_index: 0,
      choice_label: '救助幸存者',
      consequence_text: '幸存者递给你一把钥匙。',
      rewards: { coins: 100, storyFragments: 1, worldLineShift: -3, items: [{ itemKey: 'old_key' }] },
      unlock_locations: ['old_station'],
      unlock_events: ['hidden_signal'],
      story_flags_updated: { story_dark_spring_interwoven: true },
      new_titles: ['守望者'],
    };

    const entries = formatChoiceOutcomeLogs(
      choice,
      [{ itemKey: 'old_key', itemName: '旧钥匙' }],
    );

    expect(entries.map((entry) => entry.message)).toEqual([
      '你选择了「救助幸存者」',
      '幸存者递给你一把钥匙。',
      '个人分支：并置两段记录',
      '解锁地点：old_station',
      '发现新的故事线索（1）',
      '获得星币 +100',
      '获得故事碎片 +1',
      '世界线偏移 -3',
      '获得掉落：旧钥匙',
      '获得称号：守望者',
    ]);
    expect(entries.slice(0, 2).every((entry) => entry.type === 'story')).toBe(true);
    expect(entries.slice(3, 5).every((entry) => entry.type === 'explore')).toBe(true);
  });

  it('将剧情支付的负资源记为消耗而不是奖励', () => {
    const entries = formatChoiceOutcomeLogs({
      event_key: 'paid-branch',
      event_name: '雾桥',
      choice_index: 0,
      choice_label: '支付通行费',
      consequence_text: '雾桥让开了。',
      rewards: { coins: -50, storyFragments: -2 },
      unlock_locations: [],
      unlock_events: [],
      new_titles: [],
    });

    expect(entries.map((entry) => entry.message)).toContain('消耗星币 50');
    expect(entries.map((entry) => entry.message)).toContain('消耗故事碎片 2');
    expect(entries.map((entry) => entry.message)).not.toContain('损失星币 50');

    const persistedEntries = formatPersistedLogDetails({
      id: 16,
      type: 'story',
      message: '你选择了: 支付通行费',
      metadata: {
        kind: 'story_choice',
        rewards: { coins: -50, storyFragments: -2 },
      },
    });
    expect(persistedEntries.map((entry) => entry.message)).toContain('消耗星币 50');
    expect(persistedEntries.map((entry) => entry.message)).toContain('消耗故事碎片 2');
  });

  it('将持久化的技能解锁成本展开为可核对的消耗日志', () => {
    const details = formatPersistedLogDetails({
      id: 17,
      type: 'skill_unlock',
      message: '习得技能: 档案脉冲',
      metadata: {
        kind: 'skill_unlock',
        skillKey: 'archive_pulse',
        costs: { coins: 40, storyFragments: 3 },
      },
    });

    expect(details.map((entry) => entry.message)).toEqual([
      '消耗星币 40',
      '消耗故事碎片 3',
    ]);
  });

  it('将战斗失败扣除的负星币记为损失而不是主动消耗', () => {
    const entries = formatCombatLogEntries(makeCombatResult({
      result: 'lose',
      rewards: { exp: 0, coins: -10, storyFragments: 0, channelHeat: 0 },
      drops: [],
      newLogs: [{ message: '被 变异站鼠 击败了', type: 'battle' }],
    }));

    expect(entries).toContainEqual(expect.objectContaining({ message: '损失星币 10', type: 'loss' }));
    expect(entries.map((entry) => entry.message)).not.toContain('消耗星币 10');
  });

  it('使用与服务端一致的选择摘要来替换日志，而不是追加重复记录', () => {
    expect(choiceSummaryMessage('救助幸存者')).toBe('你选择了: 救助幸存者');
  });

  it('将已持久化的探索奖励和剧情选择详情展开为日志行，不重复摘要', () => {
    const exploration = formatPersistedLogDetails({
      id: 10,
      type: 'exploration',
      message: '找到旧车站（消耗体力5）',
      metadata: {
        kind: 'exploration_result',
        rewards: { exp: 12, coins: 25, worldLineShift: 1 },
        newTitles: [{ titleName: '拾荒者' }],
      },
    });
    expect(exploration.map((entry) => entry.message)).toEqual([
      '获得经验值 +12', '获得星币 +25', '世界线偏移 +1', '获得称号：拾荒者',
    ]);

    const choice = formatPersistedLogDetails({
      id: 11,
      type: 'story',
      message: '你选择了: 救助幸存者',
      metadata: {
        kind: 'story_choice',
        consequenceText: '幸存者递给你一把钥匙。',
        storyFlagsUpdated: { story_dark_spring_separated: true },
        rewards: {
          coins: 100,
          worldLineShift: -2,
          equipment: [{ equipmentKey: 'starfall_blade', equipmentName: '星陨之刃' }],
        },
        unlockLocations: ['废弃车站'],
        unlockEvents: ['hidden_signal'],
      },
    });
    expect(choice.map((entry) => entry.message)).toEqual([
      '幸存者递给你一把钥匙。',
      '个人分支：分别保存两段记录',
      '解锁地点：废弃车站',
      '发现新的故事线索（1）',
      '获得星币 +100',
      '世界线偏移 -2',
      '获得掉落：星陨之刃',
    ]);
    expect(choice.some((entry) => entry.message === '你选择了: 救助幸存者')).toBe(false);
  });

  it('从持久化战斗 metadata 恢复回合和掉落详情', () => {
    const details = formatPersistedLogDetails({
      id: 12,
      type: 'battle',
      message: '击败了 变异站鼠（1回合）',
      metadata: {
        kind: 'combat_result',
        result: 'win',
        monster: { monsterKey: 'station_rat', name: '变异站鼠' },
        totalRounds: 1,
        rounds: [{ round: 1, actions: [
          { actor: 'player', damage: 20, critical: true, targetHpAfter: 0 },
        ] }],
        rewards: { exp: 50, coins: 20, storyFragments: 0, channelHeat: 0 },
        drops: [
          { itemKey: 'scrap_metal', itemName: '废旧金属', quantity: 2 },
          { equipmentKey: 'station_blade', equipmentName: '车站短刃' },
        ],
        newLogs: [{ message: '击败了 变异站鼠（1回合）', type: 'battle' }],
      },
    });
    expect(details.map((entry) => entry.message)).toEqual([
      '第1回合：你对变异站鼠造成20点伤害（暴击），变异站鼠剩余生命 0。',
      '获得经验值 +50',
      '获得星币 +20',
      '获得掉落：废旧金属 ×2',
      '获得掉落：车站短刃',
    ]);
  });

  it('将已领取任务奖励展开为可刷新的资源与称号日志', () => {
    const details = formatPersistedLogDetails({
      id: 13,
      type: 'quest',
      message: '领取了任务「每日探索」的奖励',
      metadata: {
        kind: 'quest_reward',
        questKey: 'daily_explore_3',
        questTitle: '每日探索',
        cycleKey: '2026-09-24',
        rewards: {
          coins: 15,
          exp: 30,
          items: [{ itemKey: 'herb', quantity: 2 }],
          titleKey: 'station_watcher',
        },
        messages: ['获得称号: station_watcher'],
      },
    }, [{ itemKey: 'herb', itemName: '草药' }], [
      { titleKey: 'station_watcher', name: '车站守望者' },
    ]);

    expect(details.map((entry) => entry.message)).toEqual([
      '任务周期：2026-09-24',
      '获得经验值 +30',
      '获得星币 +15',
      '获得掉落：草药 ×2',
      '获得称号：车站守望者',
    ]);
  });

  it('一次性成就的领奖日志不显示虚假的周期标记', () => {
    const details = formatPersistedLogDetails({
      id: 14,
      type: 'quest',
      message: '领取了任务「初露锋芒」的奖励',
      metadata: {
        kind: 'quest_reward',
        questKey: 'achv_level_5',
        questTitle: '初露锋芒',
        cycleKey: 'none',
        rewards: { coins: 500 },
      },
    });

    expect(details.map((entry) => entry.message)).toEqual(['获得星币 +500']);
  });

  it('展开物品使用日志时展示服务端实际应用的效果', () => {
    const details = formatPersistedLogDetails({
      type: 'item_use',
      message: '使用了 看门人的痕迹',
      metadata: {
        kind: 'item_use',
        itemKey: 'station_keeper_trace',
        itemName: '看门人的痕迹',
        appliedEffects: { storyFragments: 10, channelHeat: 8, hp: 0, abyssMark: 2 },
      },
    });

    expect(details.map((entry) => entry.message)).toEqual([
      '获得故事碎片 +10',
      '获得频道热度 +8',
      '获得深渊印记 +2',
    ]);
  });

  it('展开主线推进日志时展示章节实际发放的资源', () => {
    const details = formatPersistedLogDetails({
      type: 'story',
      message: '主线推进：「第二阶段」→「第三阶段」',
      metadata: {
        kind: 'chapter_reward',
        chapterKey: 'main_ch02_contract',
        chapterName: '第二阶段',
        nextChapterKey: 'main_ch03_kings_conflict',
        nextChapterName: '第三阶段',
        rewards: { coins: 100, storyFragments: 10, channelHeat: 30 },
      },
    });

    expect(details.map((entry) => entry.message)).toEqual([
      '获得星币 +100',
      '获得故事碎片 +10',
      '获得频道热度 +30',
    ]);
  });

  it('任务过期历程说明周期和过期时进度，不把未发奖励显示成已获得', () => {
    const details = formatPersistedLogDetails({
      id: 15,
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
    });

    expect(details.map((entry) => entry.message)).toEqual([
      '任务周期：2026-09-24',
      '到期进度：3 / 3',
    ]);
  });
});
