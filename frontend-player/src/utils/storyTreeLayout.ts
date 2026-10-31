import type { StoryTreeEdge, StoryTreeNode } from '@/api/exploreApi';

// 剧情流程图的纯布局：按「从根出发的最长路径」分层（同一层画在同一行），
// 每层内先按枝（root → 同行者 → 无名者 → 其余）分组，再按父节点的平均横坐标排，
// 让同一根枝的节点聚在一起、连线尽量不交叉。不读任何剧情含义，只用 node_id 与边。

export interface PositionedNode {
  node: StoryTreeNode;
  depth: number;
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

export interface StoryTreeLayout {
  nodes: PositionedNode[];
  edges: PositionedEdge[];
  // 多条「且」前置汇合到同一节点时的汇合点
  junctions: { x: number; y: number }[];
  width: number;
  height: number;
}

export const NODE_WIDTH = 148;
export const NODE_HEIGHT = 60;
const H_GAP = 28;
const V_GAP = 76;
const PADDING = 20;
export const JUNCTION_OFFSET = 18;

const BRANCH_ORDER: Record<string, number> = { root: 0, companion: 1, nameless: 2 };

function branchRank(branch: string) {
  return BRANCH_ORDER[branch] ?? 3;
}

const isAndGate = (edge: StoryTreeEdge) => edge.gate.startsWith('and');

// 选出要画的边：「或」边是树的主干，全画；「且」边若其来源已经是目标在主干上的祖先，
// 画出来只会多一条从树根拉到深处的重复长线（例如每根枝都被树根选择「且」约束），故不画——
// 服务端仍会执行这条约束，前端只是不重复表达。其余「且」边是真正额外的前置，照画。
function visibleEdges(nodes: StoryTreeNode[], edges: StoryTreeEdge[]) {
  const ids = new Set(nodes.map((node) => node.node_id));
  const inTree = edges.filter((edge) => ids.has(edge.from) && ids.has(edge.to));
  const orParents = new Map<string, string[]>();
  for (const edge of inTree.filter((e) => !isAndGate(e))) {
    orParents.set(edge.to, [...(orParents.get(edge.to) || []), edge.from]);
  }
  const ancestorCache = new Map<string, Set<string>>();
  function ancestors(id: string, seen = new Set<string>()): Set<string> {
    const cached = ancestorCache.get(id);
    if (cached) return cached;
    const result = new Set<string>();
    if (seen.has(id)) return result;
    seen.add(id);
    for (const parent of orParents.get(id) || []) {
      result.add(parent);
      ancestors(parent, seen).forEach((a) => result.add(a));
    }
    ancestorCache.set(id, result);
    return result;
  }
  return inTree.filter((edge) => !isAndGate(edge) || !ancestors(edge.to).has(edge.from));
}

export function layoutStoryTree(nodes: StoryTreeNode[], allEdges: StoryTreeEdge[]): StoryTreeLayout {
  const edges = visibleEdges(nodes, allEdges);
  const parents = new Map<string, string[]>();
  for (const edge of edges) {
    parents.set(edge.to, [...(parents.get(edge.to) || []), edge.from]);
  }

  // 最长路径分层；遇到环时保守地停在当前深度，避免死循环。
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

  const layers = new Map<number, StoryTreeNode[]>();
  for (const node of nodes) {
    const d = depthOf.get(node.node_id) ?? 0;
    layers.set(d, [...(layers.get(d) || []), node]);
  }

  const xOf = new Map<string, number>();
  const positioned: PositionedNode[] = [];
  const maxPerLayer = Math.max(1, ...[...layers.values()].map((layer) => layer.length));
  const contentWidth = maxPerLayer * NODE_WIDTH + (maxPerLayer - 1) * H_GAP;

  for (const d of [...layers.keys()].sort((a, b) => a - b)) {
    const layer = layers.get(d) || [];
    const parentCenter = (node: StoryTreeNode) => {
      const xs = (parents.get(node.node_id) || []).map((p) => xOf.get(p)).filter((x): x is number => x !== undefined);
      return xs.length ? xs.reduce((sum, x) => sum + x, 0) / xs.length : 0;
    };
    layer.sort((a, b) => branchRank(a.branch) - branchRank(b.branch)
      || parentCenter(a) - parentCenter(b)
      || a.node_id.localeCompare(b.node_id));
    const rowWidth = layer.length * NODE_WIDTH + (layer.length - 1) * H_GAP;
    const startX = PADDING + (contentWidth - rowWidth) / 2;
    layer.forEach((node, index) => {
      const x = startX + index * (NODE_WIDTH + H_GAP);
      const y = PADDING + d * (NODE_HEIGHT + V_GAP);
      xOf.set(node.node_id, x + NODE_WIDTH / 2);
      positioned.push({ node, depth: d, x, y });
    });
  }

  const posById = new Map(positioned.map((p) => [p.node.node_id, p]));
  const positionedEdges: PositionedEdge[] = [];
  const junctions: { x: number; y: number }[] = [];
  // 同一目标有多条「且」边时，先汇到目标上方的汇合点，再由一段线进入目标。
  // 同一来源的多个可选索引算一条前置，按不同来源计数。
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

  const layerCount = layers.size || 1;
  return {
    nodes: positioned,
    edges: positionedEdges,
    junctions,
    width: contentWidth + PADDING * 2,
    height: layerCount * NODE_HEIGHT + (layerCount - 1) * V_GAP + PADDING * 2,
  };
}
