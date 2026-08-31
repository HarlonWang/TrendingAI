package whl.trending.chat.db

import android.content.Context
import androidx.room.Room

private val lock = Any()

@Volatile
private var instance: ChatDatabase? = null

/** 进程级单例；库名与 Room KMP 化之前一致（chat.db），存量数据直接沿用。 */
fun chatDatabase(context: Context): ChatDatabase =
    instance ?: synchronized(lock) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            ChatDatabase::class.java,
            "chat.db",
        ).build().also { instance = it }
    }
