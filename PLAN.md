# 全知读者视角 — 开发计划

> 最后更新: 2026-06-03 (阶段四完成)

## 项目架构

```
reader-scenario-game/
├── springboot-backend/     # Spring Boot 3.2.5 + MySQL + MyBatis-Plus (端口 8080)
├── frontend-player/        # Vue 3 + TypeScript + Vite (玩家端)
├── frontend-admin/         # 管理后台 (待建)
└── docs/                   # 设计文档
```

后端 31 个 Controller，前端 8 个 Store + 24 个 API 模块 + 38 个 Vue 组件。

---

## 阶段一: UI 主题升级 ✅ 已完成

金→蓝深空主题迁移。31 文件，3603 行新增，1662 行删除。零金色残留。

---

## 阶段二: 核心面板对接真实数据 ✅ 已完成

几乎所有面板都通过以下两种方式连接了真实数据：
- **ConnectedPanel** (1267 行通用面板引擎) — 驱动 explore/hiddenScene/ranking/history/underworld/chat/trade/skills/faction/pk/quests/friends/titles/inventory/synthesis/equipment/rest/party/support/worldBoss/guide/notice/system 共 23 个功能面板
- **专用组件** — ScenarioPanel、ConstellationPanel、PlayerStatusPanel、DeathOverlay、ExploreResultDialog、BroadcastStrip 都直接通过 gameStore 或 API 获取数据

### 本轮完成
- [x] ScenarioPanel — 从 `exploreApi.getLocations()` 动态生成阶段进度/目标清单
- [x] ConstellationPanel — 从 `GET /player/constellations` 获取星座，支持切换
- [x] PlayerStatusPanel 硬编码修复 — "无名读者"改为从 `player.title/avatarRank` 取值，"第四面墙"改为从 `player.stigma` 取值
- [x] 23 个 ConnectedPanel 子面板 — 全部对接真实 API（含增删改查操作）
- [x] DeathOverlay — 复活逻辑对接 `playerApi.revive()`
- [x] BroadcastStrip — 从 `gameStore.broadcastSummary` (bootstrap 提供) 取值
- [x] ExploreResultDialog — 接收真实 `ExploreResult` API 响应

---

## 阶段三: 场景地图系统 🗺️ ✅ 已完成

目标：可交互的星图式场景地图。

### 本轮完成
- [x] **Demo 页面清理** — 删除 SceneMapDemo、IntegrationDemo、GameSceneMapPreview + IntegrationA/B/C 共 6 个 demo 文件，移除 3 条 demo 路由
- [x] **危险等级颜色编码** — F(灰 #788899)→E(蓝灰 #7a8aaa)→D(蓝 #4a8fcc)→C(紫 #7b5ecc)→B(亮蓝 #4a9fef)→A(红 #e05050)→S(紫亮 #c85aef)，显示在节点外环和详情 Sheet 中
- [x] **世界线进度展示** — 4 卷进度条显示已完成/总场景数，在星图筛选栏下方
- [x] **故事耗尽标记** — 已完成节点显示 ✓，未解锁节点显示 🔒
- [x] **主流程集成** — SceneMapPanel 已在 GameLayout 中通过「场景地图」按钮可用，实时切换 + 后端状态同步

---

## 阶段四: 多人交互系统 🕹️ ✅ 已验证

后端 API 全部就绪，前端 ConnectedPanel 已有完整的 UI 和交互逻辑。端到端验证结果：

- [x] **PK 双模式** — API: challengeId 14 创建成功。UI: 化身战场面板 7 个子 tab 正常渲染，可挑战列表 + 战斗记录
- [x] **悬赏求助** — API: 发布/接取/完成流程贯通，sharePercent 40%。UI: 支援面板完整（发布表单 + 悬赏列表 + 接取/取消按钮）。**注意：支援面板底部导航缺少入口按钮，需在 BottomNav 添加**
- [x] **WebSocket 实时通信** — realtimeClient 已连接（status: connected），处理 8 种事件（online/worldline/broadcast/quest/worldBoss/chat/system/stats）
- [x] **组队系统** — API: party_no PT202606031430096632，2 成员，leader 角色。UI: 队伍面板 4 个子 tab，队伍摘要 + 活跃队伍列表 + 加入/战斗/离开按钮
- [x] **世界 BOSS** — API: WB202606031320128484 "废弃车站灾厄核心"，伤害 174，HP 99.8%。UI: 灾厄面板 4 个子 tab，当前灾厄 + 伤害排行（rank #1）
- [x] **交易市场** — API: 上架/购买流程贯通，"体力面包" 50 币。UI: 鬼怪商店 10 个子 tab，市场摘要 + 交易记录
- [x] **好友系统** — API: 好友申请 pending 状态。UI: 同伴面板 4 个子 tab，搜索框 + 好友列表 + 待处理申请

### 发现的问题
1. **支援面板无入口** — BottomNav 缺少 support 按钮，面板代码完整但用户无法访问
2. **悬赏 API 500 错误** — `GET /api/bounty/my/26` 返回 500（服务器内部错误）
3. **广播靠 HTTP 轮询** — `GET /api/broadcast/active` 高频轮询（~3s），WebSocket 已连接但广播未走 WS 推送

---

## 阶段六: 放置 RPG 核心三系统 ✅ 已完成

称号系统 + 剧情推进约束 + 战斗位格/叙事压制。

### 称号系统 (20 个 ORV 原著称号)
- [x] `seed_growth.sql` 新增 20 个原著称号，包含 `strong_against_json` / `weak_against_json` / `tags_json`
- [x] 5 个叙事阵营：reader(读者) → abyss(深渊) ← king(王者) ← starstream(星流) ← reader, combat(战斗) 中立
- [x] 克制矩阵：strong_against 命中 → +40%, weak_against 命中 → -40%

### 剧情推进约束
- [x] `ExploreServiceImpl.findLowerUnfinishedStories()` — 低等级地点有未触发故事事件时，高等级地点屏蔽 story/side_story
- [x] 前端 `ExploreResultDialog` 显示剧情屏蔽黄色警告

### 战斗位格压制
- [x] `CombatServiceImpl.rankToValue()` — 位格转数值 (F=1 → SSS=9)
- [x] 公式: `clamp(1 + 位格差 × 0.15, 0.5, 3.0)`，每高一位格 +15% 伤害

### 战斗叙事压制
- [x] `CombatServiceImpl.narrativeSuppressionMultiplier()` — 玩家称号阵营 vs 怪物叙事标签
- [x] 玩家克制怪物 → 1.4x，被克制 → 0.6x；怪物对玩家同样有逆向叙事倍率
- [x] `Monster` 实体/VO 新增 `narrativeTags` / `narrative_tags_json` 字段
- [x] 4 个怪物种子数据已分配叙事标签

### 修改文件
| 文件 | 改动 |
|------|------|
| `seed_growth.sql` | +20 称号 INSERT IGNORE |
| `seed_monsters.sql` | 4 怪物增加 narrative_tags_json |
| `init.sql` | monsters 表新增 narrative_tags_json 列 |
| `Monster.java` / `MonsterVO.java` | 新增 narrativeTags 字段 |
| `MonsterServiceImpl.java` | toVO() 映射 narrativeTags |
| `ExploreServiceImpl.java` | +findLowerUnfinishedStories(), ~50 行 |
| `CombatServiceImpl.java` | +rankToValue, +narrativeSuppressionMultiplier, +parseStringList, 构造函数新增 LocationMapper/TitleMapper，calcDamage 传入压制倍率 |
| `exploreApi.ts` | ExploreResult 新增 stories_blocked/lower_unfinished |
| `ExploreResultDialog.vue` | 剧情屏蔽警告 UI |

---

## 阶段五: 管理与运维 🖥️ ✅ 已完成

- [x] **CLAUDE.md 更新** — 已更新为 Spring Boot + MySQL + Vue 3 技术栈
- [x] **管理后台前端** (frontend-admin/) — Vue 3 + Element Plus + TypeScript 已搭建
  - [x] 登录/鉴权（JWT + X-Admin-Key 双通道）
  - [x] 仪表盘（玩家统计）
  - [x] 玩家管理（搜索/分页/详情抽屉/属性编辑/快捷操作/物品发放/日志）
  - [x] 反馈管理（状态筛选/状态更新）
  - [x] 操作日志（管理员操作审计）
  - [x] 调度器（摘要/全局Tick/任务执行/日志分页）
  - [x] 世界Boss管理（创建/开启/结算）
  - [x] 星流放送（活跃广播展示，CRUD 待后端补端点）
  - [x] 交易审计（市场挂单展示，审计 API 待补）
- [x] **后端管理 CRUD 补齐** — 12 组实体的完整 CRUD 端点（AdminCrudController: list/get/create/update/delete），前端「数据管理」页面 + 广播/交易页面已升级为完整 CRUD
  - 探索事件/NPC鬼怪/星流广播/星座势力/道具/装备/技能/怪物/称号/地点/任务/世界Boss
  - 前端: adminApi.crudList/crudGet/crudCreate/crudUpdate/crudDelete + AdminCrudView 通用管理页面
- [x] **部署验证** — Nginx + systemd 配置已验证并修复：VITE_BASE=/admin/ 子路径、alias+try_files 兼容、HTTP 401 重定向 BASE_URL 适配

---

## 阶段三·五: 战斗系统闭合 ⚔️ ✅ 已完成

目标：探索中的 battle_placeholder 事件 → 独立 CombatController 完整战斗。

### 本轮完成
- [x] **种子数据** — 10 个 battle_placeholder 事件分布到 10 个地点，4 种怪物 (station_rat/broken_avatar_shadow/station_keeper_fragment/mall_hunger_echo)
- [x] **后端事件映射** — ExploreServiceImpl.mapEventTypeToLogType() 补充 `case "battle_placeholder": return "battle"`
- [x] **API 契约** — combatApi.ts (CombatResult/startCombat/resolveCombat) + exploreApi.ts (monster_key 字段)
- [x] **战斗 UI** — ExploreResultDialog 新增 battleMode，3 阶段 (idle/fighting/done)，含战斗/撤退/奖励展示
- [x] **种子阈值修复** — count >= 40 → >= 70，确保新事件能正常播种
- [x] **端到端验证** — 浏览器测试：探索→遭遇怪物→战斗→胜利奖励，全流程贯通

---

## 当前任务聚焦 (2026-06-03)

**阶段六已完成**: 放置 RPG 核心三系统 — 称号(20 原著) + 剧情推进约束 + 位格/叙事压制战斗

**剩余技术债务**:
- [ ] 前端缺少单元测试和 E2E 测试
- [ ] 大量未提交的变更（50+ 文件修改）

---

## 技术债务

- [x] ~~CLAUDE.md 严重过时~~ — 已更新为 Spring Boot + MySQL + Vue 3
- [ ] 前端缺少单元测试和 E2E 测试
- [x] ~~frontend-admin 目录不存在~~ — 已搭建，9 个管理页面，构建通过
- [x] ~~3 个 demo 路由页面~~ — 已清理
- [x] ~~场景地图 mockData~~ — 已与后端 exploreApi 双向同步，mockData 仅作为位置/坐标/连接的静态参考
- [x] ~~支援面板缺少导航入口~~ — BottomNav 已添加"悬赏支援"按钮
- [x] ~~悬赏 API `GET /api/bounty/my/{playerId}` 返回 500~~ — Map.of() 不允许 null 值，改用 LinkedHashMap
