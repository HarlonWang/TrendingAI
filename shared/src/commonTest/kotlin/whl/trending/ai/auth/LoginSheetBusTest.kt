package whl.trending.ai.auth

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 登录面板总线的状态机。
 *
 * 它承担的是「面板不在时，GitHub 授权结果去哪」——授权要跳出 App，回来时面板可能已经
 * 不存在（进程被回收 → 冷启动）。结果由常驻的 OAuthOutcomeHost 写到这里，面板读。
 *
 * 最要紧的一条是**新请求必须从干净状态开始**：上一轮遗留的失败若留着，用户下次打开
 * 登录面板的瞬间就会顶着一条「GitHub 登录失败」的红字，而他可能只是想用邮箱登录。
 */
class LoginSheetBusTest {

    // 进程级单例，用例之间会串状态
    @BeforeTest
    fun setUp() = LoginSheetBus.clear()

    @AfterTest
    fun tearDown() = LoginSheetBus.clear()

    @Test
    fun `同一面板内再次发起授权前结果先归零——连续两次取消第二次也观察得到`() {
        LoginSheetBus.request("account_hub")
        LoginSheetBus.reportGithubResult(GithubAuthResult.CANCELED)

        LoginSheetBus.beginGithubAttempt()
        assertNull(LoginSheetBus.githubResult.value, "新一次授权开始时不该还挂着上一次的结果")

        LoginSheetBus.reportGithubResult(GithubAuthResult.CANCELED)
        assertEquals(GithubAuthResult.CANCELED, LoginSheetBus.githubResult.value)
    }

    @Test
    fun `新的登录请求清掉上一轮遗留的失败`() {
        LoginSheetBus.reportGithubResult(GithubAuthResult.FAILED)

        LoginSheetBus.request("home_avatar")

        assertEquals("home_avatar", LoginSheetBus.request.value)
        assertNull(LoginSheetBus.githubResult.value, "面板不该一打开就顶着上一轮的错误")
    }

    @Test
    fun `面板关闭时请求与结果一并清空`() {
        LoginSheetBus.request("home_avatar")
        LoginSheetBus.reportGithubResult(GithubAuthResult.CANCELED)

        LoginSheetBus.clear()

        assertNull(LoginSheetBus.request.value)
        assertNull(LoginSheetBus.githubResult.value)
    }

    @Test
    fun `授权结果能送达面板——失败与取消是两种状态`() {
        LoginSheetBus.request("home_avatar")

        LoginSheetBus.reportGithubResult(GithubAuthResult.FAILED)
        assertEquals(GithubAuthResult.FAILED, LoginSheetBus.githubResult.value)

        LoginSheetBus.reportGithubResult(GithubAuthResult.CANCELED)
        assertEquals(GithubAuthResult.CANCELED, LoginSheetBus.githubResult.value)
    }

    @Test
    fun `面板不在时也能收结果——冷启动路径不会丢`() {
        // request 为 null＝面板没组合（进程被回收后的冷启动就是这个状态）
        assertNull(LoginSheetBus.request.value)

        LoginSheetBus.reportGithubResult(GithubAuthResult.FAILED)

        assertEquals(GithubAuthResult.FAILED, LoginSheetBus.githubResult.value)
    }
}
