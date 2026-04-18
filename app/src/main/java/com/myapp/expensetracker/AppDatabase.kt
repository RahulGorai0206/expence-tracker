package com.myapp.expensetracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(entities = [Transaction::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val databaseScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Reset 'pending' transactions to 'failed' on app start 
                        // so they don't get stuck in a loading state if the app crashed/closed during sync.
                        databaseScope.launch {
                            getDatabase(context).transactionDao().resetPendingStatus()
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
