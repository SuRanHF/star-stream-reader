<script setup lang="ts">
import { computed, inject, ref, watch, onBeforeUnmount } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import { useRouter } from 'vue-router';
import { broadcastApi } from '@/api/broadcastApi';
import { bountyApi } from '@/api/bountyApi';
import { chatApi } from '@/api/chatApi';
import { combatApi } from '@/api/combatApi';
import { endingApi } from '@/api/endingApi';
import EndingArchivePanel from '@/components/panels/EndingArchivePanel.vue';
import { equipmentApi } from '@/api/equipmentApi';
import { exploreApi } from '@/api/exploreApi';
import { factionApi } from '@/api/factionApi';
import { friendApi } from '@/api/friendApi';
import { gameApi } from '@/api/gameApi';
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
import { realtimeClient } from '@/realtime/realtimeClient';
import { useAuthStore } from '@/stores/authStore';
import { useGameStore } from '@/stores/gameStore';
import type { ApiRecord } from '@/types/api';
import { formatItemUseSuccess, formatQuestClaimSuccess } from '@/utils/actionFeedback';
import { refreshActionState } from '@/utils/actionRefresh';
import { advanceGhostDialogue, startGhostDialogue } from '@/utils/narrativeDialogue';
import {
  exploreActionEmptyStateMessage,
  formatRatingDelta,
  formatRankingProximity,
  getExploreActionPositionState,
  getPkPerformancePositionState,
  getStoryDiscoveryPositionState,
  isHighAvatarRank,
  pkPerformanceEmptyStateMessage,
  storyDiscoveryEmptyStateMessage,
  withLeaderboardPositions,
} from '@/utils/ranking';
import {
  abilityTypeLabel,
  classifyAbilityType,
  supportsStandardSkillLifecycle,
} from '@/utils/abilityTaxonomy';
import { getSkillSlotGuidance } from '@/utils/skillSlotGuidance';
import { filterQuestItems } from '@/utils/questFilter';
import { questDependencyHint } from '@/utils/questDependencyHint';
import { buildStoryRecapEntries, hasStoryRecapLoadError } from '@/utils/storyRecap';
import { buildEndingArchiveEntries } from '@/utils/endingArchive';
import { WORLDLINE_HISTORY_DESCRIPTION, worldlineCauseDisplay } from '@/utils/worldlineDisplay';
import {
  asArray,
  asRecord,
  confirmAction,
  displayMeta,
  formatEquipmentDetails,
  formatCount,
  formatSkillEffectSummary,
  formatSummaryValue,
  LABEL_MAP,
  pick,
  pickKey,
  pickNumber,
  pickText,
  type PanelAction,
  type PanelBlock,
} from '@/utils/panelData';

const props = defineProps<{
  panel: string;
}>();

const emit = defineEmits<{
  selectPanel: [panel: string];
  'close-panel': [];
}>();

const router = useRouter();
const authStore = useAuthStore();
const gameStore = useGameStore();
const queryClient = useQueryClient();

const actionMessage = ref('');
const actionError = ref('');
const actionBusy = ref('');
let toastTimer: ReturnType<typeof setTimeout> | undefined;

function flashToast(msg: string, err: boolean) {
  if (err) {
    actionError.value = msg;
    actionMessage.value = '';
  } else {
    actionMessage.value = msg;
    actionError.value = '';
  }
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    actionMessage.value = '';
    actionError.value = '';
  }, 4000);
}

function getDurabilityClass(meta: string): string {
  const match = meta.match(/耐久[:\s]*(\d+)\/(\d+)/);
  if (!match) return '';
  const pct = (Number(match[1]) / Number(match[2])) * 100;
  if (pct >= 70) return 'durability-good';
  if (pct >= 40) return 'durability-warn';
  return 'durability-bad';
}

interface ProgressInfo {
  pct: number;
  barClass: string;
}

function parseProgress(statLabel: string, statValue: string): ProgressInfo | null {
  const m = statValue.match(/(\d+)\s*\/\s*(\d+)/);
  if (!m) return null;
  const pct = Number(m[2]) ? Math.round((Number(m[1]) / Number(m[2])) * 100) : 0;
  const label = statLabel.toLowerCase();
  let barClass = 'hp';
  if (label.includes('行动') || label.includes('stamina') || label.includes('体力')) barClass = 'stamina';
  else if (label.includes('经验') || label.includes('exp')) barClass = 'exp';
  else if (label.includes('耐久') || label.includes('dura')) barClass = 'durability';
  return { pct, barClass };
}
const chatText = ref('');
const chatFilter = ref<'all' | 'chat' | 'notice'>('all');
const feedbackText = ref('');
const feedbackType = ref('bug');
const friendKeyword = ref('');
const friendFilter = ref<'全部' | '在线' | '离线' | '有未读'>('全部');
const friendSort = ref<'默认' | '等级'>('默认');
const friendFilters = ['全部', '在线', '离线', '有未读'] as const;
const encyclopediaSearch = ref('');
const rankingKeyword = ref('');
const rankingScope = ref<'全部' | '常规成长阶' | '高阶成长阶'>('全部');
const rankingBoard = ref('成长阶');
const rankingVisibleLimit = ref(30);
const rankingScopes = ['全部', '常规成长阶', '高阶成长阶'] as const;
const inventorySearch = ref('');
const inventoryRarity = ref('');
const inventorySort = ref('default');
const inventoryFilterOpen = ref(false);
const skillSearch = ref('');
const tradeSearch = ref('');
type TradeWorkbenchTab = 'market' | 'buyreq' | 'mine' | 'watch' | 'history' | 'public';
const tradeTab = ref<TradeWorkbenchTab>('market');
const tradeSort = ref<'newest' | 'priceAsc' | 'priceDesc'>('newest');
type TransmitWorkbenchTab = 'browse' | 'list' | 'my' | 'history';
const transmitTab = ref<TransmitWorkbenchTab>('browse');
const transmitSearch = ref('');
const transmitType = ref('');
const transmitRarity = ref('');
const transmitRealm = ref('');
type PvpWorkbenchTab = 'opponents' | 'records';
const pvpTab = ref<PvpWorkbenchTab>('opponents');
const pvpSearch = ref('');
const pvpMode = ref<'spar' | 'deathmatch'>('spar');
const bountyMonsterKey = ref('');
const bountyLocationKey = ref('');
const bountySharePercent = ref(50);
const ghostNarrative = ref('');
const ghostChoices = ref<Array<{ index: number; text: string }>>([]);
const ghostNodeIndex = ref(0);
const ghostKey = ref('');
const ghostDialogueTree = ref<ApiRecord[]>([]);
const showGhostDialog = ref(false);
const giftTarget = ref<ApiRecord | null>(null);
const giftItemKey = ref('');
const giftQuantity = ref(1);
const showGiftDialog = ref(false);

const playerId = computed(() => Number(gameStore.player?.id || gameStore.player?.playerId || 0));
const activeSubMenus = ref<Record<string, string>>({});
const activeSubMenu = computed(() => activeSubMenus.value[props.panel] || subMenusFor(props.panel)[0] || '');

const panelQuery = useQuery({
  queryKey: computed(() => [
    'feature-panel',
    props.panel,
    playerId.value,
    friendKeyword.value,
    props.panel === 'chat' ? activeSubMenu.value : '',
  ]),
  queryFn: () => loadPanel(props.panel, playerId.value),
  enabled: computed(() => canLoadPanel(props.panel, playerId.value)),
  retry: 1,
});

const giftItems = computed(() => asArray(asRecord(panelQuery.data.value).inventory)
  .filter((item) => {
    const itemKey = pickText(item, ['itemKey', 'item_key'], '');
    const isEquipment = item.slot !== undefined || item.maxDurability !== undefined || item.max_durability !== undefined;
    return Boolean(itemKey) && !isEquipment;
  }));

const inventoryItems = computed(() => asArray(asRecord(panelQuery.data.value).inventory));
const inventoryItemCount = computed(() => inventoryItems.value.reduce(
  (total, item) => total + Math.max(0, pickNumber(item, ['quantity', 'count'], 1)),
  0,
));

const equipmentSlotDefinitions = [
  { key: 'weapon', label: '武器', aliases: ['weapon', 'mainHand', '主手'] },
  { key: 'armor', label: '护甲', aliases: ['armor', 'body', '防具', '服饰'] },
  { key: 'helmet', label: '头盔', aliases: ['helmet', 'head', '头部'] },
  { key: 'accessory', label: '饰品', aliases: ['accessory', 'accessory1', 'accessory2', '法器'] },
  { key: 'relic', label: '遗物', aliases: ['relic'] },
] as const;

const LEGACY_EQUIPMENT_SLOT_LABELS: Record<string, string> = {
  ring: '戒指',
  bag: '储物',
  furnace: '炼制器',
  offHand: '副手',
  legs: '腿部',
  feet: '足部',
};

const equipmentSlots = computed(() => {
  const equipped = asRecord(panelQuery.data.value?.equipped);
  const entries = Object.entries(equipped).map(([key, value]) => {
    const item = asRecord(value);
    return { key, item, slot: pickText(item, ['slot'], key) };
  });
  const configured = equipmentSlotDefinitions.map((slot) => {
    const entry = entries.find((candidate) => (slot.aliases as readonly string[]).includes(candidate.slot));
    const item = entry?.item || {};
    const hasItem = Object.keys(item).length > 0
      && pickText(item, ['equipmentName', 'name', 'equipmentKey'], '') !== '';
    return { ...slot, item: hasItem ? item : null };
  });
  const knownSlots = new Set<string>(equipmentSlotDefinitions.flatMap((slot) => [...slot.aliases]));
  const legacy = entries
    .filter((entry) => !knownSlots.has(entry.slot))
    .filter(({ item }) => pickText(item, ['equipmentName', 'name', 'equipmentKey'], '') !== '')
    .map(({ key, item, slot }) => ({
      key: `legacy-${key}`,
      label: LEGACY_EQUIPMENT_SLOT_LABELS[slot] || '其他装备',
      aliases: [slot],
      item,
    }));
  return [...configured, ...legacy];
});

const equippedSlotCount = computed(() => equipmentSlots.value.filter((slot) => slot.item).length);

const panelTitle = computed(() => titleFor(props.panel));
const panelSubtitle = computed(() => subtitleFor(props.panel));
const blocks = computed(() => buildBlocks(props.panel, asRecord(panelQuery.data.value)));
const secondaryMenus = computed(() => subMenusFor(props.panel));

const panelData = computed(() => asRecord(panelQuery.data.value));
const storyDiscoveryPositionState = computed(() => getStoryDiscoveryPositionState(
  asRecord(panelData.value.storyDiscoveryPosition),
  truthyRecordValue(panelData.value, ['storyDiscoveryPositionUnavailable']),
));
const pkPerformancePositionState = computed(() => getPkPerformancePositionState(
  asRecord(panelData.value.pkPerformancePosition),
  truthyRecordValue(panelData.value, ['pkPerformancePositionUnavailable']),
));
const exploreActionPositionState = computed(() => getExploreActionPositionState(
  asRecord(panelData.value.exploreActionPosition),
  truthyRecordValue(panelData.value, ['exploreActionPositionUnavailable']),
));
const pkPerformanceNextScore = computed(() => pick(
  asRecord(panelData.value.pkPerformancePosition), ['nextRankRatingDelta'],
));
const exploreActionNextScore = computed(() => pick(
  asRecord(panelData.value.exploreActionPosition), ['nextRankActionCount'],
));
const tradeListings = computed(() => {
  const payload = asRecord(panelData.value.listings);
  return asArray(payload.listings ?? panelData.value.listings);
});
const tradeMyListings = computed(() => asArray(panelData.value.my));
const tradeRecords = computed(() => asArray(panelData.value.records));
const tradeSummary = computed(() => asRecord(panelData.value.summary));
const tradeRows = computed(() => {
  let rows: ApiRecord[];
  if (tradeTab.value === 'market') rows = tradeListings.value;
  else if (tradeTab.value === 'mine') rows = tradeMyListings.value;
  else if (tradeTab.value === 'history' || tradeTab.value === 'public') rows = tradeRecords.value;
  else rows = [];

  const keyword = tradeSearch.value.trim().toLowerCase();
  const filtered = rows.filter((entry) => !keyword || [
    pickText(entry, ['itemName', 'equipmentName', 'name', 'listingNo'], ''),
    pickText(entry, ['sellerName', 'buyerName', 'status'], ''),
  ].join(' ').toLowerCase().includes(keyword));

  return [...filtered].sort((left, right) => {
    if (tradeSort.value === 'priceAsc') {
      return pickNumber(left, ['unitPrice', 'totalPrice'], 0) - pickNumber(right, ['unitPrice', 'totalPrice'], 0);
    }
    if (tradeSort.value === 'priceDesc') {
      return pickNumber(right, ['unitPrice', 'totalPrice'], 0) - pickNumber(left, ['unitPrice', 'totalPrice'], 0);
    }
    const leftTime = new Date(pickText(left, ['createdAt', 'created_at', 'soldAt'], '')).getTime() || 0;
    const rightTime = new Date(pickText(right, ['createdAt', 'created_at', 'soldAt'], '')).getTime() || 0;
    return rightTime - leftTime;
  });
});
const tradeTabLabels: Record<TradeWorkbenchTab, string> = {
  market: '市场',
  buyreq: '求购',
  mine: '我的',
  watch: '关注',
  history: '我的成交',
  public: '成交',
};
const tradeTabs: Array<{ key: TradeWorkbenchTab; label: string }> = [
  { key: 'market', label: '市场' },
  { key: 'buyreq', label: '求购' },
  { key: 'mine', label: '我的' },
  { key: 'watch', label: '关注' },
  { key: 'history', label: '我的成交' },
  { key: 'public', label: '成交' },
];
const transmitTabs: Array<{ key: TransmitWorkbenchTab; label: string }> = [
  { key: 'browse', label: '浏览' },
  { key: 'list', label: '技能分享' },
  { key: 'my', label: '我的' },
  { key: 'history', label: '学习记录' },
];
const transmitOwnedKeys = computed(() => new Set(
  asArray(panelData.value.owned)
    .map((entry) => pickText(entry, ['skillKey', 'skill_key', 'key'], ''))
    .filter(Boolean),
));
const transmitUnlockableKeys = computed(() => new Set(
  asArray(panelData.value.unlockable)
    .map((entry) => pickText(entry, ['skillKey', 'skill_key', 'key'], ''))
    .filter(Boolean),
));
const skillSlotNotice = computed(() => {
  const summary = asRecord(gameStore.skillSummary);
  const slotLimit = pick(summary, ['skillSlotLimit', 'skill_slot_limit']);
  if (typeof slotLimit !== 'number') return null;
  return getSkillSlotGuidance(
    slotLimit,
    pickNumber(summary, ['equippedSkillCount', 'equipped_skill_count'], 0),
  );
});
const transmitRows = computed(() => {
  const base = transmitTab.value === 'browse'
    ? asArray(panelData.value.all)
    : transmitTab.value === 'my'
      ? asArray(panelData.value.owned)
      : [];
  const keyword = transmitSearch.value.trim().toLowerCase();
  return base.filter((entry) => {
    const type = pickText(entry, ['type', 'skillType', 'skill_type'], '').toLowerCase();
    const rarity = pickText(entry, ['rarity', 'tier', 'grade'], '').toLowerCase();
    const realm = pickNumber(entry, ['requiredRealm', 'required_realm', 'minRealm', 'min_realm', 'realmLevel'], 0);
    const matchesKeyword = !keyword || [
      pickText(entry, ['skillName', 'name', 'skillKey', 'skill_key'], ''),
      pickText(entry, ['description', 'desc', 'effects'], ''),
    ].join(' ').toLowerCase().includes(keyword);
    const matchesType = !transmitType.value
      || classifyAbilityType(type) === transmitType.value;
    const matchesRarity = !transmitRarity.value || rarity.includes(transmitRarity.value.toLowerCase());
    const matchesRealm = !transmitRealm.value || realm >= Number(transmitRealm.value);
    return matchesKeyword && matchesType && matchesRarity && matchesRealm;
  });
});
const pvpOpponentRows = computed(() => {
  const keyword = pvpSearch.value.trim().toLowerCase();
  return asArray(panelData.value.opponents).filter((entry) => !keyword || [
    pickText(entry, ['playerName', 'player_name', 'name'], ''),
    pickText(entry, ['realm', 'avatarRank', 'rank', 'location'], ''),
  ].join(' ').toLowerCase().includes(keyword));
});
const pvpRecordRows = computed(() => {
  const keyword = pvpSearch.value.trim().toLowerCase();
  return asArray(panelData.value.records).filter((entry) => !keyword || [
    pickText(entry, ['opponentName', 'opponent_name', 'defenderName', 'attackerName', 'winnerName'], ''),
    pickText(entry, ['result', 'status', 'mode'], ''),
  ].join(' ').toLowerCase().includes(keyword));
});
const activeBoss = computed(() => asRecord(panelData.value.active));
const bossRankings = computed(() => asArray(panelData.value.rankings));
const bossParticipation = computed(() => asArray(panelData.value.participation));
const bossHistory = computed(() => asArray(panelData.value.history));
const rankingRows = computed(() => {
  const data = panelData.value;
  const isAvatarRankBoard = rankingBoard.value === '成长阶';
  const isStoryDiscoveryBoard = rankingBoard.value === '近7日故事发现';
  const isPkPerformanceBoard = rankingBoard.value === '近7日PK表现';
  const isExploreActionBoard = rankingBoard.value === '近7日探索行动';
  const source = isAvatarRankBoard
    ? asArray(data.avatarRankings)
    : isStoryDiscoveryBoard ? asArray(data.storyDiscoveryRankings)
      : isPkPerformanceBoard ? asArray(data.pkPerformanceRankings)
        : isExploreActionBoard ? asArray(data.exploreActionRankings) : asArray(data.rankings);
  const positionedSource = isAvatarRankBoard ? withLeaderboardPositions(source) : source;
  const keyword = rankingKeyword.value.trim().toLowerCase();
  return positionedSource.filter((entry) => {
    const name = pickText(entry, ['player_name', 'playerName', 'name'], '').toLowerCase();
    const realm = pickText(entry, ['avatarRank', 'storyGrade', 'realm', 'rank'], '').toLowerCase();
    const scope = rankingScope.value;
    const isHighRank = isHighAvatarRank(realm);
    return (!keyword || `${name} ${realm}`.includes(keyword))
      && (!isAvatarRankBoard || scope === '全部' || (scope === '高阶成长阶' ? isHighRank : !isHighRank));
  });
});
const nearbyRankingRows = computed(() => asArray(panelData.value.nearbyRankings));
const avatarRankRequirements = computed(() => asArray(asRecord(panelData.value.avatarRankInfo).requirements));
const pendingAvatarRankRequirements = computed(() => avatarRankRequirements.value.filter(
  (requirement) => !truthyRecordValue(requirement, ['completed']),
));
const nextAvatarRankRequirement = computed(() => pendingAvatarRankRequirements.value[0]);

function isMyRanking(entry: ApiRecord): boolean {
  return pickNumber(entry, ['playerId', 'player_id'], 0) === playerId.value;
}

function rankingNearbyLabel(entry: ApiRecord): string {
  const myRank = asRecord(panelData.value.myRank);
  return formatRankingProximity(
    pickNumber(entry, ['playerId', 'player_id'], 0),
    playerId.value,
    pickNumber(entry, ['rank'], 0),
    pickNumber(myRank, ['rank'], 0),
    pickNumber(entry, ['rating'], 0),
    pickNumber(myRank, ['rating'], 0),
  );
}

function truthyRecordValue(entry: ApiRecord, keys: string[]): boolean {
  return keys.some((key) => {
    const value = pick(entry, [key]);
    return value === true || value === 1 || value === '1' || String(value || '').toLowerCase() === 'true';
  });
}

const friendRows = computed(() => {
  const rows = asArray(panelData.value.friends).filter((entry) => {
    const online = truthyRecordValue(entry, ['online', 'isOnline', 'is_online'])
      || ['online', '在线'].includes(pickText(entry, ['status'], '').toLowerCase());
    const unread = truthyRecordValue(entry, ['unread', 'hasUnread', 'has_unread', 'unreadCount'])
      || pickNumber(entry, ['unreadCount', 'unread_count'], 0) > 0;
    if (friendFilter.value === '在线') return online;
    if (friendFilter.value === '离线') return !online;
    if (friendFilter.value === '有未读') return unread;
    return true;
  });
  return [...rows].sort((left, right) => {
    if (friendSort.value === '等级') return pickNumber(right, ['level'], 0) - pickNumber(left, ['level'], 0);
    return 0;
  });
});
const friendRequestRows = computed(() => asArray(panelData.value.requests));
const friendRecentRows = computed(() => asArray(panelData.value.recent));
const friendSearchRows = computed(() => asArray(panelData.value.search));
const friendOnlineCount = computed(() => friendRows.value.filter((entry) => truthyRecordValue(entry, ['online', 'isOnline', 'is_online'])).length);
const encyclopediaOwnedKeys = computed(() => new Set(
  asArray(panelData.value.mine)
    .map((entry) => pickText(entry, ['titleKey', 'title_key', 'key'], ''))
    .filter(Boolean),
));
const encyclopediaRows = computed(() => {
  const section = activeSubMenu.value;
  const keyword = encyclopediaSearch.value.trim().toLowerCase();
  let rows = section === '档案检索' ? asArray(panelData.value.all) : asArray(panelData.value.mine);
  if (section !== '档案检索') {
    const categoryMap: Record<string, string[]> = {
      材料档案: ['material', '材料'],
      补给档案: ['consumable', 'supply', '补给', '消耗品'],
      装备档案: ['equipment', 'weapon', 'armor', '装备'],
      技能档案: ['skill', '功法', '技能'],
    };
    const aliases = categoryMap[section] || [];
    rows = rows.filter((entry) => {
      const type = pickText(entry, ['type', 'category', 'itemType', 'titleType', 'source'], '').toLowerCase();
      return aliases.some((alias) => type.includes(alias));
    });
  }
  if (!keyword) return rows;
  return rows.filter((entry) => [
    pickText(entry, ['titleName', 'name', 'titleKey'], ''),
    pickText(entry, ['description', 'source', 'effects', 'type'], ''),
  ].join(' ').toLowerCase().includes(keyword));
});
const chatChannel = computed(() => ({
  星流: 'world',
  阵营: 'faction',
  队伍: 'team',
}[activeSubMenu.value] || 'world'));

const chatMessages = computed(() => {
  const channel = chatChannel.value;
  return asArray(panelData.value.recent).filter((entry) => {
    const entryChannel = pickText(entry, ['channel'], channel).toLowerCase();
    return entryChannel === channel || (channel === 'world' && entryChannel === 'system');
  });
});

const visibleChatMessages = computed(() => {
  if (chatFilter.value === 'all') return chatMessages.value;
  return chatMessages.value.filter((entry) => {
    const messageType = pickText(entry, ['messageType', 'message_type', 'type'], 'player').toLowerCase();
    return chatFilter.value === 'notice' ? messageType !== 'player' : messageType === 'player';
  });
});

const historyFilters = ['全部记录', '收益记录', '战斗记录', '场景记录', '交易记录'];
const historyLogs = computed(() => {
  const filter = activeSubMenu.value;
  const logs = asArray(panelData.value.logs);
  if (filter === '收益记录') return logs.filter((entry) => ['income', 'reward', '收益'].includes(pickText(entry, ['type'], '')));
  if (filter === '战斗记录') return logs.filter((entry) => ['battle', 'combat', 'pk', '战斗'].includes(pickText(entry, ['type'], '')));
  if (filter === '场景记录') return logs.filter((entry) => ['explore', 'scene', 'adventure', 'move', '场景'].includes(pickText(entry, ['type'], '')));
  if (filter === '交易记录') return logs.filter((entry) => ['trade', 'market', 'exchange', '交易'].includes(pickText(entry, ['type'], '')));
  return logs;
});

const storyLogEntries = computed(() => buildStoryRecapEntries(
  panelData.value.storyLog,
  panelData.value.npcEncounters,
));
const npcRecapUnavailable = computed(() => hasStoryRecapLoadError(panelData.value.npcEncounters));
const endingArchiveEntries = computed(() => buildEndingArchiveEntries(panelData.value.endings));

const questItems = computed(() => {
  const filter = activeSubMenu.value;
  const quests = asArray(panelData.value.quests);
  return filterQuestItems(quests, filter);
});

const questSummary = computed(() => asRecord(panelData.value.summary));
const partySummary = computed(() => asRecord(panelData.value.summary));
const myParty = computed(() => asRecord(panelData.value.my));
const partyMembers = computed(() => asArray(myParty.value.members));
const activeParties = computed(() => asArray(panelData.value.active));

// 星座阵营使用独立工作台承载身份摘要、阵营选择、技能与战绩。
// 数据仍然来自现有 factionApi / skillApi，不改变任何业务接口。
const sectMe = computed(() => asRecord(panelData.value.my));
const sectSummary = computed(() => asRecord(panelData.value.summary));
const sectBuff = computed(() => asRecord(panelData.value.buff));
const sectHasFaction = computed(() => Boolean(
  pickText(sectSummary.value, ['joined'], '') === 'true'
    || pickNumber(sectSummary.value, ['joined'], 0) === 1
    || pickText(sectSummary.value, ['factionKey', 'faction_key'], '')
    || pickText(sectMe.value, ['factionKey', 'faction_key'], ''),
));
const sectName = computed(() => pickText(
  sectSummary.value,
  ['factionName', 'name'],
  pickText(sectMe.value, ['factionName', 'name'], '未加入阵营'),
));
const sectFactions = computed(() => asArray(panelData.value.factions));
const sectRankings = computed(() => asArray(panelData.value.rankings));
const sectSkills = computed(() => asArray(panelData.value.skills));
const sectWars = computed(() => asArray(panelData.value.wars));
const sectKpis = computed(() => [
  { label: '阵营等级', value: pickText(sectSummary.value, ['factionLevel', 'level'], pickText(sectMe.value, ['factionLevel', 'level'], '—')) },
  { label: '阵营排名', value: pickText(sectSummary.value, ['factionRank', 'rank'], '—') },
  { label: '个人声望', value: pickText(sectSummary.value, ['reputation'], pickText(sectMe.value, ['reputation'], '0')) },
  { label: '累计贡献', value: pickText(sectSummary.value, ['contributionTotal', 'totalContributionScore'], pickText(sectMe.value, ['contributionTotal', 'totalContributionScore'], '0')) },
  { label: '今日贡献', value: pickText(sectSummary.value, ['dailyContribution'], pickText(sectBuff.value, ['dailyContribution'], '0')) },
  { label: '当前加成', value: pickText(sectSummary.value, ['factionBuff'], pickText(sectBuff.value, ['buffAtk'], '0')) },
]);

// 参考站的侧栏不是简单的数据表，而是由摘要、分柜和卡片组成的工作台。
// 这些面板继续使用星流读者自己的查询与操作，只切换到同一套视觉骨架。
const referenceWorkbenchPanels = [
  'pk',
  'titles', 'synthesis', 'trade', 'skills', 'rest', 'support', 'underworld',
  'guide', 'notice', 'system', 'constellation',
];

watch(() => props.panel, () => {
  activeSubMenus.value = {};
  encyclopediaSearch.value = '';
  inventorySearch.value = '';
  inventoryRarity.value = '';
  inventorySort.value = 'default';
  inventoryFilterOpen.value = false;
  skillSearch.value = '';
  tradeSearch.value = '';
  chatFilter.value = 'all';
  rankingKeyword.value = '';
  rankingScope.value = '全部';
  rankingBoard.value = '成长阶';
  rankingVisibleLimit.value = 30;
  friendFilter.value = '全部';
  friendSort.value = '默认';
});

function inventoryRarityMatches(entry: ApiRecord): boolean {
  if (!inventoryRarity.value) return true;
  const rarity = pickText(entry, ['rarity', 'tier'], '').toLowerCase();
  const aliases: Record<string, string[]> = {
    '1': ['1', 'common', '普通'],
    '2': ['2', 'uncommon', '优良', '非凡'],
    '3': ['3', 'rare', '稀有'],
    '4': ['4', 'epic', '史诗'],
    '5': ['5', 'legendary', '传说'],
  };
  return aliases[inventoryRarity.value]?.some((value) => rarity === value || rarity.includes(value)) ?? true;
}

function inventoryEntryName(entry: ApiRecord): string {
  return pickText(entry, ['itemName', 'name', 'itemKey'], '').toLowerCase();
}

function filterSkillEntries(entries: ApiRecord[]): ApiRecord[] {
  const keyword = skillSearch.value.trim().toLowerCase();
  if (!keyword) return entries;
  return entries.filter((entry) => [
    pickText(entry, ['skillName', 'name', 'skillKey'], ''),
    pickText(entry, ['description', 'desc', 'effects'], ''),
    pickText(entry, ['type', 'rarity'], ''),
  ].join(' ').toLowerCase().includes(keyword));
}

function filterTradeEntries(entries: ApiRecord[]): ApiRecord[] {
  const keyword = tradeSearch.value.trim().toLowerCase();
  if (!keyword) return entries;
  return entries.filter((entry) => [
    pickText(entry, ['itemName', 'equipmentName', 'name', 'listingNo'], ''),
    pickText(entry, ['sellerName', 'buyerName', 'status'], ''),
  ].join(' ').toLowerCase().includes(keyword));
}

function tradeEntryName(entry: ApiRecord): string {
  return pickText(entry, ['itemName', 'equipmentName', 'name', 'itemKey', 'equipmentKey'], '未知物品');
}

function tradeStatusLabel(status: unknown): string {
  const labels: Record<string, string> = {
    active: '在售',
    sold: '已成交',
    cancelled: '已撤单',
    pending: '待处理',
  };
  const value = String(status || '').toLowerCase();
  return labels[value] || String(status || '已记录');
}

function tradeEntryActions(entry: ApiRecord): PanelAction[] {
  if (tradeTab.value === 'market') return tradeBuyActions(entry);
  if (tradeTab.value === 'mine') return tradeCancelActions(entry);
  return [];
}

function entryRarityClass(entry: { meta?: string[] } | undefined): string {
  const rarityMeta = entry?.meta?.find((meta) => meta.startsWith('稀有度') || meta.startsWith('品质')) || '';
  const rarity = rarityMeta.toLowerCase();
  if (rarity.includes('传说') || /(?:^|\s|:)5(?:$|\s)/.test(rarity)) return 'r5';
  if (rarity.includes('史诗') || /(?:^|\s|:)4(?:$|\s)/.test(rarity)) return 'r4';
  if (rarity.includes('稀有') || /(?:^|\s|:)3(?:$|\s)/.test(rarity)) return 'r3';
  if (rarity.includes('优良') || rarity.includes('非凡') || /(?:^|\s|:)2(?:$|\s)/.test(rarity)) return 'r2';
  if (rarity.includes('普通') || /(?:^|\s|:)1(?:$|\s)/.test(rarity)) return 'r1';
  return '';
}

function equipmentRarityClass(item: ApiRecord | null): string {
  const rarity = pickText(item || {}, ['rarity', 'tier'], '').toLowerCase();
  if (rarity.includes('传说') || rarity === '5' || rarity.includes('legendary')) return 'r5';
  if (rarity.includes('史诗') || rarity === '4' || rarity.includes('epic')) return 'r4';
  if (rarity.includes('稀有') || rarity === '3' || rarity.includes('rare')) return 'r3';
  if (rarity.includes('优良') || rarity.includes('非凡') || rarity === '2' || rarity.includes('uncommon')) return 'r2';
  if (rarity.includes('普通') || rarity === '1' || rarity.includes('common')) return 'r1';
  return '';
}

function equipmentDetail(item: ApiRecord | null): string {
  if (!item) return '点击进入装备整备';
  const durability = pickText(item, ['durability'], '');
  const maxDurability = pickNumber(item, ['maxDurability', 'max_durability'], 0);
  const enhance = pickNumber(item, ['enhancementLevel', 'enhancement_level'], 0);
  const maxEnhance = pickNumber(item, ['maxEnhancementLevel', 'max_enhancement_level'], 10);
  const bonus = pickNumber(item, ['enhancementBonusPercent', 'enhancement_bonus_percent'], enhance * 5);
  const properties = formatEquipmentDetails(item);
  const durabilityText = durability
    ? `耐久 ${durability}${maxDurability > 0 ? `/${maxDurability}` : ''}`
    : '';
  return [durabilityText, properties, `强化 +${enhance}/${maxEnhance}（基础属性 +${bonus}%）`]
    .filter(Boolean).join(' · ') || '已装备';
}

function canLoadPanel(panel: string, id: number) {
  return ['guide', 'system', 'feedback'].includes(panel) || Boolean(id);
}

function titleFor(panel: string) {
  const titles: Record<string, string> = {
    explore: '场景地图',
    hiddenScene: '隐藏场景',
    ranking: '化身排行',
    history: '故事记录',
    underworld: '冥界',
    chat: '星流频道',
    trade: '鬼怪商店',
    skills: '技能图谱',
    faction: '星座阵营',
    pk: '化身战场',
    quests: '场景试炼',
    friends: '私信',
    titles: '档案',
    inventory: '背包',
    synthesis: '合成',
    equipment: '装备',
    rest: '安全区',
    party: '队伍',
    support: '支援',
    worldBoss: '灾厄',
    guide: '说明',
    notice: '星流公告',
    feedback: '鬼怪反馈',
    system: '系统',
  };
  return titles[panel] || '面板';
}

function subtitleFor(panel: string) {
  const subtitles: Record<string, string> = {
    explore: '地点、切换、探索。',
    hiddenScene: '组队副本、Boss 挑战、掉落预览。',
    ranking: '全服排行与个人排名。',
    history: '日志、任务、战报与世界线记录。',
    underworld: '冥界与复活入口。',
    chat: '',
    trade: '商店、市场、竞拍、交易记录。',
    skills: '浏览已配置技能的条件、消耗、装备与效果。',
    faction: '背后星阵营与阵营排行。',
    pk: '挑战、赛程、战绩与奖励。',
    quests: '试炼任务与奖励。',
    friends: '同伴、私信和系统信件。',
    titles: '档案、图鉴、称号与加成。',
    inventory: '背包、使用、出售、上架。',
    synthesis: '物品合成、配方与制作。',
    equipment: '装备、打造、修理、套装。',
    rest: '安全区恢复、制造与化身状态。',
    party: '队伍、招募与协作状态。',
    support: '支援信标与悬赏记录。',
    worldBoss: '灾厄挑战、排行、奖励。',
    guide: '版本、更新日志和玩法说明。',
    notice: '历史版本更新记录与变更日志。',
    feedback: '提交问题、建议或体验反馈。',
    system: '在线状态、版本与退出。',
  };
  return subtitles[panel] || '';
}

function subMenusFor(panel: string) {
  const menus: Record<string, string[]> = {
    explore: ['场景锚点', '世界线坐标', '进入记录'],
    hiddenScene: ['隐藏剧情', '灾厄房间', '奖励预览', '队伍信标'],
    history: ['全部记录', '收益记录', '战斗记录', '场景记录', '交易记录'],
    underworld: ['死亡名单', '复活协议', '援救委托', '异常记录'],
    chat: ['星流', '阵营', '队伍'],
    trade: ['商品清单', '求购信标', '我的摊位', '关注清单', '成交记录', '竞价频道', '寄售中', '出价记录', '补给入口', '秘匣入口'],
    skills: ['技能图谱', '可学习技能', '已掌握技能', '技能加成'],
    faction: ['阵营概览', '星座设施'],
    pk: ['挑战列表', '模拟战', '化身榜单', '战报记录', '胜负预测', '战场奖励', '规则'],
    quests: ['每日委托', '每周委托', '主线任务', '世界线成就', '快速处理', '模拟场景'],
     friends: ['全部', '最近互动', '同伴列表', '待处理申请', '搜索同伴'],
    titles: ['档案检索', '材料档案', '补给档案', '装备档案', '技能档案'],
    inventory: ['全部物资', '武器', '防具', '消耗品', '材料'],
    synthesis: ['全部配方', '武器合成', '防具合成', '消耗品合成'],
    equipment: ['全部装备', '已穿戴', '需修理', '套装效果'],
    rest: ['恢复舱', '装备整备', '补给制作', '化身状态', '休整记录'],
    party: ['队伍关系', '申请加入', '招募成员', '关系解除'],
    support: ['支援入口', '复制信标', '邀请记录'],
    worldBoss: ['灾厄档案', '当前战场', '伤害排行', '奖励领取'],
    guide: ['说明检索', '基础规则', '战斗规则', '交易规则', '社交流程'],
    notice: ['更新公告', '游戏公告'],
    feedback: ['我的反馈', '提交反馈', '反馈广场', '删除记录'],
    system: ['字体设置', '主题设置', '系统操作'],
  };
  return menus[panel] || [];
}

function selectSubMenu(label: string) {
  activeSubMenus.value = { ...activeSubMenus.value, [props.panel]: label };
}

function selectTradeTab(tab: TradeWorkbenchTab) {
  tradeTab.value = tab;
  tradeSearch.value = '';
  tradeSort.value = tab === 'history' || tab === 'public' ? 'newest' : tradeSort.value;
}

function selectTransmitTab(tab: TransmitWorkbenchTab) {
  transmitTab.value = tab;
  transmitSearch.value = '';
  transmitType.value = '';
  transmitRarity.value = '';
  transmitRealm.value = '';
}

function transmitName(entry: ApiRecord): string {
  return pickText(entry, ['skillName', 'name', 'skillKey', 'skill_key'], '未命名技能');
}

function transmitTypeLabel(value: unknown): string {
  return abilityTypeLabel(value);
}

function transmitRarityClass(entry: ApiRecord): string {
  return equipmentRarityClass(entry);
}

function transmitActions(entry: ApiRecord): PanelAction[] {
  const type = pick(entry, ['type', 'skillType', 'skill_type']);
  if (!supportsStandardSkillLifecycle(type)) return [];

  const key = pickText(entry, ['skillKey', 'skill_key', 'key'], '');
  if (!key) return [];
  if (transmitTab.value === 'my') return ownedSkillActions(entry);
  if (transmitTab.value !== 'browse') return [];
  if (transmitOwnedKeys.value.has(key)) return ownedSkillActions(entry);
  if (transmitUnlockableKeys.value.has(key)) return skillActions(entry);
  return [];
}

function selectPvpTab(tab: PvpWorkbenchTab) {
  pvpTab.value = tab;
  pvpSearch.value = '';
}

function pvpModeLabel(mode: unknown): string {
  const value = String(mode || '').toLowerCase();
  if (value.includes('death') || value.includes('生死')) return '生死对战';
  return '练习对战';
}

function pvpChallengeActions(entry: ApiRecord): PanelAction[] {
  const defenderId = Number(pick(entry, ['id', 'playerId', 'player_id', 'defenderId']));
  return [{
    label: pvpMode.value === 'deathmatch' ? '发起生死挑战' : '发起练习对战',
    disabled: !defenderId,
    onClick: () => runAction('发起挑战', `${pvpModeLabel(pvpMode.value)} ${pickText(entry, ['playerName', 'player_name', 'name'], '该玩家')}？`, () => pkApi.challenge(playerId.value, defenderId, pvpMode.value)),
  }];
}

function setRankingScope(scope: (typeof rankingScopes)[number]) {
  rankingScope.value = scope;
}

function setFriendFilter(filter: (typeof friendFilters)[number]) {
  friendFilter.value = filter;
}

function formatPanelTime(value: unknown): string {
  const raw = String(value || '');
  if (!raw) return '';
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return raw.replace('T', ' ').slice(0, 19);
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function chatInitial(entry: ApiRecord): string {
  return pickText(entry, ['senderName', 'sender_name'], '灵').trim().slice(0, 1) || '灵';
}

function chatMessageIsSelf(entry: ApiRecord): boolean {
  return Number(pick(entry, ['senderPlayerId', 'sender_player_id'])) === playerId.value;
}

function chatChannelLabel(value: unknown): string {
  const labels: Record<string, string> = { world: '世界', faction: '阵营', team: '队伍', system: '系统' };
  return labels[String(value || '').toLowerCase()] || String(value || '星流');
}

function historyTypeLabel(type: unknown): string {
  const labels: Record<string, string> = {
    info: '信息', exploration: '探索', explore: '探索', story: '因果', adventure: '奇遇',
    battle: '战斗', combat: '战斗', pk: '挑战', trade: '交易', reward: '收益', income: '收益',
    quest: '试炼', party: '队伍', admin: '管理', system: '系统', death: '陨落',
  };
  return labels[String(type || '').toLowerCase()] || String(type || '记录');
}

function historyTone(type: unknown): string {
  const value = String(type || '').toLowerCase();
  if (['battle', 'combat', 'pk', 'death'].includes(value)) return 'danger';
  if (['reward', 'income', 'trade'].includes(value)) return 'jade';
  if (['explore', 'exploration', 'adventure', 'story'].includes(value)) return 'purple';
  if (['admin', 'system'].includes(value)) return 'muted';
  return 'gold';
}

function questProgress(entry: ApiRecord): { current: number; target: number; pct: number } {
  const current = Math.max(0, pickNumber(entry, ['progress', 'currentValue'], 0));
  const target = Math.max(0, pickNumber(entry, ['targetValue', 'target'], 0));
  const status = pickText(entry, ['status'], '').toLowerCase();
  const pct = target > 0 ? Math.min(100, Math.round((current / target) * 100)) : (['completed', 'claimed'].includes(status) ? 100 : 0);
  return { current, target, pct };
}

function questStatusLabel(status: unknown): string {
  const labels: Record<string, string> = { active: '进行中', in_progress: '进行中', completed: '可领取', claimed: '已领取', expired: '已过期', locked: '未解锁' };
  return labels[String(status || '').toLowerCase()] || String(status || '进行中');
}

function questStatusClass(status: unknown): string {
  const value = String(status || '').toLowerCase();
  if (value === 'completed') return 'is-completed';
  if (value === 'claimed') return 'is-claimed';
  if (value === 'expired' || value === 'locked') return 'is-muted';
  return 'is-active';
}

function questTypeLabel(entry: ApiRecord): string {
  const type = pickText(entry, ['questType', 'quest_type', 'category'], '试炼').toLowerCase();
  if (type.includes('daily') || type.includes('每日')) return '每日';
  if (type.includes('weekly') || type.includes('每周')) return '每周';
  if (type.includes('achievement') || type.includes('成就')) return '成就';
  if (type.includes('main') || type.includes('story') || type.includes('主线')) return '主线';
  return '试炼';
}

function questRewardText(entry: ApiRecord): string {
  const rewards = asRecord(entry.rewards);
  return Object.keys(rewards).length ? formatSummaryValue(rewards) : '完成后获得奖励';
}

function partyStatusLabel(value: unknown): string {
  const labels: Record<string, string> = { active: '招募中', ready: '已准备', battling: '讨伐中', disbanded: '已解散' };
  return labels[String(value || '').toLowerCase()] || String(value || '待命');
}

function dataPanelFor(panel: string) {
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

async function loadPanel(panel: string, id: number): Promise<ApiRecord> {
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
        logs: gameStore.recentLogs,
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
      return { recent: await chatApi.getRecentMessages(chatChannel.value) };
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
        search: friendKeyword.value.trim().length >= 2 ? await friendApi.search(friendKeyword.value.trim(), id) : [],
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

function buildBlocks(panel: string, data: ApiRecord): PanelBlock[] {
  switch (dataPanelFor(panel)) {
    case 'explore':
      if (activeSubMenu.value === '世界线坐标') {
        const historyEntries = asArray(data.worldlineHistory).map((entry) => ({
          ...entry,
          causeDisplay: worldlineCauseDisplay(entry),
        }));
        const historyBlock = entriesBlock('最近变更', historyEntries, ['stateName', 'stateKey'], [
          ['变量', 'valueKey'], ['变化', 'changeAmount'], ['变化前', 'beforeValue'],
          ['变化后', 'afterValue'], ['原因', 'causeDisplay'], ['时间', 'createdAt'],
        ], '暂无世界线变更记录');
        historyBlock.description = WORLDLINE_HISTORY_DESCRIPTION;
        return [
          summaryBlock('世界线摘要', asRecord(data.worldlineSummary)),
          historyBlock,
        ];
      }
      return buildExploreBlocks(data);
    case 'hiddenScene':
      return [
        summaryBlock('隐藏场景状态', asRecord(data.summary)),
        entriesBlock('当前灾厄', [asRecord(data.active)], ['name', 'bossName', 'boss_name', 'bossNo'], [['HP', 'hp'], ['状态', 'status']], '当前没有活跃灾厄'),
        entriesBlock('我的参与', asArray(data.participation), ['bossName', 'bossNo'], [['伤害', 'damage'], ['奖励', 'rewardStatus']], '暂无参与记录'),
      ];
    case 'ranking':
      return [
        summaryBlock('我的排行', asRecord(data.myRank)),
        entriesBlock('化身排行', asArray(data.rankings), ['player_name', 'playerName', 'name'], [['排名', 'rank'], ['战力', 'power'], ['等级', 'level']], '暂无排行'),
        entriesBlock('成长阶排行', asArray(data.avatarRankings), ['player_name', 'playerName', 'name'], [['排名', 'avatarRank'], ['威望', 'prestige']], '暂无成长阶排行'),
      ];
    case 'history': {
      const histFilter = activeSubMenu.value;
      let logs = asArray(data.logs);
      if (histFilter === '收益记录') logs = logs.filter(e => ['income', 'reward', '收益'].includes(pickText(e, ['type'], '')));
      else if (histFilter === '战斗记录') logs = logs.filter(e => ['battle', 'combat', 'pk', '战斗'].includes(pickText(e, ['type'], '')));
      else if (histFilter === '场景记录') logs = logs.filter(e => ['explore', 'scene', '场景'].includes(pickText(e, ['type'], '')));
      else if (histFilter === '交易记录') logs = logs.filter(e => ['trade', '交易'].includes(pickText(e, ['type'], '')));
      const storyLogEntries = buildStoryRecapEntries(data.storyLog, data.npcEncounters).map((story) => ({
        id: story.id,
        title: story.title,
        subtitle: `${story.source} · 选择「${story.choice}」${story.consequence ? ` · ${story.consequence}` : ''}`,
        meta: [story.location, story.time].filter(Boolean),
        actions: [] as PanelAction[],
      }));
      return [
        entriesBlock('最近日志', logs, ['message'], [['类型', 'type'], ['时间', 'createdAt']], histFilter !== '全部记录' ? `没有${histFilter}` : '暂无日志'),
        ...(storyLogEntries.length ? [{ title: '故事记录', emptyText: '暂无故事记录', entries: storyLogEntries, stats: undefined as undefined }] as PanelBlock[] : []),
        summaryBlock('任务摘要', asRecord(data.summary)),
        entriesBlock('任务记录', asArray(data.quests), ['questName', 'name', 'title', 'questKey'], [['状态', 'status'], ['进度', 'progress']], '暂无任务'),
        entriesBlock('已达成结局', asArray(data.endings), ['name'], [['优先级', 'priority']], '尚未抵达个人终局'),
      ];
    }
    case 'underworld':
      return [
        entriesBlock(
          '死亡名单',
          asArray(data.deadList),
          ['player_name', 'playerName', 'name'],
          [['等级', 'level'], ['背后星', 'constellation']],
          '冥界暂时没有等待复活的化身',
          (entry) => underworldActions(entry),
        ),
      ];
    case 'chat':
      return [entriesBlock('最近消息', asArray(data.recent), ['content', 'message'], [['频道', 'channel'], ['发言者', 'senderName']], '暂无频道消息')];
    case 'trade':
      return [
        summaryBlock('市场摘要', asRecord(data.summary)),
        entriesBlock('市场挂单', filterTradeEntries(asArray(data.listings)), ['itemName', 'equipmentName', 'name', 'listingNo'], [['价格', 'unitPrice'], ['数量', 'quantity'], ['卖家', 'sellerName']], '暂无挂单', (entry) => tradeBuyActions(entry)),
        entriesBlock('我的挂单', filterTradeEntries(asArray(data.my)), ['itemName', 'equipmentName', 'name', 'listingNo'], [['状态', 'status'], ['价格', 'unitPrice']], '暂无我的挂单', (entry) => tradeCancelActions(entry)),
        entriesBlock('交易记录', filterTradeEntries(asArray(data.records)), ['itemName', 'equipmentName', 'listingNo'], [['价格', 'unitPrice'], ['时间', 'createdAt']], '暂无交易记录'),
      ];
    case 'skills': {
      const section = activeSubMenu.value;
      if (section === '技能加成') return [summaryBlock('当前加成', asRecord(data.bonus))];
      if (section === '已掌握技能') return [entriesBlock('已掌握', filterSkillEntries(asArray(data.owned)), ['skillName', 'name', 'skillKey'], [['等级', 'level'], ['类型', 'type'], ['效果', 'effects']], '暂无已掌握技能', (entry) => ownedSkillActions(entry))];
      if (section === '可学习技能') return [entriesBlock('当前可学习', filterSkillEntries(asArray(data.unlockable)), ['skillName', 'name', 'skillKey'], [['消耗', 'cost'], ['条件', 'unlockConditions'], ['效果', 'effects']], '暂无当前可学习技能', (entry) => skillActions(entry))];
      return [entriesBlock('技能图谱', filterSkillEntries(asArray(data.all)), ['skillName', 'name', 'skillKey'], [['类型', 'type'], ['稀有度', 'rarity'], ['效果', 'effects'], ['学习消耗', 'cost'], ['解锁条件', 'unlockConditions']], '暂无技能定义')];
    }
    case 'faction': {
      const section = activeSubMenu.value;
      const myFaction = asRecord(data.my);
      const hasFaction = myFaction.factionKey || myFaction.faction_key;
      const buff = asRecord(data.buff);
      const blocks: PanelBlock[] = [];

      if (section === '阵营概览') {
        if (hasFaction) {
          blocks.push(summaryBlock('阵营加成', buff));
          blocks.push(entriesBlock('我的阵营', [myFaction], ['factionName', 'name', 'factionKey'], [['角色', 'role'], ['声望', 'reputation'], ['贡献', 'contributionTotal']], '尚未加入阵营'));
        } else {
          blocks.push(entriesBlock('我的阵营', [], ['factionName', 'name', 'factionKey'], [], '尚未加入阵营'));
        }
        blocks.push(entriesBlock('全星座阵营', asArray(data.factions), ['name', 'factionName', 'factionKey'], [['成员', 'memberCount'], ['等级', 'level'], ['阵营加成', 'buffs'], ['理念', 'ideology']], '暂无阵营', (entry) => factionActions(entry)));
        blocks.push(entriesBlock('阵营排行', asArray(data.rankings), ['factionName', 'name', 'factionKey'], [['排名', 'rank'], ['等级', 'level'], ['成员', 'memberCount'], ['总贡献', 'totalContribution']], '暂无排行'));
      }

      if (section === '星座设施') {
        if (hasFaction) {
          blocks.push(entriesBlock('阵营技能', asArray(data.skills), ['name', 'skillName', 'skillKey'], [['描述', 'description'], ['解锁等级', 'unlockLevel'], ['效果', 'effects'], ['消耗', 'cost'], ['已学习', 'learned'], ['可学习', 'canLearn']], '该阵营暂无技能', (entry) => factionSkillActions(entry)));
          blocks.push(entriesBlock('阵营战记录', asArray(data.wars), ['warNo'], [['攻击方', 'attackerFactionKey'], ['防御方', 'defenderFactionKey'], ['状态', 'status'], ['攻击方分数', 'attackerScore'], ['防御方分数', 'defenderScore']], '暂无阵营战记录'));
        } else {
          blocks.push({ title: '星座设施', description: '加入阵营后解锁阵营技能与阵营战', emptyText: '请先加入阵营' });
        }
      }

      return blocks;
    }
    case 'pk':
      return [
        entriesBlock('可挑战化身', asArray(data.opponents), ['playerName', 'name'], [['等级', 'level'], ['战力', 'power'], ['评分', 'rating']], '暂无可挑战对象', (entry) => pkActions(entry)),
        entriesBlock('战斗记录', asArray(data.records), ['opponentName', 'defenderName', 'attackerName', 'result'], [['模式', 'mode'], ['胜者', 'winnerName'], ['回合', 'totalRounds'], ['时间', 'createdAt']], '暂无战斗记录'),
      ];
    case 'quests':
      return [
        summaryBlock('试炼摘要', asRecord(data.summary)),
        entriesBlock('当前任务', asArray(data.quests), ['questName', 'name', 'title', 'questKey'], [['状态', 'status'], ['进度', 'progress'], ['周期', 'cycleKey']], '暂无任务', (entry) => questActions(entry)),
        entriesBlock('任务定义', asArray(data.definitions), ['name', 'questName', 'title', 'questKey'], [['类型', 'type'], ['目标', 'target']], '暂无任务定义'),
      ];
    case 'friends': {
      const section = activeSubMenu.value;
      const blocks: PanelBlock[] = [];
      if (section === '全部' || section === '') blocks.push(summaryBlock('同伴摘要', asRecord(data.summary)));
      if (section === '全部' || section === '' || section === '搜索同伴') {
        blocks.push(entriesBlock('搜索结果', asArray(data.search), ['playerName', 'name'], [['等级', 'level'], ['ID', 'id']], friendKeyword.value.trim().length >= 2 ? '没有搜索结果' : '输入至少 2 个字搜索同伴', (entry) => friendRequestActions(entry)));
      }
      if (section === '全部' || section === '' || section === '待处理申请') {
        blocks.push(entriesBlock('待处理申请', asArray(data.requests), ['fromPlayerName', 'playerName', 'name'], [['状态', 'status'], ['时间', 'createdAt']], '暂无待处理申请', (entry) => friendRequestHandleActions(entry)));
      }
      if (section === '全部' || section === '' || section === '最近互动') {
        blocks.push(entriesBlock('最近互动', asArray(data.recent), ['playerName', 'name'], [['互动类型', 'interactionType'], ['状态', 'status'], ['在线', 'isOnline'], ['时间', 'interactedAt']], '暂无最近互动记录'));
      }
      if (section === '全部' || section === '' || section === '同伴列表') {
        blocks.push(entriesBlock('同伴列表', asArray(data.friends), ['friendName', 'playerName', 'name'], [['等级', 'level'], ['成长阶', 'avatarRank']], '暂无同伴', (entry) => friendRemoveActions(entry)));
      }
      return blocks;
    }
    case 'titles':
      return [
        summaryBlock('当前称号', asRecord(data.equipped)),
        summaryBlock('称号效果', asRecord(data.effects)),
        entriesBlock('已拥有称号', asArray(data.mine), ['titleName', 'name', 'titleKey'], [['稀有度', 'rarity'], ['状态', 'status']], '暂无称号', (entry) => titleActions(entry)),
        entriesBlock('档案库', asArray(data.all), ['titleName', 'name', 'titleKey'], [['稀有度', 'rarity'], ['来源', 'source']], '暂无称号定义'),
      ];
    case 'inventory': {
      const filterType = activeSubMenu.value;
      const rawInv = asArray(data.inventory);
      let filteredInv = filterType === '全部物资' ? rawInv : rawInv.filter(e => {
        const t = pickText(e, ['type', 'itemType'], '');
        if (filterType === '武器') return t === 'weapon' || t === '武器';
        if (filterType === '防具') return t === 'armor' || t === '防具';
        if (filterType === '消耗品') return t === 'consumable' || t === '消耗品';
        if (filterType === '材料') return t === 'material' || t === '材料';
        return true;
      });
      const keyword = inventorySearch.value.trim().toLowerCase();
      if (keyword) {
        filteredInv = filteredInv.filter((entry) => {
          const searchable = [
            inventoryEntryName(entry),
            pickText(entry, ['itemKey', 'item_key'], '').toLowerCase(),
            pickText(entry, ['description', 'desc', 'effects'], '').toLowerCase(),
          ].join(' ');
          return searchable.includes(keyword);
        });
      }
      filteredInv = filteredInv.filter(inventoryRarityMatches);
      if (inventorySort.value === 'name') {
        filteredInv = [...filteredInv].sort((a, b) => inventoryEntryName(a).localeCompare(inventoryEntryName(b), 'zh-CN'));
      } else if (inventorySort.value === 'quantity') {
        filteredInv = [...filteredInv].sort((a, b) => pickNumber(b, ['quantity', 'count'], 0) - pickNumber(a, ['quantity', 'count'], 0));
      }
      return [
        entriesBlock('背包物品', filteredInv, ['itemName', 'name', 'itemKey'], [['数量', 'quantity'], ['类型', 'type'], ['稀有度', 'rarity']], filterType !== '全部物资' ? `没有${filterType}类型物品` : '背包为空', (entry) => inventoryActions(entry)),
      ];
    }
    case 'synthesis': {
      const filterType = activeSubMenu.value;
      const rawRecipes = asArray(data.recipes);
      const filteredRecipes = filterType === '全部配方' ? rawRecipes : rawRecipes.filter(e => {
        const t = pickText(e, ['type', 'recipeType', 'resultType'], '');
        if (filterType === '武器合成') return t === 'weapon' || t === '武器';
        if (filterType === '防具合成') return t === 'armor' || t === '防具';
        if (filterType === '消耗品合成') return t === 'consumable' || t === '消耗品';
        return true;
      });
      return [
        entriesBlock('合成配方', filteredRecipes, ['name', 'recipeKey'], [['结果', 'resultItemName'], ['材料', 'ingredientSummary'], ['星币', 'costCoins'], ['类型', 'type'], ['效果', 'resultEffects']], filterType !== '全部配方' ? `没有${filterType}配方` : '暂无合成配方', (entry) => synthesisActions(entry)),
      ];
    }
    case 'equipment': {
      const eqFilter = activeSubMenu.value;
      if (eqFilter === '套装效果') {
        const setBonuses = asRecord(data.setBonuses);
        return [
          summaryBlock('套装效果合计（配置值，尚未接入结算）', asRecord(setBonuses.bonuses)),
          entriesBlock('已满足套装件数门槛', asArray(setBonuses.activeSets), ['name', 'setKey'], [['已穿戴', 'equippedCount'], ['配置效果（尚未接入结算）', 'bonuses']], '当前没有满足件数门槛的套装'),
          entriesBlock('套装配置（效果暂未接入结算）', asArray(data.sets), ['name', 'setKey'], [['组成', 'pieceCount'], ['效果门槛', 'bonuses']], '暂无套装定义'),
        ];
      }
      const equippedEntries = Object.values(asRecord(data.equipped)).map(asRecord);
      let allEquip = asArray(data.equipment);
      let filteredEquipped = equippedEntries;
      if (eqFilter === '已穿戴') {
        allEquip = [];
      } else if (eqFilter === '需修理') {
        const needsRepair = (e: ApiRecord) => {
          const dur = pickText(e, ['durability'], '');
          const m = dur.match(/(\d+)\/(\d+)/);
          return m ? (Number(m[1]) / Number(m[2])) < 0.7 : false;
        };
        allEquip = allEquip.filter(needsRepair);
        filteredEquipped = equippedEntries.filter(needsRepair);
      }
      return [
        summaryBlock('装备加成', asRecord(data.bonus)),
        entriesBlock('当前穿戴', filteredEquipped, ['equipmentName', 'name', 'equipmentKey'], [['槽位', 'slot'], ['耐久', 'durability'], ['特效', 'specialEffects'], ['强化', 'enhancement_bonus_percent']], eqFilter === '已穿戴' ? '暂无穿戴装备' : '没有需修理的穿戴装备', (entry) => equippedActions(entry)),
        entriesBlock('装备列表', allEquip, ['equipmentName', 'name', 'equipmentKey'], [['槽位', 'slot'], ['耐久', 'durability'], ['品质', 'rarity'], ['属性', 'stats'], ['特效', 'specialEffects'], ['强化', 'enhancement_bonus_percent']], eqFilter === '已穿戴' ? '切换筛选查看装备列表' : '暂无装备', (entry) => equipmentActions(entry)),
      ];
    }
    case 'rest':
      return [restBlock(asRecord(data.rest))];
    case 'party':
      return [
        summaryBlock('我的队伍摘要', asRecord(data.summary)),
        entriesBlock('我的队伍', [asRecord(data.my)], ['name', 'partyName', 'partyNo'], [['成员', 'memberCount'], ['状态', 'status']], '尚未加入队伍', () => partyLeaveActions()),
        entriesBlock('队伍成员', asArray(asRecord(data.my).members), ['playerName', 'playerId'], [['身份', 'role'], ['在线', 'online'], ['准备', 'ready'], ['等级', 'level']], '尚未加入队伍'),
        entriesBlock('活跃队伍', asArray(data.active), ['name', 'partyName', 'partyNo'], [['成员', 'memberCount'], ['状态', 'status']], '暂无活跃队伍', (entry) => partyJoinActions(entry)),
      ];
    case 'support':
      return [
        entriesBlock('支援悬赏', asArray(data.pending), ['title', 'name', 'bountyNo'], [['奖励', 'reward'], ['状态', 'status']], '暂无支援悬赏', (entry) => bountyAcceptActions(entry)),
        entriesBlock('我的支援', asArray(data.mine), ['title', 'name', 'bountyNo'], [['状态', 'status'], ['时间', 'createdAt']], '暂无我的支援', (entry) => bountyCancelActions(entry)),
      ];
    case 'worldBoss': {
      const active = asRecord(data.active);
      const participation = asArray(data.participation);
      const history = asArray(data.history);
      if (activeSubMenu.value === '灾厄档案') {
        return [
          entriesBlock('灾厄档案', history, ['name', 'bossName', 'bossNo'], [
            ['状态', 'status'], ['等级', 'level'], ['参与人数', 'participantCount'],
            ['总伤害', 'totalDamage'], ['首位化身', 'topPlayerName'], ['首位伤害', 'topDamage'],
          ], '暂无已结算灾厄记录'),
        ];
      }
      if (activeSubMenu.value === '伤害排行') {
        return [
          summaryBlock('灾厄摘要', asRecord(data.summary)),
          entriesBlock('伤害排行', asArray(data.rankings), ['player_name', 'playerName', 'name'], [['排名', 'rank'], ['伤害', 'damage'], ['攻击次数', 'attackCount']], '暂无伤害排行'),
        ];
      }
      if (activeSubMenu.value === '奖励领取') {
        const claimable = participation.filter((entry) => entry.canClaimReward === true || pickText(entry, ['canClaimReward'], '') === 'true' || pickNumber(entry, ['canClaimReward'], 0) === 1);
        const claimedOrPending = participation.filter((entry) => !claimable.includes(entry));
        return [
          entriesBlock('待领取奖励', claimable, ['bossName', 'name', 'bossNo'], [['状态', 'bossStatus'], ['伤害', 'damage'], ['排名', 'rankNo']], '暂无可领取奖励', (entry) => worldBossParticipationActions(entry)),
          entriesBlock('我的参与记录', claimedOrPending, ['bossName', 'name', 'bossNo'], [['状态', 'bossStatus'], ['伤害', 'damage'], ['排名', 'rankNo'], ['奖励', 'rewardClaimed']], '暂无其他参与记录'),
        ];
      }
      return [
        entriesBlock('当前灾厄', [active], ['name', 'bossName', 'boss_name', 'bossNo'], [['HP', 'hp'], ['状态', 'status']], '当前没有活跃灾厄', () => worldBossActions(active)),
        summaryBlock('灾厄摘要', asRecord(data.summary)),
        entriesBlock('我的参与', participation.slice(0, 5), ['bossName', 'name', 'bossNo'], [['状态', 'bossStatus'], ['伤害', 'damage'], ['排名', 'rankNo'], ['奖励', 'rewardClaimed']], '暂无参与记录'),
      ];
    }
    case 'guide': {
      const version = asRecord(data.version);
      const filteredVersion: ApiRecord = {};
      for (const [k, v] of Object.entries(version)) {
        if (k !== 'springBoot' && k !== 'spring_boot') filteredVersion[k] = v as unknown;
      }
      const memories = asArray(data.memories);
      const memoryMap: Array<{ id: string; title: string; subtitle: string; meta: string[]; actions: PanelAction[] }> = [];
      for (const mem of memories) {
        const itemKey = pickText(mem, ['itemKey', 'item_key'], '');
        const text = pickText(mem, ['memoryText', 'memory_text', 'text'], '');
        const itemName = pickText(mem, ['itemName', 'item_name', 'name'], itemKey);
        if (itemKey && text) memoryMap.push({ id: `memory-${itemKey}`, title: itemName, subtitle: text, meta: [], actions: [] });
      }
      return [
        summaryBlock('版本信息', filteredVersion),
        ...(memoryMap.length ? [{ title: '物品记忆', emptyText: '暂无物品记忆', entries: memoryMap, stats: undefined as undefined }] as PanelBlock[] : []),
      ];
    }
    case 'notice': {
      const allBroadcasts = asArray(data.broadcasts);
      const section = activeSubMenu.value;
      const filtered = section === '更新公告'
        ? allBroadcasts.filter(b => pickText(b, ['type', 'event_type'], '') === 'system_notice')
        : allBroadcasts.filter(b => pickText(b, ['type', 'event_type'], '') !== 'system_notice');
      const label = section === '更新公告' ? '更新公告' : '游戏公告';
      return [
        entriesBlock(label, filtered, ['title'], [['描述', 'description'], ['类型', 'type'], ['状态', 'status'], ['结束', 'endAt']], '暂无公告'),
      ];
    }
    case 'feedback':
      return [];
    case 'constellation': {
      const NEBULA_NAMES: Record<string, string> = {
        nebula_eden: '伊甸星云',
        nebula_vagrant: '流浪者星云',
        nebula_abyss: '深渊观测所',
        nebula_starstream: '星流档案馆',
      };
      const allConstellations = asArray(data.constellations);
      const grouped: Record<string, ApiRecord[]> = {};
      for (const c of allConstellations) {
        const nk = pickText(c, ['nebulaKey', 'nebula_key'], '');
        if (!grouped[nk]) grouped[nk] = [];
        grouped[nk].push(c);
      }
      const blocks: PanelBlock[] = [];
      for (const [nk, list] of Object.entries(grouped)) {
        const label = NEBULA_NAMES[nk] || nk;
        const block = entriesBlock(label, list, ['name', 'title', 'key'], [['加成', 'effects']], '', (entry) => constellationActions(entry));
        blocks.push(block);
      }
      if (blocks.length > 0) {
        blocks[0].description = '每次切换消耗200故事碎片';
      }
      return blocks;
    }
    case 'system':
      return [
        summaryBlock('版本', asRecord(data.version)),
        summaryBlock('在线摘要', asRecord(data.onlineSummary)),
        entriesBlock('在线化身', asArray(data.onlinePlayers), ['playerName', 'name'], [['位置', 'location'], ['状态', 'status']], '暂无在线列表'),
      ];
    default:
      return [];
  }
}

function summaryBlock(title: string, record: ApiRecord): PanelBlock {
  const stats = Object.entries(record)
    .filter(([, value]) => value !== undefined && value !== null)
    .slice(0, 8)
    .map(([label, value]) => ({ label: LABEL_MAP[label] || label, value: formatSummaryValue(value) }));
  return { title, stats, emptyText: stats.length ? undefined : '暂无摘要' };
}

function entriesBlock(
  title: string,
  entries: ApiRecord[],
  titleKeys: string[],
  metaKeys: Array<[string, string]>,
  emptyText: string,
  actions?: (entry: ApiRecord) => PanelAction[],
): PanelBlock {
  const seen = new Map<string, ApiRecord>();
  const deduped: ApiRecord[] = [];
  const idKeys = ['id', 'key', 'no', 'listingNo', 'partyNo', 'questKey', 'itemKey', 'equipmentKey', 'skillKey', 'titleKey'];
  for (const entry of entries) {
    const dedupKey = pickKey(entry, idKeys, `entry-${JSON.stringify(entry)}`);
    if (!seen.has(dedupKey)) {
      seen.set(dedupKey, entry);
      deduped.push(entry);
    }
  }
  return {
    title,
    emptyText,
    entries: deduped
      .filter((entry) => Object.keys(entry).length > 0)
      .slice(0, 20)
      .map((entry, index) => ({
        id: pickKey(entry, idKeys, `${title}-${index}`),
        title: pickText(entry, titleKeys, '未命名'),
        subtitle: pickText(entry, ['description', 'desc', 'message', 'content'], ''),
        meta: displayMeta(entry, metaKeys),
        actions: actions?.(entry) || [],
      })),
  };
}

function buildExploreBlocks(data: ApiRecord): PanelBlock[] {
  const locations = asArray(data.locations);
  return [
    {
      title: '可进入场景',
      emptyText: '暂无可探索地点',
      entries: locations.map((location, index) => {
        const locationKey = pickKey(location, ['location_key', 'locationKey', 'key'], `location-${index}`);
        return {
          id: locationKey,
          title: pickText(location, ['location_name', 'locationName', 'name'], '未命名场景'),
          subtitle: pickText(location, ['description'], ''),
          meta: displayMeta(location, [['危险', 'danger_level'], ['解锁', 'is_unlocked']]),
          actions: [
            {
              label: '切换',
              onClick: () => runAction('切换场景', `切换到 ${pickText(location, ['location_name', 'locationName', 'name'], locationKey)}？`, () => playerApi.switchLocation(playerId.value, locationKey)),
            },
          ],
        };
      }),
    },
  ];
}

function restBlock(rest: ApiRecord): PanelBlock {
  return {
    title: '安全区状态',
    stats: [
      { label: '休息中', value: pickText(rest, ['isResting'], '') === 'true' ? '休息中' : '未休息' },
      { label: '生命', value: `${formatCount(pickNumber(rest, ['hp']))}/${formatCount(pickNumber(rest, ['maxHp']))}` },
      { label: '行动力', value: `${formatCount(pickNumber(rest, ['stamina']))}/${formatCount(pickNumber(rest, ['maxStamina']))}` },
      { label: '上次恢复', value: pickText(rest, ['lastRecoveryAt'], '-') },
    ],
    entries: [
      {
        id: 'rest-actions',
        title: '恢复行动',
        subtitle: '进入安全区会持续恢复生命与行动力。',
        actions: [
          { label: '开始待机', onClick: () => runAction('开始待机', '开始在安全区待机恢复？', () => playerApi.startRest(playerId.value)) },
          { label: '结束行动', onClick: () => runAction('结束行动', '结束当前待机状态？', () => playerApi.stopRest(playerId.value)) },
        ],
      },
    ],
  };
}

function inventoryActions(entry: ApiRecord): PanelAction[] {
  const itemKey = pickText(entry, ['itemKey', 'item_key', 'key'], '');
  return [
    { label: '使用', disabled: !itemKey, onClick: () => runAction('使用物品', `使用 ${pickText(entry, ['itemName', 'name', 'itemKey'], itemKey)}？`, () => inventoryApi.useItem(playerId.value, itemKey)) },
    { label: '出售', danger: true, disabled: !itemKey, onClick: () => runAction('出售物品', `出售 1 个 ${pickText(entry, ['itemName', 'name', 'itemKey'], itemKey)}？`, () => inventoryApi.sellItem(playerId.value, itemKey, 1)) },
  ];
}

function synthesisActions(entry: ApiRecord): PanelAction[] {
  const recipeKey = pickText(entry, ['recipeKey', 'recipe_key'], '');
  return [{ label: '合成', disabled: !recipeKey, onClick: () => runAction('合成物品', `合成 ${pickText(entry, ['name', 'recipeKey'], recipeKey)}？`, () => inventoryApi.synthesize(playerId.value, recipeKey)) }];
}

function equipmentActions(entry: ApiRecord): PanelAction[] {
  const equipmentKey = pickText(entry, ['equipmentKey', 'equipment_key', 'key'], '');
  return [
    { label: '穿戴', disabled: !equipmentKey, onClick: () => runAction('穿戴装备', `穿戴 ${pickText(entry, ['equipmentName', 'name', 'equipmentKey'], equipmentKey)}？`, () => equipmentApi.equip(playerId.value, equipmentKey)) },
    { label: '修理', disabled: !equipmentKey, onClick: () => runAction('修理装备', `修理 ${pickText(entry, ['equipmentName', 'name', 'equipmentKey'], equipmentKey)}？`, () => equipmentApi.repair(playerId.value, equipmentKey)) },
    enhancementAction(entry, equipmentKey),
  ];
}

function equippedActions(entry: ApiRecord): PanelAction[] {
  const equipmentKey = pickText(entry, ['equipmentKey', 'equipment_key', 'key'], '');
  const slot = pickText(entry, ['slot'], '');
  return [
    { label: '卸下', danger: true, disabled: !equipmentKey && !slot, onClick: () => runAction('卸下装备', '卸下该装备？', () => equipmentApi.unequip(playerId.value, equipmentKey, slot)) },
    { label: '修理', disabled: !equipmentKey && !slot, onClick: () => runAction('修理装备', '修理该装备？', () => equipmentApi.repair(playerId.value, equipmentKey, slot)) },
    enhancementAction(entry, equipmentKey),
  ];
}

function enhancementAction(entry: ApiRecord, equipmentKey: string): PanelAction {
  const level = pickNumber(entry, ['enhancementLevel', 'enhancement_level'], 0);
  const maxLevel = pickNumber(entry, ['maxEnhancementLevel', 'max_enhancement_level'], 10);
  const cost = pickNumber(entry, ['nextEnhancementCost', 'next_enhancement_cost'], 0);
  const name = pickText(entry, ['equipmentName', 'name', 'equipmentKey'], equipmentKey);
  return {
    label: '强化',
    disabled: !equipmentKey || level >= maxLevel || cost <= 0,
    onClick: () => runAction(
      '强化装备',
      `将「${name}」强化至 +${level + 1}，基础属性提升 5%，成功率 100%，消耗 ${cost} 星币。确定强化？`,
      () => equipmentApi.enhance(playerId.value, equipmentKey),
    ),
  };
}

function ownedSkillActions(entry: ApiRecord): PanelAction[] {
  const skillKey = pickText(entry, ['skillKey', 'skill_key', 'key'], '');
  const isEquipped = pickText(entry, ['equipped'], '') === 'true' || (entry.equipped === 1 || entry.equipped === true);
  const actions: PanelAction[] = [];
  if (isEquipped) {
    actions.push({ label: '卸下', onClick: () => runAction('卸下技能', `卸下 ${pickText(entry, ['skillName', 'name', 'skillKey'], skillKey)}？`, () => skillApi.unequip(playerId.value, skillKey)) });
  } else {
    const guidance = skillSlotNotice.value;
    actions.push({
      label: guidance?.actionLabel || '装备',
      disabled: Boolean(guidance),
      onClick: () => runAction('装备技能', `装备 ${pickText(entry, ['skillName', 'name', 'skillKey'], skillKey)}？`, () => skillApi.equip(playerId.value, skillKey)),
    });
  }
  return actions;
}

function skillActions(entry: ApiRecord): PanelAction[] {
  const skillKey = pickText(entry, ['skillKey', 'skill_key', 'key'], '');
  const skillName = pickText(entry, ['skillName', 'name', 'skillKey'], skillKey);
  const costText = formatSummaryValue(entry.cost);
  const confirmMessage = `学习「${skillName}」？${costText ? `将消耗 ${costText}。` : ''}`;
  return [{ label: '学习', disabled: !skillKey, onClick: () => runAction('学习技能', confirmMessage, () => skillApi.unlock(playerId.value, skillKey)) }];
}

function factionActions(entry: ApiRecord): PanelAction[] {
  const factionKey = pickText(entry, ['factionKey', 'faction_key', 'key'], '');
  return [{ label: '加入', disabled: !factionKey, onClick: () => runAction('加入阵营', `加入 ${pickText(entry, ['name', 'factionName', 'factionKey'], factionKey)}？`, () => factionApi.join(playerId.value, factionKey)) }];
}

function sectFactionActions(entry: ApiRecord): PanelAction[] {
  const factionKey = pickText(entry, ['factionKey', 'faction_key', 'key'], '');
  const currentKey = pickText(sectMe.value, ['factionKey', 'faction_key'], '');
  const joined = truthyRecordValue(entry, ['joined']) || (Boolean(currentKey) && currentKey === factionKey);
  return joined ? [] : factionActions(entry);
}

function pkActions(entry: ApiRecord): PanelAction[] {
  const defenderId = Number(pick(entry, ['id', 'playerId', 'player_id', 'defenderId']));
  return [{ label: '挑战', disabled: !defenderId, onClick: () => runAction('发起挑战', `挑战 ${pickText(entry, ['playerName', 'name'], '该化身')}？`, () => pkApi.challenge(playerId.value, defenderId)) }];
}

function questActions(entry: ApiRecord): PanelAction[] {
  const questKey = pickText(entry, ['questKey', 'quest_key', 'key', 'id'], '');
  const cycleKey = pickText(entry, ['cycleKey', 'cycle_key'], '');
  return [{ label: '领取', disabled: !questKey, onClick: () => runAction('领取任务', `领取 ${pickText(entry, ['questName', 'name', 'questKey'], questKey)} 奖励？`, () => questApi.claim(playerId.value, questKey, cycleKey || undefined)) }];
}

function friendRequestActions(entry: ApiRecord): PanelAction[] {
  const targetId = Number(pick(entry, ['id', 'playerId', 'player_id', 'targetPlayerId']));
  return [{ label: '申请', disabled: !targetId, onClick: () => runAction('发送申请', `向 ${pickText(entry, ['playerName', 'name'], '该化身')} 发送同伴申请？`, () => friendApi.request(playerId.value, targetId)) }];
}

function friendRequestHandleActions(entry: ApiRecord): PanelAction[] {
  const requestId = Number(pick(entry, ['requestId', 'request_id', 'id']));
  return [
    { label: '接受', disabled: !requestId, onClick: () => runAction('接受申请', '接受该同伴申请？', () => friendApi.accept(playerId.value, requestId)) },
    { label: '拒绝', danger: true, disabled: !requestId, onClick: () => runAction('拒绝申请', '拒绝该同伴申请？', () => friendApi.reject(playerId.value, requestId)) },
  ];
}

function friendRemoveActions(entry: ApiRecord): PanelAction[] {
  const targetId = Number(pick(entry, ['friendPlayerId', 'friend_player_id', 'targetPlayerId', 'id']));
  return [
    { label: '赠送', disabled: !targetId, onClick: () => openGiftDialog(entry) },
    { label: '删除', danger: true, disabled: !targetId, onClick: () => runAction('删除同伴', `删除 ${pickText(entry, ['friendName', 'playerName', 'name'], '该同伴')}？`, () => friendApi.remove(playerId.value, targetId)) },
  ];
}

function openGiftDialog(entry: ApiRecord) {
  giftTarget.value = entry;
  giftItemKey.value = pickText(giftItems.value[0] || {}, ['itemKey', 'item_key'], '');
  giftQuantity.value = 1;
  showGiftDialog.value = true;
}

function closeGiftDialog() {
  showGiftDialog.value = false;
  giftTarget.value = null;
  giftItemKey.value = '';
  giftQuantity.value = 1;
}

async function submitGift() {
  const target = giftTarget.value;
  const targetId = Number(pick(target || {}, ['friendPlayerId', 'friend_player_id', 'targetPlayerId', 'playerId', 'id']));
  if (!targetId || !giftItemKey.value) {
    flashToast(giftItems.value.length ? '请选择要赠送的物品' : '背包中没有可赠送的物品', true);
    return;
  }
  const quantity = Math.max(1, Math.min(99, Number(giftQuantity.value) || 1));
  giftQuantity.value = quantity;
  await runAction(
    '赠送物品',
    `向 ${pickText(target || {}, ['friendName', 'playerName', 'name'], '该同伴')} 赠送 ${quantity} 个物品？`,
    () => friendApi.gift(playerId.value, targetId, giftItemKey.value, quantity),
  );
  if (!actionError.value) closeGiftDialog();
}

function titleActions(entry: ApiRecord): PanelAction[] {
  const titleKey = pickText(entry, ['titleKey', 'title_key', 'key'], '');
  return [{ label: '装备', disabled: !titleKey, onClick: () => runAction('装备称号', `装备称号 ${pickText(entry, ['titleName', 'name', 'titleKey'], titleKey)}？`, () => titleApi.equip(playerId.value, titleKey)) }];
}

function tradeBuyActions(entry: ApiRecord): PanelAction[] {
  const listingNo = pickText(entry, ['listingNo', 'listing_no', 'id'], '');
  return [{ label: '购买', disabled: !listingNo, onClick: () => runAction('购买挂单', `购买 ${pickText(entry, ['itemName', 'equipmentName', 'name', 'listingNo'], listingNo)}？`, () => tradeApi.buy(playerId.value, listingNo)) }];
}

function tradeCancelActions(entry: ApiRecord): PanelAction[] {
  const listingNo = pickText(entry, ['listingNo', 'listing_no', 'id'], '');
  const status = pickText(entry, ['status'], '');
  if (status === 'sold' || status === 'cancelled' || status === '已售出' || status === '已取消') return [];
  return [{ label: '取消', danger: true, disabled: !listingNo, onClick: () => runAction('取消挂单', `取消挂单 ${listingNo}？`, () => tradeApi.cancel(playerId.value, listingNo)) }];
}

function partyJoinActions(entry: ApiRecord): PanelAction[] {
  const partyNo = pickText(entry, ['partyNo', 'party_no', 'id'], '');
  const myPartyNo = pickText(asRecord(panelQuery.data.value?.my), ['partyNo', 'party_no'], '');
  if (partyNo && partyNo === myPartyNo) return [];
  return [{ label: '加入', disabled: !partyNo, onClick: () => runAction('加入队伍', `加入队伍 ${pickText(entry, ['name', 'partyName', 'partyNo'], partyNo)}？`, () => partyApi.join(playerId.value, partyNo)) }];
}

function partyLeaveActions(): PanelAction[] {
  const myParty = asRecord(panelQuery.data.value?.my);
  const partyNo = pickText(myParty, ['partyNo', 'party_no', 'id'], '');
  const currentMember = asArray(myParty.members).find((member) => pickNumber(member, ['playerId', 'player_id'], 0) === playerId.value);
  const ready = pick(currentMember, ['ready']) === true || pickNumber(currentMember, ['ready'], 0) === 1;
  return [
    { label: ready ? '取消准备' : '准备', disabled: !partyNo, onClick: () => runAction(ready ? '取消准备' : '准备', ready ? '取消本次队伍讨伐准备？' : '准备参加本次队伍讨伐？', () => partyApi.setReady(playerId.value, partyNo, !ready)) },
    { label: '开始战斗', disabled: !partyNo, onClick: () => runAction('开始讨伐', '开始队伍讨伐Boss战？', () => partyApi.startBattle(playerId.value, partyNo)) },
    { label: '创建队伍', onClick: () => runAction('创建队伍', '创建默认讨伐队伍？', () => partyApi.create(playerId.value, `${gameStore.playerName}的队伍`, '自动创建的星流队伍')) },
    { label: '离开队伍', danger: true, onClick: () => runAction('离开队伍', '离开当前队伍？', () => partyApi.leave(playerId.value)) },
  ];
}

function worldBossActions(entry: ApiRecord): PanelAction[] {
  const bossNo = pickText(entry, ['bossNo', 'boss_no'], '');
  const actions: PanelAction[] = [
    { label: '攻击', disabled: !bossNo, onClick: () => runAction('攻击灾厄', `攻击 ${pickText(entry, ['name', 'bossName', 'bossNo'], bossNo)}？`, () => worldBossApi.attack(playerId.value, bossNo)) },
  ];
  const canClaim = entry.canClaimReward === true || pickText(entry, ['canClaimReward'], '') === 'true' || pickNumber(entry, ['canClaimReward'], 0) === 1;
  if (canClaim) {
    actions.push({ label: '领奖', disabled: !bossNo, onClick: () => runAction('领取奖励', `领取 ${bossNo} 奖励？`, () => worldBossApi.claim(playerId.value, bossNo)) });
  }
  return actions;
}

function factionSkillActions(entry: ApiRecord): PanelAction[] {
  const skillKey = pickText(entry, ['skillKey', 'skill_key'], '');
  const learned = entry.learned === true || pickText(entry, ['learned'], '') === 'true' || pickNumber(entry, ['learned'], 0) === 1;
  const canLearn = entry.canLearn === true || pickText(entry, ['canLearn'], '') === 'true' || pickNumber(entry, ['canLearn'], 0) === 1;
  if (learned) return [];
  return [{
    label: '学习',
    disabled: !skillKey || !canLearn,
    onClick: () => runAction('学习阵营技能', `学习 ${pickText(entry, ['name', 'skillName', 'skillKey'], skillKey)}？`, () => skillApi.learnFactionSkill(playerId.value, skillKey)),
  }];
}

function worldBossParticipationActions(entry: ApiRecord): PanelAction[] {
  const bossNo = pickText(entry, ['bossNo', 'boss_no'], '');
  const canClaim = entry.canClaimReward === true || pickText(entry, ['canClaimReward'], '') === 'true' || pickNumber(entry, ['canClaimReward'], 0) === 1;
  if (!canClaim) return [];
  return [{ label: '领奖', disabled: !bossNo, onClick: () => runAction('领取奖励', `领取 ${pickText(entry, ['bossName', 'bossNo'], bossNo)} 奖励？`, () => worldBossApi.claim(playerId.value, bossNo)) }];
}

function underworldActions(entry: ApiRecord): PanelAction[] {
  const targetId = Number(pick(entry, ['id', 'playerId', 'player_id']));
  const isSelf = targetId === playerId.value;
  return [
    {
      label: isSelf ? '自我复活' : '援救',
      disabled: !targetId,
      onClick: () => runAction(isSelf ? '自我复活' : '援救化身', isSelf ? '消耗资源从冥界归来？' : `援救 ${pickText(entry, ['player_name', 'playerName', 'name'], '该化身')}？`, () =>
        isSelf ? playerApi.revive(playerId.value) : playerApi.peerRevive(playerId.value, targetId),
      ),
    },
  ];
}


function bountyAcceptActions(entry: ApiRecord): PanelAction[] {
  const bountyId = Number(pick(entry, ['id', 'bountyId', 'bounty_id']));
  return [{ label: '接取', disabled: !bountyId, onClick: () => runAction('接取悬赏', `接取 ${pickText(entry, ['title', 'name'], '该悬赏')}？`, () => bountyApi.accept(bountyId, playerId.value)) }];
}

function bountyCancelActions(_entry: ApiRecord): PanelAction[] {
  return [{ label: '取消', danger: true, onClick: () => runAction('取消悬赏', '取消你发布的悬赏？', () => bountyApi.cancel(playerId.value)) }];
}

async function sendChat() {
  const content = chatText.value.trim();
  if (!content) {
    flashToast('请输入消息', true);
    return;
  }
  await runAction('发送消息', `发送到${activeSubMenu.value || '星流'}频道？`, () => chatApi.sendMessage(playerId.value, content, chatChannel.value));
  chatText.value = '';
}

async function publishBounty() {
  if (!bountyMonsterKey.value) {
    flashToast('请选择怪物', true);
    return;
  }
  const data = asRecord(panelQuery.data.value as ApiRecord);
  const monsters = asArray(data.monsters);
  const selectedMonster = monsters.find((m: ApiRecord) => pickText(m, ['key', 'monsterKey', 'monster_key'], '') === bountyMonsterKey.value);
  const monsterName = selectedMonster ? pickText(selectedMonster, ['name', 'monsterName', 'monster_name'], bountyMonsterKey.value) : bountyMonsterKey.value;
  await runAction('发布悬赏', `发布支援悬赏 ${monsterName}？`, () =>
    bountyApi.publish({
      playerId: playerId.value,
      monsterKey: bountyMonsterKey.value,
      locationKey: bountyLocationKey.value || undefined,
      monsterName,
      sharePercent: bountySharePercent.value,
    }),
  );
  bountyMonsterKey.value = '';
  bountyLocationKey.value = '';
}

async function submitFeedback() {
  const content = feedbackText.value.trim();
  if (!content) {
    flashToast('请输入反馈内容', true);
    return;
  }
  await runAction('提交反馈', '提交反馈给鬼怪管理局？', () =>
    systemApi.submitFeedback({
      nickname: gameStore.playerName,
      type: feedbackType.value,
      content,
      page: props.panel,
      playerId: playerId.value || undefined,
    }),
  );
  feedbackText.value = '';
}

async function evaluateTitles() {
  await runAction('评估称号', '评估并解锁可获得称号？', () => titleApi.evaluate(playerId.value));
}

async function refreshQuests() {
  await runAction('刷新任务', '刷新当前任务列表？', () => questApi.refresh(playerId.value));
}

async function repairAll() {
  await runAction('全部修理', '修理全部可修理装备？', () => equipmentApi.repairAll(playerId.value));
}

async function rankUp() {
  await runAction('升阶', '尝试化身升阶？', () => rankingApi.rankUp(playerId.value));
}

async function prestigeAvatar() {
  await runAction('回归', '确认回归？成长阶将重置为F、等级重置为1；已掌握技能会保留，但当前装备技能会全部卸下（F阶暂无技能槽）。永久加成继续保留。', () => rankingApi.prestige(playerId.value));
}




const VALID_CONSTELLATION_KEYS = new Set([
  'demon_judge_of_fire', 'master_of_steel',
  'prisoner_of_golden_headband', 'abyssal_black_flame_dragon',
  'queen_of_darkest_spring', 'father_of_rich_night',
  'scribe_of_heaven', 'morning_star',
]);

function constellationActions(entry: ApiRecord): PanelAction[] {
  const key = pickText(entry, ['key', 'constellationKey', 'constellation_key'], '');
  const stats = gameStore.player?.stats || gameStore.player?.statsJson || {};
  const currentKey = String(pickText(stats as ApiRecord, ['constellation'], '') || '');
  const hasValidConstellation = VALID_CONSTELLATION_KEYS.has(currentKey);
  if (!hasValidConstellation) {
    return [{ label: '选择', disabled: !key, onClick: () => runAction('选择背后星', `选择 ${pickText(entry, ['name', 'title'], key)} 作为你的背后星？`, () => playerApi.selectConstellation(playerId.value, key)) }];
  }
  return [{ label: '更换', danger: true, disabled: !key, onClick: () => runAction('更换背后星', `更换为 ${pickText(entry, ['name', 'title'], key)}？消耗200故事碎片。`, () => playerApi.changeConstellation(playerId.value, key)) }];
}

async function checkForGhost() {
  const locationKey = pickText(gameStore.player || {}, ['currentLocationKey', 'currentLocation', 'current_location_key'], '');
  if (!locationKey) { flashToast('请先切换到一个可探索地点', true); return; }
  try {
    actionBusy.value = '检测残影';
    const result = await narrativeApi.checkGhost(playerId.value, locationKey);
    const start = startGhostDialogue(result);
    if (start.ghostKey) {
      ghostKey.value = start.ghostKey;
      ghostDialogueTree.value = start.dialogueTree;
      ghostNarrative.value = start.narrative;
      ghostNodeIndex.value = start.nodeIndex;
      ghostChoices.value = start.choices;
      showGhostDialog.value = true;
    } else {
      flashToast('当前位置未检测到NPC残影。', false);
    }
  } catch (e) { flashToast(e instanceof Error ? e.message : '检测失败', true); }
  finally { actionBusy.value = ''; }
}

async function selectGhostChoice(choiceIndex: number) {
  try {
    actionBusy.value = '残影对话';
    const result = await narrativeApi.processEncounter(playerId.value, ghostKey.value, ghostNodeIndex.value, choiceIndex);
    const next = advanceGhostDialogue(result, ghostDialogueTree.value);
    ghostNarrative.value = next.narrative;
    ghostChoices.value = next.choices;
    if (next.nodeIndex !== null) ghostNodeIndex.value = next.nodeIndex;
  } catch (e) { flashToast(e instanceof Error ? e.message : '对话失败', true); }
  finally { actionBusy.value = ''; }
}

function goAdmin() {
  window.open('http://localhost:5174', '_blank');
}

async function logout() {
  realtimeClient.disconnect();
  authStore.logout();
  await router.push('/login');
}

function formatActionSuccess(label: string, result: unknown): string {
  if (label === '使用物品' && result && typeof result === 'object') {
    return formatItemUseSuccess(result);
  }
  if (label === '领取任务' && result && typeof result === 'object') {
    return formatQuestClaimSuccess(result);
  }
  if (label === '强化装备' && result && typeof result === 'object') {
    return pickText(asRecord(result), ['message'], '装备强化完成');
  }
  if (label === '开始讨伐' && result && typeof result === 'object') {
    const data = asRecord(result);
    const message = pickText(data, ['message'], '队伍讨伐完成');
    const totalDamage = pickNumber(data, ['totalDamage', 'total_damage'], 0);
    const memberCount = pickNumber(data, ['attackedMemberCount', 'attacked_member_count'], 0);
    if (totalDamage > 0) {
      return `${message}（${memberCount}名成员，${formatCount(totalDamage)}点伤害）`;
    }
    return message;
  }
  return '操作已完成';
}

async function runAction(label: string, confirmMessage: string, action: () => Promise<unknown>) {
  actionBusy.value = label;
  clearTimeout(toastTimer);
  actionMessage.value = '';
  actionError.value = '';
  try {
    const result = await confirmAction(confirmMessage, action);
    if (result !== '已取消操作') {
      // 队伍讨伐会同时改变世界 Boss、排行和个人参与记录；即使用户仍停留在队伍面板，
      // 下次切换到灾厄面板时也必须拿到本次最新数据。
      if (label === '开始讨伐' || label === '攻击灾厄' || label === '领取奖励') {
        await queryClient.invalidateQueries({ queryKey: ['feature-panel', 'worldBoss'] });
        await queryClient.invalidateQueries({ queryKey: ['feature-panel', 'party'] });
      }
      const refreshState = await refreshActionState(
        () => panelQuery.refetch(),
        refreshBootstrap,
      );
      const successMessage = formatActionSuccess(label, result);
      if (refreshState.refreshed) {
        flashToast(successMessage, false);
      } else {
        flashToast(`${successMessage}；页面状态刷新不完整，请点击“刷新”重试。`, true);
      }
    }
  } catch (error) {
    flashToast(error instanceof Error ? error.message : '操作失败', true);
  } finally {
    actionBusy.value = '';
  }
}

async function refreshBootstrap(): Promise<boolean> {
  try {
    const payload = await gameApi.getBootstrap();
    gameStore.applyBootstrap(payload);
    return true;
  } catch {
    return false;
  }
}

// ─── 面板宽度拖拽 → 通过 inject 调整父级 grid 列宽 ───
const startPanelGridResize = inject<(() => void) | null>('startPanelGridResize', null);
const doPanelGridResize = inject<((deltaX: number, containerWidth: number) => void) | null>('doPanelGridResize', null);
const isResizingPanel = ref(false);
let resizeStartX = 0;

function startPanelResize(e: MouseEvent) {
  isResizingPanel.value = true;
  resizeStartX = e.clientX;
  startPanelGridResize?.();
  document.addEventListener('mousemove', doPanelResize);
  document.addEventListener('mouseup', stopPanelResize);
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
  e.preventDefault();
}

function doPanelResize(e: MouseEvent) {
  if (!isResizingPanel.value) return;
  const delta = e.clientX - resizeStartX;
  const frame = document.querySelector('.ling-frame') as HTMLElement | null;
  const containerWidth = frame?.clientWidth || 1000;
  doPanelGridResize?.(delta, containerWidth);
}

function stopPanelResize() {
  isResizingPanel.value = false;
  document.removeEventListener('mousemove', doPanelResize);
  document.removeEventListener('mouseup', stopPanelResize);
  document.body.style.cursor = '';
  document.body.style.userSelect = '';
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', doPanelResize);
  document.removeEventListener('mouseup', stopPanelResize);
});
</script>

<template>
  <section :class="['ling-placeholder', 'gc-panel', `ling-panel--${panel}`]" data-tone="gold">
    <div class="ling-placeholder-resize" @mousedown="startPanelResize"></div>
    <div class="ling-placeholder-inner">
    <header class="ling-panel-header gc-panel-head">
      <div class="gc-rule" aria-hidden="true"></div>
      <div class="gc-title">{{ panelTitle }}</div>
      <div class="gc-panel-sub">{{ panelSubtitle }}</div>
      <div class="ling-panel-header-actions">
          <button v-if="panel === 'quests'" class="ling-panel-tool rounded border border-line px-3 py-1 text-muted hover:text-star" :disabled="Boolean(actionBusy)" @click="refreshQuests">刷新任务</button>
          <button v-if="panel === 'explore'" class="ling-panel-tool ling-panel-tool--jade rounded border border-spirit/50 px-3 py-1 text-spirit" :disabled="Boolean(actionBusy)" @click="checkForGhost">检测残影</button>
          <button v-if="panel === 'ranking'" class="ling-panel-tool rounded border border-line px-3 py-1 text-muted hover:text-star" :disabled="Boolean(actionBusy)" @click="rankUp">升阶</button>
          <button v-if="panel === 'ranking'" class="ling-panel-tool ling-panel-tool--danger rounded border border-danger/50 px-3 py-1 text-danger" :disabled="Boolean(actionBusy)" @click="prestigeAvatar">回归</button>
          <button v-if="panel === 'titles'" class="ling-panel-tool rounded border border-line px-3 py-1 text-muted hover:text-star" :disabled="Boolean(actionBusy)" @click="evaluateTitles">评估称号</button>
          <button v-if="panel === 'equipment'" class="ling-panel-tool rounded border border-line px-3 py-1 text-muted hover:text-star" :disabled="Boolean(actionBusy)" @click="repairAll">全部修理</button>
          <button v-if="panel === 'system'" class="ling-panel-tool ling-panel-tool--accent rounded border border-star/50 px-3 py-1 text-star" @click="goAdmin">管理员</button>
          <button v-if="panel === 'system'" class="ling-panel-tool ling-panel-tool--danger rounded border border-danger/50 px-3 py-1 text-danger" @click="logout">退出登录</button>
          <button class="ling-panel-tool rounded border border-line px-3 py-1 text-muted hover:text-spirit" :disabled="panelQuery.isFetching.value" @click="panelQuery.refetch()">刷新</button>
          <button class="ling-panel-close gc-panel-close" @click="emit('close-panel')" title="关闭面板">×</button>
      </div>
      <div v-if="secondaryMenus.length && !['chat', 'history', 'quests', 'party', 'titles', 'trade', 'skills', 'pk', 'system'].includes(panel)" class="ling-submenu-row gc-panel-tabs">
        <button
          v-for="menu in secondaryMenus"
          :key="menu"
          type="button"
          :class="['ling-submenu-button', { active: activeSubMenu === menu }]"
          @click="selectSubMenu(menu)"
        >
          {{ menu }}
        </button>
      </div>
      <div v-if="actionMessage || actionError" class="ling-toast-wrap">
        <div v-if="actionMessage" class="ling-toast ling-toast-success" @click="actionMessage = ''">{{ actionMessage }}</div>
        <div v-if="actionError" class="ling-toast ling-toast-error" @click="actionError = ''">{{ actionError }}</div>
      </div>
    </header>

    <form v-if="panel === 'feedback'" class="ling-panel-form mb-4 space-y-3" @submit.prevent="submitFeedback">
      <select v-model="feedbackType" class="w-full rounded border border-line bg-control/80 px-3 py-2 text-sm text-ink outline-none focus:border-spirit">
        <option value="bug">问题</option>
        <option value="suggestion">建议</option>
        <option value="experience">体验</option>
      </select>
      <textarea v-model="feedbackText" class="min-h-28 w-full rounded border border-line bg-control/80 px-3 py-2 text-sm text-ink outline-none focus:border-spirit" placeholder="写下要提交给鬼怪管理局的内容"></textarea>
      <button class="rounded border border-star/60 px-4 py-2 text-sm text-star" :disabled="Boolean(actionBusy)">提交反馈</button>
    </form>

    <form v-if="panel === 'support'" class="ling-panel-form mb-4 space-y-3" @submit.prevent="publishBounty">
      <select v-model="bountyMonsterKey" class="w-full rounded border border-line bg-control/80 px-3 py-2 text-sm text-ink outline-none focus:border-spirit">
        <option value="">选择怪物（必选）</option>
        <option v-for="m in asArray(asRecord(panelQuery.data.value).monsters)" :key="pickText(m, ['key','monsterKey','monster_key','id'], '')" :value="pickText(m, ['key','monsterKey','monster_key'], '')">
          {{ pickText(m, ['name','monsterName','monster_name'], pickText(m, ['key','monsterKey'], '未知')) }}
        </option>
      </select>
      <select v-model="bountyLocationKey" class="w-full rounded border border-line bg-control/80 px-3 py-2 text-sm text-ink outline-none focus:border-spirit">
        <option value="">选择地点（可选）</option>
        <option v-for="l in asArray(asRecord(panelQuery.data.value).locations)" :key="pickText(l, ['location_key','locationKey','key'], '')" :value="pickText(l, ['location_key','locationKey','key'], '')">
          {{ pickText(l, ['location_name','locationName','name'], pickText(l, ['location_key','locationKey'], '未知')) }}
        </option>
      </select>
      <div class="flex items-center gap-2">
        <label class="text-xs text-muted">分成比例:</label>
        <input v-model.number="bountySharePercent" type="number" min="1" max="99" class="w-20 rounded border border-line bg-control/80 px-2 py-1 text-sm text-ink outline-none" />
        <span class="text-xs text-muted">%</span>
      </div>
      <button class="rounded border border-star/60 px-4 py-2 text-sm text-star" :disabled="Boolean(actionBusy)">发布悬赏</button>
    </form>

    <div v-if="panel === 'friends' && showGiftDialog" class="ling-panel-dialog mb-4 rounded-md border border-spirit/40 bg-control/80 p-4">
      <div class="mb-3 flex items-center justify-between gap-3">
        <h3 class="text-sm font-semibold text-star">赠送物品</h3>
        <button type="button" class="text-xs text-muted hover:text-ink" @click="closeGiftDialog">取消</button>
      </div>
      <p class="mb-3 text-xs text-muted">
        送给 {{ pickText(giftTarget || {}, ['friendName', 'playerName', 'name'], '该同伴') }}；只能赠送普通背包物品，装备需要先留在自己的装备栏。
      </p>
      <div v-if="giftItems.length" class="flex flex-col gap-2 sm:flex-row sm:items-center">
        <select v-model="giftItemKey" class="min-w-0 flex-1 rounded border border-line bg-control/80 px-3 py-2 text-sm text-ink outline-none focus:border-spirit">
          <option v-for="item in giftItems" :key="pickText(item, ['itemKey', 'item_key'], '')" :value="pickText(item, ['itemKey', 'item_key'], '')">
            {{ pickText(item, ['name', 'itemName', 'itemKey'], '未知物品') }} × {{ pickNumber(item, ['quantity'], 0) }}
          </option>
        </select>
        <input v-model.number="giftQuantity" class="w-24 rounded border border-line bg-control/80 px-3 py-2 text-sm text-ink outline-none focus:border-spirit" type="number" min="1" max="99" aria-label="赠送数量" />
        <button type="button" class="rounded border border-spirit/60 px-4 py-2 text-sm text-spirit hover:bg-spirit/10" :disabled="Boolean(actionBusy)" @click="submitGift">确认赠送</button>
      </div>
      <p v-else class="text-sm text-muted">背包中没有可赠送的普通物品。</p>
    </div>

    <div v-if="panel === 'inventory'" class="ling-inventory-workbench__chrome">
      <div class="ling-inventory-workbench__toolbar">
        <label class="ling-inventory-search" for="inventory-search">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
          <input id="inventory-search" v-model="inventorySearch" type="search" inputmode="search" placeholder="搜名称、词条…" autocomplete="off" />
          <button v-if="inventorySearch" type="button" aria-label="清空搜索" @click="inventorySearch = ''">×</button>
        </label>
        <button type="button" class="ling-inventory-filter-toggle" :aria-expanded="inventoryFilterOpen" @click="inventoryFilterOpen = !inventoryFilterOpen">
          <span aria-hidden="true"></span>筛选
        </button>
      </div>
      <div class="ling-inventory-workbench__summary">
        <span>纳戒物资</span>
        <strong>{{ inventoryItems.length }} 类 · {{ inventoryItemCount }} 件</strong>
      </div>
      <div v-if="inventoryFilterOpen" class="ling-inventory-filter-panel">
        <select v-model="inventoryRarity" aria-label="品质筛选">
          <option value="">全品质</option>
          <option value="1">普通</option>
          <option value="2">优良</option>
          <option value="3">稀有</option>
          <option value="4">史诗</option>
          <option value="5">传说</option>
        </select>
        <select v-model="inventorySort" aria-label="排序方式">
          <option value="default">默认排序</option>
          <option value="quantity">数量优先</option>
          <option value="name">名称排序</option>
        </select>
        <button type="button" @click="inventoryRarity = ''; inventorySort = 'default'">重置</button>
      </div>
    </div>

    <section v-if="panel === 'equipment'" class="ling-equipment-workbench" aria-label="装备">
      <div class="ling-equipment-workbench__head">
        <div>
          <span class="ling-equipment-workbench__rule" aria-hidden="true"></span>
          <h3>装备</h3>
          <p>已穿戴 {{ equippedSlotCount }} / {{ equipmentSlots.length }} 个部位</p>
        </div>
        <button type="button" @click="selectSubMenu('已穿戴')">查看已穿戴</button>
      </div>
      <div class="ling-equipment-slot-list">
        <button
          v-for="slot in equipmentSlots"
          :key="slot.key"
          type="button"
          :class="['ling-equipment-slot', { 'is-empty': !slot.item }, `ling-equipment-slot--${equipmentRarityClass(slot.item)}`]"
          @click="selectSubMenu('已穿戴')"
        >
          <span class="ling-equipment-slot__name">{{ slot.label }}</span>
          <span class="ling-equipment-slot__body">
            <strong>{{ slot.item ? pickText(slot.item, ['equipmentName', 'name', 'equipmentKey'], '未知装备') : '未装备' }}</strong>
            <small>{{ equipmentDetail(slot.item) }}</small>
          </span>
          <span class="ling-equipment-slot__chevron" aria-hidden="true">›</span>
        </button>
      </div>
    </section>

    <div v-if="!canLoadPanel(panel, playerId)" class="text-sm text-danger">缺少玩家信息，无法加载该面板。</div>
    <div v-else-if="panelQuery.isLoading.value" class="flex items-center gap-3 text-sm" style="color: var(--color-star);">
      <span class="inline-block w-3.5 h-3.5 border-2 border-star/30 border-t-star rounded-full animate-spin"></span>
      正在读取星流数据...
    </div>
    <div v-else-if="panelQuery.error.value" class="space-y-3 text-sm">
      <p class="text-danger">{{ panelQuery.error.value.message }}</p>
      <button class="rounded border border-line px-3 py-1 text-muted hover:text-spirit" @click="panelQuery.refetch()">重试</button>
    </div>
    <div v-else-if="panel === 'feedback'" class="text-sm text-muted">反馈内容只会在提交后写入后端。</div>
    <section v-else-if="panel === 'chat'" class="ling-chat-workbench" aria-label="星流频道">
      <nav class="ling-chat-tabs" role="tablist" aria-label="频道选择">
        <button
          v-for="channel in ['星流', '阵营', '队伍']"
          :key="channel"
          type="button"
          role="tab"
          :aria-selected="activeSubMenu === channel"
          :class="['ling-chat-tab', { active: activeSubMenu === channel }]"
          @click="selectSubMenu(channel)"
        >
          {{ channel }}
        </button>
      </nav>

      <div class="ling-chat-messages" role="log" aria-live="polite">
        <div v-if="visibleChatMessages.length === 0" class="ling-chat-empty">
          <span class="ling-chat-empty-mark">◇</span>
          <strong>暂无消息</strong>
          <small>{{ activeSubMenu || '星流' }}频道还没有新的消息。</small>
        </div>
        <article
          v-for="(message, messageIndex) in visibleChatMessages"
          :key="pickKey(message, ['id', 'createdAt', 'content'], `chat-${messageIndex}`)"
          :class="['ling-chat-message', { 'is-self': chatMessageIsSelf(message), 'is-system': pickText(message, ['messageType', 'message_type'], 'player') !== 'player' }]"
        >
          <div class="ling-chat-avatar" aria-hidden="true">{{ chatInitial(message) }}</div>
          <div class="ling-chat-stack">
            <div class="ling-chat-message-meta">
              <strong>{{ pickText(message, ['senderName', 'sender_name'], '未命名玩家') }}</strong>
              <span>{{ chatChannelLabel(pickText(message, ['channel'], chatChannel)) }}</span>
              <time>{{ formatPanelTime(pick(message, ['createdAt', 'created_at'])) }}</time>
            </div>
            <div class="ling-chat-bubble">{{ pickText(message, ['content', 'message'], '暂无消息内容') }}</div>
          </div>
        </article>
      </div>

      <footer class="ling-chat-composer-foot">
        <div class="ling-chat-filter-tabs" role="tablist" aria-label="消息类型">
          <button type="button" :class="{ active: chatFilter === 'all' }" @click="chatFilter = 'all'">全部</button>
          <button type="button" :class="{ active: chatFilter === 'chat' }" @click="chatFilter = 'chat'">聊天</button>
          <button type="button" :class="{ active: chatFilter === 'notice' }" @click="chatFilter = 'notice'">告示</button>
        </div>
        <form class="ling-chat-input-bar" @submit.prevent="sendChat">
          <textarea v-model="chatText" class="ling-chat-input" rows="1" maxlength="200" placeholder="说点什么…" aria-label="输入频道消息" @keydown.enter.exact.prevent="sendChat"></textarea>
          <button type="submit" class="ling-chat-send" :disabled="Boolean(actionBusy) || !chatText.trim()">发送</button>
        </form>
      </footer>
    </section>
    <section v-else-if="panel === 'history'" class="ling-history-workbench" aria-label="历程与因果记录">
      <nav class="ling-history-filters" role="tablist" aria-label="历程筛选">
        <button v-for="filter in historyFilters" :key="filter" type="button" :class="{ active: activeSubMenu === filter }" @click="selectSubMenu(filter)">{{ filter }}</button>
      </nav>
      <div class="ling-history-metrics">
        <div><span>近期记录</span><strong>{{ historyLogs.length }}</strong></div>
        <div><span>因果回响</span><strong>{{ storyLogEntries.length }}</strong></div>
        <div><span>试炼轨迹</span><strong>{{ asArray(panelData.quests).length }}</strong></div>
        <div><span>结局档案</span><strong>{{ endingArchiveEntries.length }}</strong></div>
      </div>
      <div class="ling-history-stream">
        <div v-if="historyLogs.length === 0" class="ling-history-empty">这一页还没有留下记录。</div>
        <article v-for="(entry, index) in historyLogs.slice(0, 40)" :key="pickKey(entry, ['id', 'createdAt', 'message'], `history-${index}`)" :class="['ling-history-entry', `is-${historyTone(pickText(entry, ['type'], 'info'))}`]">
          <span class="ling-history-marker" aria-hidden="true"></span>
          <div class="ling-history-entry-body">
            <div class="ling-history-entry-head">
              <span class="ling-history-type">{{ historyTypeLabel(pickText(entry, ['type'], 'info')) }}</span>
              <time>{{ formatPanelTime(pick(entry, ['createdAt', 'created_at', 'timestamp'])) }}</time>
            </div>
            <p>{{ pickText(entry, ['message', 'content'], '未命名事件') }}</p>
          </div>
        </article>
      </div>
      <details class="ling-history-ledger" :open="storyLogEntries.length > 0">
        <summary><span>因果卷宗</span><small>{{ storyLogEntries.length }} 条个人剧情记录</small></summary>
        <div v-if="storyLogEntries.length" class="ling-history-ledger-list">
          <article v-for="(story, index) in storyLogEntries.slice(-20)" :key="story.id || `${story.title}-${index}`">
            <div><span>{{ story.source }} · {{ story.location }}</span><time>{{ formatPanelTime(story.time) }}</time></div>
            <strong>{{ story.title }}</strong>
            <p v-if="story.source === 'NPC 残影'">你选择「{{ story.choice }}」<span v-if="story.consequence"> · 残影回应：「{{ story.consequence }}」</span></p>
            <p v-else>选择「{{ story.choice }}」<span v-if="story.consequence"> · {{ story.consequence }}</span></p>
            <div v-if="story.effects.length" class="ling-history-outcomes" aria-label="实际结算影响">
              <span class="ling-history-outcomes-label">实际影响</span>
              <span v-for="effect in story.effects" :key="effect" class="ling-history-outcome-chip">{{ effect }}</span>
            </div>
          </article>
        </div>
        <p v-if="npcRecapUnavailable" class="ling-history-ledger-empty">NPC 残影回顾暂不可用；其他故事记录仍可查看。</p>
        <p v-else-if="!storyLogEntries.length" class="ling-history-ledger-empty">尚未记录地点故事选择或 NPC 残影对话。</p>
      </details>
      <EndingArchivePanel :endings="panelData.endings" />
    </section>
    <section v-else-if="panel === 'quests'" class="ling-quest-workbench" aria-label="试炼任务">
      <div class="ling-quest-summary">
        <div><span>每日</span><strong>{{ pickNumber(questSummary, ['dailyCompleted'], 0) }} / {{ pickNumber(questSummary, ['dailyTotal'], 0) }}</strong><small>已完成</small></div>
        <div><span>每周</span><strong>{{ pickNumber(questSummary, ['weeklyCompleted'], 0) }} / {{ pickNumber(questSummary, ['weeklyTotal'], 0) }}</strong><small>已完成</small></div>
        <div class="is-highlight"><span>待领取</span><strong>{{ pickNumber(questSummary, ['dailyClaimable'], 0) + pickNumber(questSummary, ['weeklyClaimable'], 0) + pickNumber(questSummary, ['achievementClaimable'], 0) }}</strong><small>奖励</small></div>
      </div>
      <nav class="ling-quest-tabs" role="tablist" aria-label="试炼分类">
        <button v-for="menu in secondaryMenus" :key="menu" type="button" :class="{ active: activeSubMenu === menu }" @click="selectSubMenu(menu)">{{ menu }}</button>
      </nav>
      <div class="ling-quest-list">
        <div v-if="questItems.length === 0" class="ling-quest-empty">当前分类暂无试炼。</div>
        <article v-for="(quest, index) in questItems" :key="pickKey(quest, ['id', 'questKey', 'key'], `quest-${index}`)" :class="['ling-quest-card', questStatusClass(pickText(quest, ['status'], 'active'))]">
          <header class="ling-quest-card-head">
            <div class="ling-quest-name-wrap">
              <span class="ling-quest-type">{{ questTypeLabel(quest) }}</span>
              <strong>{{ pickText(quest, ['title', 'questName', 'name', 'questKey'], '未命名试炼') }}</strong>
            </div>
            <span class="ling-quest-status">{{ questStatusLabel(pickText(quest, ['status'], 'active')) }}</span>
          </header>
          <p class="ling-quest-description">{{ pickText(quest, ['description', 'desc'], '完成目标，领取对应奖励。') }}</p>
          <small v-if="questDependencyHint(quest)" class="ling-quest-dependency">{{ questDependencyHint(quest) }}</small>
          <div class="ling-quest-progress"><span :style="{ width: `${questProgress(quest).pct}%` }"></span></div>
          <footer class="ling-quest-card-foot">
            <span>{{ questProgress(quest).current }} / {{ questProgress(quest).target || '—' }} · {{ questRewardText(quest) }}</span>
            <template v-if="['completed', '可领取'].includes(pickText(quest, ['status'], '').toLowerCase())">
              <button v-for="action in questActions(quest)" :key="action.label" type="button" class="ling-quest-claim" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ actionBusy === action.label ? '处理中' : '领取' }}</button>
            </template>
          </footer>
        </article>
      </div>
      <div class="ling-quest-catalog"><span>任务图谱</span><strong>{{ asArray(panelData.definitions).length }} 个定义</strong><small>任务进度会随探索、战斗与队伍行动自动推进。</small></div>
    </section>
    <section v-else-if="panel === 'skills'" class="ling-transmit-workbench" aria-label="技能图谱">
      <header class="ling-transmit-hero">
        <div>
          <span class="ling-transmit-eyebrow">星流读者 · 能力档案</span>
          <h3>技能图谱</h3>
          <p>查看技能的学习条件、消耗、装备与效果。普通技能是游戏内规则，不等同于原著能力；未接入的效果会明确标注。原著故事关系只在有依据的具体遭遇中体现，不套用通用克制倍率。</p>
        </div>
        <div class="ling-transmit-bonus">
          <span>技能槽 · 已占用 / 开放</span>
          <strong>{{ pickNumber(gameStore.skillSummary, ['equippedSkillCount'], 0) }} / {{ pickNumber(gameStore.skillSummary, ['skillSlotLimit'], 0) }}</strong>
          <small>当前加成：{{ formatSkillEffectSummary(panelData.bonus) || '暂无' }}</small>
        </div>
        <button type="button" class="ling-transmit-refresh" @click="panelQuery.refetch()">刷新</button>
      </header>
      <aside v-if="skillSlotNotice" class="ling-transmit-slot-notice" role="status">
        <strong>{{ skillSlotNotice.title }}</strong>
        <span>{{ skillSlotNotice.message }}</span>
      </aside>

      <div class="ling-transmit-chrome">
        <nav class="ling-transmit-tabs" role="tablist" aria-label="能力档案分类">
          <button v-for="tab in transmitTabs" :key="tab.key" type="button" role="tab" :aria-selected="transmitTab === tab.key" :class="{ active: transmitTab === tab.key }" @click="selectTransmitTab(tab.key)">{{ tab.label }}</button>
        </nav>
        <div v-if="transmitTab === 'browse' || transmitTab === 'my'" class="ling-transmit-toolbar">
          <label class="ling-transmit-search">
            <span aria-hidden="true">⌕</span>
            <input v-model="transmitSearch" type="search" placeholder="搜索技能、星痕或效果…" aria-label="搜索能力名称或效果" />
            <button v-if="transmitSearch" type="button" aria-label="清空技能搜索" @click="transmitSearch = ''">×</button>
          </label>
          <select v-model="transmitType" aria-label="能力类别">
            <option value="">全部类型</option>
            <option value="art">星痕</option>
            <option value="skill">技能</option>
            <option value="custom">自创</option>
            <option value="other">其他类型</option>
          </select>
          <select v-model="transmitRarity" aria-label="能力稀有度">
            <option value="">全品阶</option>
            <option value="common">普通</option>
            <option value="uncommon">优良</option>
            <option value="rare">稀有</option>
            <option value="epic">史诗</option>
            <option value="legendary">传说</option>
          </select>
          <select v-model="transmitRealm" aria-label="最低角色等级">
            <option value="">全部等级</option>
            <option value="1">1级+</option>
            <option value="2">2级+</option>
            <option value="3">3级+</option>
            <option value="4">4级+</option>
            <option value="5">5级+</option>
          </select>
        </div>
      </div>

      <section v-if="transmitTab === 'list' || transmitTab === 'history'" class="ling-transmit-placeholder">
        <span class="ling-transmit-placeholder-mark">◇</span>
        <strong>{{ transmitTab === 'list' ? '技能分享' : '学习记录' }}尚未接入</strong>
        <p>技能图谱已支持普通技能的浏览、学习、装备与加成；星痕尚无独立的授予与成长规则，分享和学习记录也需要服务端接口支持。</p>
      </section>

      <section v-else class="ling-transmit-list-section">
        <div class="ling-transmit-section-head">
          <div><span>01</span><h4>{{ transmitTab === 'browse' ? '技能图谱与当前可学习内容' : '我已掌握的能力' }}</h4></div>
          <small>{{ transmitRows.length }} 条记录</small>
        </div>
        <div v-if="transmitRows.length" class="ling-transmit-list">
          <article v-for="(entry, index) in transmitRows" :key="pickKey(entry, ['skillKey', 'skill_key', 'id'], `transmit-${index}`)" :class="['ling-transmit-card', `is-${transmitRarityClass(entry) || 'plain'}`]">
            <header class="ling-transmit-card-head">
              <div><span>{{ transmitTypeLabel(pick(entry, ['type', 'skillType', 'skill_type'])) }}</span><h5>{{ transmitName(entry) }}</h5></div>
              <b v-if="!supportsStandardSkillLifecycle(pick(entry, ['type', 'skillType', 'skill_type']))">{{ classifyAbilityType(pick(entry, ['type', 'skillType', 'skill_type'])) === 'art' ? '星痕规则待接入' : '能力类型待确认' }}</b>
              <b v-else-if="transmitTab === 'browse' && transmitOwnedKeys.has(pickText(entry, ['skillKey', 'skill_key', 'key'], ''))">已掌握</b>
              <b v-else-if="transmitTab === 'browse' && transmitUnlockableKeys.has(pickText(entry, ['skillKey', 'skill_key', 'key'], ''))">可学习</b>
              <b v-else-if="transmitTab === 'browse'">暂不可学</b>
              <b v-else-if="pickText(entry, ['rarity', 'tier'], '')">{{ pickText(entry, ['rarity', 'tier'], '') }}</b>
            </header>
            <p>{{ pickText(entry, ['description', 'desc'], '尚未记录详细技能说明。') }}</p>
            <p v-if="!supportsStandardSkillLifecycle(pick(entry, ['type', 'skillType', 'skill_type']))" class="ling-transmit-unavailable">{{ classifyAbilityType(pick(entry, ['type', 'skillType', 'skill_type'])) === 'art' ? '星痕需要独立的授予、成长与装备规则；当前不会按普通技能学习或装备。' : '暂不识别此能力类型，当前不会提供学习或装备操作。' }}</p>
            <p v-else-if="transmitTab === 'browse' && !transmitOwnedKeys.has(pickText(entry, ['skillKey', 'skill_key', 'key'], '')) && !transmitUnlockableKeys.has(pickText(entry, ['skillKey', 'skill_key', 'key'], ''))" class="ling-transmit-unavailable">当前解锁门槛、学习消耗或休息状态限制尚未满足；具体要求见下方。</p>
            <div class="ling-transmit-meta">
              <span v-if="pickText(entry, ['rarity', 'tier'], '')">稀有度 {{ pickText(entry, ['rarity', 'tier'], '—') }}</span>
              <span v-if="pick(entry, ['level']) !== undefined">等级 {{ pickNumber(entry, ['level'], 1) }}</span>
              <span v-if="pickText(entry, ['cooldownSeconds', 'cooldown_seconds'], '')">冷却 {{ pickText(entry, ['cooldownSeconds', 'cooldown_seconds'], '—') }} 秒</span>
              <span v-if="pick(entry, ['equipped']) !== undefined">{{ truthyRecordValue(entry, ['equipped']) ? '已装备' : '未装备' }}</span>
              <span v-if="formatSummaryValue(entry.cost)">学习消耗 {{ formatSummaryValue(entry.cost) }}</span>
              <span v-if="formatSummaryValue(entry.unlockConditions)">解锁条件 {{ formatSummaryValue(entry.unlockConditions) }}</span>
            </div>
            <footer class="ling-transmit-card-foot">
              <span>{{ formatSkillEffectSummary(entry.effects) || '习得后可查看效果' }}</span>
              <div class="ling-transmit-actions"><button v-for="action in transmitActions(entry)" :key="action.label" type="button" :class="{ danger: action.danger }" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ actionBusy === action.label ? '处理中' : action.label }}</button></div>
            </footer>
          </article>
        </div>
        <p v-else class="ling-transmit-empty"><strong>暂无匹配技能</strong><span>换个关键词，或调整类型、稀有度和等级筛选。</span></p>
      </section>
    </section>
    <section v-else-if="panel === 'trade'" class="ling-market-workbench" aria-label="鬼怪商店">
      <header class="ling-market-hero">
        <div class="ling-market-hero-copy">
          <span class="ling-market-eyebrow">星流读者 · 物资交易</span>
          <h3>鬼怪商店</h3>
          <p>在星流市场查看挂单、管理交易记录与物资。</p>
        </div>
        <div class="ling-market-balance">
          <span>持有星币</span>
          <strong>{{ pickNumber(asRecord(gameStore.player), ['coins'], 0) }}</strong>
          <small>星币</small>
        </div>
        <div class="ling-market-hero-actions">
          <button type="button" @click="panelQuery.refetch()">刷新</button>
          <button type="button" @click="emit('selectPanel', 'inventory')">背包</button>
        </div>
      </header>

      <div class="ling-market-chrome">
        <nav class="ling-market-tabs" role="tablist" aria-label="商店分类">
          <button
            v-for="tab in tradeTabs"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="tradeTab === tab.key"
            :class="{ active: tradeTab === tab.key }"
            @click="selectTradeTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </nav>
        <div class="ling-market-toolbar">
          <label class="ling-market-search">
            <span aria-hidden="true">⌕</span>
            <input v-model="tradeSearch" type="search" placeholder="搜索物品、装备或玩家…" aria-label="搜索商店" />
            <button v-if="tradeSearch" type="button" aria-label="清空商店搜索" @click="tradeSearch = ''">×</button>
          </label>
          <select v-model="tradeSort" aria-label="商品排序">
            <option value="newest">最新</option>
            <option value="priceAsc">价低</option>
            <option value="priceDesc">价高</option>
          </select>
          <button type="button" class="ling-market-refresh" @click="panelQuery.refetch()">重载</button>
        </div>
      </div>

      <div class="ling-market-summary" aria-label="交易摘要">
        <div><span>在售挂单</span><strong>{{ pickNumber(tradeSummary, ['activeListingCount'], 0) }}</strong></div>
        <div><span>已卖出</span><strong>{{ pickNumber(tradeSummary, ['soldCount'], 0) }}</strong></div>
        <div><span>已买入</span><strong>{{ pickNumber(tradeSummary, ['boughtCount'], 0) }}</strong></div>
        <div><span>近期成交</span><strong>{{ pickNumber(tradeSummary, ['recentTradeCount'], tradeRecords.length) }}</strong></div>
      </div>

      <section class="ling-market-list-section">
        <div class="ling-market-section-head">
          <div><span>01</span><h4>{{ tradeTabLabels[tradeTab] }}</h4></div>
          <small v-if="tradeTab === 'market'">{{ tradeRows.length }} 条挂单</small>
          <small v-else-if="tradeTab === 'mine'">{{ tradeRows.length }} 条记录</small>
          <small v-else-if="tradeTab === 'history' || tradeTab === 'public'">{{ tradeRows.length }} 条成交</small>
        </div>

        <div v-if="tradeTab === 'buyreq' || tradeTab === 'watch'" class="ling-market-placeholder">
          <span class="ling-market-placeholder-mark">◇</span>
          <strong>{{ tradeTab === 'buyreq' ? '求购信标' : '关注清单' }}尚未接入</strong>
          <p>当前星流读者后端还没有对应数据接口；现有市场、我的挂单和成交记录仍可正常使用。</p>
        </div>

        <div v-else-if="tradeRows.length" class="ling-market-list">
          <article v-for="(entry, index) in tradeRows" :key="pickKey(entry, ['listingNo', 'listing_no', 'id'], `trade-${index}`)" :class="['ling-market-card', `is-${equipmentRarityClass(entry) || 'plain'}`]">
            <header class="ling-market-card-head">
              <div class="ling-market-card-copy">
                <span>{{ pickText(entry, ['listingType', 'listing_type'], tradeTab === 'history' || tradeTab === 'public' ? '成交记录' : '市场挂单') }}</span>
                <h5>{{ tradeEntryName(entry) }}</h5>
              </div>
              <div class="ling-market-price">
                <strong>{{ pickNumber(entry, ['totalPrice', 'unitPrice'], 0) }}</strong>
                <small>星币{{ pick(entry, ['totalPrice']) === undefined && pick(entry, ['quantity']) ? ' / 件' : '' }}</small>
              </div>
            </header>
            <div class="ling-market-meta">
              <span v-if="pick(entry, ['quantity']) !== undefined">数量 {{ pickNumber(entry, ['quantity'], 1) }}</span>
              <span v-if="pickText(entry, ['sellerName', 'seller_name'], '')">卖家 {{ pickText(entry, ['sellerName', 'seller_name'], '未知') }}</span>
              <span v-if="pickText(entry, ['buyerName', 'buyer_name'], '')">买家 {{ pickText(entry, ['buyerName', 'buyer_name'], '未知') }}</span>
              <span v-if="pickText(entry, ['status'], '')">{{ tradeStatusLabel(pick(entry, ['status'])) }}</span>
              <span v-if="pickText(entry, ['createdAt', 'created_at', 'soldAt'], '')">{{ formatPanelTime(pick(entry, ['createdAt', 'created_at', 'soldAt'])) }}</span>
            </div>
            <footer class="ling-market-card-foot">
              <span>{{ pickText(entry, ['listingNo', 'listing_no'], '星流挂单') }}</span>
              <div class="ling-market-card-actions">
                <button v-for="action in tradeEntryActions(entry)" :key="action.label" type="button" :class="{ danger: action.danger }" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ actionBusy === action.label ? '处理中' : action.label }}</button>
              </div>
            </footer>
          </article>
        </div>
        <p v-else class="ling-market-empty"><strong>暂无{{ tradeTabLabels[tradeTab] }}记录</strong><span>换个关键词，或稍后再来商店看看。</span></p>
      </section>
    </section>
    <section v-else-if="panel === 'party'" class="ling-party-workbench" aria-label="队伍协作">
      <div :class="['ling-party-overview', { 'is-empty': !Object.keys(myParty).length }]">
        <div class="ling-party-seal" aria-hidden="true">队</div>
        <div class="ling-party-overview-copy">
          <span class="ling-party-kicker">队伍协作</span>
          <h3>{{ pickText(myParty, ['name', 'partyName'], '尚未加入队伍') }}</h3>
          <p>{{ pickText(myParty, ['description'], Object.keys(myParty).length ? '等待队员集结。' : '创建或加入队伍，与其他玩家共同挑战灾厄。') }}</p>
        </div>
        <div class="ling-party-overview-count"><strong>{{ pickNumber(partySummary, ['memberCount'], partyMembers.length) }} / {{ pickNumber(partySummary, ['maxMembers', 'max_members'], pickNumber(myParty, ['maxMembers', 'max_members'], 3)) }}</strong><span>成员</span></div>
      </div>
      <div class="ling-party-actions">
        <button v-for="action in partyLeaveActions()" :key="action.label" type="button" :class="{ danger: action.danger, primary: action.label === '创建队伍' || action.label === '开始战斗' }" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ actionBusy === action.label ? '处理中' : action.label }}</button>
      </div>
      <div v-if="partyMembers.length" class="ling-party-section">
        <div class="ling-party-section-head"><span>队伍成员</span><small>{{ pickNumber(partySummary, ['onlineMemberCount'], 0) }} 人在线</small></div>
        <div class="ling-party-member-grid">
          <article v-for="(member, index) in partyMembers" :key="pickKey(member, ['playerId', 'id'], `member-${index}`)" class="ling-party-member">
            <div class="ling-party-member-avatar">{{ chatInitial({ senderName: pickText(member, ['playerName', 'name'], '道') }) }}</div>
            <div class="ling-party-member-copy"><strong>{{ pickText(member, ['playerName', 'name'], '未命名玩家') }}</strong><span>Lv.{{ pickNumber(member, ['level'], 1) }} · {{ pickText(member, ['role'], 'member') }}</span></div>
            <div class="ling-party-member-badges"><i :class="{ online: member.online }">{{ member.online ? '在线' : '离线' }}</i><i v-if="member.ready" class="ready">已准备</i></div>
          </article>
        </div>
      </div>
      <div class="ling-party-section">
        <div class="ling-party-section-head"><span>活跃队伍</span><small>{{ activeParties.length }} 支队伍正在招募</small></div>
        <div v-if="activeParties.length === 0" class="ling-party-empty">目前没有公开队伍，创建一支队伍开始冒险。</div>
        <article v-for="(party, index) in activeParties" :key="pickKey(party, ['partyNo', 'party_no', 'id'], `active-party-${index}`)" class="ling-party-recruit-card">
          <div class="ling-party-recruit-copy"><strong>{{ pickText(party, ['name', 'partyName'], '无名队伍') }}</strong><p>{{ pickText(party, ['description'], '暂无招募宣言') }}</p><span>{{ pickText(party, ['leaderName', 'leader_name'], '未知队长') }} · {{ partyStatusLabel(pickText(party, ['status'], 'active')) }}</span></div>
          <div class="ling-party-recruit-meta"><strong>{{ pickNumber(party, ['memberCount'], 0) }} / {{ pickNumber(party, ['maxMembers', 'max_members'], 3) }}</strong><button v-for="action in partyJoinActions(party)" :key="action.label" type="button" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ actionBusy === action.label ? '处理中' : action.label }}</button></div>
        </article>
      </div>
    </section>
    <section v-else-if="panel === 'friends'" class="ling-friend-workbench" aria-label="同伴列表">
      <div class="ling-friend-chrome">
        <nav class="ling-friend-filter-tabs" role="tablist" aria-label="同伴筛选">
          <button v-for="filter in friendFilters" :key="filter" type="button" role="tab" :class="{ active: friendFilter === filter }" @click="setFriendFilter(filter)">{{ filter }}<span v-if="filter === '在线'">{{ friendOnlineCount }}</span></button>
        </nav>
        <div class="ling-friend-toolbar">
          <label class="ling-friend-search"><span aria-hidden="true">⌕</span><input v-model="friendKeyword" type="search" placeholder="搜索玩家，至少 2 个字" aria-label="搜索玩家" @keydown.enter.prevent="panelQuery.refetch()" /><button v-if="friendKeyword" type="button" aria-label="清空搜索" @click="friendKeyword = ''">×</button></label>
          <select v-model="friendSort" aria-label="同伴排序"><option>默认</option><option>等级</option></select>
          <button type="button" class="ling-friend-search-button" @click="panelQuery.refetch()">搜索</button>
        </div>
      </div>

      <div class="ling-friend-counts">
        <div><span>同伴总数</span><strong>{{ friendRows.length }}</strong></div>
        <div><span>在线同伴</span><strong>{{ friendOnlineCount }}</strong></div>
        <div><span>待处理申请</span><strong>{{ friendRequestRows.length }}</strong></div>
      </div>

      <section v-if="activeSubMenu === '搜索同伴' || friendKeyword.trim().length >= 2" class="ling-friend-section">
        <header class="ling-friend-section-head"><h3>搜索结果</h3><span>{{ friendSearchRows.length }} 位</span></header>
        <div v-if="friendSearchRows.length" class="ling-friend-roster">
          <article v-for="(entry, index) in friendSearchRows" :key="pickKey(entry, ['id', 'playerId'], `friend-search-${index}`)" class="ling-friend-card">
            <div class="ling-friend-avatar">{{ chatInitial({ senderName: pickText(entry, ['playerName', 'name'], '道') }) }}</div>
            <div class="ling-friend-identity"><strong>{{ pickText(entry, ['playerName', 'name'], '未命名玩家') }}</strong><span>Lv.{{ pickNumber(entry, ['level'], 1) }} · ID {{ pickText(entry, ['id', 'playerId'], '—') }}</span></div>
            <div class="ling-friend-actions"><button v-for="action in friendRequestActions(entry)" :key="action.label" type="button" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ action.label }}</button></div>
          </article>
        </div>
        <p v-else class="ling-friend-empty"><strong>{{ friendKeyword.trim().length >= 2 ? '没有找到符合条件的玩家' : '输入至少 2 个字搜索玩家' }}</strong><span>搜索结果会显示在这里。</span></p>
      </section>

      <section v-else-if="activeSubMenu === '待处理申请'" class="ling-friend-section">
        <header class="ling-friend-section-head"><h3>待处理申请</h3><span>{{ friendRequestRows.length }} 条</span></header>
        <div v-if="friendRequestRows.length" class="ling-friend-roster">
          <article v-for="(entry, index) in friendRequestRows" :key="pickKey(entry, ['requestId', 'id'], `friend-request-${index}`)" class="ling-friend-card is-request">
            <div class="ling-friend-avatar">{{ chatInitial({ senderName: pickText(entry, ['fromPlayerName', 'playerName', 'name'], '道') }) }}</div>
            <div class="ling-friend-identity"><strong>{{ pickText(entry, ['fromPlayerName', 'playerName', 'name'], '未命名玩家') }}</strong><span>{{ pickText(entry, ['status'], '请求结交') }} · {{ formatPanelTime(pick(entry, ['createdAt', 'created_at'])) }}</span></div>
            <div class="ling-friend-actions"><button v-for="action in friendRequestHandleActions(entry)" :key="action.label" type="button" :class="{ danger: action.danger }" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ action.label }}</button></div>
          </article>
        </div>
        <p v-else class="ling-friend-empty"><strong>暂无待处理申请</strong><span>新的同伴申请会出现在这里。</span></p>
      </section>

      <section v-else-if="activeSubMenu === '最近互动'" class="ling-friend-section">
        <header class="ling-friend-section-head"><h3>最近互动</h3><span>{{ friendRecentRows.length }} 条</span></header>
        <div v-if="friendRecentRows.length" class="ling-friend-roster">
          <article v-for="(entry, index) in friendRecentRows" :key="pickKey(entry, ['id', 'playerId'], `friend-recent-${index}`)" class="ling-friend-card">
            <div class="ling-friend-avatar">{{ chatInitial({ senderName: pickText(entry, ['playerName', 'name'], '道') }) }}</div>
            <div class="ling-friend-identity"><strong>{{ pickText(entry, ['playerName', 'name'], '未命名玩家') }}</strong><span>{{ pickText(entry, ['interactionType', 'type'], '最近互动') }} · {{ pickText(entry, ['status'], '已记录') }}</span></div>
            <span class="ling-friend-relation">{{ formatPanelTime(pick(entry, ['interactedAt', 'createdAt'])) }}</span>
          </article>
        </div>
        <p v-else class="ling-friend-empty"><strong>暂无最近互动</strong><span>与同伴互动后会在这里留下记录。</span></p>
      </section>

      <section v-else class="ling-friend-section">
        <header class="ling-friend-section-head"><h3>同伴列表</h3><span>{{ friendRows.length }} 位 · {{ friendOnlineCount }} 在线</span></header>
        <div v-if="friendRows.length" class="ling-friend-roster">
          <article v-for="(entry, index) in friendRows" :key="pickKey(entry, ['friendPlayerId', 'playerId', 'id'], `friend-${index}`)" class="ling-friend-card">
            <div :class="['ling-friend-avatar', { offline: !truthyRecordValue(entry, ['online', 'isOnline', 'is_online']) }]">{{ chatInitial({ senderName: pickText(entry, ['friendName', 'playerName', 'name'], '道') }) }}</div>
            <div class="ling-friend-identity"><strong>{{ pickText(entry, ['friendName', 'playerName', 'name'], '未命名玩家') }}</strong><span>Lv.{{ pickNumber(entry, ['level'], 1) }} · {{ pickText(entry, ['realm', 'storyGrade', 'status'], '在线') }}</span></div>
            <div class="ling-friend-badges"><i :class="{ online: truthyRecordValue(entry, ['online', 'isOnline', 'is_online']) }">{{ truthyRecordValue(entry, ['online', 'isOnline', 'is_online']) ? '在线' : '离线' }}</i></div>
            <div class="ling-friend-actions"><button v-for="action in friendRemoveActions(entry)" :key="action.label" type="button" :class="{ danger: action.danger }" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ action.label }}</button></div>
          </article>
        </div>
        <p v-else class="ling-friend-empty"><strong>暂无同伴</strong><span>可以搜索玩家并发送同伴申请。</span></p>
      </section>
      </section>
    <section v-else-if="panel === 'titles'" class="ling-encyclopedia-workbench" aria-label="星流档案">
      <div class="ling-encyclopedia-chrome">
        <label class="ling-encyclopedia-search">
          <span aria-hidden="true">⌕</span>
          <input v-model="encyclopediaSearch" type="search" placeholder="搜索名称、来源、用途…" aria-label="搜索图鉴" />
          <button v-if="encyclopediaSearch" type="button" aria-label="清空图鉴搜索" @click="encyclopediaSearch = ''">×</button>
        </label>
        <nav class="ling-encyclopedia-tabs" role="tablist" aria-label="图鉴分类">
          <button v-for="tab in subMenusFor('titles')" :key="tab" type="button" role="tab" :class="{ active: activeSubMenu === tab }" @click="selectSubMenu(tab)">{{ tab }}</button>
        </nav>
      </div>

      <div class="ling-encyclopedia-summary">
        <div><span>已收录</span><strong>{{ asArray(panelData.all).length }}</strong></div>
        <div><span>已拥有</span><strong>{{ asArray(panelData.mine).length }}</strong></div>
        <div><span>当前称号</span><strong>{{ pickText(asRecord(panelData.equipped), ['titleName', 'name'], '未装备') }}</strong></div>
        <div><span>效果</span><strong>{{ formatSummaryValue(panelData.effects) || '暂无' }}</strong></div>
      </div>

      <div class="ling-encyclopedia-section-head">
        <div><span class="ling-encyclopedia-index">01</span><h3>{{ activeSubMenu || '档案检索' }}</h3></div>
        <span>{{ encyclopediaRows.length }} 条记录</span>
      </div>
      <div v-if="encyclopediaRows.length" class="ling-encyclopedia-grid">
        <article v-for="(entry, index) in encyclopediaRows" :key="pickKey(entry, ['titleKey', 'title_key', 'id', 'key'], `encyclopedia-${index}`)" :class="['ling-encyclopedia-card', `is-${equipmentRarityClass(entry) || 'plain'}`]">
          <div class="ling-encyclopedia-card-head">
            <div><span>{{ pickText(entry, ['type', 'category', 'source'], '档案') }}</span><h4>{{ pickText(entry, ['titleName', 'name', 'titleKey'], '未命名档案') }}</h4></div>
            <b>{{ encyclopediaOwnedKeys.has(pickText(entry, ['titleKey', 'title_key', 'key'], '')) ? '已拥有' : pickText(entry, ['rarity', 'tier'], '未定') }}</b>
          </div>
          <p>{{ pickText(entry, ['description', 'desc', 'effects'], '尚未记录详细说明。') }}</p>
          <div class="ling-encyclopedia-meta"><span v-if="pick(entry, ['source'])">来源 {{ pickText(entry, ['source'], '未知') }}</span><span v-if="pick(entry, ['rarity', 'tier'])">品质 {{ pickText(entry, ['rarity', 'tier'], '—') }}</span><span v-if="pick(entry, ['status'])">{{ pickText(entry, ['status'], '已记录') }}</span></div>
          <div class="ling-encyclopedia-actions"><button v-for="action in titleActions(entry)" :key="action.label" type="button" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ actionBusy === action.label ? '处理中' : action.label }}</button></div>
        </article>
      </div>
      <p v-else class="ling-encyclopedia-empty"><strong>暂无匹配档案</strong><span>换一个关键词，或切换其它图鉴分类。</span></p>
    </section>
    <section v-else-if="panel === 'faction'" class="ling-sect-workbench" aria-label="星座阵营工作台">
      <div class="ling-sect-hero">
        <div class="ling-sect-hero-copy">
          <span class="ling-sect-eyebrow">星流读者 · 星座阵营</span>
          <h2>{{ sectName }}</h2>
          <p v-if="sectHasFaction">{{ pickText(sectSummary, ['buffDescription'], pickText(sectBuff, ['description'], '阵营加成已接入你的化身。')) }}</p>
          <p v-else>选择一方星座阵营，获得专属设施、技能与世界线立场。</p>
        </div>
        <div :class="['ling-sect-status', { joined: sectHasFaction }]">
          <span>阵营状态</span>
          <strong>{{ sectHasFaction ? '已加入' : '待选择' }}</strong>
          <small>{{ sectHasFaction ? pickText(sectSummary, ['role'], pickText(sectMe, ['role'], '成员')) : '选择阵营后解锁设施' }}</small>
        </div>
      </div>

      <div class="ling-sect-kpis" aria-label="阵营摘要">
        <div v-for="kpi in sectKpis" :key="kpi.label" class="ling-sect-kpi">
          <span>{{ kpi.label }}</span>
          <strong>{{ kpi.value }}</strong>
        </div>
      </div>

      <section v-if="activeSubMenu !== '星座设施'" class="ling-sect-section">
        <header class="ling-sect-section-head">
          <div><span class="ling-sect-section-index">01</span><h3>阵营总览</h3></div>
          <span>{{ sectFactions.length }} 个阵营</span>
        </header>

        <div class="ling-sect-identity">
          <div class="ling-sect-identity-mark" aria-hidden="true">✦</div>
          <div class="ling-sect-identity-copy">
            <span>{{ sectHasFaction ? '当前归属' : '尚未归属' }}</span>
            <strong>{{ sectHasFaction ? sectName : '选择你的星座阵营' }}</strong>
            <p v-if="sectHasFaction">声望 {{ pickText(sectSummary, ['reputation'], pickText(sectMe, ['reputation'], '0')) }} · 今日贡献 {{ pickText(sectSummary, ['dailyContribution'], pickText(sectBuff, ['dailyContribution'], '0')) }}</p>
            <p v-else>加入阵营后，阵营技能、阵营战与贡献排行将会在这里展开。</p>
          </div>
          <span class="ling-sect-identity-badge">{{ sectHasFaction ? '已记录' : '未选择' }}</span>
        </div>

        <div class="ling-sect-faction-grid">
          <article v-for="faction in sectFactions" :key="pickKey(faction, ['factionKey', 'faction_key', 'id'], 'faction')" class="ling-sect-faction-card">
            <div class="ling-sect-faction-card-head">
              <div><span>{{ pickText(faction, ['constellationName', 'constellationKey'], '星座阵营') }}</span><h4>{{ pickText(faction, ['name', 'factionName', 'factionKey'], '未命名阵营') }}</h4></div>
              <b>{{ pickText(faction, ['alignment', 'ideology'], '中立') }}</b>
            </div>
            <p>{{ pickText(faction, ['description', 'ideology'], '阵营资料尚未公开。') }}</p>
            <div class="ling-sect-faction-meta"><span>Lv.{{ pickText(faction, ['level', 'factionLevel'], '1') }}</span><span>{{ pickText(faction, ['memberCount', 'activeMembers'], '0') }} 名成员</span><span>{{ pickText(faction, ['totalContribution', 'totalContributionScore'], '0') }} 贡献</span></div>
            <div v-if="sectFactionActions(faction).length" class="ling-sect-card-actions">
              <button v-for="action in sectFactionActions(faction)" :key="action.label" type="button" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ actionBusy === action.label ? '处理中' : action.label }}</button>
            </div>
            <span v-else class="ling-sect-current-badge">当前阵营</span>
          </article>
        </div>
      </section>

      <template v-else>
        <section class="ling-sect-section">
          <header class="ling-sect-section-head">
            <div><span class="ling-sect-section-index">01</span><h3>阵营设施</h3></div>
            <span>{{ sectSkills.length }} 项阵营技能</span>
          </header>
          <div v-if="sectHasFaction" class="ling-sect-skill-grid">
            <article v-for="skill in sectSkills" :key="pickKey(skill, ['skillKey', 'skill_key', 'id'], 'skill')" class="ling-sect-skill-card">
              <div class="ling-sect-skill-head"><div><span>阵营技能</span><h4>{{ pickText(skill, ['name', 'skillName', 'skillKey'], '未命名技能') }}</h4></div><b>{{ pickText(skill, ['learned'], 'false') === 'true' ? '已掌握' : `Lv.${pickText(skill, ['unlockLevel'], '1')}` }}</b></div>
              <p>{{ pickText(skill, ['description'], '暂无技能描述。') }}</p>
              <div class="ling-sect-skill-meta"><span>效果 {{ formatSummaryValue(skill.effects) || '待揭示' }}</span><span>消耗 {{ formatSummaryValue(skill.cost) || '无' }}</span></div>
              <div v-if="factionSkillActions(skill).length" class="ling-sect-card-actions"><button v-for="action in factionSkillActions(skill)" :key="action.label" type="button" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ actionBusy === action.label ? '处理中' : action.label }}</button></div>
              <span v-else class="ling-sect-current-badge">已掌握</span>
            </article>
          </div>
          <p v-else class="ling-sect-empty">加入阵营后才能进入星座设施。</p>
        </section>

        <section class="ling-sect-section">
          <header class="ling-sect-section-head">
            <div><span class="ling-sect-section-index">02</span><h3>阵营战记录</h3></div>
            <span>{{ sectWars.length }} 条记录</span>
          </header>
          <div v-if="sectWars.length" class="ling-sect-war-list">
            <article v-for="war in sectWars.slice(0, 8)" :key="pickKey(war, ['warNo', 'id'], 'war')" class="ling-sect-war-row">
              <div><span>{{ pickText(war, ['status'], '未知状态') }}</span><strong>{{ pickText(war, ['attackerFactionKey'], '未知') }} <i>VS</i> {{ pickText(war, ['defenderFactionKey'], '未知') }}</strong></div>
              <div class="ling-sect-war-score"><b>{{ pickText(war, ['attackerScore'], '0') }}</b><em>:</em><b>{{ pickText(war, ['defenderScore'], '0') }}</b></div>
            </article>
          </div>
          <p v-else class="ling-sect-empty">暂无阵营战记录，世界线正在等待下一场交锋。</p>
        </section>
      </template>

      <section class="ling-sect-section">
        <header class="ling-sect-section-head">
          <div><span class="ling-sect-section-index">{{ activeSubMenu === '星座设施' ? '03' : '02' }}</span><h3>阵营排行</h3></div>
          <span>{{ sectRankings.length }} 个名次</span>
        </header>
        <div v-if="sectRankings.length" class="ling-sect-ranking-list">
          <article v-for="(entry, index) in sectRankings.slice(0, 8)" :key="pickKey(entry, ['factionKey', 'faction_key', 'rank'], `ranking-${index}`)" class="ling-sect-ranking-row">
            <span class="ling-sect-ranking-rank">{{ pickText(entry, ['rank'], String(index + 1).padStart(2, '0')) }}</span>
            <div><strong>{{ pickText(entry, ['factionName', 'name', 'factionKey'], '未命名阵营') }}</strong><span>Lv.{{ pickText(entry, ['level', 'factionLevel'], '1') }} · {{ pickText(entry, ['memberCount', 'activeMembers'], '0') }} 名成员</span></div>
            <b>{{ pickText(entry, ['totalContribution', 'totalContributionScore'], '0') }} <small>贡献</small></b>
          </article>
        </div>
        <p v-else class="ling-sect-empty">暂无阵营排行。</p>
      </section>
    </section>
    <section v-else-if="panel === 'hiddenScene'" class="ling-dungeon-workbench" aria-label="隐藏场景">
      <div class="ling-dungeon-intro">
        <strong>组队挑战灾厄，获取世界线奖励。</strong>
        <small>※ 挑战次数与奖励状态由你的游戏服务端决定</small>
      </div>
      <div class="ling-dungeon-section-head"><h3>隐藏场景与灾厄</h3><span>{{ Object.keys(activeBoss).length ? '1 个战场' : '暂无战场' }}</span></div>
      <div v-if="Object.keys(activeBoss).length" class="ling-dungeon-card">
        <header class="ling-dungeon-card-head">
          <div>
            <span class="ling-dungeon-kicker">当前可进入</span>
            <h4>{{ pickText(activeBoss, ['name', 'bossName', 'boss_name'], '未命名灾厄') }}</h4>
          </div>
          <span class="ling-dungeon-limit">{{ pickText(activeBoss, ['status'], 'active') }}</span>
        </header>
        <div class="ling-dungeon-card-body">
          <p><b>战场编号</b> {{ pickText(activeBoss, ['bossNo', 'boss_no'], '未生成') }}</p>
          <p class="ling-dungeon-boss-stats">生命 {{ pickText(activeBoss, ['hp', 'health', 'currentHp'], '未知') }} · {{ pickText(activeBoss, ['canClaimReward'], '') === 'true' ? '可领取奖励' : '持续开放' }}</p>
          <p class="ling-dungeon-desc">进入战场后会记录你的贡献，探索、战斗与队伍行动会共同推进世界线。</p>
          <div class="ling-dungeon-tags"><span class="jade">世界线贡献</span><span class="gold">灾厄奖励</span><span class="blue">战报回放</span></div>
        </div>
        <footer class="ling-dungeon-actions">
          <button type="button" class="jade" @click="emit('selectPanel', 'party')">队伍大厅</button>
          <button
            v-for="action in worldBossActions(activeBoss)"
            :key="action.label"
            type="button"
            :class="{ danger: action.danger, primary: !action.danger }"
            :disabled="action.disabled || Boolean(actionBusy)"
            @click="action.onClick"
          >{{ actionBusy === action.label ? '处理中' : action.label }}</button>
        </footer>
      </div>
      <div v-else class="ling-dungeon-empty">当前没有开放中的灾厄战场，稍后再来查看。</div>
      <div class="ling-dungeon-section-head"><h3>行动提示</h3><span>星流读者</span></div>
      <div class="ling-dungeon-notes">
        <p>先在队伍大厅创建或加入队伍，再共同推进灾厄讨伐。</p>
        <p>每次攻击的结果、奖励和世界线变化都会同步到历程卷宗。</p>
      </div>
    </section>
    <section v-else-if="panel === 'worldBoss'" class="ling-instance-workbench" aria-label="灾厄战场">
      <div class="ling-instance-notice">
        <strong>灾厄规则</strong>
        <p>灾厄战场每日开放，参与贡献会进入个人记录与全服排行；奖励领取状态以服务端为准。</p>
      </div>
      <div class="ling-instance-section-head"><h3>当前战场</h3><span>{{ Object.keys(activeBoss).length ? '进行中' : '暂无' }}</span></div>
      <article v-if="Object.keys(activeBoss).length" class="ling-instance-boss-card">
        <div class="ling-instance-boss-title"><span class="ling-instance-boss-mark">☄</span><div><span>世界灾厄</span><h4>{{ pickText(activeBoss, ['name', 'bossName', 'boss_name'], '未命名灾厄') }}</h4></div><em>{{ pickText(activeBoss, ['status'], 'active') }}</em></div>
        <div class="ling-instance-boss-stats">
          <div><span>战场编号</span><b>{{ pickText(activeBoss, ['bossNo', 'boss_no'], '—') }}</b></div>
          <div><span>生命</span><b>{{ pickText(activeBoss, ['hp', 'health', 'currentHp'], '—') }}</b></div>
          <div><span>可领奖励</span><b>{{ pickText(activeBoss, ['canClaimReward'], '') === 'true' ? '是' : '否' }}</b></div>
        </div>
        <div class="ling-instance-actions">
          <button
            v-for="action in worldBossActions(activeBoss)"
            :key="action.label"
            type="button"
            :class="{ danger: action.danger, primary: !action.danger }"
            :disabled="action.disabled || Boolean(actionBusy)"
            @click="action.onClick"
          >{{ actionBusy === action.label ? '处理中' : action.label }}</button>
        </div>
      </article>
      <p v-else class="ling-instance-empty">当前没有活跃灾厄。</p>
      <div class="ling-instance-section-head"><h3>伤害排行</h3><span>{{ bossRankings.length }} 位记录</span></div>
      <div class="ling-instance-ranking">
        <article v-for="(entry, index) in bossRankings.slice(0, 12)" :key="pickKey(entry, ['id', 'playerId', 'playerName'], `boss-rank-${index}`)">
          <span class="ling-instance-rank-no">{{ pickNumber(entry, ['rank'], index + 1) }}</span>
          <strong>{{ pickText(entry, ['playerName', 'player_name', 'name'], '未命名玩家') }}</strong>
          <span>{{ pickText(entry, ['damage', 'totalDamage'], '0') }} 伤害</span>
        </article>
        <p v-if="bossRankings.length === 0" class="ling-instance-empty">暂无贡献排行。</p>
      </div>
      <div class="ling-instance-section-head"><h3>我的参与</h3><span>{{ bossParticipation.length }} 条</span></div>
      <div class="ling-instance-history">
        <article v-for="(entry, index) in bossParticipation.slice(0, 5)" :key="pickKey(entry, ['id', 'bossNo'], `participation-${index}`)">
          <div><strong>{{ pickText(entry, ['bossName', 'name', 'bossNo'], '灾厄记录') }}</strong><span>伤害 {{ pickText(entry, ['damage'], '0') }}</span></div>
          <div class="ling-instance-history-foot"><span>{{ pickText(entry, ['rewardStatus', 'status'], '已记录') }}</span><button v-for="action in worldBossParticipationActions(entry)" :key="action.label" type="button" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ action.label }}</button></div>
        </article>
        <p v-if="bossParticipation.length === 0" class="ling-instance-empty">暂无参与记录。</p>
      </div>
      <div v-if="bossHistory.length" class="ling-instance-history-log"><span>最近战报</span><p v-for="(entry, index) in bossHistory.slice(0, 3)" :key="pickKey(entry, ['id', 'createdAt'], `boss-history-${index}`)">{{ pickText(entry, ['message', 'content', 'result'], '战场记录已写入历程。') }}</p></div>
    </section>
    <section v-else-if="panel === 'ranking'" class="ling-ranking-workbench" aria-label="星流排行榜">
      <div class="ling-ranking-tools">
        <label class="ling-ranking-picker">
          <span>选择榜单</span>
          <select v-model="rankingBoard" aria-label="选择榜单">
            <option>成长阶</option>
            <option>PK评分</option>
            <option>近7日PK表现</option>
            <option>近7日探索行动</option>
            <option>近7日故事发现</option>
          </select>
        </label>
        <div v-if="rankingBoard === '成长阶'" class="ling-ranking-scope" role="tablist" aria-label="成长阶排行范围">
          <button v-for="scope in rankingScopes" :key="scope" type="button" :class="{ active: rankingScope === scope }" @click="setRankingScope(scope)">{{ scope }}</button>
        </div>
        <label class="ling-ranking-search">
          <span aria-hidden="true">⌕</span>
          <input v-model="rankingKeyword" type="search" placeholder="搜索玩家或角色名" aria-label="搜索玩家或角色名" />
        </label>
      </div>
      <div class="ling-ranking-summary">
        <span>星流排行 · {{ rankingBoard }}</span>
        <strong>显示 {{ Math.min(rankingVisibleLimit, rankingRows.length) }} / {{ rankingRows.length }} 位角色</strong>
        <small v-if="rankingBoard === 'PK评分'" class="ling-ranking-personal">
          我的 PK 定级：{{ truthyRecordValue(asRecord(panelData.myRank), ['ranked'])
            ? `第${pickText(asRecord(panelData.myRank), ['rank'], '—')} / ${pickText(asRecord(panelData.myRank), ['totalPlayers'], '—')} 名 · ${pickText(asRecord(panelData.myRank), ['rating'], '1000')}分 · 胜${pickText(asRecord(panelData.myRank), ['wins'], '0')}负${pickText(asRecord(panelData.myRank), ['losses'], '0')}`
            : '尚未定级 · 完成首次玩家挑战后进入正式排行' }}
        </small>
        <small v-else-if="rankingBoard === '成长阶'" class="ling-ranking-personal">
          按成长阶、等级、PK 评分排序；搜索与筛选范围为前 200 名
        </small>
        <small v-else-if="rankingBoard === '近7日故事发现'" class="ling-ranking-personal">
          每种故事只按近 7 天内的首次完成计 1；仅公开人数与数量，不公开故事、选项或个人结局。
        </small>
        <small v-else-if="rankingBoard === '近7日探索行动'" class="ling-ranking-personal">
          按近 7 天内服务端接受的探索请求去重计数；触发待战斗遭遇也算一次探索，不代表战斗胜利或战力。
        </small>
        <small v-else class="ling-ranking-personal">
          最近 7 天所有已结算 PK 的实际定级分净变化；只作近期表现展示，不重置长期评分、不发奖励。
        </small>
        <small v-if="rankingBoard === '近7日故事发现'" class="ling-ranking-personal">
          <template v-if="storyDiscoveryPositionState === 'ranked'">
            我的名次：第 {{ pickText(asRecord(panelData.storyDiscoveryPosition), ['rank'], '—') }} / {{ pickText(asRecord(panelData.storyDiscoveryPosition), ['participantCount'], '—') }} · 已发现 {{ pickText(asRecord(panelData.storyDiscoveryPosition), ['discoveryCount'], '0') }} 种
          </template>
          <template v-else-if="storyDiscoveryPositionState === 'unavailable'">我的故事榜位暂时无法读取，请稍后重试。</template>
          <template v-else-if="storyDiscoveryPositionState === 'unranked'">近 7 日已有 {{ pickText(asRecord(panelData.storyDiscoveryPosition), ['discoveryCount'], '0') }} 种发现，个人名次暂未返回。</template>
          <template v-else>尚未进入此榜 · 近 7 日还没有首次完成的新故事</template>
          <span v-if="storyDiscoveryPositionState !== 'unavailable' && pickNumber(asRecord(panelData.storyDiscoveryPosition), ['nextRankDiscoveryCount'], 0) > 0">
            · 再发现 {{ pickText(asRecord(panelData.storyDiscoveryPosition), ['discoveriesToNextRank'], '0') }} 种，可追平 {{ pickText(asRecord(panelData.storyDiscoveryPosition), ['nextRankDiscoveryCount'], '0') }} 种的更高名次档
          </span>
          <span v-else-if="storyDiscoveryPositionState === 'ranked'">· 当前已达最高分档，继续发现可巩固领先</span>
          <span v-else-if="storyDiscoveryPositionState === 'no-discoveries'">· 首次完成故事后即可进入排行</span>
        </small>
        <small v-if="rankingBoard === '近7日PK表现'" class="ling-ranking-personal">
          <template v-if="pkPerformancePositionState === 'participated'">
            我的近 7 日表现：第 {{ pickText(asRecord(panelData.pkPerformancePosition), ['rank'], '—') }} / {{ pickText(asRecord(panelData.pkPerformancePosition), ['participantCount'], '—') }} · 定级净变化 {{ formatRatingDelta(pick(asRecord(panelData.pkPerformancePosition), ['ratingDelta'])) }} 分 · {{ pickText(asRecord(panelData.pkPerformancePosition), ['winCount'], '0') }} 胜 / {{ pickText(asRecord(panelData.pkPerformancePosition), ['matchCount'], '0') }} 场
          </template>
          <template v-else-if="pkPerformancePositionState === 'unavailable'">我的近 7 日 PK 位置暂时无法读取，请稍后重试。</template>
          <template v-else>近 7 日还没有已结算的 PK 战报 · 完成一场后即可进入近期榜</template>
          <span v-if="pkPerformancePositionState === 'participated' && pkPerformanceNextScore !== null && pkPerformanceNextScore !== undefined">
            · 距离更高分数档还差 {{ pickText(asRecord(panelData.pkPerformancePosition), ['pointsToNextRank'], '0') }} 分（{{ formatRatingDelta(pkPerformanceNextScore) }} 分）
          </span>
          <span v-else-if="pkPerformancePositionState === 'participated'">· 当前已达最高分数档</span>
        </small>
        <small v-if="rankingBoard === '近7日探索行动'" class="ling-ranking-personal">
          <template v-if="exploreActionPositionState === 'participated'">
            我的近 7 日探索：第 {{ pickText(asRecord(panelData.exploreActionPosition), ['rank'], '—') }} / {{ pickText(asRecord(panelData.exploreActionPosition), ['participantCount'], '—') }} · {{ pickText(asRecord(panelData.exploreActionPosition), ['actionCount'], '0') }} 次行动
          </template>
          <template v-else-if="exploreActionPositionState === 'unavailable'">我的近 7 日探索位置暂时无法读取，请稍后重试。</template>
          <template v-else>近 7 日还没有已接受的探索行动 · 执行一次探索后即可进入榜单</template>
          <span v-if="exploreActionPositionState === 'participated' && exploreActionNextScore !== null && exploreActionNextScore !== undefined">
            · 再执行 {{ pickText(asRecord(panelData.exploreActionPosition), ['actionsToNextRank'], '0') }} 次，可追平 {{ pickText(asRecord(panelData.exploreActionPosition), ['nextRankActionCount'], '0') }} 次的更高名次档
          </span>
          <span v-else-if="exploreActionPositionState === 'participated'">· 当前已达最高次数档</span>
        </small>
        <small v-if="rankingBoard === '成长阶'" class="ling-ranking-personal">
          我的全服位次：第 {{ pickText(asRecord(panelData.avatarRankInfo), ['rankPosition'], '—') }} / {{ pickText(asRecord(panelData.avatarRankInfo), ['totalPlayers'], '—') }} · 当前 {{ pickText(asRecord(panelData.avatarRankInfo).currentRank, ['displayName'], '未知成长阶') }}
          <span v-if="truthyRecordValue(asRecord(panelData.avatarRankInfo), ['maxRank', 'isMaxRank'])">· 已达最高成长阶</span>
          <span v-else>· 下一阶：{{ pickText(asRecord(panelData.avatarRankInfo), ['nextDisplayName', 'nextRankName'], '待解锁') }}</span>
        </small>
        <small
          v-if="rankingBoard === '成长阶' && avatarRankRequirements.length"
          class="ling-ranking-personal ling-ranking-goal"
          aria-label="下一成长阶进度"
        >
          <template v-if="nextAvatarRankRequirement">
            升阶还差 {{ pendingAvatarRankRequirements.length }} 项 · 当前目标：{{ pickText(nextAvatarRankRequirement, ['label'], '成长条件') }} {{ pickNumber(nextAvatarRankRequirement, ['current'], 0) }} / {{ pickNumber(nextAvatarRankRequirement, ['required'], 0) }}
          </template>
          <template v-else>升阶条件已满足 · 可从角色状态栏确认晋升</template>
        </small>
      </div>
        <div class="ling-ranking-list">
          <article v-for="(entry, index) in rankingRows.slice(0, rankingVisibleLimit)" :key="pickKey(entry, ['id', 'playerId', 'playerName'], `ranking-${index}`)" :class="['ling-ranking-row', { 'is-top': index < 3 && (rankingBoard === '成长阶' || rankingBoard === '近7日故事发现' || rankingBoard === '近7日PK表现' || rankingBoard === '近7日探索行动' || truthyRecordValue(entry, ['ranked'])), 'is-self': (rankingBoard === '近7日故事发现' || rankingBoard === '近7日PK表现' || rankingBoard === '近7日探索行动') && isMyRanking(entry) }]">
          <span class="ling-ranking-place">{{ rankingBoard === 'PK评分' && !truthyRecordValue(entry, ['ranked']) ? '—' : pickText(entry, ['rank'], String(index + 1)) }}</span>
          <div class="ling-ranking-avatar" aria-hidden="true">{{ chatInitial({ senderName: pickText(entry, ['player_name', 'playerName', 'name'], '灵') }) }}</div>
          <div class="ling-ranking-copy">
            <strong>{{ pickText(entry, ['player_name', 'playerName', 'name'], '未命名玩家') }}</strong>
            <span v-if="rankingBoard === '成长阶'">成长阶 {{ pickText(entry, ['avatarRank'], 'F') }} · 等级 {{ pickText(entry, ['level'], '1') }}</span>
            <span v-else-if="rankingBoard === '近7日故事发现'">近 7 日首次完成的故事种类</span>
            <span v-else-if="rankingBoard === '近7日PK表现'">近 7 日净变化 {{ formatRatingDelta(pick(entry, ['ratingDelta'])) }} 分 · {{ pickText(entry, ['winCount'], '0') }} 胜 / {{ pickText(entry, ['matchCount'], '0') }} 场</span>
            <span v-else-if="rankingBoard === '近7日探索行动'">近 7 日服务端接受的探索请求</span>
            <span v-else-if="!truthyRecordValue(entry, ['ranked'])">尚未定级 · 1000 初始分 · 完成玩家挑战后进入正式排行</span>
            <span v-else>等级 {{ pickText(entry, ['level'], '1') }} · 胜 {{ pickText(entry, ['wins'], '0') }} / 负 {{ pickText(entry, ['losses'], '0') }}</span>
          </div>
          <div class="ling-ranking-score">
          <strong>{{ rankingBoard === '近7日PK表现'
            ? formatRatingDelta(pick(entry, ['ratingDelta']))
            : pickText(entry, rankingBoard === '成长阶' ? ['avatarRank'] : rankingBoard === '近7日故事发现' ? ['discoveryCount'] : rankingBoard === '近7日探索行动' ? ['actionCount'] : ['rating'], rankingBoard === '成长阶' ? 'F' : rankingBoard === '近7日故事发现' ? '0' : rankingBoard === '近7日探索行动' ? '0' : '1000') }}</strong>
          <span>{{ rankingBoard === '成长阶' ? '当前成长阶' : rankingBoard === '近7日故事发现' ? '故事种类' : rankingBoard === '近7日PK表现' ? '近7日净变化' : rankingBoard === '近7日探索行动' ? '近7日行动数' : 'PK评分' }}</span>
          </div>
        </article>
        <p v-if="rankingRows.length === 0" class="ling-ranking-empty">
          {{ rankingBoard === '近7日故事发现'
            ? storyDiscoveryEmptyStateMessage(truthyRecordValue(panelData, ['storyDiscoveryRankingsUnavailable']))
            : rankingBoard === '近7日PK表现'
              ? pkPerformanceEmptyStateMessage(truthyRecordValue(panelData, ['pkPerformanceRankingsUnavailable']))
              : rankingBoard === '近7日探索行动'
                ? exploreActionEmptyStateMessage(truthyRecordValue(panelData, ['exploreActionRankingsUnavailable']))
            : '没有符合条件的榜单记录。' }}
        </p>
      </div>
      <button
        v-if="rankingRows.length > rankingVisibleLimit"
        type="button"
        class="ling-ranking-more"
        @click="rankingVisibleLimit = Math.min(rankingVisibleLimit + 30, 200)"
      >加载更多 · 还有 {{ rankingRows.length - rankingVisibleLimit }} 位</button>
      <section v-if="rankingBoard === 'PK评分'" class="ling-ranking-nearby" aria-label="身边的定级玩家">
        <div class="ling-ranking-nearby-head">
          <div><strong>身边的对手</strong><span>按 PK 评分排列 · 前后各 2 位</span></div>
          <small>{{ nearbyRankingRows.length }} 位</small>
        </div>
        <div v-if="nearbyRankingRows.length" class="ling-ranking-list">
          <article
            v-for="(entry, index) in nearbyRankingRows"
            :key="pickKey(entry, ['playerId', 'player_id'], `nearby-ranking-${index}`)"
            :class="['ling-ranking-row', { 'is-self': isMyRanking(entry) }]"
          >
            <span class="ling-ranking-place">{{ pickText(entry, ['rank'], '—') }}</span>
            <div class="ling-ranking-avatar" aria-hidden="true">{{ chatInitial({ senderName: pickText(entry, ['playerName', 'player_name'], '灵') }) }}</div>
            <div class="ling-ranking-copy">
              <strong>{{ pickText(entry, ['playerName', 'player_name'], '未命名玩家') }}</strong>
              <span>{{ rankingNearbyLabel(entry) }} · 胜{{ pickText(entry, ['wins'], '0') }}负{{ pickText(entry, ['losses'], '0') }}</span>
            </div>
            <div class="ling-ranking-score"><strong>{{ pickText(entry, ['rating'], '1000') }}</strong><span>PK评分</span></div>
          </article>
        </div>
        <p v-else-if="panelQuery.isSuccess.value && !truthyRecordValue(asRecord(panelData.myRank), ['ranked'])" class="ling-ranking-nearby-empty">
          还没有正式名次。完成首次玩家挑战后，这里会显示你上下各两位的追赶目标。
        </p>
        <p v-else-if="panelQuery.isSuccess.value" class="ling-ranking-nearby-empty">名次详情暂不可用，请稍后刷新。</p>
      </section>
    </section>
    <section v-else-if="panel === 'pk'" class="ling-pvp-workbench" aria-label="化身战场挑战">
      <header class="ling-pvp-hero">
        <div>
          <span class="ling-pvp-eyebrow">星流读者 · 化身战场</span>
          <h3>玩家挑战</h3>
          <p>查看可挑战玩家并发起对战；生死模式可能影响角色状态，请先确认风险。</p>
        </div>
        <div class="ling-pvp-area">
          <span>当前场域</span>
          <strong>{{ pickText(asRecord(gameStore.player), ['currentLocationName', 'current_location_name', 'currentLocationKey', 'current_location'], '未知场域') }}</strong>
          <small>{{ pvpOpponentRows.length }} 位可挑战玩家</small>
        </div>
        <button type="button" class="ling-pvp-refresh" @click="panelQuery.refetch()">刷新</button>
      </header>

      <div class="ling-pvp-chrome">
        <nav class="ling-pvp-tabs" role="tablist" aria-label="挑战分类">
          <button type="button" role="tab" :aria-selected="pvpTab === 'opponents'" :class="{ active: pvpTab === 'opponents' }" @click="selectPvpTab('opponents')">同域玩家</button>
          <button type="button" role="tab" :aria-selected="pvpTab === 'records'" :class="{ active: pvpTab === 'records' }" @click="selectPvpTab('records')">战报记录</button>
        </nav>
        <div class="ling-pvp-toolbar">
          <label class="ling-pvp-search"><span aria-hidden="true">⌕</span><input v-model="pvpSearch" type="search" placeholder="搜索玩家或战报…" aria-label="搜索玩家挑战" /><button v-if="pvpSearch" type="button" aria-label="清空挑战搜索" @click="pvpSearch = ''">×</button></label>
          <div v-if="pvpTab === 'opponents'" class="ling-pvp-mode" role="tablist" aria-label="挑战模式"><button type="button" :class="{ active: pvpMode === 'spar' }" @click="pvpMode = 'spar'">练习挑战</button><button type="button" :class="{ active: pvpMode === 'deathmatch' }" @click="pvpMode = 'deathmatch'">生死对决</button></div>
          <button type="button" class="ling-pvp-reload" @click="panelQuery.refetch()">重载</button>
        </div>
      </div>

      <section class="ling-pvp-list-section">
        <div class="ling-pvp-section-head"><div><span>01</span><h4>{{ pvpTab === 'opponents' ? '玩家列表' : '最近战报' }}</h4></div><small>{{ pvpTab === 'opponents' ? pvpOpponentRows.length : pvpRecordRows.length }} 条记录</small></div>
        <div v-if="pvpTab === 'opponents' && pvpOpponentRows.length" class="ling-pvp-list">
          <article v-for="(entry, index) in pvpOpponentRows" :key="pickKey(entry, ['id', 'playerId', 'player_id'], `pvp-${index}`)" class="ling-pvp-card">
            <div class="ling-pvp-avatar">{{ chatInitial({ senderName: pickText(entry, ['playerName', 'player_name', 'name'], '玩') }) }}</div>
          <div class="ling-pvp-copy"><strong>{{ pickText(entry, ['playerName', 'player_name', 'name'], '未命名玩家') }}</strong><span>成长阶 {{ pickText(entry, ['avatarRank', 'rank', 'storyGrade', 'realm'], '未知') }} · Lv.{{ pickNumber(entry, ['level'], 1) }}</span><small>战力 {{ pickText(entry, ['combatPower', 'combat_power', 'power'], '—') }}</small><small v-if="truthyRecordValue(entry, ['ranked'])">ELO {{ pickText(entry, ['rating'], '1000') }}</small><small v-else>未定级 · 初始 ELO 1000</small><small>{{ pickText(entry, ['matchLabel', 'match_label'], '匹配信息待更新') }} · 战力差 {{ pickNumber(entry, ['powerGapPercent', 'power_gap_percent'], 0) }}%</small></div>
            <div class="ling-pvp-actions"><button v-for="action in pvpChallengeActions(entry)" :key="action.label" type="button" :class="{ danger: pvpMode === 'deathmatch' }" :disabled="action.disabled || Boolean(actionBusy)" @click="action.onClick">{{ actionBusy === action.label ? '处理中' : action.label }}</button></div>
          </article>
        </div>
        <div v-else-if="pvpTab === 'records' && pvpRecordRows.length" class="ling-pvp-list">
          <article v-for="(entry, index) in pvpRecordRows" :key="pickKey(entry, ['id', 'challengeId', 'createdAt'], `pvp-record-${index}`)" class="ling-pvp-card is-record">
            <div class="ling-pvp-avatar">战</div>
            <div class="ling-pvp-copy"><strong>{{ pickText(entry, ['opponentName', 'opponent_name', 'defenderName', 'attackerName', 'winnerName'], '未知对手') }}</strong><span>{{ pvpModeLabel(pick(entry, ['mode'])) }} · {{ pickText(entry, ['result', 'status'], '已记录') }}</span><small>{{ pickText(entry, ['winnerName', 'winner_name'], '') }} <template v-if="pickText(entry, ['createdAt', 'created_at'], '')">· {{ formatPanelTime(pick(entry, ['createdAt', 'created_at'])) }}</template></small></div>
            <b>{{ pickText(entry, ['totalRounds', 'rounds'], '—') }}<small>回合</small></b>
          </article>
        </div>
        <p v-else class="ling-pvp-empty"><strong>暂无{{ pvpTab === 'opponents' ? '可挑战的玩家' : '挑战战报' }}</strong><span>{{ pvpTab === 'opponents' ? '刷新列表，或稍后再查看在线玩家。' : '完成一次挑战后，战报会留在这里。' }}</span></p>
      </section>
      <p class="ling-pvp-note">练习对战不会损失物品；生死对战会改变战斗结果并受服务端规则限制。所有挑战均通过星流读者自己的 PK 接口提交。</p>
    </section>
    <section v-else-if="referenceWorkbenchPanels.includes(panel)" class="ling-reference-workbench" :aria-label="panelTitle">
      <div class="ling-reference-toolbar" role="toolbar" :aria-label="`${panelTitle}工具栏`">
        <span class="ling-reference-toolbar-label">当前分柜</span>
        <strong>{{ activeSubMenu || '总览' }}</strong>
        <span class="ling-reference-toolbar-spacer"></span>
        <span class="ling-reference-toolbar-count">{{ blocks.length }} 个卷宗</span>
      </div>

      <div class="ling-reference-sections">
        <article v-for="(block, blockIndex) in blocks" :key="block.title" class="ling-reference-section">
          <header class="ling-reference-section-head">
            <div class="ling-reference-section-title">
              <span class="ling-reference-section-index">{{ String(blockIndex + 1).padStart(2, '0') }}</span>
              <h4>{{ block.title }}</h4>
            </div>
            <p v-if="block.description">{{ block.description }}</p>
          </header>

          <div v-if="block.stats?.length" class="ling-reference-stat-grid">
            <div v-for="stat in block.stats" :key="`${block.title}-${stat.label}`" class="ling-reference-stat">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
              <div v-if="parseProgress(stat.label, String(stat.value))" class="ling-reference-progress">
                <i :class="parseProgress(stat.label, String(stat.value))!.barClass" :style="{ width: parseProgress(stat.label, String(stat.value))!.pct + '%' }"></i>
              </div>
            </div>
          </div>

          <div v-if="block.entries?.length" class="ling-reference-entry-grid">
            <article
              v-for="entry in block.entries"
              :key="entry.id"
              :class="['ling-reference-entry', `is-${entryRarityClass(entry) || 'plain'}`]"
            >
              <div class="ling-reference-entry-top">
                <div class="ling-reference-entry-copy">
                  <strong>{{ entry.title }}</strong>
                  <p v-if="entry.subtitle">{{ entry.subtitle }}</p>
                </div>
                <span class="ling-reference-entry-seal" aria-hidden="true">◇</span>
              </div>
              <div v-if="entry.meta?.length" class="ling-reference-entry-meta">
                <span v-for="meta in entry.meta" :key="meta">{{ meta }}</span>
              </div>
              <div v-if="entry.actions?.length" class="ling-reference-entry-actions">
                <button
                  v-for="action in entry.actions"
                  :key="action.label"
                  type="button"
                  :class="{ danger: action.danger, primary: !action.danger }"
                  :disabled="action.disabled || Boolean(actionBusy)"
                  @click="action.onClick"
                >
                  {{ actionBusy === action.label ? '处理中' : action.label }}
                </button>
              </div>
            </article>
          </div>

          <p v-if="(!block.entries || block.entries.length === 0) && !block.stats?.length" class="ling-reference-empty">
            {{ block.emptyText || '暂无卷宗记录' }}
          </p>
        </article>
      </div>
    </section>
    <div v-else class="ling-panel-content space-y-4">
      <article v-for="(block, blockIndex) in blocks" :key="block.title" class="ling-data-block rounded-md border p-4 transition-shadow duration-200 hover:shadow-[0_0_16px_var(--color-star-glow)]" style="border-color: var(--color-border); background: var(--color-card);">
        <div class="ling-data-block-heading mb-3 flex items-baseline justify-between gap-3 border-b pb-2.5" style="border-color: var(--color-border);">
          <div class="ling-block-title-wrap">
            <span class="ling-block-index">{{ String(blockIndex + 1).padStart(2, '0') }}</span>
            <h3 class="text-sm font-semibold tracking-wide" style="color: var(--color-star-bright); text-shadow: 0 0 8px var(--color-star-glow);">{{ block.title }}</h3>
          </div>
          <p v-if="block.description" class="text-xs" style="color: var(--color-muted);">{{ block.description }}</p>
        </div>

        <div v-if="block.stats?.length" class="ling-stat-grid mb-3 grid grid-cols-2 gap-2 text-xs md:grid-cols-4">
          <div v-for="stat in block.stats" :key="`${block.title}-${stat.label}`" class="ling-stat-card rounded-md border p-2.5" style="border-color: var(--color-border); background: var(--color-control);">
            <div class="ling-stat-label" style="color: var(--color-muted);">{{ stat.label }}</div>
            <div class="ling-stat-value mt-1 break-words" style="color: var(--color-spirit);">{{ stat.value }}</div>
            <div v-if="parseProgress(stat.label, String(stat.value))" class="ling-progress-bar">
              <div class="ling-progress-fill" :class="parseProgress(stat.label, String(stat.value))!.barClass" :style="{ width: parseProgress(stat.label, String(stat.value))!.pct + '%' }"></div>
            </div>
          </div>
        </div>

        <p v-if="(!block.entries || block.entries.length === 0) && !block.stats?.length" class="text-sm" style="color: var(--color-muted);">{{ block.emptyText || '暂无数据' }}</p>

        <div v-if="block.entries?.length" class="ling-entry-list space-y-2">
          <div v-for="entry in block.entries" :key="entry.id" :class="[
            'ling-entry-card rounded-md border p-3 text-sm transition-colors duration-150 hover:bg-white/[0.02]',
            { 'ling-entry-card--r1': entryRarityClass(entry) === 'r1', 'ling-entry-card--r2': entryRarityClass(entry) === 'r2', 'ling-entry-card--r3': entryRarityClass(entry) === 'r3', 'ling-entry-card--r4': entryRarityClass(entry) === 'r4', 'ling-entry-card--r5': entryRarityClass(entry) === 'r5' },
          ]" style="border-color: var(--color-border); background: var(--color-control);">
            <div class="ling-entry-row flex flex-wrap items-start justify-between gap-3">
              <div class="ling-entry-main min-w-0 flex-1">
                <div class="ling-entry-title break-words text-ink">{{ entry.title }}</div>
                <p v-if="entry.subtitle" class="mt-1 break-words text-xs text-muted">{{ entry.subtitle }}</p>
                <div v-if="entry.meta?.length" class="ling-entry-meta mt-2 flex flex-wrap gap-2 text-xs text-muted">
                  <span v-for="meta in entry.meta" :key="meta" :class="['rounded border px-2 py-1 text-xs', getDurabilityClass(meta)]" style="border-color: var(--color-border); color: var(--color-text-dim);">{{ meta }}</span>
                </div>
              </div>
              <div v-if="entry.actions?.length" class="ling-entry-actions flex flex-wrap gap-2">
                <button
                  v-for="action in entry.actions"
                  :key="action.label"
                  type="button"
                  :disabled="action.disabled || Boolean(actionBusy)"
                  :class="[
                    'ling-action-btn rounded border px-3 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-40',
                    action.danger ? 'ling-action-btn--danger border-danger/50 text-danger' : 'border-line text-muted hover:text-star',
                  ]"
                  @click="action.onClick"
                >
                  {{ actionBusy === action.label ? '处理中' : action.label }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </article>

      <button v-if="panel === 'hiddenScene'" class="rounded border border-line px-3 py-2 text-sm text-muted hover:text-star" @click="emit('selectPanel', 'worldBoss')">前往灾厄战斗</button>

      <div v-if="showGhostDialog && panel === 'explore'" class="mt-4 rounded-md border p-4" style="border-color: var(--color-spirit-border); background: var(--color-spirit-glow);">
        <h3 class="text-sm font-semibold mb-3" style="color: var(--color-spirit); text-shadow: var(--glow-spirit);">NPC残影</h3>
        <p class="text-sm text-ink mb-4 whitespace-pre-wrap">{{ ghostNarrative }}</p>
        <div v-if="ghostChoices.length" class="flex flex-wrap gap-2">
          <button v-for="c in ghostChoices" :key="c.index" class="rounded border border-line px-3 py-1 text-sm text-muted hover:text-star" :disabled="Boolean(actionBusy)" @click="selectGhostChoice(c.index)">{{ c.text }}</button>
        </div>
        <button v-else class="rounded border border-line px-3 py-1 text-sm text-muted" @click="showGhostDialog = false">关闭</button>
      </div>
    </div>
    </div>
  </section>
</template>
