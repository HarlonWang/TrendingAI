package whl.trending.ai.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.about
import trendingai.shared.generated.resources.about_us
import trendingai.shared.generated.resources.about_us_desc
import trendingai.shared.generated.resources.account_github_entry
import trendingai.shared.generated.resources.account_github_entry_desc
import trendingai.shared.generated.resources.account_plan_title
import trendingai.shared.generated.resources.account_pro_active
import trendingai.shared.generated.resources.account_signed_out_prompt
import trendingai.shared.generated.resources.account_signed_out_title
import trendingai.shared.generated.resources.account_signin_for_more
import trendingai.shared.generated.resources.account_tier_free
import trendingai.shared.generated.resources.account_title
import trendingai.shared.generated.resources.account_upgrade_cta
import trendingai.shared.generated.resources.account_upgrade_hint
import trendingai.shared.generated.resources.app_language_desc
import trendingai.shared.generated.resources.app_settings
import trendingai.shared.generated.resources.appearance
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.cancel
import trendingai.shared.generated.resources.close
import trendingai.shared.generated.resources.daily_picks_notification
import trendingai.shared.generated.resources.daily_picks_notification_desc
import trendingai.shared.generated.resources.default_home_tab
import trendingai.shared.generated.resources.default_home_tab_desc
import trendingai.shared.generated.resources.favorites
import trendingai.shared.generated.resources.feedback
import trendingai.shared.generated.resources.feedback_desc
import trendingai.shared.generated.resources.feedback_email_invalid
import trendingai.shared.generated.resources.feedback_email_placeholder
import trendingai.shared.generated.resources.feedback_error
import trendingai.shared.generated.resources.feedback_rate_limit
import trendingai.shared.generated.resources.hackernews_title
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
import trendingai.shared.generated.resources.producthunt_title
import trendingai.shared.generated.resources.profile_load_failed
import trendingai.shared.generated.resources.profile_quota_error
import trendingai.shared.generated.resources.profile_quota_rates
import trendingai.shared.generated.resources.profile_quota_remaining
import trendingai.shared.generated.resources.profile_quota_reset_hours
import trendingai.shared.generated.resources.profile_quota_reset_soon
import trendingai.shared.generated.resources.profile_followers
import trendingai.shared.generated.resources.profile_repos
import trendingai.shared.generated.resources.profile_retry
import trendingai.shared.generated.resources.settings
import trendingai.shared.generated.resources.sign_in
import trendingai.shared.generated.resources.sign_out
import trendingai.shared.generated.resources.sign_out_confirm
import trendingai.shared.generated.resources.subscribe_desc
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
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.DateTimeUtils
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
import whl.trending.ai.data.model.QuotaResponse
import whl.trending.ai.data.remote.ApiException
import whl.trending.ai.data.repository.TrendingRepository
import whl.trending.ai.notification.globalDailyPicksNotifier
import whl.trending.ai.ui.home.HomeTab
import whl.trending.ai.ui.settings.themeModeText

/**
 * 账户中心（统一 Hub）：应用「关于我」的唯一入口，替代原独立的个人主页 + 设置页。
 *
 * 从上到下：身份区（含未登录引导）→ 套餐 & 用量（含升级 CTA）→ GitHub 主页入口卡
 * （仅 GitHub 用户）→ 设置分组（个性化 / 订阅提醒 / 应用 / 关于）→ 退出登录。
 *
 * 未登录用户同样可达：身份区显示登录引导、用量区显示匿名额度、设置分组照常可用——
 * 不再像旧个人主页那样对邮箱/匿名用户只剩空壳。GitHub 开发者档案（贡献图 + 动态流）
 * 降为 [GithubProfileScreen] 子页，动态数据由共享的 [ProfileViewModel] 承载。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onNavigateToGithubProfile: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToSubscribe: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    val viewModel: ProfileViewModel = viewModel { ProfileViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    // 未接入登录的平台（iOS NoopAuthManager）：Hub 退化为纯设置页——身份/额度/GitHub/登录
    // 都无意义，隐藏这些动态区块，只留设置分组（等同旧的独立设置页）。
    val authSupported = globalAuthManager.isSupported
    LaunchedEffect(Unit) { if (authSupported) viewModel.load() }

    val isIos = isIosPlatform()
    val authState by globalAuthManager.authState.collectAsState()
    val isLoggedIn = authState is AuthState.LoggedIn
    val isPro by globalSettingsManager.isPro.collectAsState(
        initial = globalSettingsManager.currentIsPro()
    )

    val appLanguage by globalSettingsManager.appLanguage.collectAsState(AppLanguage.FOLLOW_SYSTEM)
    val summaryLanguage by globalSettingsManager.summaryLanguage.collectAsState(SummaryLanguage.FOLLOW_SYSTEM)
    val themeMode by globalSettingsManager.themeMode.collectAsState(ThemeMode.FOLLOW_SYSTEM)
    val openLinksInCustomTab by globalSettingsManager.openLinksInCustomTab.collectAsState(true)
    val defaultHomeTab by globalSettingsManager.defaultHomeTab.collectAsState(
        remember { globalSettingsManager.currentDefaultHomeTab() }
    )
    val dailyPicksNotificationEnabled by globalSettingsManager.dailyPicksNotificationEnabled.collectAsState(
        remember { globalSettingsManager.currentDailyPicksNotificationEnabled() }
    )

    var showSummaryLanguageDialog by remember { mutableStateOf(false) }
    var showLangCaptureDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val settingsScope = rememberCoroutineScope()
    val permissionDeniedMsg = stringResource(Res.string.notification_permission_denied)
    val openSystemSettingsLabel = stringResource(Res.string.open_system_settings)
    val pullToRefreshState = rememberPullToRefreshState()

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

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(Res.string.sign_out)) },
            text = { Text(stringResource(Res.string.sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    // 停留在 Hub：登出后 VM 监听 authState 落回匿名态（身份区显示登录引导）
                    viewModel.signOut()
                }) {
                    Text(stringResource(Res.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (authSupported) Res.string.account_title else Res.string.settings))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            // 未接入登录的平台（iOS）：Hub 是纯设置页，身份/额度区块都已隐藏，下拉刷新会拉
            // profile/quota 却无任何可见变化——短路成 no-op，避免无谓网络开销。
            isRefreshing = authSupported && uiState.isRefreshing,
            state = pullToRefreshState,
            onRefresh = { if (authSupported) viewModel.refresh() },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (authSupported) {
                    // ① 身份区
                    item(key = "account_header") {
                        AccountHeader(
                            uiState = uiState,
                            isPro = isPro,
                            onSignIn = { globalAuthManager.signIn("account_hub") },
                            onRetry = { viewModel.load() },
                        )
                    }

                    // ② 套餐 & 用量
                    item(key = "plan_card") {
                        PlanUsageCard(
                            quota = uiState.quota,
                            quotaError = uiState.quotaError,
                            loggedIn = uiState.loggedIn,
                            isPro = isPro,
                            onUpgrade = { ProSponsor.openSponsorPage(ProSponsor.SOURCE_ACCOUNT) },
                            onSignIn = { globalAuthManager.signIn("account_hub") },
                        )
                    }

                    // ③ GitHub 主页入口卡（仅 GitHub 用户）
                    if (uiState.user?.githubLogin != null) {
                        item(key = "github_entry") {
                            GithubEntryCard(uiState = uiState, onClick = onNavigateToGithubProfile)
                        }
                    }

                    item(key = "divider_top") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }

                // ④ 设置分组：个性化
                item(key = "group_personalization") { SettingsHeader(stringResource(Res.string.personalization)) }
                item(key = "appearance") {
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
                item(key = "favorites") {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.favorites)) },
                        leadingContent = { Icon(Icons.Default.Favorite, null) },
                        modifier = Modifier.clickable {
                            trackEvent("settings_favorites")
                            onNavigateToFavorites()
                        }
                    )
                }
                item(key = "divider_1") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                // 订阅提醒
                item(key = "group_subscription") { SettingsHeader(stringResource(Res.string.subscription_reminders)) }
                item(key = "subscribe") {
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
                if (globalDailyPicksNotifier.isSupported) {
                    item(key = "daily_picks_notification") {
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
                item(key = "divider_2") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                // 应用设置
                item(key = "group_app") { SettingsHeader(stringResource(Res.string.app_settings)) }
                item(key = "app_language") {
                    var expanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.language_settings)) },
                        supportingContent = { Text(stringResource(Res.string.app_language_desc)) },
                        leadingContent = { Icon(Icons.Default.Language, null) },
                        trailingContent = {
                            if (isIos) {
                                Text(
                                    text = stringResource(Res.string.open_system_settings),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { openAppSettings() }
                                )
                            } else {
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
                            if (isIos) openAppSettings() else expanded = true
                        }
                    )
                }
                item(key = "summary_language") {
                    var expanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.summary_language)) },
                        supportingContent = { Text(stringResource(Res.string.summary_language_desc)) },
                        leadingContent = { Icon(Icons.Default.Translate, null) },
                        trailingContent = {
                            Box {
                                Text(
                                    text = languageOptionText(summaryLanguage),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { expanded = true }
                                )
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    SummaryLanguage.entries.forEach { language ->
                                        DropdownMenuItem(
                                            text = { Text(languageOptionText(language)) },
                                            onClick = {
                                                expanded = false
                                                trackEvent("settings_summary_language_change", mapOf("language" to language.name.lowercase()))
                                                globalSettingsManager.setSummaryLanguage(language)
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            trackEvent("settings_summary_language", mapOf("summary_language" to summaryLanguage.name.lowercase()))
                            showSummaryLanguageDialog = true
                        }
                    )
                }
                item(key = "default_home_tab") {
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
                item(key = "open_links") {
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
                item(key = "divider_3") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

                // 关于
                item(key = "group_about") { SettingsHeader(stringResource(Res.string.about)) }
                item(key = "feedback") {
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
                item(key = "about_us") {
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

                // ⑤ 退出登录（仅登录用户）
                if (uiState.loggedIn) {
                    item(key = "sign_out") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(Res.string.sign_out),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            modifier = Modifier.clickable { showSignOutDialog = true }
                        )
                    }
                }
                item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** 身份区：未登录显示登录引导；登录态显示头像/名/邮箱 + Pro 徽章；加载/错误各有内联态。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountHeader(
    uiState: ProfileUiState,
    isPro: Boolean,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val user = uiState.user
        when {
            user != null -> {
                if (user.avatarUrl != null) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).clip(CircleShape),
                    )
                } else {
                    val initial = (user.displayName ?: user.email ?: "?")
                        .firstOrNull()?.uppercase() ?: "?"
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initial,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                val titleText = user.displayName ?: user.githubLogin ?: user.email.orEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    Text(
                        titleText,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isPro) ProBadge()
                }
                // 登录邮箱：仅当标题未回落到邮箱时才单独展示，避免头部重复
                user.email?.takeIf { it.isNotBlank() && it != titleText }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            uiState.isLoading -> {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(40.dp))
                }
            }

            uiState.isError -> {
                Text(stringResource(Res.string.profile_load_failed))
                Button(onClick = onRetry) { Text(stringResource(Res.string.profile_retry)) }
            }

            else -> {
                // 未登录：登录引导
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(Res.string.account_signed_out_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(Res.string.account_signed_out_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSignIn) { Text(stringResource(Res.string.sign_in)) }
            }
        }
    }
}

/**
 * 套餐 & 用量卡：档位徽章 + 余额进度 + 重置倒计时 + 费率；底部按状态给不同 CTA
 * （匿名→登录引导 / Free 登录→升级 Pro / Pro→致谢）。数据与整页解耦，失败只降级本卡。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlanUsageCard(
    quota: QuotaResponse?,
    quotaError: Boolean,
    loggedIn: Boolean,
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onSignIn: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.account_plan_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.weight(1f))
                if (isPro) ProBadge() else if (loggedIn) TierPillFree()
            }

            when {
                quota != null -> {
                    Text(
                        stringResource(Res.string.profile_quota_remaining, quota.balance, quota.dailyGrant),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (quota.dailyGrant <= 0) 0f
                            else (quota.balance.toFloat() / quota.dailyGrant).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val resetHours = remember(quota.resetAt) { DateTimeUtils.hoursUntil(quota.resetAt) }
                    val resetText = when {
                        resetHours == null -> null
                        resetHours <= 1 -> stringResource(Res.string.profile_quota_reset_soon)
                        else -> stringResource(Res.string.profile_quota_reset_hours, resetHours)
                    }
                    Text(
                        listOfNotNull(resetText, stringResource(Res.string.profile_quota_rates))
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                quotaError -> Text(
                    stringResource(Res.string.profile_quota_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { LoadingIndicator(modifier = Modifier.size(24.dp)) }
            }

            // CTA
            Spacer(Modifier.height(4.dp))
            when {
                isPro -> Text(
                    stringResource(Res.string.account_pro_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                loggedIn -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.account_upgrade_cta))
                    }
                    Text(
                        stringResource(Res.string.account_upgrade_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> FilledTonalButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.account_signin_for_more))
                }
            }
        }
    }
}

/** Free 档小徽章（与金色 ProBadge 区分，采用中性容器色）。 */
@Composable
private fun TierPillFree() {
    Text(
        text = stringResource(Res.string.account_tier_free),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** GitHub 主页入口卡：头像 + 名称 + 计数摘要，点击进 [GithubProfileScreen]。 */
@Composable
private fun GithubEntryCard(uiState: ProfileUiState, onClick: () -> Unit) {
    val user = uiState.user ?: return
    val gh = uiState.githubUser
    val summary = if (gh != null) {
        buildString {
            append("@${user.githubLogin}")
            append(" · ")
            append(DateTimeUtils.formatNumber(gh.followers))
            append(" ")
            append(stringResource(Res.string.profile_followers))
            append(" · ")
            append(DateTimeUtils.formatNumber(gh.publicRepos))
            append(" ")
            append(stringResource(Res.string.profile_repos))
        }
    } else {
        stringResource(Res.string.account_github_entry_desc)
    }
    ListItem(
        headlineContent = { Text(stringResource(Res.string.account_github_entry)) },
        supportingContent = { Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            if (user.avatarUrl != null) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp))
            }
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable { onClick() },
    )
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
    HomeTab.GitHub -> "GitHub"
    HomeTab.HackerNews -> stringResource(Res.string.hackernews_title)
    HomeTab.ProductHunt -> stringResource(Res.string.producthunt_title)
    HomeTab.Picks -> stringResource(Res.string.picks_title)
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
