package whl.trending.chat.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.migration.AutoMigrationSpec
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import kotlinx.coroutines.flow.Flow

/**
 * 会话线：一次对话的容器。
 *
 * @param updatedAt 排序键：列表按最近活跃倒序
 */
@Entity(tableName = "chat_threads")
data class ThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 持久化消息。流式过程零写库——user 消息即发即落，assistant 在终局落一次
 * （成功为全文，失败为错误行：content 空、错误详情进 segmentsJson）。
 *
 * @param imagesJson 用户附图的本地路径列表（JSON）；持久化文件在 filesDir/chat_images，
 *   删线程时先删文件再删行（CASCADE 之后路径就找不回了）
 * @param model 应答模型 id（用户在选择器里选的；展示「哪个模型答的」用）
 * @param segmentsJson 富内容信封（JSON，含 v 版本字段）：搜索来源 / research runId / 错误终局
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("threadId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val role: String,
    val content: String,
    val imagesJson: String?,
    val kind: String,
    val model: String?,
    val segmentsJson: String?,
    val createdAt: Long,
)

@Dao
interface ThreadDao {
    @Insert
    suspend fun insert(thread: ThreadEntity): Long

    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE id = :id")
    suspend fun getById(id: Long): ThreadEntity?

    @Query("UPDATE chat_threads SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    /** 会话有新活动时冒泡到列表顶部 */
    @Query("UPDATE chat_threads SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM chat_threads WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY id")
    suspend fun messagesFor(threadId: Long): List<MessageEntity>

    /** 删线程前收集待删的图片文件路径（行删除交给 CASCADE） */
    @Query("SELECT imagesJson FROM chat_messages WHERE threadId = :threadId AND imagesJson IS NOT NULL")
    suspend fun imagesJsonFor(threadId: Long): List<String>

    /** research 占位 → 终局报告（P3：占位行是跨进程恢复轮询的载体）；model 为生成模型留痕 */
    @Query("UPDATE chat_messages SET content = :content, segmentsJson = :segmentsJson, model = :model WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, segmentsJson: String?, model: String?)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 空内容的 research 行（占位或错误终局，runId/error 在 segmentsJson 里，
     * 由 [RoomChatStore.pendingResearch] 解码后过滤出真正待恢复的占位）。
     */
    @Query("SELECT * FROM chat_messages WHERE kind = :kind AND content = ''")
    suspend fun pendingResearchRows(kind: String): List<MessageEntity>
}

/** v1→v2：删除入口上下文机制的遗留列（entryKey/contextJson），Room 按 schemas/1.json 生成拷表迁移 */
@DeleteColumn(tableName = "chat_threads", columnName = "entryKey")
@DeleteColumn(tableName = "chat_threads", columnName = "contextJson")
internal class DropEntryContextColumns : AutoMigrationSpec

@Database(
    entities = [ThreadEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = DropEntryContextColumns::class)],
)
@ConstructedBy(ChatDatabaseConstructor::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun messageDao(): MessageDao
}

/** actual 由 Room KSP 生成（KMP 官方形态）。 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object ChatDatabaseConstructor : RoomDatabaseConstructor<ChatDatabase> {
    override fun initialize(): ChatDatabase
}
