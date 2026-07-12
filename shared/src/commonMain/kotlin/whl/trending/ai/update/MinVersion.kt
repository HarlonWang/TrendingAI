package whl.trending.ai.update

/**
 * 服务端下发 min_version 的强制更新判定。
 *
 * 只比较 `-`/`+` 前的数值段（缺段补 0）：本地 git describe 构建（0.14.0-5-gabc）
 * 与 prerelease（0.15.0-beta）都按其数值段参与比较。
 * 任一侧解析失败一律不拦截——getAppVersion() 的兜底值或服务端误配置不能把用户锁死。
 */
fun isVersionBlocked(current: String, minVersion: String?): Boolean {
    val min = parseVersionCore(minVersion ?: return false) ?: return false
    val cur = parseVersionCore(current) ?: return false
    val size = maxOf(cur.size, min.size)
    for (i in 0 until size) {
        val c = cur.getOrElse(i) { 0 }
        val m = min.getOrElse(i) { 0 }
        if (c != m) return c < m
    }
    return false
}

/** 取 `-`/`+` 前的数值段；空串或任一段非纯数字返回 null */
private fun parseVersionCore(version: String): List<Int>? {
    val core = version.trim().substringBefore('-').substringBefore('+')
    if (core.isEmpty()) return null
    return core.split('.').map { segment ->
        segment.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    }
}
