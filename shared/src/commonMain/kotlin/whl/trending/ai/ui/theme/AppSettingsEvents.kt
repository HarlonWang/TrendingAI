package whl.trending.ai.ui.theme

import wang.harlon.appsettings.appearance.AppearanceEvent
import wang.harlon.appsettings.appearance.ColorChoice
import wang.harlon.appsettings.appearance.ThemeMode
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.SettingKey
import whl.trending.ai.core.analytics.track
import wang.harlon.appsettings.AppSettingsEvent
import wang.harlon.appsettings.language.LanguageEvent

/**
 * appsettings-kmp 的事件 → 本 App 的埋点词汇（`telemetry-vocabulary.md`）。映射外观与界面语言事件。
 * 取值沿用库化前的口径：theme 是 follow_system / light / dark / amoled，seed_color 是预设 id、custom 或 system，
 * app_language 是 follow_system / chinese / english（库给的是标签，这里翻回旧词，词汇表不用动）。
 */
fun trackAppSettingsEvent(event: AppSettingsEvent) {
    if (event is LanguageEvent.Changed) {
        val value = when (val tag = event.tag) { null -> "follow_system"; "zh" -> "chinese"; "en" -> "english"; else -> tag }
        track(AppEvent.SettingChanged(SettingKey.APP_LANGUAGE, value))
        return
    }
    if (event !is AppearanceEvent) return   // 更新说明等暂不入词汇表
    when (event) {
        is AppearanceEvent.ModeChanged -> track(AppEvent.SettingChanged(SettingKey.THEME, event.mode.analyticsValue))
        is AppearanceEvent.ColorSelected -> when (val choice = event.choice) {
            is ColorChoice.Preset -> track(AppEvent.SettingChanged(SettingKey.SEED_COLOR, choice.id))
            ColorChoice.SystemDynamic -> track(AppEvent.SettingChanged(SettingKey.SEED_COLOR, "system"))
            // 自定义档只会从色板那颗圆进入，统一由 ColorLabOpened 记一次 custom，这里不重复
            is ColorChoice.Custom -> Unit
        }
        AppearanceEvent.ColorLabOpened -> track(AppEvent.SettingChanged(SettingKey.SEED_COLOR, "custom"))
        // 只在离开调色台时上报一次最终值；不记具体色值——那是用户的个人偏好，聚合后也说明不了什么
        is AppearanceEvent.CustomApplied -> {
            track(AppEvent.SettingChanged(SettingKey.CUSTOM_THEME_STYLE, event.color.style.storageValue))
            track(AppEvent.SettingChanged(SettingKey.CUSTOM_THEME_CONTRAST, event.color.contrast.storageValue))
        }
        // 记落定后的状态：切换失败不算
        is AppearanceEvent.AppIconChanged -> track(AppEvent.SettingChanged(SettingKey.APP_ICON, event.id))
        is AppearanceEvent.AppIconChangeFailed -> Unit
    }
}

private val ThemeMode.analyticsValue: String
    get() = when (this) {
        ThemeMode.FollowSystem -> "follow_system"
        ThemeMode.Light -> "light"
        ThemeMode.Dark -> "dark"
        ThemeMode.Amoled -> "amoled"
    }
