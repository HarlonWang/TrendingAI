package whl.trending.ai.ui.home

/**
 * 底栏一级 tab。
 *
 * [Chat] 是入口而非落点：点它直接推全屏聊天页，底栏选中态仍留在原 tab，因此它永远不会
 * 成为 [selectedTab][HomeScreen] 的取值，也不出现在「默认首页」可选项里（见 [defaultFromName]）。
 */
enum class HomeTab {
    Trending, Picks, Chat, Me;

    companion object {
        /**
         * 解析持久化的 tab name；非法值（枚举改名、脏数据）回落 [Trending]。
         *
         * 0.23 之前底栏是 GitHub / HackerNews / ProductHunt / Picks 四项，旧值里的三个源
         * 名在此一并落到 Trending——它们现在是 Trending 的子源，语义上正是同一个落点。
         */
        fun fromNameOrDefault(name: String): HomeTab =
            entries.firstOrNull { it.name == name } ?: Trending

        /** 可作为冷启动落点的 tab：[Chat] 只是入口，选它没有「停在那一页」的语义 */
        val defaultCandidates: List<HomeTab> get() = entries.filter { it != Chat }

        /** 按「默认首页」设置解析落点：在 [fromNameOrDefault] 基础上把 [Chat] 也视为非法 */
        fun defaultFromName(name: String): HomeTab =
            fromNameOrDefault(name).takeIf { it != Chat } ?: Trending
    }
}

/** Trending tab 内的三个数据源子 tab，仅点击切换、不支持横向滑动。 */
enum class TrendingSource {
    GitHub, HackerNews, ProductHunt;

    companion object {
        /** 解析持久化的子源 name；非法值回落 [GitHub] */
        fun fromNameOrDefault(name: String): TrendingSource =
            entries.firstOrNull { it.name == name } ?: GitHub
    }
}
