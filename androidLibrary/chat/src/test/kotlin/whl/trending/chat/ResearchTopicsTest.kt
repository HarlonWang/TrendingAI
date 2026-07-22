package whl.trending.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import whl.trending.ai.chat.ChatContext

/** research 主题的条目锚点拼装（纯函数）。 */
class ResearchTopicsTest {

    private val repoContext = ChatContext(
        title = "octo/demo",
        sourceUrl = "https://github.com/octo/demo",
        source = "github",
        externalId = "octo/demo",
    )

    @Test
    fun `无 context → 原文透传`() {
        assertEquals("调研一下 KMP 生态", ResearchTopics.compose("调研一下 KMP 生态", null))
    }

    @Test
    fun `条目会话 → 尾部附标题与链接锚点`() {
        val composed = ResearchTopics.compose("这个项目值得投入吗", repoContext)
        assertTrue(composed.startsWith("这个项目值得投入吗"))
        assertTrue("octo/demo" in composed)
        assertTrue("https://github.com/octo/demo" in composed)
    }

    @Test
    fun `context 无标题无链接 → 原文透传`() {
        assertEquals("hi", ResearchTopics.compose("hi", ChatContext(title = "")))
    }

    @Test
    fun `附加后超服务端上限 → 回退原文，不截用户内容`() {
        val longText = "调".repeat(ResearchTopics.MAX_TOPIC_LEN - 10)
        assertEquals(longText, ResearchTopics.compose(longText, repoContext))
    }

    @Test
    fun `拼装结果不超服务端上限`() {
        val text = "调".repeat(400)
        assertTrue(ResearchTopics.compose(text, repoContext).length <= ResearchTopics.MAX_TOPIC_LEN)
    }
}
