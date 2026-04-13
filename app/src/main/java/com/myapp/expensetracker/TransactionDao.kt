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

    @Query("SELECT COUNT(id) FROM transactions WHERE date = :date AND amount = :amount")
    suspend fun checkDuplicate(date: Long, amount: Double): Int

    @Query("SELECT COUNT(id) FROM transactions WHERE amount = :amount AND body = :body")
    suspend fun checkDuplicateByBody(amount: Double, body: String): Int

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
}
