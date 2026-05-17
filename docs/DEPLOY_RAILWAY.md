# Railway 部署指南

## 概述

将「全知读者视角」文字冒险 RPG 测试服部署到 Railway，群友通过域名访问。

## 前置条件

- GitHub 账号，项目已推送到 GitHub 仓库
- Railway 账号 (https://railway.app)

## 部署步骤

### 1. 连接 GitHub

1. 登录 Railway → Dashboard → New Project
2. 选择 "Deploy from GitHub repo"
3. 授权 Railway 访问你的 GitHub，选择项目仓库

### 2. 配置环境变量

在 Railway 项目 Settings → Variables 中添加：

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `JWT_SECRET` | JWT 签名密钥（必填） | 一段随机字符串，至少 32 字符 |
| `NODE_ENV` | 运行环境 | `production` |
| `PORT` | 端口（Railway 自动分配） | Railway 会自动设置 `PORT`，无需手动配置 |
| `ADMIN_KEY` | 后台管理密钥（必填） | 一段随机字符串，至少 8 字符 |

### 3. 部署

- Railway 会自动检测 `package.json` 中的 `start` 脚本
- `start` 脚本为 `node server.js`
- 推送代码到 GitHub → Railway 自动部署

### 4. 绑定自定义域名

1. Railway 项目 → Settings → Networking
2. 点击 "Generate Domain" 获取 `xxx.railway.app` 域名
3. 或点击 "Custom Domain" 绑定自己的域名
4. 在域名 DNS 中添加 CNAME 记录指向 Railway 提供的域名

### 5. 验证部署

1. 访问 `https://你的域名` → 看到游戏登录页 + 测试服横幅
2. 访问 `https://你的域名/admin` → 后台登录页
3. 使用 `ADMIN_KEY` 或 admin 账号登录后台

## 注意事项

- 数据库使用 SQLite（文件存储在 Railway 实例磁盘上）
- **Railway 免费套餐的磁盘不持久化**：每次重新部署数据会丢失
- 对于测试服可接受，正式服后续迁移 PostgreSQL
- `JWT_SECRET` 和 `ADMIN_KEY` 务必使用强随机字符串
- 不要将 `.env` 文件提交到 Git 仓库
