package whl.trending.ai.core.platform

/**
 * 受 Cloudflare 人机验证保护的站点。
 *
 * 应用内 WebView 会被 Cloudflare 判定为可疑客户端（UA 带 wv 标记、缺少完整
 * Client Hints 等），challenge 页面陷入死循环无法通过；这类链接改用系统浏览器
 * 环境打开（Android Custom Tabs / iOS SFSafariViewController），真实浏览器
 * 指纹可正常通过校验。
 */
private val CLOUDFLARE_PROTECTED_HOSTS = listOf("producthunt.com")

/** 判断 URL 是否指向受 Cloudflare 保护的站点（含子域名），此类链接不进应用内 WebView。 */
fun isCloudflareProtectedUrl(url: String): Boolean {
    val host = url.substringAfter("://", "")
        .substringBefore('/')
        .substringBefore('?')
        .substringAfter('@')
        .substringBefore(':')
        .lowercase()
    if (host.isEmpty()) return false
    return CLOUDFLARE_PROTECTED_HOSTS.any { host == it || host.endsWith(".$it") }
}
