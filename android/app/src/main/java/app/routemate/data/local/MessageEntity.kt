package app.routemate.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "messages",
    indices = [Index(value = ["rideId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val rideId: String,
    val senderId: String,
    val body: String,
    val createdAt: String,
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE rideId = :rideId ORDER BY createdAt ASC")
    fun observe(rideId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE rideId = :rideId ORDER BY createdAt ASC")
    suspend fun list(rideId: String): List<MessageEntity>

    @Upsert(entity = MessageEntity::class)
    suspend fun upsertAll(rows: List<MessageEntity>)

    @Upsert(entity = MessageEntity::class)
    suspend fun upsert(row: MessageEntity)

    @Query("DELETE FROM messages WHERE rideId = :rideId")
    suspend fun clear(rideId: String)
}
