# 更新弹窗展示版本更新内容 — 设计方案

日期:2026-07-12
分支:feat/update-dialog-release-notes

## 背景与问题

apk 渠道的更新提示弹窗(`androidLibrary/updater` 的 `UpdateDialog`)目前只显示
「发现新版本 x.y.z / 当前版本 x.y.z,前往官网下载最新版本」,不包含本次版本改了什么。
用户要判断"值不值得更新"只能点「查看更新日志」跳浏览器打开 GitHub Releases 页,路径长、流失率高。

## 现状梳理

- `UpdateApi.fetchLatestVersion()` 请求 `GET /repos/HarlonWang/TrendingAI/releases/latest`,
  但只反序列化了 `tag_name`,忽略了同一响应里的 `body` 字段。
- GitHub Release 正文(`body`)由 CI(`android_release.yml` 的 Generate Release Notes 步骤)
  从 whatsnew.json 拼出,格式稳定:
  ```markdown
  ### ✨ 更新内容
  - 中文条目…

  ### ✨ What's New
  - English item…

  ### Apk Diff
  …(体积对比等内部信息)
  ```
  自动/手动两种 whatsnew 生成模式产出的 body 结构一致。
  兜底格式(whatsnew 缺失时)为 `### 变更记录 (Changelog)` + 原始 commit 列表,不适合面向用户展示。
- 升级后首启的 `WhatsNewDialog`(shared 模块)已经确立了更新说明的展示样式
  (`• 条目` 列表、8dp 间距、可滚动)和语言选择规则(app 语言设置 → 系统语言,zh 缺失回退 en,反之亦然)。

## 候选方案

### 方案 A:复用 `releases/latest` 响应的 `body` 字段(推荐)

在现有请求里多解析一个 `body` 字段,客户端用纯函数从 markdown 中提取
「✨ 更新内容 / ✨ What's New」两节的条目,在更新弹窗内直接展示。

- 优点:零新增网络请求;对所有已发布的历史 release 立即生效;改动收敛在 updater 库;
  解析器是纯函数,单测容易覆盖。
- 缺点:依赖 release body 的标题格式约定(缓解:解析器宽松匹配 + 解析失败静默回退现状,
  并在 CI 工作流的 Generate Release Notes 步骤加注释声明该格式已被客户端消费)。

### 方案 B:CI 把 whatsnew.json 作为 release asset 上传,客户端二次请求

- 优点:结构化 JSON,无需解析 markdown。
- 缺点:多一次网络请求;需要改 CI;对已发布的历史版本不生效(只有下个 tag 起才有 asset);
  旧版本 app 升级路径上拿不到。
- 注:改从 tag 对应 commit 的 raw.githubusercontent 读 whatsnew.json 不可行——
  自动模式下仓库里提交的是占位文件,真实内容只存在于 CI 构建产物中。

### 方案 C:官网/服务端维护结构化 changelog 接口

- 优点:格式完全自主可控。
- 缺点:新增服务端组件与发布流程,维护成本远超收益,违反 YAGNI。

**结论:选方案 A。** B 的"结构化"收益不足以抵消额外请求 + 历史版本失效;C 过重。

## 设计(方案 A)

### 数据层:`UpdateApi`

- `GitHubRelease` 增加 `@SerialName("body") val body: String = ""`。
- `fetchLatestVersion(): String?` 改为 `fetchLatestRelease(): LatestRelease?`,
  返回 `LatestRelease(tagName: String, body: String)`。

### 解析:`ReleaseNotesParser`(updater 库内新文件,纯函数)

```kotlin
fun parseReleaseNotes(body: String): ReleaseNotes
data class ReleaseNotes(val zh: List<String>, val en: List<String>)
```

逐行扫描:

- 遇到 `###` 开头且含「更新内容」的标题 → 进入 zh 收集态;含「What's New」→ en 收集态;
  其余 `###`/`##` 标题(Apk Diff、变更记录等)→ 退出收集态。
- 收集态内,`- ` 或 `• ` 开头的行去前缀后收入当前语种列表;空行跳过。
- 兜底格式(变更记录 + raw commits)没有目标标题 → 解析结果为空,由 UI 回退。

### 状态:`UpdateInfo` / `UpdateViewModel`

- `UpdateInfo` 增加 `releaseNotes: ReleaseNotes`(默认空)。
- `doCheck()` 中拿到 `LatestRelease` 后,`isNewer` 成立时以 `parseReleaseNotes(body)` 填入。

### UI:`UpdateDialog`

- 语言选择:与 `WhatsNewHost` 相同规则——`globalSettingsManager.appLanguage`
  (FOLLOW_SYSTEM 时取 `getSystemLanguage()`),zh 取 `zh` 列表(空则回退 `en`),否则反之。
  updater 库已依赖 `:shared`,可直接复用这两个 API。
- 选出的条目列表非空时:正文区改为可滚动 `Column`(`verticalScroll` + 8dp 间距),
  逐条渲染 `• 条目`,样式对齐 `WhatsNewDialog`;不再显示原「当前版本 x,前往官网下载」句
  (新版本号已在标题,下载动作已有按钮);同时隐藏「查看更新日志」按钮——
  内容已在弹窗内,该按钮只剩查看 Apk Diff 等内部信息的价值,不值得挤占三按钮空间。
- 条目为空(解析失败/兜底格式/网络返回无 body):完全维持现状,包括「查看更新日志」按钮。

### CI 配套

`android_release.yml` 的 Generate Release Notes 步骤加一行注释:
「### ✨ 更新内容 / ### ✨ What's New 标题被 app 更新弹窗解析消费,改格式需同步 updater 库」。
不改任何行为。

### 错误处理

- 解析全程不抛错:任何意外格式都落到"空列表 → 回退现状",不新增失败面。
- 网络层行为不变(超时、异常返回 null 的语义保持)。

### 测试

updater 模块新增 `src/test`(现仅有 `main`),test 依赖复用 version catalog 现有 alias
(与 shared 模块 JVM 单测所用一致,不新建重复条目)。用例覆盖 `parseReleaseNotes`:

1. 标准双语 body(含 Apk Diff 后缀小节)→ zh/en 均正确、后缀小节不混入;
2. 仅 en 小节 → zh 空、en 正确;
3. 兜底格式(变更记录 + raw commits)→ 双语均空;
4. 空字符串 / 无关 markdown → 双语均空;
5. 条目前缀 `- ` 与 `• ` 混用、标题前后有空行 → 正常解析。

语言选择逻辑:把 `WhatsNewHost` 里「按语言从 zh/en 双列表选一」抽成 shared 里的纯函数
(如 `WhatsNew.kt` 内 `pickByLanguage(lang, zh, en)`),`WhatsNewHost` 与 `UpdateDialog` 两处调用,
单测放在现有 `WhatsNewTest`。

### 影响范围

- `androidLibrary/updater`:UpdateApi、UpdateInfo、UpdateViewModel、UpdateDialog、
  新增 ReleaseNotesParser + 单测。
- `shared`:可选的小重构(抽语言选择纯函数供 WhatsNewHost 与 UpdateDialog 复用)。
- `.github/workflows/android_release.yml`:仅加注释。
- 不涉及 play / fdroid 渠道(它们不启用自建更新检查),不涉及 whatsnew 首启弹窗行为。
