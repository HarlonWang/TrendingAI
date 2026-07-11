# 登录入口前置价值说明（Sign-in Value Proposition）设计

日期：2026-07-11
分支：feat/sign-in-value-prop

## 背景与问题

埋点数据显示：约 117 台设备尝试登录，仅 22 台成功（约 19%）；0.19.0 版本 `sign_in_failed` 的
reason 全部为 `user_canceled`——用户是在 Logto 网页授权流程中主动放弃，并非技术故障。
`repo_star` 渗透率仅 1.2%，基本被该漏斗锁死。

根因假设：用户不知道登录能得到什么（GitHub star、chat 配额），也不知道点击后会跳转浏览器走
GitHub 授权，遇到陌生网页流程即放弃。

## 现状盘点（登录入口）

| 入口 | 现状 | 是否已有价值说明 |
| --- | --- | --- |
| A. 首页 topbar 头像（`HomeScreen.kt`） | 未登录点击**直接** `signIn()` 拉起网页授权 | ❌ 零说明 |
| B/C. Trending / Readme star Snackbar | 「登录后即可 Star 该仓库」+ 登录 action | ✅ 场景化说明 |
| D. Chat 配额卡（`QuotaLimitCard.kt`） | 「登录解锁每日 10 条」CTA | ✅ |
| E. Chat 发图弹窗（`ChatInputBar.kt`） | 专门的说明 AlertDialog | ✅ |

漏斗锁死点在入口 A：它是最显眼的通用入口，却零铺垫直接把用户抛进 Logto 网页。

## 方案（已选）

**在入口 A 前置一个「登录价值说明」AlertDialog**；B–E 保持现状（已有场景化说明，避免双重弹窗）。

考虑过的替代方案：
1. 在 `AuthManager.signIn()` 内全局拦截加弹窗 —— 会给 B–E 造成双重说明/双重弹窗，放弃。
2. 用 `ModalBottomSheet` 做更丰富的登录页 —— 项目内说明类 UI 惯例是 `AlertDialog`
   （`SignInHintHost`、发图登录弹窗、登出确认等），且改动更重，YAGNI，放弃。

### 组件

新建 `shared/src/commonMain/kotlin/whl/trending/ai/ui/common/SignInValueDialog.kt`：

- Material3 `AlertDialog`（沿用项目惯例，TextButton 按钮）。
- 内容：标题 + 3 条价值点（图标 + 文案）+ 底部小字流程预期说明：
  - ⭐ 一键 Star 喜欢的仓库（对应 star 能力）
  - 💬 更多 AI 对话额度与深度解读（不体现具体次数，避免额度策略调整后文案过期）
  - 👤 查看你的 GitHub 主页与关注动态（Profile 页能力）
  - 小字：将跳转浏览器完成 GitHub 授权 —— 设定预期，直接针对「网页流程中途放弃」
- **不提收藏同步**（未上线，不承诺）。
- 参数：`source: String`（埋点来源，便于未来复用）、`onConfirm`、`onDismiss`。

### 接线（HomeScreen）

未登录点击头像：`signIn()` → `showSignInValueDialog = true`；
弹窗确认 → 关闭弹窗 + `authManager.signIn()`；取消/外点 → 关闭。
`LoggingIn` 状态下按钮已禁用，无重入问题。

### 埋点

沿用 `trackEvent`（`Platform.kt`），事件命名对齐现有 snake_case 风格，均带 `source` 属性
（当前唯一取值 `home_avatar`）：

- `sign_in_value_shown` —— 弹窗曝光
- `sign_in_value_confirm` —— 点「登录」（后续接既有 `sign_in_success`/`sign_in_failed` 形成完整漏斗）
- `sign_in_value_dismiss` —— 点「暂不」或外点/返回关闭

### 字符串

`shared/composeResources/values{,-zh}/strings.xml` 新增：
`sign_in_value_title` / `sign_in_value_star` / `sign_in_value_chat` / `sign_in_value_profile` /
`sign_in_value_footer` / `sign_in_value_dismiss`；确认按钮复用现有 `sign_in`。

## 测试与验证

- 弹窗为纯 UI 组件（无 ViewModel 逻辑），按项目现状不新增 commonTest；
- 运行时验证：构建 → 安装 Pixel_9_2 模拟器 → 未登录点头像 → 截图确认弹窗内容（中英双语）、
  「暂不」关闭、「登录」拉起 Logto 授权页。

## 成功标准

- 未登录点击首页头像先看到价值说明，确认后才进入网页授权；
- `sign_in_value_*` 漏斗可量化弹窗到授权完成的转化；
- 上线后观察 `sign_in_failed(user_canceled)` 占比与 `repo_star` 渗透变化。
