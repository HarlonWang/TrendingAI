package whl.trending.ai.core

import whl.trending.ai.ui.detail.ReadmeScreen
import whl.trending.ai.ui.trending.TrendingScreen
import whl.trending.ai.ui.settings.SettingsScreen
import whl.trending.ai.data.local.ThemeMode
import whl.trending.ai.data.local.globalSettingsManager

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay

data object Main
data object Settings
data class RepoDetail(val owner: String, val repo: String)

@Composable
@Preview
fun App() {
    val backStack = remember { mutableStateListOf<Any>(Main) }
    val themeMode by globalSettingsManager.themeMode.collectAsState(ThemeMode.FOLLOW_SYSTEM)
    
    val isDark = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = { key ->
                when (key) {
                    is Main -> NavEntry(key) {
                        TrendingScreen(
                            onNavigateToDetail = { owner, repo ->
                                backStack.add(RepoDetail(owner, repo))
                            },
                            showFilterSheet = false,
                            onDismissFilterSheet = {},
                            showHistorySheet = false,
                            onDismissHistorySheet = {},
                        )
                    }

                    is Settings -> NavEntry(key) {
                        SettingsScreen(
                            onBack = {
                                backStack.removeLastOrNull()
                            }
                        )
                    }

                    is RepoDetail -> NavEntry(key) {
                        ReadmeScreen(
                            owner = key.owner,
                            repo = key.repo,
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }

                    else -> {
                        error("Unknown route: $key")
                    }
                }
            }
        )
    }
}
