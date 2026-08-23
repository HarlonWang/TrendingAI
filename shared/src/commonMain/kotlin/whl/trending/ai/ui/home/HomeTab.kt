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
import whl.trending.ai.core.analytics.Screen

/**
 * 底栏一级 tab。
 *
 * [Chat] 是入口而非落点：点它直接推全屏聊天页，底栏选中态仍留在原 tab，因此它永远不会
 * 成为 [selectedTab][HomeScreen] 的取值，也不出现在「默认首页」可选项里（见 [defaultFromName]）。
 */
enum class HomeTab {
    Home, Picks, Chat, Me;

    /**
     * 本 tab 在埋点里的页面身份。首页三个 tab 才是页面——[Chat] 是入口，
     * 它推出去的全屏聊天页作为路由自己会报（`Screen.CHAT`），这里给 null 免得报两条。
     */
    val screen: Screen?
        get() = when (this) {
            Home -> Screen.HOME
            Picks -> Screen.PICKS
            Me -> Screen.ME
            Chat -> null
        }

    companion object {
        /**
         * 解析持久化的 tab name；非法值（枚举改名、脏数据）回落 [Home]。
         * 存量旧值（历史版本的 tab 名）对应的落点恰好都是 Home，靠这条回落即可收口，不写迁移代码。
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

/**
 * 底栏展示的 tab 清单（顺序即展示顺序）。Chat 是 FAB 入口不进胶囊，因此不在其中。
 *
 * lazy 不是装饰：本属性与 [HomeTab] 同文件，普通 val 会在文件 facade 初始化时连带
 * 构建三对 ImageVector（含 KidStar 的 path 解析），而 HomeViewModel 及其 host 单测
 * 只用枚举、不碰 UI 资源——lazy 把这层隔开。
 */
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
