package whl.trending.ai.auth

import wang.harlon.loginbase.OAuthOutcome
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OAuth 回跳结果的重复消费闸。
 *
 * 治的是：从自定义标签页回跳会重建 Activity，`OAuthOutcomeHost` 新旧两个 composition
 * 短暂并存、同时订阅 `replay = 1` 的 `oauthResults`，同一次投递被消费两次。1.4.0 首日
 * `auth_finished(canceled)` 全是成对的，登录完成率算出 200%。
 *
 * 两条用例是一对权衡：挡住重复投递，但**不能**挡住用户真的取消两次——生产数据里
 * 同一条 flow 内就有两次真实取消（23:33:11 与 23:33:16），按 flow 去重会把第二次吞掉。
 */
class OAuthResultGuardTest {

    // 进程级单例，用例之间会串状态
    @BeforeTest
    fun setUp() = OAuthResultGuard.reset()

    @AfterTest
    fun tearDown() = OAuthResultGuard.reset()

    @Test
    fun `同一次投递只处理一次——重建期间的第二个订阅者被挡掉`() {
        assertTrue(OAuthResultGuard.shouldHandle(OAuthOutcome.Cancelled))
        assertFalse(
            OAuthResultGuard.shouldHandle(OAuthOutcome.Cancelled),
            "同一次投递被消费两次：埋点成对，consumePendingSource() 这类一次性读取也会跑两遍",
        )
    }

    @Test
    fun `重新发起授权后，同一种结果仍要处理——用户可以真的取消两次`() {
        assertTrue(OAuthResultGuard.shouldHandle(OAuthOutcome.Cancelled))

        // launchGithubSignIn / launchGithubLink 在发起时会做这件事
        OAuthResultGuard.reset()

        assertTrue(
            OAuthResultGuard.shouldHandle(OAuthOutcome.Cancelled),
            "Cancelled 是 object 单例，跨轮次的引用相等说明不了什么——重置后必须重新放行",
        )
    }

    @Test
    fun `不同结果各自放行`() {
        assertTrue(OAuthResultGuard.shouldHandle(OAuthOutcome.Cancelled))
        assertTrue(OAuthResultGuard.shouldHandle(OAuthOutcome.Failed("github_in_use")))
    }
}
