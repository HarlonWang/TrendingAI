package whl.trending.ai.ui.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.time_days_ago
import trendingai.shared.generated.resources.time_hours_ago
import trendingai.shared.generated.resources.time_just_now
import trendingai.shared.generated.resources.time_minutes_ago
import whl.trending.ai.core.DateTimeUtils

/**
 * 相对时间文案；超 7 天或解析失败回退到绝对时间。
 *
 * 不 `remember`：里面只是几个 Instant 算术，而缓存住 now 会让「3 小时前」在页面长时间停留、
 * 或从后台切回后仍显示旧值（key 只有时间戳本身，永不失效）。
 *
 * 用于连续流式的时间点（动态、评论）。列表的**抓取时机**不要用它——三源是日更节律，
 * 相对时间会把正常节律说成「13 小时前」，那里走 [updateStampText]。
 */
@Composable
fun relativeTimeText(utcString: String): String {
    val rt = DateTimeUtils.relativeTime(utcString)
    return when (rt.unit) {
        DateTimeUtils.RelativeUnit.JUST_NOW -> stringResource(Res.string.time_just_now)
        DateTimeUtils.RelativeUnit.MINUTES -> stringResource(Res.string.time_minutes_ago, rt.value)
        DateTimeUtils.RelativeUnit.HOURS -> stringResource(Res.string.time_hours_ago, rt.value)
        DateTimeUtils.RelativeUnit.DAYS -> stringResource(Res.string.time_days_ago, rt.value)
        DateTimeUtils.RelativeUnit.ABSOLUTE -> rt.absolute
    }
}
