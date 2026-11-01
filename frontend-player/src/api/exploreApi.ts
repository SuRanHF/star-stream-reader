import { http } from './http';
import type { ApiRecord } from '@/types/api';

export interface ExploreResult {
  result_type: string;
  stamina_cost_paid?: number;
  monster_key?: string;
  location_key?: string;
  location_name?: string;
  result: {
    event_key: string;
    event_type: string;
    name: string;
    description: string;
    choices?: Choice[];
    personal_contexts?: string[];
  };
  rewards: Record<string, unknown>;
  progress_effects: Record<string, unknown>;
  new_titles: unknown[];
  stories_exhausted: boolean;
  stories_blocked?: boolean;
  lower_unfinished?: string[];
  request_key?: string;
  request_replayed?: boolean;
  story_instance_key?: string;
  // 主线寻踪（契约「献祭称号复活与主线寻踪」）：本次是否生效、额外消耗了多少行动力
  trace_applied?: boolean;
  trace_extra_cost?: number;
  player?: ApiRecord;
  new_logs?: unknown[];
}

export interface Choice {
  label: string;
  consequence_text: string;
  rewards_override?: Record<string, unknown>;
  risks?: {
    damagePercent?: number;
    monsterKey?: string;
    battleName?: string;
    battleDescription?: string;
  };
  unlock_locations?: string[];
  unlock_events?: string[];
  title_bias?: Record<string, number>;
  set_story_flags?: Record<string, unknown>;
}

export interface ChoiceResult {
  event_key: string;
  event_name: string;
  choice_index: number;
  choice_label: string;
  consequence_text: string;
  rewards: Record<string, unknown>;
  unlock_locations: string[];
  unlock_events: string[];
  title_bias?: Record<string, number>;
  new_titles: unknown[];
  applied_risks?: Record<string, unknown>;
  story_flags_updated?: Record<string, boolean>;
  pending_battle?: ExploreResult;
  request_key?: string;
  request_replayed?: boolean;
  story_instance_key?: string;
}

export interface StoryLogEntry {
  location_key: string;
  location_name: string;
  stories: {
    event_key: string;
    event_name: string;
    location_key: string;
    choice_index: number;
    choice_label: string;
    consequence_text: string;
    rewards: unknown;
    applied_outcomes?: {
      snapshot_version: number;
      applied_rewards?: Record<string, unknown>;
      applied_risks?: Record<string, unknown>;
      unlocked_locations?: string[];
      unlock_events?: string[];
      story_flags_updated?: Record<string, boolean>;
      pending_battle?: boolean;
    };
    created_at: string;
  }[];
}

export interface PendingStoriesResponse {
  pending_stories: ExploreResult[];
}

export interface PendingBattlesResponse {
  pending_battles: ExploreResult[];
}

export interface MapNode {
  id: string;
  name: string;
  description: string;
  minLevel: number;
  dangerLevel: number;
  recommendedRank: string;
  unlocked: boolean;
  completed: boolean;
  totalStories: number;
  triggeredStories: number;
  x: number;
  y: number;
  type: 'main' | 'side' | 'hidden' | 'boss';
  volume: number;
  chapter: number;
  chapterName: string;
  keyItems: string[];
  connectedTo: string[];
}

// 单次执行场景的行动力区间（服务端预估，不考虑累计余数）；实际扣费以 /explore/start 为准。
// 树状主线的枝尖状态（见接口契约「路线待续状态」）：
// to_be_continued = 主线走到枝尖、暂无续写；active + event_key = 枝尖已长出新剧情；active + null = 路线进行中。
export interface RouteContinuation {
  status: 'active' | 'to_be_continued';
  event_key: string | null;
}

// 剧情流程图（接口契约「剧情流程图与选择占比」，以 v2 修订为准）。
// 未到达的节点只有不透明 node_id，不含 event_key / 标题，前端不得自行推断剧情。
export type StoryNodeState = 'done' | 'pending_choice' | 'available' | 'locked' | 'closed' | 'tip';
export type SampleBand = '20+' | '50+' | '100+' | '500+';

export interface ChoiceStats {
  percent: number;
  sample_band: SampleBand;
}

export interface StoryTreeNode {
  node_id: string;
  state: StoryNodeState;
  branch: string;
  stage_key: string;
  event_key?: string | null;
  title?: string | null;
  location_name?: string | null;
}

export interface StoryTreeEdge {
  from: string;
  to: string;
  gate: string;
  choice_index?: number | null;
  choice_label?: string | null;
  taken: boolean;
  stats?: ChoiceStats | null;
}

export interface StoryTree {
  chapters: { stage_key: string; label: string }[];
  nodes: StoryTreeNode[];
  edges: StoryTreeEdge[];
  route_continuation?: RouteContinuation;
  route_stats?: ChoiceStats | null;
}

// available = false：当前地点没有你路线上可遇到的主线，寻踪不生效也不加价。
export interface TracePreview {
  available: boolean;
  extra_min: number;
  extra_max: number;
}

// 路线图鉴（契约「路线图鉴与分享路线」v1）：未到达的枝尖只有不透明 tip_id 与占比，不含标题、枝、章节。
export interface RouteCodexTip {
  tip_id: string;
  reached: boolean;
  branch?: string | null;
  title?: string | null;
  chapter_label?: string | null;
  stats?: ChoiceStats | null;
}

export interface RouteCodex {
  growth: number;
  tips: RouteCodexTip[];
  my_tip_id: string | null;
  total_tips: number;
}

export interface ExploreCostPreview {
  minStaminaCost: number;
  maxStaminaCost: number;
  currentStamina: number;
  route_continuation?: RouteContinuation;
  trace_preview?: TracePreview;
}

export const exploreApi = {
  getCostPreview(playerId: number) {
    return http.get<unknown, ExploreCostPreview>(`/explore/cost-preview/${playerId}`);
  },
  getStoryTree(playerId: number) {
    return http.get<unknown, StoryTree>(`/explore/story-tree/${playerId}`);
  },
  getRouteCodex(playerId: number) {
    return http.get<unknown, RouteCodex>(`/explore/route-codex/${playerId}`);
  },
  getLocations(playerId: number) {
    return http.get<unknown, ApiRecord>(`/explore/locations/${playerId}`);
  },
  getMapNodes(playerId: number) {
    return http.get<unknown, { nodes: MapNode[] }>(`/explore/map-nodes/${playerId}`);
  },
  startExplore(playerId: number, locationKey: string, requestKey: string, traceRoute = false) {
    return http.post<unknown, ExploreResult>('/explore/start', { playerId, locationKey, requestKey, traceRoute });
  },
  makeChoice(
    playerId: number,
    eventKey: string,
    choiceIndex: number,
    requestKey: string,
    storyInstanceKey?: string,
  ) {
    return http.post<unknown, ChoiceResult>('/explore/choose', {
      playerId, eventKey, choiceIndex, requestKey, storyInstanceKey,
    });
  },
  getPendingStories(playerId: number) {
    return http.get<unknown, PendingStoriesResponse>(`/explore/pending-stories/${playerId}`);
  },
  getPendingBattles(playerId: number) {
    return http.get<unknown, PendingBattlesResponse>(`/explore/pending-battles/${playerId}`);
  },
  acknowledgePendingBattle(playerId: number, requestKey: string) {
    return http.post<unknown, Record<string, never>>(
      `/explore/pending-battles/${playerId}/ack`, { requestKey },
    );
  },
  getStoryLog(playerId: number) {
    return http.get<unknown, { story_log: StoryLogEntry[] }>(`/explore/story-log/${playerId}`);
  },
};
