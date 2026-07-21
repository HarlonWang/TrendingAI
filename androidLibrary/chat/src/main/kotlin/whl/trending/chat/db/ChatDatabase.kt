package whl.trending.chat.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 会话线：一次对话的容器。
 *
 * @param entryKey 入口键（`repo:{externalId}` / `general`）——「同一入口再次进入恢复最近会话」
 *   按此列查询（延续原 sessionKey 的体验，但跨进程存活）
 * @param contextJson 进入时的 [whl.trending.ai.chat.ChatContext] 序列化（通用入口为 null）；
 *   恢复历史会话后「一键解读」chip 与服务端 context 注入都依赖它——不存等于恢复的会话丢了灵魂
 * @param updatedAt 排序键：列表按最近活跃倒序
 */
@Entity(tableName = "chat_threads", indices = [Index("entryKey")])
data class ThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val entryKey: String,
    val contextJson: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 持久化消息。error 消息不落库（失败是瞬态 UI 状态，可重试；重启后无意义），
 * 流式过程零写库——user 消息即发即落，assistant 仅在成功终局落一次。
 *
 * @param imagesJson 用户附图的本地路径列表（JSON）；持久化文件在 filesDir/chat_images，
 *   删线程时先删文件再删行（CASCADE 之后路径就找不回了）
 * @param model 应答模型 id（用户在选择器里选的；展示「哪个模型答的」用）
 * @param segmentsJson P2 预留：富内容时间线信封（JSON，含 v 版本字段），本期恒 null
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

    /** 入口恢复语义：该入口最近活跃的会话线 */
    @Query("SELECT * FROM chat_threads WHERE entryKey = :entryKey ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestByEntry(entryKey: String): ThreadEntity?

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

    /** research 占位 → 终局报告（P3：占位行是跨进程恢复轮询的载体） */
    @Query("UPDATE chat_messages SET content = :content, segmentsJson = :segmentsJson WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, segmentsJson: String?)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [ThreadEntity::class, MessageEntity::class], version = 1, exportSchema = true)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var instance: ChatDatabase? = null

        fun get(context: Context): ChatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat.db",
                ).build().also { instance = it }
            }
    }
}
