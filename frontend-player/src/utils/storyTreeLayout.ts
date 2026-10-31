import type { StoryTreeEdge, StoryTreeNode } from '@/api/exploreApi';

// 剧情流程图的纯布局：先按章节分段（服务端 chapters 的顺序），章节内再按「从根出发的最长路径」分行，
// 不同章节的节点不会挤在同一行；每行内先按枝（root → 同行者 → 无名者 → 其余）分组，再按父节点的
// 平均横坐标排，让同一根枝的节点聚在一起、连线尽量不交叉。不读任何剧情含义，只用 node_id、stage_key 与边。

export interface PositionedNode {
  node: StoryTreeNode;
  depth: number;
  row: number;
  x: number;
  y: number;
}

export interface PositionedEdge {
  edge: StoryTreeEdge;
  // or = 树的主干（任一满足即可）；and = 额外必须同时满足的前置
  kind: 'or' | 'and';
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

// 同一起点、同一终点的多个选项共用一组标签，上下叠放，不互相覆盖。
export interface EdgeLabelGroup {
  key: string;
  x: number;
  y: number;
  edges: StoryTreeEdge[];
}

export interface ChapterBand {
  stageKey: string;
  label: string;
  y: number;
  height: number;
}

export interface StoryTreeLayout {
  nodes: PositionedNode[];
  edges: PositionedEdge[];
  labels: EdgeLabelGroup[];
  // 多条「且」前置汇合到同一节点时的汇合点
  junctions: { x: number; y: number }[];
  chapters: ChapterBand[];
  width: number;
  height: number;
}

export const NODE_WIDTH = 148;
export const NODE_HEIGHT = 60;
export const JUNCTION_OFFSET = 18;
export const LABEL_LINE_HEIGHT = 26;
const H_GAP = 28;
const V_GAP = 76;
const PADDING = 20;
const CHAPTER_GUTTER = 64;

const BRANCH_ORDER: Record<string, number> = { root: 0, companion: 1, nameless: 2 };

function branchRank(branch: string) {
  return BRANCH_ORDER[branch] ?? 3;
}

const isAndGate = (edge: StoryTreeEdge) => edge.gate.startsWith('and');

// 选出要画的边：「或」边是树的主干，全画。「且」边只有在能证明它多余时才不画——
// 来源 A 经主干到得了目标 T，且 A 通往 T 的每一条主干出边都正是这条「且」要求的那个选项
// （或这条「且」不限选项）。否则它是真正额外的前置，照画。服务端始终执行全部约束。
function visibleEdges(nodes: StoryTreeNode[], edges: StoryTreeEdge[]) {
  const ids = new Set(nodes.map((node) => node.node_id));
  const inTree = edges.filter((edge) => ids.has(edge.from) && ids.has(edge.to));
  const orChildren = new Map<string, StoryTreeEdge[]>();
  for (const edge of inTree.filter((e) => !isAndGate(e))) {
    orChildren.set(edge.from, [...(orChildren.get(edge.from) || []), edge]);
  }
  const descendantCache = new Map<string, Set<string>>();
  function descendants(id: string, seen = new Set<string>()): Set<string> {
    const cached = descendantCache.get(id);
    if (cached) return cached;
    const result = new Set<string>([id]);
    if (seen.has(id)) return result;
    seen.add(id);
    for (const child of orChildren.get(id) || []) {
      descendants(child.to, seen).forEach((d) => result.add(d));
    }
    descendantCache.set(id, result);
    return result;
  }
  function redundant(edge: StoryTreeEdge) {
    const routes = (orChildren.get(edge.from) || []).filter((child) => descendants(child.to).has(edge.to));
    if (!routes.length) return false;
    if (edge.choice_index === undefined || edge.choice_index === null) return true;
    return routes.every((child) => child.choice_index === edge.choice_index);
  }
  return inTree.filter((edge) => !isAndGate(edge) || !redundant(edge));
}

export function layoutStoryTree(
  nodes: StoryTreeNode[],
  allEdges: StoryTreeEdge[],
  chapters: { stage_key: string; label: string }[] = [],
): StoryTreeLayout {
  const edges = visibleEdges(nodes, allEdges);
  const parents = new Map<string, string[]>();
  for (const edge of edges) {
    parents.set(edge.to, [...(parents.get(edge.to) || []), edge.from]);
  }

  // 最长路径深度；遇到环时保守地停在当前深度，避免死循环。
  const depthOf = new Map<string, number>();
  const visiting = new Set<string>();
  function depth(id: string): number {
    const known = depthOf.get(id);
    if (known !== undefined) return known;
    if (visiting.has(id)) return 0;
    visiting.add(id);
    const ups = [...new Set(parents.get(id) || [])];
    const value = ups.length ? Math.max(...ups.map((parent) => depth(parent) + 1)) : 0;
    visiting.delete(id);
    depthOf.set(id, value);
    return value;
  }
  nodes.forEach((node) => depth(node.node_id));

  // 行 = (章节序号, 深度)，压缩成连续行号；不认识的章节排在最后。
  const chapterIndex = new Map(chapters.map((chapter, index) => [chapter.stage_key, index]));
  const chapterOf = (node: StoryTreeNode) => chapterIndex.get(node.stage_key) ?? chapters.length;
  const rowKeyOf = (node: StoryTreeNode) => chapterOf(node) * 10000 + (depthOf.get(node.node_id) ?? 0);
  const rowKeys = [...new Set(nodes.map(rowKeyOf))].sort((a, b) => a - b);
  const rowOfKey = new Map(rowKeys.map((key, index) => [key, index]));

  const rows = new Map<number, StoryTreeNode[]>();
  for (const node of nodes) {
    const row = rowOfKey.get(rowKeyOf(node)) ?? 0;
    rows.set(row, [...(rows.get(row) || []), node]);
  }

  const gutter = chapters.length ? CHAPTER_GUTTER : 0;
  const rowY = (row: number) => PADDING + row * (NODE_HEIGHT + V_GAP);
  const xOf = new Map<string, number>();
  const positioned: PositionedNode[] = [];
  const maxPerRow = Math.max(1, ...[...rows.values()].map((row) => row.length));
  const contentWidth = maxPerRow * NODE_WIDTH + (maxPerRow - 1) * H_GAP;

  for (const row of [...rows.keys()].sort((a, b) => a - b)) {
    const members = rows.get(row) || [];
    const parentCenter = (node: StoryTreeNode) => {
      const xs = (parents.get(node.node_id) || []).map((p) => xOf.get(p)).filter((x): x is number => x !== undefined);
      return xs.length ? xs.reduce((sum, x) => sum + x, 0) / xs.length : 0;
    };
    members.sort((a, b) => branchRank(a.branch) - branchRank(b.branch)
      || parentCenter(a) - parentCenter(b)
      || a.node_id.localeCompare(b.node_id));
    const rowWidth = members.length * NODE_WIDTH + (members.length - 1) * H_GAP;
    const startX = PADDING + gutter + (contentWidth - rowWidth) / 2;
    members.forEach((node, index) => {
      const x = startX + index * (NODE_WIDTH + H_GAP);
      xOf.set(node.node_id, x + NODE_WIDTH / 2);
      positioned.push({ node, depth: depthOf.get(node.node_id) ?? 0, row, x, y: rowY(row) });
    });
  }

  const posById = new Map(positioned.map((p) => [p.node.node_id, p]));
  const positionedEdges: PositionedEdge[] = [];
  const junctions: { x: number; y: number }[] = [];
  // 同一目标有多个不同来源的「且」前置时，先汇到目标上方的汇合点，再由一段线进入目标。
  // 同一来源的多个可选索引算一条前置。
  const andSources = new Map<string, Set<string>>();
  edges.filter(isAndGate).forEach((edge) => andSources.set(edge.to, (andSources.get(edge.to) || new Set()).add(edge.from)));
  const junctionOf = new Map<string, { x: number; y: number }>();
  for (const [target, sources] of andSources) {
    const to = posById.get(target);
    if (!to || sources.size < 2) continue;
    const point = { x: to.x + NODE_WIDTH / 2, y: to.y - JUNCTION_OFFSET };
    junctionOf.set(target, point);
    junctions.push(point);
  }
  for (const edge of edges) {
    const from = posById.get(edge.from);
    const to = posById.get(edge.to);
    if (!from || !to) continue;
    const kind = isAndGate(edge) ? 'and' : 'or';
    const junction = kind === 'and' ? junctionOf.get(edge.to) : undefined;
    positionedEdges.push({
      edge,
      kind,
      x1: from.x + NODE_WIDTH / 2,
      y1: from.y + NODE_HEIGHT,
      x2: junction ? junction.x : to.x + NODE_WIDTH / 2,
      y2: junction ? junction.y : to.y,
    });
  }

  // 标签贴在子节点正上方（同一父节点的不同子节点之间至少隔一个节点宽）；同起终点的多个选项叠放。
  const labelGroups = new Map<string, EdgeLabelGroup>();
  for (const item of positionedEdges) {
    if (!item.edge.choice_label) continue;
    const to = posById.get(item.edge.to)!;
    const key = `${item.edge.from}->${item.edge.to}`;
    const group = labelGroups.get(key)
      || { key, x: to.x + NODE_WIDTH / 2, y: to.y - 24, edges: [] };
    group.edges.push(item.edge);
    labelGroups.set(key, group);
  }
  const labels = [...labelGroups.values()].map((group) => ({
    ...group,
    edges: [...group.edges].sort((a, b) => (a.choice_index ?? 0) - (b.choice_index ?? 0)),
  }));

  // 章节标签：每个章节覆盖它占用的连续行。
  const bands: ChapterBand[] = [];
  chapters.forEach((chapter, index) => {
    const chapterRows = positioned.filter((p) => chapterOf(p.node) === index).map((p) => p.row);
    if (!chapterRows.length) return;
    const first = Math.min(...chapterRows);
    const last = Math.max(...chapterRows);
    const top = Math.max(4, rowY(first) - V_GAP / 2 + 8);
    const bottom = rowY(last) + NODE_HEIGHT + V_GAP / 2 - 8;
    bands.push({ stageKey: chapter.stage_key, label: chapter.label, y: top, height: bottom - top });
  });

  const rowCount = rows.size || 1;
  return {
    nodes: positioned,
    edges: positionedEdges,
    labels,
    junctions,
    chapters: bands,
    width: contentWidth + gutter + PADDING * 2,
    height: rowCount * NODE_HEIGHT + (rowCount - 1) * V_GAP + PADDING * 2,
  };
}
