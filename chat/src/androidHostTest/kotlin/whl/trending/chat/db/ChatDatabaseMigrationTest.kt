package whl.trending.chat.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v1→v2 自动迁移（删 entryKey/contextJson 列）：按 schemas/1.json 的 DDL 手工造一个
 * 真实形状的存量库——含 `repo:*` 老行、contextJson、已退役的 DETAIL_SUMMARY 消息——
 * 用 Room 打开即触发迁移，数据必须原样活下来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatDatabaseMigrationTest {

    @Test
    fun `v1 存量库打开即迁移：遗留列删除，行数据与级联外键保留`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v1.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            raw.execSQL(
                "CREATE TABLE `chat_threads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `entryKey` TEXT NOT NULL, `contextJson` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
            )
            raw.execSQL("CREATE INDEX `index_chat_threads_entryKey` ON `chat_threads` (`entryKey`)")
            raw.execSQL(
                "CREATE TABLE `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`threadId` INTEGER NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                    "`imagesJson` TEXT, `kind` TEXT NOT NULL, `model` TEXT, `segmentsJson` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, FOREIGN KEY(`threadId`) REFERENCES `chat_threads`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            raw.execSQL("CREATE INDEX `index_chat_messages_threadId` ON `chat_messages` (`threadId`)")
            raw.execSQL(
                "INSERT INTO chat_threads (id,title,entryKey,contextJson,createdAt,updatedAt) " +
                    """VALUES (1,'octo/demo','repo:octo/demo','{"title":"octo/demo"}',100,200)""",
            )
            raw.execSQL(
                "INSERT INTO chat_messages (id,threadId,role,content,kind,createdAt) " +
                    "VALUES (1,1,'user','解读一下','DETAIL_SUMMARY',150)",
            )
            raw.execSQL(
                "INSERT INTO chat_messages (id,threadId,role,content,kind,createdAt) " +
                    "VALUES (2,1,'assistant','旧解读全文','DETAIL_SUMMARY',160)",
            )
            raw.execSQL("PRAGMA user_version = 1")
        }

        val db = Room.databaseBuilder(context, ChatDatabase::class.java, name)
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .allowMainThreadQueries()
            .build()
        try {
            val threads = db.threadDao().observeAll().first()
            assertEquals(listOf("octo/demo"), threads.map { it.title })
            assertEquals(
                listOf("解读一下", "旧解读全文"),
                db.messageDao().messagesFor(1).map { it.content },
            )
            // 级联外键在拷表后仍生效
            db.threadDao().delete(1)
            assertTrue(db.messageDao().messagesFor(1).isEmpty())
        } finally {
            db.close()
        }

        // 列确实删了（拷表迁移的产物）
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            fun columnsOf(table: String) = raw.rawQuery("PRAGMA table_info($table)", null).use { c ->
                generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
            }
            assertEquals(listOf("id", "title", "createdAt", "updatedAt"), columnsOf("chat_threads"))
            assertEquals(
                listOf("id", "threadId", "role", "content", "imagesJson", "model", "segmentsJson", "createdAt"),
                columnsOf("chat_messages"),
            )
        }
        file.delete()
    }
}
