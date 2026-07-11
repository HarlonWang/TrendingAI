package whl.trending.ai.ui.settings

import whl.trending.ai.core.Constants
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.ui.home.githubLogoPainter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.about_us
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.confirm
import trendingai.shared.generated.resources.contact_author
import trendingai.shared.generated.resources.donate
import trendingai.shared.generated.resources.donate_alipay
import trendingai.shared.generated.resources.donate_github_desc
import trendingai.shared.generated.resources.donate_message
import trendingai.shared.generated.resources.official_website
import trendingai.shared.generated.resources.privacy_policy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToWebPage: (url: String, title: String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var showDonateDialog by remember { mutableStateOf(false) }

    if (showDonateDialog) {
        DonateDialog(onDismiss = { showDonateDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.about_us)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.official_website)) },
                leadingContent = { Icon(Icons.Default.Public, null) },
                modifier = Modifier.clickable {
                    uriHandler.openUri(Constants.OFFICIAL_WEBSITE_URL)
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.contact_author)) },
                trailingContent = {
                    SelectionContainer {
                        Text(Constants.AUTHOR_EMAIL, color = MaterialTheme.colorScheme.outline)
                    }
                },
                leadingContent = { Icon(Icons.Default.AlternateEmail, null) }
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.donate)) },
                leadingContent = { Icon(Icons.Default.VolunteerActivism, null) },
                modifier = Modifier.clickable {
                    trackEvent("settings_donate")
                    showDonateDialog = true
                }
            )
            val privacyTitle = stringResource(Res.string.privacy_policy)
            ListItem(
                headlineContent = { Text(privacyTitle) },
                leadingContent = { Icon(Icons.Default.PrivacyTip, null) },
                modifier = Modifier.clickable {
                    onNavigateToWebPage(Constants.PRIVACY_POLICY_URL, privacyTitle)
                }
            )
        }
    }
}

@Composable
private fun DonateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.donate)) },
        text = {
            Column {
                Text(stringResource(Res.string.donate_message))
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            trackEvent("settings_donate_github")
                            ProSponsor.openSponsorPage(ProSponsor.SOURCE_SETTINGS_DONATE)
                        }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        painter = githubLogoPainter(),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text("GitHub Sponsors", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(Res.string.donate_github_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Default.VolunteerActivism,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    SelectionContainer(modifier = Modifier.padding(start = 16.dp)) {
                        Text(
                            text = stringResource(Res.string.donate_alipay, Constants.ALIPAY_ACCOUNT),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.confirm))
            }
        }
    )
}
