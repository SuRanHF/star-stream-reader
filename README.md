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
- 更新历史：`data/changelog.json` 驱动，前端按版本比对 localStorage，未读新版本弹出更新弹窗

## 安装与运行

```bash
npm install         # 安装依赖
npm start           # 启动服务器 (localhost:3000)
npm test            # 运行冒烟测试 (299 测试点)
npm run reset-db    # 重置数据库
```

## 项目结构

```
/server.js                    # Express 入口、中间件、路由、seed
/db/
  init.sql                    # 完整 schema (34 张表)
  database.js                 # 连接管理与迁移
/middleware/
  authMiddleware.js           # JWT 认证 + 玩家归属校验
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
  seedExplorationEvents.js    # 探索事件池 (42 事件)
  seedNarrative.js            # 碎片化叙事 (20+ 物品记忆/10 地点回响/12 NPC 残影)
  changelog.json              # 更新历史
/public/                      # 前端
  index.html                  # 主页面 (暗黑沉浸式布局)
  styles.css                  # 样式 (深色 + 金色/青绿点缀)
  /src/
    main.js                   # 入口
    api.js                    # fetch 封装
    ui.js                     # DOM 渲染
    gameClient.js             # 游戏状态机
    storage.js                # localStorage 缓存
AGENTS.md                     # LLM 辅助开发指令
CONTRIBUTING.md               # 贡献指南 (含 UI 保护规则)
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

## 🛡️ UI 保护规则（严格约束）

> 本项目的 UI 层经过大规模审计修复，CSS 变量、类名、DOM 元素 ID、渲染函数之间存在**深度耦合**。对以下文件/代码的任何不当修改都会导致界面断裂。**所有贡献者（人 / AI）必须严格遵守。**

### 📋 受保护文件清单

| 文件 | 保护级别 | 允许的操作 | **严禁的操作** |
|------|---------|-----------|---------------|
| `public/styles.css` | 🔴 **绝对保护** | 追加新的 CSS class 定义；新增 CSS 变量（须同时在 `:root` 和 `.night-theme` 中定义） | 除追加外**任何修改**：删除/重命名任何已有 class、修改/删除任何已有 CSS 变量的值、修改任何已有选择器的 `display` / `position` / `flex` / `grid` / `z-index` / `overflow` 属性、修改过渡动画的 `transition` 属性 |
| `public/index.html` | 🔴 **绝对保护** | 新增功能所需的 DOM 元素（须使用新 `id`）；修改纯文本内容 | 删除或修改已有元素的 `id`、`class`、`onclick`、`data-feature` 等属性；改变任何已有 DOM 元素的层级结构（嵌套关系）；修改弹窗/抽屉/面板的 HTML 骨架 |
| `public/src/ui.js` | 🔴 **绝对保护** | 新增渲染方法（返回的 HTML 只能使用已有 CSS class）；修改 `LABELS` 对象中的文本映射值 | **修改以下任一函数的实现体**（见下方精确列表）；在所有已有渲染方法的返回值中增加/删除/修改 HTML 标签、class、内联样式 |
| `public/src/gameClient.js` | 🟡 **中等保护** | 新增游戏功能逻辑、API 调用、事件处理函数；修改数据请求流程 | **修改以下任一函数的实现体**：`openFeature()`、`handleNavigation()`；修改 `openDrawer()` / `closeDrawer()` 的调用时序；修改弹窗的打开关闭逻辑 |
| `public/src/api.js` | 🟢 **软保护** | 新增 API 方法 | 修改 `request()` 基方法的错误处理逻辑和响应解析逻辑 |

---

### 🔒 精确受保护函数清单

#### 🚫 ui.js —— 禁止修改实现体的函数

| 函数签名 | 行号 | 禁止修改的原因 |
|----------|------|---------------|
| `renderLeftPanel(player, globalWLS)` | ~24 | 左侧角色面板 — 输出的 class 被 CSS 精确匹配 |
| `renderMainActionBar(player)` | ~307 | 主操作栏按钮区 — 按钮文字/行为被多处依赖 |
| `renderCurrentEventPanel(chapter, choices, lockedChoices)` | ~201 | 事件面板 — 选项列表的 HTML 结构被 `_renderPopupChoices` 和弹窗共用 |
| `renderSocialActionBar()` | ~365 | 社交操作栏 |
| `renderStoryInline(chapter, choices, lockedChoices)` | ~399 | 剧情内联面板 |
| `renderEnding(ending)` | ~406 | 结局展示 |
| `renderExploreResult(result)` | ~415 | 探索结果展示 |
| `renderLocations(locations)` | ~553 | 地图列表 |
| `renderInventory(items)` | ~575 | 背包物品列表 |
| `renderEquipment(equipped, available, activeSets)` | ~599 | 装备列表（equip/unequip 按钮 class 耦合） |
| `renderSkills(skills, factionSkills)` | ~678 | 技能列表 |
| `renderAllTitles(titles)` | ~749 | 称号列表 |
| `renderPKOpponents(opponents)` | ~771 | PK 对手列表 |
| `renderPKRecords(records)` | ~791 | PK 记录 |
| `renderPKResult(result)` | ~805 | PK 结果弹窗内容（与 `pkModalOverlay` 耦合） |
| `renderRankings(rankings)` | ~825 | 排行榜 |
| `renderAvatarRankPanel(data, playerId)` | ~845 | 位阶详情面板（含升阶按钮 class） |
| `renderAvatarRankLeaderboard(rankings)` | ~947 | 位阶排行榜 |
| `renderBroadcastLeaderboard(rankings)` | ~968 | 放送贡献榜 |
| `renderStagePanel(status)` | ~987 | 阶段总览面板 |
| `renderBroadcast(activeEvents, history, playerId)` | ~1067 | 星流放送面板 |
| `renderBroadcastProgress(progress)` | ~1146 | 放送进度（与 `broadcastProgress` DOM ID 耦合） |
| `renderContributionRanking(ranking)` | ~1164 | 放送贡献排行（与 `broadcastRanking` DOM ID 耦合） |
| `renderMyContribution(data)` | ~1180 | 我的贡献（与 `myContribution` DOM ID 耦合） |
| `renderFriendList(friends, requests)` | ~1196 | 好友列表（好友请求卡片的 class 耦合） |
| `renderChat(messages, playerId)` | ~1248 | 聊天面板 |
| `renderChangelog(changelog)` | ~2157 | 更新历史（输出的 card 结构/class 被弹窗和抽屉共用） |
| `renderSettings()` | ~2192 | 设置面板（输出结构被 CSS 精确匹配） |
| `renderDetailedStats(player, globalWLS)` | ~1732 | 详细属性面板（与 `renderLeftPanel` 联动） |
| `renderUnderworldPanel(deadList, currentPlayerId)` | ~2130 | 冥界面板 |
| `renderStageIndicator(player)` | ~2290 | 阶段指示器 |
| `renderDescriptionPanel(player, context)` | ~2442 | 描述面板（与 `descriptionPanel` DOM ID 耦合） |
| `renderArchive(player)` | ~2559 | 档案面板 |
| `renderTradePanel(...)` | ~2753 | 交易面板 |
| `renderPartyPanel(...)` | ~2821 | 组队面板 |
| `renderFactionPanel(...)` | ~2894 | 阵营面板 |
| `renderQuestPanel(...)` | ~2982 | 任务面板 |
| `renderFactionSkillsPanel(...)` | ~3048 | 阵营技能面板 |
| `_renderPopupChoices(choices, lockedChoices, chapterConsumed)` | ~1362 | 弹窗选项渲染（与 `popupChoices` DOM ID 强耦合） |
| `_renderStatsDesc(player)` | ~2455 | 属性描述（渲染方法，被 `renderDescriptionPanel` 调用） |
| `_renderEquipmentDesc(player)` | ~2506 | 装备描述 |
| `_renderCombatDesc(player)` | ~2519 | 战斗描述 |
| `_renderConstellationLore(player)` | ~2528 | 星座传说 |

#### 🚫 ui.js —— 禁止修改的交互函数

| 函数签名 | 行号 | 禁止修改的原因 |
|----------|------|---------------|
| `openDrawer(title, contentHTML)` | ~497 | 抽屉打开流程：操作 `rightDrawer` / `drawerBody` DOM、classList 状态机（`open` / `hidden` / `entering`）、300ms 转场耦合 |
| `closeDrawer()` | ~526 | 抽屉关闭流程：移除 `open` / `entering` class、与 `descriptionPanel` 的 `.shifted` class 联动、300ms 转场耦合 |
| `dismissPopup(overlayId, onDone)` | ~1322 | 弹窗关闭流程：`closing` class + 280ms setTimeout + `onDone` 回调；所有弹窗调用此函数关闭 |
| `setupModals()` | ~1305 | 弹窗初始化：绑定 11 个弹窗 overlay 的点击外部关闭行为、全局模态行为统一 |
| `renderSettingsPanel()` | ~2186 | 设置面板刷新：内联调用 `renderSettings()` 并注入 `drawerBody` |
| `renderChangelog(changelog)` | ~2157 | 更新历史渲染：被 `openFeature('changelog')` 和版本弹窗两处调用 |

#### 🚫 gameClient.js —— 禁止修改的交互函数

| 函数签名 | 行号 | 禁止修改的原因 |
|----------|------|---------------|
| `openFeature(featureName)` | ~366 | 功能导航中枢：switch 语句包含 20+ 个 case、每个分支对应一个功能面板的加载和渲染、与 `handleNavigation()` 联动 |
| `handleNavigation(featureName)` | ~432 | 导航处理：更新导航高亮 + 调用 `openFeature()`；被所有侧边栏按钮的 `onclick` 和 `data-feature` 属性调用 |

---

### 📌 受保护 DOM 元素 ID 清单

以下 ID 被 JS 硬引用，**不得删除或改名**：

```
#rightDrawer, #drawerBody, #drawerTitle, #drawerHeader
#descriptionPanel, #leftPanel
#topStatus, #logStream
#currentEventPanel, #endingInline
#avatarRankBadge, #rankUpBtn, #rankNextInfo, #titleList
#btnContinueExplore, #btnCamp, #btnQuickExplore
#broadcastMarquee, #broadcastScrollText
#broadcastProgress, #broadcastRanking, #myContribution
#warningOverlay, #warningMessage, #warningConfirm, #warningCancel
#pkModalOverlay, #pkModalContent
#storyPopupOverlay, #popupNarrative, #popupChoices, #popupStatGains, #popupContinueBtn, #storyPopupBody
#explorePopupOverlay, #explorePopupBody
#combatPopupOverlay, #combatPopupBody
#mapOverlay, #mapBody
#constellationPopupOverlay, #constellationPopupBody
#changelogOverlay, #changelogVersion, #changelogList
#underworldPopupOverlay
#modalOverlay
#feedbackOverlay
#freePointsLabel
```

---

### 🎨 受保护 CSS 变量清单

以下 CSS 变量在 **day 和 night 双主题**中均有定义，**不得删除或修改名称**，修改值必须同时改两个主题：

**:root（日间）**
```
--bg-canvas, --bg-panel, --bg-panel2, --bg-card, --bg-hover, --bg-input
--border, --border-light, --border-gold, --border-dim
--text, --text-dim, --text-bright, --text-muted, --text-primary, --text-secondary
--gold, --gold-rgb, --gold-dim, --gold-dim-rgb
--teal, --teal-dim
--red, --red-dim
--green, --green-dim
--purple, --purple-light
--blue, --orange
--success, --danger, --panel-bg
--glow-gold, --glow-teal, --glow-primary
--top-height, --bottom-height, --left-width, --right-nav-width, --drawer-width
--font-ui, --font-body, --font-mono
--fw-normal, --fw-medium, --fw-bold, --fw-headline
--bg-log, --transition-fast, --transition-smooth, --brightness-pct
```

`.night-theme`（夜间）包含同名变量但不同值，**必须同步修改**。

---

### ⚙️ 受保护交互状态机

#### 抽屉生命周期（`rightDrawer`）

```
关闭状态:  drawer.classList = ['hidden']
打开动画:  drawer.classList = ['open', 'entering']  // body 加 'entering' class
打开完成:  drawer.classList = ['open']
关闭动画:  drawer.classList = [] (无 class)
关闭完成:  drawer.classList = ['hidden']
```

**与描述面板联动：**
```javascript
// 打开抽屉时，如果描述面板已展开：
descPanel.classList.add('shifted');    // 描述面板左移让位
descPanel.classList.remove('shifted'); // 抽屉关闭后恢复
```

**转场时间常量（严禁修改）：**
- `UI.DRAWER_TRANSITION_MS` = `300` — 抽屉滑入/滑出动画时长（与 CSS `transition: 0.3s` 同步）
- `UI.POPUP_TRANSITION_MS` = `280` — 弹窗淡入/淡出动画时长

---

### 🚫 禁令清单（LLM 强制）

1. ❌ **禁止** 在任何文件中添加 `style="..."` 内联样式。新样式先在 `styles.css` 加 class，再用 `class=""` 引用。
2. ❌ **禁止** 删除或重命名已有 CSS 类名 / 元素 ID / 函数名。项目无测试覆盖 UI 层，改名 = 静默断裂。
3. ❌ **禁止** 修改已有 CSS 变量的值（修改必须在 `:root` 和 `.night-theme` 两端同步）。
4. ❌ **禁止** 修改选择器的 `display` / `position` / `flex` / `grid` / `z-index` / `overflow` / `transition` 属性。
5. ❌ **禁止** 修改 `styles.css` 编号 1–100 行的 CSS 变量定义块和 `:root` / `.night-theme` 区块。
6. ❌ **禁止** 修改弹窗 / 抽屉 / 面板的打开关闭行为和过渡动画。
7. ❌ **禁止** 修改导航切换流程（`openFeature()` → `handleNavigation()`）和页面布局结构。
8. ❌ **禁止** 删除 `<head>` 中的字体声明（`@font-face` / Google Fonts 链接）。
9. ❌ **禁止** 修改 `index.html` 中 `#changelogOverlay`、`#warningOverlay`、`#modalOverlay`、`#pkModalOverlay`、`#storyPopupOverlay`、`#explorePopupOverlay`、`#combatPopupOverlay`、`#mapOverlay`、`#underworldPopupOverlay`、`#constellationPopupOverlay`、`#feedbackOverlay` 这 11 个弹窗 overlay 的 DOM 结构和 class。
10. ❌ **禁止** 修改 `rightDrawer` / `drawerBody` / `drawerHeader` / `drawerTitle` 的 DOM 结构。
11. ❌ **禁止** 修改 `nav-item` / `nav-item-secondary` 侧边栏导航按钮的 `data-feature` 属性值。
12. ❌ **禁止** 在 `ui.js` 中用 `element.style.xxx =` 替代已有 class 的使用。如需新样式 → 追加 class。

### ✅ 功能开发的正确做法

```javascript
// ❌ 错误：添加内联样式
element.style.color = 'var(--gold)';

// ✅ 正确：使用已有 CSS 类
element.classList.add('text-gold');

// 如果不存在需要的 CSS 类，先在 styles.css 追加定义，再引用
```

```css
/* ❌ 错误：修改已有类的布局属性 */
.drawer-header { display: block; }

/* ✅ 正确：新增独立的类（不改变已有选择器） */
.drawer-header-compact { padding: 4px; }
```

```javascript
// ❌ 错误：修改受保护函数的实现
UI.renderChangelog = function(changelog) {
  // ...新实现...  // 禁止！renderChangelog 的输出结构被抽屉和弹窗两处共用
};

// ✅ 正确：新增自己的渲染函数
UI.renderMyNewFeature = function(data) {
  // ...新实现，使用已有的 CSS 工具类...
};
```

### 📖 参考文档

- **`AGENTS.md`** — LLM/AI 辅助开发核心指令（技术栈注意点、路由认证、UI 保护规则、常见陷阱）
- **`CONTRIBUTING.md`** — 人工贡献者完整指南（提交前检查清单、CSS 命名规范）

---

## 版权说明

- 本项目为《全知读者视角》(sing N song 原作) 的改编同人游戏
- 剧情、角色、称号、结局均基于原作改编
- 不复制任何第三方网站源码或 UI 资源
- 不绕过任何网站的反爬机制或 Cloudflare 保护
