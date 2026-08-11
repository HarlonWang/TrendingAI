package whl.trending.ai.data.local

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getIntFlow
import com.russhwolf.settings.coroutines.getLongFlow
import com.russhwolf.settings.coroutines.getLongOrNullFlow
import com.russhwolf.settings.coroutines.getStringFlow
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import whl.trending.ai.core.platform.getSystemLanguage
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.model.FOLLOW_SERVER_DEFAULT
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.data.model.PendingFavoriteOp

/**
 * 持久化存的是 ordinal，新档位只能追加在末尾，否则老用户的选择会错位。
 * AMOLED 是深色的变体：同一套深色配色，但背景压到纯黑，OLED 屏上省电。
 */
enum class ThemeMode(val title: String) {
    FOLLOW_SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
    AMOLED("纯黑")
}

const val DEFAULT_SEED_ARGB: Long = 0xFF6750A4L

/**
 * 自定义主题的风格/对比度缺省持久化值，字面量对应 `ThemeStyleOption.SOFT` /
 * `ThemeContrastOption.STANDARD`。这里刻意不引用 ui 层的枚举，避免 data.local 反向依赖 UI；
 * 两侧的对应关系由 ThemeCustomizationTest 断言守住。
 */
const val DEFAULT_THEME_STYLE_STORAGE: String = "soft"
const val DEFAULT_THEME_CONTRAST_STORAGE: String = "standard"

/** 默认首页 tab 的持久化值（HomeTab.name），仅 SettingsManager 内部作缺省值使用 */
private const val DEFAULT_HOME_TAB_NAME = "Home"

/** 首页上次停留的子源（TrendingSource.name），仅 SettingsManager 内部作缺省值使用 */
private const val DEFAULT_TRENDING_SOURCE_NAME = "GitHub"

enum class AppLanguage(val isoCode: String?) {
    FOLLOW_SYSTEM(null),
    CHINESE("zh"),
    ENGLISH("en")
}

/** 后端已支持的摘要语言；「跟随系统」钳制到该清单，清单外的系统语言回落英文 */
val SUPPORTED_SUMMARY_LANGS = setOf("zh", "en")
private const val FALLBACK_SUMMARY_LANG = "en"

/**
 * 摘要内容语言，与 App 界面语言（[AppLanguage]）解耦：
 * 界面语言受限于打包的 strings 翻译，摘要语言只受后端支持范围约束，两者扩展节奏独立。
 * 持久化存 [storageValue] 字符串而非 ordinal，便于后续在任意位置插入新语言。
 */
enum class SummaryLanguage(val isoCode: String?) {
    FOLLOW_SYSTEM(null),
    CHINESE("zh"),
    ENGLISH("en");

    val storageValue: String get() = isoCode ?: "system"

    companion object {
        fun fromStorage(value: String?): SummaryLanguage =
            entries.firstOrNull { it.storageValue == value } ?: FOLLOW_SYSTEM
    }
}

/**
 * 动态 App 图标档位（仅 Android 生效，iOS 隐藏入口）。
 *
 * 同一箭头 glyph 换底色出变体；[backgroundArgb] 是外观页预览用的底色，
 * **必须与 androidApp `res/values/ic_launcher_variants.xml` 里同名色值一致**（两处手动同步）。
 * 持久化存 [id] 字符串而非 ordinal，便于后续插档；图标真实状态由系统组件开关记住，
 * 本地存储只用于设置页回显选中态。
 */
enum class AppIconPreset(val id: String, val backgroundArgb: Long, val darkGlyph: Boolean = false) {
    DEFAULT("default", 0xFF6750A4),
    GRAPHITE("graphite", 0xFF46464A),
    STEEL("steel", 0xFF4A5C92),
    PINE("pine", 0xFF206A5D),
    BERRY("berry", 0xFFA03B49),
    // 浅底上白箭头不可见，预览与真实图标都换深色 glyph
    CREAM("cream", 0xFFF1E7D8, darkGlyph = true);

    companion object {
        fun fromStorage(value: String?): AppIconPreset =
            entries.firstOrNull { it.id == value } ?: DEFAULT
    }
}

/**
 * 一条自定义主题记录。风格/对比度存字符串（`ThemeStyleOption.storageValue` 等），
 * 不引用 ui 层的枚举，避免 data 层反向依赖。
 */
@Serializable
data class CustomThemeEntry(
    val seedArgb: Long,
    val style: String = DEFAULT_THEME_STYLE_STORAGE,
    val contrast: String = DEFAULT_THEME_CONTRAST_STORAGE,
)

/** 进入调色台那一刻的完整主题状态，「撤销修改」按它整体写回 */
data class ThemeSnapshot(
    val seedArgb: Long,
    val isCustom: Boolean,
    val style: String,
    val contrast: String,
    val customSeed: Long?,
)

/** 自定义色历史保留条数：够覆盖一次调色过程中的反复试错，又不至于把调色台塞满 */
private const val CUSTOM_HISTORY_LIMIT = 10

@OptIn(ExperimentalSettingsApi::class)
class SettingsManager(private val settings: ObservableSettings) {
    private val THEME_KEY = "prefs_theme_mode"
    private val SEED_COLOR_KEY = "prefs_seed_color"
    private val THEME_CUSTOM_KEY = "prefs_theme_custom"
    private val THEME_CUSTOM_SEED_KEY = "prefs_theme_custom_seed"
    private val THEME_CUSTOM_HISTORY_KEY = "prefs_theme_custom_history"
    private val THEME_STYLE_KEY = "prefs_theme_style"
    private val THEME_CONTRAST_KEY = "prefs_theme_contrast"
    private val APP_ICON_KEY = "prefs_app_icon"
    private val LANGUAGE_KEY = "prefs_language"
    private val SUMMARY_LANGUAGE_KEY = "prefs_summary_language"
    private val LAST_UPDATE_CHECK_KEY = "prefs_last_update_check"
    private val LAST_SEEN_WHATSNEW_KEY = "prefs_last_seen_whatsnew_version"
    private val FAVORITES_KEY = "prefs_favorites"
    private val FAVORITES_PENDING_KEY = "prefs_favorites_pending"
    private val FAVORITES_MERGED_KEY = "prefs_favorites_merged"
    private val SUBSCRIBED_EMAIL_KEY = "prefs_subscribed_email"
    private val INSTALL_ID_KEY = "prefs_install_id"
    private val USER_AVATAR_KEY = "prefs_user_avatar_url"
    private val USER_GITHUB_LOGIN_KEY = "prefs_user_github_login"
    private val USER_GITHUB_USER_ID_KEY = "prefs_user_github_user_id"
    private val USER_EMAIL_KEY = "prefs_user_email"
    private val FEED_HIGHLIGHTS_ONLY_KEY = "prefs_feed_filter_highlights"
    private val IS_PRO_KEY = "prefs_is_pro"
    private val SPONSOR_PAGE_OPENED_AT_KEY = "prefs_sponsor_page_opened_at"
    private val ACCOUNT_LINK_OPENED_AT_KEY = "prefs_account_link_opened_at"
    // 存储 key 的字面量不随常量名改动——改了等于丢掉存量用户已选的模型
    private val CHAT_MODEL_CHOICE_KEY = "prefs_selected_chat_model"
    private val OPEN_LINKS_IN_CUSTOM_TAB_KEY = "prefs_open_links_in_custom_tab"
    private val TRENDING_NEW_ONLY_DEFAULT_KEY = "prefs_trending_new_only_default"
    private val MIN_VERSION_KEY = "prefs_min_version"
    private val DAILY_PICKS_NOTIFICATION_KEY = "prefs_daily_picks_notification"
    private val PICKS_NEWSLETTER_BANNER_DISMISSED_KEY = "prefs_picks_newsletter_banner_dismissed"
    private val DEFAULT_HOME_TAB_KEY = "prefs_default_home_tab"
    private val TRENDING_SOURCE_KEY = "prefs_trending_source"
    private val IMMERSIVE_BROWSING_KEY = "prefs_immersive_browsing"

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

    val appIcon: Flow<AppIconPreset> = settings.getStringFlow(APP_ICON_KEY, AppIconPreset.DEFAULT.id)
        .map { AppIconPreset.fromStorage(it) }

    fun currentAppIcon(): AppIconPreset =
        AppIconPreset.fromStorage(settings.getString(APP_ICON_KEY, AppIconPreset.DEFAULT.id))

    fun setAppIcon(preset: AppIconPreset) {
        settings.putString(APP_ICON_KEY, preset.id)
    }

    /**
     * 是否处于「自定义主题」状态。
     *
     * 用显式标志而非「seed 是否命中预设表」来判断：用户完全可能在调色台里把颜色
     * 调到与某个预设一模一样，靠反查会误判成预设档，连带丢掉他调的风格与对比度。
     */
    val themeCustom: Flow<Boolean> = settings.getBooleanFlow(THEME_CUSTOM_KEY, false)

    fun currentThemeCustom(): Boolean = settings.getBoolean(THEME_CUSTOM_KEY, false)

    /** 自定义档的风格，存 [ThemeStyleOption.storageValue] 字符串（不存 ordinal，便于以后插档） */
    val themeStyle: Flow<String> =
        settings.getStringFlow(THEME_STYLE_KEY, DEFAULT_THEME_STYLE_STORAGE)

    fun currentThemeStyle(): String =
        settings.getString(THEME_STYLE_KEY, DEFAULT_THEME_STYLE_STORAGE)

    /** 自定义档的对比度，存 [ThemeContrastOption.storageValue] 字符串 */
    val themeContrast: Flow<String> =
        settings.getStringFlow(THEME_CONTRAST_KEY, DEFAULT_THEME_CONTRAST_STORAGE)

    fun currentThemeContrast(): String =
        settings.getString(THEME_CONTRAST_KEY, DEFAULT_THEME_CONTRAST_STORAGE)

    /**
     * 用户调过的自定义色，独立于当前生效的 [seedColor] 保存。
     *
     * 有它，切回预设档之后外观页仍能显示「自定义」那颗圆、一点即回到原来调好的配色；
     * 没有它，自定义色会被预设 seed 覆盖掉，用户想回去只能重调一遍。
     * null 表示从没进过调色台。
     */
    val customSeedColor: Flow<Long?> = settings.getLongOrNullFlow(THEME_CUSTOM_SEED_KEY)

    fun currentCustomSeedColor(): Long? = settings.getLongOrNull(THEME_CUSTOM_SEED_KEY)


    /** 选中某个预设档：写 seed 的同时清掉自定义标志，风格/对比度回到预设钦定的搭配 */
    fun setSeedColor(argb: Long) {
        settings.putLong(SEED_COLOR_KEY, argb)
        settings.putBoolean(THEME_CUSTOM_KEY, false)
    }

    /** 重新选中自定义档：把之前调好的色恢复为生效 seed；没调过则什么都不做 */
    fun selectCustomTheme() {
        val custom = settings.getLongOrNull(THEME_CUSTOM_SEED_KEY) ?: return
        settings.putLong(SEED_COLOR_KEY, custom)
        settings.putBoolean(THEME_CUSTOM_KEY, true)
    }

    /**
     * 进入调色台时的完整主题状态快照，供「撤销修改」整体写回。
     *
     * 调色台是实时生效的——拖一下色相整个 App 立刻变，按返回键并不会撤销。
     * 用户调坏了却记不起原来是什么色，这份快照就是他唯一的退路。
     */
    fun currentThemeSnapshot(): ThemeSnapshot = ThemeSnapshot(
        seedArgb = currentSeedColor(),
        isCustom = currentThemeCustom(),
        style = currentThemeStyle(),
        contrast = currentThemeContrast(),
        customSeed = currentCustomSeedColor(),
    )

    /** 把主题状态整体写回快照那一刻，用于撤销一次调色台编辑 */
    fun restoreThemeSnapshot(snapshot: ThemeSnapshot) {
        settings.putLong(SEED_COLOR_KEY, snapshot.seedArgb)
        settings.putBoolean(THEME_CUSTOM_KEY, snapshot.isCustom)
        settings.putString(THEME_STYLE_KEY, snapshot.style)
        settings.putString(THEME_CONTRAST_KEY, snapshot.contrast)
        // 进入前没有自定义色就把 key 抹掉，否则本次编辑产生的那个会留下来，
        // 外观页末尾那颗圆仍显示成一个已经被撤销掉的颜色
        val customSeed = snapshot.customSeed
        if (customSeed == null) settings.remove(THEME_CUSTOM_SEED_KEY)
        else settings.putLong(THEME_CUSTOM_SEED_KEY, customSeed)
    }

    /**
     * 调色台落盘：四项一起写，避免中间态让主题闪烁成半套配置。
     * 不在这里入历史——拖动过程每 120ms 就落一次盘，会把历史冲成一堆中间色；
     * 由调色台在离开时记一条最终值。
     */
    fun setCustomTheme(argb: Long, styleStorage: String, contrastStorage: String) {
        settings.putLong(SEED_COLOR_KEY, argb)
        settings.putLong(THEME_CUSTOM_SEED_KEY, argb)
        settings.putString(THEME_STYLE_KEY, styleStorage)
        settings.putString(THEME_CONTRAST_KEY, contrastStorage)
        settings.putBoolean(THEME_CUSTOM_KEY, true)
    }

    /**
     * 自定义色历史，最新在前。
     *
     * 调色本身就是试错过程，只留一个「当前自定义色」的话，调错一次、或点一下恢复默认，
     * 之前调好的就永久没了。取色类 UI 普遍保留最近用过的颜色，这里照同一结构做。
         */
    val customThemeHistory: Flow<List<CustomThemeEntry>> =
        settings.getStringOrNullFlow(THEME_CUSTOM_HISTORY_KEY).map { decodeHistory(it) }

    fun currentCustomThemeHistory(): List<CustomThemeEntry> =
        decodeHistory(settings.getStringOrNull(THEME_CUSTOM_HISTORY_KEY))

    private fun decodeHistory(json: String?): List<CustomThemeEntry> {
        if (json.isNullOrEmpty()) return emptyList()
        return runCatching { Json.decodeFromString<List<CustomThemeEntry>>(json) }.getOrElse { emptyList() }
    }

    /** 记一条自定义配置：完全相同的组合提到最前而非重复入列，超出上限截断 */
    fun pushCustomThemeHistory(entry: CustomThemeEntry) {
        val updated = (listOf(entry) + currentCustomThemeHistory().filterNot { it == entry })
            .take(CUSTOM_HISTORY_LIMIT)
        settings.putString(THEME_CUSTOM_HISTORY_KEY, Json.encodeToString(updated))
    }


    init {
        // 摘要语言一次性迁移：旧版本摘要语言复用 App 语言设置，升级后首次构造时
        // 用当时的 App 语言初始化摘要语言，解耦本身不改变既有用户看到的摘要语言
        if (!settings.hasKey(SUMMARY_LANGUAGE_KEY)) {
            val app = AppLanguage.entries.getOrElse(
                settings.getInt(LANGUAGE_KEY, AppLanguage.FOLLOW_SYSTEM.ordinal)
            ) { AppLanguage.FOLLOW_SYSTEM }
            val initial = when (app) {
                AppLanguage.CHINESE -> SummaryLanguage.CHINESE
                AppLanguage.ENGLISH -> SummaryLanguage.ENGLISH
                AppLanguage.FOLLOW_SYSTEM -> SummaryLanguage.FOLLOW_SYSTEM
            }
            settings.putString(SUMMARY_LANGUAGE_KEY, initial.storageValue)
        }
    }

    val appLanguage: Flow<AppLanguage> = settings.getIntFlow(LANGUAGE_KEY, AppLanguage.FOLLOW_SYSTEM.ordinal)
        .map { AppLanguage.entries.getOrElse(it) { AppLanguage.FOLLOW_SYSTEM } }

    /** 同步读当前 App 语言，供非 Compose 上下文使用（如拼外链的 ui_locales 参数）。 */
    fun currentAppLanguage(): AppLanguage = AppLanguage.entries.getOrElse(
        settings.getInt(LANGUAGE_KEY, AppLanguage.FOLLOW_SYSTEM.ordinal)
    ) { AppLanguage.FOLLOW_SYSTEM }

    fun setLanguage(language: AppLanguage) {
        settings.putInt(LANGUAGE_KEY, language.ordinal)
    }

    val summaryLanguage: Flow<SummaryLanguage> =
        settings.getStringFlow(SUMMARY_LANGUAGE_KEY, SummaryLanguage.FOLLOW_SYSTEM.storageValue)
            .map { SummaryLanguage.fromStorage(it) }

    /** 同步读当前摘要语言，供 Compose 取 collectAsState 初值用（避免首帧显示默认值再跳变）。 */
    fun currentSummaryLanguage(): SummaryLanguage = SummaryLanguage.fromStorage(
        settings.getString(SUMMARY_LANGUAGE_KEY, SummaryLanguage.FOLLOW_SYSTEM.storageValue)
    )

    fun setSummaryLanguage(language: SummaryLanguage) {
        settings.putString(SUMMARY_LANGUAGE_KEY, language.storageValue)
    }

    /**
     * 当前内容语言：读摘要语言设置，FOLLOW_SYSTEM 时取系统语言并钳制到后端支持清单
     * （清单外回落英文，避免发出后端无数据的 lang 导致摘要整页为空）。
     * 供摘要请求与邮件订阅复用，避免各处各自推导导致口径分叉。
     */
    suspend fun currentContentLang(): String {
        summaryLanguage.first().isoCode?.let { return it }
        val system = getSystemLanguage()
        return if (system in SUPPORTED_SUMMARY_LANGS) system else FALLBACK_SUMMARY_LANG
    }

    fun getLastUpdateCheckTime(): Long =
        settings.getLong(LAST_UPDATE_CHECK_KEY, 0L)

    fun setLastUpdateCheckTime(time: Long) =
        settings.putLong(LAST_UPDATE_CHECK_KEY, time)

    /**
     * 服务端最近一次下发的最低可用版本；离线冷启动用它兜底判定强更。
     * 服务端撤销（返回 null）时清除，避免离线状态一直误拦。
     */
    fun getCachedMinVersion(): String? =
        settings.getStringOrNull(MIN_VERSION_KEY)

    fun setCachedMinVersion(version: String?) {
        if (version == null) settings.remove(MIN_VERSION_KEY)
        else settings.putString(MIN_VERSION_KEY, version)
    }

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
        // 当前所有调用点均为用户手势，集中在此埋点即可覆盖各页面入口；
        // 若未来引入同步/导入等非手势写入，需绕开本方法或拆分埋点
        trackEvent("favorite_toggle", mapOf("on" to true, "source" to item.source))
    }

    fun removeFavorite(url: String) {
        val current = getCurrentFavorites()
        val updated = current.filter { it.url != url }
        settings.putString(FAVORITES_KEY, Json.encodeToString(updated))
        trackEvent("favorite_toggle", mapOf("on" to false))
    }

    /** 当前收藏快照（同步引擎读本地态用）。 */
    fun currentFavorites(): List<FavoriteItem> = getCurrentFavorites()

    private fun getCurrentFavorites(): List<FavoriteItem> {
        val json = settings.getStringOrNull(FAVORITES_KEY) ?: return emptyList()
        return runCatching { Json.decodeFromString<List<FavoriteItem>>(json) }.getOrElse { emptyList() }
    }

    /**
     * 用服务端全量列表覆盖本地收藏缓存（云同步「打开时全量拉取」用）。
     * 非用户手势，不埋点；与 [addFavorite]/[removeFavorite] 的手势写入区分。
     */
    fun replaceFavorites(items: List<FavoriteItem>) {
        settings.putString(FAVORITES_KEY, Json.encodeToString(items))
    }

    // ---- 云同步状态：待同步 op 队列 + 首次登录合并标记 ----

    fun getPendingFavoriteOps(): List<PendingFavoriteOp> {
        val json = settings.getStringOrNull(FAVORITES_PENDING_KEY) ?: return emptyList()
        return runCatching { Json.decodeFromString<List<PendingFavoriteOp>>(json) }.getOrElse { emptyList() }
    }

    fun setPendingFavoriteOps(ops: List<PendingFavoriteOp>) {
        if (ops.isEmpty()) settings.remove(FAVORITES_PENDING_KEY)
        else settings.putString(FAVORITES_PENDING_KEY, Json.encodeToString(ops))
    }

    /** 是否已完成本次登录的首次合并（true 后走增量 op flush，false 时走全量 batch 合并）。 */
    fun favoritesMerged(): Boolean = settings.getBoolean(FAVORITES_MERGED_KEY, false)

    fun setFavoritesMerged(value: Boolean) {
        settings.putBoolean(FAVORITES_MERGED_KEY, value)
    }

    /**
     * 登出时清空账号收藏与同步状态：收藏已安全存于该账号云端，清本地避免账号间数据串味
     * （下个账号登录时不会把上一个账号的收藏 batch 合并上去）。匿名重新收藏从空开始。
     */
    fun clearFavoritesOnSignOut() {
        settings.remove(FAVORITES_KEY)
        settings.remove(FAVORITES_PENDING_KEY)
        settings.remove(FAVORITES_MERGED_KEY)
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

    /** 已登录用户邮箱缓存：/api/me 写入、登出清除。存量会话的 token 无 email scope 时为 null（不追溯）。 */
    val userEmail: Flow<String?> = settings.getStringOrNullFlow(USER_EMAIL_KEY)

    fun setUserEmail(email: String?) {
        if (email.isNullOrBlank()) {
            settings.remove(USER_EMAIL_KEY)
        } else {
            settings.putString(USER_EMAIL_KEY, email)
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

    /**
     * 最近一次打开 Logto 账户中心「关联 GitHub」页的时间戳（epoch millis），0 表示无待刷新的绑定意图。
     * 与赞助时间戳分开：两者回前台后要做的事不同（绑定要先刷身份，赞助只需对账 Pro）。
     */
    fun currentAccountLinkOpenedAt(): Long = settings.getLong(ACCOUNT_LINK_OPENED_AT_KEY, 0L)

    fun setAccountLinkOpenedAt(time: Long) {
        settings.putLong(ACCOUNT_LINK_OPENED_AT_KEY, time)
    }

    fun clearAccountLinkOpenedAt() {
        settings.putLong(ACCOUNT_LINK_OPENED_AT_KEY, 0L)
    }

    /**
     * 用户的聊天模型意向：钉住的模型 id，或 [FOLLOW_SERVER_DEFAULT]（默认）＝跟随服务端默认、
     * 请求不带 model 字段。两种取值对应下面的 [pinChatModel] / [followServerDefault]。
     *
     * 发请求时透传钉住的值：免费档服务端按白名单强制，Pro 档服务端尊重此选择。
     */
    val chatModelChoice: Flow<String> = settings.getStringFlow(CHAT_MODEL_CHOICE_KEY, FOLLOW_SERVER_DEFAULT)

    fun currentChatModelChoice(): String = settings.getString(CHAT_MODEL_CHOICE_KEY, FOLLOW_SERVER_DEFAULT)

    /** 钉住某个具体模型：此后不再跟随服务端默认的变化，直到它下架或被 [followServerDefault] 解除。 */
    fun pinChatModel(id: String) {
        settings.putString(CHAT_MODEL_CHOICE_KEY, id)
    }

    /**
     * 回到「跟随服务端默认」。三处调用语义一致：用户点中下拉里的默认项、选择已下架/越权时自愈、
     * 登出时清掉可能残留的 Pro 模型选择（避免下个用户继承）。
     */
    fun followServerDefault() {
        settings.remove(CHAT_MODEL_CHOICE_KEY)
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
     * 沉浸式浏览：首页顶栏、子 tab 行、悬浮底栏跟随滚动收起/恢复（#88）。默认 false——常驻的顶/底栏是导航锚点，藏起来换到的屏幕空间不值得
     * 让所有人默认承担「想操作得先往回滑」的代价，留给需要的人自己打开。
     * 关闭时首页不接入任何滚动监听（见 ui/home/HomeImmersive.kt 的 null 模式）。
     */
    val immersiveBrowsing: Flow<Boolean> = settings.getBooleanFlow(IMMERSIVE_BROWSING_KEY, false)

    fun currentImmersiveBrowsing(): Boolean = settings.getBoolean(IMMERSIVE_BROWSING_KEY, false)

    fun setImmersiveBrowsing(value: Boolean) {
        settings.putBoolean(IMMERSIVE_BROWSING_KEY, value)
    }

    /**
     * GitHub 榜「只看 New」的记忆状态：榜单页显式切换时回写，冷启动恢复上次状态（开关即设置）。
     * 视图切换导致的自动关闭（New-only 仅 daily 全语言榜有效）不回写，不覆盖用户意图。
     * 存储键沿用旧「默认值」设置的键，老用户已设置的默认值自然延续为初始记忆状态。
     */
    fun currentTrendingNewOnly(): Boolean =
        settings.getBoolean(TRENDING_NEW_ONLY_DEFAULT_KEY, false)

    fun setTrendingNewOnly(value: Boolean) {
        settings.putBoolean(TRENDING_NEW_ONLY_DEFAULT_KEY, value)
    }

    /**
     * 每日 Picks 本地通知开关（默认关，用户在设置页主动开启）。
     * 仅记录用户意图；实际调度/取消由 DailyPicksNotifier 平台实现负责，
     * worker 执行时也会再读一次此值兜底（关闭后未及时取消的任务不发通知）。
     */
    val dailyPicksNotificationEnabled: Flow<Boolean> =
        settings.getBooleanFlow(DAILY_PICKS_NOTIFICATION_KEY, false)

    fun currentDailyPicksNotificationEnabled(): Boolean =
        settings.getBoolean(DAILY_PICKS_NOTIFICATION_KEY, false)

    fun setDailyPicksNotificationEnabled(value: Boolean) {
        settings.putBoolean(DAILY_PICKS_NOTIFICATION_KEY, value)
    }

    /** Picks 页 Newsletter 横幅的手动关闭标记：点「×」永久收起，与订阅状态解耦。 */
    val picksNewsletterBannerDismissed: Flow<Boolean> =
        settings.getBooleanFlow(PICKS_NEWSLETTER_BANNER_DISMISSED_KEY, false)

    fun currentPicksNewsletterBannerDismissed(): Boolean =
        settings.getBoolean(PICKS_NEWSLETTER_BANNER_DISMISSED_KEY, false)

    fun setPicksNewsletterBannerDismissed(value: Boolean) {
        settings.putBoolean(PICKS_NEWSLETTER_BANNER_DISMISSED_KEY, value)
    }

    /**
     * 冷启动默认显示的首页 tab，存 ui 层 HomeTab 枚举的 name（如 "Home"、"Picks"）。
     * data 层不依赖 ui 层枚举，只存取字符串；解析与回落由 HomeTab.defaultFromName 负责——
     * 历史上存过 "GitHub"/"HackerNews"/"ProductHunt"（0.23 前）与 "Trending"（本 tab 旧名），
     * 都匹配不上，统一回落到 Home，落点与改名前一致。
     * 只决定初始值；会话内切 tab 不回写此设置。
     */
    val defaultHomeTab: Flow<String> = settings.getStringFlow(DEFAULT_HOME_TAB_KEY, DEFAULT_HOME_TAB_NAME)

    fun currentDefaultHomeTab(): String = settings.getString(DEFAULT_HOME_TAB_KEY, DEFAULT_HOME_TAB_NAME)

    fun setDefaultHomeTab(name: String) {
        settings.putString(DEFAULT_HOME_TAB_KEY, name)
    }

    /**
     * 首页上次停留的子源，存 ui 层 TrendingSource 枚举的 name。
     * 与「默认首页」不同，这个值每次切子源都回写——用户上次看的是哪个源，下次冷启动就回哪。
     */
    fun currentTrendingSource(): String = settings.getString(TRENDING_SOURCE_KEY, DEFAULT_TRENDING_SOURCE_NAME)

    fun setTrendingSource(name: String) {
        settings.putString(TRENDING_SOURCE_KEY, name)
    }
}

val globalSettings by lazy { Settings() as ObservableSettings }
val globalSettingsManager by lazy { SettingsManager(globalSettings) }
