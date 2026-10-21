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

export const exploreApi = {
  getLocations(playerId: number) {
    return http.get<unknown, ApiRecord>(`/explore/locations/${playerId}`);
  },
  getMapNodes(playerId: number) {
    return http.get<unknown, { nodes: MapNode[] }>(`/explore/map-nodes/${playerId}`);
  },
  startExplore(playerId: number, locationKey: string, requestKey: string) {
    return http.post<unknown, ExploreResult>('/explore/start', { playerId, locationKey, requestKey });
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
