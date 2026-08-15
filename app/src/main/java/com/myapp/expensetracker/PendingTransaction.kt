package com.myapp.expensetracker

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A transaction that has been detected and shown to the user, but not yet
 * accepted, denied or timed out.
 *
 * Before this table existed the only copy of an awaiting-approval transaction
 * lived in PendingIntent extras plus a 30s alarm — a reboot, force-stop or
 * battery-optimizer kill inside that window destroyed it silently, and the
 * originating SMS had already been consumed. Rows here survive all of that and
 * are reconciled on next launch.
 */
@Entity(tableName = "pending_transactions")
data class PendingTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationId: Int,
    val sender: String,
    val amount: Double,
    val date: Long,
    val body: String,
    val bodyHash: Int,
    val category: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toTransaction(status: String): Transaction = Transaction(
        sender = sender,
        amount = amount,
        date = date,
        body = body,
        bodyHash = bodyHash,
        category = category,
        status = status,
        type = "automated",
        latitude = latitude,
        longitude = longitude,
        syncStatus = "pending"
    )
}

@Dao
interface PendingTransactionDao {

    @Insert
    suspend fun insert(pending: PendingTransaction): Long

    @Query("SELECT * FROM pending_transactions WHERE id = :id")
    suspend fun getById(id: Long): PendingTransaction?

    @Query("SELECT * FROM pending_transactions ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingTransaction>

    @Query("DELETE FROM pending_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Guards against two detection layers queueing the same message. */
    @Query(
        "SELECT COUNT(id) FROM pending_transactions " +
                "WHERE bodyHash = :bodyHash AND ABS(date - :date) < :windowMs"
    )
    suspend fun countMatching(date: Long, bodyHash: Int, windowMs: Long): Int
}
