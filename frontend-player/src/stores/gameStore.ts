import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type { ApiRecord, User } from '@/types/api';
import type { GameBootstrap, LogEntry, QuestSummary } from '@/types/bootstrap';
import type { Player } from '@/types/player';
import { playerApi } from '@/api/playerApi';

export const useGameStore = defineStore('game', () => {
  const user = ref<User | null>(null);
  const player = ref<Player | null>(null);
  const recentLogs = ref<LogEntry[]>([]);
  const restState = ref<ApiRecord | null>(null);
  const worldlineSummary = ref<ApiRecord>({});
  const broadcastSummary = ref<ApiRecord>({});
  const questSummary = ref<QuestSummary>({});
  const onlineSummary = ref<ApiRecord>({});
  const chatSummary = ref<ApiRecord>({});
  const inventorySummary = ref<ApiRecord[]>([]);
  const equipmentSummary = ref<ApiRecord>({});
  const skillSummary = ref<ApiRecord>({});
  const pkSummary = ref<ApiRecord>({});
  const rankingSummary = ref<ApiRecord>({});
  const factionSummary = ref<ApiRecord>({});
  const worldBossSummary = ref<ApiRecord>({});
  const narrativeSummary = ref<ApiRecord>({});
  const activePanel = ref('');

  const playerName = computed(() => player.value?.playerName || player.value?.['player_name'] || player.value?.name || '未命名读者');

  function applyBootstrap(payload: GameBootstrap) {
    user.value = payload.user || null;
    const rawPlayer = payload.player ? { ...payload.player } : null;
    // 把 stats 里的关键字段展平到 player 顶层，方便组件直接读取
    if (rawPlayer && rawPlayer.stats) {
      const stats = { ...rawPlayer.stats } as ApiRecord;
      rawPlayer.level = (stats.level ?? rawPlayer.level ?? 1) as number;
      rawPlayer.avatarRank = (stats.avatarRank ?? rawPlayer.avatarRank ?? 'F') as string;
      rawPlayer.avatarRankName = (stats.avatarRankName ?? rawPlayer.avatarRankName ?? '临时化身') as string;
      rawPlayer.storyGrade = (stats.storyGrade ?? rawPlayer.storyGrade ?? 'ordinary') as string;
      rawPlayer.exp = (stats.exp ?? rawPlayer.exp ?? 0) as number;
      rawPlayer.maxExp = (stats.maxExp ?? rawPlayer.maxExp ?? 100) as number;
      rawPlayer.hp = (stats.hp ?? rawPlayer.hp ?? 100) as number;
      rawPlayer.maxHp = (stats.maxHp ?? rawPlayer.maxHp ?? 100) as number;
      rawPlayer.stamina = (stats.stamina ?? rawPlayer.stamina ?? 50) as number;
      rawPlayer.maxStamina = (stats.maxStamina ?? rawPlayer.maxStamina ?? 50) as number;
      rawPlayer.attack = (stats.attack ?? rawPlayer.attack ?? 10) as number;
      rawPlayer.defense = (stats.defense ?? rawPlayer.defense ?? 5) as number;
      rawPlayer.speed = (stats.speed ?? rawPlayer.speed ?? 10) as number;
      rawPlayer.isDead = (stats.isDead ?? rawPlayer.isDead ?? false) as boolean;
      rawPlayer.isResting = (stats.isResting ?? rawPlayer.isResting ?? false) as boolean;
      rawPlayer.freePoints = (stats.freePoints ?? rawPlayer.freePoints ?? 0) as number;
      rawPlayer.allocatedAtk = (stats.allocatedAtk ?? rawPlayer.allocatedAtk ?? 0) as number;
      rawPlayer.allocatedDef = (stats.allocatedDef ?? rawPlayer.allocatedDef ?? 0) as number;
      rawPlayer.allocatedSpd = (stats.allocatedSpd ?? rawPlayer.allocatedSpd ?? 0) as number;
      rawPlayer.allocatedCrit = (stats.allocatedCrit ?? rawPlayer.allocatedCrit ?? 0) as number;
    }
    if (rawPlayer) {
      rawPlayer.currentLocationKey = String((rawPlayer as ApiRecord).current_location || rawPlayer.currentLocation || (payload as ApiRecord).current_location || (payload as ApiRecord).currentLocation || '');
      rawPlayer.currentLocationName = String((rawPlayer as ApiRecord).current_location_name || rawPlayer.currentLocationName || (payload as ApiRecord).current_location_name || '');
    }
    player.value = rawPlayer;
    recentLogs.value = payload.recentLogs || [];
    restState.value = payload.restState || null;
    worldlineSummary.value = payload.worldlineSummary || {};
    broadcastSummary.value = payload.broadcastSummary || {};
    questSummary.value = payload.questSummary || {};
    onlineSummary.value = payload.onlineSummary || {};
    chatSummary.value = payload.chatSummary || {};
    inventorySummary.value = payload.inventorySummary || [];
    equipmentSummary.value = payload.equipmentSummary || {};
    skillSummary.value = payload.skillSummary || {};
    pkSummary.value = payload.pkSummary || {};
    factionSummary.value = payload.factionSummary || {};
    worldBossSummary.value = payload.worldBossSummary || {};
    narrativeSummary.value = payload.narrativeSummary || {};
  }

  function setActivePanel(panel: string) {
    activePanel.value = panel;
  }

  function setOnlineSummary(summary: ApiRecord) {
    onlineSummary.value = { ...onlineSummary.value, ...summary };
  }

  function setWorldBossSummary(summary: ApiRecord) {
    worldBossSummary.value = { ...worldBossSummary.value, ...summary };
  }

  function setWorldlineSummary(summary: ApiRecord) {
    worldlineSummary.value = { ...worldlineSummary.value, ...summary };
  }

  function setBroadcastSummary(summary: ApiRecord) {
    broadcastSummary.value = { ...broadcastSummary.value, ...summary };
  }

  function applyRestState(data: ApiRecord) {
    restState.value = { ...restState.value, ...data };
    if (player.value) {
      const p = { ...player.value } as ApiRecord;
      const stats = { ...(p.stats || {}) } as ApiRecord;
      if ('hp' in data) {
        stats.hp = data.hp;
        p.hp = data.hp as number;
      }
      if ('maxHp' in data) {
        stats.maxHp = data.maxHp;
        p.maxHp = data.maxHp as number;
      }
      if ('stamina' in data) {
        stats.stamina = data.stamina;
        p.stamina = data.stamina as number;
      }
      if ('maxStamina' in data) {
        stats.maxStamina = data.maxStamina;
        p.maxStamina = data.maxStamina as number;
      }
      if ('isResting' in data) {
        stats.isResting = data.isResting;
        p.isResting = data.isResting;
      }
      if ('exp' in data) {
        stats.exp = data.exp;
        p.exp = data.exp as number;
      }
      if ('maxExp' in data) {
        stats.maxExp = data.maxExp;
        p.maxExp = data.maxExp as number;
      }
      p.stats = stats;
      player.value = p as unknown as Player;
    }
  }

  function markQuestClaimable(data: ApiRecord) {
    questSummary.value = {
      ...questSummary.value,
      hasRealtimeUpdate: true,
      lastCompletedQuest: data.questName || data.questKey || '任务完成',
    };
  }

  function patchPlayer(data: ApiRecord) {
    if (!player.value) {
      player.value = data as unknown as Player;
      return;
    }
    const incoming = data as ApiRecord;
    const merged: ApiRecord = { ...player.value };
    if (incoming.stats) {
      merged.stats = { ...(player.value.stats || {}), ...(incoming.stats as ApiRecord) };
    }
    const flatKeys = ['coins', 'story_fragments', 'hp', 'maxHp', 'stamina', 'maxStamina',
      'attack', 'defense', 'speed', 'level', 'current_location', 'currentLocation',
      'current_main_chapter', 'currentMainChapter', 'current_chapter', 'currentChapter',
      'avatarRank', 'avatarRankName', 'storyGrade', 'player_name', 'playerName',
      'isDead', 'isResting', 'currentLocationKey', 'currentLocationName', 'current_location_name'];
    for (const key of flatKeys) {
      if (key in incoming) {
        merged[key] = incoming[key];
      }
    }
    // 从 stats 中展平 stats 专属字段
    const stats = merged.stats as ApiRecord | undefined;
    const statsFlatKeys = ['level', 'exp', 'maxExp', 'freePoints',
      'allocatedAtk', 'allocatedDef', 'allocatedSpd', 'allocatedCrit',
      'hp', 'maxHp', 'stamina', 'maxStamina', 'attack', 'defense', 'speed',
      'avatarRank', 'avatarRankName', 'storyGrade', 'isDead', 'isResting'];
    for (const key of statsFlatKeys) {
      if (stats && key in stats) {
        merged[key] = stats[key];
      }
    }
    player.value = merged as unknown as Player;
  }

  async function reloadPlayer() {
    const pid = player.value?.id;
    if (!pid) return;
    try {
      const data = await playerApi.getPlayer(pid);
      patchPlayer(data as unknown as ApiRecord);
    } catch { /* ignore */ }
  }

  return {
    user,
    player,
    recentLogs,
    restState,
    worldlineSummary,
    broadcastSummary,
    questSummary,
    onlineSummary,
    chatSummary,
    inventorySummary,
    equipmentSummary,
    skillSummary,
    pkSummary,
    rankingSummary,
    factionSummary,
    worldBossSummary,
    narrativeSummary,
    activePanel,
    playerName,
    applyBootstrap,
    setActivePanel,
    setOnlineSummary,
    setWorldBossSummary,
    setWorldlineSummary,
    setBroadcastSummary,
    applyRestState,
    markQuestClaimable,
    patchPlayer,
    reloadPlayer,
  };
});
