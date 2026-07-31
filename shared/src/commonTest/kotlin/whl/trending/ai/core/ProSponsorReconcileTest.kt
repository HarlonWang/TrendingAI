package whl.trending.ai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import whl.trending.ai.data.model.ProRefreshResponse

/**
 * 赞助对账判定。这是 2026-07-29 首位赞助者付完钱被当免费用户拦 48 分钟那条链路的收口：
 * 当时后端只回裸 `pro:false`，「没赞助」与「赞助了但没关联 GitHub」完全同形，客户端只能沉默。
 *
 * 三条分支的边界必须锁死——把 GUIDE_LINK 误判成 STAY_SILENT 就是退回事故当天的行为，
 * 把 STAY_SILENT 误判成 GUIDE_LINK 则会对着没赞助的人弹「感谢赞助」。
 */
class ProSponsorReconcileTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `已是 Pro 就结束对账窗口`() {
        assertEquals(
            ReconcileAction.MARK_PRO,
            ProSponsor.reconcileAction(ProRefreshResponse(pro = true)),
        )
    }

    @Test
    fun `钱付了但没关联 GitHub 要弹引导`() {
        assertEquals(
            ReconcileAction.GUIDE_LINK,
            ProSponsor.reconcileAction(
                ProRefreshResponse(pro = false, reason = "github_not_linked"),
            ),
        )
    }

    @Test
    fun `查证确实没赞助时保持沉默`() {
        assertEquals(
            ReconcileAction.STAY_SILENT,
            ProSponsor.reconcileAction(ProRefreshResponse(pro = false, reason = "not_sponsor")),
        )
    }

    @Test
    fun `查询失败时不得断言用户没赞助`() {
        assertEquals(
            ReconcileAction.STAY_SILENT,
            ProSponsor.reconcileAction(ProRefreshResponse(pro = false, reason = "lookup_failed")),
        )
    }

    @Test
    fun `请求本身失败时保持沉默`() {
        assertEquals(ReconcileAction.STAY_SILENT, ProSponsor.reconcileAction(null))
    }

    @Test
    fun `老后端不带 reason 时退化为纯布尔语义`() {
        // 客户端可能先于后端发版；缺 reason 不能崩、也不能误触发引导
        val old = json.decodeFromString<ProRefreshResponse>("""{"pro":false}""")
        assertNull(old.reason)
        assertEquals(ReconcileAction.STAY_SILENT, ProSponsor.reconcileAction(old))
    }

    @Test
    fun `reason 能从后端 JSON 正确解析`() {
        val parsed = json.decodeFromString<ProRefreshResponse>(
            """{"pro":false,"reason":"github_not_linked"}""",
        )
        assertEquals(ProRefreshResponse.REASON_GITHUB_NOT_LINKED, parsed.reason)
        assertEquals(ReconcileAction.GUIDE_LINK, ProSponsor.reconcileAction(parsed))
    }

    @Test
    fun `pro 为真时即使带 reason 也以 pro 为准`() {
        // 不该出现的组合，但真出现时「已是 Pro」必须压过引导，否则会对付费用户弹关联提示
        assertEquals(
            ReconcileAction.MARK_PRO,
            ProSponsor.reconcileAction(ProRefreshResponse(pro = true, reason = "github_not_linked")),
        )
    }
}
