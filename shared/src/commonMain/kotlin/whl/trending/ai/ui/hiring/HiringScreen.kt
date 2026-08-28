package whl.trending.ai.ui.hiring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.close
import trendingai.shared.generated.resources.hiring_apply
import trendingai.shared.generated.resources.hiring_clear
import trendingai.shared.generated.resources.hiring_detail_all_roles
import trendingai.shared.generated.resources.hiring_detail_cities
import trendingai.shared.generated.resources.hiring_detail_employment
import trendingai.shared.generated.resources.hiring_detail_language
import trendingai.shared.generated.resources.hiring_detail_region
import trendingai.shared.generated.resources.hiring_detail_salary
import trendingai.shared.generated.resources.hiring_detail_stack
import trendingai.shared.generated.resources.hiring_detail_timezone
import trendingai.shared.generated.resources.hiring_detail_work_auth
import trendingai.shared.generated.resources.hiring_empty_filtered
import trendingai.shared.generated.resources.hiring_filter_remote
import trendingai.shared.generated.resources.hiring_filter_role
import trendingai.shared.generated.resources.hiring_load_failed
import trendingai.shared.generated.resources.hiring_meta
import trendingai.shared.generated.resources.hiring_mode_hybrid
import trendingai.shared.generated.resources.hiring_mode_onsite
import trendingai.shared.generated.resources.hiring_mode_remote
import trendingai.shared.generated.resources.hiring_more_roles
import trendingai.shared.generated.resources.hiring_result_count
import trendingai.shared.generated.resources.hiring_role_ai_ml
import trendingai.shared.generated.resources.hiring_role_android
import trendingai.shared.generated.resources.hiring_role_backend
import trendingai.shared.generated.resources.hiring_role_data
import trendingai.shared.generated.resources.hiring_role_design
import trendingai.shared.generated.resources.hiring_role_devops_sre
import trendingai.shared.generated.resources.hiring_role_embedded_hardware
import trendingai.shared.generated.resources.hiring_role_frontend
import trendingai.shared.generated.resources.hiring_role_fullstack
import trendingai.shared.generated.resources.hiring_role_ios
import trendingai.shared.generated.resources.hiring_role_management
import trendingai.shared.generated.resources.hiring_role_mobile
import trendingai.shared.generated.resources.hiring_role_other
import trendingai.shared.generated.resources.hiring_role_product
import trendingai.shared.generated.resources.hiring_role_security
import trendingai.shared.generated.resources.hiring_scope_restricted
import trendingai.shared.generated.resources.hiring_scope_unspecified
import trendingai.shared.generated.resources.hiring_scope_worldwide
import trendingai.shared.generated.resources.hiring_search_hint
import trendingai.shared.generated.resources.hiring_source_link
import trendingai.shared.generated.resources.hiring_title
import trendingai.shared.generated.resources.hiring_type_contract
import trendingai.shared.generated.resources.hiring_type_full_time
import trendingai.shared.generated.resources.hiring_type_internship
import trendingai.shared.generated.resources.hiring_type_part_time
import trendingai.shared.generated.resources.hiring_unavailable_desc
import trendingai.shared.generated.resources.hiring_unavailable_title
import trendingai.shared.generated.resources.hiring_view_post
import trendingai.shared.generated.resources.retry
import trendingai.shared.generated.resources.time_days_ago
import trendingai.shared.generated.resources.time_hours_ago
import trendingai.shared.generated.resources.time_just_now
import trendingai.shared.generated.resources.time_minutes_ago
import whl.trending.ai.core.DateTimeUtils
import whl.trending.ai.core.Hiring
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.ContentActionKind
import whl.trending.ai.core.analytics.ListFilter
import whl.trending.ai.core.analytics.track
import whl.trending.ai.core.hnDiscussionUrl
import whl.trending.ai.data.model.HiringPost
import whl.trending.ai.ui.common.BetaBadge
import whl.trending.ai.ui.common.TrendingBottomSheet
import whl.trending.ai.ui.common.TrendingDropdownMenu
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar
import whl.trending.ai.ui.home.HackerNewsOrange
import whl.trending.ai.ui.home.hackerNewsIcon

private const val SOURCE = "hn_whoishiring"

/** 概览区在列表里的下标（来源行之后）。收起判定按它算 */
private const val OverviewItemIndex = 1

/** 正文宽度上限，守 M3 的 40–60 字符/行；手机上不生效，平板与折叠屏上防止整行拉满 */
private val ContentMaxWidth = 600.dp

/** 埋点维度映射。新增筛选维度时两边一起改 */
private fun HiringFilterDim.toListFilter() = when (this) {
    HiringFilterDim.ROLE_CATEGORY -> ListFilter.ROLE_CATEGORY
    HiringFilterDim.REMOTE_KIND -> ListFilter.REMOTE_KIND
}

/**
 * HN 招聘月度专题。
 *
 * **产品边界：只呈现原文写明的约束事实，不替用户判断能不能投。**
 * 因此：不推断所在地、不做默认过滤、进入页面一律无筛选态、不记忆筛选偏好。
 *
 * 页面上**只有概览区一套可交互元素**：它既是「这个月有什么」的结构展示，也是唯一的筛选面。
 * 卡片上的事实是只读标签，不做成 chip——M3 里 chip 一律是交互元素，拿禁用态的 chip 当静态
 * 元数据用会得到一屏分不清哪些能点的灰块。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HiringScreen(
    page: Hiring,
    onBack: () -> Unit,
    onOpenUrl: (url: String) -> Unit,
    viewModel: HiringViewModel = viewModel(key = "hiring/${page.month ?: "latest"}") {
        HiringViewModel(page.month)
    },
) {
    val uiState by viewModel.uiState.collectAsState()
    val ready = uiState as? HiringUiState.Ready

    TrendingScaffold(
        topBar = {
            TrendingTopAppBar(
                // 标题恒为页面名，期次挪到右侧：两者是「这是什么页」和「看的是哪一期」两件事，
                // 曾经共用标题位、数据一到就把页面名顶掉，加载完那一下页面名就消失了
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.hiring_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        BetaBadge()
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                actions = {
                    // 月份只在数据到位后出现：没数据时不知道是哪一期，留空好过占位
                    if (ready != null) {
                        if (ready.months.size > 1) {
                            MonthSwitcher(ready) { month ->
                                track(AppEvent.ListFiltered(ListFilter.MONTH, month))
                                viewModel.switchMonth(month)
                            }
                        } else {
                            // 只有一期，没得切，就别做成按钮样子
                            Text(
                                text = ready.month,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val s = uiState) {
                is HiringUiState.Loading -> LoadingIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                is HiringUiState.Unavailable -> Placeholder(
                    title = stringResource(Res.string.hiring_unavailable_title),
                    desc = stringResource(Res.string.hiring_unavailable_desc),
                )

                is HiringUiState.Error -> Placeholder(
                    title = stringResource(Res.string.hiring_load_failed),
                    desc = null,
                    action = { TextButton(onClick = viewModel::retry) { Text(stringResource(Res.string.retry)) } },
                )

                is HiringUiState.Ready -> ReadyContent(s, viewModel, onOpenUrl)
            }
        }
    }
}

@Composable
private fun ReadyContent(
    s: HiringUiState.Ready,
    viewModel: HiringViewModel,
    onOpenUrl: (String) -> Unit,
) {
    val list = s.filtered
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<HiringPost?>(null) }

    // 概览区滚出视野后，把当前筛选压缩成一条常驻摘要——结构看一次就够（内容），筛选随时
    // 要改（控件）。整块跟着列表滚走会让人翻到第 80 条时既不知道筛了什么、也改不了
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > OverviewItemIndex }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (collapsed && s.isFiltering) {
            SummaryBar(
                s = s,
                shown = list.size,
                // 摘要条只读不可编辑，点它滚回概览区——否则关键词筛着却改不了，只能手动往回翻
                onExpand = { scope.launch { listState.animateScrollToItem(0) } },
                onClear = viewModel::clearFilters,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 来源标注 + 当月主帖入口。放首屏第一行而不是列表末尾——233 条之后的标注等于
            // 没标，且卡片只能逐条跳楼层，整月主帖本来无处可去
            item { Centered { SourceHeader(s.storyId, onOpenUrl) } }

            item { Centered { OverviewPanel(s, viewModel) } }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            if (list.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.hiring_empty_filtered),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }

            items(list, key = { it.externalId }) { post ->
                Centered { JobCard(post, onOpenUrl) { detail = post } }
            }
        }
    }

    detail?.let { post ->
        JobDetailSheet(post, onOpenUrl, onDismiss = { detail = null })
    }
}

/** 宽屏上把内容收进可读宽度并居中；手机上等同于铺满 */
@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = ContentMaxWidth)) { content() }
    }
}

/**
 * 本期结构 + 唯一的筛选面。
 *
 * 用紧凑文字而不是 chip：十来项 chip 的容器与内边距会吃掉半屏，且会与卡片上的只读标签同构。
 * 职能用换行不用横滚——尾巴上的「Android 4 / iOS 3」正是这个维度最大的价值（让人三秒内知道
 * 别翻了），横滚会把它藏起来。
 */
@Composable
private fun OverviewPanel(s: HiringUiState.Ready, viewModel: HiringViewModel) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(Res.string.hiring_meta, s.all.size, s.lastSyncedAt?.take(10) ?: s.month),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        OutlinedTextField(
            value = s.query,
            onValueChange = viewModel::search,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(Res.string.hiring_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (s.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.search("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
                    }
                }
            },
            singleLine = true,
        )

        FacetGroup(Res.string.hiring_filter_role, HiringFilterDim.ROLE_CATEGORY, s, viewModel)
        FacetGroup(Res.string.hiring_filter_remote, HiringFilterDim.REMOTE_KIND, s, viewModel)

        if (s.isFiltering) {
            TextButton(onClick = viewModel::clearFilters, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(Res.string.hiring_clear))
            }
        }
    }
}

@Composable
private fun FacetGroup(
    label: StringResource,
    dim: HiringFilterDim,
    s: HiringUiState.Ready,
    viewModel: HiringViewModel,
) {
    val counts = s.counts(dim)
    if (counts.isEmpty()) return
    val selected = s.selected[dim].orEmpty()

    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            counts.entries.sortedByDescending { it.value }.forEach { (value, n) ->
                FacetItem(
                    text = labelOf(value),
                    count = n,
                    selected = value in selected,
                    onClick = {
                        track(AppEvent.ListFiltered(dim.toListFilter(), value))
                        viewModel.toggle(dim, value)
                    },
                )
            }
        }
    }
}

@Composable
private fun FacetItem(text: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 概览区滚走后的常驻摘要：当前生效的条件 + 结果数 + 清除。
 *
 * 维度标签与关键词**必须同时呈现**：只显示其一会让另一个变成看不见的筛选条件，
 * 用户看着 41 条结果却不知道是什么把它筛成这样的。关键词用「」括起来与维度标签区分。
 */
@Composable
private fun SummaryBar(s: HiringUiState.Ready, shown: Int, onExpand: () -> Unit, onClear: () -> Unit) {
    val active = listOfNotNull(
        s.selected.values.flatten().map { labelOf(it) }.joinToString(" · ").ifEmpty { null },
        s.query.trim().ifEmpty { null }?.let { "「$it」" },
    ).joinToString(" · ")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onExpand,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                text = active,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = stringResource(Res.string.hiring_result_count, shown),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text(stringResource(Res.string.hiring_clear)) }
        }
    }
}

/**
 * 取值的展示文案。**`unspecified` 与 `worldwide` 必须用不同措辞**——
 * 前者是「原文未说明」（信息缺失），后者是「不限地域」（事实），
 * 合并成同一个词等于把缺失伪造成事实。
 */
@Composable
private fun labelOf(value: String): String = when (value) {
    "worldwide" -> stringResource(Res.string.hiring_scope_worldwide)
    "restricted" -> stringResource(Res.string.hiring_scope_restricted)
    "unspecified" -> stringResource(Res.string.hiring_scope_unspecified)
    "remote" -> stringResource(Res.string.hiring_mode_remote)
    "onsite" -> stringResource(Res.string.hiring_mode_onsite)
    "hybrid" -> stringResource(Res.string.hiring_mode_hybrid)
    "full_time" -> stringResource(Res.string.hiring_type_full_time)
    "contract" -> stringResource(Res.string.hiring_type_contract)
    "internship" -> stringResource(Res.string.hiring_type_internship)
    "part_time" -> stringResource(Res.string.hiring_type_part_time)
    "backend" -> stringResource(Res.string.hiring_role_backend)
    "frontend" -> stringResource(Res.string.hiring_role_frontend)
    "fullstack" -> stringResource(Res.string.hiring_role_fullstack)
    "mobile" -> stringResource(Res.string.hiring_role_mobile)
    "android" -> stringResource(Res.string.hiring_role_android)
    "ios" -> stringResource(Res.string.hiring_role_ios)
    "ai_ml" -> stringResource(Res.string.hiring_role_ai_ml)
    "data" -> stringResource(Res.string.hiring_role_data)
    "devops_sre" -> stringResource(Res.string.hiring_role_devops_sre)
    "security" -> stringResource(Res.string.hiring_role_security)
    "embedded_hardware" -> stringResource(Res.string.hiring_role_embedded_hardware)
    "design" -> stringResource(Res.string.hiring_role_design)
    "product" -> stringResource(Res.string.hiring_role_product)
    "management" -> stringResource(Res.string.hiring_role_management)
    "other" -> stringResource(Res.string.hiring_role_other)
    // 未知取值原样透出：服务端将来加了新枚举值，界面不至于显示空白
    else -> value
}

/**
 * 卡片上的地域事实。地区多于两个时截断，但**必须带 +N 显式说明还有几个**——
 * 静默截断会把不完整的准入范围呈现成完整的，比原文没写更糟（读者会据此排除自己）。
 * 完整列表在详情工作表里。2026-08 期 233 条中有 11 条命中这条分支，最多的一条 6 个地区。
 */
private fun regionsLabel(regions: List<String>): String {
    if (regions.size <= 2) return regions.joinToString(" · ")
    return regions.take(2).joinToString(" · ") + " +" + (regions.size - 2)
}

@Composable
private fun regionFact(post: HiringPost): String =
    regionsLabel(post.allowedRegions).ifEmpty { labelOf(post.regionScope) }

/**
 * 岗位卡片。只留三个决策事实（工作方式 / 地域 / 薪资），其余进详情——时区、语言、签证、
 * 城市、技术栈全平铺时能撑到四五行且全是同一种灰，读者无从判断先看哪个。
 */
@Composable
private fun JobCard(post: HiringPost, onOpenUrl: (String) -> Unit, onOpenDetail: () -> Unit) {
    OutlinedCard(
        onClick = onOpenDetail,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = post.title ?: post.company.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (post.roleCategories.isNotEmpty()) {
                    Text(
                        text = post.roleCategories.map { labelOf(it) }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (post.roles.size > 1) {
                    Text(
                        text = stringResource(Res.string.hiring_more_roles, post.roles.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 事实标签：只陈述原文写了什么，不带任何「适不适合你」的判断
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Fact(labelOf(post.remoteKind))
                Fact(regionFact(post))
                post.salaryRaw?.let { Fact(it) }
            }

            post.summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = relativeTimeText(post.postedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    track(AppEvent.ContentAction(ContentActionKind.HN_COMMENTS, SOURCE, post.externalId))
                    onOpenUrl(post.hnUrl)
                }) {
                    Icon(Icons.Outlined.Forum, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.hiring_view_post))
                }
                post.applyUrl?.let { url ->
                    TextButton(onClick = {
                        track(AppEvent.ContentAction(ContentActionKind.APPLY, SOURCE, post.externalId))
                        onOpenUrl(url)
                    }) {
                        Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.hiring_apply))
                    }
                }
            }
        }
    }
}

/** 卡片装不下的事实：完整岗位列表，以及时区 / 语言 / 签证 / 城市 / 技术栈 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobDetailSheet(post: HiringPost, onOpenUrl: (String) -> Unit, onDismiss: () -> Unit) {
    TrendingBottomSheet(onDismissRequest = onDismiss, title = post.title ?: post.company.orEmpty()) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (post.roles.isNotEmpty()) {
                DetailRow(stringResource(Res.string.hiring_detail_all_roles), post.roles.joinToString("\n"))
            }
            DetailRow(
                stringResource(Res.string.hiring_detail_region),
                labelOf(post.remoteKind) + " · " +
                    post.allowedRegions.joinToString(" · ").ifEmpty { labelOf(post.regionScope) },
            )
            post.employment?.let { DetailRow(stringResource(Res.string.hiring_detail_employment), labelOf(it)) }
            post.salaryRaw?.let { DetailRow(stringResource(Res.string.hiring_detail_salary), it) }
            post.timezoneReq?.let { DetailRow(stringResource(Res.string.hiring_detail_timezone), it) }
            post.languageReq?.let { DetailRow(stringResource(Res.string.hiring_detail_language), it) }
            post.workAuthorization?.let { DetailRow(stringResource(Res.string.hiring_detail_work_auth), it) }
            if (post.onsiteCities.isNotEmpty()) {
                DetailRow(stringResource(Res.string.hiring_detail_cities), post.onsiteCities.joinToString(" · "))
            }
            if (post.techStack.isNotEmpty()) {
                DetailRow(stringResource(Res.string.hiring_detail_stack), post.techStack.joinToString(" · "))
            }

            Row(modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = {
                    track(AppEvent.ContentAction(ContentActionKind.HN_COMMENTS, SOURCE, post.externalId))
                    onOpenUrl(post.hnUrl)
                }) {
                    Icon(Icons.Outlined.Forum, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.hiring_view_post))
                }
                post.applyUrl?.let { url ->
                    TextButton(onClick = {
                        track(AppEvent.ContentAction(ContentActionKind.APPLY, SOURCE, post.externalId))
                        onOpenUrl(url)
                    }) {
                        Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.hiring_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 来源行：HN 图标 + 「来源：Hacker News『Ask HN: Who is hiring?』」，点击打开当月主帖。
 *
 * 兼两件事——合规上的来源标注，和整月主帖的唯一入口（卡片上的「查看原帖」跳的是单条楼层）。
 * [storyId] 为空时降级为不可点的纯文字：标注不能因为缺个 id 就消失，但也不做假的可点感。
 */
@Composable
private fun SourceHeader(storyId: String, onOpenUrl: (String) -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val clickable = storyId.isNotBlank()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable) {
                    Modifier.clickable {
                        track(AppEvent.ContentAction(ContentActionKind.HN_COMMENTS, SOURCE, storyId))
                        onOpenUrl(hnDiscussionUrl(storyId))
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = hackerNewsIcon(if (isDark) HackerNewsOrange else Color.Black),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Color.Unspecified,
        )
        Text(
            text = stringResource(Res.string.hiring_source_link),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (clickable) {
            Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 只读事实标签。刻意不用 chip：M3 的 chip 一律是交互元素，静态元数据用它会长出假的可点感 */
@Composable
private fun Fact(text: String) {
    if (text.isBlank()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun relativeTimeText(postedAt: String): String {
    val rt = DateTimeUtils.relativeTime(postedAt)
    return when (rt.unit) {
        DateTimeUtils.RelativeUnit.JUST_NOW -> stringResource(Res.string.time_just_now)
        DateTimeUtils.RelativeUnit.MINUTES -> stringResource(Res.string.time_minutes_ago, rt.value)
        DateTimeUtils.RelativeUnit.HOURS -> stringResource(Res.string.time_hours_ago, rt.value)
        DateTimeUtils.RelativeUnit.DAYS -> stringResource(Res.string.time_days_ago, rt.value)
        DateTimeUtils.RelativeUnit.ABSOLUTE -> rt.absolute
    }
}

/** 期次切换器，挂在顶栏右侧的 actions 上——标题位固定给页面名，见 [HiringScreen] */
@Composable
private fun MonthSwitcher(s: HiringUiState.Ready, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(s.month, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        TrendingDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            s.months.forEach { m ->
                DropdownMenuItem(text = { Text(m) }, onClick = {
                    expanded = false
                    onPick(m)
                })
            }
        }
    }
}

@Composable
private fun Placeholder(title: String, desc: String?, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        desc?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        action?.invoke()
    }
}
