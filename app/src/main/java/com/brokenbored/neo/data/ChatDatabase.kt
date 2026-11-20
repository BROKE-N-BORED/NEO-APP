package com.brokenbored.neo.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One chat conversation. History lives only on the phone, in Room. */
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * One message. Roles: "user", "assistant", "terminal" (a command result —
 * rendered as a terminal card, not as chat text).
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class ChatMessage(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long
)

@Dao
interface NeoDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun conversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC")
    fun messages(id: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC")
    suspend fun messagesOnce(id: String): List<ChatMessage>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: String): Conversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation)

    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Query("UPDATE conversations SET updatedAt = :time, title = :title WHERE id = :id")
    suspend fun touch(id: String, time: Long, title: String)

    @Query("UPDATE conversations SET providerId = :providerId, model = :model WHERE id = :id")
    suspend fun setProvider(id: String, providerId: String, model: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)
}

@Database(
    entities = [Conversation::class, ChatMessage::class],
    version = 1,
    exportSchema = false
)
abstract class NeoDatabase : RoomDatabase() {
    abstract fun dao(): NeoDao
}
