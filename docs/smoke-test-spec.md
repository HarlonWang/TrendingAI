# 发版冒烟测试规格

> 打 tag 前的最后一道检查。它从 App 自身的组成与风险面出发列检查项，不是某次事故的补丁；
> 事故只用来校准覆盖面。覆盖面刻意收紧，一轮在模拟器上十五分钟内跑完；要扩面先在用例表追加，不另起文档。
> **状态：v1（2026-10-20）**。每次发版在文末「执行记录」追加一行。

## 为什么分三层

发版前能检查的东西，按「什么时候能做、靠什么判定」天然分成三层。混在一起的后果是：确定性的检查被
人工步骤拖慢，人工才能看的东西又被脚本的 PASS 掩盖（1.9.3 冒烟 PASS，Play 因 .so 未按 16 KB 对齐拒更，就是这一种）。

| 层 | 回答的问题 | 怎么做 | 需要什么 |
|---|---|---|---|
| 一、产物断言 | 这个包的身份与合规性对不对 | `scripts/release-smoke.sh` 自动，任一不符即 FAIL | 只要包 |
| 二、设备冒烟 | 装上跑起来，用户看到的对不对 | 脚本跑确定性的几条，其余按下面的用例表在模拟器上走 | 设备、测试账号 |
| 三、发后核对 | 只有 CI 出包、线上有数据之后才能验的 | tag 推出后按表核对 | CI 产物、看板 |

## 前置

| 项 | 约定 |
|---|---|
| commit | 就是要打 tag 的那个。TinyUI 内置包已刷新并提交（脚本第一步做，有变化会自动 commit） |
| 包 | 脚本产出的 r2 渠道 release 包（R8 开启，与线上同配置），本地用 debug 签名。四个渠道只差 `UpdateWrapper` 一个文件，验 r2 即可 |
| 设备 | 模拟器 `Pixel_10`（Android 17，arm64，**16 KB 页面**）。16 KB 页面设备是硬要求：原生库对齐问题只在这类设备上显形。手动启动时带上 `-http-proxy "$http_proxy"`，否则检查更新直连 GitHub API 不通（脚本自己启动 AVD 时会带） |
| 装前 | **先卸旧包**。残留的登录态与本地数据会让首启类用例失真 |
| 账号 | 商店审核演示账号，见 `TrendingProjects/auth-regression-checklist.md` §1.3（私有仓，本文不写账号）。要点：固定验证码、不发信，但**同邮箱 10 分钟内最多 3 次发送的限流照样算**（第 4 次提示「10 分钟后再试」），一轮冒烟加发后核对的登录次数要省着用，撞上就等 10 分钟；**已手工挂 Pro**；**没绑 GitHub**，所以 Star 与 GitHub 个人页走不通；「管理订阅」行会出现、点开报「打开失败」是预期 |
| 看板 | <https://dashboard.trendingai.cn>（Cloudflare Access），TinyUI 专题，按 `app_version` 过滤 |

## 第一层：产物断言

全部在 `scripts/release-smoke.sh` 里，FAIL 级。这一层不需要设备。

| ID | 断言 | 为什么 |
|---|---|---|
| A1 | TinyUI 内置包与 production 当前版本一致；F-Droid 页面源码快照能重编出字节码且哈希与 manifest 一致 | F-Droid 从快照重编，分岔会让 F-Droid 版打不出订阅页 |
| A2 | `HostSnapshotTest` 与 `EmbeddedPackageTest` 通过 | 宿主接口快照与内置包的发版检查 |
| A3 | 包非 debuggable | 可调试包不得分发 |
| A4 | `targetSdkVersion` 等于 `libs.versions.toml` 的 `android-targetSdk` | Play 对 targetSdk 有年度下限，构建漂移时在这里拦 |
| A5 | 包内所有 `.so` 的 ELF LOAD 段对齐 ≥ 16 KB | Play 自 2025-11 起拒绝不支持 16 KB 页面的更新。1.9.3 因 quickjs-kmp 0.1.2 的 4 KB 对齐被拒 |
| A6 | `native-code` 含 `arm64-v8a` | 唯一的第三方原生库 `libquickjs_kmp.so` 漏打 ABI 时订阅页会崩 |
| A7 | `local.properties` 没有库的 `<库>.dir` 覆盖（`sdk` / `ndk` / `cmake` 除外） | 有覆盖时本机走 composite build 编本地源码，CI 走 Maven 坐标，冒烟测的不是要发出去的字节，分岔不报错。1.10.0 冒烟时 `appsettings-kmp.dir` 还在，靠事后核对本地仓与 0.2.1 无源码差异才放行 |

## 第二层：设备冒烟

「预期」是用户视角可观察的结果；任一条不符即记为失败。「执行」列说明这条由脚本判定还是按表走。
登录态作为一个**切换点**：前半段未登录跑，S05 登录，后半段已登录跑，一轮覆盖两种状态。

| ID | 块 | 操作 | 预期 | 状态 | 执行 |
|---|---|---|---|---|---|
| S01 | 启动 | 卸旧包，安装，冷启动，停 10 秒 | 进程存活，无 crash 日志；无「App 兼容性」系统弹窗；logcat 本进程无 R8 特征异常（`UnsatisfiedLinkError` / `AbstractMethodError` / `NoSuchMethodError` / `NoClassDefFoundError` / `NoSuchFieldError` / `MissingFieldException` / `SerializationException`） | 未登录 | 脚本 |
| S02 | 首页 | 停留首页，依次切 GitHub / Hacker News / Product Hunt 子 tab | 三源都加载出条目，不是空态、错误态或一直加载 | 未登录 | 按表 |
| S03 | 解读页 | 从首页点任意一条进解读页 | 头部标题与热度即时出来；正文加载出 AI 解读，或明确的「暂无解读」空态；底部出路按钮（读原文 / HN 讨论 / 查看 README）按来源出现；返回回到列表 | 未登录 | 按表 |
| S04 | 精选 | 切到精选 tab | 当日精选列表加载出来 | 未登录 | 按表 |
| S05 | 我 | 进「我」页 | 未登录态：账户头部「未登录」+ 登录按钮；套餐卡有「登录获取更多额度」按钮和底部的「升级到 Pro」行 | 未登录 | 按表 |
| S06 | 订阅页 | 点套餐卡的「升级到 Pro」行进订阅页（TinyUI 页） | 渲染出服务端下发的标题、权益清单、两档价格与退款说明；CTA 为「登录后订阅」 | 未登录 | 按表 |
| S07 | 登录 | 点订阅页的 CTA，用演示账号走邮箱验证码登录 | 登录浮层从订阅页拉起；登录成功后**回到订阅页**，CTA 原地切成 Pro 致谢。返回「我」页，账户头部已登录并带 Pro 标，升级行消失 | 切换 | 按表 |
| S08 | 聊天 | 进聊天页，**发一条消息** | 输入框可输入；收到 AI 回复正文，不是报错或一直等待 | 已登录 | 按表（脚本只验输入框渲染，作为兜底） |
| S09 | 热下发 | 杀进程，冷启动，直接再开一个 TinyUI 页（Pro 账号进不了订阅页，用设置里的数据来源页） | 免登录进首页；页面仍正常渲染；logcat 里 `TinyUI updates` 显示 `source=EMBEDDED` 且 `UpToDate`（这条走启动时选包与 sha256 校验的路径） | 已登录 | 按表 |
| S10 | 设置 | 进设置页、数据来源页（TinyUI 页）、关于页，点检查更新 | 三页都能进且渲染正常；检查更新有明确结果，不崩不报错。结果三选一且都算过：「有新版本」弹窗、「已是最新」、「检查失败」——后两者只停 2 秒，要用半秒一次的轮询抓。本地构建的 versionName 带 git 后缀，被当作上个 tag 的预发布版，连上时会弹「新版本」为上一个 tag，属本地包现象；检查失败多半是模拟器直连 GitHub API 不通（主机的代理不会传给模拟器），不是 App 问题 | 已登录 | 按表 |

**账号与订阅页**：订阅页的三个入口（套餐卡升级行、设置里摘要语言的引导、聊天额度用尽）对「非 Pro」开放，未登录也能进，
页面自己处理登录（价格与文案接口公开，CTA 变「登录后订阅」并拉起登录浮层，登录后原地切回）。演示账号是 Pro，登录后三个入口
都消失，所以**订阅页必须在登录前验**，顺序固定为 S05 → S06 → S07。演示账号机制（loginbase `demoAccount`）只支持一个固定账号，
Free 登录态下的订阅页 CTA（「订阅」→ Paddle 结账）走不到，归入「改到时才验」。

### 判定与处置

- 第一层全过、S01–S10 全过，才能打 tag。
- 任一条失败：记问题，修复合入 main，重新构建，**全量重跑**，不只补跑失败项。
- 失败但判定为不阻塞发版：在执行记录里写明原因。

## 第三层：发后核对

tag 推出、CI 出包之后做。不挡发版，但要做，否则这两类问题永远没人发现。

| ID | 核对 | 预期 |
|---|---|---|
| P1 | 看板按本版本 `app_version` 过滤 | 有本设备的 `screen_viewed`（含 `paywall`）；TinyUI 专题里 `tinyui_version` 等于内置包 `manifest.json` 的 `version`；没有 `update_checked` 失败、`embedded_rejected`、`page_rolled_back` |
| P2 | 覆盖升级 | 模拟器上先装 GitHub Release **上一版**的 APK（CI 签名），登录、改一个设置，再 `adb install -r` 本版 CI 签名的 APK，冷启动 | 登录态在；设置按本版说明的预期保留或重置；不崩；无迁移类异常；首启弹本版的更新说明。本地 release 用 debug 签名盖不上线上包，所以只能在这一层做。顺带能核对两件事：上一版的自动更新检查会弹出本版并带上 whatsnew；本版的 AAB 解出 `base/lib` 跑一遍 A5 |

## 改到时才验

下面这些不在每次冒烟里，由本次 commit 范围决定要不要跑。它们要么有外部副作用，要么需要特定账号，要么坏了不致命。

| 范围 | 触发条件 | 怎么验 |
|---|---|---|
| 登录全流程（限流、验证码错误、过期、GitHub OAuth、绑定冲突） | 改到登录 / 鉴权 / loginbase 版本 | `TrendingProjects/auth-regression-checklist.md`，整份 |
| Star、GitHub 个人页 | 改到 GitHub token 链路 | 需要绑了 GitHub 的账号，演示账号不行 |
| Paddle 下单、管理订阅 | 改到订阅页下单逻辑或 Paddle 集成 | 用 Paddle 沙盒 |
| 每日精选通知 | 改到 `DailyPicksNotifier` 或 WorkManager 接线 | 设置里开通知，把系统时间拨过调度点 |
| 收藏、README 渲染、原文 WebView | 改到 `ui/favorites`、`ui/detail`、`ui/webview` | 手动点一遍 |
| 招聘专题 | 改到 `ui/hiring` 或服务端招聘接口 | 进专题，切月份与筛选维度 |
| F-Droid 渠道包 | 改到 `androidApp/src/fdroid` | 本地 `assembleFdroidRelease` 装一遍，重点看订阅页 |

## 执行记录

每次发版追加一行；问题列写 issue / PR 链接或一句话描述。

| 日期 | 版本 (versionCode) | 执行人 | 结果 | 问题 / 备注 |
|---|---|---|---|---|
| 2026-10-20 | main `878f239`，1.9.3 之后 24 个 commit（10903，未打 tag） | 王海龙（Claude Code 执行） | A1–A6 过；订阅页**未执行**，其余过（编号按当时的表） | 首轮脚本在聊天入口那一下被吞，改为按 content-desc 定位加重试后通过。订阅页走不到：演示账号是 Pro，当时入口只对「已登录且非 Pro」开放。热下发用数据来源页替代，`TinyUI updates` 为 `source=EMBEDDED` / `UpToDate`。检查更新提示「新版本 1.9.3」是本地 versionName 带 git 后缀所致。A5 确认全部 .so LOAD 段 2**14（quickjs-kmp 0.1.4） |
| 2026-10-20 | main，上一行 + 订阅页向未登录开放（随后打 tag 1.10.0 / 11000） | 王海龙（Claude Code 执行） | A1–A6 过；S01–S10 **全过** | 全量重跑。**两轮都在 `local.properties` 带着 `appsettings-kmp.dir` 下跑的**，测到的是本地源码；事后核对本地仓比 0.2.1 只多文档与工具链 commit、无源码差异，放行，并新增 A7 挡住下次。S06 未登录进订阅页渲染出文案、权益、两档价格，CTA「登录后订阅」；S07 从 CTA 登录后回到订阅页、原地切成 Pro 致谢。S08 发 ping 收到 pong（adb `input text` 不能带空格；ENTER 不触发发送，要点发送键）。S10 检查更新第一次弹「新版本 1.9.3」，之后两次「检查失败」，定位为模拟器未带主机代理、直连 GitHub API 不通，脚本已改为启动 AVD 时带 `-http-proxy`；用例预期与执行方式随之改为半秒轮询 |
| 2026-10-20 | 1.10.0（11000）发后核对 | 王海龙（Claude Code 执行） | P2 过；P1 待看板有数据后由本人看 | CI 成功，Release 带 APK / Play AAB / whatsnew，AAB 的 `base/lib` 全部 .so 2**14，签名与 1.9.3 一致。1.9.3 的 CI 包在 16 KB 模拟器上一启动就弹系统兼容性对话框、点名 `libquickjs_kmp.so`（被拒的实证）；其自动更新检查弹出「New Version 1.10.0」并带新说明。盖装 1.10.0 冷启动：不崩、无 R8 异常、无兼容弹窗、登录态保留、TinyUI `EMBEDDED` / `UpToDate`。两个预期内的观察：主题从 Dark + Plum 回到 System + Default（外观存储换 key，说明里已写）；**升级后没弹 1.10.0 的更新说明**，因为 appsettings-kmp 不迁旧的 lastSeen key（其 `docs/design.md` 2026-10-14 的决定，代价是老用户这一次少弹一回），从 1.10.0 起恢复正常。另：演示账号发码撞过一次限流，等了 10 分钟 |
