# 收藏功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 TrendingAI 客户端添加纯本地收藏功能，用户可在 Trending / Feed / Picks 页面收藏内容，在设置页查看收藏列表。

**Architecture:** 基于现有 `SettingsManager` + `multiplatform-settings` 扩展，将收藏列表序列化为 JSON 存入 Settings。新增 `FavoriteItem` 数据模型、`FavoriteListScreen` 页面，在三种卡片组件中添加收藏按钮。

**Tech Stack:** Kotlin, Compose Multiplatform, multiplatform-settings (coroutines), kotlinx.serialization

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `shared/.../data/model/FavoriteItem.kt` | 新增 | 收藏数据模型 |
| `shared/.../data/local/SettingsManager.kt` | 修改 | 收藏的增删查方法 + Flow |
| `shared/src/commonMain/composeResources/values/strings.xml` | 修改 | 英文字符串 |
| `shared/src/commonMain/composeResources/values-zh/strings.xml` | 修改 | 中文字符串 |
| `shared/.../ui/favorites/FavoriteListScreen.kt` | 新增 | 收藏列表页面 |
| `shared/.../ui/trending/TrendingScreen.kt` | 修改 | RepoItem 添加收藏按钮 |
| `shared/.../ui/feed/FeedScreen.kt` | 修改 | FeedItemCard 添加收藏按钮 |
| `shared/.../ui/picks/PicksScreen.kt` | 修改 | DeepDiveCard / SpeedReadItem / ControversyGroup 添加收藏按钮 |
| `shared/.../ui/settings/SettingsScreen.kt` | 修改 | 新增「我的收藏」入口 |
| `shared/.../core/App.kt` | 修改 | 新增 Favorites 路由 |

---

### Task 1: 数据模型 — FavoriteItem

**Files:**
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/data/model/FavoriteItem.kt`

- [ ] **Step 1: 创建 FavoriteItem 数据类**

```kotlin
package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteItem(
    val url: String,
    val title: String,
    val source: String,
    val description: String? = null,
    val summary: String? = null,
    val savedAt: Long = 0L
)
```

- [ ] **Step 2: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/data/model/FavoriteItem.kt
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: add FavoriteItem data model"
```

---

### Task 2: SettingsManager 扩展 — 收藏存储

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/data/local/SettingsManager.kt`

- [ ] **Step 1: 添加 import 和 favorites 常量**

在 `SettingsManager.kt` 顶部添加 import：

```kotlin
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import whl.trending.ai.data.model.FavoriteItem
```

在 `SettingsManager` 类中添加 key 常量：

```kotlin
private val FAVORITES_KEY = "prefs_favorites"
```

- [ ] **Step 2: 添加 favorites Flow**

在 `SettingsManager` 类中添加：

```kotlin
val favorites: Flow<List<FavoriteItem>> = settings.getStringOrNullFlow(FAVORITES_KEY)
    .map { json ->
        if (json.isNullOrEmpty()) emptyList()
        else runCatching { Json.decodeFromString<List<FavoriteItem>>(json) }.getOrElse { emptyList() }
    }
```

- [ ] **Step 3: 添加 addFavorite / removeFavorite / isFavorite 方法**

```kotlin
fun addFavorite(item: FavoriteItem) {
    val current = getCurrentFavorites()
    if (current.any { it.url == item.url }) return
    val updated = listOf(item) + current
    settings.putString(FAVORITES_KEY, Json.encodeToString(updated))
}

fun removeFavorite(url: String) {
    val current = getCurrentFavorites()
    val updated = current.filter { it.url != url }
    settings.putString(FAVORITES_KEY, Json.encodeToString(updated))
}

fun isFavorite(url: String): Flow<Boolean> = favorites.map { list -> list.any { it.url == url } }

private fun getCurrentFavorites(): List<FavoriteItem> {
    val json = settings.getStringOrNull(FAVORITES_KEY) ?: return emptyList()
    return runCatching { Json.decodeFromString<List<FavoriteItem>>(json) }.getOrElse { emptyList() }
}
```

- [ ] **Step 4: 验证编译**

Run: `cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/data/local/SettingsManager.kt
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: add favorites storage to SettingsManager"
```

---

### Task 3: 字符串资源

**Files:**
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values-zh/strings.xml`

- [ ] **Step 1: 添加英文字符串**

在 `values/strings.xml` 的 `</resources>` 前添加：

```xml
    <string name="favorites">Favorites</string>
    <string name="favorites_empty">No favorites yet</string>
    <string name="favorites_empty_hint">Tap the bookmark icon on any item to save it here</string>
    <string name="favorites_removed">Removed from favorites</string>
```

- [ ] **Step 2: 添加中文字符串**

在 `values-zh/strings.xml` 的 `</resources>` 前添加：

```xml
    <string name="favorites">我的收藏</string>
    <string name="favorites_empty">暂无收藏</string>
    <string name="favorites_empty_hint">点击任意内容的书签图标即可收藏</string>
    <string name="favorites_removed">已取消收藏</string>
```

- [ ] **Step 3: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/composeResources/
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: add favorites string resources"
```

---

### Task 4: 收藏列表页面 — FavoriteListScreen

**Files:**
- Create: `shared/src/commonMain/kotlin/whl/trending/ai/ui/favorites/FavoriteListScreen.kt`

- [ ] **Step 1: 创建 FavoriteListScreen**

```kotlin
package whl.trending.ai.ui.favorites

import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import whl.trending.ai.core.platform.openUrl
import whl.trending.ai.ui.common.AiSummaryBox
import whl.trending.ai.ui.picks.SourceTag

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.back
import trendingai.shared.generated.resources.favorites
import trendingai.shared.generated.resources.favorites_empty
import trendingai.shared.generated.resources.favorites_empty_hint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteListScreen(onBack: () -> Unit) {
    val favorites by globalSettingsManager.favorites.collectAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.favorites)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.favorites_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.favorites_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            ) {
                itemsIndexed(
                    favorites,
                    key = { _, item -> item.url }
                ) { index, item ->
                    FavoriteCard(
                        item = item,
                        onRemove = { globalSettingsManager.removeFavorite(item.url) }
                    )
                    if (index < favorites.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteCard(item: FavoriteItem, onRemove: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surface
            )
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable { openUrl(item.url) }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行：来源标签 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceTag(source = item.source, label = item.source)
                Text(
                    text = formatSavedAt(item.savedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 标题
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 描述
            if (!item.description.isNullOrBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // AI 摘要（复用 AiSummaryBox 组件，保持样式一致）
            if (!item.summary.isNullOrBlank()) {
                AiSummaryBox(summary = item.summary)
            }
        }
    }
}

private fun formatSavedAt(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.monthNumber}/${local.dayOfMonth}"
}
```

**注意**：`SourceTag` 组件目前定义在 `PicksScreen.kt` 中且为 `private`。需要将其可见性改为 `internal`（在 Task 7 中处理），或在此文件中重新实现。考虑到 `SourceTag` 在收藏列表和 Picks 页面都需要用，更好的做法是将它提取到公共包。这将在 Task 7 的第一步处理。

- [ ] **Step 2: 验证编译**

此步暂时可能因 `SourceTag` 可见性问题编译失败，将在 Task 7 中一并解决。

- [ ] **Step 3: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/ui/favorites/FavoriteListScreen.kt
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: add FavoriteListScreen"
```

---

### Task 5: Trending 卡片添加收藏按钮

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/trending/TrendingScreen.kt`

- [ ] **Step 1: 添加 import**

在 `TrendingScreen.kt` 顶部添加：

```kotlin
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.collectAsState
import kotlinx.datetime.Clock
```

- [ ] **Step 2: 修改 RepoItem 签名和添加收藏按钮**

修改 `RepoItem` 函数（约第 259 行），在 Column 内最后添加收藏按钮行。

将 `RepoItem` 改为：

```kotlin
@Composable
private fun RepoItem(index: Int, repo: TrendingRepo, since: String, onClick: () -> Unit) {
    val isFavorite by globalSettingsManager.isFavorite(repo.url).collectAsState(false)

    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.W500)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${repo.author}/${repo.repoName}",
                fontSize = 16.sp,
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (repo.description.isNotBlank()) {
                Text(
                    text = repo.description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (repo.aiSummaries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repo.aiSummaries.forEach { summary ->
                        if (summary.content.isNotEmpty()) {
                            AiSummaryBox(summary.content)
                        }
                    }
                }
            }

            if (repo.builtBy.isNotEmpty()) {
                ContributorAvatars(contributors = repo.builtBy)
            }

            RepoMetadata(repo = repo, since = since)
        }
        IconButton(
            onClick = {
                if (isFavorite) {
                    globalSettingsManager.removeFavorite(repo.url)
                } else {
                    globalSettingsManager.addFavorite(
                        FavoriteItem(
                            url = repo.url,
                            title = "${repo.author}/${repo.repoName}",
                            source = "github",
                            description = repo.description.takeIf { it.isNotBlank() },
                            summary = repo.aiSummaries.firstOrNull()?.content,
                            savedAt = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}
```

关键改动：
1. Column 添加 `Modifier.weight(1f)` 让它占满剩余空间
2. Row 末尾添加 `IconButton` 收藏按钮
3. 通过 `globalSettingsManager.isFavorite(repo.url)` 判断状态

- [ ] **Step 3: 验证编译**

Run: `cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/ui/trending/TrendingScreen.kt
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: add favorite button to TrendingScreen"
```

---

### Task 6: Feed 卡片添加收藏按钮

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/feed/FeedScreen.kt`

- [ ] **Step 1: 添加 import**

在 `FeedScreen.kt` 顶部添加：

```kotlin
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.datetime.Clock
```

- [ ] **Step 2: 修改 FeedItemCard 添加收藏按钮**

将 `FeedItemCard` 改为：

```kotlin
@Composable
private fun FeedItemCard(index: Int, item: FeedItem) {
    val isFavorite by globalSettingsManager.isFavorite(item.url).collectAsState(false)

    Row(
        modifier = Modifier
            .clickable {
                trackItemClick(
                    source = item.source,
                    rank = index + 1,
                    title = item.title
                )
                openUrl(item.url)
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.W500)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!item.description.isNullOrBlank()) {
                Text(
                    text = item.description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = if (item.source == "producthunt") 2 else Int.MAX_VALUE,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!item.summary.isNullOrBlank()) {
                AiSummaryBox(summary = item.summary)
            }
            FeedItemMetadata(item = item)
        }
        IconButton(
            onClick = {
                if (isFavorite) {
                    globalSettingsManager.removeFavorite(item.url)
                } else {
                    globalSettingsManager.addFavorite(
                        FavoriteItem(
                            url = item.url,
                            title = item.title,
                            source = item.source,
                            description = item.description,
                            summary = item.summary,
                            savedAt = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}
```

关键改动与 Task 5 相同：Column 添加 `weight(1f)`，Row 末尾添加 IconButton。

- [ ] **Step 3: 验证编译**

Run: `cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/ui/feed/FeedScreen.kt
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: add favorite button to FeedScreen"
```

---

### Task 7: Picks 卡片添加收藏按钮

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/picks/PicksScreen.kt`

这是最复杂的 Task，因为 Picks 有三种卡片类型（DeepDiveCard、SpeedReadItem、ControversyGroup），且 `SourceTag` 需要提取为公共组件。

- [ ] **Step 1: 将 SourceTag 和 formatScore 可见性改为 internal**

在 `PicksScreen.kt` 中找到 `SourceTag` 和 `formatScore`（约第 419 和 442 行），将 `private` 改为 `internal`：

```kotlin
// 原来是 private fun，改为 internal
@Composable
internal fun SourceTag(source: String, label: String) { ... }

internal fun formatScore(source: String, score: Int): String { ... }
```

- [ ] **Step 2: 添加 import**

在 `PicksScreen.kt` 顶部添加：

```kotlin
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.FavoriteItem
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.IconButton
import kotlinx.datetime.Clock
```

- [ ] **Step 3: 修改 DeepDiveCard 添加收藏按钮**

在 DeepDiveCard 的头部区域 Row 中，在 SourceTag 之后添加收藏按钮。修改后的头部区域：

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeepDiveCard(item: PickItem, onClick: () -> Unit) {
    val isFavorite by globalSettingsManager.isFavorite(item.url).collectAsState(false)

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 头部区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            SourceTag(source = item.source, label = "${item.sourceLabel} ${formatScore(item.source, item.score)}")
            IconButton(
                onClick = {
                    if (isFavorite) {
                        globalSettingsManager.removeFavorite(item.url)
                    } else {
                        globalSettingsManager.addFavorite(
                            FavoriteItem(
                                url = item.url,
                                title = item.title,
                                source = item.source,
                                description = item.analysis?.core,
                                summary = item.analysis?.whyImportant,
                                savedAt = Clock.System.now().toEpochMilliseconds()
                            )
                        )
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }

        // 正文区域（保持不变）
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item.analysis?.let { analysis ->
                Text(
                    text = analysis.core,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = analysis.whyImportant,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val actionLabel = stringResource(Res.string.picks_label_action)
                val alternativesLabel = stringResource(Res.string.picks_label_alternatives)
                val termsLabel = stringResource(Res.string.picks_label_terms)
                val labels = buildList {
                    analysis.action?.takeIf { it.isNotBlank() }?.let {
                        add(actionLabel to it)
                    }
                    analysis.alternatives?.takeIf { it.isNotBlank() }?.let {
                        add(alternativesLabel to it)
                    }
                    analysis.terms?.takeIf { it.isNotEmpty() }?.let {
                        add(termsLabel to it.joinToString("、"))
                    }
                }
                if (labels.isNotEmpty()) {
                    HorizontalDivider()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        labels.forEach { (label, value) ->
                            LabeledText(label = label, value = value)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: 修改 SpeedReadItem 添加收藏按钮**

在 SpeedReadItem 的第一行 Row 中，在 SourceTag 后添加收藏按钮：

```kotlin
@Composable
private fun SpeedReadItem(item: PickItem, onClick: () -> Unit) {
    val isFavorite by globalSettingsManager.isFavorite(item.url).collectAsState(false)

    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 序号
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${item.rank}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // 标题
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 来源标签 + 分数
                SourceTag(source = item.source, label = "${item.sourceLabel} ${formatScore(item.source, item.score)}")

                // 收藏按钮
                IconButton(
                    onClick = {
                        if (isFavorite) {
                            globalSettingsManager.removeFavorite(item.url)
                        } else {
                            globalSettingsManager.addFavorite(
                                FavoriteItem(
                                    url = item.url,
                                    title = item.title,
                                    source = item.source,
                                    description = item.description,
                                    summary = item.summary,
                                    savedAt = Clock.System.now().toEpochMilliseconds()
                                )
                            )
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }

            // AI 总结
            if (!item.summary.isNullOrBlank()) {
                AiSummaryBox(
                    summary = item.summary,
                    modifier = Modifier.padding(start = 34.dp)
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}
```

- [ ] **Step 5: 修改 ControversyGroup 添加收藏按钮**

在 ControversyGroup 的每个 item 的标题行中添加收藏按钮：

```kotlin
@Composable
private fun ControversyGroup(items: List<PickItem>, onItemClick: (PickItem) -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        items.forEachIndexed { index, item ->
            val isFavorite by globalSettingsManager.isFavorite(item.url).collectAsState(false)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) }
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SourceTag(source = item.source, label = item.sourceLabel)
                    IconButton(
                        onClick = {
                            if (isFavorite) {
                                globalSettingsManager.removeFavorite(item.url)
                            } else {
                                globalSettingsManager.addFavorite(
                                    FavoriteItem(
                                        url = item.url,
                                        title = item.title,
                                        source = item.source,
                                        description = item.analysis?.core,
                                        summary = item.analysis?.whyImportant,
                                        savedAt = Clock.System.now().toEpochMilliseconds()
                                    )
                                )
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }

                item.analysis?.let { analysis ->
                    Text(
                        text = analysis.core,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (index < items.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
```

- [ ] **Step 6: 验证编译**

Run: `cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/ui/picks/PicksScreen.kt
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: add favorite button to PicksScreen"
```

---

### Task 8: 设置页入口 + 导航路由

**Files:**
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/ui/settings/SettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/whl/trending/ai/core/App.kt`

- [ ] **Step 1: 修改 SettingsScreen 添加收藏入口**

在 `SettingsScreen.kt` 中添加 import：

```kotlin
import androidx.compose.material.icons.filled.Bookmark
import trendingai.shared.generated.resources.favorites
```

修改 `SettingsScreen` 函数签名，添加 `onNavigateToFavorites` 回调：

```kotlin
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToFeedback: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToWebPage: (url: String, title: String) -> Unit = { _, _ -> }
)
```

在 LazyColumn 中，**在「个性化」分组之前**插入收藏入口：

```kotlin
        LazyColumn(...) {
            // 我的收藏
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.favorites)) },
                    leadingContent = { Icon(Icons.Default.Bookmark, null) },
                    modifier = Modifier.clickable {
                        trackEvent("settings_favorites")
                        onNavigateToFavorites()
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // 分组 1: 个性化（原有代码）
            item { SettingsHeader(stringResource(Res.string.personalization)) }
            ...
```

- [ ] **Step 2: 修改 App.kt 添加导航路由**

在 `App.kt` 中添加 import：

```kotlin
import whl.trending.ai.ui.favorites.FavoriteListScreen
```

在导航类型定义处添加：

```kotlin
data object Favorites
```

在 `Settings` 的 `NavEntry` 中传递 `onNavigateToFavorites`：

```kotlin
is Settings -> NavEntry(key) {
    SettingsScreen(
        onBack = {
            backStack.removeLastOrNull()
        },
        onNavigateToFeedback = {
            backStack.add(Feedback)
        },
        onNavigateToFavorites = {
            backStack.add(Favorites)
        },
        onNavigateToWebPage = { url, title ->
            backStack.add(WebPage(url, title))
        }
    )
}
```

在 `entryProvider` 的 `when` 中添加 `Favorites` 分支（在 `Settings` 分支之后）：

```kotlin
is Favorites -> NavEntry(key) {
    FavoriteListScreen(
        onBack = { backStack.removeLastOrNull() }
    )
}
```

- [ ] **Step 3: 验证编译**

Run: `cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git -C /Users/wanghl/TrendingProjects/TrendingAI add shared/src/commonMain/kotlin/whl/trending/ai/ui/settings/SettingsScreen.kt shared/src/commonMain/kotlin/whl/trending/ai/core/App.kt
git -C /Users/wanghl/TrendingProjects/TrendingAI commit -m "feat: add favorites entry in settings and navigation route"
```

---

### Task 9: 最终验证

- [ ] **Step 1: 全量编译**

Run: `cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 检查 kotlinx-datetime 依赖**

FavoriteListScreen 中使用了 `kotlinx.datetime`。检查项目是否已有此依赖：

Run: `grep -r "kotlinx-datetime\|kotlinx.datetime" /Users/wanghl/TrendingProjects/TrendingAI/gradle/`

如果没有，需要在 `libs.versions.toml` 和 `shared/build.gradle.kts` 中添加。可替代方案：将 `formatSavedAt` 改为简单的不依赖 kotlinx-datetime 的实现：

```kotlin
// 如果没有 kotlinx-datetime 依赖，改用简单格式化
private fun formatSavedAt(timestamp: Long): String {
    // 使用 platform expect/actual 或简单展示相对时间
    // 最简方案：直接不显示日期，改为空字符串
    return ""
}
```

或者添加 kotlinx-datetime 依赖（推荐，因为 Ktor 通常已间接引入）。

- [ ] **Step 3: 构建 Android APK 验证**

Run: `cd /Users/wanghl/TrendingProjects/TrendingAI && ./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL
