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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import trendingai.shared.generated.resources.about_us
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
import trendingai.shared.generated.resources.app_settings
import trendingai.shared.generated.resources.app_settings_entry_desc
import trendingai.shared.generated.resources.appearance
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.cancel
import trendingai.shared.generated.resources.daily_picks_notification
import trendingai.shared.generated.resources.favorites
import trendingai.shared.generated.resources.feedback
import trendingai.shared.generated.resources.notification_permission_denied
import trendingai.shared.generated.resources.open_system_settings
import trendingai.shared.generated.resources.personalization
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
import trendingai.shared.generated.resources.settings_group_general
import trendingai.shared.generated.resources.sign_in
import trendingai.shared.generated.resources.sign_out
import trendingai.shared.generated.resources.sign_out_confirm
import trendingai.shared.generated.resources.subscribe_title
import trendingai.shared.generated.resources.subscription_reminders
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.core.platform.openAppSettings
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.QuotaResponse
import whl.trending.ai.notification.globalDailyPicksNotifier
import whl.trending.ai.ui.settings.themeModeText

/**
 * 账户中心（统一 Hub）：应用「关于我」的唯一入口，替代原独立的个人主页 + 设置页。
 *
 * 从上到下：身份区（含未登录引导）→ 套餐 & 用量（含升级 CTA）→ GitHub 主页入口卡
 * （仅 GitHub 用户）→ 设置分组（个性化 / 订阅提醒 / 通用）→ 退出登录。
 * 低频应用偏好（语言/默认首页/打开方式）折叠进「应用设置」子页 [AppSettingsScreen]。
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
    onNavigateToAppSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
) {
    val viewModel: ProfileViewModel = viewModel { ProfileViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    // 未接入登录的平台（iOS NoopAuthManager）：Hub 退化为纯设置页——身份/额度/GitHub/登录
    // 都无意义，隐藏这些动态区块，只留设置分组（等同旧的独立设置页）。
    val authSupported = globalAuthManager.isSupported
    LaunchedEffect(Unit) { if (authSupported) viewModel.load() }

    val isPro by globalSettingsManager.isPro.collectAsState(
        initial = globalSettingsManager.currentIsPro()
    )
    val themeMode by globalSettingsManager.themeMode.collectAsState(ThemeMode.FOLLOW_SYSTEM)
    val dailyPicksNotificationEnabled by globalSettingsManager.dailyPicksNotificationEnabled.collectAsState(
        remember { globalSettingsManager.currentDailyPicksNotificationEnabled() }
    )

    var showSignOutDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val settingsScope = rememberCoroutineScope()
    val permissionDeniedMsg = stringResource(Res.string.notification_permission_denied)
    val openSystemSettingsLabel = stringResource(Res.string.open_system_settings)
    val pullToRefreshState = rememberPullToRefreshState()

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
                        leadingContent = { Icon(Icons.Default.Palette, null) },
                        trailingContent = {
                            Text(
                                text = themeModeText(themeMode),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
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

                // 通用：应用设置（低频偏好折叠进子页）+ 反馈 + 关于
                item(key = "group_general") { SettingsHeader(stringResource(Res.string.settings_group_general)) }
                item(key = "app_settings_entry") {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.app_settings)) },
                        supportingContent = { Text(stringResource(Res.string.app_settings_entry_desc)) },
                        leadingContent = { Icon(Icons.Default.Tune, null) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            trackEvent("settings_app_settings")
                            onNavigateToAppSettings()
                        }
                    )
                }
                item(key = "feedback") {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.feedback)) },
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
                        // 用 secondary 而非 primary：满格时整条主题色过重，抢走卡片焦点
                        color = MaterialTheme.colorScheme.secondary,
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

            // CTA：Pro 致谢 / 匿名登录引导。Free 登录态的升级入口在卡片底部独立成行（见下）
            when {
                isPro -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(Res.string.account_pro_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !loggedIn -> {
                    Spacer(Modifier.height(4.dp))
                    FilledTonalButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.account_signin_for_more))
                    }
                }
            }
        }

        // Free 登录态的升级入口：卡片底部整行可点，与「GitHub 主页」同一套列表行语言。
        // 刻意不做填充按钮——带动词的行 + 尾部箭头已足够表达动作，视觉权重却低两档。
        if (loggedIn && !isPro) {
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(Res.string.account_upgrade_cta)) },
                supportingContent = {
                    // 限一行：换行会把这行撑得比「GitHub 主页」还高，反而重新变显眼
                    Text(
                        stringResource(Res.string.account_upgrade_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                colors = ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.clickable(onClick = onUpgrade),
            )
        }
    }
}

/**
 * Free 档小徽章（与金色 ProBadge 区分，采用中性容器色）。
 * 纯状态标识、不可点：pill 表达「你现在处于什么档」，动作交给卡片底部那行升级入口。
 */
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
