package io.homeassistant.companion.android.database.notification

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

internal const val MAX_NOTIFICATION_HISTORY = 500

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationItem): Long

    /** Inserts a notification and atomically retains only the newest history rows. */
    @Transaction
    suspend fun add(notification: NotificationItem): Long {
        val id = insert(notification)
        pruneToLimit(MAX_NOTIFICATION_HISTORY)
        return id
    }

    @Query(
        """
        DELETE FROM notification_history
        WHERE id NOT IN (
            SELECT id FROM notification_history
            ORDER BY received DESC, id DESC
            LIMIT :limit
        )
        """,
    )
    suspend fun pruneToLimit(limit: Int)

    @Query("SELECT * FROM notification_history WHERE id = :id")
    suspend fun get(id: Int): NotificationItem?

    @Query("SELECT * FROM notification_history ORDER BY received DESC, id DESC")
    suspend fun getAll(): Array<NotificationItem>

    @Query("SELECT * FROM notification_history ORDER BY received DESC, id DESC LIMIT (:amount)")
    suspend fun getLastItems(amount: Int): Array<NotificationItem>

    @Query("DELETE FROM notification_history WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM notification_history")
    suspend fun deleteAll()
}
