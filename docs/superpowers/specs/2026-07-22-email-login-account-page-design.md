# 邮箱验证码登录 + 账户页重构 设计方案

2026-07-22 · 状态：已评审通过，待实施

## 背景与目标

额度体系（账本 + 授予模型）与 Deep Research 上线后，登录体系需要从「解锁更高限额的开关」升级为可感知的账户体系。两个痛点：

1. **登录可达性**：埋点显示 GitHub 登录取消率 76%，结构性原因是大陆 GitHub 可达性差；Google 同样不可达，只有邮箱验证码能真正解决。
2. **余额可见性**：后端 `GET /api/quota` 已上线（balance / dailyGrant / resetAt / tier），但客户端从未调用，用户只能在 429 触顶后被动看到额度卡片。

本期范围：**保留 Logto，新增邮箱验证码登录（Logto 托管登录页）；Profile 页重构为账户页，展示账户信息与配额**。仅 Android。

## 已确认的决策

| 决策点 | 结论 |
|---|---|
| 方向 | 保留 Logto，增加登录方式（非替换、非纯展示） |
| 新增登录方式 | 仅邮箱验证码（Google/微信/手机号不做） |
| 登录交互 | Logto 托管登录页（signIn 去掉 directSignIn，页内同时提供邮箱与 GitHub） |
| Profile 形态 | 整体重构为账户页，GitHub 档案降为可选模块 |
| 配额展示位置 | 仅账户页（chat 界面维持现状：触顶弹卡） |
| 账户页数据获取 | 并行调 `GET /api/me` + `GET /api/quota` 两个现成端点，不新增聚合端点 |

## 现状关键事实（探索结论）

- 后端鉴权：Logto opaque token 在线校验（`src/lib/logto-auth.js` `requireAuth`，userinfo 结果按 token 哈希缓存 10 分钟）。
- 计费身份：`identities` 表按 `user:{Logto sub}` 记账，与登录方式无关——邮箱用户自动进登录档（10 credits/天），**计费侧零改动**。
- `app_users.github_user_id` 允许 NULL（SQLite UNIQUE 不限制多个 NULL），邮箱用户走 `me.js` INSERT upsert 路径结构上兼容；但表无 `email` 列，`extractProfile` 不提取 email。
- Pro 权益按 `github_user_id` 挂靠（GitHub Sponsors webhook），邮箱用户天然非 Pro，逻辑自洽。
- 客户端 Logto scope 目前仅 `identities`，拿 email claim 需加 `email` scope。
- newsletter 已在用 Resend（`src/newsletter/index.js`），域名邮件通道现成。
- 客户端 ProfileScreen 现定位「GitHub 开发者档案」（头像/资料/贡献热力图/活动流），无任何配额展示。

## 设计

### 1. Logto 控制台配置（不产代码）

- **Email connector**：复用 Resend 通道——优先原生 Resend connector，没有则 SMTP connector 指向 `smtp.resend.com`（同一 API key 域名体系，发件人如 `noreply@trendingai.cn`），配中英验证码模板。
- **登录体验**：identifier 增加 Email + 验证码（无密码）；GitHub 社交登录保留。
- **账户自动关联**：若控制台提供「社交账号邮箱与已有邮箱账户相同则关联」选项则开启，堵住同一人先邮箱后 GitHub 登录产生双账户的分裂；没有该选项就接受双账户现状，不自建合并。
- **配置时机**：Logto 只有生产实例，托管页配置即时生效；老版本客户端 directSignIn 直跳 GitHub 不经过托管页，开启 Email identifier 对存量用户无感。

### 2. 后端改动（github-ai-trending-api，小改）

- 迁移 030：`app_users` 加 `email TEXT` 列。**不加 UNIQUE**——Logto 是身份权威，email 仅为展示字段，避免重演删号重注册撞 UNIQUE 约束的老坑（见 me.js Fix 1 注释）。
- `me.js`：
  - `extractProfile` 提取 `claims.email`；
  - INSERT / UPDATE（github_user_id 对回原行路径）/ RETURNING 三处带上 email；
  - `displayName` 兜底链改为 `claims.name ?? githubLogin ?? email 前缀`。
- `pro.js`：`isProUser` 对 `githubUserId == null` 短路返回 false。
- `/api/quota` 原样复用，零改动。

### 3. 客户端登录链路（Android）

- `LogtoAuthManager`：
  - scope 增加 `email`；
  - `signIn()` 去掉 directSignIn 参数，改走 Logto 托管登录页。
- `MeResponse`/`MeUser` 增加可空 `email` 字段。
- `SettingsManager` 持久化 email，登出清空；`setGithubIdentity` 接受 null。
- 邮箱用户调 `LogtoAccountApi` 取 GitHub token 会失败——确认该路径静默降级、不弹错。
- iOS `NoopAuthManager` 不变。

### 4. 账户页重构（原 ProfileScreen）

信息架构从「GitHub 开发者档案」改为「账户页」，三段式：

- **账户区**（所有登录用户）：头像（无头像用邮箱/名称首字母圆形占位）、显示名、邮箱行、档位徽章（Free / Pro）。
- **配额卡**：新增 `TrendingApi.fetchQuota()` 调 `GET /api/quota`（带 `X-Install-Id` + Bearer），展示余额 / 每日额度进度、UTC 重置倒计时、档位。每次进入页面实时拉取不走缓存（余额要新鲜），下拉刷新一并刷新；quota 请求失败只降级这张卡（占位/错误态），不阻塞账户区。
- **GitHub 模块**：有 GitHub 身份 → 现有内容（计数行、贡献热力图、活动流）整体收进模块，行为不变；无 GitHub 身份 → 整块隐藏。
- 未登录态引导不变。
- UI 遵循项目规范：加载态一律 M3 Expressive `LoadingIndicator`，下拉刷新显式传 `indicator`。

### 5. 错误处理与兼容

- quota 与 me 并行请求，各自独立错误态；me 失败沿用现有 Profile 错误处理。
- 邮箱用户无 GitHub token：GitHub 模块隐藏，不弹错误。
- 老客户端不受影响：登录链路只是托管页多了选项；旧版本 directSignIn 仍直跳 GitHub 可继续用。
- 计费/额度语义不变，`QuotaLimitCard` 触顶体系维持现状。

### 6. 测试与验收

- 后端：`tests/api/` 补 `me` 的「无 GitHub identity claims」用例（email 用户建档、pro=false、null 字段兜底）。
- 客户端：debug 包 Pixel_9_2 冒烟——邮箱验证码登录 → 账户页显示邮箱 + 配额卡；GitHub 登录全量回归（档案模块、贡献图、活动流）；发版前照常跑 `scripts/release-smoke.sh`。

### 7. 规模与提交策略

- 后端 ~60 行 + 1 迁移，独立小 PR。
- 客户端登录链路 ~30 行、账户页重构 ~400–600 行，属大改动，本分支（`feat/email-login-account-page`）走 PR。

## 明确不做

- 应用内绑定/解绑 GitHub（靠 Logto 自动关联部分覆盖，后续再议）
- Google / 微信 / 手机号登录
- chat 界面常驻配额展示
- me + quota 聚合端点
- iOS 登录接入
