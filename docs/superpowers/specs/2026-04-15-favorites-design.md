# 收藏功能设计文档

## 背景

用户反馈希望能收藏感兴趣的项目（feedback #13）。设计一个纯本地的收藏功能，覆盖所有内容类型，在设置页提供查看入口。

## 设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 存储位置 | 纯本地（Settings） | 最简方案，零服务端改动，零新依赖 |
| 用户标识 | 无需 | 纯本地，不涉及用户体系 |
| 收藏范围 | 所有内容（Trending + Feed + Picks） | 满足用户在任意页面收藏的需求 |
| 列表入口 | 设置页子入口 | 不新增底部 Tab，保持导航结构简洁 |
| 列表功能 | 单一列表，按时间倒序 | 不需要分类/筛选，保持简单 |

## 数据模型

```kotlin
@Serializable
data class FavoriteItem(
    val url: String,           // 唯一标识（去重判断）
    val title: String,         // 显示标题
    val source: String,        // "github" / "hackernews" / "producthunt" / "picks"
    val description: String?,  // 简要描述
    val summary: String?,      // AI 中文摘要
    val savedAt: Long          // 收藏时间戳，用于排序
)
```

- `url` 作为唯一键，三种数据源都有且唯一
- `source` 区分来源，用于列表中展示来源标签
- `summary` 保存中文摘要文本，收藏列表可直接查看，无需再请求 API
- 使用 `kotlinx.serialization` 序列化为 JSON 存入 Settings

## 架构设计

### SettingsManager 扩展

在现有 `SettingsManager` 中新增：

- `favorites: Flow<List<FavoriteItem>>` — 响应式收藏列表
- `addFavorite(item: FavoriteItem)` — 添加收藏（url 去重）
- `removeFavorite(url: String)` — 移除收藏
- `isFavorite(url: String): Flow<Boolean>` — 判断是否已收藏

内部用一个 Settings key 存储序列化后的 JSON 字符串，读取时反序列化为列表。与现有 theme/language 的存储模式一致。

### 不需要新增的层

- 不需要 ViewModel — 直接 collect `SettingsManager.favorites` Flow，与现有 theme/language 模式一致
- 不需要 Repository 层 — 纯本地操作，SettingsManager 足够
- 不需要 API 改动 — 纯客户端功能

## UI 设计

### 收藏按钮

在三种内容的列表卡片上增加收藏图标按钮：
- 未收藏：空心书签/心形图标
- 已收藏：实心图标
- 点击切换收藏/取消收藏状态

涉及的卡片组件：TrendingRepo、PickItem、FeedItem 对应的 Composable。

### 设置页入口

设置页新增「我的收藏」行，放在「个性化」分组之前（最顶部），点击导航到收藏列表页。

### 收藏列表页（FavoriteListScreen）

- 按收藏时间倒序排列
- 每条显示：来源标签 + 标题 + 描述/摘要
- 点击跳转到对应 URL（复用现有 WebView/外部浏览器跳转逻辑）
- 左滑或长按删除收藏
- 列表为空时显示空状态提示

### 导航

设置页 → FavoriteListScreen，复用现有导航框架。

## 存储容量评估

Settings（SharedPreferences）存储 JSON 字符串，每条 FavoriteItem 约 500-1000 字节（含摘要），100 条约 50-100KB，完全在 Settings 的合理使用范围内。用户收藏量预期在百条级别，不需要 SQLite。

## 涉及文件

| 文件 | 改动 |
|------|------|
| `shared/.../data/model/FavoriteItem.kt` | 新增，收藏数据模型 |
| `shared/.../data/local/SettingsManager.kt` | 扩展，增加收藏相关方法和 Flow |
| `shared/.../ui/settings/SettingsScreen.kt` | 修改，新增「我的收藏」入口行 |
| `shared/.../ui/favorites/FavoriteListScreen.kt` | 新增，收藏列表页面 |
| 各内容卡片 Composable | 修改，添加收藏图标按钮 |
| 导航配置 | 修改，新增收藏列表路由 |
