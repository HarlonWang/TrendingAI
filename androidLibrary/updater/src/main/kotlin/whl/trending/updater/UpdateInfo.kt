package whl.trending.updater

import whl.trending.ai.update.WhatsNewInfo

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    /** 新版本的更新内容（来自 release asset whatsnew.json）；取不到时为 null，弹窗回退纯版本提示 */
    val whatsNew: WhatsNewInfo? = null,
)
