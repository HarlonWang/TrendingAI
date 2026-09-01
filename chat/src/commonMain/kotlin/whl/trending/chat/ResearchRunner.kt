package whl.trending.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import whl.trending.chat.engine.ChatEngine
import whl.trending.chat.engine.ChatException
import whl.trending.chat.host.ChatAiEvent
import whl.trending.chat.host.ChatAiKind
import whl.trending.chat.host.ChatAiOutcome
import whl.trending.chat.model.ChatError
import whl.trending.chat.model.ChatErrorCategory
import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.MessageKind
import whl.trending.chat.store.ChatStore

/**
 * Deep Research 的提交与轮询。任务是服务端资产（已扣费），生命周期独立于会话切换：
 * 轮询终局先写库，再经 [applyToVisible] 按消息 id 更新可见列表（不在当前会话就只落库，
 * 用户切过去即见终局）。消息 id 即 store 行 id，全局唯一，可见性判定不需要 threadId。
 *
 * @param applyToVisible (messageId, transform)：该消息在当前可见列表中则应用变换，否则忽略
 */
internal class ResearchRunner(
    private val scope: CoroutineScope,
    private val engine: ChatEngine,
    private val store: ChatStore,
    private val track: (ChatAiEvent) -> Unit,
    private val applyToVisible: (Long, (ChatMessage) -> ChatMessage) -> Unit,
) {

    private class Polling(val threadId: Long, val job: Job)

    private val jobs = mutableMapOf<Long, Polling>()

    /**
     * 提交任务。成功：占位行落库、启动轮询，返回占位消息（searching=true）；
     * 失败：错误行落库并记终态埋点，返回错误消息。两种返回调用方直接追加即可。
     */
    suspend fun submit(threadId: Long, topic: String): ChatMessage {
        val runId = try {
            engine.createResearch(topic)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 不只 catch ChatException：Demo/预览引擎的默认实现抛 UnsupportedOperationException，逃逸即崩
            val error = (e as? ChatException)?.error
                ?: ChatError(ChatErrorCategory.UNKNOWN, detail = e.toString())
            track(failureEvent(MessageKind.DEEP_RESEARCH, error))
            return store.appendErrorMessage(threadId, MessageKind.DEEP_RESEARCH, error)
        }
        val placeholder = store.appendResearchPlaceholder(threadId, runId)
        startPolling(threadId, placeholder.id, runId)
        return placeholder.copy(searching = true)
    }

    /** 幂等：同一占位行已有在途轮询则不重复发起 */
    fun startPolling(threadId: Long, messageId: Long, runId: String) {
        if (jobs[messageId]?.job?.isActive == true) return
        val job = scope.launch {
            try {
                poll(threadId, messageId, runId)
            } finally {
                jobs.remove(messageId)
            }
        }
        jobs[messageId] = Polling(threadId, job)
    }

    /**
     * 跨进程恢复**所有**未完成轮询，与「当前打开哪个会话」无关——挂着任务的会话
     * 可能永远不被打开，但任务已扣费，轮询照常落库，用户从抽屉切过去就能看到报告。
     */
    suspend fun resumeAll() {
        store.pendingResearch().forEach { pending ->
            startPolling(pending.threadId, pending.messageId, pending.runId)
            applyToVisible(pending.messageId) { it.copy(searching = true) }
        }
    }

    /** 线程行将消失，终局写库会撞外键，轮询一并取消 */
    fun cancelForThread(threadId: Long) {
        jobs.filterValues { it.threadId == threadId }.forEach { (messageId, polling) ->
            polling.job.cancel()
            jobs.remove(messageId)
        }
    }

    /**
     * 快轮 8s 覆盖正常时长，转慢轮 60s 直到盖过服务端 2h 超龄判死闸——服务端保证死任务
     * 终会转 failed，客户端跟到那个终态为止。绝对上限仍无终态（异常）→ 可重试错误，
     * runId 保留（重试恢复轮询，不重复扣费）。
     */
    private suspend fun poll(threadId: Long, messageId: Long, runId: String) {
        repeat(MAX_FAST_POLLS + MAX_SLOW_POLLS) { attempt ->
            delay(if (attempt < MAX_FAST_POLLS) FAST_POLL_MS else SLOW_POLL_MS)
            val run = try {
                engine.pollResearch(runId)
            } catch (e: ChatException) {
                // 瞬态（网络/超时/5xx）继续轮；永久错误（鉴权/配额/非法请求）终局呈现。
                // runId 保留：任务可能仍在服务端跑完，重试可恢复轮询
                if (e.error.category.retryable) return@repeat
                track(failureEvent(MessageKind.DEEP_RESEARCH, e.error))
                store.markResearchError(threadId, messageId, e.error, runId = runId)
                applyToVisible(messageId) { it.copy(searching = false, error = e.error) }
                return
            }
            when (run.status) {
                "completed" -> {
                    val report = run.report.orEmpty()
                    if (report.isBlank()) {
                        // 空报告视同失败（服务端已按失败退款）；runId 置空防止重启误续死任务
                        val error = ChatError(ChatErrorCategory.SERVER, detail = "empty report")
                        track(ChatAiEvent.Completed(ChatAiKind.RESEARCH, ChatAiOutcome.ERROR, reason = "empty_report"))
                        store.markResearchError(threadId, messageId, error, runId = null)
                        applyToVisible(messageId) { it.copy(searching = false, error = error, researchRunId = null) }
                    } else {
                        store.completeResearch(threadId, messageId, report, runId, run.model)
                        applyToVisible(messageId) { it.copy(content = report, searching = false, model = run.model) }
                        track(ChatAiEvent.Completed(ChatAiKind.RESEARCH, ChatAiOutcome.OK))
                    }
                    return
                }
                "failed" -> {
                    val error = ChatError(ChatErrorCategory.SERVER, detail = run.error ?: "research failed")
                    track(ChatAiEvent.Completed(ChatAiKind.RESEARCH, ChatAiOutcome.ERROR, reason = run.error ?: "unknown"))
                    store.markResearchError(threadId, messageId, error, runId = null)
                    applyToVisible(messageId) { it.copy(searching = false, error = error, researchRunId = null) }
                    return
                }
                else -> Unit // running：继续
            }
        }
        val error = ChatError(ChatErrorCategory.TIMEOUT, detail = "research polling exhausted")
        track(failureEvent(MessageKind.DEEP_RESEARCH, error))
        store.markResearchError(threadId, messageId, error, runId = runId)
        applyToVisible(messageId) { it.copy(searching = false, error = error) }
    }

    private companion object {
        const val FAST_POLL_MS = 8_000L
        const val MAX_FAST_POLLS = 90
        const val SLOW_POLL_MS = 60_000L
        const val MAX_SLOW_POLLS = 110
    }
}
