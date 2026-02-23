package whl.trending.ai.data.local

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.getIntFlow
import com.russhwolf.settings.coroutines.getBooleanFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    private val NOTIFICATION_KEY = "prefs_notification_enabled"

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

    val isNotificationEnabled: Flow<Boolean> = settings.getBooleanFlow(NOTIFICATION_KEY, false)

    fun setNotificationEnabled(enabled: Boolean) {
        settings.putBoolean(NOTIFICATION_KEY, enabled)
        whl.trending.ai.core.platform.NotificationScheduler.update(enabled)
    }
}

val globalSettings by lazy { Settings() as ObservableSettings }
val globalSettingsManager by lazy { SettingsManager(globalSettings) }
