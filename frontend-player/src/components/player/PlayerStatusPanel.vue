<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { Player, PlayerStats } from '@/types/player';
import type { ApiRecord } from '@/types/api';
import { equipmentApi } from '@/api/equipmentApi';
import { rankingApi } from '@/api/rankingApi';
import { useUiStore } from '@/stores/uiStore';
import AllocDialog from '@/components/player/AllocDialog.vue';
import { getExperienceProgress } from '@/utils/experienceProgress';
import { formatEquipmentDetails, formatSummaryValue } from '@/utils/panelData';

const ui = useUiStore();

const props = defineProps<{
  player: Player | null;
}>();

function stats(): PlayerStats {
  return props.player?.stats || props.player?.statsJson || {};
}

function num(value: unknown): number {
  return typeof value === 'number' ? value : Number(value || 0);
}

function compact(value: unknown) {
  const next = num(value);
  if (next >= 10000) return `${(next / 10000).toFixed(next >= 100000 ? 0 : 1)}万`;
  return String(next);
}

function percent(value: unknown) {
  return `${(num(value) * 100).toFixed(1)}%`;
}

const experience = computed(() => getExperienceProgress(stats().exp, stats().level));

interface EquipmentRow {
  slot: string;
  name: string;
  detail: string;
}

interface RankRequirementProgress {
  type?: string;
  label?: string;
  current?: number;
  required?: number;
  completed?: boolean;
  reqKey?: string;
}

interface AvatarRankProgress {
  canRankUp?: boolean;
  maxRank?: boolean;
  isMaxRank?: boolean;
  nextRankName?: string;
  nextDisplayName?: string;
  nextRank?: { displayName?: string; description?: string };
  requirements?: RankRequirementProgress[];
}

function rarityLabel(rarity: string): string {
  const map: Record<string, string> = {
    legendary: '传说',
    epic: '史诗',
    rare: '稀有',
    uncommon: '精良',
    common: '普通',
  };
  return map[rarity] || rarity || '';
}

function roundDurability(durability: unknown): number {
  return Math.round(num(durability));
}

function buildDetail(item: ApiRecord): string {
  const parts: string[] = [];
  const equipmentDetails = formatEquipmentDetails(item);
  if (equipmentDetails) parts.push(equipmentDetails);
  const bonus = item.bonus ?? item.statBonus;
  if (!equipmentDetails && bonus !== undefined && bonus !== null) {
    const n = num(bonus);
    if (n > 0) parts.push(`+${n}`);
    else if (n < 0) parts.push(`${n}`);
  }
  if (item.durability !== undefined && item.durability !== null) {
    const d = roundDurability(item.durability);
    const maxDurability = roundDurability(item.maxDurability ?? item.max_durability);
    parts.push(maxDurability > 0 ? `耐久${d}/${maxDurability}` : `耐久${d}`);
  }
  if (item.bonusDesc) {
    parts.push(String(item.bonusDesc));
  } else if (item.effect !== undefined && item.effect !== null) {
    parts.push(typeof item.effect === 'object'
      ? formatSummaryValue(item.effect)
      : String(item.effect));
  }
  return parts.join(' ') || '';
}

const equippedItems = ref<EquipmentRow[]>([]);
const rankInfo = ref<AvatarRankProgress | null>(null);
const rankInfoLoading = ref(false);
const rankInfoFailed = ref(false);
let rankInfoRequestVersion = 0;

function selectedConstellationKey(): string {
  const constellation = stats().constellation;
  return typeof constellation === 'string' ? constellation.trim() : '';
}

function rankProgressFingerprint(): string {
  const player = props.player;
  if (!player) return '';

  return JSON.stringify({
    level: stats().level,
    storyFragments: player.storyFragments ?? player.story_fragments,
    channelHeat: stats().channelHeat,
    rating: stats().rating,
    worldLineShift: stats().worldLineShift,
    stageProgress: player.stage_progress ?? player.stageProgress,
    titles: player['titles'],
  });
}

async function loadEquippedEquipment() {
  const playerId = props.player?.id ?? props.player?.playerId;
  if (!playerId || !selectedConstellationKey()) {
    equippedItems.value = [];
    return;
  }
  try {
    const equipped = await equipmentApi.getEquipped(playerId);
    const entries = Object.entries(equipped).filter(
      ([, item]) => item != null && typeof item === 'object' && (item as ApiRecord).equipped === true
    );
    if (entries.length > 0) {
      equippedItems.value = entries.map(([slotKey, item]) => {
        const equipment = item as ApiRecord;
        const slot = String(equipment.slot || slotKey || '');
        const rarity = rarityLabel(String(equipment.rarity || ''));
        const nameRaw = String(equipment.equipmentName || equipment.name || equipment.equipmentKey || '未知装备');
        const name = rarity ? `${rarity}·${nameRaw}` : nameRaw;
        return { slot, name, detail: buildDetail(equipment) };
      });
    } else {
      equippedItems.value = [];
    }
  } catch {
    equippedItems.value = [];
  }
}

watch(
  () => [props.player?.id ?? props.player?.playerId, selectedConstellationKey()],
  () => void loadEquippedEquipment(),
  { immediate: true },
);

const rankGoalRequirements = computed(() => rankInfo.value?.requirements || []);
const rankGoalDoneCount = computed(() => rankGoalRequirements.value
  .filter((requirement) => requirement.completed).length);
const rankGoalName = computed(() => rankInfo.value?.nextDisplayName
  || rankInfo.value?.nextRank?.displayName
  || rankInfo.value?.nextRankName
  || '下一成长阶');
const rankGoalDescription = computed(() => rankInfo.value?.nextRank?.description || '');
const rankGoalIsMax = computed(() => Boolean(rankInfo.value?.maxRank ?? rankInfo.value?.isMaxRank));

function rankRequirementCurrent(requirement: RankRequirementProgress): number {
  const current = Number(requirement.current ?? 0);
  return Number.isFinite(current) ? Math.max(0, current) : 0;
}

function rankRequirementRequired(requirement: RankRequirementProgress): number {
  const required = Number(requirement.required ?? 0);
  return Number.isFinite(required) ? Math.max(0, required) : 0;
}

function rankRequirementComplete(requirement: RankRequirementProgress): boolean {
  return requirement.completed ?? rankRequirementCurrent(requirement) >= rankRequirementRequired(requirement);
}

function rankRequirementPercent(requirement: RankRequirementProgress): number {
  const required = rankRequirementRequired(requirement);
  if (required <= 0) return rankRequirementComplete(requirement) ? 100 : 0;
  return Math.min(100, Math.round(rankRequirementCurrent(requirement) / required * 100));
}

function rankRequirementProgressText(requirement: RankRequirementProgress): string {
  return `${rankRequirementCurrent(requirement)} / ${rankRequirementRequired(requirement)}`;
}

const RANK_LOCATION_NAMES: Record<string, string> = {
  ruined_station: '废弃车站',
  demon_gate: '恶魔之门',
};

const BOSS_CLUE_SOURCES: Record<string, string> = {
  station_keeper: '废弃车站 →「看门人的痕迹」',
  hellfire: '忠武路 →「地狱之火的预兆」',
  second_giant: '东大门 →「第二个巨人的痕迹」',
  underground_behemoth: '江南站 →「地下的声音」',
};

function rankRequirementHint(requirement: RankRequirementProgress): string {
  switch (requirement.type) {
    case 'levelMin':
      return '通过探索、战斗与任务积累经验并提升等级。';
    case 'storyFragmentsMin':
      return '故事碎片可从探索事件、剧情和任务奖励中收集。';
    case 'explorationsByLocation': {
      const location = RANK_LOCATION_NAMES[requirement.reqKey || ''] || '指定场景';
      return `来源：在「${location}」完成探索。`;
    }
    case 'storyEventsTriggeredMin':
      return '推进你的个人主线时间线并触发故事事件；不会改变其他玩家的剧情。';
    case 'bossClue': {
      const source = BOSS_CLUE_SOURCES[requirement.reqKey || ''];
      return source
        ? `线索来源：${source}。`
        : `线索来源：在探索中触发与「${requirement.label || '目标首领'}」有关的线索事件。`;
    }
    case 'titlesCountMin':
      return '完成故事、任务与阶段目标收集称号。';
    case 'rareTitleRequired':
      return '在称号面板确认品质；需要至少一个稀有或更高品质称号。';
    case 'epicTitleRequired':
      return '在称号面板确认品质；需要至少一个史诗或更高品质称号。';
    case 'legendaryTitleRequired':
      return '在称号面板确认品质；需要至少一个传说品质称号。';
    case 'channelHeatMin':
      return '通过星流频道活动与公共互动提升热度。';
    case 'pkRatingMin':
      return '参与竞技挑战提升评分。';
    case 'worldLineShiftMin':
      return '个人剧情选择与相关事件会改变你的世界线偏移。';
    default:
      return '完成游戏中对应的成长目标；当前进度由服务端实时判定。';
  }
}

async function loadRankInfo() {
  const requestVersion = ++rankInfoRequestVersion;
  const playerId = props.player?.id ?? props.player?.playerId;
  if (!playerId || !selectedConstellationKey()) {
    rankInfo.value = null;
    rankInfoFailed.value = false;
    rankInfoLoading.value = false;
    return;
  }

  rankInfoLoading.value = true;
  rankInfoFailed.value = false;
  try {
    const info = await rankingApi.getAvatarRankInfo(Number(playerId)) as unknown as AvatarRankProgress;
    if (requestVersion === rankInfoRequestVersion) rankInfo.value = info;
  } catch {
    if (requestVersion === rankInfoRequestVersion) {
      rankInfo.value = null;
      rankInfoFailed.value = true;
    }
  } finally {
    if (requestVersion === rankInfoRequestVersion) rankInfoLoading.value = false;
  }
}

watch(
  () => [
    props.player?.id ?? props.player?.playerId,
    selectedConstellationKey(),
    stats().avatarRank,
    rankProgressFingerprint(),
  ],
  () => void loadRankInfo(),
  { immediate: true },
);

const tooltipKey = ref<string | null>(null);
const tooltipX = ref(0);
const tooltipY = ref(0);

function b(key: string): number {
  const s = stats();
  return s[key] instanceof Number || typeof s[key] === 'number' ? Number(s[key]) : 0;
}

const tooltipText = computed(() => {
  const s = stats();
  const lv = num(s.level) || 1;
  const levelExperience = experience.value;
  const constName = String(s.bonusConstellationName || '');

  function constPart(value: number): string {
    if (!value) return '';
    return constName ? `背后星·${constName}(+${value})` : `背后星(+${value})`;
  }
  function constPartPct(value: number): string {
    if (!value) return '';
    return constName ? `背后星·${constName}(+${(value * 100).toFixed(0)}%)` : `背后星(+${(value * 100).toFixed(0)}%)`;
  }
  function equipPart(value: number, detail: string): string {
    if (detail) return `装备·${detail}`;
    return value ? `装备(+${value})` : '';
  }
  function equipPartPct(value: number, detail: string): string {
    if (detail) return `装备·${detail}`;
    return value ? `装备(+${(value * 100).toFixed(0)}%)` : '';
  }
  function skillPart(value: number, detail: string): string {
    if (detail) return `技能·${detail}`;
    return value ? `技能(+${value})` : '';
  }
  function skillPartPct(value: number, detail: string): string {
    if (detail) return `技能·${detail}`;
    return value ? `技能(+${(value * 100).toFixed(0)}%)` : '';
  }
  function levelGrowthPart(value: number): string {
    return value ? `等级成长(+${value})` : '';
  }
  function storedGrowthPart(value: number): string {
    if (!value) return '';
    return `存档成长(${value > 0 ? '+' : ''}${value})`;
  }
  function storedGrowthPartPct(value: number): string {
    if (!value) return '';
    const percent = (value * 100).toFixed(1);
    return `存档成长(${value > 0 ? '+' : ''}${percent}%)`;
  }
  function titlePart(value: number, detail: string): string {
    if (detail) return `称号·${detail}`;
    if (!value) return '';
    const name = String(s.bonusTitleName || '已装备称号');
    return `称号·${name}(+${value})`;
  }
  function titlePartPct(value: number, detail: string): string {
    if (detail) return `称号·${detail}`;
    if (!value) return '';
    const name = String(s.bonusTitleName || '已装备称号');
    return `称号·${name}(+${(value * 100).toFixed(0)}%)`;
  }
  function factionPart(value: number, detail: string): string {
    if (detail) return `阵营·${detail}`;
    return value ? `阵营(+${value})` : '';
  }
  function factionSkillPart(value: number): string {
    return value ? `阵营技能(+${value})` : '';
  }
  function factionSkillPartPct(value: number): string {
    return value ? `阵营技能(+${(value * 100).toFixed(0)}%)` : '';
  }

  const combatProjection = s.combatProjection === true;
  function constellationCombatNote(value: number, percent = false): string {
    if (!combatProjection || !value) return '';
    const name = constName ? `·${constName}` : '';
    const amount = percent ? `${(value * 100).toFixed(0)}%` : String(value);
    return `\n背后星${name}加成(${value > 0 ? '+' : ''}${amount})（当前战斗公式暂未计入）`;
  }
  function constellationHpIncludedNote(value: number): string {
    if (!combatProjection || !value) return '';
    const name = constName ? `·${constName}` : '';
    return `\n背后星${name}加成(+${value})已计入最大生命上限及战斗结算。`;
  }
  const eqAtkDetail = String(s.bonusAtkEquipmentDetail || '');
  const skAtkDetail = String(s.bonusAtkSkillDetail || '');
  const titleAtkDetail = String(s.bonusAtkTitleDetail || '');
  const faAtkDetail = String(s.bonusAtkFactionDetail || '');
  const partsAtk = (combatProjection ? [
    `基础(${b('bonusAtkBase')})`,
    `属性点(${b('bonusAtkAlloc')})`,
    storedGrowthPart(b('bonusAtkStoredGrowth')),
    levelGrowthPart(b('bonusAtkLevelGrowth')),
    titlePart(b('bonusAtkTitle'), titleAtkDetail),
    equipPart(b('bonusAtkEquipment'), eqAtkDetail),
    skillPart(b('bonusAtkSkill'), skAtkDetail),
    factionPart(b('bonusAtkFaction'), faAtkDetail),
    factionSkillPart(b('bonusAtkFactionSkill')),
  ] : [
    `基础(${b('bonusAtkBase')})`,
    `属性点(${b('bonusAtkAlloc')})`,
    constPart(b('bonusAtkConstellation')),
    levelGrowthPart(b('bonusAtkLevelGrowth')),
    titlePart(b('bonusAtkTitle'), titleAtkDetail),
    equipPart(b('bonusAtkEquipment'), eqAtkDetail),
    skillPart(b('bonusAtkSkill'), skAtkDetail),
    factionPart(b('bonusAtkFaction'), faAtkDetail),
    factionSkillPart(b('bonusAtkFactionSkill')),
  ]).filter(Boolean).join(' + ');
  const totalAtk = num(s.attack);

  const eqDefDetail = String(s.bonusDefEquipmentDetail || '');
  const skDefDetail = String(s.bonusDefSkillDetail || '');
  const titleDefDetail = String(s.bonusDefTitleDetail || '');
  const partsDef = (combatProjection ? [
    `基础(${b('bonusDefBase')})`,
    `属性点(${b('bonusDefAlloc')})`,
    storedGrowthPart(b('bonusDefStoredGrowth')),
    levelGrowthPart(b('bonusDefLevelGrowth')),
    titlePart(b('bonusDefTitle'), titleDefDetail),
    equipPart(b('bonusDefEquipment'), eqDefDetail),
    skillPart(b('bonusDefSkill'), skDefDetail),
    factionSkillPart(b('bonusDefFactionSkill')),
  ] : [
    `基础(${b('bonusDefBase')})`,
    `属性点(${b('bonusDefAlloc')})`,
    constPart(b('bonusDefConstellation')),
    levelGrowthPart(b('bonusDefLevelGrowth')),
    titlePart(b('bonusDefTitle'), titleDefDetail),
    equipPart(b('bonusDefEquipment'), eqDefDetail),
    skillPart(b('bonusDefSkill'), skDefDetail),
    factionSkillPart(b('bonusDefFactionSkill')),
  ]).filter(Boolean).join(' + ');
  const totalDef = num(s.defense);

  const eqSpdDetail = String(s.bonusSpdEquipmentDetail || '');
  const skSpdDetail = String(s.bonusSpdSkillDetail || '');
  const titleSpdDetail = String(s.bonusSpdTitleDetail || '');
  const partsSpd = (combatProjection ? [
    `基础(${b('bonusSpdBase')})`,
    `属性点(${b('bonusSpdAlloc')})`,
    storedGrowthPart(b('bonusSpdStoredGrowth')),
    titlePart(b('bonusSpdTitle'), titleSpdDetail),
    equipPart(b('bonusSpdEquipment'), eqSpdDetail),
    skillPart(b('bonusSpdSkill'), skSpdDetail),
    factionSkillPart(b('bonusSpdFactionSkill')),
  ] : [
    `基础(${b('bonusSpdBase')})`,
    `属性点(${b('bonusSpdAlloc')})`,
    constPart(b('bonusSpdConstellation')),
    titlePart(b('bonusSpdTitle'), titleSpdDetail),
    equipPart(b('bonusSpdEquipment'), eqSpdDetail),
    skillPart(b('bonusSpdSkill'), skSpdDetail),
    factionSkillPart(b('bonusSpdFactionSkill')),
  ]).filter(Boolean).join(' + ');
  const totalSpd = num(s.speed);

  const eqCritDetail = String(s.bonusCritEquipmentDetail || '');
  const skCritDetail = String(s.bonusCritSkillDetail || '');
  const titleCritDetail = String(s.bonusCritTitleDetail || '');
  const partsCrit = (combatProjection ? [
    `基础(${(b('bonusCritBase') * 100).toFixed(1)}%)`,
    `属性点(${(b('bonusCritAlloc') * 2).toFixed(0)}%)`,
    storedGrowthPartPct(b('bonusCritStoredGrowth')),
    titlePartPct(b('bonusCritTitle'), titleCritDetail),
    equipPartPct(b('bonusCritEquipment'), eqCritDetail),
    skillPartPct(b('bonusCritSkill'), skCritDetail),
    factionSkillPartPct(b('bonusCritFactionSkill')),
  ] : [
    `属性点(${(b('bonusCritAlloc') * 2).toFixed(0)}%)`,
    constPartPct(b('bonusCritConstellation')),
    titlePartPct(b('bonusCritTitle'), titleCritDetail),
    equipPartPct(b('bonusCritEquipment'), eqCritDetail),
    skillPartPct(b('bonusCritSkill'), skCritDetail),
    factionSkillPartPct(b('bonusCritFactionSkill')),
  ]).filter(Boolean).join(' + ');
  const totalCrit = num(s.critRate);

  const eqHpDetail = String(s.bonusMaxHpEquipmentDetail || '');
  const skHpDetail = String(s.bonusMaxHpSkillDetail || '');
  const titleHpDetail = String(s.bonusMaxHpTitleDetail || '');
  const partsMaxHp = (combatProjection ? [
    `基础(${b('bonusMaxHpBase')})`,
    storedGrowthPart(b('bonusMaxHpStoredGrowth')),
    constPart(b('bonusMaxHpConstellation')),
    titlePart(b('bonusMaxHpTitle'), titleHpDetail),
    equipPart(b('bonusMaxHpEquipment'), eqHpDetail),
    skillPart(b('bonusMaxHpSkill'), skHpDetail),
    factionSkillPart(b('bonusMaxHpFactionSkill')),
  ] : [
    `基础(${b('bonusMaxHpBase')})`,
    constPart(b('bonusMaxHpConstellation')),
    titlePart(b('bonusMaxHpTitle'), titleHpDetail),
    equipPart(b('bonusMaxHpEquipment'), eqHpDetail),
    skillPart(b('bonusMaxHpSkill'), skHpDetail),
    factionSkillPart(b('bonusMaxHpFactionSkill')),
  ]).filter(Boolean).join(' + ');
  const totalMaxHp = num(s.maxHp);

  const lines: Record<string, string> = {
    hp: `最大生命 = ${partsMaxHp} = ${totalMaxHp}\n`
      + `当前: ${compact(s.hp)} / ${compact(s.maxHp)}\n`
      + `归零时化身将陷入死亡状态。`
      + constellationHpIncludedNote(b('bonusMaxHpConstellation')),
    stamina: `行动力上限: ${compact(s.maxStamina)}\n`
      + `当前: ${compact(s.stamina)} / ${compact(s.maxStamina)}\n`
      + `探索场景、执行行动的核心资源。休息中自动恢复。`,
    attack: `攻击力 = ${partsAtk} = ${totalAtk}\n`
      + `1属性点 = 1攻击力`
      + constellationCombatNote(b('bonusAtkConstellation')),
    defense: `防御力 = ${partsDef} = ${totalDef}\n`
      + `1属性点 = 1防御力`
      + constellationCombatNote(b('bonusDefConstellation')),
    power: `综合战力 = 攻击×2 + 防御×1.5 + 反应×1.2 + 最大生命×0.3\n`
      + `= ${totalAtk}×2 + ${totalDef}×1.5 + ${totalSpd}×1.2 + ${totalMaxHp}×0.3 = ${computedPower()}`,
    speed: `速度 = ${partsSpd} = ${totalSpd}\n`
      + `1属性点 = 1速度\n决定战斗中的行动优先级。`
      + constellationCombatNote(b('bonusSpdConstellation')),
    luck: `幸运: ${compact(s.luck)}\n`
      + `影响 NPC 残影相遇权重：每点幸运使权重增加 2%。`,
    insight: `洞察: ${compact(s.insight)}\n`
      + `部分技能的学习条件会检查洞察；它不会直接替代攻击或防御。`,
    channelHeat: `频道热度: ${compact(s.channelHeat)}\n`
      + `反映在星流频道中的影响力。影响特殊事件与隐藏选项。`,
    worldLineShift: `世界线偏移: ${compact(s.worldLineShift)}\n`
      + `偏移越大越容易触发特殊结局。`,
    critRate: `暴击率 = ${partsCrit || '基础(0%)'} = ${(totalCrit * 100).toFixed(1)}%\n`
      + `1属性点 = 2%暴击率`
      + constellationCombatNote(b('bonusCritConstellation'), true),
    critDamage: `暴击伤害倍率: ${percent(s.critDamage ?? 1.5)}\n`
      + `暴击时按此倍率计算伤害。`,
    rank: `成长阶是本游戏的化身成长段位，不等同于原著中的位格。\n`
      + `完成经验与成长条件后可提升。`,
    exp: `本级经验: ${compact(levelExperience.current)} / ${compact(levelExperience.required)}\n`
      + `通过探索、任务、战斗获得。\n`
      + `满足条件后可提升成长阶。`,
    level: `等级 = floor(√(经验/100)) + 1\n`
      + `= floor(√(${num(s.exp)}/100)) + 1 = ${lv}\n`
      + `每升一级: 自由属性点+3`,
  };

  const key = tooltipKey.value;
  return key ? lines[key] || '' : '';
});

function showTooltip(key: string, e: MouseEvent) {
  if (tooltipKey.value === key) {
    tooltipKey.value = null;
    return;
  }
  tooltipKey.value = key;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  tooltipX.value = rect.right + 8;
  tooltipY.value = rect.top;
}

function hideTooltip() {
  tooltipKey.value = null;
}

const showAllocDialog = ref(false);
const rankingUp = ref(false);
const emit = defineEmits<{
  playerUpdated: [data: unknown];
  openPanel: [panel: string];
}>();

async function handleRankUp() {
  const playerId = props.player?.id ?? props.player?.playerId;
  if (!playerId || rankingUp.value) return;
  rankingUp.value = true;
  try {
    const info = await rankingApi.getAvatarRankInfo(Number(playerId)) as unknown as AvatarRankProgress;
    rankInfo.value = info;
    rankInfoFailed.value = false;
    const reqs = info?.requirements || [];
    const canRankUp: boolean = info?.canRankUp ?? false;
    const nextName: string = info?.nextDisplayName || info?.nextRankName || '下一级';

    const details = reqs.map((requirement) => ({
      label: requirement.label || '成长条件',
      current: rankRequirementCurrent(requirement),
      required: rankRequirementRequired(requirement),
      completed: rankRequirementComplete(requirement),
    }));

    rankingUp.value = false; // 释放按钮，弹窗期间不禁用

    if (canRankUp) {
      const confirmed = await ui.showConfirm(
        '提升成长阶',
        `即将晋升至「${nextName}」，确认提升成长阶？`,
        details,
      );
      if (!confirmed) return;
      rankingUp.value = true; // 确认后，执行升阶时显示加载状态
    } else {
      await ui.showAlert('提升成长阶', '成长条件尚未全部满足：', details);
      return;
    }

    await rankingApi.rankUp(playerId);
    emit('playerUpdated', null);
    void loadRankInfo();
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '提升失败';
    ui.showAlert('提升成长阶', msg);
  } finally {
    rankingUp.value = false;
  }
}

function clampX(x: number) {
  return Math.min(x, window.innerWidth - 270);
}

function clampY(y: number) {
  return Math.min(y, window.innerHeight - 160);
}

function computedPower(): number {
  const s = stats();
  const atk = num(s.attack);
  const def = num(s.defense);
  const spd = num(s.speed);
  const mxHp = num(s.maxHp);
  if (!atk && !def && !spd && !mxHp) return 0;
  return Math.round(atk * 2 + def * 1.5 + spd * 1.2 + mxHp * 0.3);
}

function readPlayerValue(...keys: string[]): unknown {
  const playerRecord = (props.player || {}) as Record<string, unknown>;
  const statRecord = stats() as Record<string, unknown>;
  for (const key of keys) {
    if (statRecord[key] !== undefined && statRecord[key] !== null) return statRecord[key];
    if (playerRecord[key] !== undefined && playerRecord[key] !== null) return playerRecord[key];
  }
  return undefined;
}

function textValue(value: unknown, fallback = '—'): string {
  return value === undefined || value === null || value === '' ? fallback : String(value);
}

const growthStageName = computed(() => textValue(
  readPlayerValue('avatarRankName', 'avatarRank'),
  '未定级',
));

const insight = computed(() => compact(stats().insight));

const attrs = computed(() => {
  const s = stats();
  return [
    { label: '生命', key: 'hp', color: 'hp-text', display: `${compact(s.hp)} / ${compact(s.maxHp)}` },
    { label: '行动力', key: 'stamina', color: 'mp-text', display: `${compact(s.stamina)} / ${compact(s.maxStamina)}` },
    { label: '攻击', key: 'attack', color: 'atk-text', display: compact(s.attack) },
    { label: '防御', key: 'defense', color: 'def-text', display: compact(s.defense) },
    { label: '速度', key: 'speed', color: 'speed-text', display: compact(s.speed) },
    { label: '暴击率', key: 'critRate', color: 'crit-text', display: percent(s.critRate) },
    { label: '暴击伤害', key: 'critDamage', color: 'crit-text', display: percent(s.critDamage ?? 1.5) },
    { label: '战力', key: 'power', color: 'power-text', display: compact(computedPower()) },
    { label: '洞察', key: 'insight', color: 'insight-text', display: insight.value },
    { label: '幸运', key: 'luck', color: 'luck-text', display: compact(s.luck) },
  ];
});

const EQUIPMENT_SLOTS = [
  { key: 'weapon', label: '武器', aliases: ['weapon', 'mainHand', '主手'] },
  { key: 'armor', label: '护甲', aliases: ['armor', 'body', '防具', '外装', '服饰'] },
  { key: 'helmet', label: '头盔', aliases: ['helmet', 'head', '头部'] },
  { key: 'accessory', label: '饰品', aliases: ['accessory', 'accessory1', 'accessory2', '法器'] },
  { key: 'relic', label: '遗物', aliases: ['relic'] },
] as const;

const equipSlots = computed(() => {
  const configuredSlots = EQUIPMENT_SLOTS.map(slot => ({
    ...slot,
    item: equippedItems.value.find(item => (slot.aliases as readonly string[]).includes(item.slot)),
  }));
  const unmatchedItems = equippedItems.value
    .filter(item => !EQUIPMENT_SLOTS.some(slot => (slot.aliases as readonly string[]).includes(item.slot)))
    .map(item => ({
      key: `legacy-${item.slot}`,
      label: item.slot || '其他装备',
      aliases: [item.slot],
      item,
    }));
  return [...configuredSlots, ...unmatchedItems];
});

const dailyHelp = computed(() => `${textValue(readPlayerValue('dailyHelpCount', 'daily_help_count'), '0')}/30`);
const dailyAssist = computed(() => `${textValue(readPlayerValue('dailyAssistCount', 'daily_assist_count'), '0')}/30`);
const skillName = computed(() => textValue(readPlayerValue('skillName', 'skill', 'technique'), '无'));
const courtTitle = computed(() => textValue(readPlayerValue('courtTitle', 'officialTitle', 'title'), '—'));
const nemesisLabel = computed(() => textValue(readPlayerValue('nemesisCount', 'nemesis_count'), '仇敌录'));

async function repairAllEquipment() {
  const playerId = props.player?.id ?? props.player?.playerId;
  if (!playerId) return;
  try {
    await equipmentApi.repairAll(Number(playerId));
    await loadEquippedEquipment();
    emit('playerUpdated', null);
  } catch (error: unknown) {
    await ui.showAlert('装备修复', error instanceof Error ? error.message : '修复失败');
  }
}
</script>

<template>
  <div class="ling-side-content psp-root player-panel-replica">
    <section class="panel-section panel-section--role">
      <h3 class="panel-title">
        <span class="panel-title-label">角色信息</span>
        <span class="panel-title-spacer" aria-hidden="true"></span>
        <span class="panel-title-status-edge">{{ player?.id ? `#${player.id}` : '' }}</span>
      </h3>
      <div class="stat-row stat-row--name">
        <span class="stat-label">姓名</span>
        <span class="stat-value stat-value-clickable stat-name-value">{{ player?.playerName || player?.player_name || player?.name || '未命名读者' }}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">成长阶</span>
        <span class="stat-value realm-text">{{ growthStageName }}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">经验</span>
        <span class="stat-value stat-value-clickable" role="button" tabindex="0" @click="showTooltip('exp', $event)">{{ compact(experience.current) }} / {{ compact(experience.required) }}</span>
      </div>
      <div class="progress-bar-container">
        <div class="progress-bar" :style="{ width: `${experience.percent}%` }"></div>
        <span class="progress-text">{{ experience.percent }}%</span>
      </div>
      <div class="role-actions">
        <button class="ling-break-btn" :disabled="rankingUp" @click="handleRankUp">{{ rankingUp ? '提升中...' : '提升成长阶' }}</button>
        <button class="ling-alloc-btn" @click.stop="showAllocDialog = true">属性加点</button>
      </div>
      <section v-if="rankInfo && !rankGoalIsMax" class="rank-goal-panel" data-testid="rank-goal-panel" aria-label="下一成长阶条件">
        <div class="rank-goal-heading">
          <div class="rank-goal-heading-copy">
            <span class="rank-goal-kicker">成长目标</span>
            <strong>{{ rankGoalName }}</strong>
          </div>
          <span v-if="rankGoalRequirements.length" class="rank-goal-count">
            {{ rankGoalDoneCount }} / {{ rankGoalRequirements.length }}
          </span>
          <span v-else class="rank-goal-count is-ready">可直接提升</span>
        </div>
        <p v-if="rankGoalDescription" class="rank-goal-description">{{ rankGoalDescription }}</p>
        <ol v-if="rankGoalRequirements.length" class="rank-goal-list">
          <li
            v-for="(requirement, index) in rankGoalRequirements"
            :key="`${requirement.type || 'requirement'}-${requirement.reqKey || index}`"
            :class="['rank-goal-item', { 'is-complete': rankRequirementComplete(requirement) }]"
            :data-requirement-type="requirement.type"
          >
            <span class="rank-goal-marker" aria-hidden="true">
              {{ rankRequirementComplete(requirement) ? '✓' : String(index + 1).padStart(2, '0') }}
            </span>
            <div class="rank-goal-item-content">
              <div class="rank-goal-item-head">
                <span class="rank-goal-label">{{ requirement.label || '成长条件' }}</span>
                <span class="rank-goal-progress-text">{{ rankRequirementProgressText(requirement) }}</span>
              </div>
              <div
                class="rank-goal-track"
                role="progressbar"
                :aria-label="requirement.label || '成长条件进度'"
                :aria-valuenow="rankRequirementPercent(requirement)"
                aria-valuemin="0"
                aria-valuemax="100"
              >
                <span :style="{ width: `${rankRequirementPercent(requirement)}%` }"></span>
              </div>
              <p class="rank-goal-hint">{{ rankRequirementHint(requirement) }}</p>
            </div>
          </li>
        </ol>
        <p v-else class="rank-goal-empty">此阶段没有额外前置条件，可以提升成长阶。</p>
        <p v-if="rankInfo.canRankUp" class="rank-goal-footer is-ready">条件已齐备，可以提升成长阶。</p>
        <p v-else class="rank-goal-footer">全部完成后，点击上方「提升成长阶」晋升。</p>
      </section>
      <div v-else-if="rankInfo && rankGoalIsMax" class="rank-goal-terminal" data-testid="rank-goal-terminal">
        已抵达当前成长体系的最高阶
      </div>
      <div v-else-if="rankInfoLoading" class="rank-goal-loading" role="status">正在读取下一成长阶的目标…</div>
      <div v-else-if="rankInfoFailed" class="rank-goal-loading is-error" role="status">
        暂时无法读取升阶目标
        <button type="button" @click="loadRankInfo">重试</button>
      </div>
      <div v-else-if="player?.id && !selectedConstellationKey()" class="rank-goal-loading">
        选择背后星后显示成长阶目标
      </div>
      <div class="allocation-hint">
        自由属性点 <b>{{ stats().freePoints ?? 0 }}</b>
        <span>攻{{ stats().allocatedAtk ?? 0 }} · 防{{ stats().allocatedDef ?? 0 }} · 速{{ stats().allocatedSpd ?? 0 }}</span>
      </div>
    </section>

    <section class="panel-section">
      <h3 class="panel-title"><span class="panel-title-label">属性</span></h3>
      <div class="stat-row" v-for="item in attrs" :key="item.key">
        <span class="stat-label">{{ item.label }}</span>
        <span
          :class="['stat-value', item.color, 'stat-value-clickable']"
          role="button"
          tabindex="0"
          :title="`${item.label}详情`"
          @click.stop="showTooltip(item.key, $event)"
        >{{ item.display }}</span>
      </div>
    </section>

    <section class="panel-section" id="equipGridContainer">
      <h3 class="panel-title">
        <button class="panel-title-link panel-title-link--text" @click="emit('openPanel', 'equipment')">装备</button>
        <span class="panel-title-actions">
          <button type="button" class="panel-title-action" title="打开装备面板" @click="emit('openPanel', 'equipment')">整备</button>
          <button type="button" class="panel-title-action" title="修复全部装备" @click="repairAllEquipment">修复</button>
        </span>
      </h3>
      <div class="equip-grid" id="equipGrid">
        <button
          v-for="slot in equipSlots"
          :key="slot.key"
          type="button"
          :class="['equip-slot', { empty: !slot.item }]"
          @click="emit('openPanel', 'equipment')"
        >
          <span class="slot-name">{{ slot.label }}</span>
          <span class="equip-info">
            <span class="equip-name-row">
              <span :class="['equip-name-text', { 'empty-text': !slot.item }]">{{ slot.item?.name || '未装备' }}</span>
            </span>
            <small v-if="slot.item?.detail" class="equip-meta-row">{{ slot.item.detail }}</small>
          </span>
        </button>
      </div>
    </section>

    <section class="panel-section">
      <h3 class="panel-title">
        <span class="panel-title-label">星币</span>
        <button type="button" class="panel-title-link" @click="emit('openPanel', 'trade')">商店</button>
      </h3>
      <div class="stat-row">
        <span class="stat-label">持有</span>
        <span class="stat-value stone-lower">{{ compact(player?.coins) }}</span>
      </div>
      <div class="stat-row stat-row--secondary">
        <span class="stat-label">故事碎片</span>
        <span class="stat-value">{{ compact(player?.storyFragments || player?.story_fragments) }}</span>
      </div>
    </section>

    <section class="panel-section">
      <h3 class="panel-title"><span class="panel-title-label">其他</span></h3>
      <div class="stat-row"><span class="stat-label">官职</span><span class="stat-value">{{ courtTitle }}</span></div>
      <div class="stat-row"><span class="stat-label">技能</span><span class="stat-value">{{ skillName }}</span></div>
      <div class="stat-row"><span class="stat-label">位置</span><span class="stat-value">{{ player?.currentLocationName || player?.currentLocation || player?.currentLocationKey || '未知区域' }}</span></div>
      <div class="stat-row"><span class="stat-label">今日求助</span><span class="stat-value stat-value-muted">{{ dailyHelp }}</span></div>
      <div class="stat-row"><span class="stat-label">今日协助</span><span class="stat-value stat-value-muted">{{ dailyAssist }}</span></div>
      <div class="stat-row">
        <span class="stat-label">仇敌</span>
        <button type="button" class="stat-value stat-value-link" @click="emit('openPanel', 'pk')">{{ nemesisLabel }}</button>
      </div>
    </section>

    <div class="player-panel-samsara-entry">
      <button type="button" class="player-panel-samsara-btn" @click="emit('openPanel', 'history')">
        <span aria-hidden="true">⟳</span>
        <span>轮回</span>
      </button>
    </div>

    <AllocDialog
      :visible="showAllocDialog"
      :player-id="Number(player?.id || player?.playerId || 0)"
      :stats="stats()"
      @close="showAllocDialog = false"
      @updated="(data: unknown) => { showAllocDialog = false; emit('playerUpdated', data) }"
    />
    <Teleport to="body">
      <div v-if="tooltipKey" class="ling-tooltip-backdrop" @click="hideTooltip"></div>
      <div
        v-if="tooltipKey"
        class="ling-stats-tooltip"
        :style="{ left: `${clampX(tooltipX)}px`, top: `${clampY(tooltipY)}px` }"
      >
        <div class="ling-stats-tooltip-title">
          <span>{{ attrs.find(a => a.key === tooltipKey)?.label || (tooltipKey === 'rank' ? '成长阶' : tooltipKey === 'exp' ? '经验' : '') }}</span>
          <button class="ling-stats-tooltip-close" @click.stop="hideTooltip">✕</button>
        </div>
        <div class="ling-stats-tooltip-text" style="white-space: pre-line;">{{ tooltipText }}</div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* ── LingVerse 左栏复刻：册页式窄栏，不使用卡片堆叠 ── */
.player-panel-replica {
  padding: 16px;
  color: var(--color-text-bright);
  font-family: var(--font-serif, serif);
}

.player-panel-replica .panel-section {
  margin: 0 0 20px;
  padding: 0;
  border: 0;
  background: none;
}

.player-panel-replica .panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 26px;
  margin: 0 0 8px;
  padding: 0;
  border: 0;
  color: var(--color-star);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
  text-shadow: none;
}

.player-panel-replica .panel-title-label,
.player-panel-replica .panel-title-link--text {
  flex: 0 1 auto;
  padding: 0 0 6px;
  border-bottom: 2px solid color-mix(in srgb, var(--color-star) 65%, transparent);
  white-space: nowrap;
}

.player-panel-replica .panel-title-spacer {
  flex: 1 1 auto;
}

.player-panel-replica .panel-title-status-edge {
  color: var(--color-muted);
  font-family: var(--font-mono, monospace);
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0;
}

.player-panel-replica .panel-title-link,
.player-panel-replica .panel-title-action {
  border: 1px solid color-mix(in srgb, var(--color-star) 30%, transparent);
  border-radius: 4px;
  background: color-mix(in srgb, var(--color-star) 7%, transparent);
  color: var(--color-star);
  cursor: pointer;
  font: inherit;
  font-size: 11px;
  letter-spacing: 0;
}

.player-panel-replica .panel-title-link {
  margin-left: auto;
  padding: 3px 7px;
}

.player-panel-replica .panel-title-link:hover,
.player-panel-replica .panel-title-action:hover {
  border-color: var(--color-star);
  background: color-mix(in srgb, var(--color-star) 16%, transparent);
}

.player-panel-replica .panel-title-actions {
  display: inline-flex;
  gap: 4px;
  margin-left: auto;
}

.player-panel-replica .panel-title-action {
  padding: 3px 6px;
}

.player-panel-replica .stat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 28px;
  padding: 2px 0;
  font-size: 13px;
}

.player-panel-replica .stat-row--name {
  align-items: flex-start;
}

.player-panel-replica .stat-label {
  flex: 0 0 auto;
  color: var(--color-text-dim);
  font-size: 12px;
}

.player-panel-replica .stat-value {
  min-width: 0;
  overflow: hidden;
  color: var(--color-text-bright);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-panel-replica .stat-value-clickable,
.player-panel-replica .stat-value-link {
  cursor: pointer;
  text-decoration: underline dashed;
  text-underline-offset: 4px;
}

.player-panel-replica .stat-value-clickable:hover,
.player-panel-replica .stat-value-link:hover {
  color: var(--color-star-bright);
}

.player-panel-replica .stat-value-muted {
  color: var(--color-muted);
  font-weight: 500;
}

.player-panel-replica .realm-text,
.player-panel-replica .mp-text,
.player-panel-replica .insight-text { color: var(--color-system-bright); }
.player-panel-replica .hp-text { color: var(--color-danger); }
.player-panel-replica .atk-text { color: var(--color-system-bright); }
.player-panel-replica .def-text { color: var(--color-star); }
.player-panel-replica .speed-text,
.player-panel-replica .crit-text { color: var(--color-system); }
.player-panel-replica .power-text { color: var(--color-text-bright); }
.player-panel-replica .luck-text { color: var(--color-spirit); }

.player-panel-replica .progress-bar-container {
  position: relative;
  height: 16px;
  margin-top: 6px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-log);
}

.player-panel-replica .progress-bar {
  height: 100%;
  min-width: 0;
  border-radius: 8px;
  background: linear-gradient(90deg, var(--color-spirit), var(--color-system));
  transition: width 0.45s ease;
}

.player-panel-replica .progress-text {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--color-text-bright);
  font-size: 10px;
  font-weight: 600;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.7);
}

.player-panel-replica .role-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-top: 8px;
}

.player-panel-replica .ling-break-btn,
.player-panel-replica .ling-alloc-btn {
  width: 100%;
  height: 32px;
  margin: 0;
  border: 1px solid var(--color-system-border);
  border-radius: 6px;
  background: var(--color-system-bg);
  color: var(--color-system-bright);
  cursor: pointer;
  font-size: 12px;
  letter-spacing: 0;
}

.player-panel-replica .ling-break-btn:hover,
.player-panel-replica .ling-alloc-btn:hover {
  border-color: var(--color-system);
  background: var(--color-system-glow);
}

.player-panel-replica .allocation-hint {
  display: flex;
  justify-content: space-between;
  gap: 5px;
  margin-top: 6px;
  color: var(--color-text-dim);
  font-size: 10px;
  white-space: nowrap;
}

.player-panel-replica .allocation-hint b { color: var(--color-star); }
.player-panel-replica .allocation-hint span { overflow: hidden; color: var(--color-muted); text-overflow: ellipsis; }

.player-panel-replica .rank-goal-panel {
  margin-top: 9px;
  padding: 10px;
  border: 1px solid color-mix(in srgb, var(--color-star) 28%, var(--color-border));
  border-radius: 7px;
  background:
    linear-gradient(115deg, color-mix(in srgb, var(--color-star) 7%, transparent), transparent 62%),
    color-mix(in srgb, var(--color-card) 92%, transparent);
}

.player-panel-replica .rank-goal-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.player-panel-replica .rank-goal-heading-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.player-panel-replica .rank-goal-kicker {
  color: var(--color-star);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1.5px;
}

.player-panel-replica .rank-goal-heading-copy strong {
  overflow: hidden;
  color: var(--color-text-bright);
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-panel-replica .rank-goal-count {
  flex: 0 0 auto;
  padding: 4px 7px;
  border: 1px solid color-mix(in srgb, var(--color-star) 24%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-star) 9%, transparent);
  color: var(--color-star);
  font-size: 10px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.player-panel-replica .rank-goal-count.is-ready,
.player-panel-replica .rank-goal-footer.is-ready { color: var(--color-spirit); }

.player-panel-replica .rank-goal-description {
  margin: 6px 0 0;
  color: var(--color-muted);
  font-size: 10px;
  line-height: 1.45;
}

.player-panel-replica .rank-goal-list {
  display: grid;
  gap: 9px;
  margin: 9px 0 0;
  padding: 0;
  list-style: none;
}

.player-panel-replica .rank-goal-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: start;
  gap: 7px;
}

.player-panel-replica .rank-goal-marker {
  display: grid;
  width: 17px;
  height: 17px;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--color-star) 34%, transparent);
  border-radius: 50%;
  color: var(--color-star);
  font-size: 8px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.player-panel-replica .rank-goal-item.is-complete .rank-goal-marker {
  border-color: color-mix(in srgb, var(--color-spirit) 50%, transparent);
  background: color-mix(in srgb, var(--color-spirit) 12%, transparent);
  color: var(--color-spirit);
}

.player-panel-replica .rank-goal-item-content { min-width: 0; }

.player-panel-replica .rank-goal-item-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.player-panel-replica .rank-goal-label {
  min-width: 0;
  color: var(--color-text-dim);
  font-size: 10px;
  line-height: 1.35;
}

.player-panel-replica .rank-goal-progress-text {
  flex: 0 0 auto;
  color: var(--color-muted);
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.player-panel-replica .rank-goal-item.is-complete .rank-goal-progress-text { color: var(--color-spirit); }

.player-panel-replica .rank-goal-track {
  height: 3px;
  margin-top: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-border) 78%, transparent);
}

.player-panel-replica .rank-goal-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-star), var(--color-system));
  transition: width 0.35s ease;
}

.player-panel-replica .rank-goal-item.is-complete .rank-goal-track span {
  background: var(--color-spirit);
}

.player-panel-replica .rank-goal-hint {
  margin: 4px 0 0;
  color: var(--color-muted);
  font-size: 9px;
  line-height: 1.45;
}

.player-panel-replica .rank-goal-empty,
.player-panel-replica .rank-goal-footer,
.player-panel-replica .rank-goal-terminal,
.player-panel-replica .rank-goal-loading {
  margin: 8px 0 0;
  color: var(--color-muted);
  font-size: 10px;
  line-height: 1.45;
}

.player-panel-replica .rank-goal-footer {
  padding-top: 7px;
  border-top: 1px solid color-mix(in srgb, var(--color-border) 75%, transparent);
}

.player-panel-replica .rank-goal-loading.is-error { color: var(--color-danger); }

.player-panel-replica .rank-goal-loading button {
  margin-left: 6px;
  border: 0;
  background: transparent;
  color: var(--color-star);
  cursor: pointer;
  font: inherit;
  text-decoration: underline;
}

.player-panel-replica .stat-row--secondary .stat-value {
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 500;
}

.player-panel-replica .equip-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

.player-panel-replica .equip-slot {
  display: flex;
  align-items: stretch;
  min-height: 48px;
  padding: 0 10px 0 0;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-left: 3px solid color-mix(in srgb, var(--color-muted) 45%, transparent);
  border-radius: 8px;
  background: var(--color-card);
  cursor: pointer;
  text-align: left;
}

.player-panel-replica .equip-slot:hover {
  border-color: color-mix(in srgb, var(--color-star) 50%, transparent);
  border-left-color: var(--color-star);
  background: color-mix(in srgb, var(--color-star) 5%, var(--color-card));
}

.player-panel-replica .equip-slot.empty { border-style: dashed; border-left-style: solid; }

.player-panel-replica .slot-name {
  display: flex;
  flex: 0 0 52px;
  align-items: center;
  justify-content: center;
  padding: 0 2px;
  border-right: 1px solid color-mix(in srgb, var(--color-border) 85%, transparent);
  background: color-mix(in srgb, var(--color-log) 55%, transparent);
  color: var(--color-text-dim);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
  text-align: center;
  white-space: nowrap;
}

.player-panel-replica .equip-info {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  padding: 8px 0 8px 10px;
}

.player-panel-replica .equip-name-row { display: flex; min-width: 0; align-items: center; }
.player-panel-replica .equip-name-text {
  min-width: 0;
  overflow: hidden;
  color: var(--color-text-bright);
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.player-panel-replica .equip-name-text.empty-text { color: var(--color-muted); font-weight: 500; }
.player-panel-replica .equip-meta-row { color: var(--color-muted); font-size: 10px; }

.player-panel-replica .player-panel-samsara-entry { margin: 4px 0 12px; }
.player-panel-replica .player-panel-samsara-btn {
  display: inline-flex;
  width: 100%;
  min-height: 36px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid color-mix(in srgb, var(--color-star) 40%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-star) 12%, transparent);
  color: var(--color-star);
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 2px;
}
.player-panel-replica .player-panel-samsara-btn:hover {
  border-color: color-mix(in srgb, var(--color-star) 65%, transparent);
  background: color-mix(in srgb, var(--color-star) 18%, transparent);
}

/* ── 面板容器 ── */
.psp-root {
  position: relative;
}

/* 面板顶部蓝色微光装饰 */
.psp-root::before {
  content: '';
  position: sticky;
  top: 0;
  z-index: 2;
  display: block;
  height: 1px;
  margin: 0 -14px 0;
  background: linear-gradient(
    90deg,
    transparent 0%,
    var(--color-star-border) 20%,
    rgba(var(--color-star-rgb), 0.12) 50%,
    transparent 100%
  );
}

/* ── 分区标题增强 ── */
.psp-root :deep(.ling-side-section h3) {
  position: relative;
  padding-bottom: 8px;
  color: var(--color-system-bright);
  font-size: 12px;
}

/* 标题下方蓝线 */
.psp-root :deep(.ling-side-section h3::after) {
  content: '';
  position: absolute;
  left: 0;
  bottom: 0;
  width: 28px;
  height: 1px;
  background: linear-gradient(90deg, var(--color-star-border), transparent);
  transition: width 0.3s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1));
}

.psp-root :deep(.ling-side-section:hover h3::after) {
  width: 44px;
}

/* ── KV 键值对增强 ── */
.psp-root :deep(.ling-kv div) {
  padding: 2px 4px;
  border-radius: 3px;
  transition: background 0.15s;
}

.psp-root :deep(.ling-kv div:hover) {
  background: var(--color-star-glow);
}

.psp-root :deep(.ling-kv dt) {
  font-size: 11px;
  letter-spacing: 1px;
}

/* 等级蓝色发光 */
.psp-root :deep(.gold) {
  text-shadow: var(--glow-star);
}

/* ── 进度条增强 ── */
.psp-root :deep(.ling-progress) {
  border-radius: 4px;
  border-color: var(--color-border);
}

.psp-root :deep(.ling-progress span) {
  border-radius: 4px;
  background: linear-gradient(90deg, var(--color-system), var(--color-star), var(--color-system));
  background-size: 200% 100%;
  animation: pspShimmer 3s ease-in-out infinite;
}

@keyframes pspShimmer {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

/* ── 成长晋升 / 商店按钮 ── */
.psp-root :deep(.ling-break-btn),
.psp-root :deep(.ling-market-btn) {
  border-radius: 5px;
  letter-spacing: 1px;
  font-weight: 600;
  transition: all 0.25s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1));
}

.psp-root :deep(.ling-break-btn:hover),
.psp-root :deep(.ling-market-btn:hover) {
  transform: translateY(-1px);
}

.psp-root :deep(.ling-break-btn:active),
.psp-root :deep(.ling-market-btn:active) {
  transform: scale(0.98);
}

/* ── 属性分配区增强 ── */
.psp-root :deep(.ling-alloc-section) {
  border-radius: 5px;
  background: var(--color-log);
  border-color: var(--color-border);
  transition: border-color 0.3s;
}

.psp-root :deep(.ling-alloc-section:hover) {
  border-color: var(--color-star-border);
}

.psp-root :deep(.ling-alloc-btn) {
  letter-spacing: 1px;
  font-weight: 600;
  border-radius: 5px;
  transition: all 0.25s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1));
}

.psp-root :deep(.ling-alloc-btn:hover) {
  transform: translateY(-1px);
}

/* ── 属性列表 ── */
.psp-root :deep(.ling-attrs .ling-info-btn) {
  opacity: 0;
  transition: opacity 0.2s;
}

.psp-root :deep(.ling-attrs div:hover .ling-info-btn) {
  opacity: 1;
}

/* ── 装备区增强 ── */
.psp-root :deep(.ling-equipment-list div) {
  border-radius: 5px;
  transition: all 0.25s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1));
}

.psp-root :deep(.ling-equipment-list div:hover) {
  transform: translateX(2px);
}

/* ── 分区最后一项 ── */
.psp-root :deep(.ling-side-section:last-of-type) {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

/* ── 信息按钮 ── */
.psp-root :deep(.ling-info-btn) {
  transition: all 0.2s var(--ease-out-expo, cubic-bezier(0.16, 1, 0.3, 1)) !important;
}

/* ── 移动端适配 ── */
@media (max-width: 760px) {
  .psp-root {
    padding: 10px 10px 18px;
  }

  .psp-root :deep(.ling-side-section) {
    padding-bottom: 12px;
    margin-bottom: 12px;
  }
}
</style>
