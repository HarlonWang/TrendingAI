package whl.trending.chat.engine

/** 语音转写结果；[languages] 为服务端检测到的语言码（可空）。 */
data class Transcription(val text: String, val languages: List<String> = emptyList())

/**
 * 语音录入的转写段：音频进、纯文本出。文本随后走 [ChatEngine.send] 的普通发送路径，
 * 对话侧不感知音频。正式注入 [ChatApi]；Demo 不注入（麦克风入口随之隐藏）。
 */
interface VoiceTranscriber {
    /**
     * @param path 本地音频文件（AAC m4a）
     * @param durationMs 录音时长，服务端校验上限与成本折算的兜底依据
     * @throws ChatException HTTP 非 2xx / 传输异常，分类同 [ChatEngine.send]
     */
    suspend fun transcribe(path: String, durationMs: Long): Transcription
}
