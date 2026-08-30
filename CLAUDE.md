# TrendingAI 客户端

## Android Studio 运行（动态图标坑）

在**切换过 App 图标**的设备/模拟器上点 Run 会报 `Activity class {…/whl.trending.ai.MainActivityDefault} does not exist`。**报错文案有误导**：组件存在，只是被动态图标机制（#90，activity-alias 任何时刻只启用一个）disable 了——AS 的 "Default Activity" 启动策略从 merged manifest 里固定挑 `MainActivityDefault`，撞上 disabled 就报"不存在"。安装本身是成功的。

修法：Run → Edit Configurations → androidApp → Launch Options 改为 **Specified Activity** → `whl.trending.ai.MainActivity`（本体 `exported="true"`，显式启动不需要 LAUNCHER filter），一次配置后与图标状态永不打架。**不要**用 adb 强行 enable `MainActivityDefault`——app 内持久化的图标选择不会跟着变，状态不一致还可能桌面双图标。adb 脚本侧无此问题（`monkey -c LAUNCHER` 解析的是当前启用的入口）。

## 埋点（自建 eventbase，2026-08-19 起）

上报走 `wang.harlon:eventbase-kt`（仓库 `~/eventbase-kt`，服务端 `~/eventbase`），Aptabase 已下线。
调用面是 `shared/.../core/analytics/AppEvent.kt` 的 sealed class + `track(event)`，**没有裸字符串入口**。

- **新增或修改事件前**，先改 `~/eventbase/docs/telemetry-design.md` §12.9 的事件词汇表——那是唯一权威，
  **禁止在调用点就地发明事件名**；`AppEvent` 只是它的 Kotlin 投影，两边必须同步改。
- **页面浏览不要手写埋点**：`screen_viewed` 由导航层自动产生（`core/analytics/ScreenTracking.kt`），
  两个源分别是 `App.kt` 的 backStack 栈顶变化和 `HomeScreen` 的 tab 变化。
  新增页面只做一件事：路由声明实现 `core/Route.kt` 的 `Route` 并填 `screen`——漏填是编译错误。
  唯一手写的例外是登录浮层（不进 backStack，见 `LoginSheetHost`）。
  **外链、静默动作、说明弹窗不是页面**，别往 `Screen` 里加，它们进 `SettingsItemClicked` 或各自的业务事件。
- `app_opened` / `app_backgrounded` 与会话时长由库自己算（挂 ProcessLifecycleOwner），App 侧不要碰。
- **eventbase-kt 与 loginbase-kt 同一套双轨**：本机可经 `local.properties` 的 `eventbase-kt.dir` 走
  composite build，CI 与 F-Droid 源码构建一律走 `libs.versions.toml` 的 Maven 坐标。
  **改了库就发版并 bump 那里**——两条路构建的不是同一份代码，分岔不会有任何报错。
- `docs/analytics-notes.md` 只负责**本 App 的历史断点与坑**（含这次词汇换代那节）；口径、指标定义、数据模型的权威在 eventbase 仓。


分析埋点导出 CSV 或看板数据前，**必读 `docs/analytics-notes.md`**——各版本埋点断点（同名不同义、事件改名、词汇换代）、留存基线、口径坑都记在那里，跨版本看曲线不按它切段必然误读。最容易忘的三条：跨天/留存一律用 `install_id`（Aptabase 时代是因为 `user_id` 每日轮换，eventbase 时代是因为它才是安装口径，`user_id` 只有登录用户才有）；chat 用量必须按设备去重（单设备重度用户占总量可达 70%+）；分渠道看留存（Play 渠道量虚，混渠道总留存会被构成效应带偏）。

## UI 规范

- **加载指示统一**：所有加载态（页面级 / 列表 / 下拉刷新 / 按钮内 / 列表项 trailing 等内嵌场景）一律用 M3 Expressive 的 `androidx.compose.material3.LoadingIndicator`，**全 app 不用 `CircularProgressIndicator`**。
  - 小尺寸内嵌也用 `LoadingIndicator`：`Modifier.size(24.dp)` 即可（参照首页 topbar 头像登录态、`Feedback/Subscribe` 提交按钮、`Settings` 检查更新）；放在 filled 按钮内时传 `color = MaterialTheme.colorScheme.onPrimary` 保证对比度。
  - 需要 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`。
  - 下拉刷新用 `PullToRefreshBox` 时**必须显式传 `indicator`**，统一为：
    ```kotlin
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        state = pullToRefreshState,
        onRefresh = { viewModel.refresh() },
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = uiState.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) { /* content */ }
    ```
    不传 `indicator` 会回落到默认的 `CircularProgressIndicator`，与全 app 的 `LoadingIndicator` 风格不一致。参照 `Picks/Feed/Trending/Profile` 各 Screen 的写法。

- **页面骨架统一**：所有页面用 `whl.trending.ai.ui.common` 的 `TrendingScaffold` + `TrendingTopAppBar`，**不直接用 `Scaffold` + `TopAppBar`**。

  ```kotlin
  TrendingScaffold(
      topBar = {
          TrendingTopAppBar(
              title = { Text(stringResource(Res.string.xxx)) },
              navigationIcon = { /* 返回键 */ },
          )
      },
  ) { padding -> /* content */ }
  ```

  - 效果：**顶栏底色恒定为 `surfaceContainer`**，比内容区（`background`）深一档，靠这层固定的层次差把顶栏和内容分开——**不随滚动变色**。全 app 统一为 `pinnedScrollBehavior`——顶栏不走 M3 折叠。**唯一例外是首页的沉浸式浏览**（设置项，默认关）：开启时首页顶栏/子 tab 行/底栏随滚动经 `translationY` 退场（`ui/home/HomeImmersive.kt`，不改测量高度、内容不重排），与 M3 折叠机制无关；二级页不受影响。
  - **不要改回 M3 默认的滚动变色**（`surface` → `scrolledContainerColor`）。0.22.0～0.23.0 期间用过那套，代价是：顶栏下面挂子 tab 的页面头部会被劈成两截——首页的三源 `SecondaryTabRow` 是内容区的第一项、底色跟着 `background`，顶栏一变色就出现一条突兀的横向断层，而 tab 行自带的 divider 早已承担了分隔职责；pinned 行为下那个变色还是 0→1 的阶跃，断层是"啪"地出现的。参照物 Echo 从一开始就是恒定底色（`MainActivity.kt` 的 `topAppBarColors` 里 `containerColor` 与 `scrolledContainerColor` 同值）。
  - `scrollBehavior` 的创建、`Modifier.nestedScroll` 挂载、传给 `TopAppBar` 这三步都封在组件内部，经 `LocalTopAppBarScrollBehavior` 送达。**页面不要自己 `remember` behavior，也不要把它当参数往下传**。底色恒定后这套接线只剩"让顶栏正常参与 nestedScroll"这一层作用，保留是为了将来要折叠时不用重接。
  - 顶栏拆成子 composable 时，子函数里直接调 `TrendingTopAppBar` 即可，不需要任何参数透传。
  - `TrendingScaffold` 只透传各页实际用到的 `Scaffold` 参数，缺什么补什么。
  - 没有例外页：`ReadmeScreen` 曾因 WebView 滚动不走 nestedScroll、收不到变色事件而保留原生 `Scaffold` + 写死底色，顶栏改为恒定后这个理由消失，已并回统一写法。

- **设置项用卡片组**：设置页 / 关于页 /「我的」页的列表行一律用 `ui/common` 的 `SettingsGroup`，slot API 写法（`settingsItem(...)`），不要再用裸 `ListItem` + `HorizontalDivider`。图标进 40dp 圆形容器（`secondaryContainer`），头像这类自带形状的前导元素走 `leading` slot。规格与取舍见 `docs/settings-style-comparison.md`。

  - **边界：纯展示的说明页不走 `SettingsGroup`**。它是为「可点击设置项 + trailing 控件」而生，整页一行都不可点时用它会长出假的可点感；且 title/description 的两段式结构逼着每条配一个标签，「来源」「收录范围」这类零信息量的标签会以 titleMedium 粗体压过灰色正文——最醒目的东西最没用。这类页面改成每块内容一张卡、卡内标题直接写实际主体（如源名）。参照 `ui/settings/DataSourcesScreen.kt`（数据来源与更新）。**这是有意偏离，别改回 `SettingsGroup`。**

- **弹窗 / 浮层 / 下拉的选型**（见 `docs/interaction-consistency-audit.md`）：

  | 场景 | 用什么 |
  |---|---|
  | 破坏性动作确认（删除、退出、清空） | `AlertDialog`，**确认按钮 `error` 色** |
  | 纯说明 / 引导（一个动作或「知道了」） | `ui/common` 的 `InfoDialog` |
  | 单选，≤4 项且有明确锚点（某行的当前值） | `ui/common` 的 `TrendingDropdownMenu` |
  | 单选，>4 项 / 选项带描述或图标 / 需要滚动 | `ui/common` 的 `TrendingBottomSheet` |
  | 多维筛选（两个及以上维度） | `TrendingBottomSheet` + 分段控件 / Chip |
  | 系统级选择（日期、时间） | 对应的 `*PickerDialog`，别自绘 |

  - 底部浮层**一律走 `TrendingBottomSheet`**，它固定了标题字号（`titleLarge`）、水平边距（24dp）、底部留白（`navigationBarsPadding()` + 16dp）。别直接用 `ModalBottomSheet`——四处各写各的正是 0.23 之前的状态。
  - 下拉菜单**一律走 `TrendingDropdownMenu`**（24dp 圆角，与卡片、胶囊同一套大圆角语言）。需要特殊容器色/elevation 时传参，别绕开组件自己写 `DropdownMenu`。
  - 浮层里的可点选项用 `SettingsGroup` 渲染，与三个页面的卡片语言一致（登录方式选择就是这么做的）。

## 发布前冒烟（必做）

**打 tag 前必须跑 `scripts/release-smoke.sh` 并看到 PASS。** 它构建 r2 渠道 release 包（与线上同样开 R8 minify）、安装到 Pixel_9_2 模拟器、启动并检查崩溃日志与进程存活。日常开发全用 debug 包（不混淆），R8 裁剪类问题只有 release 包能暴露——0.20.0 曾因此启动即崩、发布后才发现（room 2.6.1 老 keep 规则 + R8 full mode 裁掉 WorkDatabase_Impl 构造器）。FAIL 时禁止发布，先按崩溃堆栈排查。

## 版本更新说明（whatsnew）发布流程

App 升级后首启弹的「新版本更新说明」来自 `shared/src/commonMain/composeResources/files/whatsnew.json`（随 APK 打包）。同一份内容也用于拼 GitHub Release 正文。它有两种生成模式，由**内容驱动的开关**决定，无需额外配置：

- **自动（默认）**：仓库里 `whatsnew.json` 保持占位（`version: 0.0.0`、zh/en 空）。直接 `git tag <版本> && git push origin <版本>`，CI（`release.yml` 的 Generate What's New 步骤）自动调 AI（gpt-5.4）用 commit 记录生成并打包。
- **手动**：`whatsnew.json` 的 `version` 恰等于本次 tag 且 zh/en 至少一个非空时，CI 检测到「本版已有人工内容」→ **跳过 AI，直接采用**。

### 手动模式：Claude Code 本地执行流程

当我（本地 agent）收到「**这次发布说明走本地**」「**手动写这次更新说明**」「**别让 CI 自动生成 changelog**」等**等价暗示**时，按下面步骤走（本地起草用我自己的 AI，与 CI 的 gpt-5.4 是两套、不共用脚本）：

1. 确定本次将发布的 tag `<版本>`（如 `0.9.0`）。
2. 取上个 tag 到 HEAD 的 commit：
   ```bash
   PREV=$(git tag --sort=-v:refname | grep -E '^[0-9]+\.[0-9]+\.[0-9]+' | grep -v "<版本>" | head -n 1)
   git log "$PREV"..HEAD --no-merges --oneline
   ```
3. 按**面向普通用户**口径起草中英双语更新说明（与 CI prompt 同款要求）：
   - 过滤纯内部改动（构建、CI、打包、签名、依赖升级、重构、测试、文档）及用户无直接感知的能力（统计、埋点、渠道识别、版本分发、监控、日志），只保留用户可感知的新功能、优化、修复；
   - 每条一句话，简洁、不堆技术术语；**中文 3–6 条**，英文为对应翻译。
4. 把草稿逐条交用户调整定稿。
5. 写入 `whatsnew.json`，格式 `{"version": "<版本>", "zh": [...], "en": [...]}`，**`version` 必须等于本次 tag**（否则 CI 判成自动模式、AI 会覆盖你的内容）。
6. **同步写 F-Droid fastlane changelog**（仅手动模式做，自动模式 CI 不回写）：
   - 路径 `fastlane/metadata/android/zh-CN/changelogs/<versionCode>.txt` 和 `en-US/changelogs/<versionCode>.txt`，内容与 whatsnew 的 zh/en 一致（每条前加 `• `）；
   - `versionCode = MAJOR*10000 + MINOR*100 + PATCH`（与 `androidApp/build.gradle.kts` 的 tag 推导规则一致，如 `0.20.0` → `2000`）；
   - 单文件不超过 500 字符（F-Droid 上限，超出会截断）；
   - 必须与 whatsnew.json 同一个 commit——F-Droid 从 tag 对应的 commit 读元数据。
7. `git commit` 后再 `git tag <版本> && git push origin <版本>`，让 tag 指向含手动内容的 commit。
