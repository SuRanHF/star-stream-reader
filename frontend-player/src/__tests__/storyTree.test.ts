import { flushPromises, mount } from '@vue/test-utils';
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { StoryTree } from '@/api/exploreApi';
import { layoutStoryTree, NODE_HEIGHT } from '@/utils/storyTreeLayout';

const mocks = vi.hoisted(() => ({ getStoryTree: vi.fn() }));
vi.mock('@/api/exploreApi', () => ({ exploreApi: { getStoryTree: mocks.getStoryTree } }));

import StoryTreePanel from '@/components/story-tree/StoryTreePanel.vue';
import { useGameStore } from '@/stores/gameStore';

// 同行者走到 A0 枝尖的样例：未到达 / 未走的节点只有不透明 node_id。
function sampleTree(overrides: Partial<StoryTree> = {}): StoryTree {
  return {
    chapters: [{ stage_key: 'vol1_ch1', label: '第一章' }, { stage_key: 'vol1_ch2', label: '第二章' }],
    nodes: [
      { node_id: 'n-root', state: 'done', branch: 'root', stage_key: 'vol1_ch1', event_key: 'station_first_trace', title: '第一个异常痕迹', location_name: '废弃车站' },
      { node_id: 'n-a', state: 'tip', branch: 'companion', stage_key: 'vol1_ch2', event_key: 'dongmyo_market_story', title: '东庙交易市场', location_name: '东庙' },
      { node_id: 'n-b', state: 'closed', branch: 'nameless', stage_key: 'vol1_ch2' },
      { node_id: 'n-c', state: 'locked', branch: 'companion', stage_key: 'vol1_ch2' },
      { node_id: 'n-d', state: 'available', branch: 'companion', stage_key: 'vol1_ch2', location_name: '忠武路' },
    ],
    edges: [
      { from: 'n-root', to: 'n-a', gate: 'g1', choice_index: 0, choice_label: '"是的。我知道你是谁——刘众赫。"', taken: true, stats: { percent: 42, sample_band: '50+' } },
      { from: 'n-root', to: 'n-b', gate: 'g2', choice_index: 1, choice_label: '"我只是一个普通的幸存者。"', taken: false, stats: null },
      { from: 'n-a', to: 'n-c', gate: 'g3', taken: false },
      { from: 'n-a', to: 'n-d', gate: 'g4', taken: false },
    ],
    route_continuation: { status: 'to_be_continued', event_key: 'dongmyo_market_story' },
    route_stats: { percent: 12, sample_band: '100+' },
    ...overrides,
  };
}

describe('layoutStoryTree', () => {
  it('按从根出发的最长路径分层，同一根枝的节点排在一起', () => {
    const tree = sampleTree();
    const layout = layoutStoryTree(tree.nodes, tree.edges);
    const byId = Object.fromEntries(layout.nodes.map((p) => [p.node.node_id, p]));

    expect(byId['n-root'].depth).toBe(0);
    expect(byId['n-a'].depth).toBe(1);
    expect(byId['n-b'].depth).toBe(1);
    expect(byId['n-c'].depth).toBe(2);
    // 第 1 层：同行者在无名者左边
    expect(byId['n-a'].x).toBeLessThan(byId['n-b'].x);
    expect(byId['n-c'].y).toBe(byId['n-d'].y);
    expect(layout.edges).toHaveLength(4);
    expect(layout.edges[0].y1).toBe(byId['n-root'].y + NODE_HEIGHT);
  });

  it('「且」前置若已是主干祖先则不画；多条真正的「且」前置汇到一个汇合点', () => {
    const node = (id: string) => ({ node_id: id, state: 'locked' as const, branch: 'root', stage_key: 's' });
    const nodes = ['r', 'a', 'b', 'x', 'y', 't'].map(node);
    const edges = [
      { from: 'r', to: 'a', gate: 'or:0', choice_index: 0, taken: true },
      { from: 'a', to: 'b', gate: 'or:0', taken: false },
      // 树根选择对深处节点的约束：r 已是 b 的主干祖先，不应再拉一条长线
      { from: 'r', to: 'b', gate: 'and', taken: false },
      { from: 'r', to: 'x', gate: 'or:0', taken: false },
      { from: 'r', to: 'y', gate: 'or:1', taken: false },
      { from: 'x', to: 't', gate: 'and', taken: false },
      { from: 'y', to: 't', gate: 'and', taken: false },
    ];
    const layout = layoutStoryTree(nodes, edges);

    expect(layout.edges.some((e) => e.edge.from === 'r' && e.edge.to === 'b')).toBe(false);
    expect(layout.edges.filter((e) => e.kind === 'and')).toHaveLength(2);
    expect(layout.junctions).toHaveLength(1);
    const tPos = layout.nodes.find((p) => p.node.node_id === 't')!;
    const andEdges = layout.edges.filter((e) => e.edge.to === 't');
    andEdges.forEach((e) => {
      expect(e.x2).toBe(layout.junctions[0].x);
      expect(e.y2).toBeLessThan(tPos.y);
    });
    // 被隐藏的「且」边不参与分层：b 仍在 a 的下一层
    const depth = Object.fromEntries(layout.nodes.map((p) => [p.node.node_id, p.depth]));
    expect(depth.b).toBe(depth.a + 1);
  });

  it('只有能证明多余时才隐藏「且」边：祖先可多选、「且」只限其中一个时照画', () => {
    const node = (id: string) => ({ node_id: id, state: 'locked' as const, branch: 'root', stage_key: 's' });
    const nodes = ['r', 'a', 't'].map(node);
    const edges = [
      { from: 'r', to: 'a', gate: 'or:0', choice_index: 0, taken: false },
      { from: 'r', to: 'a', gate: 'or:0', choice_index: 1, taken: false },
      { from: 'a', to: 't', gate: 'or:0', taken: false },
      { from: 'r', to: 't', gate: 'and', choice_index: 1, taken: false },
    ];
    const layout = layoutStoryTree(nodes, edges);
    expect(layout.edges.some((e) => e.kind === 'and' && e.edge.from === 'r' && e.edge.to === 't')).toBe(true);
  });

  it('同起终点的多个选项合成一组标签，按选项顺序叠放', () => {
    const node = (id: string) => ({ node_id: id, state: 'done' as const, branch: 'root', stage_key: 's' });
    const edges = [
      { from: 'p', to: 'c', gate: 'or:0', choice_index: 1, choice_label: '乙', taken: true },
      { from: 'p', to: 'c', gate: 'or:0', choice_index: 0, choice_label: '甲', taken: false },
    ];
    const layout = layoutStoryTree([node('p'), node('c')], edges);
    expect(layout.labels).toHaveLength(1);
    expect(layout.labels[0].edges.map((e) => e.choice_label)).toEqual(['甲', '乙']);
  });

  it('按章节分行：不同章节不在同一行，并返回章节带', () => {
    const nodes = [
      { node_id: 'r', state: 'done' as const, branch: 'root', stage_key: 'ch1' },
      { node_id: 'a', state: 'locked' as const, branch: 'companion', stage_key: 'ch2' },
      { node_id: 'b', state: 'locked' as const, branch: 'nameless', stage_key: 'ch1' },
    ];
    const edges = [
      { from: 'r', to: 'a', gate: 'or:0', taken: false },
      { from: 'r', to: 'b', gate: 'or:1', taken: false },
    ];
    const chapters = [{ stage_key: 'ch1', label: '第一章' }, { stage_key: 'ch2', label: '第二章' }];
    const layout = layoutStoryTree(nodes, edges, chapters);
    const byId = Object.fromEntries(layout.nodes.map((p) => [p.node.node_id, p]));
    // a 与 b 深度相同，但章节不同，分在两行；第一章在上
    expect(byId.b.y).toBeLessThan(byId.a.y);
    expect(layout.chapters.map((band) => band.label)).toEqual(['第一章', '第二章']);
    expect(layout.chapters[0].y).toBeGreaterThanOrEqual(0);
  });

  it('有环时不会死循环', () => {
    const nodes = [
      { node_id: 'x', state: 'locked' as const, branch: 'root', stage_key: 's' },
      { node_id: 'y', state: 'locked' as const, branch: 'root', stage_key: 's' },
    ];
    const edges = [{ from: 'x', to: 'y', gate: 'a', taken: false }, { from: 'y', to: 'x', gate: 'b', taken: false }];
    expect(() => layoutStoryTree(nodes, edges)).not.toThrow();
  });
});

describe('StoryTreePanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    useGameStore().player = { id: 7 } as never;
    mocks.getStoryTree.mockReset();
  });

  async function mountPanel() {
    const wrapper = mount(StoryTreePanel, {
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }]] },
    });
    await flushPromises();
    return wrapper;
  }

  it('不剧透：未到达只显示「？」，没走的路只显示「未走的路」，现在能遇到的只给地点', async () => {
    mocks.getStoryTree.mockResolvedValue(sampleTree());
    const wrapper = await mountPanel();

    expect(mocks.getStoryTree).toHaveBeenCalledWith(7);
    expect(wrapper.find('.stp-node.is-locked .stp-node-title').text()).toBe('？');
    expect(wrapper.find('.stp-node.is-closed .stp-node-title').text()).toBe('未走的路');
    expect(wrapper.find('.stp-node.is-available .stp-node-sub').text()).toBe('去忠武路看看');
    expect(wrapper.find('.stp-node.is-done .stp-node-title').text()).toBe('第一个异常痕迹');
    expect(wrapper.find('.stp-node.is-tip .stp-node-badge').text()).toBe('待续');
    // 前端从不显示内部事件键
    expect(wrapper.html()).not.toContain('dongmyo_market_story');
  });

  it('选项占比带分档，样本不足时显示「数据不足」，并显示路线稀有度与待续提示', async () => {
    mocks.getStoryTree.mockResolvedValue(sampleTree());
    const wrapper = await mountPanel();
    const statsTexts = wrapper.findAll('.stp-edge-stats').map((node) => node.text());

    expect(statsTexts).toContain('42% 的读者（50+）');
    expect(statsTexts).toContain('数据不足');
    expect(wrapper.text()).toContain('走过和你完全一样这条路的读者：12%（100+）');
    expect(wrapper.find('.stp-banner--pending').exists()).toBe(true);
  });

  it('接口出错时可重试', async () => {
    // 面板与其他面板一样失败后自动重试一次，两次都失败才显示错误
    mocks.getStoryTree.mockRejectedValue(new Error('network'));
    const wrapper = await mountPanel();
    await new Promise((resolve) => setTimeout(resolve, 1300));
    await flushPromises();
    expect(wrapper.text()).toContain('流程图暂时读不到');
    expect(mocks.getStoryTree).toHaveBeenCalledTimes(2);

    mocks.getStoryTree.mockResolvedValue(sampleTree());
    await wrapper.get('.stp-retry').trigger('click');
    await flushPromises();
    expect(wrapper.find('.stp-node.is-done').exists()).toBe(true);
  });
});
