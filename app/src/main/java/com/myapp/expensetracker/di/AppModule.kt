package com.myapp.expensetracker.di

import com.myapp.expensetracker.AppDatabase
import com.myapp.expensetracker.viewmodel.HomeViewModel
import com.myapp.expensetracker.viewmodel.TransactionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().transactionDao() }

    viewModel { HomeViewModel(get()) }
    viewModel { TransactionViewModel(get()) }
}
