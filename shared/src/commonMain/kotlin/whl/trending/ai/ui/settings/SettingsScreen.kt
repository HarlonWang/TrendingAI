package whl.trending.ai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.app_language_desc
import trendingai.shared.generated.resources.about
import trendingai.shared.generated.resources.appearance
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.cancel
import trendingai.shared.generated.resources.close
import trendingai.shared.generated.resources.data_sources_desc
import trendingai.shared.generated.resources.data_sources_title
import trendingai.shared.generated.resources.chat_title
import trendingai.shared.generated.resources.daily_picks_notification
import trendingai.shared.generated.resources.default_home_tab
import trendingai.shared.generated.resources.default_home_tab_desc
import trendingai.shared.generated.resources.immersive_browsing
import trendingai.shared.generated.resources.immersive_browsing_desc
import trendingai.shared.generated.resources.feedback
import trendingai.shared.generated.resources.feedback_email_invalid
import trendingai.shared.generated.resources.feedback_email_placeholder
import trendingai.shared.generated.resources.feedback_error
import trendingai.shared.generated.resources.feedback_rate_limit
import trendingai.shared.generated.resources.language_option_chinese
import trendingai.shared.generated.resources.language_option_english
import trendingai.shared.generated.resources.language_option_follow_system
import trendingai.shared.generated.resources.language_settings
import trendingai.shared.generated.resources.notification_permission_denied
import trendingai.shared.generated.resources.open_links_in_browser
import trendingai.shared.generated.resources.open_links_in_browser_desc
import trendingai.shared.generated.resources.open_system_settings
import trendingai.shared.generated.resources.personalization
import trendingai.shared.generated.resources.picks_title
import trendingai.shared.generated.resources.me_title
import trendingai.shared.generated.resources.settings
import trendingai.shared.generated.resources.settings_group_general
import trendingai.shared.generated.resources.subscribe_title
import trendingai.shared.generated.resources.subscription_reminders
import trendingai.shared.generated.resources.summary_lang_capture_identity_note
import trendingai.shared.generated.resources.summary_lang_capture_label
import trendingai.shared.generated.resources.summary_lang_capture_message
import trendingai.shared.generated.resources.summary_lang_capture_title
import trendingai.shared.generated.resources.summary_language
import trendingai.shared.generated.resources.summary_language_desc
import trendingai.shared.generated.resources.summary_language_feedback
import trendingai.shared.generated.resources.summary_language_message
import trendingai.shared.generated.resources.summary_language_sponsor
import trendingai.shared.generated.resources.home_title
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.core.isValidEmail
import whl.trending.ai.core.platform.getSystemLanguage
import whl.trending.ai.core.platform.getSystemLanguageDisplayName
import whl.trending.ai.core.platform.isIosPlatform
import whl.trending.ai.core.platform.openAppSettings
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.SummaryLanguage
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.remote.ApiException
import whl.trending.ai.data.repository.TrendingRepository
import whl.trending.ai.notification.globalDailyPicksNotifier
import whl.trending.ai.ui.common.SettingsGroup
import whl.trending.ai.ui.common.TrendingDropdownMenu
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar
import whl.trending.ai.ui.home.HomeTab

/**
 * 设置页：应用偏好的唯一落点，从账户页（后续版本为「我的」tab 的扩展菜单）进入。
 *
 * 分三组：个性化（外观 / 界面语言 / 摘要语言 / 默认首页）、订阅与提醒（邮件订阅 / 每日推送）、
 * 通用（外链打开方式 / 反馈 / 关于）。
 *
 * 这一页同时收掉了原来的「应用设置」子页——账户中心拆分后设置不再与身份、额度混排，
 * 四个低频偏好可以直接平铺，不必再折叠一层。「关于」在通用组末尾，与底栏「⋯」菜单里的那一项指向同一页——
 * 菜单是快捷入口，这里是完整清单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToSubscribe: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToDataSources: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    val isIos = isIosPlatform()
    val themeMode by globalSettingsManager.themeMode.collectAsState(ThemeMode.FOLLOW_SYSTEM)
    val appLanguage by globalSettingsManager.appLanguage.collectAsState(AppLanguage.FOLLOW_SYSTEM)
    val summaryLanguage by globalSettingsManager.summaryLanguage.collectAsState(SummaryLanguage.FOLLOW_SYSTEM)
    val openLinksInCustomTab by globalSettingsManager.openLinksInCustomTab.collectAsState(true)
    val immersiveBrowsing by globalSettingsManager.immersiveBrowsing.collectAsState(false)
    val defaultHomeTab by globalSettingsManager.defaultHomeTab.collectAsState(
        remember { globalSettingsManager.currentDefaultHomeTab() }
    )
    val dailyPicksNotificationEnabled by globalSettingsManager.dailyPicksNotificationEnabled.collectAsState(
        remember { globalSettingsManager.currentDailyPicksNotificationEnabled() }
    )
    val authState by globalAuthManager.authState.collectAsState()
    val isLoggedIn = authState is AuthState.LoggedIn

    var showSummaryLanguageDialog by remember { mutableStateOf(false) }
    var showLangCaptureDialog by remember { mutableStateOf(false) }
    // 三个下拉菜单的展开态提到页面作用域：SettingsGroup 的 content 是收集用的普通 lambda，
    // 不是 @Composable，里面调不了 remember
    var appLanguageMenuExpanded by remember { mutableStateOf(false) }
    var summaryLanguageMenuExpanded by remember { mutableStateOf(false) }
    var homeTabMenuExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val settingsScope = rememberCoroutineScope()
    val permissionDeniedMsg = stringResource(Res.string.notification_permission_denied)
    val openSystemSettingsLabel = stringResource(Res.string.open_system_settings)

    if (showSummaryLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showSummaryLanguageDialog = false },
            title = { Text(stringResource(Res.string.summary_language)) },
            text = { Text(stringResource(Res.string.summary_language_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSummaryLanguageDialog = false
                    showLangCaptureDialog = true
                }) {
                    Text(stringResource(Res.string.summary_language_sponsor))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showSummaryLanguageDialog = false }) {
                        Text(stringResource(Res.string.close))
                    }
                    TextButton(onClick = {
                        showSummaryLanguageDialog = false
                        trackEvent("settings_summary_language_feedback")
                        onNavigateToFeedback()
                    }) {
                        Text(stringResource(Res.string.summary_language_feedback))
                    }
                }
            }
        )
    }

    if (showLangCaptureDialog) {
        LanguageCaptureDialog(isLoggedIn = isLoggedIn, onDismiss = { showLangCaptureDialog = false })
    }

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ① 个性化
            item(key = "group_personalization") {
                SettingsGroup(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(Res.string.personalization),
                ) {
                    settingsItem(
                        icon = Icons.Default.Palette,
                        title = { Text(stringResource(Res.string.appearance)) },
                        trailing = {
                            Text(
                                text = themeModeText(themeMode),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            trackEvent("settings_appearance")
                            onNavigateToAppearance()
                        },
                    )
                    // 应用语言：只管界面文案；iOS 由系统的按 App 语言设置接管，跳系统设置
                    settingsItem(
                        icon = Icons.Default.Language,
                        title = { Text(stringResource(Res.string.language_settings)) },
                        description = { Text(stringResource(Res.string.app_language_desc)) },
                        trailing = {
                            if (isIos) {
                                Text(
                                    text = stringResource(Res.string.open_system_settings),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Box {
                                    Text(
                                        text = languageOptionText(appLanguage),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    TrendingDropdownMenu(
                                        expanded = appLanguageMenuExpanded,
                                        onDismissRequest = { appLanguageMenuExpanded = false },
                                    ) {
                                        AppLanguage.entries.forEach { language ->
                                            DropdownMenuItem(
                                                text = { Text(languageOptionText(language)) },
                                                onClick = {
                                                    appLanguageMenuExpanded = false
                                                    trackEvent("settings_language_change", mapOf("language" to language.name.lowercase()))
                                                    globalSettingsManager.setLanguage(language)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        onClick = {
                            if (isIos) openAppSettings() else appLanguageMenuExpanded = true
                        },
                    )
                    // 摘要语言：独立决定 AI 摘要/解读的请求语言；行点击弹说明（含「更多语言」引导采集 + 赞助），
                    // 点右侧当前值才是直接换语言——两个动作分开，别合并
                    settingsItem(
                        icon = Icons.Default.Translate,
                        title = { Text(stringResource(Res.string.summary_language)) },
                        description = { Text(stringResource(Res.string.summary_language_desc)) },
                        trailing = {
                            Box {
                                Text(
                                    text = languageOptionText(summaryLanguage),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { summaryLanguageMenuExpanded = true },
                                )
                                TrendingDropdownMenu(
                                    expanded = summaryLanguageMenuExpanded,
                                    onDismissRequest = { summaryLanguageMenuExpanded = false },
                                ) {
                                    SummaryLanguage.entries.forEach { language ->
                                        DropdownMenuItem(
                                            text = { Text(languageOptionText(language)) },
                                            onClick = {
                                                summaryLanguageMenuExpanded = false
                                                trackEvent("settings_summary_language_change", mapOf("language" to language.name.lowercase()))
                                                globalSettingsManager.setSummaryLanguage(language)
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            trackEvent("settings_summary_language", mapOf("summary_language" to summaryLanguage.name.lowercase()))
                            showSummaryLanguageDialog = true
                        },
                    )
                    // 默认首页 tab：只决定冷启动进入哪个 tab，会话内切换不回写
                    settingsItem(
                        icon = Icons.Default.Home,
                        title = { Text(stringResource(Res.string.default_home_tab)) },
                        description = { Text(stringResource(Res.string.default_home_tab_desc)) },
                        trailing = {
                            Box {
                                Text(
                                    text = homeTabOptionText(HomeTab.defaultFromName(defaultHomeTab)),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                TrendingDropdownMenu(
                                    expanded = homeTabMenuExpanded,
                                    onDismissRequest = { homeTabMenuExpanded = false },
                                ) {
                                    HomeTab.defaultCandidates.forEach { tab ->
                                        DropdownMenuItem(
                                            text = { Text(homeTabOptionText(tab)) },
                                            onClick = {
                                                homeTabMenuExpanded = false
                                                trackEvent("settings_default_home_tab_change", mapOf("tab" to tab.name.lowercase()))
                                                globalSettingsManager.setDefaultHomeTab(tab.name)
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { homeTabMenuExpanded = true },
                    )
                    // 沉浸式浏览：默认关。行点击与开关同为切换（与「外链打开方式」一致）
                    settingsItem(
                        icon = Icons.Default.SwipeVertical,
                        title = { Text(stringResource(Res.string.immersive_browsing)) },
                        description = { Text(stringResource(Res.string.immersive_browsing_desc)) },
                        trailing = {
                            Switch(
                                checked = immersiveBrowsing,
                                onCheckedChange = { enabled ->
                                    trackEvent("settings_immersive_toggle", mapOf("enabled" to enabled.toString()))
                                    globalSettingsManager.setImmersiveBrowsing(enabled)
                                },
                            )
                        },
                        onClick = {
                            val enabled = !immersiveBrowsing
                            trackEvent("settings_immersive_toggle", mapOf("enabled" to enabled.toString()))
                            globalSettingsManager.setImmersiveBrowsing(enabled)
                        },
                    )
                }
            }

            // ② 订阅与提醒
            item(key = "group_subscription") {
                SettingsGroup(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(Res.string.subscription_reminders),
                ) {
                    settingsItem(
                        icon = Icons.Default.Email,
                        title = { Text(stringResource(Res.string.subscribe_title)) },
                        onClick = {
                            trackEvent("settings_subscribe")
                            onNavigateToSubscribe()
                        },
                    )
                    if (globalDailyPicksNotifier.isSupported) {
                        settingsItem(
                            icon = Icons.Default.Notifications,
                            title = { Text(stringResource(Res.string.daily_picks_notification)) },
                            trailing = {
                                Switch(
                                    checked = dailyPicksNotificationEnabled,
                                    onCheckedChange = { enabled ->
                                        trackEvent(
                                            "settings_daily_picks_notification",
                                            mapOf("enabled" to enabled.toString())
                                        )
                                        if (enabled) {
                                            settingsScope.launch {
                                                val granted = globalDailyPicksNotifier.enable()
                                                globalSettingsManager.setDailyPicksNotificationEnabled(granted)
                                                if (!granted) {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = permissionDeniedMsg,
                                                        actionLabel = openSystemSettingsLabel,
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        openAppSettings()
                                                    }
                                                }
                                            }
                                        } else {
                                            globalDailyPicksNotifier.disable()
                                            globalSettingsManager.setDailyPicksNotificationEnabled(false)
                                        }
                                    }
                                )
                            },
                        )
                    }
                }
            }

            // ③ 通用
            item(key = "group_general") {
                SettingsGroup(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(Res.string.settings_group_general),
                ) {
                    // 外链打开方式：行点击与开关同为切换
                    settingsItem(
                        icon = Icons.Default.OpenInBrowser,
                        title = { Text(stringResource(Res.string.open_links_in_browser)) },
                        description = { Text(stringResource(Res.string.open_links_in_browser_desc)) },
                        trailing = {
                            Switch(
                                checked = openLinksInCustomTab,
                                onCheckedChange = { enabled ->
                                    trackEvent("settings_open_links_in_browser", mapOf("enabled" to enabled.toString()))
                                    globalSettingsManager.setOpenLinksInCustomTab(enabled)
                                }
                            )
                        },
                        onClick = {
                            val enabled = !openLinksInCustomTab
                            trackEvent("settings_open_links_in_browser", mapOf("enabled" to enabled.toString()))
                            globalSettingsManager.setOpenLinksInCustomTab(enabled)
                        },
                    )
                    // 三源抓取时机与收录口径。挂在设置而非「关于」里：它解释的是**每天看到的
                    // 内容从哪来**，属于日常疑问，不是版本/致谢那类身份信息
                    settingsItem(
                        icon = Icons.Default.Schedule,
                        title = { Text(stringResource(Res.string.data_sources_title)) },
                        description = { Text(stringResource(Res.string.data_sources_desc)) },
                        onClick = {
                            trackEvent("settings_data_sources")
                            onNavigateToDataSources()
                        },
                    )
                    settingsItem(
                        icon = Icons.Default.Feedback,
                        title = { Text(stringResource(Res.string.feedback)) },
                        onClick = {
                            trackEvent("settings_feedback")
                            onNavigateToFeedback()
                        },
                    )
                    // 关于页同时挂在底栏的「⋯」菜单上——那里是快捷入口，这里是设置的完整清单
                    settingsItem(
                        icon = Icons.Default.Info,
                        title = { Text(stringResource(Res.string.about)) },
                        onClick = {
                            trackEvent("settings_about")
                            onNavigateToAbout()
                        },
                    )
                }
            }
            item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** 设置分组标题，账户页与设置页共用。 */
@Composable
private fun languageOptionText(language: AppLanguage): String {
    val labelRes = when (language) {
        AppLanguage.FOLLOW_SYSTEM -> Res.string.language_option_follow_system
        AppLanguage.CHINESE -> Res.string.language_option_chinese
        AppLanguage.ENGLISH -> Res.string.language_option_english
    }
    return stringResource(labelRes)
}

@Composable
private fun languageOptionText(language: SummaryLanguage): String {
    val labelRes = when (language) {
        SummaryLanguage.FOLLOW_SYSTEM -> Res.string.language_option_follow_system
        SummaryLanguage.CHINESE -> Res.string.language_option_chinese
        SummaryLanguage.ENGLISH -> Res.string.language_option_english
    }
    return stringResource(labelRes)
}

@Composable
private fun homeTabOptionText(tab: HomeTab): String = when (tab) {
    HomeTab.Home -> stringResource(Res.string.home_title)
    HomeTab.Picks -> stringResource(Res.string.picks_title)
    HomeTab.Me -> stringResource(Res.string.me_title)
    // Chat 不在 defaultCandidates 里，选项列表不会渲染它；穷尽 when 用
    HomeTab.Chat -> stringResource(Res.string.chat_title)
}

/**
 * 摘要语言意图采集弹窗：期望语言（+ 未登录的选填邮箱）经反馈接口提交，成功后跳赞助页。
 * 状态自持有——挂载即全新、关闭即丢弃。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LanguageCaptureDialog(isLoggedIn: Boolean, onDismiss: () -> Unit) {
    var langInput by remember { mutableStateOf(getSystemLanguageDisplayName()) }
    var langEmail by remember { mutableStateOf("") }
    var langEmailInvalid by remember { mutableStateOf(false) }
    var langSubmitting by remember { mutableStateOf(false) }
    var langError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val errGeneric = stringResource(Res.string.feedback_error)
    val errRate = stringResource(Res.string.feedback_rate_limit)

    AlertDialog(
        onDismissRequest = { if (!langSubmitting) onDismiss() },
        title = { Text(stringResource(Res.string.summary_lang_capture_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.summary_lang_capture_message))
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = langInput,
                    onValueChange = { langInput = it; langError = null },
                    label = { Text(stringResource(Res.string.summary_lang_capture_label)) },
                    singleLine = true,
                    isError = langError != null,
                    enabled = !langSubmitting,
                    supportingText = langError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isLoggedIn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.summary_lang_capture_identity_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = langEmail,
                        onValueChange = { langEmail = it; langEmailInvalid = false },
                        label = { Text(stringResource(Res.string.feedback_email_placeholder)) },
                        singleLine = true,
                        isError = langEmailInvalid,
                        enabled = !langSubmitting,
                        supportingText = if (langEmailInvalid) {
                            { Text(stringResource(Res.string.feedback_email_invalid), color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = langInput.isNotBlank() && !langSubmitting,
                onClick = {
                    val lang = langInput.trim()
                    if (lang.isEmpty()) return@TextButton
                    val email = langEmail.trim()
                    if (!isLoggedIn && email.isNotEmpty() && !isValidEmail(email)) {
                        langEmailInvalid = true
                        return@TextButton
                    }
                    langSubmitting = true
                    langError = null
                    scope.launch {
                        val identityLine = if (isLoggedIn) {
                            val login = globalSettingsManager.currentGithubLogin()
                            val userId = globalSettingsManager.currentGithubUserId()
                            when {
                                login != null && userId != null -> "GitHub：@${login}（id ${userId}）"
                                login != null -> "GitHub：@${login}"
                                else -> "GitHub：已登录（未取到身份）"
                            }
                        } else null
                        val content = buildString {
                            append("【摘要语言支持请求】期望语言：$lang · 系统语言：${getSystemLanguage()}")
                            identityLine?.let { append(" · $it") }
                        }
                        val submitEmail = if (!isLoggedIn) email.ifEmpty { null } else null
                        TrendingRepository.shared.submitFeedback(content, submitEmail).fold(
                            onSuccess = {
                                langSubmitting = false
                                onDismiss()
                                trackEvent("settings_summary_language_sponsor", mapOf("language" to lang))
                                ProSponsor.openSponsorPage(ProSponsor.SOURCE_SETTINGS_LANGUAGE)
                            },
                            onFailure = { e ->
                                langSubmitting = false
                                langError = if ((e as? ApiException)?.statusCode == 429) errRate else errGeneric
                            },
                        )
                    }
                }
            ) {
                if (langSubmitting) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(stringResource(Res.string.summary_language_sponsor))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !langSubmitting, onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
