# TrendingAI 接入 Logto：GitHub 登录 + 个人主页 + 动态流（设计文档）

> 日期：2026-06-09　修订：2026-06-10（按最新官方资料逐项核实后修正，修正记录见 §12）
> 范围：仅 TrendingAI（不涉及 Tono 或其他 App）
> 状态：设计稿 v2，待评审

## 一、背景与目标

TrendingAI 当前是一个无登录态的内容浏览产品（Daily Picks + 三源浏览）。本次要新增**用户体系的第一块**：

- 接入 **GitHub 登录**
- 登录后展示用户的 **GitHub 个人主页（Profile）**
- 展示用户的 **GitHub 动态流（Feed）** = GitHub Dashboard 风格的"关注的人/仓库动态"

**战略意图**：表面是"加个人主页 + feed"，真实目的是**先把"用户"这个实体在产品里立起来**，为后续的 AI chat、订阅收费打地基。

**关键决策**：用户体系不自建，**接入 Logto Cloud（Free 档）作为身份基础设施**。本次按"Logto Cloud 国内可达"假设推进（见 §9 风险）。

## 二、为什么用 Logto，而不是自建

1. **自建身份服务（OAuth 流程、邮箱 OTP、session 轮换、token 加密、限频、发信）是一个完整的新子系统**，且安全细节多、容易做错、需长期自维护。
2. Logto 把这些**全部内置**：GitHub 社交登录、邮箱 OTP、OIDC/PKCE、token 签发与刷新、Secret Vault（替你存 GitHub access token）、Account API。
3. Logto Cloud **Free 档**（已核实，2026-06）：50K MAU、**50K access token/月**（refresh token 不计费）、3 应用、3 社交连接器、1 M2M 应用、1 个自定义域名、Account API、Secret Vault，无需信用卡。远超 TrendingAI 当前体量。
4. **Free 档不含 API 资源（API resources）**——这是 2025-09 调价后的新规则，导致 Worker 验证方式与初稿不同（见 §5）。
5. 接入后 TrendingAI 自己的代码只剩两件事：**客户端拉起登录拿 token**、**后端校验 token 取用户 ID**。

> Logto 是标准 OIDC，未来若新增其他 App 可复用同一租户实现"一个账号通用"，但**本设计不为此做任何额外工作**。
> 开发期可使用 Logto **dev tenant**（自带全部 Pro 特性，不可用于生产）先行开发，生产用 Free 档正式租户。

## 三、范围

### 本次要做（完整方案）
- TrendingAI Android 接入 Logto 登录（GitHub）
- 登录入口放在 **GitHub 页 TopAppBar 右上角头像**
- 登录后：Profile 展示 + Feed 动态流（全量活动流）
- Worker 端：校验 Logto token + 用户建档表（`/api/me`）

### 推荐的实施分期（见 §8）
- **Phase 1**：登录 + 建档 + 极简 Profile（验证用户体系脊柱）
- **Phase 2**：Feed 动态流（**纯客户端工作**，经 Account API 取 GitHub token 直调 GitHub）

### 明确不做（Non-goals）
- 不做 AI chat、订阅收费（仅留好挂载点：业务/会员按内部 `user_id` 挂在 D1，不依赖 Logto 付费特性）
- 不做 iOS UI（KMP shared 层就绪，后续接）
- **不碰 Tono 或任何其他项目**
- 不做密码登录（Logto 邮箱方式走 OTP）
- 本次登录方式只启用 **GitHub**；邮箱 OTP 可在 Logto 控制台随时开启，无需改代码（但仅 GitHub 登录的用户有 feed，见 §6）

## 四、整体架构

```
                ┌──────────────────────────────────────────────┐
                │            Logto Cloud（Free 档租户）           │
                │  连接器：GitHub 社交（store tokens 开启）          │
                │  应用：TrendingAI-Android（Native）              │
                │  Account API：启用（客户端取 GitHub token）        │
                │  自定义域名：auth.trendingai.cn（推荐）             │
                └──────────────────────────────────────────────┘
        ▲ OIDC 登录(Custom Tab)     ▲ Account API        ▲ userinfo 校验
        │                          │ 取 GitHub token     │ (opaque token→sub)
 ┌────────────────┐                │            ┌──────────────────────┐
 │ TrendingAI      │ ── access token ──────────▶│ Cloudflare Worker     │
 │ Android(KMP)    │                            │ userinfo→sub→建档      │
 │                 │ ── GitHub token 直调 ──┐    │ app_users(logto_sub)  │
 └────────────────┘                        │    │ /api/me               │
                                           ▼    └──────────────────────┘
                                    api.github.com
                                 (profile 计数 / received_events)
```

**身份职责切分**：
- **Logto** 拥有"你是谁"（identity、登录、token、GitHub 授权与 token 保管）
- **TrendingAI Worker** 拥有"你在本 App 是什么"（按内部 `user_id` 挂的业务数据、未来会员；`logto_sub`/`github_user_id` 仅是身份映射列）
- **GitHub 数据（profile 计数、feed）由客户端直连 GitHub API**，Worker 不经手（原因见 §6）

## 五、登录与 Worker 校验（OIDC Authorization Code + PKCE）

```
① App 点 TopAppBar 头像（未登录）
② Logto Android SDK 拉起 Logto 托管登录页（Custom Tab）
③ 用户点「使用 GitHub 登录」
④ 【Logto 内部】完成 GitHub OAuth 授权（GitHub secret 由 Logto 连接器持有）
⑤ Logto 回跳 App（自定义 scheme，如 cn.trendingai://<package>/callback）带 code
⑥ SDK 用 PKCE 换取 id_token + access_token + refresh_token，并安全存储
⑦ App 用 access_token 调 api.trendingai.cn
⑧ Worker 拿该 token 调 Logto userinfo 端点换取 sub → upsert 建档 → 放行
```

**关键点（v2 修正）**：

- **Free 档无 API 资源 → access token 是 opaque token，无法 JWKS 离线验签**。Worker 改为调 Logto **userinfo 端点**在线校验（`GET <logto-endpoint>/oidc/me`，Bearer 带用户 token），返回 sub 与基础 claims。
- **校验结果按 token 做短期缓存**（isolate 内存，key 为 token 哈希，TTL ≤ 10 分钟），多数请求不产生额外往返。
- **升级路径**：未来订阅收费上线、升 Pro（$24/月）后，注册 `https://api.trendingai.cn` API 资源，切换为 JWT + JWKS 离线验签；改动局限在 `logto-auth.js` 一个文件，客户端只需在 LogtoConfig 加 `resources`。
- Native app 是 OIDC public client，PKCE 免 client_secret；token 存储与刷新由 SDK 托管（SDK 默认请求 `openid profile offline_access`）。
- Redirect 用 Logto SDK 的自定义 scheme 方案（`$(SCHEME)://$(PACKAGE)/callback`），无需 App Links 服务端配置。

## 六、Feed 数据方案（v2 重写）

- **数据源**：GitHub `GET /users/{login}/received_events`（最接近 Dashboard Feed 的官方端点）
- **硬限制（已按 2026-06 官方文档核实，如实告知用户/UI 兜底）**：
  - 最多约 **300 条、仅最近 30 天**（旧资料的"90 天"已失效）
  - 事件**非实时**，延迟 30 秒 ~ 6 小时
  - 用本人 token 调用时**含私有事件**（比初稿假设的"仅公开"更丰富；私有可见范围受 OAuth scope 约束，`read:user` 下以公开为主）
- **GitHub token 链路（核心修正）**：Logto Secret Vault 中的第三方 token **只能由终端用户经 Account API 取回**（`GET /my-account/identities/github/access-token`，凭用户自己的 Logto token）；**后端 Management API 只能读元数据、拿不到 token 本体**。因此：
  - **客户端**经 Account API 取 GitHub token → **直调 GitHub API**（profile 计数 + feed）
  - Worker 的 feed/profile 代理层**整个取消**——既不可行也不再需要
  - GitHub OAuth App 的 access token **永不过期**（无 refresh token，连接器 scope 切勿加 `offline_access`），客户端可长期缓存，失效（用户撤销授权）时经 Logto Social Verification API 重新授权
- **展示形态**：**全量活动流**——Release / PullRequest(开·合) / Push(commit) / Watch(star) / Fork / Create(建仓·分支) / Issues / IssueComment 等全部按时间倒序渲染（事件归一化逻辑从 Worker 移至 KMP shared 层）
- **scope**：Logto GitHub 连接器留空即默认 `read:user`，够用
- **缓存/配额**：received_events 按用户 5000 次/小时，远够用；客户端用 ETag 轮询命中 304 时不计配额

> 仅通过邮箱 OTP 登录（未来若开启）的用户没有 GitHub token → 无 feed。本次只启用 GitHub 登录，不存在此情况。
> 若实测国内直连 `api.github.com` 不稳，兜底方案：客户端把 GitHub token 放请求头，经 Worker 纯透传代理（Worker 不存 token），见 §9。

## 七、详细设计

### 7.1 Logto 一次性配置（控制台）

1. 创建租户（Cloud Free 档；开发期可先用 dev tenant）
2. **GitHub 社交连接器**：填 GitHub OAuth App 的 client_id/secret；Scopes 留空（默认 `read:user`）；开启 **Store tokens for persistent API access**；profile 同步策略选 "Always sync at sign-in"
3. **登录体验**：启用「GitHub」登录方式，配品牌与中英文案
4. **注册应用**：`TrendingAI-Android`（Native 类型）→ 拿 app_id，配 redirect URI（自定义 scheme）
5. **启用 Account API**：Console > Sign-in & Account > Account center（客户端取 GitHub token 的前提）
6. **（推荐）绑定自定义域名** `auth.trendingai.cn`：与 `api.trendingai.cn` 同走 Cloudflare 边缘，降低国内可达性风险
7. ~~注册 API 资源~~（Free 档不可用，升 Pro 后再做）

### 7.1.1 已完成的配置产出（2026-06-10 实操记录）

§7.1 的控制台配置已全部完成，参数如下（均为客户端公开参数，secret 仅存于 Logto/GitHub 后台）：

| 项 | 值 |
|---|---|
| Logto 租户 | `TrendingAI`（ID `28bniv`，US 区域，产品类型，Free 档） |
| Logto Endpoint | `https://28bniv.logto.app` |
| 应用（Native） | `TrendingAI-Android`，App ID `lasqslwwdjbim73vgkapj` |
| Redirect URIs | `cn.trendingai://whl.trending.ai/callback`、`cn.trendingai://whl.trending.ai.debug/callback` |
| GitHub 连接器 | ID `0xb17od4fhlnc4z1wmo8m`；Scope 留空（默认 `read:user`）；Store tokens ✅；每次登录同步资料 ✅ |
| GitHub OAuth App | `TrendingAI`（HarlonWang 名下，应用 ID 3656545），Client ID `Ov23liJ06uldRD2ZUKce` |
| 登录体验 | 仅 GitHub 社交登录（邮箱/密码注册与登录已移除） |
| Account center | 已启用，"第三方访问令牌获取"已自动开启（Phase 2 取 GitHub token 的前提） |
| 演示连接器 | Discord/GitHub/Google demo 已全部删除 |

待办：自定义域名 `auth.trendingai.cn`（上线前绑定）；无代理国内真机实测可达性。

### 7.2 客户端（KMP / Android）

- `androidApp` 接入 **Logto 官方 Android SDK**（`io.logto.sdk:android:1.1.3`，minSdk 24，满足现状）；用 `expect/actual` 或接口把"登录态 + 当前 access token + Account API 调用"暴露给 `shared`。iOS 后续接 Logto Swift SDK，`shared` 不变。
- **shared 层**（现在写好，跨平台）：
  - `AuthState`：LoggedOut / LoggingIn / LoggedIn（封装 SDK，业务页面只看状态）
  - LogtoConfig 的 `scopes` 加 `identities`（让 userinfo 返回 GitHub 数字 user id，供建档落 `github_user_id`）
  - Ktor `ApiClient`：自动在请求头挂 Logto access token，调 `api.trendingai.cn`
  - `GithubClient`：持有经 Account API 取回的 GitHub token，直调 `api.github.com`（带 ETag 缓存）
  - `ProfileRepository`、`FeedRepository`（分页 + 事件归一化：type / actor / repo / 一句话摘要 / created_at / target_url）
- **Android UI**：
  - GitHub 页 `TrendingTopBar`（`HomeScreen.kt` 的 `actions` 区）新增**头像/登录 IconButton**
    - 未登录：显示登录图标，点击 → SDK 拉起 Logto 登录
    - 已登录：显示用户头像，点击 → 进入 Profile
  - **Profile 页**：头像 / 昵称 / GitHub login / bio / followers·following·repos 计数（直调 GitHub `GET /user`）/ 跳 GitHub / 登出
  - **Feed 列表**：分页、下拉刷新、按事件类型渲染、点击用 Custom Tab 打开 GitHub 原页；空态/错误/加载态；触达 30 天/300 条上限时展示"已到底"提示
- 登录为**可选加法**：现有 Daily Picks / 三源浏览 / 历史保持免登录可用，登录只解锁 Profile + Feed。

### 7.3 后端（Cloudflare Worker）

现有后端是手写 if 路由 + D1（无框架、无 KV）。改动**与现有 trending/picks/feed 业务零交叉**，且比初稿进一步缩小。

**新增文件**：
- `src/lib/logto-auth.js`：
  - `requireAuth(request, env)`：取 Bearer token → 查 isolate 内存缓存 → 未命中则调 Logto userinfo 端点校验并缓存（TTL ≤ 10 分钟）→ 返回 `{ sub, name, picture, ... }`，401 兜底
  - 不依赖 `jose`（无 JWT 验签；该依赖留到升 Pro 切 JWKS 时再加）
- `src/api/auth.js`：
  - `GET /api/me`：凭 token 取/建当前用户，返回 profile 快照

**新增迁移 `migrations/015_app_users.sql`**：
```sql
CREATE TABLE IF NOT EXISTS app_users (
  user_id        TEXT PRIMARY KEY,          -- 内部用户 ID（UUID），业务数据的唯一锚点
  logto_sub      TEXT NOT NULL UNIQUE,      -- Logto 用户 ID（当前 IdP 的映射，可整体替换）
  github_user_id INTEGER UNIQUE,            -- GitHub 数字 ID（永不变，跨 IdP 对账的耐久锚点）
  github_login   TEXT,                      -- GitHub login（可改名，仅展示用）
  display_name   TEXT,
  avatar_url     TEXT,
  bio            TEXT,
  html_url       TEXT,
  raw_profile    TEXT,                      -- 最近一次 profile 快照 JSON
  created_at     TEXT NOT NULL DEFAULT (datetime('now')),
  last_login_at  TEXT
  -- 未来：订阅/会员 entitlement 一律挂内部 user_id（与 IdP 解耦），不挂 logto_sub
);
```
首次带 token 访问 `/api/me` 时 upsert（或后续接 Logto webhook 建档，Free 档含 1 个 webhook）。`github_user_id` 从 userinfo 的 `identities` claim 取（客户端登录 scope 需加 `identities`，见 7.2）。

**改动现有文件**：
- `src/index.js`：加 1 条路由 `if`（`/api/me`）
- vars：Logto endpoint（租户域名或 `auth.trendingai.cn`）；无需 M2M secret（Phase 1/2 都用不到 Management API）

**初稿中已取消的部分**：~~`src/lib/logto-vault.js`~~、~~`src/api/github-proxy.js`~~（Secret Vault 后端取不到 token，GitHub 数据改由客户端直连，见 §6）

### 7.4 IdP 解耦与迁移路径（退出成本）

本设计刻意把"迁离 Logto"的成本压到最低，三道隔离已内置：

1. **数据层**：`app_users` 主键是内部 `user_id`，业务/订阅数据只挂它；`logto_sub` 仅是当前 IdP 的映射列，`github_user_id`（GitHub 数字 ID，永不变）是跨身份系统对账的耐久锚点
2. **后端**：IdP 校验收敛在 `logto-auth.js` 单文件，换 IdP 只改它
3. **客户端**：shared 层只看抽象 `AuthState`，换登录 SDK 不动业务

**若日后迁移**（自建 Logto OSS / 其他 IdP / 完全自建），步骤为：

1. Logto Management API 分页导出全部用户（profile + identities 含 GitHub user id）；Cloud→OSS 无自助整体迁移，必要时可联系官方导出
2. 新系统导入用户并预绑定 GitHub identity（按 `github_user_id`）
3. D1 把 `logto_sub` 列替换为新系统的 sub 映射（主键与业务外键不动）
4. 换 `logto-auth.js` 实现 + 客户端换 SDK
5. 用户下次打开 App 重新走一次 GitHub 登录，按 `github_user_id` 自动对回原账号（无密码可迁，社交登录用户无感）

**唯一迁不走的**：Secret Vault 里的 GitHub token（加密不可导出）——用户重新登录时自动重建，无实际损失。

### 7.5 相对 DIY 方案，后端被省掉的部分

- ❌ OAuth start/callback/session/logout 端点
- ❌ `sessions` / `login_sessions` 表与 session 轮换
- ❌ `email_otp` 表 + OTP 生成/限频/发信
- ❌ token 加密（`crypto.js`）—— 改由 Logto Secret Vault 托管
- ❌ feed/profile 代理与事件归一化（移至客户端）

后端从"一个新子系统"缩成"**一次 userinfo 校验 + 一张表**"。

## 八、推荐的实施分期

| 阶段 | 内容 | 理由 |
|---|---|---|
| **Phase 1** | 登录 + 建档(`app_users`) + 极简 Profile（TopAppBar 头像入口） | 用最小 UI 验证"身份脊柱"端到端跑通；后端只需 `logto-auth.js` + `/api/me` + 一张表 |
| **Phase 2** | Feed 动态流（Account API 取 token + 客户端直调 received_events + 事件归一化） | **纯客户端工作，后端零改动**；脊柱稳了再投入 |

> Phase 1 的 Profile 基础字段（头像/昵称/login/bio）直接来自 Logto 身份声明（id_token/userinfo，社交连接器登录时已带回），落库到 `app_users` 即可，无需调 GitHub。需要 GitHub token 的部分（关注/仓库计数的实时刷新、feed）全部推迟到 Phase 2。

## 九、风险与待办

1. **国内可达性（唯一硬阻断，当前按"通"假设）** ⚠️
   开发机有 TUN 全局代理，**无法在本机实测**。缓解与验证：
   - Free 档含 1 个自定义域名 → 绑 `auth.trendingai.cn`，与已验证国内可用的 `api.trendingai.cn` 同走 Cloudflare 边缘，风险显著低于直连 `*.logto.app`
   - 上线前用无代理的国内真机实测登录页 / userinfo / token 端点
   - 不通的出路：自建 Logto OSS（Docker + PostgreSQL，破坏纯 serverless）或本功能延后
2. **客户端直连 `api.github.com` 的国内可达性**：feed/计数走客户端直连，国内偶有不稳。兜底：Worker 加纯透传代理（客户端把 GitHub token 放头里，Worker 不存储），改动很小。
3. **供应商依赖**：auth 在关键路径。userinfo 在线校验意味着 Logto 故障时新 token 无法校验（缓存内的不受影响）；升 Pro 切离线验签后此依赖消失。
4. **Free 档 50K access token/月**：当前体量绰绰有余；用户量上来后监控用量，超限即是该升 Pro 的信号（届时也该切 JWT 验签）。
5. **Secret Vault 取 token 仅限客户端**：这是 Logto 当前产品设计（开发者只能读元数据）。若未来后端确需代调 GitHub，用兜底透传方案，不依赖 Logto 改产品。
6. **Account API 细节**：客户端调 `/my-account/identities/github/access-token` 所需的 token 形态与 scope，接入时按文档确认（控制台需先启用 Account center）。

## 十、已对齐的决策清单

- 用户体系：接入 Logto Cloud Free 档，不自建 ✅
- Worker 校验方式：**opaque token + userinfo 在线校验（短缓存）**；升 Pro 后切 JWT 离线验签 ✅（2026-06-10 拍板）
- 登录即在身份层建立用户，后端 `app_users` 建档；**主键用内部 `user_id`，`logto_sub` 仅作映射列，另存 `github_user_id` 作迁移锚点** ✅（2026-06-10 拍板）
- 登录方式：本次仅 GitHub；邮箱 OTP 留作 Logto 侧可开关 ✅
- GitHub 权限：默认 `read:user`（最小化）✅
- 登录定位：可选加法，不拦截现有内容 ✅
- 登录入口：GitHub 页 TopAppBar 右上角头像 ✅
- Feed：全量活动流，源自 received_events，**客户端直连 GitHub** ✅
- 端范围：仅 Android（KMP shared 跨平台就绪）✅
- 不碰 Tono ✅

## 十一、验证方式

1. **Logto 配置**：控制台完成 GitHub 连接器（含 Store tokens）+ 应用 + Account center 启用
2. **客户端**：Android 模拟器走通 登录→拿 token→Profile 展示；登出；Account API 取到 GitHub token 并成功直调 GitHub
3. **后端**：本地/远端 Worker 用真实 Logto token 经 userinfo 校验通过，`/api/me` 正确建档；缓存生效（同 token 第二次请求不打 userinfo）
4. **边界**：取消登录、access token 过期自动刷新、feed 为空态、received_events 到达 30 天/300 条上限停止分页、GitHub token 被用户撤销后的重授权
5. **国内可达性**：上线前对 Logto 登录页与 OIDC 端点（含自定义域名）做无代理国内真机实测

## 十二、v2 修订记录（2026-06-10，基于最新官方资料核实）

| # | 初稿假设 | 核实结果 | 设计影响 |
|---|---|---|---|
| 1 | Free 档可注册 API 资源，JWT + JWKS 离线验签 | **2025-09 调价后 Free 档 API 资源为 0**（Pro $24/月含 3 个）；Free 档另有 50K access token/月计量 | Worker 改为 userinfo 在线校验 + 短缓存；升 Pro 后再切离线验签 |
| 2 | Worker 用 M2M 凭据从 Secret Vault 取 GitHub token 代理 feed | **token 本体只有终端用户经 Account API 能取**，后端只能读元数据 | 客户端直调 GitHub；Worker 代理层取消，Phase 2 后端零改动 |
| 3 | received_events 约 90 天窗口、仅公开事件 | **30 天**/300 条；本人 token 可见私有事件；延迟 30s~6h | UI 文案与分页到底逻辑按 30 天；feed 内容比预期更丰富 |
| 4 | 回跳用 App Links | SDK 标准方案为自定义 scheme | 接入更简单，无需服务端 App Links 配置 |
| 5 | GitHub token 需关注过期刷新 | GitHub OAuth App token **永不过期**（勿加 `offline_access`） | 客户端可长期缓存，仅需处理用户主动撤销 |
| 6 | `logto_sub` 直接做 `app_users` 主键 | Cloud↔OSS 无自助迁移；Management API 可自助导出用户与 identities | 主键改内部 `user_id` + 增 `github_user_id` 锚点列，IdP 解耦（见 7.4） |

主要依据：[Logto Pricing](https://logto.io/pricing)、[2025-09 调价公告](https://blog.logto.io/pricing-sep-2025)、[计费文档](https://docs.logto.io/logto-cloud/billing-and-pricing)、[Secret Vault / 第三方 token](https://docs.logto.io/secret-vault/federated-token-set)、[GitHub 连接器](https://docs.logto.io/integrations/github)、[Android SDK 快速入门](https://docs.logto.io/quick-starts/android)、[GitHub Events API](https://docs.github.com/en/rest/activity/events)
