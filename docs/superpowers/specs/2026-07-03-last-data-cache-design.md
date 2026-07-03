# 统一「上次数据缓存」（SWR）设计

日期：2026-07-03
状态：已确认

## 背景与目标

首次进入各页面时接口加载较慢，用户只能看骨架屏。目标：为首页四个 tab（Picks / Hacker News / Product Hunt / Trending）与 GitHub 个人主页（Profile）引入统一的「上次数据缓存」——进入页面优先展示上次成功获取的数据，同时自动触发一次刷新（顶部显示下拉刷新指示器），拿到新数据后无感替换（stale-while-revalidate）。

已确认的取舍：

- **覆盖范围**：首页四 tab + Profile 首屏（用户信息 / GitHub 计数 / 贡献热力图 / feed 首屏）。下钻页（README、repos/followers 分页列表）不缓存。
- **刷新策略**：有缓存必刷新（SWR），不做 TTL。
- **Trending 参数**：只缓存默认视图（`period=daily`、`language=all`、无日期无批次），切换筛选条件仍走网络。
- **存储**：文件缓存（每个 key 一个 JSON 文件，存平台 cache 目录），不用 multiplatform-settings 塞大 JSON，不用 Ktor HttpCache（给不了先展示旧数据的 UI 语义）。

## 1. 缓存基础设施

### CacheFileStore（`data/local/CacheFileStore.kt`）

```kotlin
interface CacheFileStore {
    fun read(name: String): String?
    fun write(name: String, content: String)
    fun delete(name: String)
}
expect fun platformCacheFileStore(): CacheFileStore
```

- Android actual：`AndroidContextHolder.get()?.cacheDir` 下的 `lastdata/` 子目录，普通 File 读写；拿不到 context 时降级为 no-op。
- iOS actual：`NSCachesDirectory` 下同名子目录。
- 抽 interface 而非裸 expect fun，为了 commonTest 注入内存 fake。

### LastDataCache（`data/local/LastDataCache.kt`）

```kotlin
class LastDataCache(private val store: CacheFileStore = platformCacheFileStore()) {
    inline fun <reified T> get(key: String): T?    // 缺失/解码失败 → 删文件并返回 null
    inline fun <reified T> put(key: String, value: T)
    fun remove(key: String)
}
val globalLastDataCache by lazy { LastDataCache() }
```

- 文件名含全局 schema 版本：`v1_<key>.json`。数据模型不兼容改动时 bump 版本整体失效，无迁移。
- `Json { ignoreUnknownKeys = true }`；解码失败静默当无缓存并删除文件，绝不 crash。

### 缓存 key（lang = summaryLang，内容随语言变化必须进 key）

| key | 内容 |
|---|---|
| `picks_{lang}` | `PicksResponse` |
| `feed_hackernews_{lang}` / `feed_producthunt_{lang}` | `FeedResponse` |
| `trending_default_{lang}` | `TrendingResponse`（仅默认视图） |
| `profile` | `ProfileCache` |

## 2. 缓存写入规则（覆盖语义）

**基本规则：只有网络成功才写缓存；写入是对该 key 的全量覆盖，不做增量合并。**

- Picks / Feed / Trending：fetch 成功即 `put(key, response)` 覆盖。列表是服务端一次性返回的完整快照，无合并意义；返回空列表也照常覆盖（空是合法的最新状态）。
- 失败不碰缓存：网络失败、解码失败走不到 `put()`，旧缓存原样保留——这是「刷新失败仍能展示上次数据」的保障。
- 文件级原子性：先写临时文件再 rename，避免写一半被杀进程留下损坏文件；即使损坏，读取侧解码失败兜底删除，双保险。

**Profile 例外：覆盖 + 只增不减。** Profile 渐进加载（user 先到 → contributions / feed 后到），现有 `refresh()` 在 fetchMe 成功后会先清空 contributions 和 feed 再重拉。若 `persistSnapshot()` 在中间时刻纯覆盖，会把完整旧缓存冲成 header-only 的残缺快照。因此：

- `persistSnapshot()` 组装快照时，若当前 state 的 `contributions == null` 或 `feedItems` 为空，而旧缓存有值且 `highlightsOnly` 档位一致，则沿用旧缓存对应字段补齐后再覆盖写入；
- 新数据到达后快照即为新值，最终一致；
- 登出直接 `remove("profile")`，不受此规则影响。

一句话：列表页 = 成功即覆盖；Profile = 成功即覆盖，但残缺字段用旧值补齐后再覆盖。

## 3. 列表页统一接入模式（Picks / Feed / Trending）

三个 VM 按同一模式各自改造（不引入额外抽象层，保持手动 DI 风格），每个 VM 约 +15 行：

```
init:
  cached = cache.get(key)
  if (cached != null) → state = { isLoading=false, data=cached, isRefreshing=true }
                        → 走网络（复用现有 fetch，跳过 delay(500)）
  else                → 现状不变（isLoading 骨架屏 → 网络）

网络成功 → 更新 state + cache.put(key, response)
网络失败 → 有内容时保留、仅收起指示器；无内容才整页错误
```

要点：

- 自动刷新复用 `isRefreshing`：`PullToRefreshBox` 顶部 `LoadingIndicator` 自然转起来，UI 零新增。
- `delay(500)` 只保留给手动下拉（防指示器闪烁），SWR 自动刷新不加。
- Trending 仅默认视图读写缓存：init 读缓存只在初始参数为默认时；fetch 成功只在当前参数为默认时 put。
- 错误展示条件收紧：规则收敛在 VM 层——失败且已有内容时不置 `error`（静默保留），仅无内容时才置 `error` 触发整页错误。Screen 零改动，且规则可单测。
- 语言切换监听不动：切换后 fetch 成功写入新语言 key，冷启动按当前语言读 key，天然隔离。

## 4. Profile（GitHub 个人主页）

### 缓存载体

```kotlin
@Serializable
data class ProfileCache(
    val login: String,                        // 冗余校验字段
    val user: MeUser,                         // 已 @Serializable
    val githubUser: GithubUser?,              // 已 @Serializable
    val contributions: ContributionCalendar?, // 需补 @Serializable
    val feedItems: List<GithubFeedItem>,      // 需补 @Serializable
    val highlightsOnly: Boolean,              // 缓存的 feed 属于哪个档
)
```

feedItems 只缓存前 50 条（首屏够用，控制体积）。

### 读取（`load()` 开头）

- 命中缓存 → 一次性填充完整 state（头像/计数/热力图/feed 秒出）+ `isRefreshing=true`，走现有 `refresh()` 网络路径（已实现保留旧内容、渐进替换）。
- 缓存的 `highlightsOnly` 与当前设置不一致 → 只用 header 部分（user/计数/热力图），feed 走骨架。
- 无缓存 → 现状不变。

### 写入

`persistSnapshot()`（含只增不减规则）在三个时机调用：`fetchMe` 成功、contributions 到达、`loadMoreFeed` 每轮成功写回 state 后。每次全量 snapshot 序列化落盘。

### 清理

已有 `authState → LoggedOut` 监听里追加 `cache.remove("profile")`，杜绝换账号串号（换账号必经登出，key 不必带 login，`login` 字段仅冗余校验）。`hasLoaded` 子页返回不重拉语义不变。

## 5. 边界与错误处理

- 冷启动无网络：有缓存 → 展示缓存，刷新失败静默收起指示器；无缓存 → 现有错误页。
- 缓存损坏/模型变更：解码失败删文件回退骨架屏；不兼容改动 bump `v1` → `v2`。
- 系统清缓存：文件在 cacheDir，被清后自动回退首装行为，无需处理。

## 6. 测试（commonTest）

- `LastDataCache`：注入内存 fake `CacheFileStore`——put/get 往返、解码失败返 null 并删文件、remove。
- 各 VM：命中缓存先出数据且 `isRefreshing=true`；网络成功覆盖并写缓存；网络失败保留缓存内容；Trending 非默认参数不写缓存；Profile 登出清缓存、persistSnapshot 只增不减。

## 7. 提交方式

预计 ~12 文件、300+ 行，属大改动：从 main 切 `feat/last-data-cache` 分支走 PR。
