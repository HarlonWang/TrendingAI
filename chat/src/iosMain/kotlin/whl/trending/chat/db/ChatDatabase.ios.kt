package whl.trending.chat.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** 进程级单例（lazy 自带线程安全）；落在 Documents 下（随 app 备份），驱动用 bundled SQLite。 */
private val instance: ChatDatabase by lazy {
    Room.databaseBuilder<ChatDatabase>(name = databasePath())
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

fun chatDatabase(): ChatDatabase = instance

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun databasePath(): String {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(documents).path + "/chat.db"
}
