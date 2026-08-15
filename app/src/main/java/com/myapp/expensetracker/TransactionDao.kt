package com.myapp.expensetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE status != 'deleted' ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getTransactionById(id: Int): Flow<Transaction?>

    /**
     * Every row, tombstones included — backups are lossless snapshots, not
     * reports, so soft-deleted rows travel with them.
     */
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllTransactionsList(): List<Transaction>

    @Query("SELECT COUNT(id) FROM transactions WHERE (bodyHash = :bodyHash OR (ABS(date - :date) < 60000 AND ABS(amount - :amount) < 0.001))")
    suspend fun checkDuplicate(date: Long, amount: Double, bodyHash: Int): Int

    @Query("SELECT * FROM transactions WHERE bodyHash = :bodyHash OR (ABS(date - :date) < 60000 AND ABS(amount - :amount) < 0.001) LIMIT 1")
    suspend fun findExistingTransaction(date: Long, amount: Double, bodyHash: Int): Transaction?


    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionSync(id: Int): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndReturnId(transaction: Transaction): Long

    @Query("UPDATE transactions SET remoteId = :remoteId, syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, remoteId: String?, status: String)

    @Query("UPDATE transactions SET status = 'deleted', syncStatus = 'pending' WHERE id = :id")
    suspend fun softDelete(id: Int)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("UPDATE transactions SET syncStatus = 'failed' WHERE syncStatus = 'pending'")
    suspend fun resetPendingStatus()

    @Query("SELECT SUM(amount) FROM transactions WHERE status != 'deleted'")
    suspend fun getTotalBalance(): Double?

    @Query("SELECT SUM(ABS(amount)) FROM transactions WHERE amount < 0 AND status != 'deleted'")
    suspend fun getTotalSpent(): Double?

    @Query("SELECT SUM(ABS(amount)) FROM transactions WHERE amount < 0 AND status != 'deleted' AND date >= :startDate AND date <= :endDate")
    suspend fun getTotalSpentBetween(startDate: Long, endDate: Long): Double?

    @Query("SELECT * FROM transactions WHERE status != 'deleted' ORDER BY date DESC LIMIT 1")
    suspend fun getLastTransaction(): Transaction?

    @Query("SELECT * FROM transactions WHERE status != 'deleted' AND date >= :startDate AND date <= :endDate ORDER BY date DESC LIMIT 1")
    suspend fun getLastTransactionBetween(startDate: Long, endDate: Long): Transaction?

    @Query("SELECT COUNT(*) FROM transactions WHERE status != 'deleted'")
    suspend fun getActiveTransactionCount(): Int

    @Query("SELECT * FROM transactions WHERE syncStatus IN ('pending', 'failed') AND id != :excludeId")
    suspend fun getPendingOrFailedTransactions(excludeId: Int): List<Transaction>

    @Query("SELECT * FROM transactions WHERE syncStatus IN ('pending', 'failed')")
    suspend fun getAllTransactionsNeedingSync(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE status = 'deleted' AND syncStatus IN ('pending', 'failed') ORDER BY date DESC")
    fun getDeletedPendingOrFailedTransactions(): Flow<List<Transaction>>

    // ── Analytics queries ──────────────────────────────────────────────

    @Query("SELECT * FROM transactions WHERE status != 'deleted' AND date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getTransactionsInRange(startDate: Long, endDate: Long): Flow<List<Transaction>>

    @Query("SELECT SUM(ABS(amount)) FROM transactions WHERE amount < 0 AND status != 'deleted' AND date >= :startDate AND date <= :endDate")
    fun getTotalSpentInRange(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM transactions WHERE amount < 0 AND status != 'deleted' AND date >= :startDate AND date <= :endDate")
    fun getTransactionCountInRange(startDate: Long, endDate: Long): Flow<Int>

    @Query(
        """
        SELECT strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') AS monthLabel,
               SUM(ABS(amount)) AS total
        FROM transactions
        WHERE amount < 0 AND status != 'deleted' AND date >= :startDate AND date <= :endDate
        GROUP BY monthLabel
        ORDER BY monthLabel ASC
    """
    )
    fun getMonthlySpending(startDate: Long, endDate: Long): Flow<List<MonthlySpending>>

    @Query(
        """
        SELECT category,
               SUM(ABS(amount)) AS total
        FROM transactions
        WHERE amount < 0 AND status != 'deleted' AND date >= :startDate AND date <= :endDate
        GROUP BY category
        ORDER BY total DESC
    """
    )
    fun getCategorySpending(startDate: Long, endDate: Long): Flow<List<CategorySpending>>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', date / 1000, 'unixepoch', 'localtime') AS dayLabel,
               SUM(ABS(amount)) AS total
        FROM transactions
        WHERE amount < 0 AND status != 'deleted' AND date >= :startDate AND date <= :endDate
        GROUP BY dayLabel
        ORDER BY dayLabel ASC
    """
    )
    fun getDailySpending(startDate: Long, endDate: Long): Flow<List<DailySpending>>

    @Query(
        """
        SELECT CASE WHEN tag IS NULL OR TRIM(tag) = '' THEN 'Untagged' ELSE tag END AS tagLabel,
               SUM(ABS(amount)) AS total
        FROM transactions
        WHERE amount < 0 AND status != 'deleted' AND date >= :startDate AND date <= :endDate
        GROUP BY tagLabel
        ORDER BY total DESC
    """
    )
    fun getTagSpending(startDate: Long, endDate: Long): Flow<List<TagSpending>>
}

data class MonthlySpending(val monthLabel: String, val total: Double)
data class CategorySpending(val category: String, val total: Double)
data class DailySpending(val dayLabel: String, val total: Double)
data class TagSpending(val tagLabel: String, val total: Double)
