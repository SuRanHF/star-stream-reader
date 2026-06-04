# CLAUDE.md

## 项目概况

全知读者视角 — 文字冒险游戏。后端 Spring Boot 3.2.5 + MySQL + MyBatis-Plus，前端 Vue 3 + TypeScript + Vite。

```
reader-scenario-game/
├── springboot-backend/     # Spring Boot 3.2.5 + MySQL + MyBatis-Plus (端口 8080)
├── frontend-player/        # Vue 3 + TypeScript + Vite (玩家端, 端口 5173)
├── frontend-admin/         # Vue 3 + Element Plus + TypeScript + Vite (管理后台, 端口 5174)
└── docs/                   # 设计文档
```

后端 31 个 Controller，前端 8 个 Pinia Store + 24 个 API 模块 + 38 个 Vue 组件。

## Project Rules

- 后端负责所有业务逻辑和校验。
- 前端只负责展示和交互，不存储关键游戏状态。
- 使用项目级 subagents 分工。
- 不复制受版权保护的小说正文。
- 不爬取受保护的小说网站。
- 后端必须校验选项合法性，不能只依赖前端禁用按钮。
- 称号必须影响玩法，不能只是展示文本。

## 技术约定

### 后端 (Spring Boot)
- Spring Boot 3.2.5 + MySQL 8.0 + MyBatis-Plus 3.5.5
- 统一返回 `ApiResponse<T>` 格式：`{ code: 200, message: "success", data: ... }`
- 认证：JWT token（`LoginUserContext`），`@ConstellationRequired` AOP 校验星座
- 种子数据通过 `src/main/resources/db/seed_*.json` 自动播种
- 管理端点鉴权：角色="admin" 或 `X-Admin-Key` 请求头
- 端口 8080，context-path: `/api`

### 前端玩家端 (Vue 3)
- Vue 3 Composition API (`<script setup lang="ts">`)
- Pinia Setup Store 风格
- Axios 拦截器：自动注入 JWT，401 跳转登录
- 路径别名 `@/` → `src/`
- TailwindCSS + 自定义深空主题 CSS 变量
- Token 存储：localStorage key `lingverse_token`
- 端口 5173

### 前端管理后台 (Vue 3, 待建)
- 与玩家端技术栈一致
- 端口 5174
- 鉴权：JWT (role=admin) + X-Admin-Key 双通道

## 文件职责 (后端)

| 目录 | 职责 |
|------|------|
| `config/` | Spring 配置、CORS、MyBatis-Plus 分页 |
| `security/` | JWT 过滤器、LoginUserContext、ConstellationRequiredAspect |
| `controller/` | REST 控制器（31 个） |
| `service/impl/` | 业务逻辑实现 |
| `mapper/` | MyBatis-Plus Mapper 接口 |
| `entity/` | 数据库实体类 |
| `model/` | DTO/Request/Response 模型 |
| `websocket/` | WebSocket 实时通信 |

## 文件职责 (前端)

| 目录 | 职责 |
|------|------|
| `src/api/` | Axios 封装 + 24 个 API 模块 |
| `src/stores/` | Pinia Store（auth, game, chat, quest, realtime 等） |
| `src/components/` | Vue 组件（按功能域分包） |
| `src/components/panels/ConnectedPanel.vue` | 通用面板引擎（驱动 23 个子面板） |
| `src/views/` | 页面级组件 |
| `src/layouts/GameLayout.vue` | 游戏主布局 |
| `src/router/` | Vue Router + 导航守卫 |
| `src/realtime/realtimeClient.ts` | WebSocket 客户端 |
| `src/styles/` | 全局样式 + 深空主题 CSS 变量 |

## Validation

Before finishing, verify:
- 后端 mvn compile 通过。
- 后端启动无报错（端口 8080）。
- 前端 npm run dev 启动无报错。
- 浏览器打开 localhost:5173 能登录进入游戏。
- 核心流程：探索→战斗→奖励 全链路贯通。
- 浏览器控制台无明显报错。
