# 全知读者视角 — 星流观测

基于 sing N song 原作《全知读者视角》改编的暗黑沉浸式文字 RPG。

## 项目定位

探索驱动的主线剧情推进，玩家通过探索触发剧情事件、收集资源、解锁称号、完成阶段目标，最终抵达结局。中央日志流展示所有游戏事件（战斗/剧情/奖励/系统），右侧导航抽屉承载功能面板。

## 核心循环

```
探索 → 触发事件(剧情/战斗/机遇/隐藏/Boss线索) → 积累资源和阶段进度
    → 触发最终剧情事件 → 阶段结算(剧情选择) → 推进下一阶段 → 结局
```

## 技术栈

- 后端：Node.js + Express
- 数据库：SQLite（better-sqlite3，同步 API）
- 前端：HTML + CSS + Vanilla JavaScript
- 暗黑沉浸式布局：左面板 + 中央日志流 + 右导航抽屉 + 底栏

## 安装与运行

```bash
npm install
npm start
# 浏览器访问 http://localhost:3000
npm test  # 运行测试套件
```

## 项目结构

```
/server.js                    # Express 入口、中间件、路由、seed
/db/
  init.sql                    # 完整 schema (23 张表)
  database.js                 # 连接管理与迁移
/routes/                      # REST API 路由层 (参数校验、调用服务)
/services/                    # 业务逻辑层
  playerService.js            # 玩家 CRUD、JSON 字段管理
  storyService.js             # 章节获取、选项校验、效果应用
  chapterService.js           # 主线阶段系统 (状态机/推进/目标检查)
  exploreService.js           # 探索事件系统 (概率/保底/奖励/风险)
  combatService.js            # 回合制自动战斗
  pkService.js                # PvP 对战 (叙事压制/评分)
  titleService.js             # 称号解锁/效果聚合/叙事身份
  endingService.js            # 结局条件评估
  saveService.js              # 存档/读档
  broadcastService.js         # 星流放送 (创建/参与/贡献/结算)
  worldStateService.js        # 世界状态聚合
  aiDirectorService.js        # AI 导演 (放送草案生成)
  inventoryService.js         # 背包系统
  skillService.js             # 技能系统
/data/                        # 种子数据
  seedStory.js                # 章节与选择
  seedMainChapters.js         # 主线阶段定义
  seedTitles.js               # 称号定义
  seedEndings.js              # 结局定义
  seedLocations.js            # 探索地图
  seedMonsters.js             # 怪物数据
  seedItems.js                # 道具数据
  seedEquipment.js            # 装备数据
  seedSkills.js               # 技能数据
  seedExplorationEvents.js    # 探索事件池
/public/                      # 前端
  index.html                  # 主页面 (暗黑沉浸式布局)
  styles.css                  # 样式 (深色 + 金色/青绿点缀)
  /src/
    main.js                   # 入口
    api.js                    # fetch 封装
    ui.js                     # DOM 渲染
    gameClient.js             # 游戏状态机
    storage.js                # localStorage 缓存
/tests/
  smoke.test.js               # 冒烟测试
```

## 数据库表 (23 张)

`chapters`, `choices`, `titles`, `endings`, `locations`, `monsters`, `items`, `equipment`, `skills`, `main_chapters`, `players`, `player_inventory`, `player_equipment`, `player_skills`, `battle_logs`, `exploration_logs`, `pk_records`, `rankings`, `saves`, `authorized_sources`, `story_drafts`, `exploration_events`, `broadcast_events`, `broadcast_participation`, `broadcast_contributions`

## API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/player/create` | 创建玩家 |
| GET | `/api/player/:id` | 获取玩家完整状态 |
| POST | `/api/player/reset/:id` | 重置进度 |
| GET | `/api/story/current/:playerId` | 获取当前章节与选项 |
| POST | `/api/story/choose` | 执行选择 (校验/效果/阶段推进) |
| GET | `/api/chapter/status/:playerId` | 获取阶段总览 |
| POST | `/api/chapter/advance` | 阶段推进 |
| POST | `/api/explore` | 执行探索 |
| GET | `/api/explore/locations/:playerId` | 获取已解锁地图 |
| GET | `/api/explore/progress/:playerId` | 获取阶段进度 |
| GET | `/api/titles/:playerId` | 已解锁称号 |
| GET | `/api/endings/:playerId` | 可达成结局 |
| GET | `/api/inventory/:playerId` | 背包 |
| POST | `/api/inventory/use` | 使用道具 |
| GET | `/api/equipment/:playerId` | 装备列表 |
| POST | `/api/equipment/equip` | 装备道具 |
| GET | `/api/skills/:playerId` | 技能列表 |
| GET | `/api/pk/opponents/:playerId` | PK 对手 |
| POST | `/api/pk/challenge` | PK 挑战 |
| GET | `/api/pk/rankings` | 排行榜 |
| POST | `/api/save` | 创建存档 |
| GET | `/api/save/:playerId` | 存档列表 |
| POST | `/api/save/load` | 读档 |
| GET | `/api/broadcast/active` | 活跃放送 |
| GET | `/api/broadcast/history` | 放送历史 |
| POST | `/api/broadcast/join` | 参加放送 |
| POST | `/api/broadcast/claim` | 领取奖励 |
| GET | `/api/world-state` | 世界状态摘要 |

## 核心系统

### 探索事件系统
- 8种事件类型：story / side_story / battle / elite_battle / boss_clue / opportunity / resource / hidden
- 每个地图有独立概率配置，称号和星流放送可修正概率
- story pity 保底机制：每5次非剧情探索必定触发剧情事件
- 事件产生奖励和风险 (worldLineShift / channelHeat)

### 主线阶段系统
- 8个主线阶段，状态机: locked → current → completed
- 阶段目标驱动：探索触发剧情事件 → 积累阶段进度 → 触发最终剧情事件 → stage_final 选择 → 推进下一阶段
- 支持的目标类型：visited_nodes_min / any_flag / story_events_min / explorations_by_location / boss_clues_min / opportunity_events_min / level_min / title_required / flag_required 等

### PvP 叙事压制
- 10种叙事身份：reader / regressor / king / king_without_throne / demon_king / constellation_agent / anti_constellation / salvation / sacrifice / observer / lonely_one
- 克制关系矩阵：最大 +30% 伤害加成，最大 -25% 惩罚
- 阶段压制（章节差距）+ 频道干扰（channelHeat 差距）
- 标准 ELO 评分公式

### 星流放送
- 8种事件类型：world_boss / exploration_drive / story_hunt / pk_tournament / faction_conflict / disaster / opportunity_rain / stage_support
- 生命周期：draft → active → completed → rewarded
- 玩家参与、贡献追踪、奖励领取
- 活跃放送影响 gameplay（概率修正、伤害加成、评分加成等）

### 称号效果
- combat_bonus: 战斗属性加成
- exploration_bonus: 探索体力/掉落/安全修正
- pk_bonus: PK 攻击/防御/评分加成
- event_prob_modifiers: 事件概率修正
- coin_multiplier: 硬币倍率
- stat_modifier: 剧情属性加成（接入战斗计算）
- narrative_tags: 叙事身份标签
- unlock_choices / unlock_locations / unlock_skills / block_endings / block_sponsors

## 版权说明

- 本项目为《全知读者视角》(sing N song 原作) 的改编同人游戏
- 剧情、角色、称号、结局均基于原作改编
- 不复制任何第三方网站源码或 UI 资源
- 不绕过任何网站的反爬机制或 Cloudflare 保护
- 用户导入文本仅作为授权草稿内容
