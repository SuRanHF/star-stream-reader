import { broadcastApi } from '@/api/broadcastApi';
import { bountyApi } from '@/api/bountyApi';
import { chatApi } from '@/api/chatApi';
import { combatApi } from '@/api/combatApi';
import { endingApi } from '@/api/endingApi';
import { equipmentApi } from '@/api/equipmentApi';
import { exploreApi } from '@/api/exploreApi';
import { factionApi } from '@/api/factionApi';
import { friendApi } from '@/api/friendApi';
import { inventoryApi } from '@/api/inventoryApi';
import { narrativeApi } from '@/api/narrativeApi';
import { partyApi } from '@/api/partyApi';
import { pkApi } from '@/api/pkApi';
import { playerApi } from '@/api/playerApi';
import { questApi } from '@/api/questApi';
import { rankingApi } from '@/api/rankingApi';
import { skillApi } from '@/api/skillApi';
import { systemApi } from '@/api/systemApi';
import { titleApi } from '@/api/titleApi';
import { tradeApi } from '@/api/tradeApi';
import { worldBossApi } from '@/api/worldBossApi';
import { worldlineApi } from '@/api/worldlineApi';
import type { ApiRecord } from '@/types/api';
import { asArray, asRecord, pickText } from '@/utils/panelData';

export interface ConnectedPanelLoadContext {
  chatChannel: string;
  friendKeyword: string;
  recentLogs: unknown[];
}

export function dataPanelFor(panel: string) {
  const aliases: Record<string, string> = {
    auction: 'trade',
    shop: 'trade',
    teach: 'skills',
    martial: 'pk',
    trial: 'quests',
    codex: 'titles',
  };
  return aliases[panel] || panel;
}

export async function loadConnectedPanelData(
  panel: string,
  id: number,
  context: ConnectedPanelLoadContext,
): Promise<ApiRecord> {
  switch (dataPanelFor(panel)) {
    case 'explore':
      return {
        locations: asArray(await exploreApi.getLocations(id)),
        worldlineSummary: await worldlineApi.getSummary(),
        worldlineHistory: await worldlineApi.getHistory(),
      };
    case 'hiddenScene':
      return {
        active: await worldBossApi.getActiveWorldBoss(id),
        participation: await worldBossApi.getMyParticipation(id),
        summary: await worldBossApi.getWorldBossSummary(id),
      };
    case 'ranking': {
      const [rankings, myRank, nearbyRankings, avatarRankings, avatarRankInfo, storyDiscoveryRankings, storyDiscoveryPosition, pkPerformanceRankings, pkPerformancePosition, exploreActionRankings, exploreActionPosition] = await Promise.all([
        rankingApi.getRankings(),
        rankingApi.getMyRank(id),
        rankingApi.getNearbyRankings(id).catch(() => []),
        rankingApi.getAvatarLeaderboard(),
        rankingApi.getAvatarRankInfo(id).catch(() => ({})),
        rankingApi.getStoryDiscoveries(100)
          .then((data) => ({ data, unavailable: false }))
          .catch(() => ({ data: [], unavailable: true })),
        rankingApi.getStoryDiscoveryPosition(id)
          .then((data) => ({ data, unavailable: false }))
          .catch(() => ({ data: {}, unavailable: true })),
        rankingApi.getPkPerformanceRankings(100)
          .then((data) => ({ data, unavailable: false }))
          .catch(() => ({ data: [], unavailable: true })),
        rankingApi.getPkPerformancePosition(id)
          .then((data) => ({ data, unavailable: false }))
          .catch(() => ({ data: {}, unavailable: true })),
        rankingApi.getExploreActionRankings(100)
          .then((data) => ({ data, unavailable: false }))
          .catch(() => ({ data: [], unavailable: true })),
        rankingApi.getExploreActionPosition(id)
          .then((data) => ({ data, unavailable: false }))
          .catch(() => ({ data: {}, unavailable: true })),
      ]);
      return {
        rankings,
        myRank,
        nearbyRankings,
        avatarRankings,
        avatarRankInfo,
        storyDiscoveryRankings: storyDiscoveryRankings.data,
        storyDiscoveryRankingsUnavailable: storyDiscoveryRankings.unavailable,
        storyDiscoveryPosition: storyDiscoveryPosition.data,
        storyDiscoveryPositionUnavailable: storyDiscoveryPosition.unavailable,
        pkPerformanceRankings: pkPerformanceRankings.data,
        pkPerformanceRankingsUnavailable: pkPerformanceRankings.unavailable,
        pkPerformancePosition: pkPerformancePosition.data,
        pkPerformancePositionUnavailable: pkPerformancePosition.unavailable,
        exploreActionRankings: exploreActionRankings.data,
        exploreActionRankingsUnavailable: exploreActionRankings.unavailable,
        exploreActionPosition: exploreActionPosition.data,
        exploreActionPositionUnavailable: exploreActionPosition.unavailable,
      };
    }
    case 'history':
      return {
        logs: context.recentLogs,
        quests: await questApi.getQuests(id),
        summary: await questApi.getQuestSummary(id),
        endings: await safeList(() => endingApi.getEndings(id).then(r => asArray(asRecord(r).endings || [])), '结局接口暂不可用'),
        storyLog: await safeList(
          () => exploreApi.getStoryLog(id).then(r => (asRecord(r).story_log || []) as ApiRecord[]),
          '故事记录接口暂不可用',
        ),
        npcEncounters: await safeList(
          () => narrativeApi.getEncounters(id),
          'NPC 残影回顾接口暂不可用',
        ),
      };
    case 'underworld':
      return { deadList: await playerApi.getDeadList() };
    case 'chat':
      return { recent: await chatApi.getRecentMessages(context.chatChannel) };
    case 'trade':
      return {
        listings: await tradeApi.getListings(),
        my: await tradeApi.getMyListings(id),
        records: await tradeApi.getRecords(id),
        summary: await tradeApi.getSummary(id),
      };
    case 'skills':
      return {
        all: await skillApi.getAllSkills(),
        owned: await skillApi.getPlayerSkills(id),
        unlockable: await skillApi.getUnlockable(id),
        bonus: await skillApi.getBonus(id),
      };
    case 'faction': {
      const myFaction = await factionApi.getMyFaction(id);
      const factionKey = pickText(asRecord(myFaction), ['factionKey', 'faction_key'], '');
      const [factions, summary, buff, rankings, wars, skills] = await Promise.all([
        factionApi.getFactions(id),
        factionApi.getFactionSummary(id),
        factionApi.getFactionBuff(id),
        factionApi.getRankings(),
        safeList(() => factionApi.getFactionWars(), '阵营战接口暂不可用'),
        factionKey ? safeList(() => skillApi.getFactionPlayerSkills(id), '阵营技能接口暂不可用') : Promise.resolve([]),
      ]);
      return {
        factions,
        my: myFaction,
        summary,
        buff,
        rankings,
        wars,
        skills,
      };
    }
    case 'pk':
      return { opponents: await pkApi.getOpponents(id), records: await pkApi.getRecords(id) };
    case 'quests':
      return {
        quests: await questApi.getQuests(id),
        summary: await questApi.getQuestSummary(id),
        definitions: await questApi.getDefinitions(),
      };
    case 'friends':
      return {
        friends: await friendApi.getFriends(id),
        requests: await friendApi.getRequests(id),
        summary: await friendApi.getSummary(id),
        recent: await friendApi.getRecent(id),
        inventory: await inventoryApi.getInventory(id),
        search: context.friendKeyword.trim().length >= 2 ? await friendApi.search(context.friendKeyword.trim(), id) : [],
      };
    case 'titles':
      return {
        all: await titleApi.getAll(),
        mine: await titleApi.getMine(id),
        equipped: await titleApi.getEquipped(id),
        effects: await titleApi.getEffects(id),
      };
    case 'inventory':
      return {
        inventory: await inventoryApi.getInventory(id),
      };
    case 'synthesis':
      return {
        recipes: await safeList(() => inventoryApi.getRecipes().then(r => asArray(asRecord(r).recipes || [])), '合成配方接口暂不可用'),
      };
    case 'equipment': {
      const [equipment, equipped, bonus, sets, setBonuses] = await Promise.all([
        equipmentApi.getPlayerEquipment(id),
        equipmentApi.getEquipped(id),
        equipmentApi.getBonus(id),
        equipmentApi.getSets(),
        equipmentApi.getMySetBonuses(id),
      ]);
      return { equipment, equipped, bonus, sets, setBonuses };
    }
    case 'rest':
      return { rest: await playerApi.getRestState(id) };
    case 'party':
      return {
        active: await partyApi.getActiveParties(),
        my: await partyApi.getMyParty(id),
        summary: await partyApi.getSummary(id),
      };
    case 'support':
      return {
        pending: await safeList(() => bountyApi.getPending(), '支援悬赏接口暂不可用'),
        mine: await safeList(() => bountyApi.getMine(id), '我的支援接口暂不可用'),
        locations: await safeList(() => exploreApi.getLocations(id).then(r => asArray(asRecord(r).locations || r)), '地点列表暂不可用'),
        monsters: await safeList(() => combatApi.getMonsters(), '怪物列表暂不可用'),
      };
    case 'worldBoss': {
      const active = await worldBossApi.getActiveWorldBoss(id);
      const bossNo = pickText(active, ['bossNo', 'boss_no'], '');
      const [rankings, participation, history, summary] = await Promise.all([
        bossNo ? worldBossApi.getRankings(bossNo) : Promise.resolve([]),
        worldBossApi.getMyParticipation(id),
        worldBossApi.getHistory(),
        worldBossApi.getWorldBossSummary(id),
      ]);
      return {
        active,
        rankings,
        participation,
        history,
        summary,
      };
    }
    case 'guide':
      return {
        version: await systemApi.getVersion(),
        memories: await safeList(() => narrativeApi.getItemMemories(), '物品记忆接口暂不可用'),
      };
    case 'notice':
      return { broadcasts: await broadcastApi.getActive() };
    case 'feedback':
      return {};
    case 'constellation':
      return {
        constellations: await safeList(() => playerApi.getConstellations().then(r => asArray(asRecord(r).constellations || [])), '星座接口暂不可用'),
      };
    case 'system':
      return {
        version: await systemApi.getVersion(),
        onlineSummary: await systemApi.getOnlineSummary(),
        onlinePlayers: await systemApi.getOnlinePlayers(),
      };
    default:
      return {};
  }
}

async function safeList(loader: () => Promise<ApiRecord[]>, message: string): Promise<ApiRecord[]> {
  try {
    return await loader();
  } catch (error) {
    return [{ message: error instanceof Error ? error.message : message, status: '接口错误' }];
  }
}
