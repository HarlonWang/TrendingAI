# F-Droid：TinyUI 页面从源码构建

F-Droid 不接受仓库里提交的编译产物，所以 fdroid 渠道的 TinyUI 页面字节码（`.jsb`）在 F-Droid 构建时从这里的源码快照重新生成，并逐页校验与线上逐字节一致。其他渠道照旧用 `shared/.../files/tinyui/<pkg>/pages/` 里提交的 `.jsb`。方案与取舍见 `docs/fdroid-tinyui-build.md`。

AGP 不认识 flavor 目录下的 `tinyui/`，这里的东西不进任何 APK。

## 文件

| 文件 | 谁调用 | 作用 |
|---|---|---|
| `sync.sh` | `scripts/release-smoke.sh` | 按各包 `manifest.json` 的 `version` 后缀（页面 commit）把 `source/<pkg>/` 刷新成页面仓库在该 commit 的完整导出（除 `.github/`） |
| `build.sh` | F-Droid 配方（无参数）；`release-smoke.sh`（`--out <dir>`） | 从快照 `pnpm install --frozen-lockfile && tinyui build`，按 manifest 的 `hashes` 逐页校验，写回 `pages/`（或 `--out` 目录） |
| `source/<pkg>/` | — | 页面源码快照，**由 `sync.sh` 生成，勿手改**；手改会让哈希对不上，发版冒烟直接失败 |
| `source/<pkg>/SOURCE` | — | `repo=` 页面仓库地址，`commit=` 完整 commit |

## 约定

- **manifest 与 i18n 始终是线上原件，只重编 `.jsb`。** 本地构建的 manifest 时间戳每次不同、缺 `hostVersion`，还会让热下发误判「内置更新」。
- **以 manifest 为唯一依据**：生成哪些页、每页的哈希都从 manifest 读，脚本里不写任何页面名。新增或删除页面不用改这里，也不用改 fdroiddata。
- **pnpm 版本取快照 `package.json` 的 `packageManager`**，经 `npx` 调用；F-Droid 配方只装 `nodejs npm`，页面仓库升 pnpm 不用改配方。
- **编译器**：默认是页面仓库 lockfile 里的 npm 版 `qjsc-kmp`；设了 `TINYUI_QJSC` 时 tinyui-cli 优先用它（F-Droid 要求从源码编 qjsc-kmp 时的接口，见文档 §7 预案 A）。

## 新增一个 TinyUI 包

1. 手写 `source/<新包>/SOURCE`，只需一行 `repo=https://github.com/<owner>/<repo>`，然后跑 `sync.sh` 或直接跑发版冒烟。
2. **fdroiddata 配方加一行** `rm: shared/src/commonMain/composeResources/files/tinyui/<新包>/pages`。漏了的话 `build.sh` 会报「pages 仍存在」而失败，不会悄悄打出旧字节。

## fdroiddata 配方（参考）

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
