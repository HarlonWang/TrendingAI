package whl.trending.ai.ui.common

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.sponsor_link_needed_later
import trendingai.shared.generated.resources.sponsor_link_needed_message
import trendingai.shared.generated.resources.sponsor_link_needed_title
import whl.trending.ai.core.ProSponsor

/**
 * 全局「去过赞助页、但账户没关联 GitHub」提示宿主，挂 App 根部。
 * 判定是启发式的——「赞助了」只是本地意图时间戳，后端不知道付没付钱；
 * 文案只能说「如果你刚完成赞助」，不得断言「赞助已收到」。
 */
@Composable
fun SponsorLinkHost() {
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ProSponsor.needsGithubLink.collect {
            ProSponsor.consumeNeedsGithubLink()
            show = true
        }
    }

    if (!show) return

    LinkGithubDialog(
        title = stringResource(Res.string.sponsor_link_needed_title),
        message = stringResource(Res.string.sponsor_link_needed_message),
        onDismissRequest = { show = false },
        dismissButton = {
            TextButton(onClick = { show = false }) {
                Text(stringResource(Res.string.sponsor_link_needed_later))
            }
        },
    )
}
