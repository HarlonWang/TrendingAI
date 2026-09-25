# APK 体积基线（订阅页接入 TinyUI）

采集于 2026-09-18，`feat/tinyui-subscription`（commit `085b08f`）验收时留档。
**用途**：后续做包大小优化时的参考分析——接入 TinyUI / QuickJS 引擎给 Android 包带来的增量是多少、
落在哪一层、哪些是引擎本体成本、哪些是分发策略可选项。三条待决项只列不定，见文末。

## 结论摘要

github 渠道 release 包 **3.6 MiB → 5.8 MiB，+2.2 MiB（+61%）**，其中 **96% 是 `libquickjs_kmp.so`**
（三个 ABI 合计 2.1 MiB，已 strip，无再压空间）。Kotlin 侧（tinyui + quickjs-kmp）经 R8 后 dex 增量约 46 KiB；
页面字节码 + 运行时字节码 + source map 共 40 KiB。真机只加载一个 ABI，因此 Play（AAB 按 ABI 拆分）
用户实际下载增量约 0.8 MiB，github / R2 的 universal APK 用户全额承担 2.2 MiB。

## 对比口径

| | 基线（old） | 接入后（new） |
|---|---|---|
| 产物 | 线上 Release 1.8.0 的 `TrendingAI-1.8.0-android.apk`（github 渠道，CI 签名） | `feat/tinyui-subscription` `085b08f` 本地 `assembleGithubRelease`（R8，本地签名） |
| 大小 | 3,761,359 B | 6,027,870 B |
| 工具 | `diffuse 0.3.0`（与 `.github/workflows/release.yml` 同款） | |

**基线取线上 1.8.0 而非 main**：它是用户实际拿到的包、且有签名产物可复现。两者差 `1.8.0..main` 一个
commit（`e8b5292`，首页顶部留白修法，3 个 Kotlin 文件纯 UI 逻辑，不动依赖 / 资源 / native）——
它只影响 dex 栏，量级 ≤ 数 KiB，**不影响 native / asset 结论**；分支自身还删了 `SubscriptionViewModel`
与半个 `SubscriptionScreen`，dex 栏本就是正负相抵后的近似值。后续要精确归因 Kotlin 侧增量，同基线同分支各编一个包即可。

签名方式（V2）、versionCode（10800）两包一致；new 的 versionName 带 `-3-g085b08f` git describe 后缀属本地构建正常现象。

## 数据

### 按分类（compressed = 下载体积；uncompressed = 安装后展开）

| 分类 | old | new | 增量（compressed） | 增量（uncompressed） |
|---|---|---|---|---|
| dex | 2.7 MiB | 2.7 MiB | +45.9 KiB | +85.2 KiB |
| arsc | 421.5 KiB | 421.5 KiB | 0 | 0 |
| manifest | 3.1 KiB | 3.1 KiB | +13 B | +24 B |
| res | 231.5 KiB | 231.5 KiB | +2 B | 0 |
| native | 81 KiB | 2.2 MiB | **+2.1 MiB** | +2 MiB |
| asset | 150.2 KiB | 190.6 KiB | +40.4 KiB | +104.9 KiB |
| other | 66.8 KiB | 66.8 KiB | +1 B | 0 |
| **total** | **3.6 MiB** | **5.8 MiB** | **+2.2 MiB** | **+2.2 MiB** |

dex 层：类 6209 → 6359（+150），方法 32467 → 32797（+330），字段 +561，字符串 +533。

### 逐文件明细（增量 > 1 KiB）

| compressed | 增量 | 路径 |
|---|---|---|
| 802.5 KiB | +802.5 KiB | `lib/x86_64/libquickjs_kmp.so` |
| 780.8 KiB | +780.8 KiB | `lib/arm64-v8a/libquickjs_kmp.so` |
| 547.5 KiB | +547.5 KiB | `lib/armeabi-v7a/libquickjs_kmp.so` |
| 2.7 MiB | +45.9 KiB | `classes.dex` |
| 15.6 KiB | +15.6 KiB | `assets/…/files/tinyui/runtime/core.js.map` |
| 12.3 KiB | +12.3 KiB | `assets/…/files/tinyui/runtime/core.bin` |
| 4 KiB | +4 KiB | `assets/…/files/tinyui/pages/subscription.js.map` |
| 3.6 KiB | +3.6 KiB | `assets/…/files/tinyui/pages/subscription.bin` |
| 2.3 KiB | +2.3 KiB | `assets/…/files/tinyui/runtime/native.js.map` |
| 2 KiB | +2 KiB | `assets/…/files/tinyui/runtime/native.bin` |

`lib/*/libandroidx.graphics.path.so` 三个 ABI 各有 +4～19 KiB 的 compressed 差异但 uncompressed 为 0，
是 zip 内对齐 / 压缩方式变化（AGP 版本不同）造成的，与 TinyUI 无关，忽略。

asset 里字节码（`.bin`）共 18 KiB、source map（`.js.map`）共 22 KiB；`manifest.json` 235 B。

## 归因

- **native +2.1 MiB = QuickJS 引擎本体**。`file` 确认三份 `.so` 均已 strip；QuickJS 单 ABI 550～800 KiB
  是它的正常体量，没有编译层面的压缩余地。
- **dex +46 KiB**：tinyui 的组合期解析 / patch 应用 / 组件渲染器 + quickjs-kmp 的 Kotlin 绑定 + 本仓
  `TinyUIHost` 与宿主能力适配器，扣除被删的 `SubscriptionViewModel` 等。含 `e8b5292` 的少量噪声（见口径）。
- **asset +40 KiB**：一半以上是 source map。

## 待决项（未做，只记录）

1. **universal APK 带了三个 ABI**：真机只用 arm64（约 780 KiB），armv7 / x86_64 那 1.3 MiB 是陪跑。
   Play 走 AAB 按 ABI 拆分不受影响；github / R2 是 universal 包。可选：这两个渠道也按 ABI 出分包，
   或只保留 arm64（放弃 armv7 老机与 x86_64 模拟器）。属分发策略，待判断。
2. **x86（32 位）ABI 空洞**：APK 内 `lib/x86/` 只有 androidx 的 `.so`，没有 `libquickjs_kmp.so`
   （quickjs-kmp 不编 x86）。32 位 x86 设备现实中基本绝迹，但装到这种设备上打开订阅页会
   `UnsatisfiedLinkError`。堵法是 androidApp 加 `abiFilters` 显式排除 x86。待判断。
3. **source map 随 release 包（22 KiB）**：保留是为了日志里的错误栈能映射回 `.tsx`（`TinyUI.debug`
   在 release 为 false，失败页不带栈，栈只进日志）。占比小，暂维持。

## 复现协议

1. 基线：`gh release download <tag> -p 'TrendingAI-<tag>-android.apk'`（github 渠道）。
2. 对比包：`./gradlew :androidApp:assembleGithubRelease`，取 `androidApp/build/outputs/apk/github/release/*.apk`。
   渠道必须一致（r2 与 github 都含 updater，尺寸相同，但仍按同渠道比）。
3. `diffuse diff <old.apk> <new.apk> > diff.txt`（`brew install diffuse`）。首表看分类，`==== APK ====`
   节看逐文件，`==== DEX ====` 节看类 / 方法级明细。
4. 看 ABI 覆盖：`unzip -l <new.apk> 'lib/*'`，arm64-v8a / armeabi-v7a / x86_64 三个目录的 `.so` 清单应一致；`lib/x86/` 缺 `libquickjs_kmp.so` 是待决项 2 的已知现状，待决后按其结论（排除 x86 或补 so）改这条校验。

## 增量：tinyui 0.8.0（ADR-007 职责边界，2026-09-25）

订阅页的请求、文案、Pro 态、下单、图标搬进页面包，框架自带 http（ktor）/ 存储（okio）/ i18n / toast 等。
同机同口径：main（tinyui 0.7.0）与 `feat/tinyui-framework-boundary`（tinyui 0.8.0，Maven 坐标、非 composite）
各 `clean` 后 `assembleGithubRelease`，`diffuse 0.3.0` 对比。

| 分类 | 增量（compressed） | 增量（uncompressed） |
|---|---|---|
| dex | +2.3 KiB | +3.3 KiB |
| native | -4.8 KiB | 0（`libandroidx.graphics.path.so` 压缩波动） |
| asset | +4.3 KiB | +5.7 KiB |
| **total** | **+1.9 KiB** | **+9.1 KiB** |

dex 类数 6419 → 6403（-16：宿主能力、props、PaywallContent 删掉，框架新代码抵消）。ktor 与 okio 本来就在 App 里，
框架改依赖它们不增加体积。asset 增量是页面字节码（6.5 → 12.3 KB，逻辑从 Kotlin 搬进 JS）与两份 i18n（1.6 KB）。

**测量踩坑**：Compose 资源拷到 assets 时不删旧文件，同一构建目录里早期布局遗留的 `files/tinyui/runtime/*`、`*.js.map`
会一直留在 APK 里（这次未 clean 时多出 64 KiB）。测体积前先删 `androidApp/build` 与 `shared/build`。

