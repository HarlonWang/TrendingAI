package whl.trending.ai.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.action_help
import trendingai.shared.generated.resources.batch_am
import trendingai.shared.generated.resources.batch_pm
import trendingai.shared.generated.resources.meta_batch
import trendingai.shared.generated.resources.meta_generated
import trendingai.shared.generated.resources.meta_ph_scope
import trendingai.shared.generated.resources.meta_snapshot
import trendingai.shared.generated.resources.meta_updated
import trendingai.shared.generated.resources.update_stamp_earlier
import trendingai.shared.generated.resources.update_stamp_today
import trendingai.shared.generated.resources.update_stamp_yesterday
import whl.trending.ai.core.DateTimeUtils

/**
 * 列表头部的抓取时机条：一行「今天 08:17 更新 · 上午批次 ⓘ」，整行点开数据说明页。
 *
 * 放在列表的第一个 item 里、随内容滚动退场，不做常驻栏——常驻会挤内容，也会跟沉浸式浏览
 * 的头部退场逻辑打架。
 *
 * **居中不是装饰**：左对齐时它与列表项同宽同边距、文字起点又跟序号圆点对齐而非标题对齐，
 * 读起来像"第 0 条内容"。居中本身就是「我不属于内容流」的信号——这行文字原先待在列表
 * 尾部时就是居中灰字，从不显突，挪到头部后一并把那个信号带过来。与首项之间留出比自身
 * 内边距更大的空隙，进一步和 divider 分隔的内容项拉开。
 *
 * 时间为什么不用相对时间（"3 小时前"）：三源都是日更节律，相对时间会把正常节律说成
 * 「13 小时前更新」，读起来像陈旧数据。详见 [DateTimeUtils.updateStamp] 的说明。
 *
 * ⓘ 只是视觉提示，**点击区是整行**——24dp 的图标做唯一热区太窄。
 */
@Composable
fun SourceMetaBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(Res.string.action_help),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
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
    val stamp = DateTimeUtils.updateStamp(capturedAt)
    val timePart = when (stamp.unit) {
        DateTimeUtils.UpdateStampUnit.TODAY -> stringResource(Res.string.update_stamp_today, stamp.time)
        DateTimeUtils.UpdateStampUnit.YESTERDAY -> stringResource(Res.string.update_stamp_yesterday, stamp.time)
        DateTimeUtils.UpdateStampUnit.EARLIER -> stringResource(
            Res.string.update_stamp_earlier,
            stamp.month.toString(),
            stamp.day.toString(),
            stamp.time,
        )
        DateTimeUtils.UpdateStampUnit.UNKNOWN -> return null
    }
    return stringResource(Res.string.meta_updated, timePart)
}

/** Picks 用的生成时刻文案：「今天 09:00 生成」。语义是生成而非更新，动词单列一份。 */
@Composable
fun generatedStampText(generatedAt: String): String? {
    val stamp = DateTimeUtils.updateStamp(generatedAt)
    val timePart = when (stamp.unit) {
        DateTimeUtils.UpdateStampUnit.TODAY -> stringResource(Res.string.update_stamp_today, stamp.time)
        DateTimeUtils.UpdateStampUnit.YESTERDAY -> stringResource(Res.string.update_stamp_yesterday, stamp.time)
        DateTimeUtils.UpdateStampUnit.EARLIER -> stringResource(
            Res.string.update_stamp_earlier,
            stamp.month.toString(),
            stamp.day.toString(),
            stamp.time,
        )
        DateTimeUtils.UpdateStampUnit.UNKNOWN -> return null
    }
    return stringResource(Res.string.meta_generated, timePart)
}

/** 给整句挂上批次后缀：「今天 08:17 更新 · 上午批次」。[batch] 取 'am' / 'pm'。 */
@Composable
fun withBatchSuffix(text: String, batch: String): String {
    val batchLabel = if (batch.equals("pm", ignoreCase = true)) {
        stringResource(Res.string.batch_pm)
    } else {
        stringResource(Res.string.batch_am)
    }
    return stringResource(Res.string.meta_batch, text, batchLabel)
}

/**
 * 历史快照态的整句：「2026-08-09 · 上午批次快照」。
 *
 * 快照看的是过去某一批，说「今天 08:17 更新」是错的——这一支直接用选中的日期，不走
 * [updateStampText]。
 */
@Composable
fun snapshotStampText(date: String, batch: String): String {
    val batchLabel = if (batch.equals("pm", ignoreCase = true)) {
        stringResource(Res.string.batch_pm)
    } else {
        stringResource(Res.string.batch_am)
    }
    return stringResource(Res.string.meta_snapshot, date, batchLabel)
}

/** 给整句挂上 PH 的收录口径后缀：「今天 08:30 更新 · 收录前一日发布」。 */
@Composable
fun withPhScopeSuffix(text: String): String = stringResource(Res.string.meta_ph_scope, text)
