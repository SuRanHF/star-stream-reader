# 全知读者视角 — 星流观测

基于 sing N song 原作《全知读者视角》改编的多人联机开放世界文字 RPG。

## 核心定位

**无存档 · 服务端持久化 · 玩家独立实体 · 全服共享世界**

ORV 世界作为背景设定，玩家通过探索触发事件、战斗、收集资源、解锁称号、提升化身位阶。中央日志流展示所有游戏事件，右侧导航抽屉承载功能面板。全服玩家共享世界线偏移值，同星座玩家结成阵营，共同影响世界走向。

## 核心循环

```
探索 → 触发事件(剧情/战斗/NPC残影/地点回响/机遇/隐藏)
    → 累积资源 → 提升位阶(F→SSS) → 解锁新功能
    → 改变世界线 → 影响全服 → 阵营贡献 → 阵营战
```

---

## 玩法系统

### 化身位阶 (Avatar Rank)

核心成长轴，替代线性剧情推进。F→SSS 共 9 级。

| 位阶 | 解锁内容 |
|------|---------|
| F | 基础探索(1地点)、背包、休息 |
| E | 2地点、基础装备 |
| D | PK、3地点、技能槽1 |
| C | 全基础地点、交易市场、技能槽2 |
| B | 组队讨伐、世界Boss、技能槽3 |
| A | 全部机制、技能槽4 |
| S | 进阶Boss、突破加成 |
| SS | 精英探索区、技能槽5 |
| SSS | 全解锁、回归(Prestige)可用 |

**升阶条件**：等级、故事碎片、探索次数等多条件组合，成功率看完成度。
**回归**：SSS 后可重置为 F 级，永久保留 +2% 全属性加成（可叠加）。

### 探索事件系统

- 8 种事件类型：剧情 / 支线 / 战斗 / 精英 / Boss线索 / 机遇 / 资源 / 隐藏
- 每个地图有独立概率配置，称号和星流放送可修正概率
- 剧情保底：每 5 次非剧情探索必定触发剧情
- 探索消耗体力，可批量探索（×1/×5/×10），支持自动连续探索
- 探索中可触发 NPC 残影遭遇（8% 概率）、地点回响（40% 概率）

### 世界线偏移 (Worldline Shift)

全服共享的"混乱值"，所有玩家行为共同影响。

| 偏移值 | 全局效果 |
|--------|---------|
| ≥10 | 探索奖励 +10% |
| ≥25 | 战斗奖励 +25% |
| ≥50 | 故事事件概率 +8%，随机地点变为异常热点 |
| ≥100 | 全属性 +10% |

- 探索触发剧情 → 偏移 +0.5
- PK 击杀 → 偏移 +1
- 击杀 Boss → 偏移 -2（稳定世界线）
- 服务器每 60 秒自动衰减 1%

### 星座阵营系统

8 个星座阵营，选择背后星自动加入。全服共享阵营贡献分数。

| 阵营 | 特性 |
|------|------|
| 金乌神教 | 擅攻 · 暴击取向 · 灼热贯穿 |
| 黑焰龙渊 | 纯攻取向 · 速度抢先 · 防御偏低 |
| 火之审判庭 | 攻速均衡 · 暴击加成 · 正义之焰 |
| 深渊凝视者 | 暴击取向 · 弱点洞察 · 精确命中 |
| 命运编织会 | 速度取向 · 幸运提升 · 先手优势 |
| 冥界女王府 | 防御取向 · 持久战 · 死亡抗性 |
| 海上战神盟 | 防御取向 · 速度加成 · 坚不可摧 |
| 星流守望塔 | 均衡取向 · 硬币加成 · 全面稳定 |

- 探索/战斗/PK 自动为阵营贡献分数
- 每周一 0:05 结算阵营战，霸主阵营全员获得 +2 星座好感 + 全属性加成
- 霸主阵营成员享受 5% 探索故事概率 + 8% 战斗伤害 + 50% 交易手续费减免
- 非霸主阵营享受减半加成

### 星流放送 (Broadcast)

星座向全服发布的限时集体任务，8 种类型：
世界Boss / 探索驱动 / 剧情狩猎 / PK 锦标赛 / 阵营冲突 / 灾难 / 机遇之雨 / 阶段支援

- 生命周期：draft → active → completed → rewarded
- 全服进度追踪，达成目标全员获奖
- 调度引擎自动生成 2 个备选方案，用世界状态评分选出最优并激活
- 过期放送自动结算（成功/失败），失败时星座态度变冷

### 碎片化叙事

用具象记忆替代线性剧情，三种形式：

**物品记忆**（20+ 条）：打开物品详情时显示记忆文本，揭示物品背后的故事。

**地点回响**（10 条，每地点 2 条）：探索时 40% 概率触发环境描写，展现场景历史和氛围。

**NPC 残影**（12 个，含完整对话树）：探索时 8% 概率遭遇过去的 NPC 投影，每个提供 3 个对话选项，不同选择给出不同文本和奖励。

### 交易市场

玩家间用硬币自由买卖物品。
- 发布挂单：选择物品、数量、价格
- 其他玩家实时浏览和购买
- 霸主阵营成员享受交易手续费 50% 减免

### 组队讨伐

最多 3 人组队讨伐世界 Boss。
- 创建队伍 → 玩家加入 → 点"讨伐"开始
- Boss 属性按队伍人数动态调整
- 需位阶 C 级以上可创建队伍

### 聊天频道

三种频道：全服 / 阵营 / 私聊。调度引擎消息同步广播到全服频道。

### 好友系统

添加好友、查看在线状态、发送好友申请、最近互动记录。

### PvP 叙事压制 (PK)

- 10 种叙事身份：reader / regressor / king / king_without_throne / demon_king / constellation_agent / anti_constellation / salvation / sacrifice / observer / lonely_one
- 克制关系矩阵：最大 +30% 伤害加成
- 阶段压制 + 频道干扰
- 标准 ELO 评分公式

### 称号效果系统

- combat_bonus：战斗属性加成
- exploration_bonus：探索体力/掉落修正
- pk_bonus：PK 攻击/防御加成
- event_prob_modifiers：事件概率修正
- coin_multiplier：硬币倍率
- stat_modifier：剧情属性加成
- narrative_tags：叙事身份标签
- unlock_choices / unlock_locations / unlock_skills

### 手机端布局切换

4 种布局（仅移动端生效）：
- **默认经典**：经典底栏导航
- **星流仪表盘**：悬浮状态球 + FAB 快捷菜单 + 3-tab 底栏
- **沉浸式阅读器**：极简顶栏 + 半屏更多菜单 + 全屏故事体验
- **故事轮盘**：角色数据顶栏 + 四大分类卡片 + 汉堡侧滑菜单

### 显示设置

文字亮度/粗细/字体/昼夜模式，偏好 localStorage 持久化。

### 冥界系统

死亡玩家进入冥界，可自我复活（消耗硬币或献祭称号）或由其他玩家支付复活。

---

## 技术栈

- 后端：Node.js + Express
- 数据库：SQLite（sql.js，同步 API）
- 前端：HTML + CSS + Vanilla JavaScript
- 调度引擎：60s 心跳自动驱动（放送生成/结算、世界线衰减、PK 过期、阵营战）

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
  init.sql                    # 完整 schema (30+ 张表)
  database.js                 # 连接管理与迁移
/routes/                      # REST API 路由层 (20+ 路由文件)
/services/                    # 业务逻辑层 (25+ 服务文件)
  playerService.js            # 玩家 CRUD、JSON 字段管理
  storyService.js             # 章节获取、选项校验、效果应用
  chapterService.js           # 主线阶段系统 (状态机/推进/目标检查)
  exploreService.js           # 探索事件系统 (概率/保底/奖励/风险/世界线修正)
  combatService.js            # 回合制自动战斗
  pkService.js                # PvP 对战 (叙事压制/评分)
  titleService.js             # 称号解锁/效果聚合
  endingService.js            # 结局条件评估
  broadcastService.js         # 星流放送 (创建/参与/贡献/结算)
  schedulerService.js         # 全服调度引擎 (60s 心跳)
  worldlineService.js         # 世界线偏移 (共享状态/阈值/衰减/异常点)
  worldStateService.js        # 世界状态聚合
  factionService.js           # 星座阵营 (贡献/排行榜/周战/领域加成)
  tradeService.js             # 交易市场 (挂单/购买/取消)
  partyService.js             # 组队 Boss 讨伐
  chatService.js              # 聊天频道
  friendService.js            # 好友系统
  narrativeService.js         # 碎片化叙事 (物品记忆/地点回响/NPC残影)
  avatarRankService.js        # 化身位阶 (升阶/限制/回归)
  aiDirectorService.js        # AI 导演 (放送草案生成)
  inventoryService.js         # 背包系统
  skillService.js             # 技能系统
/data/                        # 种子数据
  seedStory.js                # 章节与选择
  seedMainChapters.js         # 主线阶段定义
  seedTitles.js               # 称号定义
  seedEndings.js              # 结局定义
  seedLocations.js            # 探索地图 (10 地点)
  seedMonsters.js             # 怪物数据 (37 怪物)
  seedItems.js                # 道具数据 (26 物品)
  seedEquipment.js            # 装备数据
  seedSkills.js               # 技能数据
  seedExplorationEvents.js    # 探索事件池
  seedNarrative.js            # 碎片化叙事 (20+物品记忆/10地点回响/12 NPC残影)
/public/                      # 前端
  index.html                  # 主页面 (暗黑沉浸式布局)
  styles.css                  # 样式 (深色 + 金色/青绿点缀)
  /src/
    main.js                   # 入口
    api.js                    # fetch 封装
    ui.js                     # DOM 渲染
    gameClient.js             # 游戏状态机
    storage.js                # localStorage 缓存
```

## 数据库表 (30+ 张)

`chapters`, `choices`, `titles`, `endings`, `locations`, `monsters`, `items`, `equipment`, `skills`, `main_chapters`, `players`, `player_inventory`, `player_equipment`, `player_skills`, `battle_logs`, `exploration_logs`, `pk_records`, `rankings`, `broadcast_events`, `broadcast_participation`, `broadcast_contributions`, `world_state`, `constellation_factions`, `faction_contributions`, `faction_wars`, `trade_listings`, `parties`, `party_members`, `item_memories`, `location_echoes`, `npc_ghosts`, `player_npc_encounters`, `chat_messages`, `friend_relations`

## API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 创建/登录玩家 |
| GET | `/api/player/:id` | 获取玩家完整状态 |
| POST | `/api/player/reset/:id` | 重置进度 |
| POST | `/api/player/peer-revive` | 复活其他玩家 |
| GET | `/api/player/dead-list` | 冥界玩家列表 |
| GET | `/api/story/current/:playerId` | 获取当前章节与选项 |
| POST | `/api/story/choose` | 执行选择 |
| GET | `/api/chapter/status/:playerId` | 获取阶段总览 |
| POST | `/api/chapter/advance` | 阶段推进 |
| POST | `/api/explore` | 执行探索 |
| GET | `/api/explore/locations/:playerId` | 已解锁地图 |
| GET | `/api/explore/progress/:playerId` | 阶段进度 |
| GET/POST | `/api/combat/*` | 战斗系统 |
| GET | `/api/titles/:playerId` | 已解锁称号 |
| GET | `/api/endings/:playerId` | 可达成结局 |
| GET | `/api/inventory/:playerId` | 背包 |
| POST | `/api/inventory/use` | 使用道具 |
| GET/POST | `/api/equipment/*` | 装备系统 |
| GET/POST | `/api/skills/*` | 技能系统 |
| GET | `/api/pk/opponents/:playerId` | PK 对手 |
| POST | `/api/pk/challenge` | PK 挑战 |
| GET/POST | `/api/broadcast/*` | 星流放送 |
| GET | `/api/worldline/status` | 世界线状态 |
| GET | `/api/worldline/history` | 世界线历史 |
| GET | `/api/factions` | 阵营列表 |
| GET | `/api/factions/:key` | 阵营详情 |
| GET/POST | `/api/trade/*` | 交易市场 |
| GET/POST | `/api/party/*` | 组队讨伐 |
| GET/POST | `/api/chat/*` | 聊天频道 |
| GET/POST | `/api/friends/*` | 好友系统 |
| GET | `/api/narrative/*` | 碎片化叙事 |
| GET | `/api/rankings/*` | 排行榜 |
| GET | `/api/avatar-rank/:playerId` | 位阶详情 |
| POST | `/api/avatar-rank/:playerId/prestige` | 回归 |
| GET | `/api/version` | 版本检测 |
| GET | `/api/changelog` | 更新历史 |
| POST | `/api/feedback` | 提交反馈 |
| POST | `/api/heartbeat` | 心跳（在线标记） |

---

## 版权说明

- 本项目为《全知读者视角》(sing N song 原作) 的改编同人游戏
- 剧情、角色、称号、结局均基于原作改编
- 不复制任何第三方网站源码或 UI 资源
- 不绕过任何网站的反爬机制或 Cloudflare 保护
