package whl.trending.chat.engine.byok

/** BYOK 端点 URL 拼接：容忍 baseUrl 结尾是否带 `/`。 */
object ByokUrls {
    private fun join(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    fun chatCompletions(baseUrl: String): String = join(baseUrl, "chat/completions")
    fun openAiModels(baseUrl: String): String = join(baseUrl, "models")
    fun anthropicMessages(baseUrl: String): String = join(baseUrl, "v1/messages")
    fun anthropicModels(baseUrl: String): String = join(baseUrl, "v1/models")
}
