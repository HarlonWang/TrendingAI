package whl.trending.ai.ui.profile

import kotlinx.serialization.Serializable
import whl.trending.ai.data.model.ContributionCalendar
import whl.trending.ai.data.remote.GithubUser

/**
 * GitHub 子页「上次数据缓存」快照：GitHub 档案 + 贡献热力图 + feed 首屏。
 * key 固定为 [KEY]，登出时整体 remove；[login] 校验归属，不匹配的快照不复用。
 */
@Serializable
data class GithubProfileCache(
    val login: String,
    val githubUser: GithubUser? = null,
    val contributions: ContributionCalendar? = null,
    val feedItems: List<GithubFeedItem> = emptyList(),
    /** 缓存的 feed 属于哪个档（精选/全部），读取时档位不一致则不复用 feed */
    val highlightsOnly: Boolean = true,
) {
    companion object {
        const val KEY = "github_profile"

        /** feed 只缓存首屏所需条数，控制文件体积 */
        const val MAX_FEED_ITEMS = 50

        /**
         * 从当前 state 组装快照。写入语义是「覆盖 + 只增不减」：页面渐进加载，
         * 刷新中途 contributions/feed 会被清空重拉，此刻落盘若纯覆盖会把完整旧缓存
         * 冲成残缺快照——因此残缺字段用旧快照补齐后再覆盖。
         * feed 与档位绑定，仅档位一致时复用旧值；contributions/githubUser 档位无关。
         */
        fun from(state: GithubProfileUiState, previous: GithubProfileCache?): GithubProfileCache? {
            val login = state.login ?: return null
            val prev = previous?.takeIf { it.login == login }
            val prevFeed = prev?.takeIf { it.highlightsOnly == state.highlightsOnly }?.feedItems.orEmpty()
            return GithubProfileCache(
                login = login,
                githubUser = state.githubUser ?: prev?.githubUser,
                contributions = state.contributions ?: prev?.contributions,
                feedItems = state.feedItems.ifEmpty { prevFeed }.take(MAX_FEED_ITEMS),
                highlightsOnly = state.highlightsOnly,
            )
        }
    }
}
