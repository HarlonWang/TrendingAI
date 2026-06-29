package whl.trending.chat.engine.byok

/** BYOK 端点 URL 拼接：归一化 baseUrl（去空白/补协议）+ 容忍结尾是否带 `/`。 */
object ByokUrls {
    /**
     * 归一化 baseUrl：去首尾空白；缺 `http(s)://` 协议时补 `https://`，
     * 避免用户漏填协议导致 key 走明文（`http://` 远端在 targetSdk 36 会被系统拦成网络错误，
     * 这里主动补 https 兜住"漏填协议"这一最常见情形）。本机 `http://localhost`（如 Ollama）显式带协议则原样保留。
     */
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        val withScheme = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return withScheme.trimEnd('/')
    }

    private fun join(baseUrl: String, path: String): String =
        normalizeBaseUrl(baseUrl) + "/" + path.trimStart('/')

    fun chatCompletions(baseUrl: String): String = join(baseUrl, "chat/completions")
    fun openAiModels(baseUrl: String): String = join(baseUrl, "models")
    fun anthropicMessages(baseUrl: String): String = join(baseUrl, "v1/messages")
    fun anthropicModels(baseUrl: String): String = join(baseUrl, "v1/models")
}
