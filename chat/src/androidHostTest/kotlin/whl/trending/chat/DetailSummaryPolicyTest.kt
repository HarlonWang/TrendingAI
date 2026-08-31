package whl.trending.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import whl.trending.chat.ChatContext
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageKind
import whl.trending.chat.model.Role

/** 「一键详细解读」chip 可见性策略（纯函数）。 */
class DetailSummaryPolicyTest {

    private val githubContext = ChatContext(
        title = "octo/demo",
        sourceUrl = "https://github.com/octo/demo",
        source = "github",
        externalId = "octo/demo",
        readmeLength = 5000,
    )

    @Test
    fun `GitHub 条目且 README 达标 → 显示`() {
        assertTrue(DetailSummaryPolicy.chipVisible(githubContext, emptyList()))
    }

    @Test
    fun `通用助手入口（context 为空）→ 不显示`() {
        assertFalse(DetailSummaryPolicy.chipVisible(null, emptyList()))
    }

    @Test
    fun `非 GitHub 源 → 不显示`() {
        assertFalse(DetailSummaryPolicy.chipVisible(githubContext.copy(source = "hackernews"), emptyList()))
        assertFalse(DetailSummaryPolicy.chipVisible(githubContext.copy(source = null), emptyList()))
    }

    @Test
    fun `readmeLength 为 null（未加载完）→ 不显示，宁缺勿滥`() {
        assertFalse(DetailSummaryPolicy.chipVisible(githubContext.copy(readmeLength = null), emptyList()))
    }

    @Test
    fun `readmeLength 低于阈值 → 不显示`() {
        assertFalse(
            DetailSummaryPolicy.chipVisible(
                githubContext.copy(readmeLength = DetailSummaryPolicy.MIN_README_CHARS - 1),
                emptyList(),
            ),
        )
    }

    @Test
    fun `已有成功解读消息 → 隐藏`() {
        val success = ChatMessage(2, Role.ASSISTANT, "解读全文", kind = MessageKind.DETAIL_SUMMARY)
        assertFalse(DetailSummaryPolicy.chipVisible(githubContext, listOf(success)))
    }

    @Test
    fun `解读失败消息不算成功 → 仍显示（可重新触发）`() {
        val failed = ChatMessage(
            2, Role.ASSISTANT, "",
            error = ChatError(ChatErrorCategory.SERVER),
            kind = MessageKind.DETAIL_SUMMARY,
        )
        assertTrue(DetailSummaryPolicy.chipVisible(githubContext, listOf(failed)))
    }

    @Test
    fun `已有普通对话轮次 → 常驻显示（不同于介绍 chip 的 messages 为空规则）`() {
        val chat = listOf(
            ChatMessage(1, Role.USER, "hi"),
            ChatMessage(2, Role.ASSISTANT, "hello"),
        )
        assertTrue(DetailSummaryPolicy.chipVisible(githubContext, chat))
    }
}
