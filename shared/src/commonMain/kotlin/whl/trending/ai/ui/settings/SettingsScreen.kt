package whl.trending.ai.ui.settings

import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.DEFAULT_SEED_ARGB
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.core.platform.isIosPlatform
import whl.trending.ai.core.platform.openAppSettings
import whl.trending.ai.core.platform.getAppVersion
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.core.Constants
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.chat.globalByokSettingsScreen
import whl.trending.ai.ui.theme.PRESET_PALETTE
import whl.trending.ai.ui.theme.ThemeSeed
import whl.trending.ai.update.globalUpdateChecker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.VolunteerActivism
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.about
import trendingai.shared.generated.resources.about_us
import trendingai.shared.generated.resources.about_us_desc
import trendingai.shared.generated.resources.confirm
import trendingai.shared.generated.resources.contact_author
import trendingai.shared.generated.resources.donate
import trendingai.shared.generated.resources.donate_alipay
import trendingai.shared.generated.resources.donate_message
import trendingai.shared.generated.resources.app_settings
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.byok_settings_desc
import trendingai.shared.generated.resources.byok_settings_title
import trendingai.shared.generated.resources.check_updates
import trendingai.shared.generated.resources.close
import trendingai.shared.generated.resources.dark_mode
import trendingai.shared.generated.resources.language_settings
import trendingai.shared.generated.resources.language_option_chinese
import trendingai.shared.generated.resources.language_option_english
import trendingai.shared.generated.resources.language_option_follow_system
import trendingai.shared.generated.resources.language_system_follow
import trendingai.shared.generated.resources.open_system_settings
import trendingai.shared.generated.resources.personalization
import trendingai.shared.generated.resources.settings
import trendingai.shared.generated.resources.theme_color
import trendingai.shared.generated.resources.theme_dark
import trendingai.shared.generated.resources.theme_follow_system
import trendingai.shared.generated.resources.theme_light

import trendingai.shared.generated.resources.favorites
import trendingai.shared.generated.resources.feedback
import trendingai.shared.generated.resources.feedback_desc
import trendingai.shared.generated.resources.privacy_policy
import trendingai.shared.generated.resources.subscribe_title
import trendingai.shared.generated.resources.subscribe_desc
import trendingai.shared.generated.resources.summary_language
import trendingai.shared.generated.resources.summary_language_desc
import trendingai.shared.generated.resources.summary_language_feedback
import trendingai.shared.generated.resources.summary_language_message
import trendingai.shared.generated.resources.version
import trendingai.shared.generated.resources.version_up_to_date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToSubscribe: () -> Unit = {},
    onNavigateToByokSettings: () -> Unit = {},
    onNavigateToWebPage: (url: String, title: String) -> Unit = { _, _ -> }
) {
    val isIos = isIosPlatform()
    val themeMode by globalSettingsManager.themeMode.collectAsState(ThemeMode.FOLLOW_SYSTEM)
    val seedColor by globalSettingsManager.seedColor.collectAsState(DEFAULT_SEED_ARGB)
    val appLanguage by globalSettingsManager.appLanguage.collectAsState(AppLanguage.FOLLOW_SYSTEM)
    val appVersion = remember { getAppVersion() }
    val isChecking by globalUpdateChecker.isChecking.collectAsState()
    val isUpToDate by globalUpdateChecker.isUpToDate.collectAsState()
    var showDonateDialog by remember { mutableStateOf(false) }
    var showSummaryLanguageDialog by remember { mutableStateOf(false) }

    if (showSummaryLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showSummaryLanguageDialog = false },
            title = { Text(stringResource(Res.string.summary_language)) },
            text = { Text(stringResource(Res.string.summary_language_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSummaryLanguageDialog = false
                    trackEvent("settings_summary_language_feedback")
                    onNavigateToFeedback()
                }) {
                    Text(stringResource(Res.string.summary_language_feedback))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSummaryLanguageDialog = false }) {
                    Text(stringResource(Res.string.close))
                }
            }
        )
    }

    if (showDonateDialog) {
        AlertDialog(
            onDismissRequest = { showDonateDialog = false },
            title = { Text(stringResource(Res.string.donate)) },
            text = {
                SelectionContainer {
                    Column {
                        Text(stringResource(Res.string.donate_message))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(Res.string.donate_alipay, Constants.ALIPAY_ACCOUNT))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDonateDialog = false }) {
                    Text(stringResource(Res.string.confirm))
                }
            }
        )
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 分组 1: 个性化
            item { SettingsHeader(stringResource(Res.string.personalization)) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(Res.string.dark_mode),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            val labelRes = when (mode) {
                                ThemeMode.FOLLOW_SYSTEM -> Res.string.theme_follow_system
                                ThemeMode.LIGHT -> Res.string.theme_light
                                ThemeMode.DARK -> Res.string.theme_dark
                            }
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = {
                                    trackEvent("settings_theme_change", mapOf("theme" to mode.name.lowercase()))
                                    globalSettingsManager.setThemeMode(mode)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size
                                ),
                                label = {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.ColorLens,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(Res.string.theme_color),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                    SwatchGrid(
                        selected = seedColor,
                        onSelect = { seed ->
                            trackEvent("settings_seed_color", mapOf("seed" to seed.id))
                            globalSettingsManager.setSeedColor(seed.argb)
                        }
                    )
                }
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
            item {
                if (isIos) {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.language_settings)) },
                        supportingContent = { Text(stringResource(Res.string.language_system_follow)) },
                        trailingContent = {
                            Text(
                                text = stringResource(Res.string.open_system_settings),
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Language, null) },
                        modifier = Modifier.clickable { openAppSettings() }
                    )
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.language_settings)) },
                        trailingContent = {
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
                        },
                        leadingContent = { Icon(Icons.Default.Language, null) }
                    )
                }
            }
            // 摘要语言说明
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.summary_language)) },
                    supportingContent = { Text(stringResource(Res.string.summary_language_desc)) },
                    leadingContent = { Icon(Icons.Default.Translate, null) },
                    modifier = Modifier.clickable {
                        trackEvent("settings_summary_language", mapOf("app_language" to appLanguage.name.lowercase()))
                        showSummaryLanguageDialog = true
                    }
                )
            }
            // 自定义 AI 模型（BYOK）——仅 Android 注册了设置页 slot 时显示
            if (globalByokSettingsScreen != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.byok_settings_title)) },
                        supportingContent = { Text(stringResource(Res.string.byok_settings_desc)) },
                        leadingContent = { Icon(Icons.Default.SmartToy, null) },
                        modifier = Modifier.clickable {
                            trackEvent("settings_byok")
                            onNavigateToByokSettings()
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
            // apk 渠道显示自建更新检查，iOS 跳转官网；play 渠道由商店管理更新，不显示
            if (globalUpdateChecker.isEnabled || isIos) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.check_updates)) },
                        trailingContent = {
                            when {
                                !isIos && isChecking -> LoadingIndicator(
                                    modifier = Modifier.size(24.dp)
                                )
                                !isIos && isUpToDate -> Text(
                                    stringResource(Res.string.version_up_to_date),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Refresh, null) },
                        modifier = Modifier.clickable(enabled = !isChecking) {
                            trackEvent("settings_check_update")
                            if (isIos) {
                                openUrl(Constants.OFFICIAL_WEBSITE_URL)
                            } else {
                                globalUpdateChecker.manualCheck()
                            }
                        }
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.about_us)) },
                    supportingContent = { Text(stringResource(Res.string.about_us_desc)) },
                    leadingContent = { Icon(Icons.Default.Info, null) },
                    modifier = Modifier.clickable {
                        openUrl(Constants.OFFICIAL_WEBSITE_URL)
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.contact_author)) },
                    trailingContent = {
                        SelectionContainer {
                            Text(Constants.AUTHOR_EMAIL, color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    leadingContent = { Icon(Icons.Default.AlternateEmail, null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.donate)) },
                    leadingContent = { Icon(Icons.Default.VolunteerActivism, null) },
                    modifier = Modifier.clickable {
                        trackEvent("settings_donate")
                        showDonateDialog = true
                    }
                )
            }
            item {
                val privacyTitle = stringResource(Res.string.privacy_policy)
                ListItem(
                    headlineContent = { Text(privacyTitle) },
                    leadingContent = { Icon(Icons.Default.PrivacyTip, null) },
                    modifier = Modifier.clickable {
                        onNavigateToWebPage(Constants.PRIVACY_POLICY_URL, privacyTitle)
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.version)) },
                    trailingContent = {
                        Text(appVersion, color = MaterialTheme.colorScheme.outline)
                    },
                    leadingContent = { Icon(Icons.Default.Numbers, null) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchGrid(
    selected: Long,
    onSelect: (ThemeSeed) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PRESET_PALETTE.forEach { seed ->
            ThemeSwatch(
                seed = seed,
                selected = seed.argb == selected,
                onClick = { onSelect(seed) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSwatch(
    seed: ThemeSeed,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = Color(seed.argb)
    val name = stringResource(seed.nameRes)
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = color,
        modifier = Modifier
            .size(40.dp)
            .semantics {
                contentDescription = name
                role = Role.RadioButton
            },
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (color.luminance() < 0.5f) Color.White else Color.Black,
                )
            }
        }
    }
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
