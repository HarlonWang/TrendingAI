package whl.trending.ai.update

import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.AppConfigResponse
import whl.trending.ai.data.remote.TrendingApi

/**
 * 拉取 /api/app-config 并把各字段落进本地缓存，返回响应供调用方消费；
 * 失败返回 null——fail-open，接口未上线（404）/断网都不影响使用，调用方以缓存兜底。
 * app-config 新增字段的缓存落地写在这里。
 */
suspend fun refreshAppConfig(): AppConfigResponse? =
    runCatching { TrendingApi().fetchAppConfig() }
        .onSuccess { config ->
            globalSettingsManager.setCachedMinVersion(config.minVersion)
            globalSettingsManager.setChatImagesConfig(
                config.chatImages?.maxCount,
                config.chatImages?.perImageJpegKb,
            )
        }
        .onFailure { println("refreshAppConfig failed: $it") }
        .getOrNull()
