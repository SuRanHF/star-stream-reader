<script setup lang="ts">
import { computed, inject, ref, watch, onBeforeUnmount } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { useRouter } from 'vue-router';
import { broadcastApi } from '@/api/broadcastApi';
import { bountyApi } from '@/api/bountyApi';
import { chatApi } from '@/api/chatApi';
import { combatApi } from '@/api/combatApi';
import { endingApi } from '@/api/endingApi';
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
import { realtimeClient } from '@/realtime/realtimeClient';
import { useAuthStore } from '@/stores/authStore';
import { useGameStore } from '@/stores/gameStore';
import type { ApiRecord } from '@/types/api';
import {
  asArray,
  asRecord,
  confirmAction,
  displayMeta,
  formatCount,
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
const feedbackText = ref('');
const feedbackType = ref('bug');
const friendKeyword = ref('');
const bountyMonsterKey = ref('');
const bountyLocationKey = ref('');
const bountySharePercent = ref(50);
const factionContributionType = ref('coins');
const factionContributionValue = ref(100);
const ghostNarrative = ref('');
const ghostChoices = ref<Array<{ index: number; text: string }>>([]);
const ghostNodeIndex = ref(0);
const ghostKey = ref('');
const showGhostDialog = ref(false);

const playerId = computed(() => Number(gameStore.player?.id || gameStore.player?.playerId || 0));

const panelQuery = useQuery({
  queryKey: computed(() => ['feature-panel', props.panel, playerId.value, friendKeyword.value]),
  queryFn: () => loadPanel(props.panel, playerId.value),
  enabled: computed(() => canLoadPanel(props.panel, playerId.value)),
  retry: 1,
});

const panelTitle = computed(() => titleFor(props.panel));
const panelSubtitle = computed(() => subtitleFor(props.panel));
const blocks = computed(() => buildBlocks(props.panel, asRecord(panelQuery.data.value)));
const secondaryMenus = computed(() => subMenusFor(props.panel));
const activeSubMenus = ref<Record<string, string>>({});
const activeSubMenu = computed(() => activeSubMenus.value[props.panel] || secondaryMenus.value[0] || '');

watch(() => props.panel, () => {
  activeSubMenus.value = {};
});

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
    skills: '星痕传承',
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
    history: '日志、任务、战报记录。',
    underworld: '冥界与复活入口。',
    chat: '星流频道消息与发言。',
    trade: '商店、市场、竞拍、交易记录。',
    skills: '技能浏览、学习和传承。',
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
    ranking: ['化身位阶', '星币资产', '战力指数', '击杀记录', '灾厄贡献', '频道热度'],
    history: ['全部记录', '收益记录', '战斗记录', '场景记录', '交易记录'],
    underworld: ['死亡名单', '复活协议', '援救委托', '异常记录'],
    chat: ['星流', '阵营', '队伍'],
    trade: ['商品清单', '求购信标', '我的摊位', '关注清单', '成交记录', '竞价频道', '寄售中', '出价记录', '补给入口', '秘匣入口'],
    skills: ['星痕图谱', '可解锁星痕', '已掌握星痕', '技能加成'],
    faction: ['阵营概览', '星座设施'],
    pk: ['挑战列表', '模拟战', '化身榜单', '战报记录', '胜负预测', '战场奖励', '规则'],
    quests: ['主线任务', '每日委托', '世界线成就', '快速处理', '模拟场景'],
    friends: ['全部', '同伴列表', '待处理申请', '搜索同伴'],
    titles: ['档案检索', '材料档案', '补给档案', '装备档案', '技能档案'],
    inventory: ['全部物资', '武器', '防具', '消耗品', '材料'],
    synthesis: ['全部配方', '武器合成', '防具合成', '消耗品合成'],
    equipment: ['全部装备', '已穿戴', '需修理'],
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
      return { locations: asArray(await exploreApi.getLocations(id)) };
    case 'hiddenScene':
      return {
        active: await worldBossApi.getActiveWorldBoss(id),
        participation: await worldBossApi.getMyParticipation(id),
        summary: await worldBossApi.getWorldBossSummary(id),
      };
    case 'ranking':
      return {
        rankings: await rankingApi.getRankings(),
        myRank: await rankingApi.getMyRank(id),
        avatarRankings: await rankingApi.getAvatarLeaderboard(),
      };
    case 'history':
      return {
        logs: gameStore.recentLogs,
        quests: await questApi.getQuests(id),
        summary: await questApi.getQuestSummary(id),
        endings: await safeList(() => endingApi.getEndings(id).then(r => asArray(asRecord(r).endings || [])), '结局接口暂不可用'),
        storyLog: await safeList(
          () => exploreApi.getStoryLog(id).then(r => asRecord(r).story_log || []),
          '故事记录接口暂不可用',
        ),
      };
    case 'underworld':
      return { deadList: await playerApi.getDeadList() };
    case 'chat':
      return { recent: await chatApi.getRecentMessages() };
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
      return {
        factions: await factionApi.getFactions(id),
        my: myFaction,
        summary: await factionApi.getFactionSummary(id),
        buff: await factionApi.getFactionBuff(id),
        rankings: await factionApi.getRankings(),
        wars: await safeList(() => factionApi.getFactionWars(), '阵营战接口暂不可用'),
        skills: factionKey ? await safeList(() => factionApi.getFactionSkills(factionKey), '阵营技能接口暂不可用') : [],
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
    case 'equipment':
      return {
        equipment: await equipmentApi.getPlayerEquipment(id),
        equipped: await equipmentApi.getEquipped(id),
        bonus: await equipmentApi.getBonus(id),
      };
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
      return {
        active,
        rankings: bossNo ? await worldBossApi.getRankings(bossNo) : [],
        summary: await worldBossApi.getWorldBossSummary(id),
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
        entriesBlock('化身位阶排行', asArray(data.avatarRankings), ['player_name', 'playerName', 'name'], [['排名', 'avatarRank'], ['威望', 'prestige']], '暂无化身位阶排行'),
      ];
    case 'history': {
      const histFilter = activeSubMenu.value;
      let logs = asArray(data.logs);
      if (histFilter === '收益记录') logs = logs.filter(e => ['income', 'reward', '收益'].includes(pickText(e, ['type'], '')));
      else if (histFilter === '战斗记录') logs = logs.filter(e => ['battle', 'combat', 'pk', '战斗'].includes(pickText(e, ['type'], '')));
      else if (histFilter === '场景记录') logs = logs.filter(e => ['explore', 'scene', '场景'].includes(pickText(e, ['type'], '')));
      else if (histFilter === '交易记录') logs = logs.filter(e => ['trade', '交易'].includes(pickText(e, ['type'], '')));
      const storyLogEntries = (asArray(data.storyLog) as ApiRecord[]).flatMap((grp: ApiRecord) => {
        const locName = pickText(grp, ['location_name', 'locationName'], pickText(grp, ['location_key', 'locationKey'], '未知'));
        const stories = asArray(grp.stories);
        return stories.map((s: ApiRecord, i: number) => ({
          id: pickText(s, ['event_key', 'eventKey'], `${grp.location_key}-${i}`),
          title: pickText(s, ['event_name', 'eventName'], '未命名事件'),
          subtitle: `选择: ${pickText(s, ['choice_label', 'choiceLabel'], '无')} | ${pickText(s, ['consequence_text', 'consequenceText'], '')}`,
          meta: [locName, pickText(s, ['created_at', 'createdAt'], '')],
          actions: [] as PanelAction[],
        }));
      });
      return [
        entriesBlock('最近日志', logs, ['message'], [['类型', 'type'], ['时间', 'createdAt']], histFilter !== '全部记录' ? `没有${histFilter}` : '暂无日志'),
        ...(storyLogEntries.length ? [{ title: '故事记录', emptyText: '暂无故事记录', entries: storyLogEntries, stats: undefined as undefined }] as PanelBlock[] : []),
        summaryBlock('任务摘要', asRecord(data.summary)),
        entriesBlock('任务记录', asArray(data.quests), ['questName', 'name', 'title', 'questKey'], [['状态', 'status'], ['进度', 'progress']], '暂无任务'),
        entriesBlock('结局记录', asArray(data.endings), ['name', 'endingKey'], [['是否达成', 'fulfilled'], ['优先级', 'priority'], ['描述', 'description']], '暂无结局'),
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
        entriesBlock('市场挂单', asArray(data.listings), ['itemName', 'equipmentName', 'name', 'listingNo'], [['价格', 'unitPrice'], ['数量', 'quantity'], ['卖家', 'sellerName']], '暂无挂单', (entry) => tradeBuyActions(entry)),
        entriesBlock('我的挂单', asArray(data.my), ['itemName', 'equipmentName', 'name', 'listingNo'], [['状态', 'status'], ['价格', 'unitPrice']], '暂无我的挂单', (entry) => tradeCancelActions(entry)),
        entriesBlock('交易记录', asArray(data.records), ['itemName', 'equipmentName', 'listingNo'], [['价格', 'unitPrice'], ['时间', 'createdAt']], '暂无交易记录'),
      ];
    case 'skills': {
      const section = activeSubMenu.value;
      if (section === '技能加成') return [summaryBlock('当前加成', asRecord(data.bonus))];
      if (section === '已掌握星痕') return [entriesBlock('已掌握', asArray(data.owned), ['skillName', 'name', 'skillKey'], [['等级', 'level'], ['类型', 'type'], ['效果', 'effects']], '暂无已掌握技能', (entry) => ownedSkillActions(entry))];
      if (section === '可解锁星痕') return [entriesBlock('可解锁', asArray(data.unlockable), ['skillName', 'name', 'skillKey'], [['消耗', 'cost'], ['条件', 'unlockCondition'], ['效果', 'effects']], '暂无可解锁技能', (entry) => skillActions(entry))];
      return [entriesBlock('星痕图谱', asArray(data.all), ['skillName', 'name', 'skillKey'], [['类型', 'type'], ['稀有度', 'rarity'], ['效果', 'effects'], ['解锁条件', 'unlockConditions']], '暂无星痕定义')];
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
          blocks.push(entriesBlock('阵营技能', asArray(data.skills), ['name', 'skillName', 'skillKey'], [['描述', 'description'], ['解锁等级', 'unlockLevel'], ['效果', 'effects'], ['消耗', 'cost']], '该阵营暂无技能'));
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
      if (section === '全部' || section === '' || section === '同伴列表') {
        blocks.push(entriesBlock('同伴列表', asArray(data.friends), ['friendName', 'playerName', 'name'], [['亲密度', 'intimacy'], ['状态', 'status']], '暂无同伴', (entry) => friendRemoveActions(entry)));
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
      const filteredInv = filterType === '全部物资' ? rawInv : rawInv.filter(e => {
        const t = pickText(e, ['type', 'itemType'], '');
        if (filterType === '武器') return t === 'weapon' || t === '武器';
        if (filterType === '防具') return t === 'armor' || t === '防具';
        if (filterType === '消耗品') return t === 'consumable' || t === '消耗品';
        if (filterType === '材料') return t === 'material' || t === '材料';
        return true;
      });
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
        entriesBlock('合成配方', filteredRecipes, ['name', 'recipeKey'], [['结果', 'resultItemName'], ['金币', 'costCoins'], ['类型', 'type'], ['效果', 'resultEffects']], filterType !== '全部配方' ? `没有${filterType}配方` : '暂无合成配方', (entry) => synthesisActions(entry)),
      ];
    }
    case 'equipment': {
      const eqFilter = activeSubMenu.value;
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
        entriesBlock('当前穿戴', filteredEquipped, ['equipmentName', 'name', 'equipmentKey'], [['槽位', 'slot'], ['耐久', 'durability']], eqFilter === '已穿戴' ? '暂无穿戴装备' : '没有需修理的穿戴装备', (entry) => equippedActions(entry)),
        entriesBlock('装备列表', allEquip, ['equipmentName', 'name', 'equipmentKey'], [['槽位', 'slot'], ['耐久', 'durability'], ['品质', 'rarity']], eqFilter === '已穿戴' ? '切换筛选查看装备列表' : '暂无装备', (entry) => equipmentActions(entry)),
      ];
    }
    case 'rest':
      return [restBlock(asRecord(data.rest))];
    case 'party':
      return [
        summaryBlock('我的队伍摘要', asRecord(data.summary)),
        entriesBlock('我的队伍', [asRecord(data.my)], ['name', 'partyName', 'partyNo'], [['成员', 'memberCount'], ['状态', 'status']], '尚未加入队伍', () => partyLeaveActions()),
        entriesBlock('活跃队伍', asArray(data.active), ['name', 'partyName', 'partyNo'], [['成员', 'memberCount'], ['状态', 'status']], '暂无活跃队伍', (entry) => partyJoinActions(entry)),
      ];
    case 'support':
      return [
        entriesBlock('支援悬赏', asArray(data.pending), ['title', 'name', 'bountyNo'], [['奖励', 'reward'], ['状态', 'status']], '暂无支援悬赏', (entry) => bountyAcceptActions(entry)),
        entriesBlock('我的支援', asArray(data.mine), ['title', 'name', 'bountyNo'], [['状态', 'status'], ['时间', 'createdAt']], '暂无我的支援', (entry) => bountyCancelActions(entry)),
      ];
    case 'worldBoss': {
      const active = asRecord(data.active);
      return [
        entriesBlock('当前灾厄', [active], ['name', 'bossName', 'boss_name', 'bossNo'], [['HP', 'hp'], ['状态', 'status']], '当前没有活跃灾厄', () => worldBossActions(active)),
        summaryBlock('灾厄摘要', asRecord(data.summary)),
        entriesBlock('伤害排行', asArray(data.rankings), ['player_name', 'playerName', 'name'], [['排名', 'rank'], ['伤害', 'damage']], '暂无伤害排行'),
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
  ];
}

function equippedActions(entry: ApiRecord): PanelAction[] {
  const equipmentKey = pickText(entry, ['equipmentKey', 'equipment_key', 'key'], '');
  const slot = pickText(entry, ['slot'], '');
  return [
    { label: '卸下', danger: true, disabled: !equipmentKey && !slot, onClick: () => runAction('卸下装备', '卸下该装备？', () => equipmentApi.unequip(playerId.value, equipmentKey, slot)) },
    { label: '修理', disabled: !equipmentKey && !slot, onClick: () => runAction('修理装备', '修理该装备？', () => equipmentApi.repair(playerId.value, equipmentKey, slot)) },
  ];
}

function ownedSkillActions(entry: ApiRecord): PanelAction[] {
  const skillKey = pickText(entry, ['skillKey', 'skill_key', 'key'], '');
  const isEquipped = pickText(entry, ['equipped'], '') === 'true' || (entry.equipped === 1 || entry.equipped === true);
  const actions: PanelAction[] = [];
  if (isEquipped) {
    actions.push({ label: '卸下', onClick: () => runAction('卸下技能', `卸下 ${pickText(entry, ['skillName', 'name', 'skillKey'], skillKey)}？`, () => skillApi.unequip(playerId.value, skillKey)) });
  } else {
    actions.push({ label: '装备', onClick: () => runAction('装备技能', `装备 ${pickText(entry, ['skillName', 'name', 'skillKey'], skillKey)}？`, () => skillApi.equip(playerId.value, skillKey)) });
  }
  return actions;
}

function skillActions(entry: ApiRecord): PanelAction[] {
  const skillKey = pickText(entry, ['skillKey', 'skill_key', 'key'], '');
  return [{ label: '解锁', disabled: !skillKey, onClick: () => runAction('解锁技能', `解锁 ${pickText(entry, ['skillName', 'name', 'skillKey'], skillKey)}？`, () => skillApi.unlock(playerId.value, skillKey)) }];
}

function factionActions(entry: ApiRecord): PanelAction[] {
  const factionKey = pickText(entry, ['factionKey', 'faction_key', 'key'], '');
  return [{ label: '加入', disabled: !factionKey, onClick: () => runAction('加入阵营', `加入 ${pickText(entry, ['name', 'factionName', 'factionKey'], factionKey)}？`, () => factionApi.join(playerId.value, factionKey)) }];
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
    { label: '赠送', disabled: !targetId, onClick: () => runAction('赠送物品', `向 ${pickText(entry, ['friendName', 'playerName', 'name'], '该同伴')} 发送礼物？`, () => friendApi.gift(playerId.value, targetId)) },
    { label: '删除', danger: true, disabled: !targetId, onClick: () => runAction('删除同伴', `删除 ${pickText(entry, ['friendName', 'playerName', 'name'], '该同伴')}？`, () => friendApi.remove(playerId.value, targetId)) },
  ];
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
  const partyNo = pickText(asRecord(panelQuery.data.value?.my), ['partyNo', 'party_no', 'id'], '');
  return [
    { label: '开始战斗', disabled: !partyNo, onClick: () => runAction('开始讨伐', '开始队伍讨伐Boss战？', () => partyApi.startBattle(playerId.value, partyNo)) },
    { label: '创建队伍', onClick: () => runAction('创建队伍', '创建默认讨伐队伍？', () => partyApi.create(playerId.value, `${gameStore.playerName}的队伍`, '自动创建的星流队伍')) },
    { label: '离开队伍', danger: true, onClick: () => runAction('离开队伍', '离开当前队伍？', () => partyApi.leave(playerId.value)) },
  ];
}

function worldBossActions(entry: ApiRecord): PanelAction[] {
  const bossNo = pickText(entry, ['bossNo', 'boss_no'], '');
  return [
    { label: '攻击', disabled: !bossNo, onClick: () => runAction('攻击灾厄', `攻击 ${pickText(entry, ['name', 'bossName', 'bossNo'], bossNo)}？`, () => worldBossApi.attack(playerId.value, bossNo)) },
    { label: '领奖', disabled: !bossNo, onClick: () => runAction('领取奖励', `领取 ${bossNo} 奖励？`, () => worldBossApi.claim(playerId.value, bossNo)) },
  ];
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

function broadcastActions(entry: ApiRecord): PanelAction[] {
  const eventKey = pickText(entry, ['eventKey', 'event_key', 'key'], '');
  return [{ label: '领奖', disabled: !eventKey, onClick: () => runAction('领取星流奖励', `领取 ${pickText(entry, ['title', 'name', 'eventKey'], eventKey)} 奖励？`, () => broadcastApi.claim(playerId.value, eventKey)) }];
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
  await runAction('发送消息', '发送到星流频道？', () => chatApi.sendMessage(playerId.value, content));
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
  await runAction('回归', '确认回归？达到SSS位阶后重置为F位阶，保留永久加成。', () => rankingApi.prestige(playerId.value));
}

async function contributeFaction() {
  const val = Number(factionContributionValue.value);
  if (!val || val <= 0) { flashToast('请输入有效贡献值', true); return; }
  const myData = asRecord(panelQuery.data.value as ApiRecord);
  const factionKey = pickText(asRecord(myData.my), ['factionKey', 'faction_key'], '');
  if (!factionKey) { flashToast('请先加入阵营', true); return; }
  await runAction('阵营贡献', `向阵营贡献 ${val} ${factionContributionType.value}？`, () =>
    factionApi.contribute(playerId.value, factionKey, factionContributionType.value, val),
  );
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
    const ghost = asRecord(asRecord(result).ghost || result);
    if (Object.keys(ghost).length > 0) {
      ghostKey.value = pickText(ghost, ['ghostKey', 'ghost_key', 'key'], '');
      ghostNarrative.value = pickText(ghost, ['narrative', 'text', 'description'], '检测到残影...');
      ghostNodeIndex.value = pickNumber(ghost, ['nodeIndex', 'node_index'], 0);
      const choices = asArray(asRecord(result).choices || []);
      ghostChoices.value = choices.map((c: ApiRecord, i: number) => ({ index: i, text: pickText(c, ['text', 'label'], `选项${i + 1}`) }));
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
    ghostNarrative.value = pickText(asRecord(result), ['narrative', 'text', 'result'], '对话已推进。');
    const nextChoices = asArray(asRecord(result).choices || []);
    if (nextChoices.length > 0) {
      ghostChoices.value = nextChoices.map((c: ApiRecord, i: number) => ({ index: i, text: pickText(c, ['text', 'label'], `选项${i + 1}`) }));
      ghostNodeIndex.value = pickNumber(asRecord(result), ['nodeIndex', 'node_index'], ghostNodeIndex.value + 1);
    } else {
      ghostChoices.value = [];
    }
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

async function runAction(label: string, confirmMessage: string, action: () => Promise<unknown>) {
  actionBusy.value = label;
  clearTimeout(toastTimer);
  actionMessage.value = '';
  actionError.value = '';
  try {
    const msg = await confirmAction(confirmMessage, action);
    if (msg !== '已取消操作') {
      flashToast(msg, false);
      await panelQuery.refetch();
      await refreshBootstrap();
    }
  } catch (error) {
    flashToast(error instanceof Error ? error.message : '操作失败', true);
  } finally {
    actionBusy.value = '';
  }
}

async function refreshBootstrap() {
  try {
    const payload = await gameApi.getBootstrap();
    gameStore.applyBootstrap(payload);
  } catch {
    // 面板操作成功后刷新总览失败不阻断当前反馈。
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
  const el = (e.target as HTMLElement).closest('.ling-placeholder') as HTMLElement;
  const containerWidth = el?.parentElement?.clientWidth || 1000;
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
  <section class="ling-placeholder">
    <div class="ling-placeholder-resize" @mousedown="startPanelResize"></div>
    <div class="ling-placeholder-inner">
    <header class="mb-4 border-b border-line pb-3">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2>{{ panelTitle }}</h2>
          <p class="mt-1 text-xs text-muted">{{ panelSubtitle }}</p>
        </div>
        <div class="flex flex-wrap gap-2 text-xs">
          <button v-if="panel === 'quests'" class="rounded border border-line px-3 py-1 text-muted hover:text-star" :disabled="Boolean(actionBusy)" @click="refreshQuests">刷新任务</button>
          <button v-if="panel === 'explore'" class="rounded border border-spirit/50 px-3 py-1 text-spirit" :disabled="Boolean(actionBusy)" @click="checkForGhost">检测残影</button>
          <button v-if="panel === 'ranking'" class="rounded border border-line px-3 py-1 text-muted hover:text-star" :disabled="Boolean(actionBusy)" @click="rankUp">升阶</button>
          <button v-if="panel === 'ranking'" class="rounded border border-danger/50 px-3 py-1 text-danger" :disabled="Boolean(actionBusy)" @click="prestigeAvatar">回归</button>
          <button v-if="panel === 'titles'" class="rounded border border-line px-3 py-1 text-muted hover:text-star" :disabled="Boolean(actionBusy)" @click="evaluateTitles">评估称号</button>
          <button v-if="panel === 'equipment'" class="rounded border border-line px-3 py-1 text-muted hover:text-star" :disabled="Boolean(actionBusy)" @click="repairAll">全部修理</button>
          <button v-if="panel === 'system'" class="rounded border border-star/50 px-3 py-1 text-star" @click="goAdmin">管理员</button>
          <button v-if="panel === 'system'" class="rounded border border-danger/50 px-3 py-1 text-danger" @click="logout">退出登录</button>
          <button class="rounded border border-line px-3 py-1 text-muted hover:text-spirit" :disabled="panelQuery.isFetching.value" @click="panelQuery.refetch()">刷新</button>
          <button class="ling-panel-close" @click="emit('close-panel')" title="关闭面板">✕</button>
        </div>
      </div>
      <div v-if="secondaryMenus.length" class="ling-submenu-row">
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

    <form v-if="panel === 'chat'" class="mb-4 flex flex-col gap-2 sm:flex-row" @submit.prevent="sendChat">
      <input v-model="chatText" class="min-w-0 flex-1 rounded border border-line bg-black/20 px-3 py-2 text-sm text-ink outline-none focus:border-spirit" placeholder="向星流频道发送消息" />
      <button class="rounded border border-star/60 px-4 py-2 text-sm text-star" :disabled="Boolean(actionBusy)">发送</button>
    </form>

    <form v-if="panel === 'feedback'" class="mb-4 space-y-3" @submit.prevent="submitFeedback">
      <select v-model="feedbackType" class="w-full rounded border border-line bg-black/20 px-3 py-2 text-sm text-ink outline-none focus:border-spirit">
        <option value="bug">问题</option>
        <option value="suggestion">建议</option>
        <option value="experience">体验</option>
      </select>
      <textarea v-model="feedbackText" class="min-h-28 w-full rounded border border-line bg-black/20 px-3 py-2 text-sm text-ink outline-none focus:border-spirit" placeholder="写下要提交给鬼怪管理局的内容"></textarea>
      <button class="rounded border border-star/60 px-4 py-2 text-sm text-star" :disabled="Boolean(actionBusy)">提交反馈</button>
    </form>

    <form v-if="panel === 'support'" class="mb-4 space-y-3" @submit.prevent="publishBounty">
      <select v-model="bountyMonsterKey" class="w-full rounded border border-line bg-black/20 px-3 py-2 text-sm text-ink outline-none focus:border-spirit">
        <option value="">选择怪物（必选）</option>
        <option v-for="m in asArray(asRecord(panelQuery.data.value).monsters)" :key="pickText(m, ['key','monsterKey','monster_key','id'], '')" :value="pickText(m, ['key','monsterKey','monster_key'], '')">
          {{ pickText(m, ['name','monsterName','monster_name'], pickText(m, ['key','monsterKey'], '未知')) }}
        </option>
      </select>
      <select v-model="bountyLocationKey" class="w-full rounded border border-line bg-black/20 px-3 py-2 text-sm text-ink outline-none focus:border-spirit">
        <option value="">选择地点（可选）</option>
        <option v-for="l in asArray(asRecord(panelQuery.data.value).locations)" :key="pickText(l, ['location_key','locationKey','key'], '')" :value="pickText(l, ['location_key','locationKey','key'], '')">
          {{ pickText(l, ['location_name','locationName','name'], pickText(l, ['location_key','locationKey'], '未知')) }}
        </option>
      </select>
      <div class="flex items-center gap-2">
        <label class="text-xs text-muted">分成比例:</label>
        <input v-model.number="bountySharePercent" type="number" min="1" max="99" class="w-20 rounded border border-line bg-black/20 px-2 py-1 text-sm text-ink outline-none" />
        <span class="text-xs text-muted">%</span>
      </div>
      <button class="rounded border border-star/60 px-4 py-2 text-sm text-star" :disabled="Boolean(actionBusy)">发布悬赏</button>
    </form>

    <form v-if="panel === 'friends'" class="mb-4 flex flex-col gap-2 sm:flex-row" @submit.prevent="panelQuery.refetch()">
      <input v-model="friendKeyword" class="min-w-0 flex-1 rounded border border-line bg-black/20 px-3 py-2 text-sm text-ink outline-none focus:border-spirit" placeholder="搜索同伴，至少 2 个字" />
      <button class="rounded border border-line px-4 py-2 text-sm text-muted hover:text-spirit">搜索</button>
    </form>

    <div v-if="!canLoadPanel(panel, playerId)" class="text-sm text-danger">缺少玩家信息，无法加载该面板。</div>
    <div v-else-if="panelQuery.isLoading.value" class="text-sm text-star">正在读取星流数据...</div>
    <div v-else-if="panelQuery.error.value" class="space-y-3 text-sm">
      <p class="text-danger">{{ panelQuery.error.value.message }}</p>
      <button class="rounded border border-line px-3 py-1 text-muted hover:text-spirit" @click="panelQuery.refetch()">重试</button>
    </div>
    <div v-else-if="panel === 'feedback'" class="text-sm text-muted">反馈内容只会在提交后写入后端。</div>
    <div v-else class="space-y-4">
      <article v-for="block in blocks" :key="block.title" class="rounded border border-line/70 bg-black/10 p-3">
        <div class="mb-3 flex items-baseline justify-between gap-3">
          <h3 class="text-sm text-star">{{ block.title }}</h3>
          <p v-if="block.description" class="text-xs text-muted">{{ block.description }}</p>
        </div>

        <div v-if="block.stats?.length" class="mb-3 grid grid-cols-2 gap-2 text-xs md:grid-cols-4">
          <div v-for="stat in block.stats" :key="`${block.title}-${stat.label}`" class="rounded border border-line/70 p-2">
            <div class="text-muted">{{ stat.label }}</div>
            <div class="mt-1 break-words text-spirit">{{ stat.value }}</div>
            <div v-if="parseProgress(stat.label, String(stat.value))" class="ling-progress-bar">
              <div class="ling-progress-fill" :class="parseProgress(stat.label, String(stat.value))!.barClass" :style="{ width: parseProgress(stat.label, String(stat.value))!.pct + '%' }"></div>
            </div>
          </div>
        </div>

        <p v-if="(!block.entries || block.entries.length === 0) && !block.stats?.length" class="text-sm text-muted">{{ block.emptyText || '暂无数据' }}</p>

        <div v-if="block.entries?.length" class="space-y-2">
          <div v-for="entry in block.entries" :key="entry.id" class="rounded border border-line/60 bg-panel/40 p-3 text-sm">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div class="min-w-0 flex-1">
                <div class="break-words text-ink">{{ entry.title }}</div>
                <p v-if="entry.subtitle" class="mt-1 break-words text-xs text-muted">{{ entry.subtitle }}</p>
                <div v-if="entry.meta?.length" class="mt-2 flex flex-wrap gap-2 text-xs text-muted">
                  <span v-for="meta in entry.meta" :key="meta" :class="['rounded border border-line/50 px-2 py-1', getDurabilityClass(meta)]">{{ meta }}</span>
                </div>
              </div>
              <div v-if="entry.actions?.length" class="flex flex-wrap gap-2">
                <button
                  v-for="action in entry.actions"
                  :key="action.label"
                  type="button"
                  :disabled="action.disabled || Boolean(actionBusy)"
                  :class="[
                    'rounded border px-3 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-40',
                    action.danger ? 'border-danger/50 text-danger' : 'border-line text-muted hover:text-star',
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

      <div v-if="showGhostDialog && panel === 'explore'" class="mt-4 rounded border border-spirit/60 bg-spirit/10 p-4">
        <h3 class="text-sm text-spirit mb-2">NPC残影</h3>
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
