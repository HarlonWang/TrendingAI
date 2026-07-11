package whl.trending.ai.ui.settings

import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.core.platform.isIosPlatform
import whl.trending.ai.core.platform.openAppSettings
import whl.trending.ai.core.platform.getAppVersion
import whl.trending.ai.core.platform.getSystemLanguage
import whl.trending.ai.core.platform.getSystemLanguageDisplayName
import whl.trending.ai.core.Constants
import whl.trending.ai.core.isValidEmail
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.data.remote.ApiException
import whl.trending.ai.data.repository.TrendingRepository
import whl.trending.ai.ui.home.HomeTab
import whl.trending.ai.notification.globalDailyPicksNotifier
import whl.trending.ai.update.globalUpdateChecker

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.about
import trendingai.shared.generated.resources.about_us
import trendingai.shared.generated.resources.about_us_desc
import trendingai.shared.generated.resources.appearance
import trendingai.shared.generated.resources.app_settings
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.close
import trendingai.shared.generated.resources.daily_picks_notification
import trendingai.shared.generated.resources.daily_picks_notification_desc
import trendingai.shared.generated.resources.notification_permission_denied
import trendingai.shared.generated.resources.default_home_tab
import trendingai.shared.generated.resources.default_home_tab_desc
import trendingai.shared.generated.resources.hackernews_title
import trendingai.shared.generated.resources.producthunt_title
import trendingai.shared.generated.resources.picks_title
import trendingai.shared.generated.resources.language_settings
import trendingai.shared.generated.resources.language_option_chinese
import trendingai.shared.generated.resources.language_option_english
import trendingai.shared.generated.resources.language_option_follow_system
import trendingai.shared.generated.resources.new_only_default
import trendingai.shared.generated.resources.new_only_default_desc
import trendingai.shared.generated.resources.open_links_in_browser
import trendingai.shared.generated.resources.open_links_in_browser_desc
import trendingai.shared.generated.resources.open_system_settings
import trendingai.shared.generated.resources.personalization
import trendingai.shared.generated.resources.settings

import trendingai.shared.generated.resources.favorites
import trendingai.shared.generated.resources.feedback
import trendingai.shared.generated.resources.feedback_desc
import trendingai.shared.generated.resources.subscribe_title
import trendingai.shared.generated.resources.subscribe_desc
import trendingai.shared.generated.resources.summary_language
import trendingai.shared.generated.resources.summary_language_desc
import trendingai.shared.generated.resources.summary_language_feedback
import trendingai.shared.generated.resources.summary_language_message
import trendingai.shared.generated.resources.summary_language_sponsor
import trendingai.shared.generated.resources.summary_lang_capture_title
import trendingai.shared.generated.resources.summary_lang_capture_message
import trendingai.shared.generated.resources.summary_lang_capture_label
import trendingai.shared.generated.resources.summary_lang_capture_identity_note
import trendingai.shared.generated.resources.cancel
import trendingai.shared.generated.resources.feedback_error
import trendingai.shared.generated.resources.feedback_rate_limit
import trendingai.shared.generated.resources.feedback_email_placeholder
import trendingai.shared.generated.resources.feedback_email_invalid
import trendingai.shared.generated.resources.version
import trendingai.shared.generated.resources.version_tap_to_check
import trendingai.shared.generated.resources.version_up_to_date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToSubscribe: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    val isIos = isIosPlatform()
    val uriHandler = LocalUriHandler.current
    val themeMode by globalSettingsManager.themeMode.collectAsState(ThemeMode.FOLLOW_SYSTEM)
    val appLanguage by globalSettingsManager.appLanguage.collectAsState(AppLanguage.FOLLOW_SYSTEM)
    val openLinksInCustomTab by globalSettingsManager.openLinksInCustomTab.collectAsState(true)
    val trendingNewOnlyDefault by globalSettingsManager.trendingNewOnlyDefault.collectAsState(false)
    val defaultHomeTab by globalSettingsManager.defaultHomeTab.collectAsState(
        remember { globalSettingsManager.currentDefaultHomeTab() }
    )
    val appVersion = remember { getAppVersion() }
    val isChecking by globalUpdateChecker.isChecking.collectAsState()
    val isUpToDate by globalUpdateChecker.isUpToDate.collectAsState()
    var showSummaryLanguageDialog by remember { mutableStateOf(false) }
    // 语言采集流程：点「赞助 Pro」→ 采集期望语言（复用反馈接口提交）→ 成功后跳赞助页
    // 登录用户带上 GitHub 身份（便于与赞助对齐 + 后续通知）；未登录则收邮箱
    val authState by globalAuthManager.authState.collectAsState()
    val isLoggedIn = authState is AuthState.LoggedIn
    var showLangCaptureDialog by remember { mutableStateOf(false) }
    val dailyPicksNotificationEnabled by globalSettingsManager.dailyPicksNotificationEnabled.collectAsState(
        remember { globalSettingsManager.currentDailyPicksNotificationEnabled() }
    )
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
                    // 中性关闭按钮：只想看说明的用户有静默出口，不被迫进反馈/赞助流程
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 分组 1: 个性化
            item { SettingsHeader(stringResource(Res.string.personalization)) }
            // 外观：深色模式 + 主题色收进二级页
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.appearance)) },
                    supportingContent = { Text(themeModeText(themeMode)) },
                    leadingContent = { Icon(Icons.Default.Palette, null) },
                    modifier = Modifier.clickable {
                        trackEvent("settings_appearance")
                        onNavigateToAppearance()
                    }
                )
            }
            // 我的收藏
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.favorites)) },
                    leadingContent = { Icon(Icons.Default.Favorite, null) },
                    modifier = Modifier.clickable {
                        trackEvent("settings_favorites")
                        onNavigateToFavorites()
                    }
                )
            }
            // 邮件订阅
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.subscribe_title)) },
                    supportingContent = { Text(stringResource(Res.string.subscribe_desc)) },
                    leadingContent = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.clickable {
                        trackEvent("settings_subscribe")
                        onNavigateToSubscribe()
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // 分组 2: 应用设置
            item { SettingsHeader(stringResource(Res.string.app_settings)) }
            // 语言设置：trailing 切应用语言；行点击弹摘要语言说明（原独立条目并入此处）
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.language_settings)) },
                    supportingContent = { Text(stringResource(Res.string.summary_language_desc)) },
                    leadingContent = { Icon(Icons.Default.Language, null) },
                    trailingContent = {
                        if (isIos) {
                            Text(
                                text = stringResource(Res.string.open_system_settings),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { openAppSettings() }
                            )
                        } else {
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                Text(
                                    text = languageOptionText(appLanguage),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { expanded = true }
                                )
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    AppLanguage.entries.forEach { language ->
                                        DropdownMenuItem(
                                            text = { Text(languageOptionText(language)) },
                                            onClick = {
                                                expanded = false
                                                trackEvent("settings_language_change", mapOf("language" to language.name.lowercase()))
                                                globalSettingsManager.setLanguage(language)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        trackEvent("settings_summary_language", mapOf("app_language" to appLanguage.name.lowercase()))
                        showSummaryLanguageDialog = true
                    }
                )
            }
            // 默认首页 tab：只决定冷启动进入哪个 tab，会话内切换不回写
            item {
                var expanded by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.default_home_tab)) },
                    supportingContent = { Text(stringResource(Res.string.default_home_tab_desc)) },
                    leadingContent = { Icon(Icons.Default.Home, null) },
                    trailingContent = {
                        Box {
                            Text(
                                text = homeTabOptionText(HomeTab.fromNameOrDefault(defaultHomeTab)),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { expanded = true }
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                HomeTab.entries.forEach { tab ->
                                    DropdownMenuItem(
                                        text = { Text(homeTabOptionText(tab)) },
                                        onClick = {
                                            expanded = false
                                            trackEvent("settings_default_home_tab_change", mapOf("tab" to tab.name.lowercase()))
                                            globalSettingsManager.setDefaultHomeTab(tab.name)
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
            // 「只看 New」默认开关：只决定进入 app 时的初始状态，榜单页手动切换不回写
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.new_only_default)) },
                    supportingContent = { Text(stringResource(Res.string.new_only_default_desc)) },
                    leadingContent = { Icon(Icons.Default.FiberNew, null) },
                    trailingContent = {
                        Switch(
                            checked = trendingNewOnlyDefault,
                            onCheckedChange = { enabled ->
                                trackEvent("settings_new_only_default", mapOf("enabled" to enabled.toString()))
                                globalSettingsManager.setTrendingNewOnlyDefault(enabled)
                            }
                        )
                    }
                )
            }
            // 外链打开方式：行点击与开关同为切换，说明并入 supporting，不再弹对话框
            item {
                val toggleOpenLinks = { enabled: Boolean ->
                    trackEvent("settings_open_links_in_browser", mapOf("enabled" to enabled.toString()))
                    globalSettingsManager.setOpenLinksInCustomTab(enabled)
                }
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.open_links_in_browser)) },
                    supportingContent = { Text(stringResource(Res.string.open_links_in_browser_desc)) },
                    leadingContent = { Icon(Icons.Default.OpenInBrowser, null) },
                    trailingContent = {
                        Switch(
                            checked = openLinksInCustomTab,
                            onCheckedChange = toggleOpenLinks
                        )
                    },
                    modifier = Modifier.clickable { toggleOpenLinks(!openLinksInCustomTab) }
                )
            }
            // 每日精选提醒：本地定时通知（WorkManager），全渠道可用；iOS 未支持则隐藏
            if (globalDailyPicksNotifier.isSupported) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.daily_picks_notification)) },
                        supportingContent = { Text(stringResource(Res.string.daily_picks_notification_desc)) },
                        leadingContent = { Icon(Icons.Default.Notifications, null) },
                        trailingContent = {
                            Switch(
                                checked = dailyPicksNotificationEnabled,
                                onCheckedChange = { enabled ->
                                    trackEvent(
                                        "settings_daily_picks_notification",
                                        mapOf("enabled" to enabled.toString())
                                    )
                                    if (enabled) {
                                        settingsScope.launch {
                                            // 先确权再落设置：权限被拒时开关保持关闭，引导去系统设置
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
                        }
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // 分组 3: 关于
            item { SettingsHeader(stringResource(Res.string.about)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.feedback)) },
                    supportingContent = { Text(stringResource(Res.string.feedback_desc)) },
                    leadingContent = { Icon(Icons.Default.Feedback, null) },
                    modifier = Modifier.clickable {
                        trackEvent("settings_feedback")
                        onNavigateToFeedback()
                    }
                )
            }
            // 关于我们：官网/联系作者/捐赠/隐私政策收进二级页
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.about_us)) },
                    supportingContent = { Text(stringResource(Res.string.about_us_desc)) },
                    leadingContent = { Icon(Icons.Default.Info, null) },
                    modifier = Modifier.clickable {
                        trackEvent("settings_about")
                        onNavigateToAbout()
                    }
                )
            }
            // 版本 + 检查更新合并为一行：apk 渠道点按自建检查，iOS 跳官网，play 渠道仅展示版本号
            item {
                val canCheckUpdate = globalUpdateChecker.isEnabled || isIos
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.version)) },
                    supportingContent = if (canCheckUpdate) {
                        { Text(stringResource(Res.string.version_tap_to_check)) }
                    } else null,
                    leadingContent = { Icon(Icons.Default.Numbers, null) },
                    trailingContent = {
                        when {
                            !isIos && isChecking -> LoadingIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                            !isIos && isUpToDate -> Text(
                                stringResource(Res.string.version_up_to_date),
                                color = MaterialTheme.colorScheme.primary
                            )
                            else -> Text(appVersion, color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    modifier = if (canCheckUpdate) {
                        Modifier.clickable(enabled = !isChecking) {
                            trackEvent("settings_check_update")
                            if (isIos) {
                                uriHandler.openUri(Constants.OFFICIAL_WEBSITE_URL)
                            } else {
                                globalUpdateChecker.manualCheck()
                            }
                        }
                    } else Modifier
                )
            }
        }
    }
}

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
private fun homeTabOptionText(tab: HomeTab): String = when (tab) {
    HomeTab.GitHub -> "GitHub"
    HomeTab.HackerNews -> stringResource(Res.string.hackernews_title)
    HomeTab.ProductHunt -> stringResource(Res.string.producthunt_title)
    HomeTab.Picks -> stringResource(Res.string.picks_title)
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * 摘要语言意图采集弹窗：期望语言（+ 未登录的选填邮箱）经反馈接口提交，成功后跳赞助页。
 * 状态自持有——挂载即全新、关闭即丢弃，宿主只管 show 标记，无需在打开前手动重置各字段。
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
                    // 未登录：邮箱选填，但填了就必须格式正确
                    if (!isLoggedIn && email.isNotEmpty() && !isValidEmail(email)) {
                        langEmailInvalid = true
                        return@TextButton
                    }
                    langSubmitting = true
                    langError = null
                    scope.launch {
                        // 身份直接读 syncMe 落好的本地缓存：免一次串行 /api/me 往返；id 可能缺失（后端
                        // 兜底 username 建档时无数字 id），缺就只带 login，别写出「id null」污染赞助匹配
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
