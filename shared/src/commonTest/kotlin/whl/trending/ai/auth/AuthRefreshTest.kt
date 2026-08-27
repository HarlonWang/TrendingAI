package whl.trending.ai.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import wang.harlon.loginbase.AuthClient
import wang.harlon.loginbase.InMemoryTokenStore
import wang.harlon.loginbase.TokenPair
import whl.trending.ai.data.remote.installTrendingAuth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 鉴权接线：带 token、认 401、刷新、重试一次全由 ktor `Auth` 插件完成（`data/remote/TrendingAuth.kt`），
 * 业务代码不再经手 token。这里测的就是那条接线——它写错了不会有任何编译或运行期报错。
 */
class AuthRefreshTest {

    private class FakeAuth(
        private var token: String?,
        private val refreshedTo: String? = null,
    ) : AuthManager {
        var refreshCalls = 0
        override val isSupported = true
        override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.LoggedIn)
        override fun signIn(source: String) {}
        override fun signOut() {}
        override suspend fun getAccessToken(): String? = token
        override suspend fun refreshAccessToken(): String? {
            refreshCalls++
            token = refreshedTo
            return refreshedTo
        }
    }

    /**
     * 假服务端：只认 [validToken]。**401 响应刻意不带 `WWW-Authenticate`**——我们后端的
     * `jsonError('Unauthorized', 401)` 就不带，而 ktor 只有在「装了不止一个 provider」时才要求它
     * （`Auth.findProvider`）。这个用例同时钉住那条前提。
     */
    private fun server(validToken: String?, hits: MutableList<String?>) = MockEngine { request ->
        val header = request.headers[HttpHeaders.Authorization]
        hits += header
        // validToken = null 表示匿名可用的端点（/api/quota）：不带头也照样 200
        if (validToken == null || header == "Bearer $validToken") {
            respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        } else {
            respond("", HttpStatusCode.Unauthorized)
        }
    }

    private fun clientFor(auth: AuthManager, engine: MockEngine) =
        HttpClient(engine) { installTrendingAuth { auth } }

    @Test
    fun `401 后刷新并用新 token 重试一次`() = runTest {
        val hits = mutableListOf<String?>()
        val auth = FakeAuth(token = "a0", refreshedTo = "a1")
        val response = clientFor(auth, server(validToken = "a1", hits = hits))
            .get("https://api.trendingai.cn/api/me")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, auth.refreshCalls)
        assertEquals(listOf<String?>("Bearer a0", "Bearer a1"), hits.toList())
    }

    @Test
    fun `刷新后仍 401 不再刷新——插件的 circuit breaker 兜住死循环`() = runTest {
        // 别的原因造成的 401（权限不足、服务端 bug）不该被当成过期无限刷下去
        val hits = mutableListOf<String?>()
        val auth = FakeAuth(token = "a0", refreshedTo = "a1")
        val response = clientFor(auth, server(validToken = "never", hits = hits))
            .get("https://api.trendingai.cn/api/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, auth.refreshCalls)
        assertEquals(2, hits.size)
    }

    @Test
    fun `无会话时请求照发、不带 Authorization`() = runTest {
        // 匿名可用的端点（/api/quota）靠这条：手写的「无会话就短路」包装器套不进来
        val hits = mutableListOf<String?>()
        val auth = FakeAuth(token = null)
        clientFor(auth, server(validToken = null, hits = hits)).get("https://api.trendingai.cn/api/quota")

        assertEquals(listOf<String?>(null), hits.take(1))
        assertEquals(0, auth.refreshCalls)
    }

    @Test
    fun `会话被服务端终结时发出提示——否则用户只会发现自己莫名未登录`() = runTest {
        SignInFailureBus.consume() // 清掉别的用例可能留下的重放
        val seen = mutableListOf<SignInFailureReason>()
        val collector = launch { SignInFailureBus.events.collect { seen += it } }

        assertNull(managerWith(refreshOk = false).refreshAccessToken())
        runCurrent()

        assertEquals(listOf(SignInFailureReason.SESSION_EXPIRED), seen)
        collector.cancel()
        SignInFailureBus.consume()
    }

    @Test
    fun `网络原因刷新失败不提示——会话可能好好的，别打扰`() = runTest {
        SignInFailureBus.consume()
        val seen = mutableListOf<SignInFailureReason>()
        val collector = launch { SignInFailureBus.events.collect { seen += it } }

        // 刷新请求直接抛（网络不通）→ RefreshOutcome.Failed，不是 SessionEnded
        val client = AuthClient("https://x/auth", InMemoryTokenStore(TokenPair("a0", "r0"))) {
            httpEngine = MockEngine { throw RuntimeException("network down") }
        }
        assertNull(LoginbaseAuthManager(client, CoroutineScope(Dispatchers.Unconfined)).refreshAccessToken())
        runCurrent()

        assertEquals(emptyList(), seen)
        collector.cancel()
        SignInFailureBus.consume()
    }

    private fun managerWith(
        tokens: TokenPair? = TokenPair("a0", "r0"),
        refreshOk: Boolean = true,
    ): LoginbaseAuthManager {
        val engine = MockEngine {
            if (refreshOk) {
                respond(
                    """{"accessToken":"a1","refreshToken":"r1"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    """{"error":"invalid_refresh_token","reason":"session_revoked"}""",
                    HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val client = AuthClient("https://x/auth", InMemoryTokenStore(tokens)) { httpEngine = engine }
        // 传入 scope：默认的 Dispatchers.Main 在单测环境不存在（init 块会立刻炸）
        return LoginbaseAuthManager(client, CoroutineScope(Dispatchers.Unconfined))
    }
}
