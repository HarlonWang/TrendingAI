package whl.trending.ai.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.batch_am
import trendingai.shared.generated.resources.batch_pm
import trendingai.shared.generated.resources.meta_batch
import trendingai.shared.generated.resources.meta_generated
import trendingai.shared.generated.resources.meta_snapshot
import trendingai.shared.generated.resources.meta_updated
import trendingai.shared.generated.resources.update_stamp_earlier
import trendingai.shared.generated.resources.update_stamp_today
import trendingai.shared.generated.resources.update_stamp_yesterday
import whl.trending.ai.core.DateTimeUtils

/**
 * 列表尾部的抓取时机行：一行居中淡灰字「今天 08:17 更新 · 上午批次」。
 *
 * 放在列表**最后一项之后**，并顺带承担列表底部的视觉收尾——三源的 `contentPadding`
 * 底部只让出悬浮底栏的高度、不留呼吸量，末条卡片本来是贴着底栏边缘的。
 *
 * 曾试过挪到列表头部（0.24.0 开发期）：那里它与列表项同宽同边距、文字起点又跟序号圆点
 * 对齐而非标题对齐，读起来像「第 0 条内容」；改居中后仍嫌重，最终回到尾部。代价是
 * 「数据新不新」的答案在最后一屏，取舍上认了——这行是**背景信息**，不是主动要传达的话。
 *
 * **纯展示、不可点**：口径说明的入口只留「我的 › 关于 › 数据来源与更新」一处。列表内
 * 挂一个隐形可点区（没有图标提示）只会带来误触，不会带来发现。
 *
 * 时间为什么不用相对时间（"3 小时前"）：三源都是日更节律，相对时间会把正常节律说成
 * 「13 小时前更新」，读起来像陈旧数据。详见 [DateTimeUtils.updateStamp] 的说明。
 */
@Composable
fun SourceMetaFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.outline,
    )
}

/**
 * 抓取时刻的整句文案：「今天 08:17 更新」。[capturedAt] 为 UTC 串，无从得知时返回 null，
 * 由调用方据此整行不渲染——宁可不显示，也不显示一个假的时间。
 *
 * 不做 `remember`：里面只是几个 Instant 算术，而缓存住 now 会让用户从后台切回来时看到
 * 过期的「今天」判断（`capturedAt` 没变、key 也不会变，缓存永不失效）。
 */
@Composable
fun updateStampText(capturedAt: String): String? {
    val timePart = stampTimePart(capturedAt) ?: return null
    return stringResource(Res.string.meta_updated, timePart)
}

/** Picks 用的生成时刻文案：「今天 09:00 生成」。语义是生成而非更新，动词单列一份。 */
@Composable
fun generatedStampText(generatedAt: String): String? {
    val timePart = stampTimePart(generatedAt) ?: return null
    return stringResource(Res.string.meta_generated, timePart)
}

/** 时间片：「今天 08:17」/「昨天 19:17」/「8月9日 19:17」。解析不出返回 null。 */
@Composable
private fun stampTimePart(utcString: String): String? {
    val stamp = DateTimeUtils.updateStamp(utcString)
    return when (stamp.unit) {
        DateTimeUtils.UpdateStampUnit.TODAY ->
            stringResource(Res.string.update_stamp_today, stamp.time)
        DateTimeUtils.UpdateStampUnit.YESTERDAY ->
            stringResource(Res.string.update_stamp_yesterday, stamp.time)
        DateTimeUtils.UpdateStampUnit.EARLIER -> stringResource(
            Res.string.update_stamp_earlier,
            stamp.month.toString(),
            stamp.day.toString(),
            stamp.time,
        )
        DateTimeUtils.UpdateStampUnit.UNKNOWN -> null
    }
}

/** 给整句挂上批次后缀：「今天 08:17 更新 · 上午批次」。[batch] 取 'am' / 'pm'。 */
@Composable
fun withBatchSuffix(text: String, batch: String): String =
    stringResource(Res.string.meta_batch, text, batchLabel(batch))

/**
 * 历史快照态的整句：「2026-08-09 · 上午批次快照」。
 *
 * 快照看的是过去某一批，说「今天 08:17 更新」是错的——这一支直接用选中的日期，不走
 * [updateStampText]。
 */
@Composable
fun snapshotStampText(date: String, batch: String): String =
    stringResource(Res.string.meta_snapshot, date, batchLabel(batch))

@Composable
private fun batchLabel(batch: String): String = if (batch.equals("pm", ignoreCase = true)) {
    stringResource(Res.string.batch_pm)
} else {
    stringResource(Res.string.batch_am)
}
