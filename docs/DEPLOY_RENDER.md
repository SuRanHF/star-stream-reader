# Render 部署指南

## 概述

将「全知读者视角」文字冒险 RPG 测试服部署到 Render，群友通过域名访问。

## 前置条件

- GitHub 账号，项目已推送到 GitHub 仓库
- Render 账号 (https://render.com)

## 部署步骤

### 1. 创建 Web Service

1. 登录 Render → Dashboard → New → Web Service
2. 连接 GitHub，选择项目仓库
3. 配置以下选项：

| 配置项 | 值 |
|--------|-----|
| Name | 全知读者视角（或自定义） |
| Runtime | Node |
| Build Command | `npm install` |
| Start Command | `node server.js` |
| Instance Type | Free（测试服） |

### 2. 配置环境变量

在 Web Service → Environment 中添加：

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `JWT_SECRET` | JWT 签名密钥（必填） | 一段随机字符串，至少 32 字符 |
| `NODE_ENV` | 运行环境 | `production` |
| `ADMIN_KEY` | 后台管理密钥（必填） | 一段随机字符串，至少 8 字符 |
| `PORT` | 端口 | Render 自动设置，可不填 |

### 3. 部署

- 点击 "Create Web Service" → Render 自动构建部署
- 部署完成后会获得 `xxx.onrender.com` 域名

### 4. 绑定自定义域名

1. Web Service → Settings → Custom Domain
2. 添加你的域名
3. 在域名 DNS 中添加记录指向 Render 提供的目标

### 5. 验证部署

1. 访问 `https://你的域名` → 看到游戏登录页 + 测试服横幅
2. 访问 `https://你的域名/admin` → 后台登录页
3. 使用 `ADMIN_KEY` 或 admin 账号登录后台

## 注意事项

- Render 免费套餐有冷启动延迟（闲置后首次访问需 ~30 秒）
- 数据库使用 SQLite，磁盘空间有限（免费套餐约 1GB）
- Render 免费套餐的磁盘不持久化，重新部署数据会丢失
- 对于测试服可接受，正式服后续迁移 PostgreSQL
- `JWT_SECRET` 和 `ADMIN_KEY` 务必使用强随机字符串
- 不要将 `.env` 文件提交到 Git 仓库
