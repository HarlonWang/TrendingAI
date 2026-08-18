package whl.trending.ai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

/**
 * Paddle 购买回流对账的轮询判定。
 *
 * 为什么必须重试而不是查一次：权益要等 Paddle 的 webhook 落库，而用户完全可能比 webhook
 * 先回到 App。查一次就放弃，会把「已经付过款」的人显示成免费档——正是最伤人的一刻。
 *
 * 反过来也不能无限查：对账挂在 ON_RESUME 上，退避序列就是这条链路的成本上限。
 */
class ProCheckoutReconcileTest {

    @Test
    fun `首次就到账时不再等待，返回第 1 次`() = runTest {
        var calls = 0
        val attempt = ProCheckout.reconcile { calls++; true }
        assertEquals(1, attempt)
        assertEquals(1, calls)
        // 首次查询前不该有任何等待：webhook 多数情况早已落库，让秒到的用户白等是没必要的
        assertEquals(0L, currentTime)
    }

    @Test
    fun `中途到账即停，不再继续轮询`() = runTest {
        var calls = 0
        val attempt = ProCheckout.reconcile { calls++; calls == 3 }
        assertEquals(3, attempt)
        assertEquals(3, calls)
    }

    @Test
    fun `始终不到账时用尽 4 次并返回 null——窗口留给下次回前台`() = runTest {
        var calls = 0
        val attempt = ProCheckout.reconcile { calls++; false }
        assertNull(attempt)
        assertEquals(4, calls)
    }

    @Test
    fun `整轮等待不超过半分钟——对账挂在 onResume 上，不能让用户干等`() = runTest {
        ProCheckout.reconcile { false }
        assertTrue(currentTime <= 30_000L, "整轮耗时 ${currentTime}ms 超出预期")
    }

    @Test
    fun `单次查询抛异常不中断整轮，后面仍有机会拿到权益`() = runTest {
        var calls = 0
        val attempt = ProCheckout.reconcile {
            calls++
            // 网络抖动 / token 刚过期：这一次失败不代表没付款
            if (calls < 3) throw IllegalStateException("boom") else true
        }
        assertEquals(3, attempt)
    }

    @Test
    fun `全程抛异常等价于没到账，不误判为已开通`() = runTest {
        val attempt = ProCheckout.reconcile { throw IllegalStateException("boom") }
        assertNull(attempt)
    }
}
