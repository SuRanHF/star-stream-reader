import { http } from './http';
import type { ApiRecord } from '@/types/api';

export interface CombatStatsVO {
  level: number;
  hp: number;
  maxHp: number;
  attack: number;
  defense: number;
  speed: number;
  critRate: number;
  critDamage: number;
  bonuses: Record<string, number>;
}

export interface CombatRoundVO {
  round: number;
  actions: Record<string, unknown>[];
}

export interface CombatRewardVO {
  exp: number;
  coins: number;
  storyFragments: number;
  channelHeat: number;
  items?: unknown[];
  equipment?: unknown[];
}

export interface CombatResult {
  result: 'win' | 'lose' | 'escape' | 'draw';
  monster: {
    monsterKey: string;
    name: string;
    type: string;
    rarity: string;
    hpBefore: number;
    hpAfter: number;
  };
  playerStats: CombatStatsVO;
  hpBefore: number;
  hpAfter: number;
  totalRounds: number;
  rounds: CombatRoundVO[];
  rewards: CombatRewardVO;
  drops: unknown[];
  newLogs: unknown[];
}

export interface CombatResolveResponse {
  success: boolean;
  message: string;
  damage_taken?: number;
  battle: {
    result: 'win' | 'lose' | 'escape' | 'draw' | 'flee';
    totalRounds: number;
    playerHpRemaining: number;
    monsterHpRemaining?: number;
    playerHpBefore?: number;
    monster?: Record<string, unknown>;
    rewards?: Partial<CombatRewardVO>;
    drops?: unknown[];
  };
  player?: Record<string, unknown>;
}

export const combatApi = {
  getMonsters() {
    return http.get<unknown, ApiRecord[]>('/combat/monsters');
  },
  getMonstersByLocation(locationKey: string) {
    return http.get<unknown, ApiRecord[]>(`/combat/monsters/by-location/${locationKey}`);
  },
  startCombat(playerId: number, monsterKey: string, requestKey: string, signal?: AbortSignal) {
    return http.post<unknown, CombatResult>(
      '/combat/start', { playerId, monsterKey, requestKey }, { signal },
    );
  },
  resolveCombat(playerId: number, monsterKey: string, action: string, requestKey: string, signal?: AbortSignal) {
    return http.post<unknown, CombatResolveResponse>('/combat/resolve', {
      playerId, monsterKey, action, requestKey,
    }, { signal });
  },
};
