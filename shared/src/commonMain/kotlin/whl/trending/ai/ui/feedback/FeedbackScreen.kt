package whl.trending.ai.ui.feedback

import whl.trending.ai.core.Constants
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.feedback
import trendingai.shared.generated.resources.feedback_content_placeholder
import trendingai.shared.generated.resources.feedback_email_placeholder
import trendingai.shared.generated.resources.feedback_submit
import trendingai.shared.generated.resources.feedback_success
import trendingai.shared.generated.resources.feedback_rate_limit
import trendingai.shared.generated.resources.feedback_error
import trendingai.shared.generated.resources.feedback_email_invalid
import trendingai.shared.generated.resources.feedback_github_issues

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = viewModel { FeedbackViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    val successMsg = stringResource(Res.string.feedback_success)
    val rateLimitMsg = stringResource(Res.string.feedback_rate_limit)
    val errorMsg = stringResource(Res.string.feedback_error)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FeedbackEvent.Success -> {
                    snackbarHostState.showSnackbar(successMsg)
                    delay(500)
                    onBack()
                }
                is FeedbackEvent.Error -> {
                    snackbarHostState.showSnackbar(
                        if (event.isRateLimit) rateLimitMsg else errorMsg
                    )
                }
            }
        }
    }

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.feedback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.content,
                onValueChange = viewModel::updateContent,
                label = { Text(stringResource(Res.string.feedback_content_placeholder)) },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                maxLines = 10,
                enabled = !uiState.isSubmitting
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::updateEmail,
                label = { Text(stringResource(Res.string.feedback_email_placeholder)) },
                supportingText = if (!uiState.isEmailValid) {
                    { Text(stringResource(Res.string.feedback_email_invalid)) }
                } else null,
                isError = !uiState.isEmailValid,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !uiState.isSubmitting
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.content.trim().isNotEmpty()
                        && uiState.content.length <= 2000
                        && uiState.isEmailValid
                        && !uiState.isSubmitting
            ) {
                if (uiState.isSubmitting) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(Res.string.feedback_submit))
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = { uriHandler.openUri(Constants.FEEDBACK_URL) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(Res.string.feedback_github_issues))
            }
        }
    }
}
