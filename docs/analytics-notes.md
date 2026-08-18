# 埋点看板备注

解读 Aptabase 数据时容易踩的坑与口径说明。看板上的原始数字不总等于字面意思，这里记录需要人工修正的解读。

> 通用口径：留存/回访一律以 `install_id` 为准（Aptabase 默认 `user_id` 每日轮换哈希，不能跨天）。详见 memory `aptabase-retention-analysis`。

---

## 登录漏斗（`sign_in_failed`）

> ⚠️ `sign_in_failed` 自 0.22.0 起已拆成 `sign_in_canceled` / `sign_in_error`，本节描述的是 **0.21.0 及以前**的口径（历史数据仍按此解读）。新事件见下文「0.22.0 埋点断点」。本节的失败归因分析（尤其是断网被计成取消那条）对新事件同样成立。

登录失败时上报 `sign_in_failed`，携带以下属性（来源：`androidApp/.../auth/LogtoAuthManager.kt` 的 `signInFailureProps`）：

| 属性 | 含义 |
|---|---|
| `reason` | 粗粒度失败类别，做看板分桶：`user_canceled` / `timeout` / `network` / `no_browser` / `config` / `other` |
| `logto_type` | Logto 原始异常类型名（`LogtoException.message`，即 `Type.name()`），保留细粒度，`reason=other` 时靠它下钻 |
| `cause` | 底层异常类名（尽力而为，常缺省；如 DNS 失败为 `GaiException`） |

### ⚠️ 关键坑：断网登录多半计入 `user_canceled`，而非 `network`

Logto SDK 会**本地缓存 OIDC discovery 配置**。这导致断网登录的归因取决于是否为冷启动：

- **无缓存（冷启动 / 刚清数据 / 首次登录）**：点登录 → SDK 拉 OIDC 配置失败 → 抛 `UNABLE_TO_FETCH_OIDC_CONFIG` → 正确计入 **`reason=network`**（并带 `cause=GaiException` 等）。
- **有缓存（此前成功登录过的老用户）**：点登录 → **浏览器照常打开**，网络错误落在 Chrome 页面内（`ERR_INTERNET_DISCONNECTED`），SDK 层无异常 → 用户关掉标签页退出 → 计入 **`reason=user_canceled`**。

**解读修正**：`user_canceled` 桶里混入了一部分"其实是断网"的老用户，真实的主动放弃率被高估、网络失败率被低估。想看纯粹的"用户主动放弃"，需要意识到这层污染；网络问题的真实影响也比 `network` 桶显示的更大。目前 SDK 层无法把这部分从浏览器内错误里捞回来。

> 验证记录（2026-07-05，Pixel_9_2）：
> - 关闭 Logto 授权页 → `reason=user_canceled, logto_type=USER_CANCELED`
> - 飞行模式 + 清数据冷启动 → `reason=network, logto_type=UNABLE_TO_FETCH_OIDC_CONFIG, cause=GaiException`

### `timeout` vs `network` 的边界

`timeout` 只有当异常 `cause` 链里恰好保留了 `SocketTimeoutException` 时才会命中。Logto SDK 构造多数异常时**丢弃底层 `Throwable`**（如授权码换 token 失败直接传 `null` cause），因此超时常常并入 `network` 桶。需要精确区分时看 `logto_type`。

## Pro upsell 漏斗的 source 词汇统一（2026-07-07）

`pro_upsell_shown` / `pro_upsell_clicked` 的 `source` 维度统一收口到 `ProSponsor`（shared/core），全部赞助入口共用同一词汇：

| source | 入口 |
|--------|------|
| `chat_quota` | 聊天配额触顶卡的「解锁 Pro」 |
| `model_locked` | 模型选择器点锁定项 |
| `settings_language` | 设置 · 摘要语言弹窗的「赞助 Pro」 |
| `settings_donate` | 设置 · 捐助弹窗的 GitHub Sponsors 渠道 |

注意事项：
- `settings_*` 两个入口自 2026-07-07 起**新增**上报 `pro_upsell_clicked`；此前只有各自的 `settings_summary_language_sponsor` / `settings_donate_github` 事件（两者保留未动，看板续用不受影响）。跨该日期对比 clicked 总量时需注意口径变化。
- 曝光语义各入口不同：`chat_quota` 按卡片挂载去重一次，`model_locked` 是每次展开下拉都算（类广告 impression），settings 入口无 shown 事件。对比 shown→clicked 转化率须按 source 分开看。

## chat 使用量：只能查 D1，埋点侧长期为零（2026-07-27 补齐）

**补齐前**（≤ 0.22.0-beta.3）：chat 管线**只有带图发送才上报**（`chat_send_with_images`），纯文本发消息没有任何事件。后果是 Aptabase 上看不到 AI 聊天的任何使用量——7 月 D1 `chat_logs` 有 1566 条消息 / 409 个 install，同期埋点侧只有 10 条 `chat_send_with_images`。**分析 0.22.0 及更早版本的 chat 活跃/留存，一律查 D1，不要用埋点。**

**补齐后**：新增 `chat_send` 事件（`ChatViewModel.trackChatSend`），覆盖 chat 管线全部发送路径，`chat_send_with_images` 同时**删除**（信息被 `image_count` 完全覆盖，历史仅 10 条，不值得为它留双事件）。

| 属性 | 取值 |
|---|---|
| `from` | `input`（输入框）/ `quick_reply`（预设建议气泡）/ `retry`（重试失败消息） |
| `image_count` | 本次携带图片数，`0` 即纯文本 |
| `has_context` | 是否带条目上下文（从详情页进入的会话） |

对账口径：**`chat_send` 事件数 ≈ D1 `chat_logs` 行数**。两侧刻意对齐——后端 `logUserQuestion`（`src/api/chat.js`）记在配额检查之前，被限流/上游报错的请求也留一行，客户端因此也在发出前记。残差只剩埋点上报丢失一项，这条等式可直接当埋点健康度指标用。

不并入本事件的路径（各有独立事件，且走独立端点、**不写** `chat_logs`）：`detail_summary_generate`（detail-summary 端点）、`research_start`（research 端点）。所以 `chat_logs` 是纯 chat 发送量，不含这两者。

> `from=quick_reply` 顺带是爬虫指纹：已知爬虫批次的特征就是只机械点预设气泡、不读内容（详见 memory `aptabase-retention-analysis`）。清洗流量时该维度独占的 install 可优先怀疑。

## 0.22.0 埋点断点：同名不同义 + 事件改名（2026-07-22 起随 beta 发出）

0.22.0 重构出账户中心，埋点有一批**不报错但会静默扭曲趋势**的变更。跨 0.22.0 看下列曲线时必须知道断点存在。

### ⚠️ `settings_*` 五个事件同名不同义

`settings_appearance` / `settings_favorites` / `settings_subscribe` / `settings_feedback` / `settings_about` 事件名一字未改，但触发位置从「设置页」迁到了「账户中心」（`AccountScreen.kt`）。**入口层级变了，语义就变了**——跨 0.22.0 的涨跌可能纯粹来自导航结构调整，而非用户兴趣变化。

同期新增：`settings_app_settings`（账户中心 → 应用设置入口）、`settings_custom_theme`（ColorLab 自定义主题）。

### 登录漏斗事件改名，旧看板会静默归零

| 0.21.0 及以前 | 0.22.0 起 |
|---|---|
| `sign_in_failed`（带 `reason` 属性） | 拆成 `sign_in_canceled` / `sign_in_error` 两个事件 |
| `sign_in_value_shown` / `_dismiss` / `_confirm` | 随 `SignInValueDialog` 下线，由 `sign_in_method_shown` / `_dismiss` 取代 |

新漏斗：`sign_in_method_shown` → `sign_in_start` → `sign_in_success` / `sign_in_canceled` / `sign_in_error`。`sign_in_canceled` 带 `cancel_kind`（quick/wait），配合新增的 `auth_probe`（可达性探测，带 `result` / `latency_bucket`）可交叉验证上面那个「断网被伪装成取消」的老盲区——用「probe 失败 × cancel_kind=wait」估可达性失败的真实占比。

**依赖旧事件名的看板在 0.22.0 之后会归零，那是发版导致的，不是用户行为变化。**

### Pro 漏斗只剩一半

`pro_upsell_shown` 已下线（决策说明见 `ProSponsor.kt` 注释：chat 侧不再做 Pro 引导，剩余入口都是主动点击，「曝光」无从定义），`pro_upsell_clicked` 保留。**shown→clicked 转化率自 2026-07-26 起不可算**，历史 shown 数据的边界止于该日。`source` 词汇同期变更：去掉 `chat_quota` / `model_locked`，新增 `account`。

### `account_link_success` 系统性低估

`account_link_start` → `account_link_success` 的转化率是**下界，不是真值**。`success` 由 `AccountLink.markLinked()` 触发，依赖用户在 30 分钟对账窗口内切回 App；在浏览器里完成 GitHub 关联却没回来的人不会被计入。Pro 赞助的 `ProSponsor.shouldReconcile()` 是同一机制、同一问题。

### 收藏云同步无埋点

0.22.0 的收藏云同步只有老的 `favorite_toggle` / `favorite_list_view`，同步/合并本身（`prefs_favorites_merged` 等）没有任何事件，功能成效在埋点侧看不到。

## 0.23.0 埋点断点：一级 tab 重排（首页改版）

> ⚠️ **版本归属修正（2026-08-09 实测）**：本节写作时预估随 0.23.0 发出，实际上首页改版**随 1.0.0-beta.1（2026-08-05）/ 1.0.0（2026-08-08）发出**，0.23.0（2026-08-04）的 tab 词汇仍是旧的。本节所有「0.23.0 起 / 跨 0.23.0」读作「1.0.0-beta.1 起 / 跨 1.0.0-beta.1」。

首页改版把底栏改成 首页 / Picks / AI 对话 / 我的，三源收进首页用子 tab 切换，账户中心升为一级 tab。埋点随之有三处变化，跨版本看首页相关曲线时必须切段。

### `tab_switch` / `tab_double_tap_refresh` 的 `tab` 取值换代

`tab` 取的是 `HomeTab.name.lowercase()`（`HomeScreen.kt:270`），改版后是 `home` / `picks` / `chat` / `me`。**正式渠道数据里只有两代词汇**（2026-08-09 用 5~8 月导出数据核实）：

| 版本 | `tab` 取值 |
|---|---|
| 1.0.0-beta.1 起 | `home` / `picks` / `chat` / `me`（1.1.0 起 `chat` 停产） |
| 0.23.0 及以前的所有正式版 | `github` / `hackernews` / `producthunt` / `picks` |

中间一代 `trending` / `picks` **只存在于 0.23.0 之后的开发期构建**（`0.23.0-9-g…`/`-10-g…` 快照，beta 发布前已改名 `home`），正式渠道从未发出，看板上偶见的 `trending` 值来自开发机。

按 `tab` 分组的看板跨版本聚合会得到割裂的曲线，且 `home` 与旧的 `github` 等虽然落点相近，含义已从「某一源」变成「三源合一的首页」，不能直接接续。

`chat` 是个特例：AI 对话是入口不是落点（点击直接推全屏聊天页，底栏选中态留在原 tab），所以 `tab=chat` 只会出现在 `tab_switch`，永远不会出现在 `tab_double_tap_refresh`。（1.1.0 起 `tab=chat` 整体停产，见「1.1.0 埋点断点」。）

### 新增 `trending_source_switch`

记录首页内三源子 tab 的切换（`HomeScreen.kt:365`），属性 `source` 取 `github` / `hackernews` / `producthunt`。0.23.0 之前这个动作是一级 `tab_switch`，之后降级成二级——**三源的相对热度要从这个新事件看，不能再看 `tab_switch`**。

### ⚠️ 进入设置页 / 关于页出现埋点盲区

0.22.0 的 `settings_app_settings`（账户中心 →「应用设置」）随该子页删除而作废，**没有替代事件**。改版后设置页与关于页挂在底栏「⋯」菜单上，而这两个入口是纯回调透传（`HomeScreen.kt:413-414` → `HomeFloatingBar.kt:295/303`），**没有任何 `trackEvent`**。

后果：

- **「进入设置页」的量自 0.23.0 起统计不到**，只能靠设置页内的子事件（`settings_appearance` / `settings_language_change` 等）间接推断，会低估只进去看一眼就退出的用户；
- `settings_about` 仍然只在设置页那个入口上报（`SettingsScreen.kt:427`），从底栏「⋯」直接进关于页的路径不计入——所以它现在**只覆盖部分进入**，跨 0.23.0 的下跌可能纯粹是入口分流，不是兴趣下降。

补两行 `trackEvent` 即可消除（底栏菜单的 settings / about 各一条）——已于 2026-08-04（commit `e08fc26`）补上 `home_open_settings` / `home_open_about`，盲区自那之后消失，但 0.23.0 期间的空档仍在。（1.1.0 起底栏「⋯」菜单删除，这两个事件的口径再次变化，见「1.1.0 埋点断点」。）

## chat 入口漏斗：补齐分母（2026-08-05，尚未发版）

**补齐前**：chat 只有「用了多少」（`chat_send` / `detail_summary_generate` / `research_start`），**没有任何入口曝光或点击事件**——README 页浏览量、AI FAB 菜单展开全是盲的。后果是一个入口冷下来，无法区分「需求不成立」和「路径太深没人看见」，容易把曝光问题误读成需求问题而砍掉功能。

新增三个事件：

| 事件 | 触发点 | 属性 |
|---|---|---|
| `readme_view` | README 详情页每次进入（记在 `ReadmeViewModel.init`，VM 随页面实例创建、旋转复用，天然不重复） | `source`：目前恒为 `github` |
| `readme_ai_menu_open` | README 页 AI FAB 菜单展开（`ReadmeScreen`） | `detail_summary_available`：README 是否 ≥1500 字，即菜单里有没有「一键解读」项 |
| `chat_entry_click` | 所有进入 chat 的点击 | `from`：`home_tab`（1.1.0 起改为 `home_fab`，见下文）/ `readme_chat` / `readme_detail_summary` / `readme_deep_research` |

可算的转化率：

- **条目入口发现率** = `readme_ai_menu_open` / `readme_view`——三个 chat 入口都藏在 FAB 菜单里，不展开就看不见，这一步是「路径深不深」的直接度量；
- **条目入口转化率** = `chat_entry_click`(from=readme_*) / `readme_ai_menu_open`。

口径注意：

- `chat_entry_click`(from=home_tab) 与既有的 `tab_switch`(tab=chat) **同一次点击报两条**，刻意保留：前者是 chat 入口漏斗，后者是底栏行为分析，分开看板各取所需，**不要相加**。（1.1.0 起双报消失：`tab_switch(chat)` 停产，底栏入口只剩 `chat_entry_click`(from=home_fab) 一条。）
- `readme_ai_menu_open` 每次展开都算（类广告 impression），同一次浏览里反复开合会多计。要按人看时用独立 `install_id` 计数。
- **仍缺的一个分母**：chat 页内「一键详细解读」chip 的曝光没有埋点（评估后决定暂不加），所以 `detail_summary_generate` 只有分子，**解读的点击转化率算不出**。能算的只是「从 README 菜单直接点进解读入口」那条路径（`chat_entry_click`(from=readme_detail_summary)），不含进了 chat 之后才点 chip 的那部分。

> 背景：2026-08-05 评估「条目入口是否鸡肋」时发现，全时段条目会话 318 条消息 / 153 人（其中 96 人只用条目入口、从没用过通用入口），而一键解读只有 23 个 repo、深度调研 3 次。绝对量小但分母未知，无法判断是需求问题还是曝光问题——这批埋点就是为回答该问题补的，建议积累 4 周后再做取舍决策。同期还发现 `usage_events` 里 detail_summary 自 7-30 起零成功（`upstream_region_blocked`），排查前不要把 8 月的低使用量当需求信号。

## 1.1.0 埋点断点：底栏 Chat FAB 改版（2026-08-09 发布）

底栏「⋯」溢出菜单删除，AI 对话从胶囊里的伪 tab 升级为独立 FAB（commit `4717ed7`）。四个事件受影响：

| 事件 | 1.1.0 起的变化 |
|---|---|
| `chat_entry_click` | `from=home_tab` 停产，改报 **`from=home_fab`**。看「底栏进 chat」的曲线跨 1.1.0 要把两个值接起来；改用新值而非沿用旧值，正是为了能对比改版前后入口点击量的变化 |
| `tab_switch` | `tab=chat` 停产（Chat 不再是底栏 tab）。与 `chat_entry_click` 的双报随之消失，底栏 chat 点击只剩漏斗事件一条 |
| `home_open_settings` | `entry=more` 停产，只剩 `entry=topbar`（顶栏齿轮成为首页唯一设置入口）。看总量不受影响，按 entry 分组时 more 的归零是入口删除，不是行为变化 |
| `home_open_about` | 整体停产（底栏的关于入口删除）。关于页唯一入口回到设置页内，`settings_about`（`SettingsScreen.kt`）自此**重新覆盖全部进入**——0.23.0 节里「只覆盖部分进入」的告警对 ≥1.1.0 不再成立 |

## 沉浸式浏览：新增 `settings_immersive_toggle`（2026-08-10 实现，尚未发版）

设置 › 个性化「沉浸式浏览」开关的切换事件，属性 `enabled`（"true"/"false"）。默认关，
事件量 = 主动改动开关的人数上界。滚动中的收起/恢复**不打点**（高频无价值）；「开了的人
是否在用」暂无直接口径，若需要再评估补一条低频首触发事件。#86 时代拟用的
`settings_hide_bottom_bar_on_scroll` 从未发出，看板上不会有该名字。

## 首页重构：`tab_double_tap_refresh` 停产（2026-08-10 实现，尚未发版）

双击底栏 tab 触发刷新的功能（#38）随首页重构整体移除（产品决策，各页自带下拉刷新已覆盖），`tab_double_tap_refresh` 事件随之**整体停产**。看板上该事件归零是功能下线，不是采集回归。

顺带修正一个历史口径瑕疵：停产前的实现里，双击「我的」并不执行刷新但照样上报（`tab=me` 的事件全部是空动作），拿旧数据分析「双击刷新使用率」时应剔除 `tab=me`。

同版本起 `tab_switch` 口径不变仍只记底栏点击，但**触发切 tab 的路径多了一条不上报的**：系统返回键在非 Home tab 上先降级回 Home（新行为），与既有的通知深链切 tab 一样不记 `tab_switch`。按 `tab=home` 统计「主动回首页」时注意口径仍是「底栏点击」，实际回首页的总次数自此略高于事件数。

## 8 月发版埋点实测核对（2026-08-09，数据截至当日上午）

用 8 月导出 CSV 对上述断点逐条验证，**全部兑现**，同时排掉几个「看似异常实则正常」：

- **1.1.0 四断点全部命中**：`chat_entry_click` 干净切到 `from=home_fab`（旧值归零）；`tab_switch(chat)` 停产（1.0.0 尚有 24 次 → 1.1.0 为 0）；`home_open_settings` 只剩 `entry=topbar`；`home_open_about` 归零。
- **0.23.0 新增的 `digest_*`（HN 解读）上线即健康**：`digest_open` 274 次 / 105 台设备，不集中于个别设备。
- ⚠️ **chat 曲线被单设备重度用户扭曲**：0.22.0 的 528 次 `chat_send` 里 70% 来自一台设备；`chat_image_add` 各版本 94~98% 集中于同一台。0.23.0 的「暴跌」只是该用户行为变化。**看 chat 用量必须按 `install_id` 去重，不能看事件总数**。
- **`settings_app_icon` 零上报是正常的**：动态图标（#90，`dbab4c9`）在 1.1.0 tag 之后才合入 main，尚未发版。
- **`daily_picks_notification_shown` 按版本分组骤降是构成效应**：新版本用户以未开通知的新装为主。按天看每日稳定 13~21 台设备收到，无采集回归。
- 代码中有而数据里从未出现的事件仅剩异常路径（`force_update_click` / `pro_refresh_failed`）与低频入口（`settings_donate_github`），均属预期。

## 每日通知的准点性与埋点丢失（2026-08-09，7~8 月导出数据实算）

排查「通知只有打开应用才弹」（小米主力机个案）是否普遍时的结论。两层发现，一层是产品问题，一层是口径问题。

### 通知不准点是普遍现象，不是小米独有

- 收到过 `daily_picks_notification_shown` 的 55 台设备中，样本 ≥2 次且时区可估（按 `country_code` 粗配）的 32 台里，**只有 1 台基本准点**（DE，92% 落在本地 9:20~10:00）；22 台从未准点过。
- CN 设备的 shown 按北京时间看**散布全天**（9~14 时仅约四成，21 时反而是峰值之一，甚至有凌晨 0~4 时的——RARE 桶任务被推迟到充电空闲维护窗才放行的典型特征）。
- 收到过通知的设备在其首末 shown 跨度内，**实收天数中位数只有 50%**，最低的只有 11~14%。
- 89% 的 shown 前 10 分钟内有同设备前台活动（受下述埋点丢失影响该值偏高，但方向明确）：大量通知是**用户打开 app 才把被配额卡住的 worker 带起来**弹出的——与小米机上的现象同机理，各厂商激进分桶下都会发生。
- 另有 7/78 台点过开启开关（终态为开）、之后仍活跃 ≥2 天却 0 次 shown，横跨 CN/US/IN/ID、play/fdroid/r2/github 各渠道——疑似权限申请被拒或排期从未获得执行机会，埋点侧不可见（worker 权限失效时静默 finishDay，无事件）。

### ⚠️ 口径坑：后台 worker 的埋点上报系统性丢失

125 条 `daily_picks_notification_open`（点通知进 app，前台上报、可靠）里 **54 条（43%）在此前 24h 内找不到配对的 shown**——通知实际弹了（否则无从点击），但后台进程在 Aptabase 批量上传前被杀，shown 事件丢失。（少量或为 recents 重投 intent 的 open 重放，`removeExtra` 只防配置变更不防最近任务重建，但不足以解释四成。）

后果：
- **shown 的事件量与设备数都是下限**，越是「后台准时弹出」的健康样本丢得越多；上一节「每日稳定 13~21 台收到」应读作 ≥13~21 台，准点率实际比测得的高、但高多少无法从现有数据判断。
- 用 shown 评估通知功能的触达/准点前，需先在 worker 上报后强制 flush（或改用可靠上报通道），否则数据不可用于精细分析。
- `open`/`shown` 比值不能当点击率用（分母残缺 + 分子少量重放）。

## 通知迁移 AlarmManager 后的新埋点口径（2026-08-09 实现，尚未发版）

上两节的问题在闹钟迁移（feat/daily-picks-alarm 分支）里一并修掉。发版后按新口径解读：

| 事件 | 变化 |
|---|---|
| `daily_picks_notification_shown` | 新增属性：`trigger`（exact=精确闹钟 / inexact=降级档）、`attempt`（0~4，第几次尝试）、`delay_min`（实际弹出距计划 9:30 的分钟数，客户端自算）。**准点率从此直接看 `delay_min ≤ 10` 占比，不再需要按 country_code 猜时区**；迁移前基线约 12% |
| `daily_picks_notification_skipped` | 新增，终局未弹时上报。`reason=permission_revoked`（开关开着但系统通知权限被收回，此前静默）/ `gave_up`（5 次重试烧完，服务端当天没出新内容） |
| `daily_picks_notification_open` | 修掉重放：通知 intent 携带当天 date，按日去重。**跨版本对比 open 量时注意新版会低于旧版**，降幅即旧口径的重放污染 |

配套机制与对账等式：

- 终局事件上报后 receiver 留 2 秒上传窗口再收尾（Aptabase 0.0.8 无本地队列、每条即发 HTTP），后台丢失应大幅收敛；
- 按天对账：`shown + skipped ≈ 开着开关且当日闹钟响过的设备数`，残差即上报丢失率，可当埋点健康度指标；
- `open ≤ 当日 shown` 恒成立，破了说明又有重放；
- 按 `trigger` 分组可回答「不精确档实际差多少」（Android 12/13 自动精确、14+ 新装不精确；app 内无权限引导入口，评估后删除），为将来是否值得加回引导入口提供数据。

## ⚠️ 1.2.0 起 `app_started` 被后台唤醒污染（2026-08-18 定位，2026-08-18 修 receiver）

**症状**：Aptabase 看板 "Avg. Duration" 08-16 起显示 0s。该指标名叫 Avg 实为**中位数**（`median(max-min)`，按 `(user_id, session_id)` 分组，见 aptabase 源码 `etc/clickhouse/queries/key_metrics__v2.liquid`），单事件 session 的时长记 0，这类 session 一旦过半，中位数就落到 0。

**根因**：`app_started` 在 `TrendingApplication.onCreate` **无条件上报**，只要进程被创建就发——包括没有任何界面的后台唤醒。1.2.0 随 AlarmManager 迁移新增的静态 receiver 挂了 `BOOT_COMPLETED` / `TIME_SET` / `TIMEZONE_CHANGED` 三条系统广播，**由系统侧 filter 匹配、与通知开关无关**，于是全体用户的进程被反复拉起，每次留下一个只含 `app_started` 的空 session。

模拟器实测（Android 16，断网无污染）：`am kill` 后切换系统时区，logcat 出现 `am_proc_start: [...,broadcast,{.../DailyPicksAlarmReceiver}]` 紧跟 Aptabase 上报尝试，而该机通知开关是关的。

**量化**（08-05~12 旧版 vs 08-15~17 用 1.2.0 的同一批 193 台设备）：

| | 单事件 session 占比 | 含 UI 事件的 session |
|---|---|---|
| 旧版 | 24% | 63% |
| 1.2.0 | **63%** | 31% |

非孤立 session 的内部构成前后几乎不变——**真实使用行为没有退化**，纯粹是多出一批空 session。受影响 378 台里只有 22 台有过 `daily_picks_*` 事件，坐实唤醒源与通知功能无关。

**分析时的修正口径**（1.2.0 ~ 修复版本之间的数据长期适用）：

- 算 session 时长 / 参与度、日活、留存，一律**先剔除单事件 session**（一个 `session_id` 只对应一条事件）。判据取「单事件」而非「只含 `app_started`」，是为了连带盖住后台上报的 `daily_picks_notification_shown` / `skipped`——那两个必须在后台发、不该消除，但同样各自造一个空 session（量级 ~20/天）。
- ⚠️ **这条剔除规则会误杀一小撮真实使用，接受它**：`onStop` 里 `durationSeconds > 0` 的闸门让**停留不足 1 秒的前台会话发不出 `app_session`**，于是一次真实启动也只剩一条 `app_started`；进程被系统杀在 `onStop` 之前也是同样结果。实测 `duration=1s` 有 257 条（占 `app_session` 的 3.28%），分布在极短端没有塌陷，据此外推被闸掉的 <1s 约每天十几条。落到日活上还要求该设备当天**唯一**的活动就是这次启动，实际影响估计 <1%——相对不剔就有的 55% 虚高，这个代价划算。**这段区间内没有更好的办法**：`app_started` 本身分不清前台后台，没有可靠的前台标记可用。
- 剔除后中位停留 08-08~08-16 稳定在 **70~95s**，全程无恶化。
- **Sessions 数虚高约一倍**，不能直接跨 1.2.0 对比。
- **日活虚高，08-16 已达 55%**（看板 541 → 真实 246；08-01~08-12 的 11~19% 是旧版就有的基线噪声，1.2.0 的增量是 08-13 起抬到 55% 那一段）。整段数据里有 90 台设备只有 `app_started`。
- 连带失效的指标：**「装机 ≥7 天的日活」**（老设备被唤醒的概率比新装还大，08-13 之后这条曲线要按剔除口径重算）；**留存被两头污染且方向相反**——分子端老 cohort 没打开也算回访、分母端"装完即走"的设备被唤醒留在活跃盘里。08-09 那份留存基线本身取自旧版数据、未受影响，但 08-13 之后新算的 cohort 不能直接与它对比。

## ⚠️ `app_started` 改到「首次进入前台」上报（2026-08-18 实现，尚未发版）

上一节的根因修复：上报点从 `TrendingApplication.onCreate`（进程创建即报）挪到
`ProcessLifecycleOwner` 的 `onStart` 首次触发，进程内只报一次。语义从「进程创建次数」
变为「前台启动次数」——对真实用户两者等价，差异只在没有界面的后台唤醒场景。

跨该版本解读时注意两个断点：

- **`app_started` 事件量会显著下降，降幅本身就是后台唤醒的量**，可以直接拿来验证修复效果（预期回到 1.2.0 之前 `app_session / app_started ≈ 1.4~1.5` 的比值区间）。
- **同时修掉一处反向漏报**：唤醒后的进程会以 cached 状态留存一段时间，用户在这个窗口里打开 app 时 `onCreate` 不再执行，旧写法整次启动都不上报。所以降幅略小于纯粹的唤醒量，两者无法从数据里分离。

发版铺开后看板日活会从 500 多掉回 250 上下、Sessions 腰斩，**那是回归真值，不是掉量**。

### ⚠️ 剔除判据随之收窄，别沿用上一节的

上一节那条「剔除所有单事件 session」是**为污染区间定制的**，因为那时 `app_started` 分不清前后台。修复后 `app_started` 只在前台发，判据必须跟着收窄：

| 数据区间 | 剔除判据 |
|---|---|
| 1.2.0 ~ 修复版本 | 所有单事件 session（含误杀，见上一节） |
| 修复版本之后 | **只剔「仅含后台事件」的 session**，即整个 session 只有 `daily_picks_notification_shown` / `skipped` |

修复后若继续一刀切，会把「只含一条 `app_started`」的 session 全扔掉——而它们此时已经是**真实的前台短启动**（停留不足 1 秒、`app_session` 被 `durationSeconds > 0` 闸掉），每天白扔十几条。

> 为什么不能"保留 onCreate 上报 + 加 `foreground` 属性靠看板过滤"：Aptabase 的 session 由事件构成，事件发出 session 即成立；且看板首屏那四个指标走 `key_metrics` 查询，只支持 country / os / version / event_name 维度过滤，**不认自定义属性**。打了标日活照样虚高、时长照样 0s。

## 移除系统广播重排 + 新增 `daily_picks_alarm_relinked`（2026-08-18 实现，尚未发版）

上一节的 receiver 三条 `<action>` 连同 `RECEIVE_BOOT_COMPLETED` 权限一并删除（闹钟自身走显式 `PendingIntent`，不需要 intent-filter）。断链恢复从此**只剩** `reconcile()` 冷启动对账一条路径，代价由新事件量化：

| 属性 | 取值 |
|---|---|
| `reason` | `pi_missing`（PendingIntent 没了，force-stop/重启清空的典型形态）/ `stale`（有记录但过期超 6h 宽限，进程死在终局重排之前）/ `pi_missing_stale`（两者同时）/ `no_record`（开关开着却无 `next_trigger_at`） |
| `overdue_min` | 距记录触发时刻的分钟数，**可为负**（闹钟还没到点就没了＝重启清空）。`reason=no_record` 时不带此属性 |

解读要点：

- **这个事件是「决定要不要把 `BOOT_COMPLETED` 加回来」的依据**。`pi_missing` 的频率 × `overdue_min` 的分布 ≈ 重启断链造成的通知真空期。真空期短或罕见就维持现状；若普遍且长，再考虑加回（届时要连带处理组件启停与开关的双状态对账——`allowBackup=true` 且无 backup rules，换机恢复会持续制造脱节）。
- `reason=no_record` 在发版后会有**一次性尖峰**：Worker 时代升级上来的存量设备 prefs 里没有 `next_trigger_at`。之后应归零，不归零说明 `cancel` 与开关脱节，是 bug 信号。
- 分母是「开着通知开关的设备的冷启动次数」，不是全体 DAU，绝对量本来就小（8 月量级：开过通知的约 55~78 台）。

## 留存与新老用户基线（2026-08-09，5~8 月导出数据实算）

口径前提：`install_id` **2026-06-04 才上线**，此前（含 5 月整月）只有按天轮换的 `user_id`，设备级留存从 06-04 起算；「活跃」= 当天有任意埋点上报。5 月仅能看 DAU：日均 37 → 月末 19（F-Droid 06-22 上架前的小基数期）。

### 每日活跃的新老构成

- 老用户（非当日首见）占比在无新装潮的安静期（7 月下旬）达 **74~76%**；新装潮期间回落到 45~55% 是分母变大，不是流失。
- **最硬的健康指标是「装机≥7 天的日活」**：6 月底 8 台 → 8 月初 85 台，两个月阶梯式爬升、无回落。每波新装潮沉淀 5~10% 进成熟盘。
- 增长模式：**F-Droid 上架新版本 → 进「最近更新」曝光位 → 新装潮**（06-22 首上架、08-05~08 上架 0.22.0/0.23.0 各对应一波；08-05~08 四天新增 467 台 fdroid 设备）。

### 留存基线（周新增 cohort）

- 严格口径：D1 约 **15~21%**，D7 3~8%，D30 1~4%；周窗口口径：次周内回访（W1）**26~37%**，第 4 周（W4）约 12%。7 月各 cohort 无恶化趋势，最大 cohort（07-13，648 台）反而最好。
- 留下的用户是「每周打开几次」节奏而非日活型，**评估留存用周窗口比严格 Dn 公允**。
- 装完即走（仅活跃 1 天）占 59%，2~3 天再流失 24%——对工具类 App 属正常区间。

### ⚠️ 渠道留存差异极大，混渠道看总留存会失真

| 渠道 | D1 | W1 | W4 |
|---|---|---|---|
| r2（官网直下） | 19% | **45%** | **28%** |
| github | 20% | 40% | 21% |
| fdroid | 19% | 36% | 12% |
| play | **5%** | **11%** | **2%** |

Play 渠道几乎不留存（新装集中在 IN/NG/ID 的商店闲逛流量），**Play 的安装量基本是虚的**；官网/GitHub 直下用户意图最强。fdroid 是量与质的主力。任何「总留存下跌」先查是不是 Play/新装潮占比升高的构成效应。

## 登录体系换底座：漏斗事件重构（2026-08-17 实现，尚未发版）

登录实现整体替换（PR #99）后，登录相关埋点是**重写的一套**，不是在旧事件上加减。跨这个版本看任何登录曲线都要按下面切段。

### 当前全部事件

| 事件 | 属性 | 触发点 |
|---|---|---|
| `sign_in_start` | `source`, `method="sheet"` | 登录面板弹出（7 个入口共用） |
| `sign_in_start` | `source`, `method="github"` | 面板内点「使用 GitHub 继续」 |
| `sign_in_success` | `source`, `method`(`email`/`github`), `is_new` | 验证码通过 / OAuth 回跳成功 |
| `sign_in_error` | `source`, `method` | 验证码失败 / OAuth 回跳带 error |
| `sign_in_canceled` | `source`, `method="github"` | 用户放弃 GitHub 授权 |
| `sign_out` | — | 退出登录 |
| `account_link_start` | `source` | 点绑定入口 |
| `account_link_success` | — | 绑定成功 |
| `account_link_error` | `reason` | 绑定失败（回跳带 error，或发起阶段失败） |
| `account_link_canceled` | `source`, `method="github"` | 绑定流程中用户放弃授权 |

### ⚠️ `sign_in_canceled` 同名不同属性

事件名与 0.22.0 那套**完全相同**，属性却换了：旧的带 `cancel_kind`（quick/wait），新的带 `source` / `method`。看板不会报错，但**跨版本按 `cancel_kind` 分组会在新版本上全部落空**。取消总量可以跨版本比，取消类型不行。

### ⚠️ `auth_probe` 消失且无替代

可达性探测（带 `result` / `latency_bucket`）随旧实现一并删除。上文「登录漏斗」一节提到的老盲区——**断网被计成用户取消**——原本靠「probe 失败 × cancel_kind=wait」交叉验证，这个方法在新版本上做不了了。新版本里连通性失败会走 `sign_in_error`（邮箱路径给通用错误提示），但 GitHub 路径上"浏览器没打开/中途断了"仍然只会表现为 `sign_in_canceled`，与主动放弃无法区分。

### `sign_in_start` 每次登录会打两条

面板弹出打一条 `method="sheet"`，点 GitHub 再打一条 `method="github"`。**算转化率必须先按 `method` 分组**，否则分母虚高一倍。邮箱路径只有 `sheet` 那一条。

### 旧的 `account_link_success` 低估问题已消失

0.22.0 一节写的「`account_link_success` 是下界，因为依赖用户在 30 分钟对账窗口内切回 App」——那套 30 分钟窗口补偿已整体删除，改由常驻宿主在 OAuth 回跳时直接触发。**那条结论对新版本不再成立**，转化率可以当真值看。

唯一残留的低估来源变成了「回跳没能送达 App」（外部浏览器/系统层中断），与旧机制无关。
