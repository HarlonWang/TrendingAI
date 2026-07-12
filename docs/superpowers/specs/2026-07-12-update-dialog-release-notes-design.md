# 更新弹窗展示版本更新内容 — 设计方案

日期:2026-07-12
分支:feat/update-dialog-release-notes

## 背景与问题

apk 渠道的更新提示弹窗(`androidLibrary/updater` 的 `UpdateDialog`)目前只显示
「发现新版本 x.y.z / 当前版本 x.y.z,前往官网下载最新版本」,不包含本次版本改了什么。
用户要判断"值不值得更新"只能点「查看更新日志」跳浏览器打开 GitHub Releases 页,路径长、流失率高。

## 现状梳理

- `UpdateApi.fetchLatestVersion()` 请求 `GET /repos/HarlonWang/TrendingAI/releases/latest`,
  只反序列化 `tag_name`;响应里还有 `body`(Release 正文 markdown)和 `assets[]`(附件列表)。
- 更新说明的源头是 CI 构建时生成/采用的 whatsnew.json(结构化 JSON:`{version, zh[], en[]}`),
  它随 APK 打包供升级后首启弹窗,同时被拼进 Release 正文;但 CI 目前**不把这份 JSON 上传为
  release asset**,自动模式下仓库里提交的又只是占位文件——即结构化数据在发布后无处可取。
- 升级后首启的 `WhatsNewDialog`(shared 模块)已确立更新说明的展示样式
  (`• 条目` 列表、8dp 间距、可滚动)和语言选择规则(app 语言设置 → 系统语言,zh 缺失回退 en,
  反之亦然);shared 里已有 `WhatsNewInfo` 数据类和 `parseWhatsNew()` 解析函数。

## 候选方案

### 方案 A:客户端解析 `releases/latest` 响应的 `body` markdown

- 优点:零新增网络请求、不改 CI。
- 缺点:依赖 Release 正文的标题格式约定(`### ✨ 更新内容` / `### ✨ What's New`),
  markdown 解析天然脆弱;`gh release create` 还带 `--generate-notes`,正文里混有
  GitHub 自动生成段落和 Apk Diff 等内部信息,解析器要逐一排雷。

### 方案 B:CI 把 whatsnew.json 上传为 release asset,客户端二次请求(选定)

- 优点:结构化 JSON,客户端直接复用 shared 已有的 `WhatsNewInfo` / `parseWhatsNew()`,
  无 markdown 解析;与 Release 正文格式解耦,正文以后随便改;数据契约就是 app 内已在消费的
  whatsnew.json 本身,一处格式两处使用。
- 缺点:发现新版本时多一次网络请求(仅在确认有更新后才发起,频率极低);需小改 CI。
- 关于"对历史 release 不生效":实际影响可忽略——弹窗只展示 `releases/latest` 指向的
  **最新** release,带此功能的 app 版本发布时 CI 改动同步上线,此后检查到的任何新版本都带 asset;
  唯一拿不到 asset 的场景是 latest 恰为功能上线前的旧版本,而那时也不存在"有更新"可弹。

### 方案 C:官网/服务端维护结构化 changelog 接口

- 优点:格式完全自主可控。
- 缺点:新增服务端组件与发布流程,维护成本远超收益,违反 YAGNI。

**结论:选方案 B**(用户拍板)。结构化契约的长期稳健性优于 A 省掉的那一次低频请求;C 过重。

## 设计(方案 B)

### CI:上传 whatsnew.json 为 release asset

`android_release.yml` 的 Create Release 步骤,`FILES` 数组组装处追加条件上传:

- 仅当 `whatsnew.json` 的 `version` 恰等于本次 tag 且 zh/en 至少一个非空时,
  `FILES+=("$WHATSNEW_PATH")`;否则跳过(占位文件/生成失败不上传,
  客户端"找不到 asset → 回退现状"即为正确行为)。
- 判定逻辑与 Generate What's New 步骤的"手动模式"判定同构,用几行 python/jq 实现。
- asset 文件名固定为 `whatsnew.json`(gh 按原文件名上传),这是客户端查找的契约。

### 数据层:`UpdateApi`

- `GitHubRelease` 增加 `@SerialName("assets") val assets: List<GitHubAsset> = emptyList()`,
  `GitHubAsset(name, @SerialName("browser_download_url") browserDownloadUrl)`。
- `fetchLatestVersion(): String?` 改为 `fetchLatestRelease(): LatestRelease?`,
  返回 `LatestRelease(tagName: String, whatsNewAssetUrl: String?)`
  (从 assets 里找 `name == "whatsnew.json"`,找不到为 null)。
- 新增 `suspend fun fetchWhatsNew(url: String): WhatsNewInfo?`:
  GET 该 asset(okhttp 默认跟随 GitHub 的重定向),响应文本交给 shared 的 `parseWhatsNew()`;
  任何异常返回 null。复用现有 client 与超时配置。

### 状态:`UpdateInfo` / `UpdateViewModel`

- `UpdateInfo` 增加 `whatsNew: WhatsNewInfo?`(默认 null)。
- `doCheck()`:`isNewer` 成立且 `whatsNewAssetUrl != null` 时,再请求 asset;
  取到的 `WhatsNewInfo` 需通过版本校验 `info.version == tagName` 才采用
  (防串版:asset 内容与 release 不符时宁可不展示),否则置 null。
  asset 请求失败不影响弹窗本身,只是不带更新内容。
- 版本校验收敛为纯函数以便测试:
  `fun acceptWhatsNew(info: WhatsNewInfo?, tag: String): WhatsNewInfo?`
  (null / isEmpty / version 不等于 tag → null)。

### UI:`UpdateDialog`

- 语言选择:把 `WhatsNewHost` 里「按语言从 zh/en 双列表选一」抽成 shared 里的纯函数
  (如 `WhatsNew.kt` 内 `pickByLanguage(lang, zh, en)`),`WhatsNewHost` 与 `UpdateDialog`
  两处调用;语言来源同 `WhatsNewHost`:`globalSettingsManager.appLanguage`
  (FOLLOW_SYSTEM 时取 `getSystemLanguage()`)。updater 库已依赖 `:shared`。
- 选出的条目列表非空时:正文区改为可滚动 `Column`(`verticalScroll` + 8dp 间距),
  逐条渲染 `• 条目`,样式对齐 `WhatsNewDialog`;不再显示原「当前版本 x,前往官网下载」句
  (新版本号已在标题,下载动作已有按钮);同时隐藏「查看更新日志」按钮——
  内容已在弹窗内,该按钮只剩查看 Apk Diff 等内部信息的价值,不值得挤占三按钮空间。
- 条目为空(无 asset / 请求失败 / 版本校验不过):完全维持现状,包括「查看更新日志」按钮。

### 错误处理

- asset 缺失、下载失败、JSON 解析失败、版本不匹配,全部落到「whatsNew = null → 回退现状」,
  不新增失败面;更新弹窗本身的出现与否只由 `tag_name` 版本比较决定,与 asset 无关。
- 网络层行为不变(超时、异常返回 null 的语义保持)。

### 测试

updater 模块新增 `src/test`(现仅有 `main`),test 依赖复用 version catalog 现有 alias
(与现有 JVM 单测所用一致,不新建重复条目)。用例:

1. `acceptWhatsNew`:version 匹配且非空 → 采用;version 不匹配 / zh+en 均空 / null → 拒绝;
2. `LatestRelease` 的 asset 查找:assets 含 `whatsnew.json` → 取到 url;
   不含 / assets 为空 → null(可作为响应 JSON 反序列化测试,用 mockwebserver 或直接喂 Json 字符串);
3. shared 的 `pickByLanguage`:zh 环境取 zh、zh 空回退 en、反之亦然
   (放现有 `WhatsNewTest`,连同 `WhatsNewHost` 重构后的行为一并覆盖)。

### 影响范围

- `androidLibrary/updater`:UpdateApi、UpdateInfo、UpdateViewModel、UpdateDialog,新增单测。
- `shared`:抽 `pickByLanguage` 纯函数(`WhatsNew.kt`),`WhatsNewHost` 改为调用它。
- `.github/workflows/android_release.yml`:Create Release 步骤条件上传 whatsnew.json asset。
- 不涉及 play / fdroid 渠道(它们不启用自建更新检查),不涉及 whatsnew 首启弹窗行为。

### 发布注意事项

功能上线后的**第一个** tag 必须包含本 CI 改动(否则该版 release 无 asset,弹窗回退旧样式,
功能等于晚一个版本才可见);无需其他运维动作。
