package whl.trending.ai.data.local

/**
 * 「上次数据缓存」的底层文件存取抽象：每个 name 对应平台 cache 目录下的一个文件。
 * 抽成接口是为了 commonTest 能注入内存替身；所有实现都必须静默吞掉 IO 异常——
 * 缓存读写失败只能降级为无缓存，绝不能影响正常流程。
 */
interface CacheFileStore {
    fun read(name: String): String?
    fun write(name: String, content: String)
    fun delete(name: String)
}

/** 平台默认实现：Android 用 context.cacheDir，iOS 用 NSCachesDirectory */
expect fun platformCacheFileStore(): CacheFileStore
