package com.myapp.expensetracker

import android.app.Application
import com.myapp.expensetracker.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ExpenseApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ExpenseApplication)
            modules(appModule)
        }
    }
}
