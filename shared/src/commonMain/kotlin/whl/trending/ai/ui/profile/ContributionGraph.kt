package whl.trending.ai.ui.profile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.contrib_less
import trendingai.shared.generated.resources.contrib_months
import trendingai.shared.generated.resources.contrib_more
import trendingai.shared.generated.resources.contrib_total
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.data.model.ContributionCalendar
import whl.trending.ai.data.model.ContributionLevel
import whl.trending.ai.data.model.ContributionWeek

private val CellSize = 11.dp
private val CellGap = 3.dp
private val WeekdayLabelWidth = 28.dp
private val MonthLabelHeight = 16.dp

// 配色方案 A：保留 GitHub 风格绿色梯度，辨识度最高（"一眼就是贡献图"）。
// NONE 档用主题色融入背景，L1~L4 用 GitHub 明/暗两套绿阶。
private val GreensLight = listOf(
    Color(0xFF9BE9A8),
    Color(0xFF40C463),
    Color(0xFF30A14E),
    Color(0xFF216E39),
)
private val GreensDark = listOf(
    Color(0xFF0E4429),
    Color(0xFF006D32),
    Color(0xFF26A641),
    Color(0xFF39D353),
)

/**
 * GitHub 风格贡献热力图（52~53 周 × 7 天）。横向可滚动看满一年，左侧 Mon/Wed/Fri 标签固定。
 * 仅在数据可用时渲染（[calendar] 由调用方判空后传入）。
 */
@Composable
fun ContributionGraph(
    calendar: ContributionCalendar,
    // 滚动状态由调用方（ProfileScreen）持有：放在被 LazyColumn 回收的 item 内部会随上下滚动
    // 销毁重建、位置被重置到最左，故上提到不随 item 回收的作用域
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val greens = if (dark) GreensDark else GreensLight
    val noneColor = MaterialTheme.colorScheme.surfaceContainerHighest

    fun levelColor(level: ContributionLevel): Color = when (level) {
        ContributionLevel.NONE -> noneColor
        ContributionLevel.FIRST -> greens[0]
        ContributionLevel.SECOND -> greens[1]
        ContributionLevel.THIRD -> greens[2]
        ContributionLevel.FOURTH -> greens[3]
    }

    val monthNames = stringResource(Res.string.contrib_months).split(",")
    // 每周首格对应的月份索引（1~12）；用于在月份切换处打标签
    val monthLabels = remember(calendar) { computeMonthLabels(calendar.weeks) }

    // 默认滚动到最右（最新一周），且仅在首次布局后执行一次：用持久化 flag 锁定，
    // 否则热力图每次重新进入可视区都会被强拉到末尾、覆盖用户手动滚动的位置
    var didAutoScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scrollState.maxValue) {
        if (!didAutoScroll && scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
            didAutoScroll = true
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.contrib_total, DateTimeUtils.formatNumber(calendar.total)),
            style = MaterialTheme.typography.titleSmall,
        )

        Row {
            // 固定的左侧 Mon/Wed/Fri 标签（与网格 7 行对齐）
            WeekdayLabels()
            // 网格本体：横向滚动
            Column(
                modifier = Modifier.horizontalScroll(scrollState),
            ) {
                MonthLabelsRow(monthLabels, monthNames)
                Row(horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    calendar.weeks.forEach { week ->
                        WeekColumn(week) { levelColor(it) }
                    }
                }
            }
        }

        Legend(noneColor = noneColor, greens = greens)
    }
}

@Composable
private fun WeekdayLabels() {
    // 行序对齐 GitHub：0=Sun(空) 1=Mon 2=Tue(空) 3=Wed 4=Thu(空) 5=Fri 6=Sat(空)
    val labels = listOf("", "Mon", "", "Wed", "", "Fri", "")
    Column(
        modifier = Modifier.width(WeekdayLabelWidth).padding(top = MonthLabelHeight, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        labels.forEach { label ->
            Box(Modifier.height(CellSize), contentAlignment = Alignment.CenterEnd) {
                if (label.isNotEmpty()) {
                    Text(
                        label,
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        // 文字行高大于格高，允许溢出 Box 并居中，避免被裁掉上下沿
                        modifier = Modifier.wrapContentSize(unbounded = true),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthLabelsRow(monthLabels: List<Int?>, monthNames: List<String>) {
    Row(
        modifier = Modifier.height(MonthLabelHeight),
        horizontalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        monthLabels.forEach { month ->
            Box(Modifier.width(CellSize)) {
                if (month != null) {
                    val name = monthNames.getOrNull(month - 1) ?: ""
                    Text(
                        name,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        // 让月份名从本列起向右溢出到相邻空列上方，不被本列 11dp 宽度裁掉
                        modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekColumn(week: ContributionWeek, colorOf: (ContributionLevel) -> Color) {
    Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
        // 按 weekday 0~6 补齐 7 行；首尾周缺失的日期渲染为透明占位，保持网格对齐
        for (weekday in 0..6) {
            val day = week.days.firstOrNull { it.weekday == weekday }
            Box(
                Modifier
                    .size(CellSize)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (day == null) Color.Transparent else colorOf(day.level)),
            )
        }
    }
}

@Composable
private fun Legend(noneColor: Color, greens: List<Color>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.contrib_less),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        (listOf(noneColor) + greens).forEach { color ->
            Box(
                Modifier
                    .padding(horizontal = 1.dp)
                    .size(CellSize)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(Res.string.contrib_more),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 计算每周列上方应显示的月份标签。规则对齐 GitHub：当某周首日的月份与上一周不同，
 * 在该列打出新月份；否则留空（null）。
 */
private fun computeMonthLabels(weeks: List<ContributionWeek>): List<Int?> {
    var prevMonth = -1
    return weeks.map { week ->
        val firstDate = week.days.firstOrNull()?.date
        val month = firstDate?.let { monthOf(it) }
        if (month != null && month != prevMonth) {
            prevMonth = month
            month
        } else {
            null
        }
    }
}

/** 从 "YYYY-MM-DD" 取月份（1~12）；解析失败返回 null。 */
private fun monthOf(date: String): Int? =
    date.substringAfter('-', "").substringBefore('-', "").toIntOrNull()
