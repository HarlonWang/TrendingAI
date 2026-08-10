package whl.trending.ai.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.home_title
import trendingai.shared.generated.resources.me_title
import trendingai.shared.generated.resources.picks_title

/**
 * 底栏一级 tab。
 *
 * [Chat] 是入口而非落点：点它直接推全屏聊天页，底栏选中态仍留在原 tab，因此它永远不会
 * 成为 [selectedTab][HomeScreen] 的取值，也不出现在「默认首页」可选项里（见 [defaultFromName]）。
 */
enum class HomeTab {
    Home, Picks, Chat, Me;

    companion object {
        /**
         * 解析持久化的 tab name；非法值（枚举改名、脏数据）回落 [Home]。
         *
         * 存量值一律靠这条回落收口，不写迁移代码——回落目标就是第一个 tab 本身，落点不变：
         * 0.23 之前的 "GitHub"/"HackerNews"/"ProductHunt"（现为 Home 的三个子源）、
         * 0.23 的 "Trending"（本 tab 改名前的旧名），全都落到 Home。"Picks"/"Me" 名字没变，正常匹配。
         */
        fun fromNameOrDefault(name: String): HomeTab =
            entries.firstOrNull { it.name == name } ?: Home

        /** 可作为冷启动落点的 tab：[Chat] 只是入口，选它没有「停在那一页」的语义 */
        val defaultCandidates: List<HomeTab> get() = entries.filter { it != Chat }

        /** 按「默认首页」设置解析落点：在 [fromNameOrDefault] 基础上把 [Chat] 也视为非法 */
        fun defaultFromName(name: String): HomeTab =
            fromNameOrDefault(name).takeIf { it != Chat } ?: Home
    }
}

/**
 * 底栏 tab 的展示描述：图标对（实心=选中 / 描边=未选中）+ 文案资源。
 * 形态吸收 Echo 的 `Screens` sealed class（titleId + iconIdActive/Inactive + route），
 * 但只收编「身份」，不塞内容/顶栏的 composable 引用——那两棵 when 树留在
 * HomeScreen，由编译器穷尽性检查保同步。
 */
@Immutable
internal data class HomeTabSpec(
    val tab: HomeTab,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
    val label: StringResource,
)

/** 底栏展示的 tab 清单（顺序即展示顺序）。Chat 是 FAB 入口不进胶囊，因此不在其中。 */
internal val homeTabSpecs: List<HomeTabSpec> by lazy {
    listOf(
        HomeTabSpec(HomeTab.Home, Icons.Filled.Home, Icons.Outlined.Home, Res.string.home_title),
        HomeTabSpec(HomeTab.Picks, KidStarFilled, KidStarOutlined, Res.string.picks_title),
        HomeTabSpec(HomeTab.Me, Icons.Filled.Person, Icons.Outlined.Person, Res.string.me_title),
    )
}

/** 首页内的三个数据源子 tab，仅点击切换、不支持横向滑动。 */
enum class TrendingSource {
    GitHub, HackerNews, ProductHunt;

    companion object {
        /** 解析持久化的子源 name；非法值回落 [GitHub] */
        fun fromNameOrDefault(name: String): TrendingSource =
            entries.firstOrNull { it.name == name } ?: GitHub
    }
}
