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
 * 底栏一级 tab。[Chat] 是入口而非 tab：点它推全屏聊天页、底栏选中态留在原 tab，
 * 永不成为 selectedTab 取值。它能当「默认首页」选，语义是冷启动把聊天页压在 [Home] 之上。
 */
enum class HomeTab {
    Home, Picks, Chat, Me;

    /** 本 tab 在埋点里的页面身份。[Chat] 推出去的聊天页作为路由自己会报，给 null 免得报两条。 */
    val screen: Screen?
        get() = when (this) {
            Home -> Screen.HOME
            Picks -> Screen.PICKS
            Me -> Screen.ME
            Chat -> null
        }

    companion object {
        /** 解析持久化的 tab name；非法值与存量旧值回落 [Home]，不写迁移代码。 */
        fun fromNameOrDefault(name: String): HomeTab =
            entries.firstOrNull { it.name == name } ?: Home

        /** 「默认首页」可选项，含 [Chat]（启动落点是聊天页，底下垫 [Home]） */
        val defaultCandidates: List<HomeTab> get() = entries

        /** 按「默认首页」设置解析底栏选中 tab：[Chat] 不是 tab，垫底的是 [Home] */
        fun defaultFromName(name: String): HomeTab =
            fromNameOrDefault(name).takeIf { it != Chat } ?: Home

        /** 「默认首页」设置是否要求冷启动直接进聊天页 */
        fun launchesChat(name: String): Boolean = fromNameOrDefault(name) == Chat
    }
}

/**
 * 底栏 tab 的展示描述：图标对（实心=选中 / 描边=未选中）+ 文案资源。形态参照 Echo 的
 * `Screens` sealed class，但只收编「身份」——内容/顶栏的 when 树留在 HomeScreen 由穷尽性检查保同步。
 */
@Immutable
internal data class HomeTabSpec(
    val tab: HomeTab,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
    val label: StringResource,
)

/**
 * 底栏展示的 tab 清单（顺序即展示顺序）。Chat 是 FAB 入口不进胶囊。
 * lazy 不是装饰：普通 val 会在文件 facade 初始化时连带构建 ImageVector，而只用枚举的单测不该碰 UI 资源。
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
