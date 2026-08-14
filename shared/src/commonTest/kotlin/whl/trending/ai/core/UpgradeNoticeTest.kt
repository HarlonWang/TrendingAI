package whl.trending.ai.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 「账号系统已升级」提示的触发判定（C 方案，见 loginbase docs/plan.md 第 4 步）。
 * 三个条件缺一不可：有登录痕迹、当前未登录、没展示过。
 */
class UpgradeNoticeTest {

    @Test
    fun `有痕迹且未登录且没展示过——唯一该显示的组合`() {
        assertTrue(UpgradeNotice.decide(shown = false, loggedIn = false, hasTrace = true))
    }

    @Test
    fun `展示过就不再出现`() {
        assertFalse(UpgradeNotice.decide(shown = true, loggedIn = false, hasTrace = true))
    }

    @Test
    fun `已登录不提示——重登过的人不需要看见它`() {
        assertFalse(UpgradeNotice.decide(shown = false, loggedIn = true, hasTrace = true))
    }

    @Test
    fun `无痕迹不提示——纯匿名用户与新装机不该被打扰`() {
        assertFalse(UpgradeNotice.decide(shown = false, loggedIn = false, hasTrace = false))
    }
}
