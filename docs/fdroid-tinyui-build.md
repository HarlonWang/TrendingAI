# F-Droid 构建 TinyUI 内置包：方案说明

起草于 2026-10-10，起因是 issue #153。状态：**方案已定，待审核员表态后实施**（实施步骤见 §8）。

## 1. 背景

TrendingAI 的订阅页和数据来源页是 TinyUI 页面，以 QuickJS 字节码（`.jsb`）的形式作为内置包随 App 打包，路径是 `shared/src/commonMain/composeResources/files/tinyui/<pkg>/`。内置包由 `release-smoke.sh` 通过 `tinyui pull` 从线上 production 拉取后提交进仓库。

F-Droid 的扫描器拦下了这些字节码（当时还是 `.bin`，issue #153）。审核员不接受直接使用提交进仓库的编译产物，要求在构建时从源码生成。bot 生成的 1.9.1 构建条目（fdroiddata !50109）只是 `rm` 掉了文件，这样订阅页会加载失败。

## 2. 目标与边界

**目标**
- F-Droid 构建时，从公开源码重新生成全部页面字节码，仓库里提交的 `.jsb` 不进入 F-Droid 的 APK。
- F-Droid 版和其他渠道的页面字节逐字节一致，由校验机制保证。
- 新增或删除页面、日常发版，都不需要修改 fdroiddata，bot 自动更新恢复正常。
- 和 F-Droid 相关的东西集中放在 fdroid 渠道目录 `androidApp/src/fdroid/`。

**不在范围内**
- github、r2、play、iOS 的构建和产物：保持不变，继续使用仓库里提交的 `.jsb`。
- 热下发机制：fdroid 渠道暂不调整，风险见 §7。
- tinyui、quickjs-kmp、trendingai-tinyui 三个上游仓库：都不需要改动。
- `fastlane/metadata/`：F-Droid 约定从仓库根目录读取，保持原位。

## 3. 核心思路

1. **仓库里继续提交 `.jsb`。** 其他渠道照常使用，构建链路不变。
2. **F-Droid 构建时删掉重编。** 配方 `rm` 掉每个包的 `pages/` 目录，再由 fdroid 目录下的脚本，从随仓库提交的页面源码快照重新生成。这是 fdroiddata 处理「上游提交了预编译产物」的标准做法。
3. **以线上 manifest 为唯一依据。** 内置包的 `manifest.json` 是线上原件，记录了页面源码 commit（`version` 字段的后缀）、页面列表（`files`）和每页的 sha256（`hashes`）。脚本据此确定生成哪些页面，并逐页校验，任何地方都不写死文件名。
4. **构建工具链和线上发布完全相同。** 线上页面由页面仓库的 CI（`staging.yml`）在 `ubuntu-latest` 上执行 `pnpm install --frozen-lockfile` + `tinyui build` 产出。F-Droid 走同一条命令、同一份 lockfile，qjsc-kmp、esbuild、oxc-parser 都来自 npm 的同一版本。

**已验证**（2026-10-10，macOS arm64）：把页面源码切到 commit `fe4ec117` 后构建，两个页面的 `.jsb` 和两份 i18n 文件都和仓库当前提交的版本逐字节一致。编译器分别试了 npm 的预编译版和从源码编的 quickjs-kmp 0.1.2（`294538d3`），结果相同。本地构建出的 `manifest.json` 只在 `createdAt`、`version` 时间戳和缺少 `hostVersion` 上与线上原件不同，所以只替换 `.jsb`、不替换 manifest。

为什么不能用本地构建的 manifest：
- 它的 `createdAt` 和 `version` 是构建时刻，每次构建都不同，APK 不可复现。
- 它缺少 `hostVersion`，发版检查不过。
- 它的 `createdAt` 会比线上同一版本更新，热下发会判定「内置更新，跳过」，导致这个版本的热更新全部失效（tinyui `docs/updates.md` §4.3）。

## 4. 仓库改动（TrendingAI）

### 4.1 目录

```
androidApp/src/fdroid/
├── kotlin/…                       （已有）
└── tinyui/
    ├── README.md                  作用、流程、「source/ 自动生成，勿手改」、新增包时的配方提醒
    ├── sync.sh                    发版时调用：按各包 manifest 的 commit 刷新 source/
    ├── build.sh                   F-Droid 和发版守护调用：从 source/ 生成各包页面、校验、写回
    └── source/
        └── <pkg>/                 每个包一份页面仓库快照（目前只有 trendingai）
            └── SOURCE             来源仓库 URL 和完整 commit
```

AGP 会忽略 flavor 目录里不认识的子目录，这个目录对 Android 构建没有任何影响。

### 4.2 `sync.sh`（发版时调用）

对 `shared/.../files/tinyui/` 下每个含 `manifest.json` 的包：
1. 从 manifest 的 `version` 字段取出 commit 后缀。
2. 下载页面仓库在这个 commit 的 GitHub tarball，取完整文件导出，CI 目录 `.github/` 除外。
3. 用导出的文件覆盖 `source/<pkg>/`，并写入 `SOURCE`，记录仓库 URL 和完整 commit。

快照是页面仓库的完整导出，新增页面的源码会自动带进来。

### 4.3 `build.sh`（F-Droid 构建和发版守护时调用）

对每个包：
1. **来源校验**：`source/<pkg>/SOURCE` 里的 commit 必须等于 manifest `version` 的后缀。
2. **删除检查**：在 F-Droid 模式下，`pages/` 必须已经被删除。没删说明配方漏了这个包的 `rm`，直接报错。
3. **构建**：在快照目录执行 `pnpm install --frozen-lockfile` 和 `tinyui build`。编译器默认用 npm 的 qjsc-kmp；设置了 `TINYUI_QJSC` 时用它指定的那份，这是留给 §7 预案 A 的接口。
4. **逐页校验**：
   - manifest `files` 里列出的每一页，都必须生成出来，且 sha256 等于 `hashes` 里的值；
   - 生成结果里出现 manifest 没有的页面，同样算失败。
5. **写回**：把 `.jsb` 写进 `shared/.../files/tinyui/<pkg>/pages/`。manifest 和 i18n 不动。

上述任何一步失败，脚本都以非零状态退出，构建随之失败，不可能打出和线上不同的页面。

另外支持 `--out <dir>` 参数，把产物写到临时目录而不写回仓库，也跳过第 2 步的删除检查，供发版守护使用。

### 4.4 `release-smoke.sh`（新增两行调用）

在现有的 `tinyui pull` 之后：
1. 调用 `sync.sh`，刷新快照，和 manifest、`.jsb` 放在同一个 commit 里提交。
2. 调用 `build.sh --out <临时目录>`，确认 F-Droid 那条生成路径可用、哈希一致。失败就和其他冒烟检查一样，禁止发版。

除此之外，fdroid 目录之外没有其他改动，CI 配置也不用改。

## 5. F-Droid 配方（fdroiddata）

```yaml
  - versionName: x.y.z
    versionCode: …
    commit: …
    subdir: androidApp
    sudo:
      - apt-get update
      - apt-get install -y nodejs npm
    gradle:
      - fdroid
    rm:
      - shared/src/commonMain/composeResources/files/tinyui/trendingai/pages
    prebuild: sed -i -e '/foojay/d' ../settings.gradle.kts
    build: src/fdroid/tinyui/build.sh
```

- `rm` 删除整个 `pages/` 目录，不逐个列文件名，新增页面时不需要改。
- 生成步骤放在 `build:`，在扫描器之后执行。
- 不需要 srclib，不需要 cmake。qjsc-kmp 的版本由页面仓库的 lockfile 决定，和线上构建天然一致。
- Node 的安装方式（构建机自带的版本不够时）要看构建机的实际环境，见 §7。
- **提交配方的 MR 说明里主动列出**：构建时会从 npm 获取 esbuild、oxc-parser、qjsc-kmp 三个预编译工具，都是 MIT 许可；其中 qjsc-kmp 可以随时改为从源码构建，并附上切换方式（§7 预案 A）。

## 6. 迭代场景

| 变化 | TrendingAI | fdroiddata |
|---|---|---|
| 只改页面，不发 App | 无，页面照常经 staging、production 热下发 | 无 |
| 发 App 版本 | 照常跑 `release-smoke.sh`，快照自动同步 | 无，bot 自动生成构建条目 |
| 新增或删除页面 | 无，脚本以 manifest 为准 | 无 |
| 页面仓库升级 tinyui 或 quickjs-kmp | 无，快照里带着新的 lockfile | 无 |
| bump `hostVersion` 或换签名公钥 | 无，manifest 是线上原件 | 无 |
| 新增一个 TinyUI 包 | 无，脚本自动遍历 | **加**一行 `rm …/<新包>/pages`。漏加的话 `build.sh` 会报错 |
| 快照被手改，或没有同步 | 发版守护失败，禁止发版 | — |

## 7. 风险与预案

**风险**
1. **npm 上的预编译构建工具。** esbuild（Go 编写）、oxc-parser（Rust 编写）、qjsc-kmp（C 编写）都通过 npm 获取预编译二进制。F-Droid 收录政策允许的预编译来源只有 Debian 和几个 Maven 仓库，npm 不在其中（Hermes 是点名豁免），有被要求改动的可能。**这是最大的不确定性。**
2. **F-Droid 构建机的 Node 版本。** 页面仓库要求 Node ≥22，构建机的 Debian 自带版本可能不够，还没查证。
3. **热下发。** fdroid 渠道会在运行时下载页面字节码，和 F-Droid 收录政策里「未经用户 opt-in 不得下载可执行代码」那一条有冲突。本次暂不处理，风险依然存在。
4. **Linux 上的一致性。** 逐字节一致目前只在 macOS 上实测过。不过线上字节本来就是在 Linux 上用同一套工具链产出的，风险很低，还是要在 §8 第 3 步确认。

**预案 A：只有 qjsc-kmp 被要求从源码构建。** 只改 fdroiddata 配方，TrendingAI 不用发版：

```yaml
    sudo:
      - apt-get update
      - apt-get install -y cmake nodejs npm
    srclibs:
      - quickjs-kmp@<与页面仓库 lockfile 中 qjsc-kmp 一致的 tag>
    build:
      - cmake -S $$quickjs-kmp$$/native -B $$quickjs-kmp$$/build -DCMAKE_BUILD_TYPE=Release -DQJS_TOOLS=ON
      - cmake --build $$quickjs-kmp$$/build --target qjsc-kmp
      - TINYUI_QJSC=$$quickjs-kmp$$/build/qjsc-kmp src/fdroid/tinyui/build.sh
```

另外需要新增 `srclibs/quickjs-kmp.yml`（`RepoType: git`，`Repo: https://github.com/HarlonWang/quickjs-kmp.git`）。从那以后，每次升级 quickjs-kmp 都要同步修改 srclib 的 tag；漏改的话哈希校验不过，构建失败。这条路径已经实测过：`tinyui-cli` 查找编译器的优先级是 `--qjsc` > `TINYUI_QJSC` > npm 包 > `PATH`，所以 `TINYUI_QJSC` 会优先于 npm 包生效；路径无效时直接报错，不会悄悄退回预编译版。

**预案 B：esbuild 和 oxc-parser 也不被接受，或者 Node 版本无法满足。** 在快照里额外放入 esbuild 打包后的中间产物 `pages/*.js`。它可读、没有压缩，订阅页约 11 KB。这样 F-Droid 只需要从源码编 qjsc-kmp（即预案 A），再编译这些 `.js`，不再需要 Node、npm、esbuild 和 oxc。代价有两个：审核员可能认为 `.js` 也是生成物；还要先验证这种方式编出的字节和 `tinyui build` 一致。这一项需要改 TrendingAI，要跟一个版本发出去。

## 8. 实施步骤

1. 在 #153 回复审核员，说明本方案的思路，等对方表态。
2. TrendingAI 开分支，写 `androidApp/src/fdroid/tinyui/` 下的三个文件，改 `release-smoke.sh`，生成首份快照。
3. 在 Linux 环境（Debian 容器）里验证：删掉 `pages/` 后跑 `build.sh`，哈希全部一致。顺带验证预案 A 的从源码构建路径。
4. 本地模拟 F-Droid 流程：删掉 `pages/`，跑 `build.sh`，再执行 `assembleFdroidRelease`，在模拟器上打开订阅页确认正常。
5. 发一个包含上述改动的版本。
6. 向 fdroiddata 提交构建配方，替换 bot 生成的 1.9.1 条目，MR 说明里列出三个 npm 预编译工具。
7. 视审核员的答复，决定是否启用预案 A 或 B。

## 9. 待定

- 承接这些改动的版本号，比如 1.9.3。

## 参考

- issue：https://github.com/HarlonWang/TrendingAI/issues/153
- fdroiddata 元数据：https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/whl.trending.ai.yml
- bot 生成的 1.9.1 条目：https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50109
- F-Droid 收录政策：https://f-droid.org/docs/Inclusion_Policy/
- fdroiddata 的 React Native 构建模板：https://gitlab.com/fdroid/fdroiddata/-/raw/master/templates/build-react-native.yml
- tinyui 内置包与热下发设计：tinyui 仓库 `docs/updates.md`（§1.1 manifest 字段、§1.4 内置包、§4.3 更新判定）
