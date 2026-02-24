package whl.trending.ai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import whl.trending.ai.core.App
import whl.trending.ai.core.platform.NotificationScheduler
import whl.trending.ai.data.local.AppLanguage
import whl.trending.ai.data.local.globalSettingsManager

class MainActivity : AppCompatActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        NotificationScheduler.onPermissionResult(isGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        whl.trending.ai.core.platform.AndroidContextHolder.initialize(this)
        NotificationScheduler.initLauncher(requestPermissionLauncher)

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
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
