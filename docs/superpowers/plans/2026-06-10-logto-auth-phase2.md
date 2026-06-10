# Logto Phase 2：GitHub Feed 动态流 + Profile 完善 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Profile 页一体化升级——头部展示 GitHub 计数（followers/following/repos），下方接 GitHub Dashboard 风格动态流（received_events）；同时清掉 Phase 1 遗留的双 syncMe、UserRepository 构造位置与错误态无登出问题。

**Architecture:** 纯客户端工作（Worker 零改动）。客户端经 Logto Account API（`GET /api/my-account/identities/github/access-token`，凭现有 opaque token）取出 Secret Vault 里的 GitHub access token（GitHub OAuth App token 永不过期，会话内存缓存），直调 `api.github.com`：`/user` 取计数、`/users/{login}/received_events` 取动态（仅最近 30 天/最多 300 条，30s~6h 延迟）。事件归一化为结构化 `GithubFeedItem`（kind 枚举 + 参数），文案由 UI 层 stringResource 拼装（i18n）。

**Tech Stack:** Kotlin Multiplatform、Ktor、kotlinx-serialization（payload 用 JsonObject 弹性解析）、Compose Multiplatform LazyColumn、coil3。

**仓库：** 仅 `/Users/wanghl/TrendingProjects/TrendingAI`，新分支 `feat/logto-phase2-feed`（从 main 切出）。

**已核实的事实（执行者无须再查）：**
- Account API：`GET https://28bniv.logto.app/api/my-account/identities/github/access-token`，Bearer 用现有 `AuthManager.getAccessToken()` 的 opaque token（登录 scope 已含 `identities` 满足要求）；200 响应 `{"access_token": "...", "scope": ..., "token_type": ..., "expires_in": ...}`；404=未存 token；401=token 过期
- GitHub events：`GET https://api.github.com/users/{login}/received_events?per_page=30&page=N`，Bearer GitHub token；事件结构 `{id, type, actor:{login, avatar_url}, repo:{name}, payload:{...按 type 不同}, created_at, public}`；上限 300 条/30 天，翻页超界返回空数组
- 现有代码锚点：`ui/feed/` 已被 HN/PH 占用（**勿放新文件**）；`DateTimeUtils.formatToLocalTime(utcString)` 已有（绝对时间展示，不做相对时间）；`ApiException(statusCode, body)` 在 `data/remote/` 已有；ProfileScreen/ProfileViewModel 在 `ui/profile/`；strings 在 `composeResources/values{,-zh}/strings.xml`（撇号 `&apos;`、占位符必须 `%1$s` 位置形式）
- Phase 1 遗留现状：`LogtoAuthManager.signIn` 回调里有 `scope.launch { UserRepository().syncMe(...) }`（与 HomeScreen LaunchedEffect 重复）；`close()`/`scope` 仅为该 launch 存在；HomeScreen LaunchedEffect 内裸构造 `UserRepository()`

---

### Task 1: 分支 + 重构收拢（消双 syncMe / 移除 scope / remember 仓库实例）

**Files:**
- Modify: `androidApp/src/main/kotlin/whl/trending/ai/auth/LogtoAuthManager.kt`
- Modify: `androidApp/src/main/kotlin/whl/trending/ai/MainActivity.kt`
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt`

- [ ] **Step 1: 切分支**

```bash
cd /Users/wanghl/TrendingProjects/TrendingAI && git checkout main && git pull && git checkout -b feat/logto-phase2-feed
```

- [ ] **Step 2: LogtoAuthManager 删除冗余 syncMe 与 scope**

原理：登录成功 → `_authState` 变 `LoggedIn` → HomeScreen 的 `LaunchedEffect(authState)` 必然触发 syncMe，回调内再调一次是纯重复。删除后 `scope`/`close()` 失去存在意义，一并移除（比"记得 cancel"更优的修法）。

具体改动：
1. `signIn` 成功分支删掉 `scope.launch { UserRepository().syncMe(getAccessToken()) }` 及其注释行（保留 `_authState.value = ...` 与 `trackEvent`）
2. 删除字段 `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`
3. 删除 `close()` 方法
4. 删除因此失效的 import：`CoroutineScope`、`Dispatchers`、`SupervisorJob`、`launch`、`cancel`、`UserRepository`

- [ ] **Step 3: MainActivity 删除 close() 调用**

删掉这一行（注入行保留）：
```kotlin
(whl.trending.ai.auth.globalAuthManager as? whl.trending.ai.auth.LogtoAuthManager)?.close()
```

- [ ] **Step 4: HomeScreen 把 UserRepository 提为 remember**

在 `val authManager = globalAuthManager` 旁加：
```kotlin
val userRepository = remember { UserRepository() }
```
`LaunchedEffect` 内 `UserRepository().syncMe(...)` 改为 `userRepository.syncMe(...)`。
需要 import `androidx.compose.runtime.remember`（该文件已有则跳过）。

- [ ] **Step 5: 编译 + 测试**

Run: `./gradlew :androidApp:assembleGithubDebug shared:testAndroidHostTest 2>&1 | tail -3`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor: 收拢 syncMe 单触发点，移除 LogtoAuthManager 冗余 scope

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Profile 错误态加登出出口

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/ProfileScreen.kt`

- [ ] **Step 1: error 分支追加登出按钮**

在 `uiState.isError ->` 分支的重试 `Button` 之后追加（解决 refresh token 永久失效时用户卡死在"重试"循环的问题）：

```kotlin
OutlinedButton(onClick = {
    viewModel.signOut()
    onBack()
}) {
    Text(stringResource(Res.string.sign_out))
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew :androidApp:assembleGithubDebug -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/ProfileScreen.kt
git commit -m "fix: Profile 加载失败时提供登出出口，避免凭证失效后卡死重试

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: GitHub token 链路（Account API + 会话缓存，TDD）

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/auth/AuthManager.kt`（追加常量）
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/data/remote/LogtoAccountApi.kt`
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/auth/GithubTokenProvider.kt`
- Modify: `androidApp/src/main/kotlin/whl/trending/ai/auth/LogtoAuthManager.kt`（endpoint 常量改引用 shared + 登出清缓存）
- Test: `shared/src/commonTest/kotlin/whl/trending/ai/data/remote/LogtoAccountApiTest.kt`

- [ ] **Step 1: 写失败测试（响应模型解析）**

```kotlin
package whl.trending.ai.data.remote

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LogtoAccountApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decode_github_token_response() {
        val payload = """{"access_token":"gho_abc123","scope":"read:user","token_type":"bearer"}"""
        val resp = json.decodeFromString<GithubTokenResponse>(payload)
        assertEquals("gho_abc123", resp.accessToken)
    }

    @Test
    fun decode_minimal_response() {
        val payload = """{"access_token":"gho_x"}"""
        val resp = json.decodeFromString<GithubTokenResponse>(payload)
        assertEquals("gho_x", resp.accessToken)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew shared:testAndroidHostTest --tests "*LogtoAccountApiTest*" 2>&1 | tail -5`
Expected: 编译失败（GithubTokenResponse 不存在）

- [ ] **Step 3: shared 加 LOGTO_ENDPOINT 常量**

`AuthManager.kt` 文件末尾追加：
```kotlin
/** Logto 租户端点：客户端 SDK 与 Account API 共用 */
const val LOGTO_ENDPOINT = "https://28bniv.logto.app"
```

- [ ] **Step 4: 实现 `LogtoAccountApi.kt`**

```kotlin
package whl.trending.ai.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import whl.trending.ai.auth.LOGTO_ENDPOINT

@Serializable
data class GithubTokenResponse(
    @SerialName("access_token") val accessToken: String,
)

/**
 * Logto Account API：从 Secret Vault 取回用户的第三方（GitHub）access token。
 * 凭据是用户自己的 Logto opaque access token（登录 scope 已含 identities）。
 */
open class LogtoAccountApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 15000
        }
    }

    /**
     * 取 GitHub access token。404=该用户未存 token（如撤销过授权），返回 null；
     * 其余非 2xx 抛 ApiException（401 含 token 过期，调用方决定重试/登出）。
     */
    open suspend fun fetchGithubToken(logtoAccessToken: String): String? {
        val response = client.get("$LOGTO_ENDPOINT/api/my-account/identities/github/access-token") {
            header(HttpHeaders.Authorization, "Bearer $logtoAccessToken")
        }
        if (response.status.value == 404) return null
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<GithubTokenResponse>().accessToken
    }
}
```
（`Json`、`bodyAsText` 需对应 import：`kotlinx.serialization.json.Json`、`io.ktor.client.statement.bodyAsText`。`ApiException` 复用 `data/remote` 现有定义——若它在 `TrendingApi.kt` 内部且不可见，将其提升为同包顶层类，注意保持既有调用点编译通过。）

- [ ] **Step 5: 实现 `GithubTokenProvider.kt`**

```kotlin
package whl.trending.ai.auth

import whl.trending.ai.data.remote.LogtoAccountApi

/**
 * GitHub token 会话级缓存。GitHub OAuth App 的 access token 永不过期
 * （用户主动撤销授权才失效），进程内取一次即可；不落盘，避免明文持久化第三方凭据。
 */
open class GithubTokenProvider(
    private val accountApi: LogtoAccountApi = LogtoAccountApi(),
    private val authManager: () -> AuthManager = { globalAuthManager },
) {
    private var cached: String? = null

    open suspend fun get(): String? {
        cached?.let { return it }
        val logtoToken = authManager().getAccessToken() ?: return null
        return try {
            accountApi.fetchGithubToken(logtoToken)?.also { cached = it }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    fun clear() {
        cached = null
    }

    companion object {
        /** 全局共享实例：登出时由 LogtoAuthManager 清空 */
        val shared = GithubTokenProvider()
    }
}
```

- [ ] **Step 6: LogtoAuthManager 接线**

1. companion 里删除 `private const val LOGTO_ENDPOINT = ...`，改 import shared 的 `whl.trending.ai.auth.LOGTO_ENDPOINT`（LogtoConfig 的 endpoint 参数引用它）
2. `signOut()` 中 `setUserAvatarUrl(null)` 旁追加：`GithubTokenProvider.shared.clear()`

- [ ] **Step 7: 测试 + 编译**

Run: `./gradlew shared:testAndroidHostTest :androidApp:assembleGithubDebug 2>&1 | tail -3`
Expected: BUILD SUCCESSFUL（新增 2 个解析测试通过）

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/whl/trending/ai shared/src/commonTest androidApp/src/main/kotlin/whl/trending/ai/auth/LogtoAuthManager.kt
git commit -m "feat: GithubTokenProvider——经 Logto Account API 取 GitHub token 并做会话缓存

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: GithubApi + 事件 DTO（TDD）

**Files:**
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/data/remote/GithubApi.kt`
- Test: `shared/src/commonTest/kotlin/whl/trending/ai/data/remote/GithubApiModelsTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package whl.trending.ai.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class GithubApiModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decode_github_user() {
        val payload = """
            {"login":"HarlonWang","followers":491,"following":81,"public_repos":24,"name":"Harlon"}
        """.trimIndent()
        val user = json.decodeFromString<GithubUser>(payload)
        assertEquals("HarlonWang", user.login)
        assertEquals(491, user.followers)
        assertEquals(81, user.following)
        assertEquals(24, user.publicRepos)
    }

    @Test
    fun decode_event_with_payload_kept_raw() {
        val payload = """
            [{"id":"22249084947","type":"WatchEvent",
              "actor":{"id":1,"login":"octocat","avatar_url":"https://a.png"},
              "repo":{"id":2,"name":"octocat/Hello-World"},
              "payload":{"action":"started"},
              "public":true,"created_at":"2026-06-09T12:47:28Z"}]
        """.trimIndent()
        val events = json.decodeFromString<List<GithubEventDto>>(payload)
        assertEquals(1, events.size)
        assertEquals("WatchEvent", events[0].type)
        assertEquals("octocat", events[0].actor.login)
        assertEquals("octocat/Hello-World", events[0].repo.name)
        assertEquals("started", events[0].payload?.jsonObject?.get("action")?.jsonPrimitive?.content)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew shared:testAndroidHostTest --tests "*GithubApiModelsTest*" 2>&1 | tail -5`
Expected: 编译失败

- [ ] **Step 3: 实现 `GithubApi.kt`**

```kotlin
package whl.trending.ai.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class GithubUser(
    val login: String,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("public_repos") val publicRepos: Int = 0,
)

@Serializable
data class GithubEventActor(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class GithubEventRepo(
    val name: String,
)

@Serializable
data class GithubEventDto(
    val id: String,
    val type: String,
    val actor: GithubEventActor,
    val repo: GithubEventRepo,
    /** 各事件类型 payload 结构不同，保留原始 JSON 由 mapper 弹性提取 */
    val payload: JsonElement? = null,
    @SerialName("created_at") val createdAt: String,
)

/** GitHub REST 直连：feed 与计数。token 来自 GithubTokenProvider（Secret Vault 取回）。 */
open class GithubApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 15000
        }
    }

    private val baseHost = "https://api.github.com"

    open suspend fun fetchUser(githubToken: String): GithubUser {
        val response = client.get("$baseHost/user") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<GithubUser>()
    }

    open suspend fun fetchReceivedEvents(
        githubToken: String,
        login: String,
        page: Int,
        perPage: Int = 30,
    ): List<GithubEventDto> {
        val response = client.get("$baseHost/users/$login/received_events") {
            header(HttpHeaders.Authorization, "Bearer $githubToken")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", perPage)
            parameter("page", page)
        }
        if (response.status.value !in 200..299) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body<List<GithubEventDto>>()
    }
}
```

- [ ] **Step 4: 测试通过 + Commit**

Run: `./gradlew shared:testAndroidHostTest 2>&1 | tail -3` → BUILD SUCCESSFUL

```bash
git add shared/src/commonMain/kotlin/whl/trending/ai/data/remote/GithubApi.kt shared/src/commonTest/kotlin/whl/trending/ai/data/remote/GithubApiModelsTest.kt
git commit -m "feat: GithubApi——直连 GitHub REST 取用户计数与 received_events

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: 事件归一化 mapper（TDD 重点）

**Files:**
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/GithubFeedItem.kt`
- Test: `shared/src/commonTest/kotlin/whl/trending/ai/ui/profile/GithubFeedMapperTest.kt`

- [ ] **Step 1: 写失败测试（覆盖全部映射类型 + fallback + 非法 payload）**

```kotlin
package whl.trending.ai.ui.profile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import whl.trending.ai.data.remote.GithubEventActor
import whl.trending.ai.data.remote.GithubEventDto
import whl.trending.ai.data.remote.GithubEventRepo
import kotlin.test.Test
import kotlin.test.assertEquals

class GithubFeedMapperTest {
    private fun event(type: String, payloadJson: String?): GithubEventDto = GithubEventDto(
        id = "1",
        type = type,
        actor = GithubEventActor(login = "octocat", avatarUrl = "https://a.png"),
        repo = GithubEventRepo(name = "owner/repo"),
        payload = payloadJson?.let { Json.parseToJsonElement(it) },
        createdAt = "2026-06-09T12:47:28Z",
    )

    @Test
    fun watch_event_maps_to_starred() {
        val item = event("WatchEvent", """{"action":"started"}""").toFeedItem()
        assertEquals(GithubFeedKind.STARRED, item.kind)
        assertEquals("https://github.com/owner/repo", item.targetUrl)
    }

    @Test
    fun fork_event() {
        assertEquals(GithubFeedKind.FORKED, event("ForkEvent", "{}").toFeedItem().kind)
    }

    @Test
    fun create_event_branch_and_repo_and_tag() {
        val branch = event("CreateEvent", """{"ref":"dev","ref_type":"branch"}""").toFeedItem()
        assertEquals(GithubFeedKind.CREATED_BRANCH, branch.kind)
        assertEquals("dev", branch.primary)

        val repo = event("CreateEvent", """{"ref":null,"ref_type":"repository"}""").toFeedItem()
        assertEquals(GithubFeedKind.CREATED_REPO, repo.kind)

        val tag = event("CreateEvent", """{"ref":"v1.0","ref_type":"tag"}""").toFeedItem()
        assertEquals(GithubFeedKind.CREATED_TAG, tag.kind)
        assertEquals("v1.0", tag.primary)
    }

    @Test
    fun release_event_uses_release_url_and_tag() {
        val item = event(
            "ReleaseEvent",
            """{"action":"published","release":{"tag_name":"v2.1","html_url":"https://github.com/owner/repo/releases/tag/v2.1"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.RELEASED, item.kind)
        assertEquals("v2.1", item.primary)
        assertEquals("https://github.com/owner/repo/releases/tag/v2.1", item.targetUrl)
    }

    @Test
    fun push_event_counts_commits() {
        val item = event("PushEvent", """{"size":3,"ref":"refs/heads/main"}""").toFeedItem()
        assertEquals(GithubFeedKind.PUSHED, item.kind)
        assertEquals("3", item.primary)
    }

    @Test
    fun pull_request_opened_merged_closed() {
        val opened = event(
            "PullRequestEvent",
            """{"action":"opened","number":12,"pull_request":{"merged":false,"html_url":"https://github.com/owner/repo/pull/12"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.PR_OPENED, opened.kind)
        assertEquals("12", opened.primary)
        assertEquals("https://github.com/owner/repo/pull/12", opened.targetUrl)

        val merged = event(
            "PullRequestEvent",
            """{"action":"closed","number":13,"pull_request":{"merged":true,"html_url":"https://github.com/owner/repo/pull/13"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.PR_MERGED, merged.kind)

        val closed = event(
            "PullRequestEvent",
            """{"action":"closed","number":14,"pull_request":{"merged":false,"html_url":"https://github.com/owner/repo/pull/14"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.PR_CLOSED, closed.kind)
    }

    @Test
    fun issues_and_comment_events() {
        val opened = event(
            "IssuesEvent",
            """{"action":"opened","issue":{"number":7,"html_url":"https://github.com/owner/repo/issues/7"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.ISSUE_OPENED, opened.kind)
        assertEquals("7", opened.primary)

        val closed = event(
            "IssuesEvent",
            """{"action":"closed","issue":{"number":8,"html_url":"https://github.com/owner/repo/issues/8"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.ISSUE_CLOSED, closed.kind)

        val comment = event(
            "IssueCommentEvent",
            """{"action":"created","issue":{"number":9,"html_url":"https://github.com/owner/repo/issues/9"},"comment":{"html_url":"https://github.com/owner/repo/issues/9#issuecomment-1"}}"""
        ).toFeedItem()
        assertEquals(GithubFeedKind.ISSUE_COMMENTED, comment.kind)
        assertEquals("https://github.com/owner/repo/issues/9#issuecomment-1", comment.targetUrl)
    }

    @Test
    fun public_event_and_unknown_fallback() {
        assertEquals(GithubFeedKind.MADE_PUBLIC, event("PublicEvent", "{}").toFeedItem().kind)

        val other = event("MemberEvent", """{"action":"added"}""").toFeedItem()
        assertEquals(GithubFeedKind.OTHER, other.kind)
        assertEquals("Member", other.primary) // type 去掉 Event 后缀作展示
    }

    @Test
    fun malformed_payload_does_not_crash() {
        val item = event("PullRequestEvent", null).toFeedItem()
        assertEquals(GithubFeedKind.OTHER, item.kind)
        assertEquals("https://github.com/owner/repo", item.targetUrl)
    }
}
```

- [ ] **Step 2: 运行确认失败** → 编译失败

- [ ] **Step 3: 实现 `GithubFeedItem.kt`**

```kotlin
package whl.trending.ai.ui.profile

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import whl.trending.ai.data.remote.GithubEventDto

enum class GithubFeedKind {
    STARRED, FORKED, CREATED_REPO, CREATED_BRANCH, CREATED_TAG, RELEASED,
    PUSHED, PR_OPENED, PR_MERGED, PR_CLOSED,
    ISSUE_OPENED, ISSUE_CLOSED, ISSUE_COMMENTED, MADE_PUBLIC, OTHER,
}

/**
 * 归一化后的 feed 条目：kind + primary（分支名/tag/编号/提交数/类型名，按 kind 而定），
 * 文案在 UI 层用 stringResource 按 kind 组装（i18n）。
 */
data class GithubFeedItem(
    val id: String,
    val actorLogin: String,
    val actorAvatarUrl: String?,
    val repoName: String,
    val kind: GithubFeedKind,
    val primary: String?,
    val createdAt: String,
    val targetUrl: String,
)

fun GithubEventDto.toFeedItem(): GithubFeedItem {
    val p = payload as? JsonObject ?: (payload?.let { runCatching { it.jsonObject }.getOrNull() })
    val repoUrl = "https://github.com/${repo.name}"

    fun str(obj: JsonObject?, key: String): String? =
        obj?.get(key)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    fun obj(parent: JsonObject?, key: String): JsonObject? =
        parent?.get(key)?.let { runCatching { it.jsonObject }.getOrNull() }

    var kind = GithubFeedKind.OTHER
    var primary: String? = type.removeSuffix("Event")
    var targetUrl = repoUrl

    when (type) {
        "WatchEvent" -> { kind = GithubFeedKind.STARRED; primary = null }
        "ForkEvent" -> { kind = GithubFeedKind.FORKED; primary = null }
        "PublicEvent" -> { kind = GithubFeedKind.MADE_PUBLIC; primary = null }
        "CreateEvent" -> when (str(p, "ref_type")) {
            "repository" -> { kind = GithubFeedKind.CREATED_REPO; primary = null }
            "branch" -> { kind = GithubFeedKind.CREATED_BRANCH; primary = str(p, "ref") }
            "tag" -> { kind = GithubFeedKind.CREATED_TAG; primary = str(p, "ref") }
        }
        "ReleaseEvent" -> {
            val release = obj(p, "release")
            kind = GithubFeedKind.RELEASED
            primary = str(release, "tag_name")
            str(release, "html_url")?.let { targetUrl = it }
        }
        "PushEvent" -> {
            kind = GithubFeedKind.PUSHED
            primary = (p?.get("size")?.jsonPrimitive?.intOrNull ?: 1).toString()
        }
        "PullRequestEvent" -> {
            val pr = obj(p, "pull_request")
            val number = p?.get("number")?.jsonPrimitive?.intOrNull
            if (pr != null && number != null) {
                kind = when {
                    str(p, "action") == "opened" -> GithubFeedKind.PR_OPENED
                    str(p, "action") == "closed" &&
                        pr["merged"]?.jsonPrimitive?.booleanOrNull == true -> GithubFeedKind.PR_MERGED
                    str(p, "action") == "closed" -> GithubFeedKind.PR_CLOSED
                    else -> GithubFeedKind.OTHER
                }
                if (kind != GithubFeedKind.OTHER) {
                    primary = number.toString()
                    str(pr, "html_url")?.let { targetUrl = it }
                }
            }
        }
        "IssuesEvent" -> {
            val issue = obj(p, "issue")
            val number = issue?.get("number")?.jsonPrimitive?.intOrNull
            if (issue != null && number != null) {
                kind = when (str(p, "action")) {
                    "opened" -> GithubFeedKind.ISSUE_OPENED
                    "closed" -> GithubFeedKind.ISSUE_CLOSED
                    else -> GithubFeedKind.OTHER
                }
                if (kind != GithubFeedKind.OTHER) {
                    primary = number.toString()
                    str(issue, "html_url")?.let { targetUrl = it }
                }
            }
        }
        "IssueCommentEvent" -> {
            val issue = obj(p, "issue")
            val number = issue?.get("number")?.jsonPrimitive?.intOrNull
            if (number != null) {
                kind = GithubFeedKind.ISSUE_COMMENTED
                primary = number.toString()
                targetUrl = str(obj(p, "comment"), "html_url")
                    ?: str(issue, "html_url")
                    ?: repoUrl
            }
        }
    }

    return GithubFeedItem(
        id = id,
        actorLogin = actor.login,
        actorAvatarUrl = actor.avatarUrl,
        repoName = repo.name,
        kind = kind,
        primary = primary,
        createdAt = createdAt,
        targetUrl = targetUrl,
    )
}
```

注意：若实现与测试期望有出入（如 PR 事件 payload 缺失时的 fallback 行为），以测试为准修实现。

- [ ] **Step 4: 测试通过 + Commit**

Run: `./gradlew shared:testAndroidHostTest 2>&1 | tail -3` → BUILD SUCCESSFUL（mapper 9 个测试全过）

```bash
git add shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/GithubFeedItem.kt shared/src/commonTest/kotlin/whl/trending/ai/ui/profile/GithubFeedMapperTest.kt
git commit -m "feat: received_events 事件归一化 mapper（14 类事件 + fallback）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: ProfileViewModel 扩展（计数 + feed 分页）

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/ProfileViewModel.kt`

- [ ] **Step 1: 重写 ProfileViewModel**

```kotlin
package whl.trending.ai.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.GithubTokenProvider
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.model.MeUser
import whl.trending.ai.data.remote.GithubApi
import whl.trending.ai.data.remote.GithubUser
import whl.trending.ai.data.repository.UserRepository

private const val FEED_PAGE_SIZE = 30
private const val FEED_MAX_EVENTS = 300 // GitHub received_events 硬上限

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: MeUser? = null,
    val isError: Boolean = false,
    /** GitHub 实时计数；token 不可用或请求失败时为 null（UI 隐藏计数行） */
    val githubUser: GithubUser? = null,
    val feedItems: List<GithubFeedItem> = emptyList(),
    val isFeedLoading: Boolean = false,
    val feedEndReached: Boolean = false,
    /** feed 不可用（无 GitHub token / 请求失败），与整页 isError 区分 */
    val feedUnavailable: Boolean = false,
)

class ProfileViewModel(
    private val repository: UserRepository = UserRepository(),
    private val githubApi: GithubApi = GithubApi(),
    private val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
    private val authManager: AuthManager = globalAuthManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var nextFeedPage = 1

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState(isLoading = true)
            nextFeedPage = 1
            val token = authManager.getAccessToken()
            if (token == null) {
                _uiState.value = ProfileUiState(isLoading = false, isError = true)
                return@launch
            }
            val user = try {
                repository.fetchMe(token)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = ProfileUiState(isLoading = false, isError = true)
                return@launch
            }
            _uiState.value = ProfileUiState(isLoading = false, user = user)
            loadGithubData(user)
        }
    }

    private suspend fun loadGithubData(user: MeUser) {
        val githubToken = tokenProvider.get()
        val login = user.githubLogin
        if (githubToken == null || login == null) {
            _uiState.value = _uiState.value.copy(feedUnavailable = true)
            return
        }
        try {
            val githubUser = githubApi.fetchUser(githubToken)
            _uiState.value = _uiState.value.copy(githubUser = githubUser)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 计数失败不致命，feed 继续尝试
        }
        loadMoreFeed()
    }

    fun loadMoreFeed() {
        val state = _uiState.value
        if (state.isFeedLoading || state.feedEndReached || state.feedUnavailable) return
        val login = state.user?.githubLogin ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFeedLoading = true)
            val githubToken = tokenProvider.get()
            if (githubToken == null) {
                _uiState.value = _uiState.value.copy(isFeedLoading = false, feedUnavailable = true)
                return@launch
            }
            try {
                val events = githubApi.fetchReceivedEvents(githubToken, login, nextFeedPage, FEED_PAGE_SIZE)
                val newItems = events.map { it.toFeedItem() }
                val merged = (_uiState.value.feedItems + newItems).distinctBy { it.id }
                val endReached = events.size < FEED_PAGE_SIZE || merged.size >= FEED_MAX_EVENTS
                nextFeedPage++
                _uiState.value = _uiState.value.copy(
                    feedItems = merged,
                    isFeedLoading = false,
                    feedEndReached = endReached,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isFeedLoading = false,
                    feedUnavailable = _uiState.value.feedItems.isEmpty(),
                )
            }
        }
    }

    fun signOut() = authManager.signOut()
}
```

- [ ] **Step 2: 编译**

Run: `./gradlew :androidApp:assembleGithubDebug -q` → BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/ProfileViewModel.kt
git commit -m "feat: ProfileViewModel 扩展——GitHub 计数 + feed 分页加载

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: ProfileScreen 一体化改造 + 文案

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/ProfileScreen.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`、`values-zh/strings.xml`

- [ ] **Step 1: 文案**

`values/strings.xml` 追加：
```xml
<string name="profile_followers">Followers</string>
<string name="profile_following">Following</string>
<string name="profile_repos">Repos</string>
<string name="feed_starred">Starred %1$s</string>
<string name="feed_forked">Forked %1$s</string>
<string name="feed_created_repo">Created repository %1$s</string>
<string name="feed_created_branch">Created branch %1$s in %2$s</string>
<string name="feed_created_tag">Created tag %1$s in %2$s</string>
<string name="feed_released">Released %1$s in %2$s</string>
<string name="feed_pushed">Pushed %1$s commits to %2$s</string>
<string name="feed_pr_opened">Opened PR #%1$s in %2$s</string>
<string name="feed_pr_merged">Merged PR #%1$s in %2$s</string>
<string name="feed_pr_closed">Closed PR #%1$s in %2$s</string>
<string name="feed_issue_opened">Opened issue #%1$s in %2$s</string>
<string name="feed_issue_closed">Closed issue #%1$s in %2$s</string>
<string name="feed_issue_commented">Commented on #%1$s in %2$s</string>
<string name="feed_made_public">Open sourced %1$s</string>
<string name="feed_other">%1$s in %2$s</string>
<string name="feed_empty">No recent activity</string>
<string name="feed_end_notice">Only activity from the last 30 days is shown</string>
<string name="feed_unavailable">Activity feed unavailable</string>
```
`values-zh/strings.xml` 追加：
```xml
<string name="profile_followers">粉丝</string>
<string name="profile_following">关注</string>
<string name="profile_repos">仓库</string>
<string name="feed_starred">Star 了 %1$s</string>
<string name="feed_forked">Fork 了 %1$s</string>
<string name="feed_created_repo">创建了仓库 %1$s</string>
<string name="feed_created_branch">在 %2$s 创建了分支 %1$s</string>
<string name="feed_created_tag">在 %2$s 创建了标签 %1$s</string>
<string name="feed_released">在 %2$s 发布了 %1$s</string>
<string name="feed_pushed">推送了 %1$s 个提交到 %2$s</string>
<string name="feed_pr_opened">在 %2$s 开启了 PR #%1$s</string>
<string name="feed_pr_merged">在 %2$s 合并了 PR #%1$s</string>
<string name="feed_pr_closed">在 %2$s 关闭了 PR #%1$s</string>
<string name="feed_issue_opened">在 %2$s 开启了 issue #%1$s</string>
<string name="feed_issue_closed">在 %2$s 关闭了 issue #%1$s</string>
<string name="feed_issue_commented">评论了 %2$s 的 #%1$s</string>
<string name="feed_made_public">开源了 %1$s</string>
<string name="feed_other">%1$s · %2$s</string>
<string name="feed_empty">暂无动态</string>
<string name="feed_end_notice">仅展示最近 30 天的动态</string>
<string name="feed_unavailable">动态加载不可用</string>
```
（两个文件各追加 21 条，key 与条目数一一对应。）

- [ ] **Step 2: 重写 ProfileScreen 为 LazyColumn 一体化**

结构（保持现有 import 风格，新增 LazyColumn/itemsIndexed/HorizontalDivider/coil 等所需 import）：

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val viewModel: ProfileViewModel = viewModel { ProfileViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()

    // 滚动到底部附近时自动加载下一页
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreFeed()
    }

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
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.isError -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(Res.string.profile_load_failed), Modifier.padding(top = 48.dp))
                Button(onClick = { viewModel.load() }) { Text(stringResource(Res.string.profile_retry)) }
                OutlinedButton(onClick = { viewModel.signOut(); onBack() }) {
                    Text(stringResource(Res.string.sign_out))
                }
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item(key = "header") {
                    ProfileHeader(
                        uiState = uiState,
                        onOpenGithub = { url -> uriHandler.openUri(url) },
                        onSignOut = { viewModel.signOut(); onBack() },
                    )
                }
                if (uiState.feedUnavailable && uiState.feedItems.isEmpty()) {
                    item(key = "feed_unavailable") {
                        FeedNotice(stringResource(Res.string.feed_unavailable))
                    }
                } else if (uiState.feedItems.isEmpty() && uiState.feedEndReached) {
                    item(key = "feed_empty") {
                        FeedNotice(stringResource(Res.string.feed_empty))
                    }
                }
                items(uiState.feedItems, key = { it.id }) { item ->
                    GithubFeedRow(item = item, onClick = { uriHandler.openUri(item.targetUrl) })
                    HorizontalDivider(thickness = 0.5.dp)
                }
                if (uiState.isFeedLoading) {
                    item(key = "feed_loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                if (uiState.feedEndReached && uiState.feedItems.isNotEmpty()) {
                    item(key = "feed_end") {
                        FeedNotice(stringResource(Res.string.feed_end_notice))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    uiState: ProfileUiState,
    onOpenGithub: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val user = uiState.user ?: return
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(96.dp).clip(CircleShape)
        )
        Text(user.displayName ?: user.githubLogin.orEmpty(), style = MaterialTheme.typography.titleLarge)
        user.githubLogin?.let {
            Text("@$it", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        user.bio?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        uiState.githubUser?.let { gh ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CountCell(gh.followers, stringResource(Res.string.profile_followers))
                CountCell(gh.following, stringResource(Res.string.profile_following))
                CountCell(gh.publicRepos, stringResource(Res.string.profile_repos))
            }
        }
        Spacer(Modifier.height(4.dp))
        user.htmlUrl?.let { url ->
            Button(onClick = { onOpenGithub(url) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.profile_open_github))
            }
        }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.sign_out))
        }
    }
}

@Composable
private fun CountCell(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeedNotice(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GithubFeedRow(item: GithubFeedItem, onClick: () -> Unit) {
    val summary = when (item.kind) {
        GithubFeedKind.STARRED -> stringResource(Res.string.feed_starred, item.repoName)
        GithubFeedKind.FORKED -> stringResource(Res.string.feed_forked, item.repoName)
        GithubFeedKind.CREATED_REPO -> stringResource(Res.string.feed_created_repo, item.repoName)
        GithubFeedKind.CREATED_BRANCH -> stringResource(Res.string.feed_created_branch, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.CREATED_TAG -> stringResource(Res.string.feed_created_tag, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.RELEASED -> stringResource(Res.string.feed_released, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.PUSHED -> stringResource(Res.string.feed_pushed, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.PR_OPENED -> stringResource(Res.string.feed_pr_opened, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.PR_MERGED -> stringResource(Res.string.feed_pr_merged, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.PR_CLOSED -> stringResource(Res.string.feed_pr_closed, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.ISSUE_OPENED -> stringResource(Res.string.feed_issue_opened, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.ISSUE_CLOSED -> stringResource(Res.string.feed_issue_closed, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.ISSUE_COMMENTED -> stringResource(Res.string.feed_issue_commented, item.primary.orEmpty(), item.repoName)
        GithubFeedKind.MADE_PUBLIC -> stringResource(Res.string.feed_made_public, item.repoName)
        GithubFeedKind.OTHER -> stringResource(Res.string.feed_other, item.primary.orEmpty(), item.repoName)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = item.actorAvatarUrl,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(CircleShape)
        )
        Column(Modifier.weight(1f)) {
            Text(item.actorLogin, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                DateTimeUtils.formatToLocalTime(item.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

（`DateTimeUtils` import `whl.trending.ai.core.DateTimeUtils`；若 `formatToLocalTime` 是 object 方法按现状调用。Res 字符串 import 按该文件现有逐条风格补齐全部新 key。）

- [ ] **Step 3: 编译 + 全量测试**

Run: `./gradlew :androidApp:assembleGithubDebug shared:testAndroidHostTest 2>&1 | tail -3` → BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/whl/trending/ai/ui/profile shared/src/commonMain/composeResources
git commit -m "feat: Profile 一体化——头部计数 + GitHub 动态流（分页/空态/到底提示）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: 端到端验证 + 收尾

- [ ] **Step 1: 安装运行**

Run: `./gradlew :androidApp:installGithubDebug && adb shell am start -n whl.trending.ai.debug/whl.trending.ai.MainActivity`

- [ ] **Step 2: 验证清单**

1. 已登录态打开 Profile：头部出现 followers/following/repos 三计数（与 github.com 个人页一致）
2. 头部下方出现动态流：star/fork/release/push 等事件正确渲染（actor 头像 + 文案 + 时间）
3. 滚动到底自动翻页；到达末尾显示"仅展示最近 30 天的动态"
4. 点击条目跳转对应 GitHub 页面（release 跳 release 页、PR 跳 PR 页）
5. 中文环境文案正确（设置切中文验证 2-3 条）
6. 登出 → 重新登录 → feed 正常（GithubTokenProvider 缓存清理生效）
7. 错误态（断网打开 Profile）：显示失败 + 重试 + 登出按钮

- [ ] **Step 3: 推送 + PR**

```bash
git push -u origin feat/logto-phase2-feed
gh pr create --title "feat: GitHub 动态流 + Profile 计数（Logto Phase 2）" --body "$(cat <<'EOF'
## Summary
- Profile 页一体化：头部新增 followers/following/repos 实时计数，下方接 GitHub Dashboard 风格动态流（received_events，分页加载，14 类事件归一化渲染，30 天/300 条到底提示）
- GitHub token 链路：经 Logto Account API 从 Secret Vault 取回 GitHub token，会话内存缓存，登出清理（Worker 零改动）
- Phase 1 遗留清理：收拢双 syncMe 为单触发点、UserRepository 提为 remember、Profile 错误态增加登出出口

## Test Plan
- [x] shared 单测全绿（Account API/GitHub 模型解析 + mapper 全事件类型覆盖）
- [x] 模拟器端到端：计数与 github.com 一致、动态流渲染/翻页/到底提示、条目跳转、中英文案、登出重登、断网错误态

设计文档：docs/superpowers/specs/2026-06-09-trendingai-logto-auth-design.md §6/§8

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## 明确不做（Phase 2 范围外）

- 不做 ETag 304 轮询优化（首版直接分页拉取，5000 次/小时配额远够）
- 不做 feed 本地缓存/离线
- 不做下拉刷新（返回再进即重新加载；如验证后觉得必要再加）
- 不做 iOS 登录接入（UI 为 CMP 共享代码本就跨端，iOS 缺的只是 AuthManager 的 Logto Swift SDK 实现；未接入前 `isSupported=false` 使登录入口自动隐藏，Profile/Feed 不可达。将来接入仅需：Swift SDK + AuthManager iOS 实现注入 + 控制台加 iOS redirect URI，shared 全部资产零改动复用）
- Worker/后端零改动
