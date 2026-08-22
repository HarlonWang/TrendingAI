package whl.trending.ai.core.platform

import whl.trending.ai.update.isVersionBlocked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Android 侧 `getAppVersion()` 的**兜底分支**本身——commonTest 里那条
 * `app_version_fallback_never_blocks` 只锁了常量与 `isVersionBlocked` 的关系，
 * 绕过常量、在平台实现里直接写回 `"1.0.0"` 它是发现不了的（PR #108 审查指出）。
 *
 * 这条走的是真实现：host test 里 `AndroidContextHolder` 从未 `initialize`，
 * `getAppVersion()` 因此落到 `context == null` 分支——**正是 1.4.0 首日全部事件
 * 走的那条**（埋点配置在 `Application.onCreate` 读版本号，而 holder 当时还没设）。
 * 不触碰任何 Android framework API，所以在 JVM 上跑得起来。
 *
 * iOS 侧不设对应用例：它读 `NSBundle.mainBundle.infoDictionary`，测试 bundle 里
 * 有没有 `CFBundleShortVersionString` 不由我们决定，断言兜底会变成断言测试环境。
 */
class AndroidAppVersionFallbackTest {

    @Test
    fun `context 未就绪时兜底为哨兵值，而不是一个像样的版本号`() {
        assertEquals(
            UNKNOWN_APP_VERSION,
            getAppVersion(),
            "兜底值必须一眼可辨。伪装成真实版本号（曾经是 1.0.0）会让上报的版本切片静默失真",
        )
    }

    @Test
    fun `兜底值不会把用户锁死在强更页`() {
        assertFalse(
            isVersionBlocked(current = getAppVersion(), minVersion = "99.0.0"),
            "isVersionBlocked 的承诺是「解析失败即不拦截」，兜底值必须真的解析不了",
        )
    }
}
