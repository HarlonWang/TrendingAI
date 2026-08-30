package whl.trending.chat

/**
 * 进入聊天时可携带的初始上下文（项目 / HN / PH 条目）。
 * 通用 AI 助手入口传 null。
 *
 * @param source 条目所属数据源（`github` 等，与服务端 contents 表口径一致），驱动「一键详细解读」入口
 * @param externalId 条目在数据源内的 ID（GitHub 为 `owner/repo`），detail-summary API 入参
 * @param readmeLength README 正文长度估计（宿主详情页进 chat 时填充；未加载完为 null → chip 不显示）
 * @param autoDetailSummary 进入 chat 后是否自动触发「一键详细解读」（详情页「一键解读」入口置 true）
 */
data class ChatContext(
    val title: String,
    val summary: String? = null,
    val sourceUrl: String? = null,
    val source: String? = null,
    val externalId: String? = null,
    val readmeLength: Int? = null,
    val autoDetailSummary: Boolean = false,
)
