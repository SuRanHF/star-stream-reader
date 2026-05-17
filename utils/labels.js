// 集中式中文标签映射

const STAT_LABELS = {
  attack: '攻击', defense: '防御', speed: '速度', hp: '生命', maxHp: '最大生命',
  critRate: '暴击率', critDamage: '暴击伤害',
  intelligence: '智慧', combat: '战斗能力', leadership: '领导力',
  bond: '羁绊', cruelty: '残酷', insight: '洞察',
  stamina: '体力', maxStamina: '最大体力', explorationPower: '探索力',
  luck: '幸运', dropRate: '掉落率',
  rating: '评分', pkWins: 'PK胜', pkLosses: 'PK负', pkStreak: '连胜',
  worldLineShift: '世界线偏移', channelHeat: '频道热度',
  freePoints: '自由点数', allocatedAtk: '已分配攻击',
  allocatedDef: '已分配防御', allocatedSpd: '已分配速度', allocatedCrit: '已分配暴击',
  level: '等级', exp: '经验值',
  atk: '攻击', def: '防御', spd: '速度',
};

const RARITY_LABELS = {
  common: '普通', uncommon: '稀有', rare: '精良',
  epic: '史诗', legendary: '传说',
};

const SLOT_LABELS = {
  weapon: '武器', armor: '防具', accessory: '饰品', relic: '遗物',
};

const EVENT_TYPE_LABELS = {
  story: '主线剧情', side_story: '支线剧情', battle: '战斗',
  elite_battle: '精英战', boss_clue: 'Boss线索', opportunity: '机遇',
  resource: '资源', hidden: '隐藏事件', nothing: '无事件',
};

const BROADCAST_STATUS_LABELS = {
  draft: '草稿', active: '进行中', completed: '已完成',
  failed: '失败', expired: '已过期', rewarded: '已发奖', cancelled: '已取消',
};

const BROADCAST_EVENT_TYPE_LABELS = {
  world_boss: '世界Boss', exploration_drive: '探索驱动',
  story_hunt: '剧情狩猎', pk_tournament: 'PK锦标赛',
  faction_conflict: '阵营冲突', disaster: '灾厄',
  opportunity_rain: '机遇放送', stage_support: '阶段支援',
};

const SKILL_TYPE_LABELS = {
  attack: '攻击', passive: '被动', defense: '防御',
  exploration: '探索', pk: 'PK', story: '剧情',
};

const ITEM_TYPE_LABELS = {
  consumable: '消耗品', material: '材料', story_item: '剧情道具',
  equipment: '装备', key_item: '关键道具',
};

const CHOICE_TYPE_LABELS = {
  action: '行动', repeatable: '可重复', progress: '剧情',
  decision: '决策', stage_final: '阶段最终',
  locked: '锁定', special: '特殊',
};

function statLabel(key) { return STAT_LABELS[key] || key; }
function rarityLabel(key) { return RARITY_LABELS[key] || key; }
function slotLabel(key) { return SLOT_LABELS[key] || key; }
function eventTypeLabel(key) { return EVENT_TYPE_LABELS[key] || key; }
function broadcastStatusLabel(key) { return BROADCAST_STATUS_LABELS[key] || key; }
function broadcastEventTypeLabel(key) { return BROADCAST_EVENT_TYPE_LABELS[key] || key; }
function skillTypeLabel(key) { return SKILL_TYPE_LABELS[key] || key; }
function itemTypeLabel(key) { return ITEM_TYPE_LABELS[key] || key; }
function choiceTypeLabel(key) { return CHOICE_TYPE_LABELS[key] || key; }

function resolveChapterName(chapterKey) {
  if (!chapterKey) return '';
  try {
    const { getDb } = require('../db/database');
    const row = getDb().prepare('SELECT title FROM chapters WHERE chapter_key = ?').get(chapterKey);
    return row ? row.title : chapterKey;
  } catch (e) { return chapterKey; }
}

function resolveLocationName(locationKey) {
  if (!locationKey) return '';
  try {
    const { getDb } = require('../db/database');
    const row = getDb().prepare('SELECT name FROM locations WHERE location_key = ?').get(locationKey);
    return row ? row.name : locationKey;
  } catch (e) { return locationKey; }
}

function resolveEquipmentName(equipmentKey) {
  if (!equipmentKey) return '';
  try {
    const { getDb } = require('../db/database');
    const row = getDb().prepare('SELECT name FROM equipment WHERE equipment_key = ?').get(equipmentKey);
    return row ? row.name : equipmentKey;
  } catch (e) { return equipmentKey; }
}

function resolveSkillName(skillKey) {
  if (!skillKey) return '';
  try {
    const { getDb } = require('../db/database');
    const row = getDb().prepare('SELECT name FROM skills WHERE skill_key = ?').get(skillKey);
    return row ? row.name : skillKey;
  } catch (e) { return skillKey; }
}

function resolveMonsterName(monsterKey) {
  if (!monsterKey) return '';
  try {
    const { getDb } = require('../db/database');
    const row = getDb().prepare('SELECT name FROM monsters WHERE monster_key = ?').get(monsterKey);
    return row ? row.name : monsterKey;
  } catch (e) { return monsterKey; }
}

function getStaticLabelMaps() {
  return {
    statLabels: STAT_LABELS, rarityLabels: RARITY_LABELS, slotLabels: SLOT_LABELS,
    eventTypeLabels: EVENT_TYPE_LABELS, broadcastStatusLabels: BROADCAST_STATUS_LABELS,
    broadcastEventTypeLabels: BROADCAST_EVENT_TYPE_LABELS, skillTypeLabels: SKILL_TYPE_LABELS,
    itemTypeLabels: ITEM_TYPE_LABELS, choiceTypeLabels: CHOICE_TYPE_LABELS,
  };
}

module.exports = {
  STAT_LABELS, RARITY_LABELS, SLOT_LABELS,
  EVENT_TYPE_LABELS, BROADCAST_STATUS_LABELS, BROADCAST_EVENT_TYPE_LABELS,
  SKILL_TYPE_LABELS, ITEM_TYPE_LABELS, CHOICE_TYPE_LABELS,
  statLabel, rarityLabel, slotLabel, eventTypeLabel,
  broadcastStatusLabel, broadcastEventTypeLabel,
  skillTypeLabel, itemTypeLabel, choiceTypeLabel,
  resolveChapterName, resolveLocationName, resolveEquipmentName,
  resolveSkillName, resolveMonsterName,
  getStaticLabelMaps,
};
