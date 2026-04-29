package com.myapp.expensetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myapp.expensetracker.AppDatabase
import com.myapp.expensetracker.GoogleSheetsLogger

class SheetsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val localId = inputData.getLong("TRANSACTION_ID", -1L)
        if (localId == -1L) {
            return Result.failure()
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.transactionDao()

        val transaction = dao.getTransactionSync(localId.toInt())
            ?: return Result.failure()

        // Check if already synced
        if (!transaction.remoteId.isNullOrBlank() && transaction.syncStatus == "synced") {
            return Result.success()
        }

        // Check if it's a deletion or creation
        return if (transaction.status == "deleted") {
            // It's a deletion sync
            try {
                if (GoogleSheetsLogger.delete(transaction)) {
                    dao.updateSyncStatus(localId.toInt(), transaction.remoteId, "synced")
                    Result.success()
                } else {
                    dao.updateSyncStatus(localId.toInt(), transaction.remoteId, "failed")
                    Result.retry()
                }
            } catch (e: Exception) {
                dao.updateSyncStatus(localId.toInt(), transaction.remoteId, "failed")
                Result.retry()
            }
        } else {
            // It's a creation/update sync
            try {
                val remoteId = GoogleSheetsLogger.log(transaction)
                if (remoteId != null) {
                    dao.updateSyncStatus(localId.toInt(), remoteId, "synced")
                    Result.success()
                } else {
                    dao.updateSyncStatus(localId.toInt(), null, "failed")
                    Result.retry()
                }
            } catch (e: Exception) {
                dao.updateSyncStatus(localId.toInt(), null, "failed")
                Result.retry()
            }
        }
    }
}
