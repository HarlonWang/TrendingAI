package whl.trending.ai.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import wang.harlon.loginbase.AuthClient
import wang.harlon.loginbase.InMemoryTokenStore
import wang.harlon.loginbase.TokenPair
import whl.trending.ai.data.remote.ApiException

/**
 * 业务请求的 401 重试（[AuthManager.authorized]）。
 *
 * 缘起：access token 只有 1 小时，过期后各调用方直接把「加载失败」摆给用户
 * ——实测账户页显示 "Couldn't load credits right now"，其实刷新一下就能继续。
 */
class AuthorizedRetryTest {

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
        val client = AuthClient("https://x/auth", InMemoryTokenStore(tokens), HttpClient(engine))
        // 传入 scope：默认的 Dispatchers.Main 在单测环境不存在（init 块会立刻炸）
        return LoginbaseAuthManager(client, "app://cb", CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun `401 后刷新并用新 token 重试一次`() = runTest {
        var calls = 0
        val seen = mutableListOf<String>()
        val result = managerWith().authorized { token ->
            calls++
            seen += token
            if (calls == 1) throw ApiException(401, "expired") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, calls, "应当重试一次")
        assertEquals(listOf("a0", "a1"), seen, "重试必须用刷新后的新 token")
    }

    @Test
    fun `只重试一次——刷新后仍 401 说明不是过期问题`() = runTest {
        var calls = 0
        assertFailsWith<ApiException> {
            managerWith().authorized { calls++; throw ApiException(401, "nope") }
        }
        assertEquals(2, calls, "不应无限重试")
    }

    @Test
    fun `非 401 异常直接抛出，不刷新也不重试`() = runTest {
        var calls = 0
        assertFailsWith<ApiException> {
            managerWith().authorized { calls++; throw ApiException(500, "server") }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `刷新失败（会话已终结）返回 null，不再调用业务块`() = runTest {
        var calls = 0
        val result = managerWith(refreshOk = false).authorized {
            calls++
            throw ApiException(401, "expired")
        }
        assertNull(result)
        assertEquals(1, calls)
    }

    @Test
    fun `无会话时返回 null，业务块根本不执行`() = runTest {
        var calls = 0
        val result = managerWith(tokens = null).authorized { calls++; "never" }
        assertNull(result)
        assertEquals(0, calls)
    }
}
