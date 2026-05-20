# 贡献指南

## ⚠️ UI 保护规则（最高优先级）

本项目的界面样式、布局、交互逻辑已经过大量修复和精细化调试。**任何贡献者（人或 AI）在添加功能或修复 Bug 时，严禁擅自修改以下保护区域**，除非该修改是功能的必要组成部分且经过充分验证。

### 🛡️ 受保护的文件与区域

| 文件 | 保护级别 | 允许的修改 | 严禁的修改 |
|------|---------|-----------|-----------|
| `public/styles.css` | 🔴 硬保护 | 新增工具类（须遵循命名规范）；修改已有 CSS 变量的值（需全局两端同时改） | 删除/重命名已有的 CSS 类、变量；改变已有元素的选择器结构；修改布局核心属性（display/position/flex/grid/z-index/overflow） |
| `public/index.html` | 🟡 中保护 | 新增功能需要的 DOM 元素；修改文本内容 | 删除或修改已有元素的 `id`、`class`、HTML 结构层级；修改弹窗/抽屉/面板的结构 |
| `public/src/ui.js` | 🔴 硬保护 | 新增渲染方法（须调用已有工具类）；修改纯文本内容 | 修改已有渲染方法生成的 HTML 结构/class/内联样式；修改 `renderChangelog`/`renderSettings`/`renderLeftPanel`/`renderMainActionBar` 等核心渲染函数输出的 DOM 结构 |
| `public/src/gameClient.js` | 🟡 中保护 | 新增功能逻辑、API 调用、事件处理 | 修改 `openFeature`/`openDrawer`/`closeDrawer`/`setupModals` 等交互函数的行为；修改导航切换流程；修改弹窗打开关闭逻辑 |
| `public/src/api.js` | 🟢 软保护 | 新增 API 方法 | 修改 `request()` 基方法的错误处理逻辑 |
| `public/src/main.js` | 🟢 软保护 | 新增初始化逻辑 | 修改 `GameClient.init()` 的调用链 |

### 🎨 CSS 类命名规范（严禁违反）

所有样式**必须**通过 CSS 类实现，严禁内联 `style=""`（动态计算值除外）。

```
工具类命名: .{property}-{value}
  示例: .mt-8, .text-gold, .flex-row, .fs-14, .op-50

组件类命名: .{component}-{modifier}
  示例: .drawer-header, .ma-btn, .nav-item.active, .modal-overlay
```

### 🤖 AI/LLM 特别指令

如果你是 AI 助手（Claude、Copilot、Cursor 等），请严格遵守以下规则：

1. **绝对不要修改 `public/styles.css` 中已有的 CSS 变量、类名、选择器**。需要新样式时，只追加新 class 定义。
2. **绝对不要删除或重命名 `index.html` 中已有的 `id` 或 `class`**。前端 JS 通过 ID 查找 DOM 元素，改名会导致功能断裂。
3. **绝对不要修改 `ui.js` 和 `gameClient.js` 中以下函数的实现逻辑**：
   - `UI.renderChangelog()` / `renderSettings()` / `renderLeftPanel()` / `renderMainActionBar()`
   - `UI.openDrawer()` / `closeDrawer()` / `dismissPopup()` / `setupModals()`
   - `GameClient.openFeature()` / `handleNavigation()`
4. **绝对不要添加 `style="..."` 内联样式**。如需新样式，先在 `styles.css` 新增工具类，再在 JS/HTML 中用 `class="..."` 引用。
5. **不要修改 `data/` 下种子数据的 JSON 结构**（可以修改值）。
6. **不要修改 `AGENTS.md` 中标记为"硬保护"的规则**。

### ✅ 功能开发的正确做法

```javascript
// ❌ 错误：添加内联样式
element.style.color = 'var(--gold)';

// ✅ 正确：使用已有 CSS 类
element.classList.add('text-gold');

// 如果不存在需要的类，先在 styles.css 追加，再使用
```

```css
/* ❌ 错误：修改已有类 */
.drawer-header { display: block; }

/* ✅ 正确：新增独立的类 */
.drawer-header-compact { padding: 4px; }
```

### 🔍 提交前检查清单

- [ ] 没有新增 `style="..."` 内联样式
- [ ] 没有修改或删除已有的 CSS 类名/ID
- [ ] 没有修改 `ui.js`/`gameClient.js` 中标记为受保护的函数
- [ ] 所有新增的 CSS 类在暗色/亮色两种主题下都能正常显示
- [ ] 没有影响弹窗/抽屉/面板的打开关闭行为
- [ ] `npm test` 全部通过（299/299）
