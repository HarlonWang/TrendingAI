# 分发渠道说明

Trending AI 通过多条渠道分发,不同渠道的 APK 来源与签名不同,**互不兼容跨渠道升级**(签名不一致需卸载重装)。本文档说明各渠道机制,以及第三方更新器的接入方式。

## 渠道总览

| 渠道 | APK 来源 | 签名 | 含应用内更新器 |
| --- | --- | --- | --- |
| 官网 / R2 直链 | `download.trendingai.cn/TrendingAI-latest.apk`(`r2` flavor) | 本仓 key | ✅ 跳官网 |
| GitHub Release | release 里的 `androidApp-github-release.apk`(`github` flavor) | 本仓 key | ✅ 跳 GitHub Release |
| Google Play | Play 商店(`play` flavor,AAB) | Play 签名 | ❌ 商店自管 |
| F-Droid 官方仓 | F-Droid buildserver 从源码自建(`fdroid` flavor) | **F-Droid key** | ❌ 客户端自管 |
| **Obtainium** | 直拉 GitHub Release 的 `androidApp-fdroid-release.apk`(`fdroid` flavor) | 本仓 key | ❌(用 Obtainium 自更新) |

> **签名提示**:Obtainium / 官网 / GitHub Release 三者同为本仓 key,可互相覆盖升级;F-Droid 官方仓与 Google Play 用各自的签名,与上述三者**不可跨渠道升级**。

---

## Obtainium 接入

[Obtainium](https://github.com/ImranR98/Obtainium) 是一款直接追踪 GitHub Release 的开源自动更新器,适合不想依赖商店、又希望自动更新的用户。

推荐让 Obtainium 抓取 **`fdroid` flavor 包**(`androidApp-fdroid-release.apk`):它**不含应用内更新器**,避免和 Obtainium 自身的更新机制重复弹窗。

> `fdroid` flavor 包自 **0.16.0 之后的版本** 起随 Release 发布;更早的 Release 仅有 `github` 包,Obtainium 也能用,只是会多一个冗余的应用内更新提示。

### 方式一:手动添加

1. Obtainium → **Add App**
2. **App Source URL** 填:`https://github.com/HarlonWang/TrendingAI`
3. 展开 **Filter APKs by Regular Expression**,填:`fdroid`
4. 保存即可,后续新版本 Obtainium 会自动检测并提示更新。

### 方式二:一键深链

在已装 Obtainium 的设备上点击下方链接,会自动带上 `fdroid` 过滤规则:

```
obtainium://app/%7B%22id%22%3A%20%22whl.trending.ai%22%2C%20%22url%22%3A%20%22https%3A%2F%2Fgithub.com%2FHarlonWang%2FTrendingAI%22%2C%20%22author%22%3A%20%22HarlonWang%22%2C%20%22name%22%3A%20%22Trending%20AI%22%2C%20%22additionalSettings%22%3A%20%22%7B%5C%22apkFilterRegEx%5C%22%3A%20%5C%22fdroid%5C%22%2C%20%5C%22invertAPKFilter%5C%22%3A%20false%2C%20%5C%22about%5C%22%3A%20%5C%22%E5%85%A8%E7%90%83%E6%8A%80%E6%9C%AF%E7%83%AD%E7%82%B9%EF%BC%8CAI%20%E7%B2%BE%E9%80%89%E9%80%9F%E9%80%92%5C%22%7D%22%7D
```

---

## F-Droid 官方仓(进行中)

通过 [fdroiddata MR #40018](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/40018) 提交,等待 F-Droid 维护者 test 与合并。机制要点:

- F-Droid **从源码自建** `fdroid` flavor(用 F-Droid 自己的 key 签名),不下载本仓的预编译 APK。
- 版本检测走 `UpdateCheckMode: HTTP`,读取每个 Release 附带的 `version_code.txt`;配合 `AutoUpdateMode: Version %v`,**合并后新 tag 自动跟随,无需逐版改 MR**。
- MR **合并前**,若发布了新版本,需手动把 MR 的 build 条目(versionName / versionCode / commit hash)同步到最新 tag。

> 因签名不同,F-Droid 官方仓版本与本仓 key 版本不可互相覆盖升级,用户在渠道间迁移需卸载重装。
