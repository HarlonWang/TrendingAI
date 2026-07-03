package whl.trending.ai.data.local

import java.io.File
import whl.trending.ai.core.platform.AndroidContextHolder

actual fun platformCacheFileStore(): CacheFileStore = AndroidCacheFileStore()

private class AndroidCacheFileStore : CacheFileStore {
    /** 拿不到 context（理论上不会发生）时返回 null，整体降级为无缓存 */
    private fun dir(): File? = AndroidContextHolder.get()?.let {
        File(it.cacheDir, "lastdata").apply { mkdirs() }
    }

    override fun read(name: String): String? {
        val file = dir()?.let { File(it, name) } ?: return null
        return runCatching { if (file.exists()) file.readText() else null }.getOrNull()
    }

    override fun write(name: String, content: String) {
        val d = dir() ?: return
        runCatching {
            // 先写临时文件再 rename：避免写一半被杀进程留下损坏文件
            val tmp = File(d, "$name.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(File(d, name))) {
                File(d, name).writeText(content)
                tmp.delete()
            }
        }
    }

    override fun delete(name: String) {
        dir()?.let { runCatching { File(it, name).delete() } }
    }
}
