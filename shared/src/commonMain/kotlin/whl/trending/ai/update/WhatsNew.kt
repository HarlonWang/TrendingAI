package whl.trending.ai.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import whl.trending.ai.core.platform.getAppVersion
import whl.trending.ai.data.local.globalSettingsManager

/**
 * 随 APK 内置的新版本更新说明（composeResources/files/whatsnew.json）。
 * 仓库里提交的是 version=0.0.0 的占位文件，CI 打 tag 构建前用 AI 生成的真实内容临时覆盖。
 */
@Serializable
data class WhatsNewInfo(
    val version: String = "",
    val zh: List<String> = emptyList(),
    val en: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = zh.isEmpty() && en.isEmpty()
}

private val whatsNewJson = Json { ignoreUnknownKeys = true }

fun parseWhatsNew(raw: String): WhatsNewInfo? =
    runCatching { whatsNewJson.decodeFromString<WhatsNewInfo>(raw) }.getOrNull()

/**
 * 升级后首启弹一次：
 * - lastSeen 为空（首次安装）不弹，由调用方静默写入当前版本
 * - lastSeen == 当前版本（日常启动）不弹
 * - 内置 changelog 版本与当前版本不一致（dev 构建 / CI 生成失败的占位文件）不弹
 */
fun shouldShowWhatsNew(
    currentVersion: String,
    bundledVersion: String,
    lastSeenVersion: String?,
): Boolean =
    lastSeenVersion != null &&
        lastSeenVersion != currentVersion &&
        bundledVersion == currentVersion

/**
 * 本次启动是否待弹 What's New，供 updater 跳过同一次启动的自动更新检查，避免双弹窗叠加。
 * 不读内置 JSON（suspend），用 lastSeen != current 近似——即便最终因 JSON 不匹配没弹，
 * WhatsNewHost 也会在该次启动把 lastSeen 写成当前版本，下次启动恢复自动检查。
 */
fun isWhatsNewPending(): Boolean {
    val lastSeen = globalSettingsManager.getLastSeenWhatsNewVersion()
    return lastSeen != null && lastSeen != getAppVersion()
}
