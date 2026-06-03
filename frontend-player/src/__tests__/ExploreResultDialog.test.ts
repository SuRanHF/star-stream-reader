import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('@/api/exploreApi', () => ({
  exploreApi: { makeChoice: vi.fn() },
}));
vi.mock('@/api/combatApi', () => ({
  combatApi: { startCombat: vi.fn(), resolveCombat: vi.fn() },
}));
vi.mock('@/api/gameApi', () => ({
  gameApi: { getBootstrap: vi.fn() },
}));

const mockShowAlert = vi.fn();
vi.mock('@/stores/uiStore', () => ({
  useUiStore: () => ({ showAlert: mockShowAlert }),
}));
vi.mock('@/stores/gameStore', () => ({
  useGameStore: () => ({
    player: { id: 1, playerId: 1, player_name: 'Test', coins: 0, story_fragments: 0 },
    applyBootstrap: vi.fn(),
  }),
}));

import ExploreResultDialog from '@/components/explore/ExploreResultDialog.vue';
import type { ExploreResult } from '@/api/exploreApi';

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

/** 组件用 Teleport to="body"，内容在 document.body */
function body() { return document.body.textContent || ''; }

describe('ExploreResultDialog', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    document.body.innerHTML = '';
  });
  afterEach(() => { document.body.innerHTML = ''; });

  // ═══════════════ 评分 ═══════════════

  it('S 级 — 故事 + fragments>=2 + coins>=1000', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result: { event_key: 's1', event_type: 'story', name: 'S级', description: '' },
      rewards: { coins: 1500, storyFragments: 3 },
    })}});
    expect(body()).toContain('完美通关');
  });

  it('A 级 — 故事 + coins>=500', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result: { event_key: 'a1', event_type: 'story', name: 'A级', description: '' },
      rewards: { coins: 600 },
    })}});
    expect(body()).toContain('出色表现');
  });

  it('B 级 — 非故事 + coins>=500', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      rewards: { coins: 600 },
    })}});
    expect(body()).toContain('有所收获');
  });

  it('C 级 — 故事耗尽', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      stories_exhausted: true,
    })}});
    expect(body()).toContain('场景耗尽');
  });

  it('D 级 — 无奖励', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result: { event_key: 'd1', event_type: 'empty', name: '空', description: '' },
    })}});
    expect(body()).toContain('平淡无奇');
  });

  // ═══════════════ 奖励 ═══════════════

  it('显示金币', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      rewards: { coins: 500 },
    })}});
    expect(body()).toContain('星币');
    expect(body()).toContain('+500');
  });

  it('显示经验值', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      rewards: { exp: 100 },
    })}});
    expect(body()).toContain('经验值');
    expect(body()).toContain('+100');
  });

  it('显示故事碎片', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      rewards: { storyFragments: 2 },
    })}});
    expect(body()).toContain('寓言碎片');
    expect(body()).toContain('+2');
  });

  // ═══════════════ 战斗 ═══════════════

  it('战斗模式 UI', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result_type: 'battle',
      monster_key: 'station_rat',
      result: { event_key: 'b1', event_type: 'battle_placeholder', name: '变异站鼠', description: '变异老鼠' },
    })}});
    expect(body()).toContain('BATTLE');
    expect(body()).toContain('变异站鼠');
    expect(body()).toContain('战斗');
    expect(body()).toContain('撤退');
  });

  // ═══════════════ 剧情屏蔽 ═══════════════

  it('显示剧情屏蔽警告', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      stories_blocked: true,
      lower_unfinished: ['废弃车站(2)', '断裂商场(1)'],
    })}});
    expect(body()).toContain('低等级地点尚有未完成的剧情');
    expect(body()).toContain('废弃车站(2)');
    expect(body()).toContain('断裂商场(1)');
  });

  it('无剧情屏蔽时不显示警告', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      stories_blocked: false,
    })}});
    expect(body()).not.toContain('低等级地点尚有未完成的剧情');
  });

  // ═══════════════ 故事选项 ═══════════════

  it('显示故事选项', () => {
    mount(ExploreResultDialog, { props: { visible: true, result: makeResult({
      result: {
        event_key: 's1', event_type: 'story', name: '命运的选择', description: '岔路口',
        choices: [
          { label: '向左走', consequence_text: '左边' },
          { label: '向右走', consequence_text: '右边' },
        ],
      },
    })}});
    expect(body()).toContain('STORY EVENT');
    expect(body()).toContain('命运的选择');
    expect(body()).toContain('向左走');
    expect(body()).toContain('向右走');
  });
});
