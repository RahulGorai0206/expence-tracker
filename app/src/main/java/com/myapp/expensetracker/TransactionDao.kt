package com.myapp.expensetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getTransactionById(id: Int): Flow<Transaction?>

    @Query("SELECT COUNT(id) FROM transactions WHERE (bodyHash = :bodyHash OR (ABS(date - :date) < 60000 AND ABS(amount - :amount) < 0.001))")
    suspend fun checkDuplicate(date: Long, amount: Double, bodyHash: Int): Int


    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionSync(id: Int): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAndReturnId(transaction: Transaction): Long

    @Query("UPDATE transactions SET remoteId = :remoteId, syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, remoteId: String?, status: String)

    @androidx.room.Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("UPDATE transactions SET syncStatus = 'failed' WHERE syncStatus = 'pending'")
    suspend fun resetPendingStatus()

    @Query("SELECT SUM(amount) FROM transactions")
    suspend fun getTotalBalance(): Double?

    @Query("SELECT SUM(ABS(amount)) FROM transactions WHERE amount < 0")
    suspend fun getTotalSpent(): Double?

    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT 1")
    suspend fun getLastTransaction(): Transaction?
}
