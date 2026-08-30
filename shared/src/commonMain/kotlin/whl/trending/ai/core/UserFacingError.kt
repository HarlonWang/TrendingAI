package whl.trending.ai.core

import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import org.jetbrains.compose.resources.getString
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.error_network

/** 请求异常转面向用户的文案：网络类异常收敛为本地化短句，其余取异常 message。 */
suspend fun Throwable.toUserMessage(): String = when {
    isNetworkError() -> getString(Res.string.error_network)
    else -> message ?: "Unknown Error"
}

// iOS 的 Ktor Darwin 异常（含整段 NSError dump 的 message）也是 IOException 子类，靠类型
// 而非 message 内容识别；cause 链上溯覆盖被包装的场景
private fun Throwable.isNetworkError(): Boolean =
    this is IOException || this is HttpRequestTimeoutException || cause?.isNetworkError() == true
