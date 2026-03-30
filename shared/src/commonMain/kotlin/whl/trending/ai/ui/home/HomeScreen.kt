package whl.trending.ai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.GitHub_Invertocat_Black
import trendingai.shared.generated.resources.GitHub_Invertocat_White
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.app_name
import trendingai.shared.generated.resources.batch_am
import trendingai.shared.generated.resources.batch_pm
import trendingai.shared.generated.resources.history_trending
import trendingai.shared.generated.resources.period_daily
import trendingai.shared.generated.resources.period_monthly
import trendingai.shared.generated.resources.period_weekly
import trendingai.shared.generated.resources.settings
import whl.trending.ai.ui.picks.PicksScreen
import whl.trending.ai.ui.picks.PicksViewModel
import whl.trending.ai.ui.trending.TrendingScreen
import whl.trending.ai.ui.trending.TrendingViewModel

enum class HomeTab {
    Trending, Picks
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (owner: String, repo: String) -> Unit
) {
    var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.Trending.name) }
    val selectedTab = HomeTab.valueOf(selectedTabName)
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }

    val trendingViewModel: TrendingViewModel = viewModel { TrendingViewModel() }
    val trendingUiState by trendingViewModel.uiState.collectAsState()
    val picksViewModel: PicksViewModel = viewModel { PicksViewModel() }
    val picksUiState by picksViewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            when (selectedTab) {
                HomeTab.Trending -> TrendingTopBar(
                    selectedPeriod = trendingUiState.selectedPeriod,
                    selectedLanguage = trendingUiState.selectedLanguage,
                    selectedDate = trendingUiState.selectedDate,
                    selectedBatch = trendingUiState.selectedBatch,
                    scrollBehavior = scrollBehavior,
                    onTitleClick = { showFilterSheet = true },
                    onHistoryClick = { showHistorySheet = true },
                    onNavigateToSettings = onNavigateToSettings
                )
                HomeTab.Picks -> PicksTopBar(
                    date = picksUiState.picks?.metadata?.date,
                    scrollBehavior = scrollBehavior,
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Trending,
                    onClick = { selectedTabName = HomeTab.Trending.name },
                    icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Trending") },
                    label = { Text("Trending") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.Picks,
                    onClick = { selectedTabName = HomeTab.Picks.name },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Picks") },
                    label = { Text("Picks") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            HomeTab.Trending -> TrendingScreen(
                onNavigateToDetail = onNavigateToDetail,
                showFilterSheet = showFilterSheet,
                onDismissFilterSheet = { showFilterSheet = false },
                showHistorySheet = showHistorySheet,
                onDismissHistorySheet = { showHistorySheet = false },
                modifier = Modifier.padding(innerPadding),
                viewModel = trendingViewModel
            )
            HomeTab.Picks -> PicksScreen(
                onNavigateToDetail = onNavigateToDetail,
                modifier = Modifier.padding(innerPadding),
                viewModel = picksViewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendingTopBar(
    selectedPeriod: String,
    selectedLanguage: String,
    selectedDate: String?,
    selectedBatch: String?,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PicksTopBar(
    date: String?,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onNavigateToSettings: () -> Unit
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Picks",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Column {
                Text(
                    text = "Picks",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = buildString {
                        append("GitHub · HN · PH")
                        if (!date.isNullOrBlank()) append(" · $date")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.settings))
            }
        }
    )
}
