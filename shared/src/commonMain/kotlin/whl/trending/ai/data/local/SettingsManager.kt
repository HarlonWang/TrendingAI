package whl.trending.ai.data.local

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getIntFlow
import com.russhwolf.settings.coroutines.getLongFlow
import com.russhwolf.settings.coroutines.getStringFlow
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import whl.trending.ai.core.platform.getSystemLanguage
import whl.trending.ai.data.model.DEFAULT_CHAT_MODEL
import whl.trending.ai.data.model.FavoriteItem

enum class ThemeMode(val title: String) {
    FOLLOW_SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

const val DEFAULT_SEED_ARGB: Long = 0xFF6750A4L

enum class AppLanguage(val isoCode: String?) {
    FOLLOW_SYSTEM(null),
    CHINESE("zh"),
    ENGLISH("en")
}

@OptIn(ExperimentalSettingsApi::class)
class SettingsManager(private val settings: ObservableSettings) {
    private val THEME_KEY = "prefs_theme_mode"
    private val SEED_COLOR_KEY = "prefs_seed_color"
    private val LANGUAGE_KEY = "prefs_language"
    private val LAST_UPDATE_CHECK_KEY = "prefs_last_update_check"
    private val LAST_SEEN_WHATSNEW_KEY = "prefs_last_seen_whatsnew_version"
    private val FAVORITES_KEY = "prefs_favorites"
    private val SUBSCRIBED_EMAIL_KEY = "prefs_subscribed_email"
    private val INSTALL_ID_KEY = "prefs_install_id"
    private val USER_AVATAR_KEY = "prefs_user_avatar_url"
    private val USER_GITHUB_LOGIN_KEY = "prefs_user_github_login"
    private val USER_GITHUB_USER_ID_KEY = "prefs_user_github_user_id"
    private val FEED_HIGHLIGHTS_ONLY_KEY = "prefs_feed_filter_highlights"
    private val IS_PRO_KEY = "prefs_is_pro"
    private val SPONSOR_PAGE_OPENED_AT_KEY = "prefs_sponsor_page_opened_at"
    private val SELECTED_CHAT_MODEL_KEY = "prefs_selected_chat_model"
    private val OPEN_LINKS_IN_CUSTOM_TAB_KEY = "prefs_open_links_in_custom_tab"
    private val TRENDING_NEW_ONLY_DEFAULT_KEY = "prefs_trending_new_only_default"

    /**
     * 安装级匿名标识：首次访问时生成并持久化，之后保持不变（卸载重装才会重新生成）。
     * 用于跨天/跨会话的留存分析，弥补 Aptabase 每日轮换 user_id 无法追踪留存的缺陷。
     *
     * 用 lazy 缓存：每进程只生成一次，既消除 check-then-write 的并发竞态，
     * 也避免每条事件都读一次 settings。
     */
    @OptIn(ExperimentalUuidApi::class)
    private val installId: String by lazy {
        settings.getStringOrNull(INSTALL_ID_KEY)?.let { return@lazy it }
        val id = Uuid.random().toString()
        settings.putString(INSTALL_ID_KEY, id)
        id
    }

    fun getOrCreateInstallId(): String = installId

    val themeMode: Flow<ThemeMode> = settings.getIntFlow(THEME_KEY, ThemeMode.FOLLOW_SYSTEM.ordinal)
        .map { ThemeMode.entries.getOrElse(it) { ThemeMode.FOLLOW_SYSTEM } }

    fun currentThemeMode(): ThemeMode =
        ThemeMode.entries.getOrElse(
            settings.getInt(THEME_KEY, ThemeMode.FOLLOW_SYSTEM.ordinal)
        ) { ThemeMode.FOLLOW_SYSTEM }

    fun setThemeMode(mode: ThemeMode) {
        settings.putInt(THEME_KEY, mode.ordinal)
    }

    val seedColor: Flow<Long> = settings.getLongFlow(SEED_COLOR_KEY, DEFAULT_SEED_ARGB)

    fun currentSeedColor(): Long = settings.getLong(SEED_COLOR_KEY, DEFAULT_SEED_ARGB)

    fun setSeedColor(argb: Long) {
        settings.putLong(SEED_COLOR_KEY, argb)
    }

    val appLanguage: Flow<AppLanguage> = settings.getIntFlow(LANGUAGE_KEY, AppLanguage.FOLLOW_SYSTEM.ordinal)
        .map { AppLanguage.entries.getOrElse(it) { AppLanguage.FOLLOW_SYSTEM } }

    /**
     * 当前内容语言：跟随 App 语言设置，FOLLOW_SYSTEM 时回退系统语言。
     * 供摘要请求与邮件订阅复用，避免各处各自推导导致口径分叉。
     */
    suspend fun currentContentLang(): String =
        appLanguage.first().isoCode ?: getSystemLanguage()

    fun setLanguage(language: AppLanguage) {
        settings.putInt(LANGUAGE_KEY, language.ordinal)
    }

    fun getLastUpdateCheckTime(): Long =
        settings.getLong(LAST_UPDATE_CHECK_KEY, 0L)

    fun setLastUpdateCheckTime(time: Long) =
        settings.putLong(LAST_UPDATE_CHECK_KEY, time)

    /** 最近一次看过更新说明的版本号；null 表示首次安装（从未记录） */
    fun getLastSeenWhatsNewVersion(): String? =
        settings.getStringOrNull(LAST_SEEN_WHATSNEW_KEY)

    fun setLastSeenWhatsNewVersion(version: String) =
        settings.putString(LAST_SEEN_WHATSNEW_KEY, version)

    val favorites: Flow<List<FavoriteItem>> = settings.getStringOrNullFlow(FAVORITES_KEY)
        .map { json ->
            if (json.isNullOrEmpty()) emptyList()
            else runCatching { Json.decodeFromString<List<FavoriteItem>>(json) }.getOrElse { emptyList() }
        }

    fun addFavorite(item: FavoriteItem) {
        val current = getCurrentFavorites()
        if (current.any { it.url == item.url }) return
        val updated = listOf(item) + current
        settings.putString(FAVORITES_KEY, Json.encodeToString(updated))
    }

    fun removeFavorite(url: String) {
        val current = getCurrentFavorites()
        val updated = current.filter { it.url != url }
        settings.putString(FAVORITES_KEY, Json.encodeToString(updated))
    }

    private fun getCurrentFavorites(): List<FavoriteItem> {
        val json = settings.getStringOrNull(FAVORITES_KEY) ?: return emptyList()
        return runCatching { Json.decodeFromString<List<FavoriteItem>>(json) }.getOrElse { emptyList() }
    }

    /** 已登录用户头像 URL 缓存：TopBar 入口同步展示用；登出时清空 */
    val userAvatarUrl: Flow<String?> = settings.getStringOrNullFlow(USER_AVATAR_KEY)

    fun setUserAvatarUrl(url: String?) {
        if (url.isNullOrBlank()) {
            settings.remove(USER_AVATAR_KEY)
        } else {
            settings.putString(USER_AVATAR_KEY, url)
        }
    }

    /**
     * 已登录用户 GitHub 身份缓存（login + 数字 id）：syncMe 时随头像一起写入，登出清除。
     * 供需要带身份的同步场景（如语言意图采集的反馈正文）直接读取，免去现场再拉一次 /api/me。
     */
    fun currentGithubLogin(): String? = settings.getStringOrNull(USER_GITHUB_LOGIN_KEY)

    fun currentGithubUserId(): Long? = settings.getLongOrNull(USER_GITHUB_USER_ID_KEY)

    fun setGithubIdentity(login: String?, userId: Long?) {
        if (login.isNullOrBlank()) {
            settings.remove(USER_GITHUB_LOGIN_KEY)
        } else {
            settings.putString(USER_GITHUB_LOGIN_KEY, login)
        }
        if (userId == null) {
            settings.remove(USER_GITHUB_USER_ID_KEY)
        } else {
            settings.putLong(USER_GITHUB_USER_ID_KEY, userId)
        }
    }

    /** Pro 权益态缓存：登录后由 /api/me 写入、登出清除。UI 据此解锁模型切换、切换配额卡形态。 */
    val isPro: Flow<Boolean> = settings.getBooleanFlow(IS_PRO_KEY, false)

    fun currentIsPro(): Boolean = settings.getBoolean(IS_PRO_KEY, false)

    fun setIsPro(value: Boolean) {
        settings.putBoolean(IS_PRO_KEY, value)
    }

    /** 最近一次打开 GitHub Sponsors 赞助页的时间戳（epoch millis），0 表示无待对账的赞助意图。 */
    fun currentSponsorPageOpenedAt(): Long = settings.getLong(SPONSOR_PAGE_OPENED_AT_KEY, 0L)

    fun setSponsorPageOpenedAt(time: Long) {
        settings.putLong(SPONSOR_PAGE_OPENED_AT_KEY, time)
    }

    fun clearSponsorPageOpenedAt() {
        settings.putLong(SPONSOR_PAGE_OPENED_AT_KEY, 0L)
    }

    /** 选中的聊天模型 id：发请求时透传（服务端仍按 tier 强制）。默认免费 gpt-5.4。 */
    val selectedChatModel: Flow<String> = settings.getStringFlow(SELECTED_CHAT_MODEL_KEY, DEFAULT_CHAT_MODEL)

    fun currentSelectedChatModel(): String = settings.getString(SELECTED_CHAT_MODEL_KEY, DEFAULT_CHAT_MODEL)

    fun setSelectedChatModel(id: String) {
        settings.putString(SELECTED_CHAT_MODEL_KEY, id)
    }

    /** 登出时调用：清掉可能残留的 Pro 模型选择，避免下个用户继承（回落到默认 gpt-5.4）。 */
    fun clearSelectedChatModel() {
        settings.remove(SELECTED_CHAT_MODEL_KEY)
    }

    val subscribedEmail: Flow<String?> = settings.getStringOrNullFlow(SUBSCRIBED_EMAIL_KEY)

    fun currentSubscribedEmail(): String? = settings.getStringOrNull(SUBSCRIBED_EMAIL_KEY)

    fun setSubscribedEmail(email: String?) {
        if (email.isNullOrBlank()) {
            settings.remove(SUBSCRIBED_EMAIL_KEY)
        } else {
            settings.putString(SUBSCRIBED_EMAIL_KEY, email)
        }
    }

    val feedHighlightsOnly: Flow<Boolean> = settings.getBooleanFlow(FEED_HIGHLIGHTS_ONLY_KEY, true)

    fun currentFeedHighlightsOnly(): Boolean = settings.getBoolean(FEED_HIGHLIGHTS_ONLY_KEY, true)

    fun setFeedHighlightsOnly(value: Boolean) {
        settings.putBoolean(FEED_HIGHLIGHTS_ONLY_KEY, value)
    }

    /**
     * 外链打开方式：true 走系统浏览器（Custom Tabs / SFSafariViewController），
     * false 走内置 WebView。默认 true——真实浏览器指纹可通过 Cloudflare 等人机验证，
     * 且自带翻译/密码填充/登录态。GitHub README 阅读不经外链路由，不受此设置影响。
     */
    val openLinksInCustomTab: Flow<Boolean> = settings.getBooleanFlow(OPEN_LINKS_IN_CUSTOM_TAB_KEY, true)

    fun currentOpenLinksInCustomTab(): Boolean = settings.getBoolean(OPEN_LINKS_IN_CUSTOM_TAB_KEY, true)

    fun setOpenLinksInCustomTab(value: Boolean) {
        settings.putBoolean(OPEN_LINKS_IN_CUSTOM_TAB_KEY, value)
    }

    /**
     * GitHub 榜「只看 New」的默认状态：true 时进入 app 默认开启该过滤。
     * 只决定初始值；榜单页手动切换仅影响当前会话，不回写此设置。
     */
    val trendingNewOnlyDefault: Flow<Boolean> =
        settings.getBooleanFlow(TRENDING_NEW_ONLY_DEFAULT_KEY, false)

    fun currentTrendingNewOnlyDefault(): Boolean =
        settings.getBoolean(TRENDING_NEW_ONLY_DEFAULT_KEY, false)

    fun setTrendingNewOnlyDefault(value: Boolean) {
        settings.putBoolean(TRENDING_NEW_ONLY_DEFAULT_KEY, value)
    }
}

val globalSettings by lazy { Settings() as ObservableSettings }
val globalSettingsManager by lazy { SettingsManager(globalSettings) }
