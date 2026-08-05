# 交互层一致性盘点：弹窗 / 底部浮层 / 下拉菜单 / 筛选

延续 `settings-style-comparison.md` 那轮的做法，这次查**交互层**：同类交互在不同页面是否用了同一种呈现方式、同一套规格。口径是**内部自洽优先**——先看我们自己一致不一致，参照仓库（`.refs/` 下三个）只在需要仲裁「该统一成哪种」时引用。

2026-08-05 成文，行号以当时的 `main`（`f18a4cf`）为准。**本文只做盘点与建议，未改任何代码。**

---

## 一、现状盘点

### 1.1 弹窗：9 处 `AlertDialog` + 1 处 `DatePickerDialog`

| 位置 | 用途 | 类型 |
|---|---|---|
| `SettingsScreen.kt:161` | 摘要语言说明（三个动作：赞助 / 关闭 / 反馈） | 说明 + 多动作 |
| `AboutScreen.kt:206` | 赞助（`text` 槽里自绘内容） | 内容型 |
| `FavoriteListScreen.kt:160` | 删除收藏确认 | **危险确认** |
| `ProfileScreen.kt:142` | 退出登录确认 | **危险确认** |
| `ProfileScreen.kt:166` | 升级前需先关联 GitHub | 引导 |
| `SponsorLinkHost.kt:52` | 已赞助但未关联，引导去关联 | 引导 |
| `SignInHintHost.kt:49` | 登录提示 / 会话过期（`dismissButton` 条件为 null） | 引导 |
| `TrendingScreen.kt:820` `InfoDialog` | 规则说明（私有组件） | 说明 |
| `WhatsNewDialog.kt:84` | 版本更新说明 | 内容型 |
| `TrendingScreen.kt:691` | 日期选择 | 选择 |

### 1.2 底部浮层：4 处 `ModalBottomSheet`

| 位置 | 用途 | 标题写法 | 水平 padding | 底部留白 | `sheetState` |
|---|---|---|---|---|---|
| `TrendingScreen.kt:576` | 筛选面板 | `BottomSheetHeader`（`titleLarge` + 帮助图标） | 16dp | 32dp | 显式传入 |
| `TrendingScreen.kt:709` | 历史批次 | 同上 | 16dp | 32dp | 显式传入 |
| `GithubProfileScreen.kt:234` | Feed 规则说明 | `FeedRulesSheet` 内部自绘 `titleLarge` | **24dp** | 32dp | 用默认 |
| `SignInMethodChooserHost.kt:62` | 登录方式选择 | 直接 `Text` + **`titleMedium`** | **24dp** | `navigationBarsPadding()` + 16dp | 用默认 |

### 1.3 下拉菜单：3 处 `DropdownMenu`

| 位置 | 用途 | 样式 |
|---|---|---|
| `SettingsScreen.kt` ×3 | 应用语言 / 摘要语言 / 默认首页 tab | 默认 |
| `ItemActionMenu.kt:70` | 列表项操作 | 默认 |
| `HomeFloatingBar.kt:282` | 底栏「⋯」菜单 | **`shape = RoundedCornerShape(24.dp)`** |

### 1.4 筛选

只有 Trending 有真正的筛选面板（`TrendingScreen.kt:576`）：`ModalBottomSheet` 内用 `SingleChoiceSegmentedButtonRow` 选时间范围、`FilterChip` 选语言、`DatePickerDialog` 选日期。Feed（HN / PH）与 Picks 没有筛选。

### 1.5 Snackbar

5 处各自 `remember { SnackbarHostState() }`：`SettingsScreen`、`FeedbackScreen`、`SubscribeScreen`、`ReadmeScreen`、`TrendingScreen`。没有全局宿主。

---

## 二、不自洽清单（按可感知度排序）

### ① 「单选」有三套并存的呈现方式 ★★★★★

同样是"从几个选项里挑一个"，我们有三种做法，**选择依据不明**：

| 场景 | 选项数 | 呈现 |
|---|---|---|
| 应用语言 / 摘要语言 / 默认首页 tab | 3–4 | `DropdownMenu` |
| 时间范围 / 历史批次 | 3–5 | `ModalBottomSheet` + `SegmentedButton` |
| 登录方式 | 2 | `ModalBottomSheet` + `ListItem` |

用户在设置页点"应用语言"弹出贴着行的小菜单，在首页点筛选弹出半屏浮层，在登录处又是另一种浮层——三种模态在同一个 app 里表达同一件事。

**建议**：定一条明确规则（见第三节的决策表），按规则回头核对——核完的结论是**三处都不用改交互模式**：

- 设置页三个下拉：选项 3–4 个、有明确锚点（行右侧当前值）、切换即时生效，**下拉是对的**；
- Trending 筛选：两个维度同屏比较，**Sheet + 分段控件是对的**；
- 登录方式：初看"2 个选项用半屏浮层"像是偏重，**但两个选项都带一句关键说明**（"Authorize in your browser"、"We'll send a verification code — no password needed"），而 `DropdownMenuItem` 只有单行 `text` 槽塞不下描述——按决策表「选项带描述 → Sheet」，**它本来就该是 Sheet**。（初版文档把这处判成了唯一的违规项，只数了选项个数、漏了带描述这一条，2026-08-05 截图核对后修正。）

所以这条**没有代码要改**，缺的只是把规则写下来，防止以后新增选择项时随手选一种。真正要动的都在下面的规格层面。

### ② 底部浮层四处规格各不相同 ★★★★☆

上面 1.2 那张表里，**标题字号、水平 padding、底部留白、是否传 `sheetState` 四项没有任何两处完全一致**。用户连续打开筛选面板和登录选择，会看到标题一大一小、内容一窄一宽。

`BottomSheetHeader`（`TrendingScreen.kt:833`）已经是个可复用的头部组件，但它是 `TrendingScreen` 的**私有函数**，另外两处 sheet 用不到，只能各写各的。

**建议**：把 `BottomSheetHeader` 提到 `ui/common`，连同一组固定规格（标题 `titleLarge`、水平 24dp、底部 `navigationBarsPadding() + 16dp`）做成 `TrendingBottomSheet` 包装。工作量：组件约 60 行 + 4 处调用改造，半天。

### ③ 危险确认按钮没有 error 色 ★★★☆☆

`FavoriteListScreen.kt:164`（删除收藏）和 `ProfileScreen.kt:146`（退出登录）的确认按钮都是**普通 `TextButton`**，与"去赞助""去关联"这类引导型弹窗的确认按钮视觉权重完全相同。M3 的破坏性动作应当用 `error` 色区分。

有意思的是，我们在**列表项**上已经这么做了——「我的」页的退出登录文字就是 `error` 色；只有它自己弹出的确认框反而没有。

**建议**：给这两处确认按钮加 `colors = ButtonDefaults.textButtonColors(contentColor = error)`。工作量：10 分钟。这是本清单里**性价比最高**的一条。

### ④ 下拉菜单圆角只有一处定制 ★★☆☆☆

`HomeFloatingBar.kt:285` 把菜单圆角设成 24dp 以呼应悬浮胶囊，另两处（设置页、列表项操作）用 M3 默认。同一个 app 里两种菜单形状。

**建议**：要么把 24dp 提成统一值（与卡片组的大圆角同语言），要么把底栏那处退回默认。倾向前者——我们整个 UI 已经是大圆角语言（卡片 24dp、胶囊全圆）。工作量：改一个默认参数 + 两处调用，20 分钟。

### ⑤ 说明型弹窗有两套写法 ★★☆☆☆

`TrendingScreen` 自己封了一个私有 `InfoDialog(title, content, onDismiss)`（:820），而其他页面要弹说明时直接手写 `AlertDialog`。同样是"标题 + 正文 + 一个确认按钮"，一处有组件、别处没有。

**建议**：把 `InfoDialog` 提到 `ui/common`，`SettingsScreen:161` 那个摘要语言说明是最直接的复用点（虽然它有三个动作，可加可选的第二动作槽）。工作量：半小时。

### ⑥ 「去关联 GitHub」两处独立实现 ★★☆☆☆

`ProfileScreen.kt:166` 与 `SponsorLinkHost.kt:52`：**触发时机不同**（前者是"升级前发现没关联"，后者是"已赞助但没关联"），文案也确实不同，所以**不是重复代码**——但两者的最终动作、按钮结构、视觉完全一样，各写一份 `AlertDialog` 有维护成本，将来改一处容易漏另一处。

**建议**：抽一个 `LinkGithubDialog(title, message, onConfirm, onDismiss)` 共用外壳，文案仍由调用方传。工作量：半小时。**优先级低**，属于代码整洁而非用户可感知。

### ⑦ Snackbar 各页自建宿主 ★☆☆☆☆

5 个页面各建各的 `SnackbarHostState`。这在 Compose 里是常规做法（`Scaffold` 级隔离），**不算问题**；只是首页四个 tab 都没有宿主，如果将来 tab 内要弹 snackbar 需要新接。**建议：不动。** 列在这里是为了说明"查过了、结论是没问题"。

---

## 三、建议补的决策规则

不自洽的根源是没有成文规则，谁写谁定。建议把下面这张表写进 `CLAUDE.md` 的 UI 规范：

| 场景 | 用什么 | 理由 |
|---|---|---|
| 破坏性动作确认（删除、退出、清空） | `AlertDialog`，确认按钮 `error` 色 | 必须打断，且要能表达"这一步有代价" |
| 纯说明 / 引导（一个动作或"知道了"） | `AlertDialog`（统一走 `ui/common` 的 `InfoDialog`） | 轻量、不需要选择 |
| 单选，≤4 项且有明确锚点（某一行的当前值） | `DropdownMenu` | 贴着触发点弹出，上下文不丢 |
| 单选，>4 项 / 选项带描述或图标 / 需要滚动 | `ModalBottomSheet` | 下拉塞不下，也读不清 |
| 多维筛选（两个及以上维度） | `ModalBottomSheet` + 分段控件 / Chip | 需要同屏比较多个维度 |
| 系统级选择（日期、时间） | 对应的 `*PickerDialog` | 用平台组件，别自绘 |

按这张表回头核对现有实现，**每一处的交互模式都能被规则解释，没有需要改模式的**。这说明问题全部集中在②③④这些**规格层面**——同一种组件的参数各写各的，而不是模式选错。

---

## 四、参照仓库怎么做（仅供仲裁）

三家在这一层的共同点：

- **Rhythm**：设置页的所有选择一律 `ModalBottomSheet`（`GoSettingsScreen.kt` 里 `showServiceSheet` / `showQualitySheet` / `showSheet` 一串），**完全不用 `DropdownMenu`**；
- **Echo**：有全局的 `BottomSheetMenu` / `BottomSheetPage`（`MainActivity.kt:1319` 挂在根部），任何页面都能调；
- **EchoFlow**：sheet 有统一封装，规格集中在 `Spacing.kt` / `ExpressiveShapes.kt`。

**它们的共性不是"都用 sheet"，而是"sheet 有统一入口和统一规格"**——这正好对应我们的第②条。至于要不要学 Rhythm 把设置页下拉也换成 sheet，我倾向不学：我们的设置项选项少（3–4 个），下拉贴着行弹出比半屏浮层更快；Rhythm 的选项动辄十几个（音质、服务商），情况不同。

---

## 五、建议的执行顺序

| 优先级 | 条目 | 工作量 | 用户可感知 |
|---|---|---|---|
| 1 | ③ 危险确认按钮 `error` 色 | 10 分钟 | 是 |
| 2 | ② 统一 sheet 规格（提 `TrendingBottomSheet` + `BottomSheetHeader`） | 半天 | 是 |
| 3 | ④ 下拉菜单圆角统一 | 20 分钟 | 弱 |
| 4 | 第三节的决策表写进 `CLAUDE.md` | 20 分钟 | 否（防未来漂移） |
| 5 | ⑤ `InfoDialog` 提到 common | 半小时 | 否 |
| 6 | ⑥ `LinkGithubDialog` 共用外壳 | 半小时 | 否 |
| — | ① 交互模式 | 无需改动 | — |
| — | ⑦ Snackbar | 不动 | — |

前三条加起来不到一天，覆盖了全部"用户能看出来"的部分。

第②条里要一并处理登录方式浮层的三处规格问题（2026-08-05 截图核对发现）：标题 `titleMedium` 比另外三处 sheet 的 `titleLarge` 矮一号；标题左边距 24dp 与下面 `ListItem` 的 16dp 对不齐；两个选项仍是裸 `ListItem`，没跟上刚统一的卡片语言——这处可直接复用 `ui/common/SettingsGroup.kt`，GitHub logo 走 `leading` slot（`tint = Color.Unspecified` 保留原配色）。
