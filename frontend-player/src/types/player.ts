export interface PlayerStats {
  hp?: number;
  maxHp?: number;
  stamina?: number;
  maxStamina?: number;
  attack?: number;
  defense?: number;
  speed?: number;
  level?: number;
  exp?: number;
  maxExp?: number;
  [key: string]: unknown;
}

export interface Player {
  id: number;
  playerId?: number;
  playerName?: string;
  name?: string;
  avatarRank?: string;
  storyGrade?: string;
  currentLocation?: string;
  currentLocationKey?: string;
  currentLocationName?: string;
  coins?: number;
  storyFragments?: number;
  story_fragments?: number;
  stats?: PlayerStats;
  statsJson?: PlayerStats;
  [key: string]: unknown;
}
