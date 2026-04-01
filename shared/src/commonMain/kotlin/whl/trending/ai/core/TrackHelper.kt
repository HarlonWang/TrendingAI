package whl.trending.ai.core

import whl.trending.ai.core.platform.trackEvent

/**
 * 统一的条目点击埋点，上报 "item_click" 事件。
 * @param source 数据来源：github / hackernews / producthunt
 * @param rank 条目在列表中的排名（从 1 开始）
 * @param title 条目标题，截断至 100 字符
 * @param section 仅 Picks 页使用：deep_dive / controversy / speed_read
 */
fun trackItemClick(
    source: String,
    rank: Int,
    title: String,
    section: String? = null
) {
    val props = mutableMapOf<String, Any>(
        "source" to source,
        "rank" to rank,
        "title" to title.take(100)
    )
    section?.let { props["section"] = it }
    trackEvent("item_click", props)
}
