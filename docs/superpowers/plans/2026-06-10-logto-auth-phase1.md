# Logto GitHub 登录 Phase 1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TrendingAI Android 接入 Logto GitHub 登录，登录后在 Worker 建档（`app_users`），并提供极简 Profile 页（头像/昵称/login/bio/跳 GitHub/登出）。

**Architecture:** 客户端用 Logto Android SDK（OIDC PKCE）登录拿 opaque access token；Worker 端 `requireAuth` 调 Logto userinfo 在线校验（10 分钟内存缓存），`GET /api/me` 据 claims upsert `app_users`（内部 `user_id` 主键 + `logto_sub`/`github_user_id` 映射列）。shared 层只看 `AuthManager` 抽象，Logto SDK 仅存在于 androidApp。

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform（jetbrains navigation3、Ktor、coil3、multiplatform-settings）、io.logto.sdk:android 1.1.3、Cloudflare Worker + D1、vitest。

**涉及两个仓库**（各自独立提交，使用 `git -C <repo>`）：
- 后端：`/Users/wanghl/TrendingProjects/github-ai-trending-api`（Task 1-6）
- 客户端：`/Users/wanghl/TrendingProjects/TrendingAI`（Task 7-13）

**关键既有事实**（执行者无须再探查）：
- Worker 路由是 `src/index.js` 里的 `if (pathname === ...)` 平铺分支；D1 binding 名 `DB`；响应辅助在 `src/lib/http.js`（`jsonOk(data, maxAge)` / `jsonError(msg, status)` / `handlePreflight()`）；测试用 vitest，mock 模式参考 `tests/api/subscribe.test.js`；migrations 最新编号 014
- 客户端无 DI 框架：全局单例 + 构造函数默认参数注入；ViewModel 用 `androidx.lifecycle.ViewModel` + `viewModel { }`；导航是 `App.kt` 的 `backStack` + `entryProvider`；字符串在 `shared/src/commonMain/composeResources/values{,-zh}/strings.xml`（撇号必须写 `&apos;`，占位符必须 `%1$s`）；coil3 已在 shared commonMain 可用
- Logto 配置（已在控制台完成）：endpoint `https://28bniv.logto.app`，App ID `lasqslwwdjbim73vgkapj`，redirect `cn.trendingai://<applicationId>/callback`（release/debug 两条均已注册）；GitHub 连接器 Store tokens 已开
- Logto userinfo 端点：`GET https://28bniv.logto.app/oidc/me`（Bearer 用户 token）；`identities` claim 需客户端登录 scope 含 `identities` 才返回，结构 `{ github: { userId: "<GitHub数字ID字符串>", details: { ..., rawData: {<GitHub /user 原始JSON>} } } }`

---

## Part A：后端（github-ai-trending-api）

### Task 1: `app_users` 迁移

**Files:**
- Create: `migrations/015_add_app_users.sql`

- [ ] **Step 1: 写迁移文件**

```sql
-- 用户建档表：业务数据唯一锚点是内部 user_id（UUID）；
-- logto_sub 是当前 IdP 的映射列（换 IdP 时整列替换）；
-- github_user_id 是 GitHub 数字 ID（永不变），跨身份系统对账的耐久锚点。
CREATE TABLE IF NOT EXISTS app_users (
    user_id        TEXT PRIMARY KEY,
    logto_sub      TEXT    NOT NULL UNIQUE,
    github_user_id INTEGER UNIQUE,
    github_login   TEXT,
    display_name   TEXT,
    avatar_url     TEXT,
    bio            TEXT,
    html_url       TEXT,
    raw_profile    TEXT,
    created_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    last_login_at  TEXT
);

CREATE INDEX IF NOT EXISTS idx_app_users_github_user_id ON app_users(github_user_id);
```

- [ ] **Step 2: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api add migrations/015_add_app_users.sql
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api commit -m "feat: app_users 建档表迁移（内部 user_id 主键 + logto_sub/github_user_id 映射）"
```

> 注意：迁移此时只提交不执行，远端 apply 统一放在 Task 6（项目规则：必须 `npx wrangler d1 migrations apply trending --remote`）。

---

### Task 2: CORS 允许 Authorization 头

**Files:**
- Modify: `src/lib/http.js`（`CORS_HEADERS` 常量）

- [ ] **Step 1: 修改 Allow-Headers**

把：
```javascript
'Access-Control-Allow-Headers': 'Content-Type',
```
改为：
```javascript
'Access-Control-Allow-Headers': 'Content-Type, Authorization',
```

- [ ] **Step 2: 跑现有测试确认无回归**

Run: `cd /Users/wanghl/TrendingProjects/github-ai-trending-api && npm test`
Expected: 全部 PASS（现有 5 个测试文件不断言 Allow-Headers 的具体值）

- [ ] **Step 3: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api add src/lib/http.js
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api commit -m "feat: CORS 允许 Authorization 头（为带 token 的 /api/me 预检放行）"
```

---

### Task 3: `logto-auth.js`（userinfo 校验 + 缓存）

**Files:**
- Create: `src/lib/logto-auth.js`
- Test: `tests/api/logto-auth.test.js`

- [ ] **Step 1: 写失败测试**

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { requireAuth, clearAuthCache } from '../../src/lib/logto-auth.js';

const ENV = { LOGTO_ENDPOINT: 'https://logto.example' };

const CLAIMS = { sub: 'logto_user_1', name: 'Harlon' };

function makeRequest(authHeader) {
    const headers = authHeader ? { Authorization: authHeader } : {};
    return new Request('https://api.trendingai.cn/api/me', { headers });
}

describe('requireAuth', () => {
    beforeEach(() => {
        clearAuthCache();
        vi.restoreAllMocks();
    });

    it('无 Authorization 头返回 401', async () => {
        const { claims, errorResponse } = await requireAuth(makeRequest(null), ENV);
        expect(claims).toBeUndefined();
        expect(errorResponse.status).toBe(401);
    });

    it('非 Bearer 格式返回 401', async () => {
        const { errorResponse } = await requireAuth(makeRequest('Basic abc'), ENV);
        expect(errorResponse.status).toBe(401);
    });

    it('userinfo 200 时返回 claims，且调用了正确端点', async () => {
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(JSON.stringify(CLAIMS), { status: 200 })
        );
        vi.stubGlobal('fetch', fetchMock);

        const { claims, errorResponse } = await requireAuth(makeRequest('Bearer token-a'), ENV);
        expect(errorResponse).toBeUndefined();
        expect(claims.sub).toBe('logto_user_1');
        expect(fetchMock).toHaveBeenCalledWith(
            'https://logto.example/oidc/me',
            expect.objectContaining({ headers: { Authorization: 'Bearer token-a' } })
        );
    });

    it('userinfo 401 时返回 401 且不缓存', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response('', { status: 401 }));
        vi.stubGlobal('fetch', fetchMock);

        const first = await requireAuth(makeRequest('Bearer bad'), ENV);
        expect(first.errorResponse.status).toBe(401);

        const second = await requireAuth(makeRequest('Bearer bad'), ENV);
        expect(second.errorResponse.status).toBe(401);
        expect(fetchMock).toHaveBeenCalledTimes(2); // 失败结果不缓存
    });

    it('同一 token 第二次命中缓存，不再调 userinfo', async () => {
        const fetchMock = vi.fn().mockResolvedValue(
            new Response(JSON.stringify(CLAIMS), { status: 200 })
        );
        vi.stubGlobal('fetch', fetchMock);

        await requireAuth(makeRequest('Bearer token-a'), ENV);
        const { claims } = await requireAuth(makeRequest('Bearer token-a'), ENV);
        expect(claims.sub).toBe('logto_user_1');
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });
});
```

- [ ] **Step 2: 运行确认失败**

Run: `cd /Users/wanghl/TrendingProjects/github-ai-trending-api && npx vitest run tests/api/logto-auth.test.js`
Expected: FAIL —— `Cannot find module '../../src/lib/logto-auth.js'`

- [ ] **Step 3: 实现 `src/lib/logto-auth.js`**

```javascript
import { jsonError } from './http.js';

// Logto Free 档无 API 资源 → access token 是 opaque token，无法 JWKS 离线验签。
// 改为调 userinfo 在线校验，并按 token 哈希做 isolate 内存缓存（设计稿 §5）。
const CACHE_TTL_MS = 10 * 60 * 1000;
const CACHE_MAX_ENTRIES = 1000;
const authCache = new Map();

/** 仅供测试重置模块级缓存 */
export function clearAuthCache() {
    authCache.clear();
}

async function sha256Hex(text) {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
    return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * 校验 Bearer token 并返回 Logto userinfo claims。
 * 成功：{ claims }；失败：{ errorResponse }（直接 return 给调用方）。
 */
export async function requireAuth(request, env) {
    const header = request.headers.get('Authorization') || '';
    if (!header.startsWith('Bearer ')) {
        return { errorResponse: jsonError('Unauthorized', 401) };
    }
    const token = header.slice(7).trim();
    if (!token) {
        return { errorResponse: jsonError('Unauthorized', 401) };
    }

    const key = await sha256Hex(token);
    const cached = authCache.get(key);
    if (cached && cached.expiresAt > Date.now()) {
        return { claims: cached.claims };
    }
    authCache.delete(key);

    const res = await fetch(`${env.LOGTO_ENDPOINT}/oidc/me`, {
        headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) {
        return { errorResponse: jsonError('Unauthorized', 401) };
    }

    const claims = await res.json();
    if (authCache.size >= CACHE_MAX_ENTRIES) {
        authCache.delete(authCache.keys().next().value); // 简单 FIFO 淘汰
    }
    authCache.set(key, { claims, expiresAt: Date.now() + CACHE_TTL_MS });
    return { claims };
}
```

- [ ] **Step 4: 运行确认通过**

Run: `npx vitest run tests/api/logto-auth.test.js`
Expected: 5 passed

- [ ] **Step 5: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api add src/lib/logto-auth.js tests/api/logto-auth.test.js
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api commit -m "feat: requireAuth——Logto userinfo 在线校验 + 10 分钟内存缓存"
```

---

### Task 4: `GET /api/me`（建档 upsert）

**Files:**
- Create: `src/api/me.js`
- Test: `tests/api/me.test.js`

- [ ] **Step 1: 写失败测试**

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { handleMe } from '../../src/api/me.js';
import { clearAuthCache } from '../../src/lib/logto-auth.js';

const CLAIMS = {
    sub: 'logto_user_1',
    name: 'Harlon',
    username: null,
    picture: 'https://avatars.githubusercontent.com/u/123456',
    identities: {
        github: {
            userId: '123456',
            details: {
                rawData: {
                    login: 'HarlonWang',
                    bio: 'Android dev',
                    html_url: 'https://github.com/HarlonWang',
                },
            },
        },
    },
};

const DB_ROW = {
    user_id: 'uuid-1',
    github_user_id: 123456,
    github_login: 'HarlonWang',
    display_name: 'Harlon',
    avatar_url: 'https://avatars.githubusercontent.com/u/123456',
    bio: 'Android dev',
    html_url: 'https://github.com/HarlonWang',
    created_at: '2026-06-10 00:00:00',
};

const makeDB = () => ({
    prepare: vi.fn().mockReturnThis(),
    bind: vi.fn().mockReturnThis(),
    first: vi.fn().mockResolvedValue(DB_ROW),
});

const makeEnv = () => ({ DB: makeDB(), LOGTO_ENDPOINT: 'https://logto.example' });

const makeRequest = (method = 'GET', authHeader = 'Bearer token-a') => {
    const headers = authHeader ? { Authorization: authHeader } : {};
    return new Request('https://api.trendingai.cn/api/me', { method, headers });
};

function stubUserinfo(status = 200, body = CLAIMS) {
    const fetchMock = vi.fn().mockResolvedValue(
        new Response(JSON.stringify(body), { status })
    );
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

describe('GET /api/me', () => {
    beforeEach(() => {
        clearAuthCache();
        vi.restoreAllMocks();
        vi.stubGlobal('crypto', {
            ...crypto,
            randomUUID: vi.fn().mockReturnValue('uuid-1'),
            subtle: crypto.subtle,
        });
    });

    it('OPTIONS 预检返回 CORS 头（含 Authorization）', async () => {
        const res = await handleMe(makeRequest('OPTIONS'), makeEnv());
        expect(res.status).toBe(200);
        expect(res.headers.get('Access-Control-Allow-Headers')).toContain('Authorization');
    });

    it('非 GET 返回 405', async () => {
        const res = await handleMe(makeRequest('POST'), makeEnv());
        expect(res.status).toBe(405);
    });

    it('无 token 返回 401，且不触发 DB 写入', async () => {
        const env = makeEnv();
        const res = await handleMe(makeRequest('GET', null), env);
        expect(res.status).toBe(401);
        expect(env.DB.prepare).not.toHaveBeenCalled();
    });

    it('userinfo 校验失败返回 401', async () => {
        stubUserinfo(401, {});
        const res = await handleMe(makeRequest(), makeEnv());
        expect(res.status).toBe(401);
    });

    it('有效 token：upsert app_users 并返回 user', async () => {
        stubUserinfo();
        const env = makeEnv();
        const res = await handleMe(makeRequest(), env);
        expect(res.status).toBe(200);

        const body = await res.json();
        expect(body.user.user_id).toBe('uuid-1');
        expect(body.user.github_login).toBe('HarlonWang');

        const sql = env.DB.prepare.mock.calls[0][0];
        expect(sql).toContain('INSERT INTO app_users');
        expect(sql).toContain('ON CONFLICT(logto_sub)');

        const bound = env.DB.bind.mock.calls[0];
        expect(bound[1]).toBe('logto_user_1');  // logto_sub
        expect(bound[2]).toBe(123456);          // github_user_id（已转数字）
        expect(bound[3]).toBe('HarlonWang');    // github_login
    });

    it('identities 缺失时仍能建档（github 字段为 null）', async () => {
        stubUserinfo(200, { sub: 'logto_user_2', name: 'NoGithub', picture: null });
        const env = makeEnv();
        const res = await handleMe(makeRequest('GET', 'Bearer token-b'), env);
        expect(res.status).toBe(200);

        const bound = env.DB.bind.mock.calls[0];
        expect(bound[1]).toBe('logto_user_2');
        expect(bound[2]).toBeNull(); // github_user_id
        expect(bound[3]).toBeNull(); // github_login
    });
});
```

- [ ] **Step 2: 运行确认失败**

Run: `npx vitest run tests/api/me.test.js`
Expected: FAIL —— `Cannot find module '../../src/api/me.js'`

- [ ] **Step 3: 实现 `src/api/me.js`**

```javascript
import { handlePreflight, jsonOk, jsonError } from '../lib/http.js';
import { requireAuth } from '../lib/logto-auth.js';

/**
 * 从 Logto userinfo claims 提取建档字段。
 * github 基础资料来自 identities claim（客户端登录 scope 须含 identities），
 * rawData 是 GitHub /user 的原始 JSON，字段可能缺失，全部做空值兜底。
 */
function extractProfile(claims) {
    const github = claims.identities?.github;
    const raw = github?.details?.rawData ?? {};
    const parsedId = github?.userId != null ? Number(github.userId) : NaN;
    const githubUserId = Number.isFinite(parsedId) ? parsedId : null;
    const githubLogin = raw.login ?? claims.username ?? null;
    return {
        logtoSub: claims.sub,
        githubUserId,
        githubLogin,
        displayName: claims.name ?? githubLogin,
        avatarUrl: claims.picture ?? raw.avatar_url ?? null,
        bio: raw.bio ?? null,
        htmlUrl: raw.html_url ?? (githubLogin ? `https://github.com/${githubLogin}` : null),
    };
}

export async function handleMe(request, env) {
    if (request.method === 'OPTIONS') return handlePreflight();
    if (request.method !== 'GET') return jsonError('Method not allowed', 405);

    const { claims, errorResponse } = await requireAuth(request, env);
    if (errorResponse) return errorResponse;
    if (!claims?.sub) return jsonError('Unauthorized', 401);

    const p = extractProfile(claims);
    const user = await env.DB.prepare(
        `INSERT INTO app_users
            (user_id, logto_sub, github_user_id, github_login, display_name,
             avatar_url, bio, html_url, raw_profile, last_login_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
         ON CONFLICT(logto_sub) DO UPDATE SET
            github_user_id = excluded.github_user_id,
            github_login   = excluded.github_login,
            display_name   = excluded.display_name,
            avatar_url     = excluded.avatar_url,
            bio            = excluded.bio,
            html_url       = excluded.html_url,
            raw_profile    = excluded.raw_profile,
            last_login_at  = excluded.last_login_at
         RETURNING user_id, github_user_id, github_login, display_name,
                   avatar_url, bio, html_url, created_at`
    ).bind(
        crypto.randomUUID(),
        p.logtoSub,
        p.githubUserId,
        p.githubLogin,
        p.displayName,
        p.avatarUrl,
        p.bio,
        p.htmlUrl,
        JSON.stringify(claims)
    ).first();

    return jsonOk({ user }, 0);
}
```

> 已知边界（接受）：若同一 GitHub 账号对应的 Logto 用户被删后重建（logto_sub 变了），`github_user_id` UNIQUE 会使 INSERT 失败返回 500。Phase 1 不处理，出现时人工清理旧行。

- [ ] **Step 4: 运行确认通过**

Run: `npx vitest run tests/api/me.test.js`
Expected: 6 passed

- [ ] **Step 5: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api add src/api/me.js tests/api/me.test.js
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api commit -m "feat: GET /api/me——凭 Logto token 建档/刷新 app_users 并返回 profile"
```

---

### Task 5: 路由注册 + LOGTO_ENDPOINT 变量

**Files:**
- Modify: `src/index.js`（import 区 + 路由分支区）
- Modify: `wrangler.toml`

- [ ] **Step 1: `src/index.js` 加路由**

在文件顶部 import 区（与其他 `handleXxx` import 并列）加：
```javascript
import { handleMe } from './api/me.js';
```
在 fetch handler 的路由分支区（与 `/api/feedback` 等并列）加：
```javascript
if (pathname === '/api/me') return handleMe(request, env);
```

- [ ] **Step 2: `wrangler.toml` 加 vars**

在 `[observability]` 块之前加（公开端点，非机密，不用 secret）：
```toml
[vars]
LOGTO_ENDPOINT = "https://28bniv.logto.app"
```

- [ ] **Step 3: 全量测试**

Run: `npm test`
Expected: 全部 PASS（7 个测试文件）

- [ ] **Step 4: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api add src/index.js wrangler.toml
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api commit -m "feat: 注册 /api/me 路由 + LOGTO_ENDPOINT 配置"
```

---

### Task 6: 迁移 apply + 部署 + 烟测

- [ ] **Step 1: 远端执行迁移**（项目规则：必须用 migrations apply）

Run: `cd /Users/wanghl/TrendingProjects/github-ai-trending-api && npx wrangler d1 migrations apply trending --remote`
Expected: `015_add_app_users.sql` 执行成功

- [ ] **Step 2: 部署 Worker**

Run: `npm run deploy`
Expected: deploy 成功，输出版本号

- [ ] **Step 3: 烟测（无 token 应 401）**

Run: `curl -s -i https://api.trendingai.cn/api/me | head -5`
Expected: `HTTP/2 401`，body `{"error":"Unauthorized"}`（具体格式以 jsonError 实现为准）

- [ ] **Step 4: 推送**

```bash
git -C /Users/wanghl/TrendingProjects/github-ai-trending-api push
```

---

## Part B：客户端（TrendingAI）

### Task 7: 依赖（version catalog + androidApp）

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `androidApp/build.gradle.kts`（dependencies 块）

- [ ] **Step 1: catalog 声明**（项目规则：禁止硬编码坐标）

`[versions]` 区加：
```toml
logto = "1.1.3"
```
`[libraries]` 区加：
```toml
logto-android = { module = "io.logto.sdk:android", version.ref = "logto" }
```

- [ ] **Step 2: androidApp 引用**

`androidApp/build.gradle.kts` 的 `dependencies` 块加（所有 flavor 共用，Logto SDK 来自 mavenCentral 且开源，fdroid flavor 无障碍）：
```kotlin
implementation(libs.logto.android)
```

- [ ] **Step 3: 验证可编译**

Run: `cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :androidApp:assembleGithubDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add gradle/libs.versions.toml androidApp/build.gradle.kts
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: 引入 Logto Android SDK 1.1.3（version catalog）"
```

---

### Task 8: shared 层 `AuthManager` 抽象

**Files:**
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/auth/AuthManager.kt`

- [ ] **Step 1: 写抽象（无平台依赖）**

```kotlin
package whl.trending.ai.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface AuthState {
    data object LoggedOut : AuthState
    data object LoggingIn : AuthState
    data object LoggedIn : AuthState
}

/**
 * 登录态抽象：shared/UI 只依赖本接口，Logto SDK 只存在于 androidApp。
 * iOS 未接入前使用 NoopAuthManager（isSupported=false，UI 隐藏登录入口）。
 */
interface AuthManager {
    val isSupported: Boolean
    val authState: StateFlow<AuthState>
    fun signIn()
    fun signOut()
    suspend fun getAccessToken(): String?
}

object NoopAuthManager : AuthManager {
    override val isSupported: Boolean = false
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.LoggedOut)
    override fun signIn() {}
    override fun signOut() {}
    override suspend fun getAccessToken(): String? = null
}

/** 仿 globalChatScreen 的依赖反转：Android 在 MainActivity.onCreate 注入实现 */
var globalAuthManager: AuthManager = NoopAuthManager
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :shared:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/auth/AuthManager.kt
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: AuthManager 登录态抽象（shared 不依赖 Logto SDK）"
```

---

### Task 9: Me 模型 + API + Repository + 头像缓存（TDD）

**Files:**
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/data/model/Me.kt`
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/data/remote/TrendingApi.kt`（类内追加方法）
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/data/repository/UserRepository.kt`
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/data/local/SettingsManager.kt`
- Test: `shared/src/commonTest/kotlin/whl/trending/ai/data/model/MeResponseTest.kt`
- Test: 扩展 `shared/src/commonTest/kotlin/whl/trending/ai/data/local/SettingsManagerTest.kt`

- [ ] **Step 1: 写失败测试 `MeResponseTest.kt`**

```kotlin
package whl.trending.ai.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decode_full_payload() {
        val payload = """
            {"user":{"user_id":"uuid-1","github_user_id":123456,"github_login":"HarlonWang",
            "display_name":"Harlon","avatar_url":"https://a.png","bio":"dev",
            "html_url":"https://github.com/HarlonWang","created_at":"2026-06-10 00:00:00"}}
        """.trimIndent()
        val me = json.decodeFromString<MeResponse>(payload)
        assertEquals("uuid-1", me.user.userId)
        assertEquals(123456L, me.user.githubUserId)
        assertEquals("HarlonWang", me.user.githubLogin)
        assertEquals("https://a.png", me.user.avatarUrl)
    }

    @Test
    fun decode_minimal_payload_with_nulls() {
        val payload = """{"user":{"user_id":"uuid-2"}}"""
        val me = json.decodeFromString<MeResponse>(payload)
        assertEquals("uuid-2", me.user.userId)
        assertNull(me.user.githubLogin)
        assertNull(me.user.avatarUrl)
    }
}
```

并在 `SettingsManagerTest.kt` 追加（沿用现有 MapSettings + setUp 结构）：
```kotlin
@Test
fun userAvatarUrl_set_and_clear() = runTest {
    manager.setUserAvatarUrl("https://a.png")
    assertEquals("https://a.png", settings.getStringOrNull("prefs_user_avatar_url"))
    manager.setUserAvatarUrl(null)
    assertEquals(null, settings.getStringOrNull("prefs_user_avatar_url"))
}
```
（若该文件未导入 `runTest`，加 `import kotlinx.coroutines.test.runTest`；若断言不需要挂起则可去掉 runTest 直接写普通 @Test。）

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew shared:test --tests "*MeResponseTest*" 2>&1 | tail -20`
Expected: 编译失败（MeResponse 不存在）

- [ ] **Step 3: 实现 `Me.kt`**

```kotlin
package whl.trending.ai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(val user: MeUser)

@Serializable
data class MeUser(
    @SerialName("user_id") val userId: String,
    @SerialName("github_user_id") val githubUserId: Long? = null,
    @SerialName("github_login") val githubLogin: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
```

- [ ] **Step 4: `TrendingApi` 追加 fetchMe**

在 `TrendingApi` 类内（与 `fetchTrending` 并列）追加；文件顶部需要 `import io.ktor.client.request.header` 与 `import io.ktor.http.HttpHeaders`（若已存在则跳过）：

```kotlin
open suspend fun fetchMe(accessToken: String): MeResponse {
    val response = client.get("$baseHost/api/me") {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
    return response.body<MeResponse>()
}
```

- [ ] **Step 5: `SettingsManager` 追加头像缓存**

在 key 常量区追加：
```kotlin
private val USER_AVATAR_KEY = "prefs_user_avatar_url"
```
在类内（subscribedEmail 旁）追加：
```kotlin
/** 已登录用户头像 URL 缓存：TopBar 入口同步展示用；登出时清空 */
val userAvatarUrl: Flow<String?> = settings.getStringOrNullFlow(USER_AVATAR_KEY)

fun setUserAvatarUrl(url: String?) {
    if (url.isNullOrBlank()) {
        settings.remove(USER_AVATAR_KEY)
    } else {
        settings.putString(USER_AVATAR_KEY, url)
    }
}
```

- [ ] **Step 6: 实现 `UserRepository.kt`**

```kotlin
package whl.trending.ai.data.repository

import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.remote.TrendingApi

class UserRepository(private val api: TrendingApi = TrendingApi()) {

    suspend fun fetchMe(accessToken: String): MeUser = api.fetchMe(accessToken).user

    /**
     * 登录成功/应用启动（已登录）时调用：服务端建档 + 刷新 last_login_at，并缓存头像。
     * 失败静默——下次打开 Profile 仍会重试，不阻塞登录主流程。
     */
    suspend fun syncMe(accessToken: String?): MeUser? {
        if (accessToken == null) return null
        return try {
            val user = fetchMe(accessToken)
            globalSettingsManager.setUserAvatarUrl(user.avatarUrl)
            user
        } catch (_: Exception) {
            null
        }
    }
}
```

- [ ] **Step 7: 运行测试确认通过**

Run: `./gradlew shared:test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL（含 MeResponseTest 2 个、SettingsManagerTest 新增 1 个）

- [ ] **Step 8: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/data shared/src/commonTest
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: /api/me 客户端模型与 UserRepository + 头像缓存"
```

---

### Task 10: androidApp `LogtoAuthManager`

**Files:**
- Create: `androidApp/src/main/kotlin/whl/trending/ai/auth/LogtoAuthManager.kt`
- Modify: `androidApp/src/main/kotlin/whl/trending/ai/MainActivity.kt`（onCreate）

- [ ] **Step 1: 实现 `LogtoAuthManager.kt`**

```kotlin
package whl.trending.ai.auth

import android.app.Activity
import io.logto.sdk.android.LogtoClient
import io.logto.sdk.android.type.LogtoConfig
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import whl.trending.ai.BuildConfig
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.UserRepository

/**
 * Logto 实现：OIDC PKCE 登录，token 存储/刷新由 SDK 托管。
 * scope 额外加 identities——Worker 经 userinfo 取 GitHub 数字 ID 建档（设计稿 §7.2/§7.3）。
 */
class LogtoAuthManager(activity: Activity) : AuthManager {
    private val activityRef = WeakReference(activity)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val logtoClient = LogtoClient(
        LogtoConfig(
            endpoint = LOGTO_ENDPOINT,
            appId = LOGTO_APP_ID,
            scopes = listOf("identities"),
            resources = null,
            usingPersistStorage = true,
        ),
        activity.application,
    )

    private val _authState = MutableStateFlow<AuthState>(
        if (logtoClient.isAuthenticated) AuthState.LoggedIn else AuthState.LoggedOut
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()
    override val isSupported: Boolean = true

    override fun signIn() {
        val activity = activityRef.get() ?: return
        _authState.value = AuthState.LoggingIn
        logtoClient.signIn(activity, REDIRECT_URI) { logtoException ->
            if (logtoException == null && logtoClient.isAuthenticated) {
                _authState.value = AuthState.LoggedIn
                trackEvent("sign_in_success")
                // 登录即建档：失败静默，打开 Profile 时会重试
                scope.launch { UserRepository().syncMe(getAccessToken()) }
            } else {
                _authState.value = AuthState.LoggedOut
                if (logtoException != null) trackEvent("sign_in_failed")
            }
        }
    }

    override fun signOut() {
        logtoClient.signOut { /* 本地凭证已清除即视为登出，远端失败不阻塞 */ }
        globalSettingsManager.setUserAvatarUrl(null)
        _authState.value = AuthState.LoggedOut
        trackEvent("sign_out")
    }

    override suspend fun getAccessToken(): String? =
        suspendCancellableCoroutine { cont ->
            logtoClient.getAccessToken { _, accessToken ->
                cont.resume(accessToken?.token)
            }
        }

    companion object {
        private const val LOGTO_ENDPOINT = "https://28bniv.logto.app"
        private const val LOGTO_APP_ID = "lasqslwwdjbim73vgkapj"

        /** release: cn.trendingai://whl.trending.ai/callback；debug 包名带 .debug，两条均已在 Logto 注册 */
        private val REDIRECT_URI = "cn.trendingai://${BuildConfig.APPLICATION_ID}/callback"
    }
}
```

> 若编译时 SDK 回调签名不符（`getAccessToken`/`signIn` 的参数个数），以 `io.logto.sdk.android.LogtoClient` 的实际签名为准微调 lambda 参数——SDK 1.1.3 的 Completion 回调形如 `(logtoException, result) -> Unit`，signIn 完成回调只有 `(logtoException) -> Unit`。
> AndroidManifest 无需新增配置（SDK 通过内置 WebView 拦截自定义 scheme 回跳，quickstart 仅要求 INTERNET 权限，项目已有）。若真机实测登录后未回跳，再按 SDK README 排查。

- [ ] **Step 2: MainActivity 注册**

在 `MainActivity.onCreate` 中，`AndroidContextHolder.initialize(this)` 之后追加：
```kotlin
// 注入 Logto 登录实现（仿 globalChatScreen 的依赖反转；配置变更重建时重新绑定 activity）
whl.trending.ai.auth.globalAuthManager = whl.trending.ai.auth.LogtoAuthManager(this)
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :androidApp:assembleGithubDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add androidApp/src/main/kotlin/whl/trending/ai/auth/LogtoAuthManager.kt androidApp/src/main/kotlin/whl/trending/ai/MainActivity.kt
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: LogtoAuthManager——Android 端 Logto 登录实现并注入 globalAuthManager"
```

---

### Task 11: Profile 页（ViewModel + Screen + 文案）

**Files:**
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/ProfileViewModel.kt`
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/ProfileScreen.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values-zh/strings.xml`

- [ ] **Step 1: 文案**

`values/strings.xml` 追加：
```xml
<string name="profile_title">Profile</string>
<string name="sign_in">Sign in</string>
<string name="sign_out">Sign out</string>
<string name="profile_open_github">Open on GitHub</string>
<string name="profile_load_failed">Failed to load profile</string>
<string name="profile_retry">Retry</string>
```
`values-zh/strings.xml` 追加：
```xml
<string name="profile_title">个人主页</string>
<string name="sign_in">登录</string>
<string name="sign_out">退出登录</string>
<string name="profile_open_github">在 GitHub 打开</string>
<string name="profile_load_failed">加载失败</string>
<string name="profile_retry">重试</string>
```

- [ ] **Step 2: `ProfileViewModel.kt`**

```kotlin
package whl.trending.ai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.repository.UserRepository

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: MeUser? = null,
    val isError: Boolean = false,
)

class ProfileViewModel(
    private val repository: UserRepository = UserRepository(),
    private val authManager: AuthManager = globalAuthManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)
            val token = authManager.getAccessToken()
            if (token == null) {
                _uiState.value = ProfileUiState(isLoading = false, isError = true)
                return@launch
            }
            runCatching { repository.fetchMe(token) }
                .onSuccess { _uiState.value = ProfileUiState(isLoading = false, user = it) }
                .onFailure { _uiState.value = ProfileUiState(isLoading = false, isError = true) }
        }
    }

    fun signOut() = authManager.signOut()
}
```

- [ ] **Step 3: `ProfileScreen.kt`**

```kotlin
package whl.trending.ai.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.profile_load_failed
import trendingai.shared.generated.resources.profile_open_github
import trendingai.shared.generated.resources.profile_retry
import trendingai.shared.generated.resources.profile_title
import trendingai.shared.generated.resources.sign_out

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val viewModel: ProfileViewModel = viewModel { ProfileViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.padding(top = 48.dp))

                uiState.isError -> {
                    Text(
                        text = stringResource(Res.string.profile_load_failed),
                        modifier = Modifier.padding(top = 48.dp)
                    )
                    Button(onClick = { viewModel.load() }) {
                        Text(stringResource(Res.string.profile_retry))
                    }
                }

                else -> uiState.user?.let { user ->
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).clip(CircleShape)
                    )
                    Text(
                        text = user.displayName ?: user.githubLogin.orEmpty(),
                        style = MaterialTheme.typography.titleLarge
                    )
                    user.githubLogin?.let {
                        Text(
                            text = "@$it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    user.bio?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    user.htmlUrl?.let { url ->
                        Button(
                            onClick = { uriHandler.openUri(url) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.profile_open_github))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.signOut()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.sign_out))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :androidApp:assembleGithubDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/ui/profile shared/src/commonMain/composeResources
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: Profile 页——头像/昵称/login/bio + 跳 GitHub + 登出"
```

---

### Task 12: 导航 + TopBar 登录入口

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/core/App.kt`
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt`

- [ ] **Step 1: `App.kt` 注册 Profile 路由**

路由声明区（`data object Favorites` 旁）加：
```kotlin
data object Profile
```
import 区加：
```kotlin
import whl.trending.ai.ui.profile.ProfileScreen
```
`entryProvider` 的 `when` 中（`is Favorites` 分支旁）加：
```kotlin
is Profile -> NavEntry(key) {
    ProfileScreen(onBack = { backStack.safePop() })
}
```
`is Home` 分支的 `HomeScreen(...)` 调用追加参数：
```kotlin
onNavigateToProfile = {
    backStack.add(Profile)
},
```

- [ ] **Step 2: `HomeScreen.kt` 接线**

(a) 函数签名追加参数（`onOpenUrl` 旁）：
```kotlin
onNavigateToProfile: () -> Unit = {}
```

(b) 函数体内（`val scrollBehavior = ...` 之前）追加：
```kotlin
val authManager = globalAuthManager
val authState by authManager.authState.collectAsState()
val userAvatarUrl by globalSettingsManager.userAvatarUrl.collectAsState(null)

// 应用启动且已登录：服务端建档/刷新 last_login_at + 同步头像（幂等，失败静默）
LaunchedEffect(authState) {
    if (authState is AuthState.LoggedIn) {
        UserRepository().syncMe(authManager.getAccessToken())
    }
}
```
需要的新增 import：
```kotlin
import androidx.compose.runtime.LaunchedEffect
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.UserRepository
```

(c) `TrendingTopBar(...)` 调用处追加实参：
```kotlin
showAuthEntry = authManager.isSupported,
authState = authState,
userAvatarUrl = userAvatarUrl,
onProfileClick = {
    if (authState is AuthState.LoggedIn) onNavigateToProfile() else authManager.signIn()
},
```

(d) `TrendingTopBar` 定义：签名追加参数（`onNavigateToSettings` 旁）：
```kotlin
showAuthEntry: Boolean,
authState: AuthState,
userAvatarUrl: String?,
onProfileClick: () -> Unit,
```
`actions = { ... }` 块开头（History 按钮之前）插入：
```kotlin
if (showAuthEntry) {
    IconButton(onClick = onProfileClick, enabled = authState !is AuthState.LoggingIn) {
        when {
            authState is AuthState.LoggedIn && userAvatarUrl != null -> AsyncImage(
                model = userAvatarUrl,
                contentDescription = stringResource(Res.string.profile_title),
                modifier = Modifier.size(28.dp).clip(CircleShape)
            )
            authState is AuthState.LoggingIn -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            else -> Icon(
                Icons.Default.AccountCircle,
                contentDescription = stringResource(Res.string.sign_in)
            )
        }
    }
}
```
需要的新增 import（已有的跳过）：
```kotlin
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import trendingai.shared.generated.resources.profile_title
import trendingai.shared.generated.resources.sign_in
```
（该文件的 Res 字符串多为通配 import `trendingai.shared.generated.resources.*` 风格则无需逐条加，以现有 import 风格为准。）

- [ ] **Step 3: 编译 + 全量 shared 测试**

Run: `./gradlew :androidApp:assembleGithubDebug shared:test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/core/App.kt shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: GitHub 页 TopBar 登录入口 + Profile 导航接线"
```

---

### Task 13: 端到端手工验证

- [ ] **Step 1: 安装到模拟器/真机**

Run: `./gradlew :androidApp:installGithubDebug`

- [ ] **Step 2: 按设计稿 §11 验证清单逐项检查**

1. GitHub Tab 右上角出现登录图标 → 点击拉起 Logto 登录页 → 「Continue with GitHub」→ GitHub 授权 → 回跳 App，图标变为头像
2. 点头像进 Profile：头像/昵称/@login/bio 正确展示；「在 GitHub 打开」跳转浏览器
3. 后端建档核对：
   `cd /Users/wanghl/TrendingProjects/github-ai-trending-api && npx wrangler d1 execute trending --remote --command "SELECT user_id, logto_sub, github_user_id, github_login, last_login_at FROM app_users"`
   Expected: 一行记录，`github_user_id` 为你的 GitHub 数字 ID
4. 登出 → 回到首页，图标恢复为未登录态；再次登录 → app_users 仍为同一行（`user_id` 不变，`last_login_at` 更新）
5. 取消登录（登录页直接返回）→ 状态回到未登录，无崩溃
6. 杀进程重启 → 仍为登录态（SDK 持久化），TopBar 直接显示头像

- [ ] **Step 3: 推送客户端提交**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI push
```

> 发现问题时：UI/交互问题就地修复后补充 commit；SDK 行为与计划假设不符（回调签名、回跳方式）以 SDK 实际为准修正 Task 10 的实现。

---

## Phase 1 不做（防 scope 蔓延）

- 不接 Account API / 不取 GitHub token / 不做 feed（Phase 2）
- 不做 followers/following/repos 计数（需 GitHub token，Phase 2）
- 不做 iOS UI（NoopAuthManager 隐藏入口）
- 不绑自定义域名 `auth.trendingai.cn`、不做国内可达性实测自动化（上线前人工验证）
