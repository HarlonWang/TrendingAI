# 最低版本检查（min_version）设计

日期：2026-07-11 ｜ 分支：feat/min-version-check ｜ 优先级：P2

## 背景

2026-07-10 埋点分析发现 25 台全新设备在跑 0.14.0 老包（无 channel 属性、部分事件无 install_id），78% 集中在尼日利亚+印度，特征符合第三方 APK 镜像站收录了老版本。需要一个服务端可控的最低版本开关：低于 `min_version` 的客户端弹强提示，引导用户去官方渠道更新，顺带收敛老版本产生的脏埋点数据。

注意时序：该机制只对「装了本功能之后版本」的包生效，存量 0.14.0 老包永远不会执行这段代码——它的价值是防患于未来（下次再被镜像站收录老包时，服务端拨一下 min_version 即可全量拦截）。存量脏数据只能靠服务端/分析侧过滤（见「服务端待办」）。

## API 契约（服务端在 github-ai-trending-api 仓库实现，见待办）

```
GET https://api.trendingai.cn/api/app-config
→ 200 {"min_version": "0.15.0"}     // 未配置强更时返回 {} 或 min_version 为 null
```

- 响应预留扩展（客户端 `ignoreUnknownKeys`），未来可加 `latest_version`、`download_url`、`message` 等字段。
- 客户端每个请求的 UA 已带 `TrendingAI/<版本> (Android ...; channel=<渠道>)`，服务端如需按渠道差异化返回可直接读 UA，第一版不做。

## 客户端方案

### 方案取舍

- **不复用 updater 库**：它打 GitHub Releases、24h 节流、可关闭、且 play/fdroid flavor 是 no-op，覆盖不了全渠道，也非服务端可控。
- **不 piggyback 到 /api/trending**：fetchTrending 有 date/batch 变体，耦合脏；独立轻量端点更干净，且启动多一个小请求可接受。
- **选定**：common 层新增独立检查（全平台、全 flavor 生效），冷启动拉取 + 本地缓存兜底，命中时整屏替换主 UI（比「不可关闭 Dialog」更强且没有 dismiss 语义可钻）。

### 组件

1. **纯逻辑** `shared/commonMain/.../update/MinVersion.kt`（照 WhatsNew.kt 模式，纯函数 + commonTest 单测）：
   - `isVersionBlocked(current, minVersion): Boolean` — 取 `-`/`+` 前的数值段逐段比较（缺段补 0）；**任一侧解析失败一律不拦截**（防御：`getAppVersion()` 兜底值、畸形配置都不能把用户锁死）。本地 git describe 版本如 `0.14.0-5-gabc` 只比 `0.14.0` 数值段。
2. **数据模型** `data/model/AppConfigResponse.kt`：`@Serializable`，`@SerialName("min_version") val minVersion: String? = null`。
3. **网络** `TrendingApi.fetchAppConfig()`：GET `$baseHost/api/app-config`，`open suspend fun`（可测试替身）。
4. **缓存** `SettingsManager`：`prefs_min_version` 存最近一次服务端下发值（null 时清除）。离线冷启动用缓存判定，保证强更一旦下发、断网重启也拦得住。
5. **UI 门** `ui/common/ForceUpdateGate.kt`：
   - `ForceUpdateGate { content }` 挂在 `App.kt` 根部（TrendingTheme 内、包住 WhatsNewHost/SignInHint/NavDisplay）。
   - 初始态读缓存；`LaunchedEffect(Unit)` 调 `fetchAppConfig()`，成功则更新缓存与状态，失败静默（不影响正常使用）。
   - 命中 `isVersionBlocked` → 渲染全屏 `ForceUpdateScreen`（图标 + 标题 + 说明 + 主按钮「前往官方渠道更新」→ `openUrl(Constants.OFFICIAL_WEBSITE_URL)`），不渲染主界面，天然规避与 What's New/SignInHint 双弹窗问题。
   - 埋点：弹出 `force_update_shown`（props: current/min），点击 `force_update_click`。
6. **文案**：`values/strings.xml` + `values-zh/strings.xml` 新增 `force_update_*` 条目。

### 错误处理

| 场景 | 行为 |
| :--- | :--- |
| 接口 404 / 网络失败（服务端未上线） | 静默，走缓存或不拦截 —— 客户端可先于服务端发布 |
| min_version 畸形（非 semver） | 不拦截 |
| 当前版本读取失败/畸形 | 不拦截 |
| 服务端撤销 min_version（返回 null） | 清缓存，下次启动恢复 |

### 测试

- `MinVersionTest`（commonTest，kotlin.test）：低于/等于/高于、缺段补 0、prerelease 与 git-describe 后缀、畸形输入、null。
- `AppConfigResponseTest`：JSON 解码（正常 / 缺字段 / 未知字段容错）。
- 模拟器人工验证：临时把缓存/接口 mock 成高 min_version，确认全屏拦截 + 按钮跳转。

## 服务端待办（github-ai-trending-api 仓库，单独操作）

1. 新增 `GET /api/app-config`，`min_version` 从 env 或 KV 读取，未配置返回 `{}`。
2. 脏数据治理（分析侧）：Aptabase 报表按「无 channel 属性 / 无 install_id」过滤老包事件；如需 API 层硬拦截，可按 UA `TrendingAI/<ver>` 对老版本拒绝服务，但对存量 0.14.0（无任何提示 UI）体验差，建议只观察不硬拦。
