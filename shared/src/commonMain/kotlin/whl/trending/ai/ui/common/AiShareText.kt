package whl.trending.ai.ui.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.share_ai_text_brief
import trendingai.shared.generated.resources.share_ai_text_full

/**
 * 按是否有摘要拼装「分享给 AI」的纯文本：有摘要走 full 模板，否则走 brief。
 * 列表 item 与详情页共用，确保分享话术一致。
 */
@Composable
fun aiShareText(title: String, summary: String?, url: String): String =
    if (summary.isNullOrBlank())
        stringResource(Res.string.share_ai_text_brief, title, url)
    else
        stringResource(Res.string.share_ai_text_full, title, summary, url)
