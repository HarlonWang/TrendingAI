package whl.trending.chat.engine.byok

import whl.trending.ai.chat.ChatContext

/** 构建 BYOK 直连请求的 system 提示：语言要求 + 可选条目上下文。 */
object ByokPrompt {

    fun system(context: ChatContext?, lang: String): String {
        val sb = StringBuilder()
        sb.append(
            if (lang == "zh") {
                "你是 TrendingAI 的 AI 助手，帮助用户了解海外技术项目与资讯。请始终用简体中文回复。"
            } else {
                "You are TrendingAI's assistant, helping users explore global tech projects and news. Always reply in English."
            },
        )
        if (context != null) {
            sb.append("\n\n")
            sb.append(if (lang == "zh") "当前讨论的条目：" else "The current item under discussion:")
            sb.append("\n- ").append(if (lang == "zh") "标题：" else "Title: ").append(context.title)
            context.summary?.takeIf { it.isNotBlank() }?.let {
                sb.append("\n- ").append(if (lang == "zh") "摘要：" else "Summary: ").append(it)
            }
            context.sourceUrl?.takeIf { it.isNotBlank() }?.let {
                sb.append("\n- ").append(if (lang == "zh") "来源：" else "Source: ").append(it)
            }
        }
        return sb.toString()
    }
}
