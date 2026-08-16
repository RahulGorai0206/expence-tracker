package com.myapp.expensetracker.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.expensetracker.GoogleSheetsLogger
import com.myapp.expensetracker.Transaction
import com.myapp.expensetracker.TransactionDao
import com.myapp.expensetracker.enqueueWidgetUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TransactionViewModel(private val dao: TransactionDao) : ViewModel() {
    private val _hasLoaded = MutableStateFlow(false)
    /** False until Room delivers its first emission — lets the UI show a
     *  skeleton instead of an empty state that isn't true yet. */
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = dao.getAllTransactions()
        .onEach { _hasLoaded.value = true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val deletedSyncTransactions: StateFlow<List<Transaction>> =
        dao.getDeletedPendingOrFailedTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun softDelete(
        context: Context,
        transactions: Collection<Transaction>,
        onDeleted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            transactions.forEach { transaction ->
                dao.softDelete(transaction.id)
                val deletedTransaction = dao.getTransactionSync(transaction.id) ?: transaction.copy(
                    status = "deleted",
                    syncStatus = "pending"
                )

                if (GoogleSheetsLogger.isConfigured()) {
                    GoogleSheetsLogger.logAsync(
                        context,
                        deletedTransaction,
                        transaction.id.toLong()
                    )
                } else {
                    dao.updateSyncStatus(transaction.id, transaction.remoteId, "synced")
                }
            }
            enqueueWidgetUpdate(context)
            onDeleted()
        }
    }

    fun retryDeleteSync(context: android.content.Context, transactions: Collection<Transaction>) {
        viewModelScope.launch {
            transactions.forEach { transaction ->
                if (GoogleSheetsLogger.isConfigured()) {
                    GoogleSheetsLogger.logAsync(context, transaction, transaction.id.toLong())
                } else {
                    dao.updateSyncStatus(transaction.id, transaction.remoteId, "synced")
                }
            }
        }
    }
}
