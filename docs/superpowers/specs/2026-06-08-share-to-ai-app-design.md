# 分享到外部 AI App — 设计方案

> 状态：待评审
> 日期：2026-06-08
> 来源：用户反馈 #16 —— "或者干脆增加接口，让用户调用现有的 ai app 操作，而不是仅仅只能分享打开浏览器"

## 1. 背景与目标

当前客户端的 AI 助手是后端代理模式（`/api/chat` → ChatGPT），用户无法把一条资讯交给自己常用的外部 AI App（ChatGPT / Kimi / 豆包等）处理，只能"分享打开浏览器"。

本功能新增一个**系统分享入口**：把当前资讯拼成一段带引导语的文本，通过系统分享面板发出，用户自行选择目标 App 接收。

**为什么这样定位**（与 `product-plan.md` 的关系）：
- 零后端、零密钥、零模型集成，不触碰产品"AI 精选 + 中文解读"的核心定位，也不削弱 Pro 订阅的服务端价值抓手。
- 对全体用户都是正向增强，是该反馈中**唯一**既低成本又与定位兼容的诉求。
- 反馈中的自定义 LLM / 本地模型 / MCP 等诉求**不在本方案范围**（评估结论：与定位冲突、成本巨大，不做）。

**成功标准**：用户能在 GitHub 项目详情页一键把"引导语 + 标题 + 摘要 + 原文链接"分享到任意已安装 App；埋点能统计该功能的真实使用量，用于验证需求是否成立。

## 2. 范围

### 本期做
- `shared` 平台层新增 `shareText` 能力（Android 完整实现，iOS 最小实现 + 留接口）。
- GitHub README 详情页（`ReadmeScreen`）顶栏新增分享入口。
- 分享文本带引导语，中英文随应用语言切换。
- 埋点 `share_to_ai`。

### 本期不做（明确边界）
- **HN / PH 条目无详情页**（点击直接开外链），因此本期没有它们的分享入口。待二期"列表条目长按菜单"统一覆盖。
- iOS 真机分享面板细节打磨（iPad popover 精确锚点、键盘态 window 等）留待 iOS 专项验证。
- 自定义模型 / 本地模型 / MCP / 向量知识库 —— 不在本功能，也不规划。

## 3. 架构设计

三个互相隔离的单元，各自职责单一、可独立理解：

### 3.1 平台能力层 — `Platform.shareText`

`shared/src/commonMain/.../core/platform/Platform.kt`，与现有 `openUrl` 并列新增：

```kotlin
/** 调起系统分享面板，把纯文本交给用户选择的目标 App（AI App / 笔记 / IM 等）。 */
expect fun shareText(text: String)
```

**Android**（`Platform.android.kt`）—— 复用现有 `AndroidContextHolder` + `FLAG_ACTIVITY_NEW_TASK` 模式：

```kotlin
actual fun shareText(text: String) {
    val context = AndroidContextHolder.get() ?: return
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
```

**iOS**（`Platform.ios.kt`）—— 最小可用实现，通过 `rootViewController` 弹 `UIActivityViewController`；iPad 用 keyWindow 中心点兜底 popover，防崩溃。标注 `// TODO: 待真机验证`：

```kotlin
actual fun shareText(text: String) {
    val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val activityVc = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null
    )
    // iPad: popover 必须有锚点，否则崩溃；用 rootView 中心兜底
    activityVc.popoverPresentationController?.let { pop ->
        rootVc.view?.let { v ->
            pop.sourceView = v
            pop.sourceRect = CGRectMake(v.bounds.useContents { size.width } / 2.0, ...)
        }
    }
    rootVc.presentViewController(activityVc, animated = true, completion = null)
}
```

> iOS 块为方向性示意，真机实现时按 KMP/cinterop 实际 API 调整；本期以 Android 为验证主体。

### 3.2 分享文本构造 — 走 Compose Resources 本地化

不引入额外的纯函数拼接层，直接用 Compose Resources 的占位符格式化（YAGNI），i18n 天然跟随应用语言。

新增字符串资源（`composeResources/values/strings.xml` 与 `values-en/`）。**两个模板**应对摘要有无：

```xml
<!-- values（中文，默认） -->
<string name="share_ai_text_full">请帮我解读这条技术资讯并提炼核心要点：\n\n标题：%1$s\n\n摘要：%2$s\n\n原文：%3$s</string>
<string name="share_ai_text_brief">请帮我解读这条技术资讯并提炼核心要点：\n\n标题：%1$s\n\n原文：%2$s</string>
```

```xml
<!-- values-en -->
<string name="share_ai_text_full">Please help me read and summarize this tech update:\n\nTitle: %1$s\n\nSummary: %2$s\n\nSource: %3$s</string>
<string name="share_ai_text_brief">Please help me read and summarize this tech update:\n\nTitle: %1$s\n\nSource: %2$s</string>
```

注意事项（来自项目既有踩坑记录）：
- Compose Resources 占位符**只认 `%1$s` 位置参数**，不能用裸 `%s`。
- strings.xml 中**不反转义**，撇号需 `&apos;`（英文模板已规避，无撇号）。

### 3.3 UI 入口 — `ReadmeScreen` 顶栏

在现有 `actions`（"在 GitHub 打开" 的 OpenInNew 图标）左侧新增一个 `Share` 图标按钮：

- 数据来源复用页面已有字段：`title = "$owner/$repo"`、`summary = readmeExcerpt(uiState.html)`（已有私有函数，截断 900 字、去标签）、`url = repoUrl`。
- 在 Composable body 内根据 `summary` 是否为空选模板调用 `stringResource(...)` 得到最终文本（`stringResource` 须在 Composable 作用域读取，故在 body 计算、`onClick` 内仅调用 `shareText` + 埋点）。
- 图标用 `Icons.Default.Share`；`contentDescription` 走新增字符串 `share_to_ai`。

```kotlin
val shareText = if (summary.isNullOrBlank())
    stringResource(Res.string.share_ai_text_brief, "$owner/$repo", repoUrl)
else
    stringResource(Res.string.share_ai_text_full, "$owner/$repo", summary, repoUrl)

IconButton(onClick = {
    shareText(shareText)            // 3.1 平台能力
    trackEvent("share_to_ai", mapOf("source" to "github", "has_summary" to !summary.isNullOrBlank()))
}) { Icon(Icons.Default.Share, stringResource(Res.string.share_to_ai)) }
```

> 命名冲突提示：局部变量 `shareText` 与平台函数 `shareText` 同名，实现时给变量改名（如 `shareContent`）。

## 4. 数据流

```
用户点详情页分享图标
  → ReadmeScreen 用 stringResource 拼装本地化文本（标题/摘要/原文）
    → Platform.shareText(text)
      → Android: ACTION_SEND chooser / iOS: UIActivityViewController
        → 用户选择目标 App（ChatGPT/Kimi/豆包/笔记/IM…）
  → 同步 trackEvent("share_to_ai", {source, has_summary})
```

## 5. 错误处理

- Android `context == null`：静默 return（与现有 `openUrl` 一致）。
- 无 `ACTION_SEND` 接收方：`createChooser` 自身会展示空面板/系统提示，不额外处理。
- README 未加载完 `summary` 为空：自动退化到 `share_ai_text_brief`（仅标题 + 原文），不阻塞分享。

## 6. 测试与验证

- **平台层**为薄壳、文本构造走资源格式化，无独立业务逻辑，不写单测（YAGNI）。
- **手动验证**（Android 模拟器/真机）：
  1. README 加载完成后点分享 → 面板出现 → 选一个 App → 收到含引导语 + 标题 + 摘要 + 链接的文本。
  2. README 加载中点分享 → 退化为 brief 模板（无"摘要"段）。
  3. 切换应用语言为英文 → 分享文本变英文模板。
  4. 确认 `share_to_ai` 埋点上报（source=github、has_summary 正确）。
- iOS：本期仅编译通过 + 接口就位，真机分享留待 iOS 专项。

## 7. 工作量估计

约 1–2 人天（Android 主体 + 资源 + 埋点；iOS 最小实现）。

## 8. 后续（非本期）

- 二期：Feed/Picks 列表条目长按菜单，统一覆盖三源分享。
- iOS 真机分享面板打磨。
- 视埋点数据决定是否做"自定义快捷指令"（反馈 #4 的轻量版）。
