// ORV 全知读者视角 — 场景地图Mock数据

export interface SceneNode {
  id: string;
  name: string;
  locationKey?: string;
  volume: number;
  chapter: number;
  chapterName: string;
  description: string;
  x: number;
  y: number;
  type: 'main' | 'side' | 'hidden' | 'boss';
  unlocked: boolean;
  completed: boolean;
  minLevel: number;
  dangerLevel: number;
  keyItems: string[];
  connectedTo: string[];
}

export interface VolumeInfo {
  volume: number;
  title: string;
  subtitle: string;
  chapters: ChapterInfo[];
}

export interface ChapterInfo {
  chapter: number;
  title: string;
  key: string;
  unlocked: boolean;
  completed: boolean;
  scenes: SceneInfo[];
}

export interface SceneInfo {
  key: string;
  name: string;
  type: 'main' | 'side' | 'hidden' | 'boss';
  unlocked: boolean;
  completed: boolean;
  minLevel: number;
  dangerLevel: number;
  description: string;
  keyItems: string[];
}

// ===== 方案B 星图节点数据 =====
export const starChartNodes: SceneNode[] = [
  // ── 第一卷：灭世之后 ──
  {
    id: 'subway-3434',
    name: '3434号地铁',
    locationKey: 'ruined_station',
    volume: 1, chapter: 1, chapterName: '序幕·开始的读者',
    description: '故事开始的地铁车厢，在这里第一次见到回归者刘众赫。车厢内满是血渍与混乱，但也是觉醒之地。',
    x: 400, y: 480,
    type: 'main',
    unlocked: true, completed: true,
    minLevel: 1, dangerLevel: 1,
    keyItems: ['新手之书', '回归者之证'],
    connectedTo: ['geumho-station', 'bridge-gwangjin'],
  },
  {
    id: 'geumho-station',
    name: '金湖站',
    locationKey: 'geumho_station',
    volume: 1, chapter: 2, chapterName: '第一个避难所',
    description: '金湖站是第一个相对安全的避难所。在这里遇到了李智慧，并且首次接触到"星座"的存在。',
    x: 580, y: 380,
    type: 'main',
    unlocked: true, completed: true,
    minLevel: 3, dangerLevel: 2,
    keyItems: ['星座碎片', '应急粮食'],
    connectedTo: ['subway-3434', 'chungmuro', 'dongmyo'],
  },
  {
    id: 'chungmuro',
    name: '忠武路',
    locationKey: 'chungmuro',
    volume: 1, chapter: 3, chapterName: '忠武路的统治者',
    description: '忠武路站被一个自称"十王"的团体控制。在这里可以挑战小型BOSS，获取稀有道具。',
    x: 720, y: 300,
    type: 'boss',
    unlocked: true, completed: false,
    minLevel: 8, dangerLevel: 4,
    keyItems: ['十王之冠', '忠武路地图'],
    connectedTo: ['geumho-station', 'myeongdong', 'dongdaemun'],
  },
  {
    id: 'dongmyo',
    name: '东庙',
    locationKey: 'dongmyo',
    volume: 1, chapter: 2, chapterName: '第一个避难所',
    description: '东庙站聚集了大量幸存者，形成了小型交易市场。可以在这里交换物品和情报。',
    x: 500, y: 280,
    type: 'side',
    unlocked: true, completed: true,
    minLevel: 2, dangerLevel: 1,
    keyItems: ['情报碎片', '交易币'],
    connectedTo: ['geumho-station', 'dongdaemun'],
  },
  {
    id: 'dongdaemun',
    name: '东大门',
    locationKey: 'dongdaemun',
    volume: 1, chapter: 4, chapterName: '东大门之战',
    description: '东大门站外盘踞着强大的恶灵"火焰巨人"，需要组队才能挑战。',
    x: 650, y: 200,
    type: 'boss',
    unlocked: true, completed: false,
    minLevel: 10, dangerLevel: 6,
    keyItems: ['火焰之心', '东大门令牌'],
    connectedTo: ['dongmyo', 'chungmuro', 'myeongdong'],
  },
  {
    id: 'myeongdong',
    name: '明洞',
    locationKey: 'myeongdong',
    volume: 1, chapter: 4, chapterName: '东大门之战',
    description: '明洞的地下商业街隐藏着"星流图书馆"的入口，可以在这里学习新技能。',
    x: 820, y: 350,
    type: 'side',
    unlocked: true, completed: false,
    minLevel: 6, dangerLevel: 3,
    keyItems: ['古老典籍', '技能书页'],
    connectedTo: ['chungmuro', 'dongdaemun'],
  },
  {
    id: 'bridge-gwangjin',
    name: '广津大桥',
    locationKey: 'gwangjin_bridge',
    volume: 1, chapter: 5, chapterName: '跨越汉江',
    description: '通往江南的必经之路。大桥上布满了陷阱和怪物，是第一个高难度关卡。',
    x: 350, y: 580,
    type: 'main',
    unlocked: true, completed: false,
    minLevel: 12, dangerLevel: 7,
    keyItems: ['汉江通行证', '桥之守护者的遗物'],
    connectedTo: ['subway-3434', 'gangnam-station'],
  },
  {
    id: 'gangnam-station',
    name: '江南站',
    locationKey: 'gangnam_station',
    volume: 1, chapter: 5, chapterName: '跨越汉江',
    description: '江南站是首尔最大的地下城入口。高等级的怪物和丰厚的奖励等待着勇敢的读者。',
    x: 250, y: 660,
    type: 'main',
    unlocked: false, completed: false,
    minLevel: 15, dangerLevel: 8,
    keyItems: ['地下城钥匙', '江南区地图'],
    connectedTo: ['bridge-gwangjin', 'seoul-forest'],
  },
  {
    id: 'seoul-forest',
    name: '首尔森林',
    locationKey: 'seoul_forest',
    volume: 1, chapter: 5, chapterName: '跨越汉江',
    description: '曾经的城市公园变成了魔物森林。据说有传说中的"世界树苗"隐藏其中。',
    x: 150, y: 700,
    type: 'hidden',
    unlocked: false, completed: false,
    minLevel: 18, dangerLevel: 9,
    keyItems: ['世界树苗', '森林精灵之泪'],
    connectedTo: ['gangnam-station'],
  },

  // ── 第二卷：恶魔世界 ──
  {
    id: 'demon-gate',
    name: '恶魔之门',
    locationKey: 'demon_gate',
    volume: 2, chapter: 1, chapterName: '次元裂缝',
    description: '通往恶魔世界的次元裂缝。穿过这道门，将进入完全不同的异世界。',
    x: 100, y: 250,
    type: 'main',
    unlocked: false, completed: false,
    minLevel: 20, dangerLevel: 8,
    keyItems: ['次元之钥', '恶魔通行证'],
    connectedTo: ['demon-city', 'abyss-shore'],
  },
  {
    id: 'demon-city',
    name: '恶魔都市·巴力',
    volume: 2, chapter: 2, chapterName: '恶魔都市',
    description: '恶魔世界的中心都市。72柱恶魔中的强者统治着这座城市，充满了阴谋与交易。',
    x: 200, y: 120,
    type: 'main',
    unlocked: false, completed: false,
    minLevel: 22, dangerLevel: 7,
    keyItems: ['恶魔契约', '灵魂货币'],
    connectedTo: ['demon-gate', 'demon-castle'],
  },
  {
    id: 'demon-castle',
    name: '恶魔城',
    volume: 2, chapter: 3, chapterName: '魔王城攻略',
    description: '魔王居住的巨大城堡。需要集结足够的战力才能挑战最终BOSS。',
    x: 320, y: 80,
    type: 'boss',
    unlocked: false, completed: false,
    minLevel: 28, dangerLevel: 10,
    keyItems: ['魔王之角', '恶魔王冠'],
    connectedTo: ['demon-city'],
  },
  {
    id: 'abyss-shore',
    name: '深渊海岸',
    volume: 2, chapter: 2, chapterName: '恶魔都市',
    description: '恶魔世界边缘的黑色海岸。据说海底沉睡着远古的存在。',
    x: 50, y: 150,
    type: 'side',
    unlocked: false, completed: false,
    minLevel: 24, dangerLevel: 8,
    keyItems: ['深渊珍珠', '远古鳞片'],
    connectedTo: ['demon-gate', 'demon-city'],
  },

  // ── 第三卷：星流战争 ──
  {
    id: 'constellation-throne',
    name: '星座王座',
    volume: 3, chapter: 1, chapterName: '星流的试炼',
    description: '星流空间的核心区域，星座们在此观战下界。玩家可以获得星座的祝福。',
    x: 600, y: 600,
    type: 'main',
    unlocked: false, completed: false,
    minLevel: 30, dangerLevel: 9,
    keyItems: ['星座祝福', '星之碎片'],
    connectedTo: ['star-trial', 'nebula-market'],
  },
  {
    id: 'star-trial',
    name: '星之试炼场',
    volume: 3, chapter: 1, chapterName: '星流的试炼',
    description: '需要通过一系列考验才能获得星座认可的地方。每层试炼的难度递增。',
    x: 720, y: 520,
    type: 'main',
    unlocked: false, completed: false,
    minLevel: 32, dangerLevel: 9,
    keyItems: ['试炼证明', '星座印记'],
    connectedTo: ['constellation-throne'],
  },
  {
    id: 'nebula-market',
    name: '星云市场',
    volume: 3, chapter: 2, chapterName: '星云贸易',
    description: '星座之间的交易场所。可以在这里用故事碎片交换稀有道具和装备。',
    x: 500, y: 680,
    type: 'side',
    unlocked: false, completed: false,
    minLevel: 28, dangerLevel: 2,
    keyItems: ['交易令牌', '星云硬币'],
    connectedTo: ['constellation-throne'],
  },

  // ── 第四卷：终章之战 ──
  {
    id: 'final-wall',
    name: '终末之墙',
    volume: 4, chapter: 1, chapterName: '最后的壁垒',
    description: '世界尽头的巨大墙壁。上面刻满了所有读者的故事，是通往结局的唯一入口。',
    x: 400, y: 40,
    type: 'main',
    unlocked: false, completed: false,
    minLevel: 40, dangerLevel: 10,
    keyItems: ['故事之钥', '读者的意志'],
    connectedTo: ['eden-garden', 'void-throne'],
  },
  {
    id: 'eden-garden',
    name: '伊甸园',
    volume: 4, chapter: 1, chapterName: '最后的壁垒',
    description: '墙壁内部的神秘花园。据说这里保存着所有可能的故事结局。',
    x: 500, y: 10,
    type: 'side',
    unlocked: false, completed: false,
    minLevel: 42, dangerLevel: 8,
    keyItems: ['禁果', '伊甸之叶'],
    connectedTo: ['final-wall'],
  },
  {
    id: 'void-throne',
    name: '虚空王座',
    volume: 4, chapter: 2, chapterName: '终章',
    description: '最终BOSS的所在地。只有最古老的读者才能到达这里，见证一切的终结。',
    x: 300, y: 10,
    type: 'boss',
    unlocked: false, completed: false,
    minLevel: 45, dangerLevel: 10,
    keyItems: ['虚空冠冕', '终章之书'],
    connectedTo: ['final-wall'],
  },
];

// ===== 方案C 树形数据 =====
export const volumeTreeData: VolumeInfo[] = [
  {
    volume: 1,
    title: '灭世之后',
    subtitle: 'The World After the Fall',
    chapters: [
      {
        chapter: 1, title: '序幕·开始的读者', key: 'ch1-prologue',
        unlocked: true, completed: true,
        scenes: [
          { key: 'subway-3434', name: '3434号地铁', type: 'main', unlocked: true, completed: true, minLevel: 1, dangerLevel: 1, description: '故事开始的地铁车厢，觉醒之地。', keyItems: ['新手之书', '回归者之证'] },
          { key: 'subway-platform', name: '地铁月台', type: 'side', unlocked: true, completed: true, minLevel: 2, dangerLevel: 2, description: '地铁月台区域，可以找到初期补给。', keyItems: ['急救包', '简易武器'] },
        ],
      },
      {
        chapter: 2, title: '第一个避难所', key: 'ch2-shelter',
        unlocked: true, completed: true,
        scenes: [
          { key: 'geumho-station', name: '金湖站', type: 'main', unlocked: true, completed: true, minLevel: 3, dangerLevel: 2, description: '第一个安全的避难所，遇到李智慧。', keyItems: ['星座碎片', '应急粮食'] },
          { key: 'dongmyo', name: '东庙', type: 'side', unlocked: true, completed: true, minLevel: 2, dangerLevel: 1, description: '幸存者聚集的交易市场。', keyItems: ['情报碎片', '交易币'] },
        ],
      },
      {
        chapter: 3, title: '忠武路的统治者', key: 'ch3-chungmuro',
        unlocked: true, completed: false,
        scenes: [
          { key: 'chungmuro', name: '忠武路站', type: 'boss', unlocked: true, completed: false, minLevel: 8, dangerLevel: 4, description: '"十王"控制的据点。', keyItems: ['十王之冠'] },
          { key: 'chungmuro-alley', name: '忠武路小巷', type: 'side', unlocked: true, completed: false, minLevel: 5, dangerLevel: 2, description: '隐藏在小巷中的秘密商店。', keyItems: ['暗巷钥匙'] },
        ],
      },
      {
        chapter: 4, title: '东大门之战', key: 'ch4-dongdaemun',
        unlocked: true, completed: false,
        scenes: [
          { key: 'dongdaemun', name: '东大门', type: 'boss', unlocked: true, completed: false, minLevel: 10, dangerLevel: 6, description: '火焰巨人盘踞之地。', keyItems: ['火焰之心', '东大门令牌'] },
          { key: 'myeongdong', name: '明洞地下街', type: 'side', unlocked: true, completed: false, minLevel: 6, dangerLevel: 3, description: '隐藏着星流图书馆的入口。', keyItems: ['古老典籍', '技能书页'] },
        ],
      },
      {
        chapter: 5, title: '跨越汉江', key: 'ch5-han-river',
        unlocked: true, completed: false,
        scenes: [
          { key: 'bridge-gwangjin', name: '广津大桥', type: 'main', unlocked: true, completed: false, minLevel: 12, dangerLevel: 7, description: '布满陷阱和怪物的跨江大桥。', keyItems: ['汉江通行证'] },
          { key: 'gangnam-station', name: '江南站', type: 'main', unlocked: false, completed: false, minLevel: 15, dangerLevel: 8, description: '首尔最大地下城入口。', keyItems: ['地下城钥匙'] },
          { key: 'seoul-forest', name: '首尔森林', type: 'hidden', unlocked: false, completed: false, minLevel: 18, dangerLevel: 9, description: '传说中的世界树苗所在地。', keyItems: ['世界树苗', '精灵之泪'] },
        ],
      },
    ],
  },
  {
    volume: 2,
    title: '恶魔世界',
    subtitle: 'The Demon World',
    chapters: [
      {
        chapter: 1, title: '次元裂缝', key: 'ch6-rift',
        unlocked: false, completed: false,
        scenes: [
          { key: 'demon-gate', name: '恶魔之门', type: 'main', unlocked: false, completed: false, minLevel: 20, dangerLevel: 8, description: '通往恶魔世界的次元裂缝。', keyItems: ['次元之钥', '恶魔通行证'] },
        ],
      },
      {
        chapter: 2, title: '恶魔都市', key: 'ch7-demon-city',
        unlocked: false, completed: false,
        scenes: [
          { key: 'demon-city', name: '恶魔都市·巴力', type: 'main', unlocked: false, completed: false, minLevel: 22, dangerLevel: 7, description: '恶魔世界中心都市，阴谋与交易之地。', keyItems: ['恶魔契约', '灵魂货币'] },
          { key: 'abyss-shore', name: '深渊海岸', type: 'side', unlocked: false, completed: false, minLevel: 24, dangerLevel: 8, description: '黑色海岸，海底沉睡着远古存在。', keyItems: ['深渊珍珠', '远古鳞片'] },
        ],
      },
      {
        chapter: 3, title: '魔王城攻略', key: 'ch8-demon-castle',
        unlocked: false, completed: false,
        scenes: [
          { key: 'demon-castle', name: '恶魔城', type: 'boss', unlocked: false, completed: false, minLevel: 28, dangerLevel: 10, description: '最终BOSS魔王的巨大城堡。', keyItems: ['魔王之角', '恶魔王冠'] },
        ],
      },
    ],
  },
  {
    volume: 3,
    title: '星流战争',
    subtitle: 'The Star Stream War',
    chapters: [
      {
        chapter: 1, title: '星流的试炼', key: 'ch9-trial',
        unlocked: false, completed: false,
        scenes: [
          { key: 'constellation-throne', name: '星座王座', type: 'main', unlocked: false, completed: false, minLevel: 30, dangerLevel: 9, description: '星座观战的核心区域。', keyItems: ['星座祝福', '星之碎片'] },
          { key: 'star-trial', name: '星之试炼场', type: 'main', unlocked: false, completed: false, minLevel: 32, dangerLevel: 9, description: '获得星座认可的试炼之地。', keyItems: ['试炼证明'] },
        ],
      },
      {
        chapter: 2, title: '星云贸易', key: 'ch10-nebula',
        unlocked: false, completed: false,
        scenes: [
          { key: 'nebula-market', name: '星云市场', type: 'side', unlocked: false, completed: false, minLevel: 28, dangerLevel: 2, description: '星座间的交易场所。', keyItems: ['交易令牌', '星云硬币'] },
        ],
      },
    ],
  },
  {
    volume: 4,
    title: '终章之战',
    subtitle: 'The Final Chapter',
    chapters: [
      {
        chapter: 1, title: '最后的壁垒', key: 'ch11-wall',
        unlocked: false, completed: false,
        scenes: [
          { key: 'final-wall', name: '终末之墙', type: 'main', unlocked: false, completed: false, minLevel: 40, dangerLevel: 10, description: '刻满读者故事的巨大墙壁。', keyItems: ['故事之钥', '读者的意志'] },
          { key: 'eden-garden', name: '伊甸园', type: 'side', unlocked: false, completed: false, minLevel: 42, dangerLevel: 8, description: '保存所有可能结局的神秘花园。', keyItems: ['禁果', '伊甸之叶'] },
        ],
      },
      {
        chapter: 2, title: '终章', key: 'ch12-final',
        unlocked: false, completed: false,
        scenes: [
          { key: 'void-throne', name: '虚空王座', type: 'boss', unlocked: false, completed: false, minLevel: 45, dangerLevel: 10, description: '最终BOSS所在地，一切的终结。', keyItems: ['虚空冠冕', '终章之书'] },
        ],
      },
    ],
  },
];
