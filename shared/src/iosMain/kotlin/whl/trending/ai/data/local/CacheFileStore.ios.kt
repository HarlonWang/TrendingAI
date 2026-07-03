package whl.trending.ai.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

actual fun platformCacheFileStore(): CacheFileStore = IosCacheFileStore()

@OptIn(ExperimentalForeignApi::class)
private class IosCacheFileStore : CacheFileStore {
    private fun filePath(name: String): String? {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: return null
        val dir = "$caches/lastdata"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return "$dir/$name"
    }

    override fun read(name: String): String? {
        val path = filePath(name) ?: return null
        return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
    }

    override fun write(name: String, content: String) {
        val path = filePath(name) ?: return
        // atomically = true：NSString 自带先写临时文件再替换的原子语义
        NSString.create(string = content)
            .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun delete(name: String) {
        val path = filePath(name) ?: return
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
}
