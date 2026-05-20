// 化身位阶配置 — 9 个位阶的升阶条件与奖励 (F/E/D/C/B/A/S/SS/SSS)
// 位阶排序: F < E < D < C < B < A < S < SS < SSS

const AVATAR_RANKS = [
  {
    rankKey: 'F',
    rankName: '临时化身',
    displayName: 'F级·临时化身',
    description: '刚被剧本系统记录，只拥有最低限度行动资格。',
    order: 1,
    nextRankKey: 'E',
    requirements: [],
    rewards: {}
  },
  {
    rankKey: 'E',
    rankName: '剧本幸存者',
    displayName: 'E级·剧本幸存者',
    description: '完成初步生存，能够稳定参与基础剧本。',
    order: 2,
    nextRankKey: 'D',
    requirements: [
      { type: 'level_min', value: 2, label: '等级达到 2' },
      { type: 'story_fragments_min', value: 5, label: '故事碎片 ≥ 5' },
      { type: 'explorations_by_location', locationKey: 'ruined_station', count: 2, label: '探索废弃车站 ≥ 2 次' }
    ],
    rewards: {
      stats: { maxHp: 20, maxStamina: 10 },
      log: '你已从临时化身晋升为剧本幸存者。剧本开始认真对待你的存在。'
    }
  },
  {
    rankKey: 'D',
    rankName: '频道记录者',
    displayName: 'D级·频道记录者',
    description: '行动开始被频道记录，拥有初步故事价值。',
    order: 3,
    nextRankKey: 'C',
    requirements: [
      { type: 'level_min', value: 5, label: '等级达到 5' },
      { type: 'stage_completed', stageKey: 'main_ch01_paid_service', label: '完成主线阶段：付费服务开始' },
      { type: 'scenario_proof_min', value: 1, label: '剧本证明 ≥ 1' }
    ],
    rewards: {
      stats: { attack: 5, defense: 5, channelHeat: 20 },
      log: '你的行动开始被频道记录。从此以后，你的存在将在星流中留下痕迹。'
    }
  },
  {
    rankKey: 'C',
    rankName: '剧本执行者',
    displayName: 'C级·剧本执行者',
    description: '可以独立完成探索、战斗和阶段目标。',
    order: 4,
    nextRankKey: 'B',
    requirements: [
      { type: 'level_min', value: 10, label: '等级达到 10' },
      { type: 'titles_count_min', value: 3, label: '拥有至少 3 个称号' },
      { type: 'stage_completed', stageKey: 'main_ch02_meeting_protagonist', label: '完成主线阶段：主角与旗帜' }
    ],
    rewards: {
      stats: { insight: 3, willpower: 3 },
      log: '你已具备独立执行剧本的资格。世界 PK 系统已对你开放。'
    }
  },
  {
    rankKey: 'B',
    rankName: '星流候选者',
    displayName: 'B级·星流候选者',
    description: '开始被星座或频道关注，拥有成为故事核心的资格。',
    order: 5,
    nextRankKey: 'A',
    requirements: [
      { type: 'level_min', value: 15, label: '等级达到 15' },
      { type: 'channel_heat_min', value: 300, label: '频道热度 ≥ 300' },
      { type: 'pk_or_broadcast', pkRating: 1000, broadcastContribution: 100, label: 'PK评分 ≥ 1000 或星流放送贡献 ≥ 100' }
    ],
    rewards: {
      stats: { channelHeat: 50, leadership: 3 },
      log: '星流开始把你列入候选观察名单。高级星流放送开始关注你的每一步行动。'
    }
  },
  {
    rankKey: 'A',
    rankName: '故事承载者',
    displayName: 'A级·故事承载者',
    description: '拥有完整故事路线，能够在星流中留下显著痕迹。',
    order: 6,
    nextRankKey: 'S',
    requirements: [
      { type: 'level_min', value: 20, label: '等级达到 20' },
      { type: 'rare_title_required', label: '拥有至少一个稀有称号' },
      { type: 'stage_completed', stageKey: 'main_ch03_constellation_sponsor', label: '完成主线阶段：星座的注视' },
      { type: 'story_grade_min', value: 'ordinary', label: '故事位格 ≥ 普通故事' }
    ],
    rewards: {
      stats: { maxHp: 50, attack: 10, defense: 10 },
      storyGrade: 'notable',
      log: '你的故事终于拥有了足以被星流承认的重量。从此刻起，你是真正的故事承载者。'
    }
  },
  {
    rankKey: 'S',
    rankName: '星流支配者',
    displayName: 'S级·星流支配者',
    description: '能够影响星流走向的存在，拥有改变故事分支的力量。',
    order: 7,
    nextRankKey: 'SS',
    requirements: [
      { type: 'level_min', value: 30, label: '等级达到 30' },
      { type: 'channel_heat_min', value: 800, label: '频道热度 ≥ 800' },
      { type: 'scenario_proof_min', value: 5, label: '剧本证明 ≥ 5' },
      { type: 'titles_count_min', value: 5, label: '拥有至少 5 个称号' }
    ],
    resourceCost: { story_fragments: 30, constellationFavor: 2 },
    breakthroughRate: 0.75,
    rewards: {
      stats: { maxHp: 80, attack: 15, defense: 15, willpower: 5, insight: 3 },
      channelHeat: 100,
      storyGrade: 'heroic',
      log: '你已突破凡人的界限，成为星流的支配者。星座们开始以你为中心重新排列。'
    }
  },
  {
    rankKey: 'SS',
    rankName: '终章铭刻者',
    displayName: 'SS级·终章铭刻者',
    description: '在星流终章中留下铭刻，成为无法被抹去的存在。',
    order: 8,
    nextRankKey: 'SSS',
    requirements: [
      { type: 'level_min', value: 40, label: '等级达到 40' },
      { type: 'channel_heat_min', value: 1500, label: '频道热度 ≥ 1500' },
      { type: 'scenario_proof_min', value: 10, label: '剧本证明 ≥ 10' },
      { type: 'rare_title_required', label: '拥有至少一个稀有称号' },
      { type: 'story_grade_min', value: 'heroic', label: '故事位格 ≥ 英雄故事' }
    ],
    resourceCost: { story_fragments: 50, constellationFavor: 5, abyssMark: 3 },
    breakthroughRate: 0.55,
    rewards: {
      stats: { maxHp: 120, attack: 25, defense: 25, willpower: 10, insight: 5, leadership: 5 },
      channelHeat: 200,
      storyGrade: 'legendary',
      log: '你的名字已铭刻于终章之上。即使世界线偏移，也无法抹去你的存在。'
    }
  },
  {
    rankKey: 'SSS',
    rankName: '全知读者',
    displayName: 'SSS级·全知读者',
    description: '超越剧本的存在，成为所有世界线的观测者与记录者。',
    order: 9,
    nextRankKey: null,
    requirements: [
      { type: 'level_min', value: 50, label: '等级达到 50' },
      { type: 'channel_heat_min', value: 3000, label: '频道热度 ≥ 3000' },
      { type: 'scenario_proof_min', value: 20, label: '剧本证明 ≥ 20' },
      { type: 'titles_count_min', value: 8, label: '拥有至少 8 个称号' },
      { type: 'story_grade_min', value: 'legendary', label: '故事位格 ≥ 传说故事' }
    ],
    resourceCost: { story_fragments: 100, constellationFavor: 10, abyssMark: 8 },
    breakthroughRate: 0.35,
    rewards: {
      stats: { maxHp: 200, attack: 40, defense: 40, willpower: 20, insight: 10, leadership: 10, luck: 5 },
      channelHeat: 500,
      storyGrade: 'mythic',
      log: '你已超越所有已知的剧本，成为真正的全知读者。从此刻起，你不再只是故事的观测者——你是所有世界线的中心。'
    }
  }
];

// 星流段位配置 — 根据 channelHeat 自动计算
const STARSTREAM_TIERS = [
  { key: 'nameless_watcher', label: '无名观测者', minHeat: 0 },
  { key: 'channel_newcomer', label: '频道新星', minHeat: 50 },
  { key: 'plot_disturber', label: '剧情扰动者', minHeat: 150 },
  { key: 'starstream_focus', label: '星流焦点', minHeat: 300 },
  { key: 'worldline_shifter', label: '世界线偏移者', minHeat: 600 },
  { key: 'final_chapter_watcher', label: '终章注视者', minHeat: 1000 }
];

// 故事位格配置
const STORY_GRADES = [
  { key: 'ordinary', label: '普通故事', order: 1 },
  { key: 'notable', label: '显著故事', order: 2 },
  { key: 'heroic', label: '英雄故事', order: 3 },
  { key: 'legendary', label: '传说故事', order: 4 },
  { key: 'mythic', label: '神话故事', order: 5 }
];

function getAvatarRankConfig() { return AVATAR_RANKS; }
function getStarstreamTier(channelHeat) {
  let tier = STARSTREAM_TIERS[0];
  for (const t of STARSTREAM_TIERS) {
    if (channelHeat >= t.minHeat) tier = t;
  }
  return tier;
}
function getStoryGradeLabel(gradeKey) {
  const g = STORY_GRADES.find(x => x.key === gradeKey);
  return g ? g.label : '普通故事';
}

module.exports = { AVATAR_RANKS, STARSTREAM_TIERS, STORY_GRADES, getAvatarRankConfig, getStarstreamTier, getStoryGradeLabel };
