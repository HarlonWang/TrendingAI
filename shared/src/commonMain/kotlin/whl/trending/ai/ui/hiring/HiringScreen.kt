package whl.trending.ai.ui.hiring

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.hiring_apply
import trendingai.shared.generated.resources.hiring_clear_filters
import trendingai.shared.generated.resources.hiring_empty_filtered
import trendingai.shared.generated.resources.hiring_filter_employment
import trendingai.shared.generated.resources.hiring_filter_region
import trendingai.shared.generated.resources.hiring_filter_remote
import trendingai.shared.generated.resources.hiring_load_failed
import trendingai.shared.generated.resources.hiring_mode_hybrid
import trendingai.shared.generated.resources.hiring_mode_onsite
import trendingai.shared.generated.resources.hiring_mode_remote
import trendingai.shared.generated.resources.hiring_scope_restricted
import trendingai.shared.generated.resources.hiring_scope_unspecified
import trendingai.shared.generated.resources.hiring_scope_worldwide
import trendingai.shared.generated.resources.hiring_type_contract
import trendingai.shared.generated.resources.hiring_type_full_time
import trendingai.shared.generated.resources.hiring_type_internship
import trendingai.shared.generated.resources.hiring_type_part_time
import trendingai.shared.generated.resources.hiring_meta
import trendingai.shared.generated.resources.hiring_source_note
import trendingai.shared.generated.resources.hiring_title
import trendingai.shared.generated.resources.hiring_unavailable_desc
import trendingai.shared.generated.resources.hiring_unavailable_title
import trendingai.shared.generated.resources.hiring_view_post
import trendingai.shared.generated.resources.retry
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.ContentActionKind
import whl.trending.ai.core.analytics.ListFilter
import whl.trending.ai.core.analytics.track
import whl.trending.ai.core.Hiring
import whl.trending.ai.data.model.HiringPost
import whl.trending.ai.ui.common.TrendingDropdownMenu
import whl.trending.ai.ui.common.TrendingScaffold
import whl.trending.ai.ui.common.TrendingTopAppBar

private const val SOURCE = "hn_whoishiring"

/** 埋点维度映射。新增筛选维度时两边一起改 */
private fun HiringFilterDim.toListFilter() = when (this) {
    HiringFilterDim.REGION_SCOPE -> ListFilter.REGION_SCOPE
    HiringFilterDim.REMOTE_KIND -> ListFilter.REMOTE_KIND
    HiringFilterDim.EMPLOYMENT -> ListFilter.EMPLOYMENT
}

/**
 * HN 招聘月度专题。
 *
 * **产品边界：只呈现原文写明的约束事实，不替用户判断能不能投。**
 * 因此：不推断所在地、不做默认过滤、进入页面一律无筛选态、不记忆筛选偏好。
 * 筛选器带实时计数且首屏可见——缓解「满屏投不了」观感的正确方式是让用户看见有什么，
 * 而不是替他删掉。这条是本页成败的关键，不要把筛选器折叠成一个漏斗图标。
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
                    Text(
                        text = stringResource(Res.string.hiring_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 期次信息条：数据截止日期 + 来源标注。月更内容会陈化，呈现上必须诚实
        item {
            Text(
                text = stringResource(
                    Res.string.hiring_meta,
                    s.all.size,
                    s.lastSyncedAt?.take(10) ?: s.month,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // 筛选器：首屏可见、不折叠、每项带实时计数。
        // 计数在「其余维度已生效」的子集上算，所以点了一个条件后其他数字会跟着变
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterRow(Res.string.hiring_filter_region, HiringFilterDim.REGION_SCOPE, s, viewModel)
                FilterRow(Res.string.hiring_filter_remote, HiringFilterDim.REMOTE_KIND, s, viewModel)
                FilterRow(Res.string.hiring_filter_employment, HiringFilterDim.EMPLOYMENT, s, viewModel)
                if (s.isFiltering) {
                    TextButton(onClick = viewModel::clearFilters) {
                        Text(stringResource(Res.string.hiring_clear_filters, list.size))
                    }
                }
            }
        }

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
            JobCard(post, onOpenUrl)
        }

        // 来源标注（需求 §4.8）：入口虽长在 HN 标题栏上，页内仍须明确标注
        item {
            Text(
                text = stringResource(Res.string.hiring_source_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun FilterRow(
    label: org.jetbrains.compose.resources.StringResource,
    dim: HiringFilterDim,
    s: HiringUiState.Ready,
    viewModel: HiringViewModel,
) {
    val counts = s.counts(dim)
    if (counts.isEmpty()) return
    val selected = s.selected[dim].orEmpty()
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            counts.entries.sortedByDescending { it.value }.forEach { (value, n) ->
                FilterChip(
                    selected = value in selected,
                    onClick = {
                        // 一次筛选改了几个维度就发几条；把维度拼进 value 会让按维度分组做不了
                        track(AppEvent.ListFiltered(dim.toListFilter(), value))
                        viewModel.toggle(dim, value)
                    },
                    label = { Text("${labelOf(value)} $n") },
                )
            }
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
    "worldwide" -> "🌍 " + stringResource(Res.string.hiring_scope_worldwide)
    "restricted" -> "📍 " + stringResource(Res.string.hiring_scope_restricted)
    "unspecified" -> "— " + stringResource(Res.string.hiring_scope_unspecified)
    "remote" -> stringResource(Res.string.hiring_mode_remote)
    "onsite" -> stringResource(Res.string.hiring_mode_onsite)
    "hybrid" -> stringResource(Res.string.hiring_mode_hybrid)
    "full_time" -> stringResource(Res.string.hiring_type_full_time)
    "contract" -> stringResource(Res.string.hiring_type_contract)
    "internship" -> stringResource(Res.string.hiring_type_internship)
    "part_time" -> stringResource(Res.string.hiring_type_part_time)
    // 未知取值原样透出：服务端将来加了新枚举值，界面不至于显示空白
    else -> value
}

@Composable
private fun JobCard(post: HiringPost, onOpenUrl: (String) -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = post.title ?: post.company.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            // 事实标签行：只陈述原文写了什么，不带任何「适不适合你」的判断
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Fact(labelOf(post.remoteKind))
                Fact(labelOf(post.regionScope))
                post.allowedRegions.forEach { Fact(it) }
                post.employment?.let { Fact(labelOf(it)) }
                post.salaryRaw?.let { Fact(it) }
                post.timezoneReq?.let { Fact("🕒 $it") }
                post.languageReq?.let { Fact("🗣 $it") }
                post.workAuthorization?.let { Fact("🛂 $it") }
                post.onsiteCities.forEach { Fact("🏢 $it") }
            }

            post.summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (post.techStack.isNotEmpty()) {
                Text(
                    text = post.techStack.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    track(AppEvent.ContentAction(ContentActionKind.HN_COMMENTS, SOURCE, post.externalId))
                    onOpenUrl(post.hnUrl)
                }) {
                    Icon(Icons.Outlined.Forum, null, modifier = Modifier.size(16.dp))
                    Text(stringResource(Res.string.hiring_view_post))
                }
                post.applyUrl?.let { url ->
                    TextButton(onClick = {
                        track(AppEvent.ContentAction(ContentActionKind.APPLY, SOURCE, post.externalId))
                        onOpenUrl(url)
                    }) {
                        Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Text(stringResource(Res.string.hiring_apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(text: String) {
    AssistChip(onClick = {}, enabled = false, label = {
        Text(text, style = MaterialTheme.typography.labelSmall)
    })
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
