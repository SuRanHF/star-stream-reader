import { http } from './http';
import type { ApiRecord } from '@/types/api';

export interface ExploreResult {
  result_type: string;
  monster_key?: string;
  result: {
    event_key: string;
    event_type: string;
    name: string;
    description: string;
    choices?: Choice[];
  };
  rewards: Record<string, unknown>;
  progress_effects: Record<string, unknown>;
  new_titles: unknown[];
  stories_exhausted: boolean;
  stories_blocked?: boolean;
  lower_unfinished?: string[];
  player?: ApiRecord;
  new_logs?: unknown[];
}

export interface Choice {
  label: string;
  consequence_text: string;
  rewards_override?: Record<string, unknown>;
  unlock_locations?: string[];
  unlock_events?: string[];
  title_bias?: Record<string, number>;
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
    created_at: string;
  }[];
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
  startExplore(playerId: number, locationKey?: string) {
    return http.post<unknown, ExploreResult>('/explore/start', { playerId, locationKey });
  },
  makeChoice(playerId: number, eventKey: string, choiceIndex: number) {
    return http.post<unknown, ChoiceResult>('/explore/choose', { playerId, eventKey, choiceIndex });
  },
  getStoryLog(playerId: number) {
    return http.get<unknown, { story_log: StoryLogEntry[] }>(`/explore/story-log/${playerId}`);
  },
};
