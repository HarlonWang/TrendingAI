package whl.trending.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role

/** 解读卡尾部「深度调研此项目」升级入口的可见性策略（纯函数）。 */
class ResearchUpsellPolicyTest {

    private val summaryUser = ChatMessage(1, Role.USER, "一键详细解读", kind = MessageKind.DETAIL_SUMMARY)
    private val summaryOk = ChatMessage(2, Role.ASSISTANT, "解读全文", kind = MessageKind.DETAIL_SUMMARY)

    @Test
    fun `成功解读消息 → 挂在该消息上`() {
        assertEquals(2L, ResearchUpsellPolicy.upsellMessageId(listOf(summaryUser, summaryOk)))
    }

    @Test
    fun `无解读消息 → 不显示`() {
        val chat = listOf(ChatMessage(1, Role.USER, "hi"), ChatMessage(2, Role.ASSISTANT, "hello"))
        assertNull(ResearchUpsellPolicy.upsellMessageId(chat))
    }

    @Test
    fun `解读失败或未出字 → 不显示`() {
        val failed = ChatMessage(
            2, Role.ASSISTANT, "",
            error = ChatError(ChatErrorCategory.SERVER),
            kind = MessageKind.DETAIL_SUMMARY,
        )
        assertNull(ResearchUpsellPolicy.upsellMessageId(listOf(summaryUser, failed)))
        val blank = ChatMessage(3, Role.ASSISTANT, "", kind = MessageKind.DETAIL_SUMMARY)
        assertNull(ResearchUpsellPolicy.upsellMessageId(listOf(summaryUser, blank)))
    }

    @Test
    fun `会话已有任何 research 消息（含在途与失败）→ 隐藏，防重复扣费`() {
        val researchUser = ChatMessage(3, Role.USER, "深度调研此项目", kind = MessageKind.DEEP_RESEARCH)
        assertNull(ResearchUpsellPolicy.upsellMessageId(listOf(summaryUser, summaryOk, researchUser)))
    }

    @Test
    fun `多条成功解读 → 挂在最后一条上`() {
        val later = ChatMessage(5, Role.ASSISTANT, "新解读", kind = MessageKind.DETAIL_SUMMARY)
        assertEquals(5L, ResearchUpsellPolicy.upsellMessageId(listOf(summaryUser, summaryOk, later)))
    }
}
