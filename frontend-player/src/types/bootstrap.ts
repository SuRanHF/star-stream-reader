import type { ApiRecord, User } from './api';
import type { Player } from './player';

export interface LogEntry {
  id?: number;
  message?: string;
  type?: string;
  createdAt?: string;
  timestamp?: string;
  [key: string]: unknown;
}

export interface QuestSummary {
  activeCount?: number;
  completedCount?: number;
  claimableCount?: number;
  dailyCompleted?: number;
  weeklyCompleted?: number;
  [key: string]: unknown;
}

export interface GameBootstrap {
  user?: User;
  player?: Player | null;
  serverTime?: string;
  recentLogs?: LogEntry[];
  restState?: ApiRecord | null;
  chapterProgress?: ApiRecord | null;
  avatarRankInfo?: ApiRecord | null;
  titles?: ApiRecord[];
  starstreamTier?: ApiRecord | null;
  inventorySummary?: ApiRecord[] | null;
  equipmentSummary?: ApiRecord | null;
  skillSummary?: ApiRecord | null;
  combatSummary?: ApiRecord | null;
  pkSummary?: ApiRecord | null;
  broadcastSummary?: ApiRecord | null;
  worldlineSummary?: ApiRecord | null;
  chatSummary?: ApiRecord | null;
  onlineSummary?: ApiRecord | null;
  friendSummary?: ApiRecord | null;
  tradeSummary?: ApiRecord | null;
  partySummary?: ApiRecord | null;
  worldBossSummary?: ApiRecord | null;
  factionSummary?: ApiRecord | null;
  questSummary?: QuestSummary | null;
  narrativeSummary?: ApiRecord | null;
}
