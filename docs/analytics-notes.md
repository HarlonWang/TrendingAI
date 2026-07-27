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
