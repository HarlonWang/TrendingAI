package whl.trending.ai.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.local.LastDataCache
import whl.trending.ai.data.local.SettingsManager
import whl.trending.ai.data.local.globalLastDataCache
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.remote.DetailSummaryApi
import whl.trending.ai.data.remote.DigestError
import whl.trending.ai.data.remote.DigestException

data class DigestUiState(
    val markdown: String = "",
    /** 初值即 true：构造即 load()，首帧到状态落地之间不该出现「不加载也无内容」的空窗 */
    val isStreaming: Boolean = true,
    val error: DigestError? = null,
)

/**
 * 本地缓存版本位。服务端 bump prompt_version 让旧解读失效后，客户端这份本地缓存
 * 不跟着换键的话，老用户永远读不到新解读——两边必须成对 bump。
 */
private const val DIGEST_CACHE_VERSION = 1

/** 本地缓存载体：解读内容按 (source, externalId, lang) 固定，存下来再进页面即可秒开 */
@Serializable
internal data class CachedDigest(val markdown: String)

/**
 * 条目 AI 解读页的状态持有者。
 *
 * 与列表页的 SWR 不同，这里**命中本地缓存就不再请求**：服务端按条目身份缓存解读，
 * 同一条目重复请求拿到的必然是同一篇，再发一次只是白跑一趟网络。
 * 需要更新时由服务端 bump prompt_version + 客户端换 cacheKey 版本位统一失效。
 */
class DigestViewModel(
    private val source: String,
    private val externalId: String,
    private val api: DetailSummaryApi = DetailSummaryApi.shared,
    private val settingsManager: SettingsManager = globalSettingsManager,
    private val cache: LastDataCache = globalLastDataCache,
    /** 埋点出口。默认直连全局实现；单测注入空实现——它依赖平台 Settings，宿主机测试里会 NPE */
    private val track: (String, Map<String, Any>) -> Unit = ::trackEvent,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DigestUiState())
    val uiState: StateFlow<DigestUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        load()

        // 未登录被拦下后，用户在本页点登录 CTA 完成登录 → 自动接着生成，
        // 不用回来再点一次「重试」。drop(1) 丢弃订阅时的当前值。
        viewModelScope.launch {
            globalAuthManager.authState.drop(1).collect { state ->
                if (state is AuthState.LoggedIn && _uiState.value.error == DigestError.LoginRequired) {
                    load()
                }
            }
        }
    }

    private fun cacheKey(lang: String) = "digest_v${DIGEST_CACHE_VERSION}_${source}_${externalId}_$lang"

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            // 语言只解析一次，请求与缓存键共用：生成一篇解读要几十秒，期间用户完全
            // 可能去设置里改摘要语言，两次各解析一次会把中文正文写到英文键上
            val lang = settingsManager.currentContentLang()
            val key = cacheKey(lang)
            val cached = cache.get<CachedDigest>(key)
            // 空内容不算命中：历史版本可能写进过空缓存，命中它会让页面永久空白且无重试入口
            if (cached != null && cached.markdown.isNotBlank()) {
                _uiState.update { it.copy(markdown = cached.markdown, isStreaming = false, error = null) }
                return@launch
            }
            _uiState.update { it.copy(markdown = "", isStreaming = true, error = null) }
            try {
                val result = api.stream(source, externalId, lang) { delta ->
                    _uiState.update { it.copy(markdown = it.markdown + delta) }
                }
                // 服务端零 delta 走完（内容过滤拒答等）照样发 done:true，正文却是空的。
                // 不拦下来就会：写入空缓存 → 页面永久空白 → 连重试入口都没有。
                if (result.markdown.isBlank()) {
                    throw DigestException(DigestError.Retryable("empty digest"))
                }
                cache.put(key, CachedDigest(result.markdown))
                // 收尾以返回的全文为准，而不是留着 delta 累积的结果：两者本应一致，
                // 但缓存里存的是全文，让屏幕与缓存有唯一同源
                _uiState.update { it.copy(markdown = result.markdown, isStreaming = false, error = null) }
                track("digest_generated", mapOf("source" to source, "cached" to result.cached))
            } catch (e: DigestException) {
                // 断流时已渲染的半截内容一并丢弃：残篇比空白更容易被误当成完整解读
                _uiState.update { it.copy(markdown = "", isStreaming = false, error = e.error) }
                track("digest_failed", mapOf("source" to source, "reason" to e.error.eventName()))
            }
        }
    }

    fun retry() = load()

    fun signIn() = globalAuthManager.signIn("digest_login_gate")
}

/** 埋点用的稳定短名，避免把 data class 的 toString 直接送进事件属性 */
private fun DigestError.eventName(): String = when (this) {
    DigestError.LoginRequired -> "login_required"
    is DigestError.Quota -> if (global) "quota_global" else "quota_device"
    DigestError.NoContent -> "no_content"
    DigestError.NotFound -> "not_found"
    is DigestError.Retryable -> "retryable"
}
