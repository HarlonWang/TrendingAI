package whl.trending.ai.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import whl.trending.ai.auth.AuthManager
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.NoopAuthManager
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.ProPaywall
import whl.trending.chat.host.PaywallSource
import whl.trending.chat.host.chatHost
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrendingChatHostTest {
    private class FakeAuthManager(state: AuthState) : AuthManager {
        override val isSupported: Boolean = true
        override val authState: StateFlow<AuthState> = MutableStateFlow(state)
        override fun signIn(source: String) {}
        override fun signOut() {}
        override suspend fun getAccessToken(): String? = null
    }

    @AfterTest
    fun tearDown() {
        globalAuthManager = NoopAuthManager
    }

    /** 宿主在登录实现注入之前就装好（两端的真实启动顺序），登录态仍须跟随之后注入的实现 */
    @Test
    fun loginFlowFollowsAuthManagerInjectedAfterInstall() = runTest {
        globalAuthManager = NoopAuthManager
        installTrendingChatHost()
        assertFalse(chatHost.isLoggedIn.first())

        globalAuthManager = FakeAuthManager(AuthState.LoggedIn)
        assertTrue(chatHost.isLoggedIn.first())
        assertTrue(chatHost.isLoggedInNow())
    }

    /** chat 库的 Pro 触点全部经宿主转发到统一入口，导航由根部收集 ProPaywall.requests 完成 */
    @Test
    fun openPaywallForwardsToProPaywall() = runTest {
        installTrendingChatHost()
        // 总线无 replay，收集者必须先于 open 挂上（App 里由常驻的 LaunchedEffect 保证）
        val received = async(start = CoroutineStart.UNDISPATCHED) { ProPaywall.requests.first() }
        chatHost.openPaywall(PaywallSource.QUOTA_CARD)
        received.await()
    }
}
