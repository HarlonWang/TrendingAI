package whl.trending.ai.data.local

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.getIntFlow
import com.russhwolf.settings.coroutines.getLongFlow
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
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
    private val FAVORITES_KEY = "prefs_favorites"
    private val SUBSCRIBED_EMAIL_KEY = "prefs_subscribed_email"
    private val INSTALL_ID_KEY = "prefs_install_id"

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

    fun getThemeModeSync(): ThemeMode =
        ThemeMode.entries.getOrElse(
            settings.getInt(THEME_KEY, ThemeMode.FOLLOW_SYSTEM.ordinal)
        ) { ThemeMode.FOLLOW_SYSTEM }

    fun setThemeMode(mode: ThemeMode) {
        settings.putInt(THEME_KEY, mode.ordinal)
    }

    val seedColor: Flow<Long> = settings.getLongFlow(SEED_COLOR_KEY, DEFAULT_SEED_ARGB)

    fun getSeedColorSync(): Long = settings.getLong(SEED_COLOR_KEY, DEFAULT_SEED_ARGB)

    fun setSeedColor(argb: Long) {
        settings.putLong(SEED_COLOR_KEY, argb)
    }

    val appLanguage: Flow<AppLanguage> = settings.getIntFlow(LANGUAGE_KEY, AppLanguage.FOLLOW_SYSTEM.ordinal)
        .map { AppLanguage.entries.getOrElse(it) { AppLanguage.FOLLOW_SYSTEM } }

    fun setLanguage(language: AppLanguage) {
        settings.putInt(LANGUAGE_KEY, language.ordinal)
    }

    fun getLastUpdateCheckTime(): Long =
        settings.getLong(LAST_UPDATE_CHECK_KEY, 0L)

    fun setLastUpdateCheckTime(time: Long) =
        settings.putLong(LAST_UPDATE_CHECK_KEY, time)

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

    val subscribedEmail: Flow<String?> = settings.getStringOrNullFlow(SUBSCRIBED_EMAIL_KEY)

    fun getSubscribedEmailSync(): String? = settings.getStringOrNull(SUBSCRIBED_EMAIL_KEY)

    fun setSubscribedEmail(email: String?) {
        if (email.isNullOrBlank()) {
            settings.remove(SUBSCRIBED_EMAIL_KEY)
        } else {
            settings.putString(SUBSCRIBED_EMAIL_KEY, email)
        }
    }
}

val globalSettings by lazy { Settings() as ObservableSettings }
val globalSettingsManager by lazy { SettingsManager(globalSettings) }
