import type { ApiRecord } from '@/types/api';
import { useUiStore } from '@/stores/uiStore';

export interface PanelStat {
  label: string;
  value: string;
}

export interface PanelAction {
  label: string;
  danger?: boolean;
  disabled?: boolean;
  onClick: () => Promise<void> | void;
}

export interface PanelEntry {
  id: string;
  title: string;
  subtitle?: string;
  meta?: string[];
  actions?: PanelAction[];
}

export interface PanelBlock {
  title: string;
  description?: string;
  stats?: PanelStat[];
  entries?: PanelEntry[];
  emptyText?: string;
}

export function asRecord(value: unknown): ApiRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as ApiRecord) : {};
}

export function asArray(value: unknown): ApiRecord[] {
  if (Array.isArray(value)) return value.map(asRecord);
  const record = asRecord(value);
  for (const key of ['items', 'list', 'listings', 'records', 'quests', 'locations', 'rankings', 'players', 'parties', 'definitions', 'activeSets', 'bonuses']) {
    if (Array.isArray(record[key])) {
      return (record[key] as unknown[]).map(asRecord);
    }
  }
  return [];
}

export function pick(value: unknown, keys: string[]): unknown {
  const record = asRecord(value);
  for (const key of keys) {
    if (record[key] !== undefined && record[key] !== null && record[key] !== '') {
      return record[key];
    }
  }
  return undefined;
}

export function pickText(value: unknown, keys: string[], fallback = '未知'): string {
  const next = pick(value, keys);
  if (next === undefined) return fallback;
  if (typeof next === 'object') return JSON.stringify(next);
  return String(next);
}

export function pickNumber(value: unknown, keys: string[], fallback = 0): number {
  const next = pick(value, keys);
  if (typeof next === 'number') return next;
  const parsed = Number(next);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function pickKey(value: unknown, keys: string[], fallback: string): string {
  const next = pickText(value, keys, fallback);
  return next || fallback;
}

export const LABEL_MAP: Record<string, string> = {
  friendCount: '好友数', bossNo: '灾厄编号', memberCount: '成员数', partyCount: '队伍数',
  listingCount: '挂单数', totalTrades: '总交易', totalContribution: '总贡献', totalDamage: '总伤害',
  rank: '排名', power: '战力', level: '等级', prestige: '威望', status: '状态',
  hp: '生命', maxHp: '最大生命', stamina: '行动力', maxStamina: '最大行动',
  atk: '攻击', def: '防御', spd: '速度', luck: '幸运',
  attack: '攻击', defense: '防御', speed: '速度', exp: '经验', maxExp: '经验上限',
  coins: '金币', storyFragments: '故事碎片', factionKey: '阵营', factionName: '阵营名',
  playerName: '化身名', currentLocation: '当前位置', completedQuests: '已完成任务',
  onlineCount: '在线人数', totalPlayers: '总化身数', activeBossCount: '活跃灾厄',
  isResting: '休息中', restEndTime: '休息结束', constellation: '背后星',
  titleCount: '称号数', skillCount: '技能数', equipmentCount: '装备数', inventoryCount: '物品数',
  pkScore: 'PK积分', winRate: '胜率', damageDealt: '造成伤害',
  avatarRank: '化身位阶', storyGrade: '故事评级',
  willpower: '意志力', worldLineShift: '世界线偏移', insight: '洞察', channelHeat: '频道热度',
  critRate: '暴击率', storyEventBonus: '故事事件加成', narrativePressureBonus: '叙事压力加成',
  pendingRequestCount: '待处理申请', onlineMemberCount: '在线成员', maxMembers: '最大成员',
  inParty: '在队伍中', partyNo: '队伍编号', role: '角色', reputation: '声望',
  contributionTotal: '总贡献', joinedAt: '加入时间', playerId: '化身ID', id: '编号',
  version: '版本号', buildTime: '构建时间', springBoot: '后端框架',
  questName: '任务名', questKey: '任务标识', activeListingCount: '在售挂单',
  soldCount: '已售出数', boughtCount: '已购买数', recentTradeCount: '最近交易',
  activeBoss: '活跃灾厄', bossName: '灾厄名称', hpPercent: '生命百分比',
  canClaimReward: '可领奖励', eventKey: '事件标识', cycleKey: '周期标识',
  totalRounds: '总回合数', defenderName: '防御方', attackerName: '攻击方',
  winnerName: '胜者', mode: '模式', rating: '评分', opponentName: '对手',
  intimacy: '亲密度', fromPlayerName: '来自', rarity: '稀有度', source: '来源',
  titleKey: '称号标识', titleName: '称号名', slot: '槽位', durability: '耐久',
  maxDurability: '最大耐久', enhanceLevel: '强化等级', damage: '伤害',
  rewardStatus: '奖励状态', progress: '进度', priority: '优先级', fulfilled: '是否达成',
  description: '描述', content: '内容', message: '消息', createdAt: '时间',
  location: '位置', tier: '品质',
  dailyTotal: '每日总数', dailyCompleted: '每日已完成', dailyClaimable: '每日可领取',
  weeklyTotal: '每周总数', weeklyCompleted: '每周已完成', weeklyClaimable: '每周可领取',
  achievementClaimable: '成就可领取', achievementTotal: '成就总数', achievementCompleted: '成就已完成',
  totalParticipants: '总参与人数', currentValue: '当前值', targetValue: '目标值',
  percentage: '百分比', scorePerUnit: '单位积分', objectives: '目标列表',
  partyName: '队伍名称', factionLevel: '阵营等级', factionRank: '阵营排名',
  totalMembers: '总成员数', contributionRank: '贡献排名', factionContribution: '阵营贡献',
  warScore: '战争分数', attackerScore: '攻击方分数', defenderScore: '防御方分数',
  attackerFactionKey: '攻击方阵营', defenderFactionKey: '防御方阵营',
  factionBuff: '阵营攻击加成', dailyContribution: '今日贡献', buffAtk: '攻击加成值',
  baseAtk: '基础攻击', multiplier: '贡献倍率', buffDescription: '加成说明',
  buffMultiplier: '贡献倍率', settledAt: '结算时间',
  unlockLevel: '解锁等级', unlockCondition: '解锁条件',
  resultItemKey: '合成结果', costCoins: '金币消耗',
  score: '分数',
  lastRecoveryAt: '上次恢复时间', recoveryRate: '恢复速率',
  equipmentKey: '装备标识', equipmentName: '装备名',
  listingNo: '挂单号', unitPrice: '单价', quantity: '数量',
  sellerName: '卖家', buyerName: '买家',
  friendName: '同伴名',
  target: '目标', reward: '奖励',
  activeCount: '进行中', completedCount: '已完成', claimableCount: '可领取',
  totalQuests: '总任务数', mainQuests: '主线任务', branchQuests: '支线任务',
  unlockedCount: '已解锁', totalDefinitions: '任务定义数',
  // 技能效果翻译
  staminaCostReduce: '行动消耗降低', attackBonus: '攻击加成', defenseBonus: '防御加成',
  speedBonus: '速度加成', hpBonus: '生命加成', critRateBonus: '暴击率加成',
  explorationPowerBonus: '探索力加成', luckBonus: '幸运加成', dropRateBonus: '掉落率加成',
  channelHeatGainRate: '频道热度获取', combatPowerBonus: '战力加成',
  damageReduction: '伤害减免', cooldownReduction: '冷却缩减', expBonus: '经验加成',
  coinBonus: '金币加成', fragmentBonus: '碎片加成', restRecoveryBonus: '休息恢复加成',
  pkStrengthBonus: 'PK强度加成', skillDuration: '技能持续',
  durationSeconds: '持续时间(秒)', maxCharges: '最大充能',
  // 解锁条件翻译
  levelMin: '最低等级', insightMin: '最低洞察', storyFragmentsMin: '最低碎片数',
  willpowerMin: '最低意志', avatarRankMin: '最低位阶', channelHeatMin: '最低频道热度',
  locationExploreCount: '场景探索次数',
  // 消耗品效果翻译
  heal_hp: '生命回复', restore_stamina: '体力回复', abyss_mark: '深渊印记',
  story_fragments: '故事碎片', channel_heat: '频道热度',
  // 地点翻译
  ruined_station: '废弃车站', underground_city: '地下城市', creature_forest: '魔物森林',
  broken_market: '断裂集市', silent_library: '沉默图书馆', broken_mall: '断裂商场',
};

const ENUM_MAP: Record<string, Record<string, string>> = {
  slot: { weapon: '武器', armor: '防具', accessory: '饰品', relic: '遗物' },
  rarity: { common: '普通', uncommon: '非凡', rare: '稀有', epic: '史诗', legendary: '传说' },
  type: { consumable: '消耗品', weapon: '武器', armor: '防具', material: '材料', exploration: '探索', combat: '战斗', passive: '被动', narrative: '叙事', faction: '阵营', system_notice: '系统公告', exploration_drive: '探索驱动', opportunity_rain: '机遇之雨', story_hunt: '故事狩猎', stage_support: '舞台支援', broadcast_station_cleanup: '频道净化' },
  status: { active: '活跃', completed: '已完成', in_progress: '进行中', claimed: '已领取', failed: '已失败', locked: '未解锁', sold: '已售出', cancelled: '已取消', pending: '待处理', expired: '已过期', defeated: '已击败' },
  channel: { world: '世界', faction: '阵营', team: '队伍' },
  is_unlocked: { true: '已解锁', false: '未解锁' },
  isResting: { true: '是', false: '否' },
  avatarRank: { F: 'F级·新晋', E: 'E级·入门', D: 'D级·熟练', C: 'C级·精英', B: 'B级·专家', A: 'A级·大师', S: 'S级·传奇', SS: 'SS级·神话', SSS: 'SSS级·超越' },
  storyGrade: { F: 'F级·新晋', E: 'E级·入门', D: 'D级·熟练', C: 'C级·精英', B: 'B级·专家', A: 'A级·大师', S: 'S级·传奇', SS: 'SS级·神话', SSS: 'SSS级·超越' },
  equipped: { true: '已穿戴', false: '未穿戴' },
  listed: { true: '已上架', false: '未上架' },
  fulfilled: { true: '是', false: '否' },
  role: { leader: '队长', member: '成员', admin: '管理员' },
  inParty: { true: '是', false: '否' },
  active: { active: '活跃', inactive: '不活跃', defeated: '已击败', pending: '待处理' },
  mode: { simulated: '模拟战', quick: '快速战', ranked: '排位战', friendly: '友谊战', challenge: '挑战' },
  cycleKey: { daily: '每日', weekly: '每周', achievement: '成就', story: '主线' },
  constellation: {
    demon_judge_of_fire: '惡魔般的火之審判者',
    master_of_steel: '鋼鐵之主',
    prisoner_of_golden_headband: '金箍棒囚徒',
    abyssal_black_flame_dragon: '深淵黑色焰龍',
    queen_of_darkest_spring: '最黑暗春天的女王',
    father_of_rich_night: '富裕夜晚之父',
    scribe_of_heaven: '天堂的抄寫員',
    morning_star: '晨星',
  },
};

export function translateFieldValue(key: string, value: string): string {
  const map = ENUM_MAP[key];
  if (map && map[value]) return map[value];
  // ISO 8601 time formatting: convert "2026-05-20T21:58:43.xxx" to "2026-05-20 21:58"
  const isoMatch = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.exec(value);
  if (isoMatch) {
    return isoMatch[0].replace('T', ' ');
  }
  if (value === 'true') return '是';
  if (value === 'false') return '否';
  return value;
}

function translateSubKey(subKey: string): string {
  for (const map of Object.values(ENUM_MAP)) {
    if (map[subKey]) return map[subKey];
  }
  if (LABEL_MAP[subKey]) return LABEL_MAP[subKey];
  return subKey;
}

function formatNestedValue(v: unknown): string {
  if (typeof v === 'object' && v !== null && !Array.isArray(v)) {
    const entries = Object.entries(asRecord(v))
      .filter(([, subV]) => subV !== undefined && subV !== null && subV !== '')
      .map(([k, subV]) => {
        const translatedKey = translateSubKey(k);
        const translatedVal = typeof subV === 'object' && subV !== null
          ? (Array.isArray(subV) ? subV.map(formatNestedValue).join(', ') : formatNestedValue(subV))
          : translateFieldValue(k, String(subV));
        return `${translatedKey}: ${translatedVal}`;
      });
    return entries.join(', ');
  }
  if (Array.isArray(v)) return v.map(formatNestedValue).join(', ');
  return String(v);
}

export function displayMeta(value: unknown, keys: Array<[string, string]>): string[] {
  return keys
    .map(([label, key]) => {
      const next = pick(value, [key]);
      if (next === undefined || next === null || next === '') return '';
      if (typeof next === 'object') {
        const entries = Object.entries(asRecord(next))
          .filter(([, v]) => v !== undefined && v !== null && v !== '')
          .map(([k, v]) => `${translateSubKey(k)}: ${typeof v === 'object' ? formatNestedValue(v) : translateFieldValue(key, String(v))}`);
        return entries.length ? `${label}: ${entries.join(', ')}` : '';
      }
      return `${label}: ${translateFieldValue(key, String(next))}`;
    })
    .filter(Boolean);
}

export function formatCount(value: unknown): string {
  const next = typeof value === 'number' ? value : Number(value || 0);
  if (!Number.isFinite(next)) return '0';
  if (next >= 10000) return `${(next / 10000).toFixed(next >= 100000 ? 0 : 1)}万`;
  return String(next);
}

export async function confirmAction(message: string, action: () => Promise<unknown>): Promise<string> {
  const ui = useUiStore();
  const confirmed = await ui.showConfirm('操作确认', message);
  if (!confirmed) return '已取消操作';
  await action();
  return '操作已完成';
}

/**
 * 安全地将任意值渲染为摘要字符串，永远不会在UI上暴露 JSON.stringify。
 * - undefined/null 返回空字符串
 * - string/number/boolean 返回 translateFieldValue 处理后的字符串
 * - 数组返回 [N项] 格式
 * - 对象只渲染前5个简单值字段，超过显示"...等N项"
 */
export function formatSummaryValue(value: unknown): string {
  if (value === undefined || value === null) return '';
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return translateFieldValue('', String(value));
  }
  if (Array.isArray(value)) {
    return `[${value.length}项]`;
  }
  if (typeof value === 'object') {
    const record = asRecord(value);
    const entries = Object.entries(record);
    if (entries.length === 0) return '';

    const simpleEntries = entries.filter(([, v]) => {
      const t = typeof v;
      return t === 'string' || t === 'number' || t === 'boolean';
    });

    const displayEntries = simpleEntries.slice(0, 5);
    const parts = displayEntries.map(([k, v]) => {
      const label = LABEL_MAP[k] || k;
      const val = translateFieldValue(k, String(v));
      return `${label}: ${val}`;
    });

    if (simpleEntries.length > 5) {
      parts.push(`...等${simpleEntries.length}项`);
    }

    return parts.join(', ');
  }
  return String(value);
}
