# CLAUDE.md

## 项目概况

全知读者视角 是一个后端+前端的文字冒险游戏。后端 Express + SQLite，前端 HTML + CSS + Vanilla JS。

## Project Rules

- 后端负责所有业务逻辑和校验。
- 前端只负责展示和交互，不存储关键游戏状态。
- 剧情数据存储在 SQLite 中，不硬编码在 DOM 事件里。
- 使用项目级 subagents 分工：backend-architect, database-designer, story-system-designer, title-ending-designer, frontend-implementer, qa-reviewer。
- 不复制受版权保护的小说正文。
- 不爬取受保护的小说网站。
- 不绕过 Cloudflare 或反爬机制。
- routeHistory 必须防止重复领奖。
- 称号必须影响玩法，不能只是展示文本。
- 后端必须校验选项合法性，不能只依赖前端禁用按钮。
- 用户导入的文本只作为草稿内容，不作为默认数据。

## 技术约定

- `better-sqlite3` 为同步 API，服务层直接返回结果，无需 async/await。
- 路由层用 try/catch 包装，统一返回 `{ code, message }` 错误格式。
- JSON 字段在服务层解析和序列化，路由层只传原生对象。
- 数据库种子数据只在 chapters 表为空时执行一次。
- 前端 JS 模块使用 const 对象挂载在全局作用域（不使用 ES module）。

## 文件职责

| 文件 | 职责 |
|------|------|
| `server.js` | Express 启动、中间件、路由挂载、seed 调用 |
| `db/database.js` | SQLite 连接管理、schema 初始化 |
| `routes/*.js` | 请求解析、参数校验、调用服务、返回 JSON |
| `services/storyService.js` | 章节获取、选项校验（五重规则）、选项执行 |
| `services/playerService.js` | 玩家 CRUD、状态更新、日志追加 |
| `services/titleService.js` | 称号解锁条件评估、互斥检查 |
| `services/endingService.js` | 结局条件评估、优先级排序 |
| `services/saveService.js` | 存档创建、列表、加载 |
| `public/src/api.js` | fetch 封装 |
| `public/src/ui.js` | DOM 渲染函数 |
| `public/src/gameClient.js` | 游戏状态机 |
| `public/src/storage.js` | localStorage 缓存 |
| `public/src/main.js` | DOMContentLoaded 入口 |

## Validation

Before finishing, verify:
- npm install 成功。
- npm start 启动后端，无报错。
- SQLite 数据库自动创建并完成 seed。
- 浏览器打开 localhost:3000 页面能进入游戏。
- 能创建玩家。
- 能读取当前章节并看到选项。
- 点击选择能推动剧情。
- 已选路线灰显锁定。
- 后端拒绝重复选择。
- 刷新页面进度不丢失。
- 称号在满足条件后解锁。
- 结局在终章正确触发。
- 存档/读档正常。
- README.md 和 CLAUDE.md 完整。
- 浏览器控制台无明显报错。
