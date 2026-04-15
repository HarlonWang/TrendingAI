package whl.trending.ai.data.local

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.getIntFlow
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import whl.trending.ai.data.model.FavoriteItem

enum class ThemeMode(val title: String) {
    FOLLOW_SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

enum class AppLanguage(val isoCode: String?) {
    FOLLOW_SYSTEM(null),
    CHINESE("zh"),
    ENGLISH("en")
}

@OptIn(ExperimentalSettingsApi::class)
class SettingsManager(private val settings: ObservableSettings) {
    private val THEME_KEY = "prefs_theme_mode"
    private val LANGUAGE_KEY = "prefs_language"
    private val LAST_UPDATE_CHECK_KEY = "prefs_last_update_check"
    private val FAVORITES_KEY = "prefs_favorites"

    val themeMode: Flow<ThemeMode> = settings.getIntFlow(THEME_KEY, ThemeMode.FOLLOW_SYSTEM.ordinal)
        .map { ThemeMode.entries.getOrElse(it) { ThemeMode.FOLLOW_SYSTEM } }

    fun setThemeMode(mode: ThemeMode) {
        settings.putInt(THEME_KEY, mode.ordinal)
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

    fun isFavorite(url: String): Flow<Boolean> = favorites.map { list -> list.any { it.url == url } }

    private fun getCurrentFavorites(): List<FavoriteItem> {
        val json = settings.getStringOrNull(FAVORITES_KEY) ?: return emptyList()
        return runCatching { Json.decodeFromString<List<FavoriteItem>>(json) }.getOrElse { emptyList() }
    }
}

val globalSettings by lazy { Settings() as ObservableSettings }
val globalSettingsManager by lazy { SettingsManager(globalSettings) }
