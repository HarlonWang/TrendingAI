package whl.trending.ai.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 举报 AI 生成内容的统一入口：chat 库没有导航栈，经总线让根部 App 打开预填好的反馈页。
 * 与 [ProPaywall] 同款机制。
 */
object ContentReport {

    /** 举报请求，值为被举报内容的 URL；根部 App 收集后进反馈页。 */
    private val _requests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val requests: SharedFlow<String> = _requests.asSharedFlow()

    fun open(url: String) {
        _requests.tryEmit(url)
    }
}
