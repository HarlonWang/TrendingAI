# 首页改版交接文档

对齐 Echo Music 的首页改版，主体已完成并合并。本文交接**剩余工作**与**已定但不写在代码里的决策**。

- 合并 commit：`dcbf7d2 feat(home): 首页改版——tab 重排 + 悬浮胶囊底栏，整体对齐 Echo Music (#81)`
- PR：https://github.com/HarlonWang/TrendingAI/pull/81 （13 个 commit，squash 合入，分支已删）
- 参照物：https://github.com/EchoMusicApp/Echo-Music

---

## 一、最高优先级原则

**照抄 Echo 的实现，不要自作主张改行为。** 想偏离——哪怕自认为更好——必须先与用户确认。

这条是用户在改版中途立的规矩，起因是连续三次自行发挥都判断错了：给 tab 加展开文字标签（Echo 是 `showSelectedLabels = false`，纯图标）、弃用 `HorizontalFloatingToolbar` 自带的 FAB 槽位改成手排 Row（Echo 是槽位 + `widthIn(max = 480.dp)` + 外层居中）、关掉 item 的 ripple 怕和药丸糊（Echo 是先 `clip` 成与药丸同形状再 ripple，不糊）。每次都要用户指出来才回退。

动手前先读 Echo 对应实现，逐项列出「我要怎么做 / Echo 怎么做」，差异集中问一次再开工。

---

## 二、已完成的内容

| 阶段 | 内容 |
|---|---|
| 1 | 个人中心与设置拆成两页；「应用设置」子页取消，四个低频偏好平铺进设置页 |
| 2 | 底栏改为 首页 / Picks / AI 对话 / 我的；三源收进首页用 `SecondaryTabRow` 切换；「我的」升为一级 tab；撤掉顶栏头像入口与 AI 悬浮 FAB |
| 3 | 标准 NavigationBar → M3 Expressive 悬浮胶囊 + 独立「⋯」FAB（设置 / 关于）；滑动药丸选中态；`LocalContentBottomPadding` 解决遮挡 |
| 4（部分） | 切 tab 与页面转场统一 1/8 屏位移 + 200ms 淡化；底栏图标实心/描边双态 |

关键文件：

- `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeScreen.kt` — 首页骨架（623 行）
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeFloatingBar.kt` — 悬浮底栏
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/HomeTab.kt` — `HomeTab` / `TrendingSource` 枚举
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/home/KidStarIcons.kt` — 自绘 kid_star 图标
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/common/ContentBottomPadding.kt` — 底部留白透传
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/profile/ProfileScreen.kt` — 「我的」tab 内容（无脚手架）
- `shared/src/commonMain/kotlin/whl/trending/ai/ui/settings/SettingsScreen.kt` — 设置页

---

## 三、剩余待办

### A. 阶段 4 未做的两项 —— 均已了结

1. ~~**设置项卡片化**~~ — **2026-08-05 已完成**（`f18a4cf` / PR #82）。设置页、关于页、「我的」页三处列表行改用 `ui/common/SettingsGroup.kt`（连体卡片 + 40dp 圆形图标容器 + slot API）。规格与取舍见 `docs/settings-style-comparison.md`。
2. ~~**页面主标题字号层级**~~ — **2026-08-05 评估后决定不做**。原以为是"我们 `titleMedium`、Echo `displaySmall`"的简单差距，核完结构发现现状是有理由的分层：

   | 首页 tab | 顶栏结构 | 字号 |
   |---|---|---|
   | GitHub | 双行：`Trending AI ⌄` + 筛选态副标题 | `titleMedium` + `bodySmall` |
   | Picks | 双行：`Picks` + 三源与日期副标题 | `titleMedium` + `bodySmall` |
   | HN / PH、我的 | 单行 | `titleMedium` |
   | 二级页（设置/关于/收藏/反馈/…） | 单行 + 返回键 | `titleLarge`（M3 默认） |

   四个一级 tab 里**有两个是双行**，M3 的 two-line top app bar 本就该用较小主标题给副标题让位；其余两个跟着用同一档，是为了**切 tab 时标题不跳档**（我们还有 1/8 屏位移转场，跳档会很显眼）。二级页用 M3 默认的 `titleLarge`，语境不同（页面身份 + 返回键），不必与一级页同号。

   要往 Echo 的大标题走**不是改字号，而是改顶栏结构**——Echo 能用 `displaySmall` 是因为它顶栏单行无副标题；我们得先把筛选态、三源日期从顶栏挪到内容区才腾得出空间，代价是这些实时信息的可见性下降。结论：不值得，别再捡起。

### B. 验证情况

1. ~~**深色 / AMOLED 观感**~~ — **2026-08-05 已验，无需改动**。Pixel_9_2 上逐像素量了底栏各元素的 WCAG 对比度：

   | 项 | 深色 | AMOLED | 判定 |
   |---|---|---|---|
   | 选中图标 / 药丸 | 6.08:1 | 6.08:1 | ✅ 远超 3:1 |
   | 未选中图标 / 胶囊 | 7.63:1 | 7.63:1 | ✅ |
   | FAB 图标 / FAB 底 | 6.04:1 | 6.04:1 | ✅ |
   | 药丸 / 胶囊底 | 1.57:1 | 1.57:1 | 可接受（见下） |
   | 胶囊 / 页面底 | 1.10:1 | 1.20:1 | 可接受（见下） |

   两个低值都不构成问题：**药丸**的选中态是三重信号叠加（药丸底色 + 图标从描边变实心 + 图标提亮到 196 vs 未选中 174），不靠色差单打独斗；**胶囊**靠形状与阴影立起来，且 Echo 在 pureBlack 档更极端——它把胶囊直接压成 `Color.Black`（`FloatingNavigationToolbar.kt:512`），与纯黑页面零色差，我们交给主题层的 AMOLED 反而留了 1.20:1。截图放大核对过，两档下胶囊边界、药丸、实心/描边差异都清晰。

   同日修掉的深色专有 bug：一级页转场整屏白闪——根部缺兜底底色，fade 那 200ms 透出了 window 背景，而 window 主题是 `AppCompat.DayNight`、跟系统深浅走，app 内深色/AMOLED 传不过去。修法照抄 Echo `MainActivity.kt:559` 的 `BoxWithConstraints(.background(...))`，见 `App.kt` 的 `Box(Modifier.fillMaxSize().background(colorScheme.background))`。
2. ~~**登录态下的「我的」页**~~ — **2026-08-05 已验**（模拟器为 Pro + 已关联 GitHub 的登录态）：身份区的头像 / 名称 / PRO 徽章 / 邮箱、额度条的「0% used + 重置倒计时 + Pro 文案」、GitHub 入口卡的头像与 followers/repos 摘要，渲染均正常。同一轮把「我的」页的三个列表项（GitHub 入口 / 收藏 / 退出登录）一并卡片化了。

（iOS 端不作验证要求，见下方「其他产品决策」。）

### C. 文档 —— 已完成

**2026-08-05 已补进 `docs/analytics-notes.md`**（「0.23.0 埋点断点」一节）：`tab_switch` / `tab_double_tap_refresh` 的 `tab` 取值三代变迁、新增的 `trending_source_switch`、以及下面这个补记时才发现的盲区。

⚠️ 补文档时核对代码，发现本节初版列的两条与实现不符，已按实际情况更正：

- **`profile_open_settings` 并不存在**——初版写它「新增、替代 `settings_app_settings`」，代码里查无此事件；
- **`settings_about` 仍只有一个触发点**（`SettingsScreen.kt:427`），初版说的「设置页 + 底栏「⋯」菜单两个触发点」不成立——底栏菜单那条路径是纯回调透传（`HomeScreen.kt:413-414`），没有埋点。

由此暴露的盲区——**改版后「进入设置页」完全统计不到**（`settings_app_settings` 作废且无替代）、关于页只统计得到一半入口——已于同日补上 `home_open_settings` / `home_open_about` 两个事件，口径写在 `docs/analytics-notes.md`。

### D. 发版

合并了但**没发版**，线上用户看不到。流程：

1. `scripts/release-smoke.sh` 跑到 PASS（这轮碰了不少 R8 敏感面，不能省）
2. `git tag <版本> && git push origin <版本>`
3. 发版说明走 CI 自动生成，还是本地手写（见 `CLAUDE.md` 的 whatsnew 流程），未定

---

## 四、已定的决策（不要推翻）

### 与 Echo 有意保留的差异

| # | Echo | 我们 | 原因 |
|---|---|---|---|
| 1 | 底栏挂 `Scaffold.bottomBar`，在 NavHost **之外**；进二级页时竖向滑走 | 挂在 `HomeScreen` 内，跟着页面横向滑出 | 用户明确说「先保持现状」。要对齐得把 tab 状态与刷新回调提升到 `App.kt`，属中等重构 |
| 2 | 位移叠加播放器展开进度 | 无播放器 | 不适用 |
| 3 | 底栏内单独判 `pureBlack` | 交给主题层 AMOLED | 已声明 |
| 4 | 有 liquidGlass 档、iOS26 `FloatingTabBar` 档、横屏 `NavigationRail` | 都不做 | 已声明 |
| 5 | FAB 不传 `shape`（androidx 默认即圆） | 显式 `CircleShape` | CMP 版 material3 默认是圆角方形，不传拿不到同款外观 |
| 6 | 胶囊底色 pureBlack 档写死 `Color.Black` | 交给主题层（AMOLED 下仍是 `surfaceContainer`） | 同差异 #3；实测反而比 Echo 多留 1.20:1 的边界可见度 |

顶栏配色后来也对齐了 Echo：**顶栏底色恒定 `surfaceContainer`，不随滚动变色**（`fc1579e`）。改版当时用的是 M3 默认的滚动变色，代价是首页那种"顶栏下面挂子 tab"的页面头部会被劈成两截。详见 `CLAUDE.md` 的 UI 规范。

### 代码不同但行为相同

- **文字标签整套没实现**。Echo 有完整实现（`AnimatedVisibility` + `expandHorizontally(expandFrom = Start)` + 8dp Spacer + `labelLarge`），只是 `showSelectedLabels = false` 关掉了。将来若要开标签，照 Echo 的做法是**只给选中项加**，并配 12↔16dp 的 padding `animateDp`，不是给四项都挂上。
- **图标来源**：Echo 每 tab 一对自绘 drawable；我们用 material-icons 的 Filled/Outlined 对，其中 kid_star 是自绘（经典 Material Icons 集没有）。
- **底部留白作用域**：Echo 的 `LocalPlayerAwareWindowInsets` 是全局 inset，所有页面消费；我们的 `LocalContentBottomPadding` 只在首页四个内容页有值，二级页拿默认 0（因为二级页不显示底栏）。若将来做「二级页保留底栏」，这里要一起改。

### 其他产品决策

- **子源记忆不外露**：`prefs_trending_source` 每次切子源自动回写，冷启动回到上次那个源，**不做设置项**。它是「记忆」不是「偏好」，与设置页的「默认首页 tab」性质不同。（注：Echo 对应结构是 `rememberSaveable`，根本不持久化，我们这条是经用户确认的超出项）
- **子源切换不加转场**：Echo 的搜索页内部 tab 是 `when (selectedTabIndex)` 硬切，我们照抄。一级 tab 有转场、二级瞬切是对齐后的结果。
- **「关于」有两个入口**（底栏「⋯」菜单 + 设置页通用组末尾），有意为之：菜单是快捷入口，设置是完整清单。别当重复入口删掉。
- **AI 对话是入口不是 tab**：点击直接推全屏聊天页，底栏选中态留在原 tab，`HomeTab.Chat` 永远不会成为 `selectedTab` 的取值。「默认首页」可以选它（2026-09-06 起）：冷启动把聊天页压在 Home 之上，返回落 Home；通知深链在场时不压。
- **iOS 端不作验证要求**（用户 2026-08-05 决定）：这轮改动只验 Android，不跑 iOS 编译，也不看 iOS 上的三 tab 退化形态。别把它重新加回待办。

---

## 五、坑与注意事项

### 复现 Echo 环境

Echo 仓库已常驻本机：`TrendingProjects/.refs/Echo-Music` （blobless clone，含完整历史）。**直接读，不要重新 clone，更不要 clone 到会话 scratchpad**——早期版本的本文档写的就是往 scratchpad clone，结果每换一个会话都要重拉一次。详见父目录 `CLAUDE.md` 的「参照仓库」一节。

万一目录不在（换机器等），才重建：

```bash
git clone --filter=blob:none https://github.com/EchoMusicApp/Echo-Music.git \
  /Users/wanghl/TrendingProjects/.refs/Echo-Music
```

跟进 upstream：`git -C /Users/wanghl/TrendingProjects/.refs/Echo-Music pull --ff-only` 。注意 `.refs/` 被父仓库 gitignore，Grep 要显式指定路径才搜得到。

编译要点（只在确实要跑 Echo 时才需要，读源码不需要）：

- **不要用 `gradle.properties.template` 覆盖 `gradle.properties`** —— 后者是入库文件，里面的 `android.newDsl=false` 是必须的；覆盖掉会让 protobuf 插件 0.9.6 在 AGP 9 上炸（`Cannot cast ApplicationExtensionImpl to BaseExtension`）
- 需要 **JDK 21**，本机只有 17；用 Android Studio 自带的：`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- 构建 `:app:assembleArm64FossDebug`（universal 包 107MB，arm64 也有 77MB）
- **模拟器装不下**：Pixel_9_2 的 /data 只剩 400MB 左右，低于系统预留阈值，`INSTALL_FAILED_INSUFFICIENT_STORAGE`。当时是直接看用户已装的 Echo Music 5.2.82 正式版

关键源码位置（相对 `.refs/Echo-Music/`，行号为 2026-08-05 的 HEAD，会随 upstream 漂移，**以符号名为准**）：

- `app/src/main/kotlin/com/music/echo/ui/component/FloatingNavigationToolbar.kt` — 悬浮底栏（默认档）
- `app/src/main/kotlin/com/music/echo/ui/component/floatingtabbar/FloatingTabBar.kt` — iOS26 档（vendored）
- `app/src/main/kotlin/com/music/echo/ui/component/Material3SettingsGroup.kt` — 设置项卡片化的参照（见「剩余待办 A1」）
- `app/src/main/kotlin/com/music/echo/ui/screens/search/SearchScreen.kt:371` — 内部 tab `SecondaryTabRow`（我们三源子 tab 的参照）
- `app/src/main/kotlin/com/music/echo/MainActivity.kt` — `shouldShowNavigationBar`（底栏显隐白名单，:658）、`NavHost(`（转场，:1241）、`navPadding` / `collapsedBound`（底栏位移换算，:672–693）、`FloatingNavigationToolbar(` 调用点（:1120）
- `app/src/main/kotlin/com/music/echo/constants/Dimensions.kt` — 尺寸常量

### 本机环境

- **用户小米 13 上装的是 debug 签名的 release 包**（本地无 `ANDROID_KEYSTORE_PATH`，release 回落 debug 签名）。想装回正式版必须先卸载。应用内更新检查会提示升级但装不上，别拿这台试更新流程。
- 无线调试容易掉线，掉了要用户在手机上重连。
- 模拟器 `emulator` 不在 PATH：`"$ANDROID_HOME/emulator/emulator" -avd Pixel_9_2 -no-snapshot-save -no-boot-anim`

### 已知误报

Sourcery 会报 `.tabIndicatorOffset(selected.ordinal)` 「传 Int 无法编译」。**这是误报**：material3 现在有 `TabRowDefaults.tabIndicatorOffset(selectedTabIndex: Int)` 重载，正是给 `PrimaryTabRow`/`SecondaryTabRow` 的 `indicator` 槽位用的。证据：编译通过、真机渲染正确、Echo 的 `SearchScreen.kt:377` 就是这么写的。

### 其他

- 改/删 `strings.xml` 的 key 后若报 `ActualResourceCollectors` 未解析，删 `shared/build/generated/compose/resourceGenerator` 重新构建
- `HomeScreen.kt` 已 623 行（Sourcery 也提了），把 `barItems` 构建和三个私有顶栏抽出去是合理的后续重构，但不紧急
