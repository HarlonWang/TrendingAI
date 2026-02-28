package whl.trending.ai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import whl.trending.ai.core.App
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.update.globalUpdateChecker
import whl.trending.updater.UpdateDialog
import whl.trending.updater.UpdateViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        whl.trending.ai.core.platform.AndroidContextHolder.initialize(this)

        lifecycleScope.launch {
            globalSettingsManager.appLanguage
                .distinctUntilChanged()
                .collect { language ->
                    val localeList = when (language) {
                        AppLanguage.CHINESE -> LocaleListCompat.forLanguageTags("zh")
                        AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
                        AppLanguage.FOLLOW_SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                    }
                    AppCompatDelegate.setApplicationLocales(localeList)
                }
        }

        setContent {
            val updateViewModel: UpdateViewModel = viewModel()
            remember(updateViewModel) { globalUpdateChecker = updateViewModel }

            val updateInfo by updateViewModel.updateInfo.collectAsState()
            updateInfo?.let {
                UpdateDialog(it, onDismiss = { updateViewModel.dismissUpdate() })
            }

            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
