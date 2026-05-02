package com.myapp.expensetracker

import android.app.Application
import com.myapp.expensetracker.di.appModule
import com.myapp.expensetracker.worker.UpdateCheckWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ExpenseApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ExpenseApplication)
            modules(appModule)
        }

        // Schedule daily update check at 6 PM IST
        UpdateCheckWorker.scheduleNextCheck(this)
    }
}
