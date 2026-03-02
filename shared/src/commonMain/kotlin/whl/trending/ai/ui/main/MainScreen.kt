package whl.trending.ai.ui.main

import whl.trending.ai.data.model.TrendingRepo
import whl.trending.ai.data.model.TrendingAiSummary
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.core.Constants
import whl.trending.ai.core.DateTimeUtils

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import trending.shared.generated.resources.GitHub_Invertocat_Black
import trending.shared.generated.resources.GitHub_Invertocat_White
import trending.shared.generated.resources.Res
import trending.shared.generated.resources.action_help
import trending.shared.generated.resources.app_name
import trending.shared.generated.resources.batch_am
import trending.shared.generated.resources.batch_pm
import trending.shared.generated.resources.cancel
import trending.shared.generated.resources.click_to_select_date
import trending.shared.generated.resources.confirm
import trending.shared.generated.resources.error_fetch
import trending.shared.generated.resources.filter_ai_provider
import trending.shared.generated.resources.filter_done
import trending.shared.generated.resources.filter_language
import trending.shared.generated.resources.filter_options
import trending.shared.generated.resources.filter_period
import trending.shared.generated.resources.filter_reset
import trending.shared.generated.resources.history_batch
import trending.shared.generated.resources.history_date
import trending.shared.generated.resources.history_info_content
import trending.shared.generated.resources.history_info_title
import trending.shared.generated.resources.history_trending
import trending.shared.generated.resources.icon_deepseek_dark
import trending.shared.generated.resources.icon_deepseek_light
import trending.shared.generated.resources.icon_gemini_dark
import trending.shared.generated.resources.icon_gemini_light
import trending.shared.generated.resources.icon_openai_dark
import trending.shared.generated.resources.icon_openai_light
import trending.shared.generated.resources.last_updated
import trending.shared.generated.resources.no_data
import trending.shared.generated.resources.retry
import trending.shared.generated.resources.select_date
import trending.shared.generated.resources.settings
import trending.shared.generated.resources.stars_total
import trending.shared.generated.resources.stars_period
import trending.shared.generated.resources.period_daily
import trending.shared.generated.resources.period_monthly
import trending.shared.generated.resources.period_weekly
import trending.shared.generated.resources.update_info_content
import trending.shared.generated.resources.update_info_title

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (owner: String, repo: String) -> Unit,
    viewModel: MainViewModel = viewModel { MainViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TrendingTopBar(
                selectedPeriod = uiState.selectedPeriod,
                selectedLanguage = uiState.selectedLanguage,
                selectedDate = uiState.selectedDate,
                selectedBatch = uiState.selectedBatch,
                scrollBehavior = scrollBehavior,
                onTitleClick = { showFilterSheet = true },
                onHistoryClick = { showHistorySheet = true },
                onNavigateToSettings = onNavigateToSettings
            )
        },
    ) { innerPadding ->
        RepoList(
            uiState = uiState,
            modifier = Modifier.padding(innerPadding),
            onRefresh = { viewModel.fetchData(isRefresh = true) },
            onNavigateToDetail = onNavigateToDetail
        )
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            selectedPeriod = uiState.selectedPeriod,
            selectedLanguage = uiState.selectedLanguage,
            selectedProviders = uiState.selectedProviders,
            onDismiss = { showFilterSheet = false },
            onConfirm = { period, language, providers ->
                viewModel.updateFilter(period, language, providers)
                showFilterSheet = false
            }
        )
    }

    if (showHistorySheet) {
        HistoryBottomSheet(
            selectedDate = uiState.selectedDate,
            selectedBatch = uiState.selectedBatch,
            onDismiss = { showHistorySheet = false },
            onConfirm = { date, batch ->
                viewModel.updateHistoryFilter(date, batch)
                showHistorySheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendingTopBar(
    selectedPeriod: String,
    selectedLanguage: String,
    selectedDate: String?,
    selectedBatch: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    onTitleClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val periodLabel = when (selectedPeriod) {
        "daily" -> stringResource(Res.string.period_daily)
        "weekly" -> stringResource(Res.string.period_weekly)
        "monthly" -> stringResource(Res.string.period_monthly)
        else -> selectedPeriod
    }

    TopAppBar(
        title = {
            Column(
                modifier = Modifier
                    .clickable { onTitleClick() }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(start = 4.dp)
                    )
                }
                
                val langLabel = selectedLanguage.replaceFirstChar { it.uppercase() }
                val subTitle = buildString {
                    append("$periodLabel · $langLabel")
                    if (!selectedDate.isNullOrEmpty()) {
                        val batchLabel = if (selectedBatch == "am") stringResource(Res.string.batch_am) else stringResource(Res.string.batch_pm)
                        append(" · $selectedDate ($batchLabel)")
                    }
                }

                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(
                        if (isDarkTheme) Res.drawable.GitHub_Invertocat_White
                        else Res.drawable.GitHub_Invertocat_Black
                    ),
                    contentDescription = "GitHub",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = onHistoryClick) {
                Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.history_trending))
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RepoList(
    uiState: MainUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onNavigateToDetail: (owner: String, repo: String) -> Unit
) {
    val state = rememberPullToRefreshState()
    
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        state = state,
        onRefresh = onRefresh,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = uiState.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(48.dp))
                }
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(Res.string.error_fetch, uiState.error),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRefresh) {
                        Text(stringResource(Res.string.retry))
                    }
                }
            }

            uiState.repos.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(Res.string.no_data))
                }
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    count = uiState.repos.size,
                    key = { index -> uiState.repos[index].url }
                ) { index ->
                    val repo = uiState.repos[index]
                    RepoItem(
                        index = index,
                        repo = repo,
                        since = uiState.since,
                        onClick = { onNavigateToDetail(repo.author, repo.repoName) }
                    )
                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
                }

                if (uiState.capturedAt.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.last_updated, uiState.capturedAt),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoItem(index: Int, repo: TrendingRepo, since: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.W500)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${repo.author}/${repo.repoName}",
                fontSize = 16.sp,
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (repo.description.isNotBlank()) {
                Text(
                    text = repo.description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (repo.aiSummaries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repo.aiSummaries.forEach { summary ->
                        AiSummaryBox(summary)
                    }
                }
            }

            RepoMetadata(repo = repo, since = since)
        }
    }
}

@Composable
private fun AiSummaryBox(aiSummary: TrendingAiSummary) {
    if (aiSummary.content.isEmpty()) return
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = aiSummary.content,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        
        val aiIcon = when (aiSummary.provider.lowercase()) {
            "chatgpt" -> if (isDarkTheme) Res.drawable.icon_openai_dark else Res.drawable.icon_openai_light
            "deepseek" -> if (isDarkTheme) Res.drawable.icon_deepseek_dark else Res.drawable.icon_deepseek_light
            "gemini" -> if (isDarkTheme) Res.drawable.icon_gemini_dark else Res.drawable.icon_gemini_light
            else -> if (isDarkTheme) Res.drawable.icon_gemini_dark else Res.drawable.icon_gemini_light
        }
        Icon(
            painter = painterResource(aiIcon),
            contentDescription = aiSummary.provider,
            tint = Color.Unspecified,
            modifier = Modifier
                .size(14.dp)
                .align(Alignment.End)
        )
    }
}

@Composable
private fun RepoMetadata(repo: TrendingRepo, since: String) {
    val periodLabel = when (since) {
        "daily" -> stringResource(Res.string.period_daily)
        "weekly" -> stringResource(Res.string.period_weekly)
        "monthly" -> stringResource(Res.string.period_monthly)
        else -> since
    }
    val starsTotal = stringResource(Res.string.stars_total, DateTimeUtils.formatNumber(repo.stars))
    val starsPeriod = stringResource(Res.string.stars_period, periodLabel, DateTimeUtils.formatNumber(repo.currentPeriodStars))
    val metadataText = buildString {
        if (!repo.language.isNullOrEmpty()) append("${repo.language} · ")
        append("$starsTotal · $starsPeriod")
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = repo.languageColor?.toColorOrNull() ?: MaterialTheme.colorScheme.outline
        ) {}
        Text(
            text = metadataText,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterBottomSheet(
    selectedPeriod: String,
    selectedLanguage: String,
    selectedProviders: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Set<String>) -> Unit
) {
    val periods = listOf("daily", "weekly", "monthly")
    val languages = listOf("all", "javascript", "java", "go", "rust", "typescript", "c++", "c", "swift", "kotlin")
    val providers = listOf("chatgpt", "deepseek")
    
    var tempPeriod by remember { mutableStateOf(selectedPeriod) }
    var tempLanguage by remember { mutableStateOf(selectedLanguage) }
    var tempProviders by remember { mutableStateOf(selectedProviders) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showHelpDialog) {
        InfoDialog(
            title = stringResource(Res.string.update_info_title),
            content = stringResource(Res.string.update_info_content),
            onDismiss = { showHelpDialog = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            BottomSheetHeader(
                title = stringResource(Res.string.filter_options),
                onHelpClick = { showHelpDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.filter_period),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                periods.forEachIndexed { index, period ->
                    val label = when (period) {
                        "daily" -> stringResource(Res.string.period_daily)
                        "weekly" -> stringResource(Res.string.period_weekly)
                        "monthly" -> stringResource(Res.string.period_monthly)
                        else -> period
                    }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                        onClick = { tempPeriod = period },
                        selected = tempPeriod == period
                    ) { Text(label) }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.filter_ai_provider),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                providers.forEach { provider ->
                    FilterChip(
                        selected = tempProviders.contains(provider),
                        onClick = {
                            tempProviders = if (tempProviders.contains(provider)) {
                                if (tempProviders.size > 1) tempProviders - provider else tempProviders
                            } else {
                                tempProviders + provider
                            }
                        },
                        label = { Text(provider.replaceFirstChar { it.uppercase() }) },
                        leadingIcon = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.filter_language),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languages.forEach { language ->
                    FilterChip(
                        selected = tempLanguage == language,
                        onClick = { tempLanguage = language },
                        label = { Text(language.replaceFirstChar { it.uppercase() }) },
                        leadingIcon = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        onConfirm("daily", "all", setOf("chatgpt"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.filter_reset))
                }

                Button(
                    onClick = { onConfirm(tempPeriod, tempLanguage, tempProviders) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.filter_done))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryBottomSheet(
    selectedDate: String?,
    selectedBatch: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?, String?) -> Unit
) {
    var tempDate by remember { mutableStateOf(selectedDate ?: "") }
    var tempBatch by remember { mutableStateOf(selectedBatch ?: "am") }
    var showHelpDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        InfoDialog(
            title = stringResource(Res.string.history_info_title),
            content = stringResource(Res.string.history_info_content),
            onDismiss = { showHelpDialog = false }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = datePickerState.selectedDateMillis
                    if (selected != null) {
                        tempDate = DateTimeUtils.formatEpochMillisToDate(selected)
                    }
                    showDatePicker = false
                }) { Text(stringResource(Res.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            BottomSheetHeader(
                title = stringResource(Res.string.history_trending),
                onHelpClick = { showHelpDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.history_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tempDate,
                    onValueChange = { },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(Res.string.click_to_select_date)) },
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.select_date))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = false,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                // 覆盖一个透明层处理点击，因为 disabled 的 TextField 无法接收点击事件
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.history_batch),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val amLabel = stringResource(Res.string.batch_am)
                val pmLabel = stringResource(Res.string.batch_pm)
                val batches = listOf("am" to amLabel, "pm" to pmLabel)
                batches.forEachIndexed { index, (batchValue, label) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = batches.size),
                        onClick = { tempBatch = batchValue },
                        selected = tempBatch == batchValue
                    ) { Text(label) }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        onConfirm(null, null)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.filter_reset))
                }

                Button(
                    onClick = { 
                        val finalDate = tempDate.trim().takeIf { it.isNotEmpty() }
                        val finalBatch = if (finalDate != null) tempBatch else null
                        onConfirm(finalDate, finalBatch) 
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.filter_done))
                }
            }
        }
    }
}

@Composable
private fun InfoDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(content) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.confirm))
            }
        }
    )
}

@Composable
private fun BottomSheetHeader(
    title: String,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        IconButton(onClick = onHelpClick) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(Res.string.action_help),
            )
        }
    }
}

private fun String.toColorOrNull(): Color? {
    val hex = this.removePrefix("#")
    return if (hex.length == 6) {
        runCatching {
            Color((hex.toLong(16) or 0xFF000000L).toInt())
        }.getOrNull()
    } else {
        null
    }
}
