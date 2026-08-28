package whl.trending.ai.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.globalAuthManager

private val authorizedClients = MutableStateFlow<List<HttpClient>>(emptyList())

/**
 * 给自家 API 的 client 装鉴权：带 token、认 401、刷新、重试一次全在这一处，业务代码因此
 * 不再出现 token。接法出自 loginbase-kt README「接入指南」第 2 步（可执行版本是该库的
 * `ReadmeIntegrationTest`）。
 *
 * 刷新必须走 [AuthManager.refreshAccessToken]——ktor 插件的单飞只覆盖单个 client，自己去
 * POST `/auth/refresh` 会并发刷新、烧掉服务端 1h/3 次的救活配额，且全程功能正常没有报错。
 *
 * 匿名可用的端点不必特殊处理：无会话时 `loadTokens` 返回 null，请求照发、不带头。
 */
fun HttpClientConfig<*>.installTrendingAuth(
    authManager: () -> AuthManager = { globalAuthManager },
) {
    install(Auth) {
        bearer {
            // refresh token 归 loginbase 管，插件不需要知道，传 null 即可
            loadTokens { authManager().getAccessToken()?.let { BearerTokens(it, null) } }
            refreshTokens { authManager().refreshAccessToken()?.let { BearerTokens(it, null) } }
        }
    }
}

/** 装了 [installTrendingAuth] 的 client 登记进来，供 [clearAuthTokenCache] 统一清缓存。 */
fun HttpClient.trackAuthTokenCache(): HttpClient = also { client ->
    authorizedClients.update { it + client }
}

/**
 * 清掉插件缓存的 token。**登出与会话终结时必须调**：holder 里存的是上一条会话的 token，
 * 不清会继续拿它发请求。反向不需要——holder 只缓存非 null 值（`AuthTokenHolder.loadToken`
 * 的 hot path 判的是 `value != null`），匿名期间每次都会重新取，登录后第一个请求就带得上。
 */
fun clearAuthTokenCache() {
    authorizedClients.value.forEach { it.authProvider<BearerAuthProvider>()?.clearToken() }
}
