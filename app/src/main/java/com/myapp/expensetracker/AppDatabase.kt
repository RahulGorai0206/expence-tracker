package com.myapp.expensetracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        Transaction::class,
        MonthlyBudget::class,
        SplitEvent::class,
        SplitMember::class,
        SplitExpense::class,
        SplitShare::class,
        SplitPayment::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun monthlyBudgetDao(): MonthlyBudgetDao
    abstract fun splitDao(): SplitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val databaseScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        /** Migration 7 → 8: Create monthly_budgets table and seed from SharedPreferences. */
        private fun createMigration7to8(context: Context) = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the new table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS monthly_budgets (
                        monthKey TEXT NOT NULL PRIMARY KEY,
                        amount REAL NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Seed from legacy SharedPreferences budget value
                val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                val legacyBudget = prefs.getFloat("budget", 0f).toDouble()
                if (legacyBudget > 0) {
                    val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                    db.execSQL(
                        "INSERT OR IGNORE INTO monthly_budgets (monthKey, amount, createdAt) VALUES (?, ?, ?)",
                        arrayOf(monthKey, legacyBudget, System.currentTimeMillis())
                    )
                }
            }
        }

        private val migration8to9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN tag TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val migration9to10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS split_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        remoteId TEXT,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS split_members (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        remoteId TEXT,
                        eventId INTEGER NOT NULL,
                        displayName TEXT NOT NULL,
                        contactLookupKey TEXT,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(eventId) REFERENCES split_events(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_members_eventId ON split_members(eventId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS split_expenses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        remoteId TEXT,
                        eventId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        description TEXT NOT NULL,
                        paidByMemberId INTEGER NOT NULL,
                        splitMode TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(eventId) REFERENCES split_events(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_expenses_eventId ON split_expenses(eventId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_expenses_paidByMemberId ON split_expenses(paidByMemberId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS split_shares (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        remoteId TEXT,
                        splitExpenseId INTEGER NOT NULL,
                        memberId INTEGER NOT NULL,
                        owedAmount REAL NOT NULL,
                        percentage REAL NOT NULL,
                        FOREIGN KEY(splitExpenseId) REFERENCES split_expenses(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_shares_splitExpenseId ON split_shares(splitExpenseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_shares_memberId ON split_shares(memberId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS split_payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        remoteId TEXT,
                        eventId INTEGER NOT NULL,
                        fromMemberId INTEGER NOT NULL,
                        toMemberId INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(eventId) REFERENCES split_events(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_payments_eventId ON split_payments(eventId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_payments_fromMemberId ON split_payments(fromMemberId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_payments_toMemberId ON split_payments(toMemberId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
                    .addMigrations(createMigration7to8(context), migration8to9, migration9to10)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Reset 'pending' transactions to 'failed' on app start 
                        // so they don't get stuck in a loading state if the app crashed/closed during sync.
                        databaseScope.launch {
                            getDatabase(context).transactionDao().resetPendingStatus()
                        }
                    }
                })
                    .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
