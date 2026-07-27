package whl.trending.ai.ui.subscribe

import whl.trending.ai.data.model.SubscribeStatus
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.subscribe_title
import trendingai.shared.generated.resources.subscribe_intro
import trendingai.shared.generated.resources.subscribe_email_placeholder
import trendingai.shared.generated.resources.subscribe_email_invalid
import trendingai.shared.generated.resources.subscribe_action_subscribe
import trendingai.shared.generated.resources.subscribe_action_cancel
import trendingai.shared.generated.resources.subscribe_footer
import trendingai.shared.generated.resources.subscribe_success_new
import trendingai.shared.generated.resources.subscribe_success_already
import trendingai.shared.generated.resources.subscribe_success_resubscribed
import trendingai.shared.generated.resources.subscribe_success_cancelled
import trendingai.shared.generated.resources.subscribe_not_subscribed
import trendingai.shared.generated.resources.subscribe_error

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubscribeScreen(
    onBack: () -> Unit,
    viewModel: SubscribeViewModel = viewModel { SubscribeViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val successNew = stringResource(Res.string.subscribe_success_new)
    val successAlready = stringResource(Res.string.subscribe_success_already)
    val successResubscribed = stringResource(Res.string.subscribe_success_resubscribed)
    val successCancelled = stringResource(Res.string.subscribe_success_cancelled)
    val notSubscribed = stringResource(Res.string.subscribe_not_subscribed)
    val errorMsg = stringResource(Res.string.subscribe_error)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SubscribeEvent.Success -> {
                    val msg = when (event.status) {
                        SubscribeStatus.NEW -> successNew
                        SubscribeStatus.ALREADY -> successAlready
                        SubscribeStatus.RESUBSCRIBED -> successResubscribed
                        SubscribeStatus.CANCELLED -> successCancelled
                        SubscribeStatus.NOT_SUBSCRIBED -> notSubscribed
                        SubscribeStatus.UNKNOWN -> successNew
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is SubscribeEvent.Error -> {
                    snackbarHostState.showSnackbar(errorMsg)
                }
            }
        }
    }

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                title = { Text(stringResource(Res.string.subscribe_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
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

            Text(
                text = stringResource(Res.string.subscribe_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::updateEmail,
                label = { Text(stringResource(Res.string.subscribe_email_placeholder)) },
                supportingText = if (!uiState.isEmailValid) {
                    { Text(stringResource(Res.string.subscribe_email_invalid)) }
                } else null,
                isError = !uiState.isEmailValid,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !uiState.isSubmitting
            )

            Spacer(Modifier.height(20.dp))

            val canSubmit = uiState.email.trim().isNotEmpty()
                    && uiState.isEmailValid
                    && !uiState.isSubmitting

            Button(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit
            ) {
                if (uiState.isSubmitting) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(Res.string.subscribe_action_subscribe))
                }
            }

            if (uiState.isAlreadySubscribed) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = viewModel::cancel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSubmitting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(Res.string.subscribe_action_cancel))
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(Res.string.subscribe_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
