package whl.trending.ai.core.analytics

import wang.harlon.eventbase.Event

/**
 * 事件词汇 v1。**唯一权威是 eventbase 仓的 `docs/telemetry-design.md` §12.9**——
 * 要加事件先改那张表，禁止在调用点就地发明。
 *
 * `app_opened` / `app_backgrounded` 不在这里：由 eventbase-kt 按自己的会话口径自动上报。
 *
 * 枚举值经库的 props 归一化输出为小写名（`DETAIL_SUMMARY` → `detail_summary`），
 * 值域因此也有编译期保护，不只是键名。null 属性在归一化时丢弃，无需调用点判空。
 */
sealed class AppEvent(
    override val name: String,
    override val props: Map<String, Any?> = emptyMap(),
) : Event {

    data class NotificationOpened(val kind: NotificationKind) :
        AppEvent("notification_opened", mapOf("kind" to kind))

    /** 通知**送达侧**（`notification_opened` 是打开侧）：shown 是打开率的分母。 */
    data class NotificationDelivery(
        val step: NotificationStep,
        val kind: NotificationKind,
        val reason: String? = null,
        val date: String? = null,
        val trigger: String? = null,
        val attempt: Int? = null,
        val delayMin: Int? = null,
        val overdueMin: Int? = null,
    ) : AppEvent(
        "notification_delivery",
        mapOf(
            "step" to step, "kind" to kind, "reason" to reason, "date" to date,
            "trigger" to trigger, "attempt" to attempt,
            "delay_min" to delayMin, "overdue_min" to overdueMin,
        ),
    )

    /**
     * **不要手写调用**——由导航层自动产生，两个源见 [whl.trending.ai.core.analytics.trackScreenViews]
     * 与首页 tab。唯一的例外是 [Screen.LOGIN]（浮层不进 backStack，见 LoginSheetHost）。
     *
     * [from] 是上一个页面，自动填，值域与 [screen] 相同。
     */
    data class ScreenViewed(val screen: Screen, val from: Screen? = null) :
        AppEvent("screen_viewed", mapOf("screen" to screen, "from" to from))

    data class TabSwitched(val tab: String, val method: TabSwitchMethod = TabSwitchMethod.TAP) :
        AppEvent("tab_switched", mapOf("tab" to tab, "method" to method))

    /**
     * [title] 截断 60 字符：拆库后埋点库 JOIN 不到业务库的 contents 表，只留 id 的话
     * 读数就是一串看不懂的字符串（词汇表里显式接受的代价）。
     */
    data class ContentOpened(
        val source: String,
        val contentId: String,
        val rank: Int,
        val title: String,
        val section: String? = null,
    ) : AppEvent(
        "content_opened",
        mapOf(
            "source" to source, "content_id" to contentId, "rank" to rank,
            "title" to title.truncateCodePoints(60), "section" to section,
        ),
    )

    data class ContentAction(
        val action: ContentActionKind,
        val source: String? = null,
        val contentId: String? = null,
        val from: String? = null,
        val hasSummary: Boolean? = null,
    ) : AppEvent(
        "content_action",
        mapOf(
            "action" to action, "source" to source, "content_id" to contentId,
            "from" to from, "has_summary" to hasSummary,
        ),
    )

    /** 一次筛选改了几个维度就发几条：把维度拼进 value 会让按维度分组彻底做不了。 */
    data class ListFiltered(val filter: ListFilter, val value: String) :
        AppEvent("list_filtered", mapOf("filter" to filter, "value" to value))

    data class AiRequested(
        val kind: AiKind,
        val from: String,
        val imageCount: Int? = null,
        val hasContext: Boolean? = null,
    ) : AppEvent(
        "ai_requested",
        mapOf("kind" to kind, "from" to from, "image_count" to imageCount, "has_context" to hasContext),
    )

    /** 与 [AiRequested] 成对，一次请求恰好一条：漏斗的分母分子都在这两个事件里。 */
    data class AiCompleted(
        val kind: AiKind,
        val outcome: AiOutcome,
        val durationMs: Long? = null,
        val reason: String? = null,
        val tier: String? = null,
    ) : AppEvent(
        "ai_completed",
        mapOf(
            "kind" to kind, "outcome" to outcome, "duration_ms" to durationMs,
            "reason" to reason, "tier" to tier,
        ),
    )

    data class AuthStarted(val action: AuthAction, val method: String, val source: String) :
        AppEvent("auth_started", mapOf("action" to action, "method" to method, "source" to source))

    data class AuthFinished(
        val action: AuthAction,
        val outcome: AuthOutcome,
        val method: String,
        val source: String? = null,
        val reason: String? = null,
        val isNew: Boolean? = null,
    ) : AppEvent(
        "auth_finished",
        mapOf(
            "action" to action, "outcome" to outcome, "method" to method,
            "source" to source, "reason" to reason, "is_new" to isNew,
        ),
    )

    data object SignedOut : AppEvent("signed_out")

    data class UpsellClicked(val source: String, val target: UpsellTarget) :
        AppEvent("upsell_clicked", mapOf("source" to source, "target" to target))

    data class CheckoutStep(
        val step: CheckoutStepKind,
        val plan: String? = null,
        val source: String? = null,
        val attempt: Int? = null,
    ) : AppEvent(
        "checkout_step",
        mapOf("step" to step, "plan" to plan, "source" to source, "attempt" to attempt),
    )

    data class SubscriptionAction(val action: SubscriptionActionKind, val outcome: ActionOutcome) :
        AppEvent("subscription_action", mapOf("action" to action, "outcome" to outcome))

    data class NewsletterAction(
        val action: NewsletterActionKind,
        val result: ActionOutcome? = null,
        val lang: String? = null,
        val status: String? = null,
    ) : AppEvent(
        "newsletter_action",
        mapOf("action" to action, "result" to result, "lang" to lang, "status" to status),
    )

    data class SettingChanged(val key: SettingKey, val value: String) :
        AppEvent("setting_changed", mapOf("key" to key, "value" to value))

    /**
     * 设置项被点开、但落点不是页面（外链 / 静默动作 / 弹窗），因此不该记成 [ScreenViewed]。
     * 与 [SettingChanged] 共用 [SettingKey]：同一个 key 的「点开 → 改值」就是转化率。
     */
    data class SettingsItemClicked(val key: SettingKey) :
        AppEvent("settings_item_clicked", mapOf("key" to key))

    /** [endpoint] 是逻辑名而非 URL：带上路径参数会把基数打爆。 */
    data class ApiFailed(val endpoint: String, val status: Int) :
        AppEvent("api_failed", mapOf("endpoint" to endpoint, "status" to status))

    data class ForceUpdate(
        val step: ForceUpdateStep,
        val currentVersion: String? = null,
        val minVersion: String? = null,
    ) : AppEvent(
        "force_update",
        mapOf("step" to step, "current_version" to currentVersion, "min_version" to minVersion),
    )

    data class FeedbackSent(val kind: FeedbackKind, val value: String) :
        AppEvent("feedback_sent", mapOf("kind" to kind, "value" to value))

    /**
     * 解读页打开了但后端没有内容。是**状态**不是页面，所以不占 [Screen] 值——
     * 混进 screen_viewed 会稀释页面浏览量口径。分母是 ScreenViewed(Screen.DIGEST)。
     */
    data class DigestUnavailable(val source: String) :
        AppEvent("digest_unavailable", mapOf("source" to source))
}

/**
 * 按 UTF-16 码元硬切会把代理对劈成半个字符（emoji 标题很常见），落库就是个坏串。
 * 边界正好落在代理对中间时退一格。
 */
private fun String.truncateCodePoints(max: Int): String {
    if (length <= max) return this
    return substring(0, if (this[max - 1].isHighSurrogate()) max - 1 else max)
}

/**
 * 页面身份。**只放真页面**——外链、静默动作、说明弹窗都不是页面，
 * 它们进 [AppEvent.SettingsItemClicked] 或各自的业务事件。
 *
 * 新增页面时不需要写任何埋点代码：在 `App.kt` 的路由声明里实现 `Route` 并填 `screen`，
 * 漏填是编译错误。这里只多一个常量。
 */
enum class Screen {
    ABOUT,
    APPEARANCE,
    CHAT,
    COLOR_LAB,
    DATA_SOURCES,
    DIGEST,
    FAVORITES,
    FEEDBACK,
    GITHUB_PROFILE,
    HOME,
    /** 邮箱登录浮层。不在 backStack 里，是唯一手写上报的页面（L2 登录漏斗的分母）。 */
    LOGIN,
    ME,
    PAYWALL,
    PICKS,
    PROFILE_FOLLOWERS,
    PROFILE_FOLLOWING,
    PROFILE_REPOS,
    README,
    SETTINGS,
    SUBSCRIBE,
    WEB_PAGE,
}

enum class NotificationKind { DAILY_PICKS }

enum class NotificationStep { SHOWN, SKIPPED, RELINKED }

enum class TabSwitchMethod { TAP, DOUBLE_TAP_REFRESH }

enum class ContentActionKind { FAVORITE, UNFAVORITE, SHARE_TO_AI, STAR, READ_ORIGINAL, HN_COMMENTS }

enum class ListFilter { NEW_ONLY, SOURCE, PERIOD, LANGUAGE, HISTORY_DATE, HISTORY_BATCH }

enum class AiKind { CHAT, DETAIL_SUMMARY, RESEARCH }

enum class AiOutcome { OK, ERROR, INTERRUPTED, CACHE_HIT }

enum class AuthAction { SIGN_IN, LINK }

enum class AuthOutcome { SUCCESS, CANCELED, ERROR }

enum class UpsellTarget { PRO, SPONSOR, NEWSLETTER }

enum class CheckoutStepKind { PLAN_SELECTED, OPENED, RECONCILED }

enum class SubscriptionActionKind { MANAGE, CANCEL }

enum class NewsletterActionKind { BANNER_CLICKED, BANNER_DISMISSED, SUBMIT, CANCEL }

enum class ActionOutcome { OK, ERROR }

enum class ForceUpdateStep { SHOWN, CLICKED }

enum class FeedbackKind { SUMMARY_LANGUAGE }

/** 设置项的键。改一个键名就是改一条口径，所以它是枚举而非裸串。 */
enum class SettingKey {
    APP_ICON,
    APP_LANGUAGE,
    CHANGELOG,
    CHECK_UPDATE,
    CUSTOM_THEME_CONTRAST,
    CUSTOM_THEME_STYLE,
    DAILY_PICKS_NOTIFICATION,
    DEFAULT_HOME_TAB,
    IMMERSIVE_BROWSING,
    OPEN_LINKS_IN_BROWSER,
    SEED_COLOR,
    SUMMARY_LANGUAGE,
    THEME,
}
