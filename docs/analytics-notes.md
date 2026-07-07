# 埋点看板备注

解读 Aptabase 数据时容易踩的坑与口径说明。看板上的原始数字不总等于字面意思，这里记录需要人工修正的解读。

> 通用口径：留存/回访一律以 `install_id` 为准（Aptabase 默认 `user_id` 每日轮换哈希，不能跨天）。详见 memory `aptabase-retention-analysis`。

---

## 登录漏斗（`sign_in_failed`）

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
