# AGENTS.md

## 关键命令

```bash
npm start        # 启动服务器 (localhost:3000)
npm test         # 运行冒烟测试 (单文件, 无测试框架)
npm run reset-db # 重置数据库
```

## 技术栈注意点

- **数据库**: `sql.js` (SQLite WebAssembly), **非** `better-sqlite3`。`initDb()` 是 async，但 `db.prepare().run()/.get()/.all()` 是同步的。
- **每次写入自动保存**: `db.prepare().run()` 内部自动调用 `saveDb()` 写回磁盘，无需手动 save。
- **错误格式**: 路由层用 try/catch，统一返回 `{ success, error: { code, message } }`。
- **成功格式**: `{ success, data }`。
- **JSON 字段**: 服务层 parse/stringify，路由层只传原生对象。

## 项目架构

```
server.js          # 入口: 中间件, 路由挂载, 多轮 seed
db/database.js     # sql.js 连接, 自动迁移
services/*.js      # 业务逻辑 (同步, 直接 return)
routes/*.js        # 参数校验 + 调用服务 + 返回 JSON
public/src/*.js    # 前端 (挂载全局对象, 非 ES module)
data/seed*.js      # 种子数据 (第一次启动自动执行)
```

## 路由认证分类

| 路由 | 认证要求 |
|------|---------|
| `/api/auth/*` | 公开 (注册/登录) |
| `/api/rankings`, `/api/health`, `/api/changelog` | 公开 |
| `/api/broadcast` GET (active/history) | 公开 |
| `/api/admin`, `/api/ai-director`, `/api/feedback` | 公开 (由路由内部校验) |
| **其余所有 `/api/*`** | `authRequired` 中间件 (Bearer JWT) |

## 中间件

- `authRequired`: 校验 JWT token，注入 `req.user` (401 无 token / token 过期)
- `requireOwnPlayer`: 校验玩家归属权 (403 访问他人绑定玩家)，无绑定或未传 playerId 则放行

## 选项类型系统 (choice_type)

| 类型 | 消耗章节? | 特点 |
|------|----------|------|
| `action` | 否 | 每章只可选一个 action (mutual exclusion), 不推进剧情 |
| `repeatable` | 否 | 类似 action |
| `progress` | 是 | 推进到下一章, 设置 `pending_next_chapter` |
| `decision` | 是 | 类似 progress |
| `stage_final` | 是 | 推进 + `completes_stage=1` |
| `hide_after_use` | 否 | 一次性, 用完隐藏 |

## 冒险游戏核心循环

```
探索 → 触发事件 → 积累阶段进度 → 触发 final_story_event
→ 选择 stage_final → 推进下一阶段 → 结局
```

- **Chapter 消耗后**: 存入 `consumed_chapters_json`, `current_chapter` 不变, `pending_next_chapter` 设置
- **探索推进章节**: 调用 `exploreService.tryAdvanceChapter(playerId)` 消费 `pending_next_chapter` 并解锁下一章

## 玩家 JSON 字段清单

`stats_json` 包含: `hp, maxHp, stamina, maxStamina, level, exp, maxExp, defense, attack, speed, worldLineShift, channelHeat, isResting, lastRecoveryAt` 等

`stage_progress_json`: `{ visitedNodes[], storyEventsTriggered[], sideEventsTriggered[], bossClues{}, explorationsByLocation{}, storyPity, locationPools[], ... }`

`consumed_chapters_json`: `["ch1_01_last_train", ...]`

`chapter_actions_json`: `{ "ch1_01_last_train": ["action_abc"] }`

`activity_history_json`: `[{ choice_key, timestamp, effects }]`

`route_history_json`: `["choice_key_1", ...]` (仅记录章节消耗选项, 不含 action/hide_after_use)

## 测试注意

- `npm test` = 单文件 `node tests/smoke.test.js`，无框架，无 watch
- 测试会创建/删除测试玩家 (前缀 `TestLogger`, `RecoveryTester`, `QuickActionTest`, `ResourceTest`, `StatsValidate`, `testauth_*`)
- 测试末尾自动 `closeDb()`, `process.exit(1)` on failure
- 需要 `.env` 文件 (缺少时自动生成开发用 JWT_SECRET 和 ADMIN_KEY)
- playwright 在 devDependencies 中但当前测试未使用

## 🛡️ UI 保护规则（LLM 强制指令）

> 本项目的界面样式、布局、交互逻辑经过大量修复。以下规则是**硬约束**，任何 AI 助手不得违反。

### 严禁修改的文件

| 文件 | 原因 | 允许的操作 |
|------|------|-----------|
| `public/styles.css` | CSS 变量、类名、选择器深度耦合 | 仅允许**追加**新 class 定义 |
| `public/index.html` | DOM 元素 id/class 被 JS 硬引用 | 仅允许新增元素或修改文本 |
| `public/src/ui.js` | 渲染方法输出结构被 CSS 精确匹配 | 仅允许新增渲染方法或修改文本 |

### 严禁修改的函数

- `UI.renderChangelog()` / `renderSettings()` / `renderLeftPanel()` / `renderMainActionBar()`
- `UI.openDrawer()` / `closeDrawer()` / `dismissPopup()` / `setupModals()`
- `GameClient.openFeature()` / `handleNavigation()`
- 任何包含 `.classList.add/remove`、`.innerHTML =`、`style.xxx =` 的 DOM 操作

### 禁令清单

1. ❌ **禁止**添加 `style="..."` 内联样式。新样式先在 `styles.css` 加工具类，再用 `class=""` 引用。
2. ❌ **禁止**删除或重命名已有 CSS 类名/元素 ID/函数名。
3. ❌ **禁止**修改已有 CSS 变量的值、选择器的 `display/position/flex/grid/z-index/overflow` 属性。
4. ❌ **禁止**修改弹窗/抽屉/面板的打开关闭行为和过渡动画。
5. ❌ **禁止**修改导航切换流程和页面布局结构。

### 功能开发的正确模式

```js
// ❌ 错误
element.style.color = 'var(--gold)';

// ✅ 正确
element.classList.add('text-gold');
```

```css
/* ❌ 错误：修改已有类 */
.drawer-header { display: block; }

/* ✅ 正确：新增类 */
.drawer-header-compact { padding: 4px; }
```

---

## 常见陷阱

- `initDb()` 是 async，但之后所有 db 操作是同步的
- 数据库文件路径: `data/game.db`
- 配置文件 `dotenv` 加载 `.env`，JWT_SECRET/ADMIN_KEY/LLM_API_KEY 为环境变量
- 未绑定 user_id 的旧玩家数据任何登录用户可访问 (requireOwnPlayer 兼容逻辑)
- CLI 界面 (没有 --help)，所有配置通过环境变量
- `.gitignore` 排除 `data/*.db`, `.env`, `authorized_content/*` (保留 README.md)
