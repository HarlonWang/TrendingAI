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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import whl.trending.ai.ui.common.LocalContentBottomPadding
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.account_github_entry
import trendingai.shared.generated.resources.account_github_entry_desc
import trendingai.shared.generated.resources.account_link_github
import trendingai.shared.generated.resources.account_link_github_desc
import trendingai.shared.generated.resources.account_link_required_message
import trendingai.shared.generated.resources.account_link_required_title
import trendingai.shared.generated.resources.account_link_sponsor_anyway
import trendingai.shared.generated.resources.account_plan_title
import trendingai.shared.generated.resources.account_pro_active
import trendingai.shared.generated.resources.account_signed_out_prompt
import trendingai.shared.generated.resources.account_signed_out_title
import trendingai.shared.generated.resources.account_signin_for_more
import trendingai.shared.generated.resources.account_tier_free
import trendingai.shared.generated.resources.account_upgrade_cta
import trendingai.shared.generated.resources.account_upgrade_hint
import trendingai.shared.generated.resources.cancel
import trendingai.shared.generated.resources.favorites
import trendingai.shared.generated.resources.profile_load_failed
import trendingai.shared.generated.resources.profile_quota_error
import trendingai.shared.generated.resources.profile_quota_exhausted
import trendingai.shared.generated.resources.profile_quota_used
import trendingai.shared.generated.resources.profile_quota_reset_hours
import trendingai.shared.generated.resources.profile_quota_reset_soon
import trendingai.shared.generated.resources.profile_followers
import trendingai.shared.generated.resources.profile_repos
import trendingai.shared.generated.resources.profile_retry
import trendingai.shared.generated.resources.sign_in
import trendingai.shared.generated.resources.sign_out
import trendingai.shared.generated.resources.sign_out_confirm
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.core.AccountLink
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.QuotaResponse

/**
 * 个人中心：只讲「你是谁、你还剩多少额度、你收藏了什么」。
 *
 * 从上到下：身份区（含未登录引导）→ 套餐 & 用量（含升级 CTA）→ GitHub 主页入口卡
 * （仅 GitHub 用户）→ 收藏 → 退出登录。
 *
 * 应用偏好全部在 [SettingsScreen]，入口挂在底栏的「⋯」扩展菜单上，本页不再重复。
 *
 * 本页是「我的」tab 的内容，不自带脚手架与顶栏：顶栏由 [HomeScreen] 统一提供，
 * 底栏要一直在，页面内套 Scaffold 会出现两层顶栏。
 *
 * 未登录用户同样可达：身份区显示登录引导、用量区显示匿名额度——不像旧个人主页那样
 * 对邮箱/匿名用户只剩空壳。GitHub 开发者档案（贡献图 + 动态流）降为
 * [GithubProfileScreen] 子页，动态数据由共享的 [ProfileViewModel] 承载。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateToGithubProfile: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
) {
    val viewModel: ProfileViewModel = viewModel { ProfileViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    // 未接入登录的平台（iOS NoopAuthManager）：身份/额度/GitHub/登录都无意义，
    // 隐藏这些动态区块，本页退化成「收藏 + 设置 + 关于」三个入口。
    val authSupported = globalAuthManager.isSupported
    LaunchedEffect(Unit) { if (authSupported) viewModel.load() }

    val isPro by globalSettingsManager.isPro.collectAsState(
        initial = globalSettingsManager.currentIsPro()
    )

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showLinkGithubDialog by remember { mutableStateOf(false) }

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

    // 邮箱用户点「升级 Pro」时的拦截：Pro 权益以 GitHub 账户为发放主体，不先关联就会
    // 「钱付了但权益对不上」。次按钮保留直接前往赞助页——有人只是想单纯支持，不图权益。
    if (showLinkGithubDialog) {
        AlertDialog(
            onDismissRequest = { showLinkGithubDialog = false },
            title = { Text(stringResource(Res.string.account_link_required_title)) },
            text = { Text(stringResource(Res.string.account_link_required_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLinkGithubDialog = false
                    AccountLink.openLinkGithubPage(AccountLink.SOURCE_UPGRADE_DIALOG)
                }) {
                    Text(stringResource(Res.string.account_link_github))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLinkGithubDialog = false
                    ProSponsor.openSponsorPage(ProSponsor.SOURCE_ACCOUNT)
                }) {
                    Text(stringResource(Res.string.account_link_sponsor_anyway))
                }
            },
        )
    }

    PullToRefreshBox(
        // 未接入登录的平台（iOS）：身份/额度区块都已隐藏，下拉刷新会拉
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
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalContentBottomPadding.current),
        ) {
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
                        onUpgrade = {
                            // 没有 GitHub 身份就直奔赞助页 = 让用户白花钱，先引导关联
                            if (uiState.user?.githubUserId == null) showLinkGithubDialog = true
                            else ProSponsor.openSponsorPage(ProSponsor.SOURCE_ACCOUNT)
                        },
                        onSignIn = { globalAuthManager.signIn("account_hub") },
                    )
                }

                // ③ GitHub 区：已关联给主页入口，未关联给关联入口。
                // 两处条件刻意不对称——githubUserId 是「是否已关联」的权威（Pro 判定同源），
                // githubLogin 才是能展示的名字。中间态（有 id 无 login，资料未同步）两块都不显示，
                // 好过让已关联的用户看到「关联 GitHub」或让主页卡显示 @null。
                if (uiState.user?.githubLogin != null) {
                    item(key = "github_entry") {
                        GithubEntryCard(uiState = uiState, onClick = onNavigateToGithubProfile)
                    }
                } else if (uiState.loggedIn && uiState.user?.githubUserId == null) {
                    item(key = "link_github") {
                        LinkGithubCard(onClick = {
                            AccountLink.openLinkGithubPage(AccountLink.SOURCE_ACCOUNT)
                        })
                    }
                }

                item(key = "divider_top") { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }

            // ④ 收藏入口。设置与关于我们不在这里——它们挂在底栏的「⋯」扩展菜单上，
                // 两处都放会变成同一个入口的两个按钮。
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
                    // 只讲「用掉了多大比例」和「何时重置」，不透出余额绝对值与单价：
                    // 用户无从算计单价，后端调价/调额度也不必跟着改文案（改了就会说谎）。
                    val usedRatio = remember(quota.balance, quota.dailyGrant) {
                        if (quota.dailyGrant <= 0) 0f
                        else ((quota.dailyGrant - quota.balance).toFloat() / quota.dailyGrant)
                            .coerceIn(0f, 1f)
                    }
                    val exhausted = quota.balance <= 0
                    Text(
                        if (exhausted) stringResource(Res.string.profile_quota_exhausted)
                        else stringResource(
                            Res.string.profile_quota_used,
                            "${(usedRatio * 100).roundToInt()}%",
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // 加粗到 8dp（默认 4dp）。振幅用组件默认函数：≤10% 与 ≥95% 自动收平成
                    // 直线——逼近上限时正好是红色直线，比红色波浪更像警示，不必自定义。
                    val barStroke = with(LocalDensity.current) {
                        Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    }
                    LinearWavyProgressIndicator(
                        // 走「已用」方向：没用过时是空条（视觉最轻），越用越长；
                        // 逼近上限才转 error 红，让「该升级了」的信号在需要时自己浮现。
                        progress = { usedRatio },
                        color = if (usedRatio >= 0.9f) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary,
                        stroke = barStroke,
                        trackStroke = barStroke,
                        // 保持默认容器高度：振幅按容器高度等比放大，撑高后波形会夸张到喧宾夺主
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 每分钟自算：resetAt 一整天恒定，用它做 remember key 会把小时数冻在进页面
                    // 那一刻（停留久了显示偏大，且下拉刷新也救不回来——quota 内容不变时本卡片
                    // 整个被 skip）。produceState 自带 state，重组由它自己触发。
                    val resetHours by produceState(
                        initialValue = DateTimeUtils.hoursUntil(quota.resetAt),
                        key1 = quota.resetAt,
                    ) {
                        while (true) {
                            value = DateTimeUtils.hoursUntil(quota.resetAt)
                            delay(1.minutes)
                        }
                    }
                    // 委托属性不能智能转换，先落到局部 val
                    val hours = resetHours
                    val resetText = when {
                        hours == null -> null
                        hours <= 1 -> stringResource(Res.string.profile_quota_reset_soon)
                        else -> stringResource(Res.string.profile_quota_reset_hours, hours)
                    }
                    if (resetText != null) {
                        Text(
                            resetText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

/**
 * 关联 GitHub 入口卡（仅无 GitHub 身份的用户可见）。与 [GithubEntryCard] 同一套列表行语言，
 * 占据它的位置——一个账户在这里要么是「已连接的 GitHub」，要么是「去连接」。
 */
@Composable
private fun LinkGithubCard(onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.account_link_github)) },
        supportingContent = {
            Text(
                stringResource(Res.string.account_link_github_desc),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp))
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
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
