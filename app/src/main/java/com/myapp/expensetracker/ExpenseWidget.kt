package com.myapp.expensetracker

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.cornerRadius
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.GlanceTheme
import android.content.Intent
import android.content.ComponentName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.glance.LocalContext
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.myapp.expensetracker.worker.WidgetUpdateWorker
import kotlin.math.abs

class ExpenseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        android.util.Log.d("ExpenseWidget", "Providing glance content")
        val db = AppDatabase.getDatabase(context)
        val sharedPrefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        val isMonthlyBudget = sharedPrefs.getBoolean("budget_monthly", true)

        val (startOfMonth, endOfMonth) = if (isMonthlyBudget) {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis

            cal.set(
                java.util.Calendar.DAY_OF_MONTH,
                cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            )
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
            cal.set(java.util.Calendar.MINUTE, 59)
            cal.set(java.util.Calendar.SECOND, 59)
            cal.set(java.util.Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            Pair(start, end)
        } else {
            Pair(0L, Long.MAX_VALUE)
        }

        val totalSpent = if (isMonthlyBudget) {
            db.transactionDao().getTotalSpentBetween(startOfMonth, endOfMonth) ?: 0.0
        } else {
            db.transactionDao().getTotalSpent() ?: 0.0
        }

        val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val budget = db.monthlyBudgetDao().getEffectiveBudget(currentMonthKey)?.amount ?: 0.0
        
        val lastTransaction = if (isMonthlyBudget) {
            db.transactionDao().getLastTransactionBetween(startOfMonth, endOfMonth)
        } else {
            db.transactionDao().getLastTransaction()
        }

        provideContent {
            GlanceTheme {
                WidgetContent(totalSpent, budget, lastTransaction, isMonthlyBudget)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        totalSpent: Double,
        budget: Double,
        lastTransaction: Transaction?,
        isMonthlyBudget: Boolean
    ) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.surface)
                .clickable(
                    actionStartActivity(
                        Intent().setComponent(
                            ComponentName(
                                context,
                                MainActivity::class.java
                            )
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Expense",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.primary
                )
            )

            val formattedSpent = "₹${"%,.0f".format(totalSpent)}"
            Text(
                text = formattedSpent,
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.padding(bottom = 2.dp)
            )

            val isExpense = lastTransaction != null && lastTransaction.amount < 0
            val pillColor =
                if (isExpense) GlanceTheme.colors.errorContainer else GlanceTheme.colors.secondaryContainer
            val pillTextColor =
                if (isExpense) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.onSecondaryContainer

            val trendLabel =
                if (lastTransaction != null || isMonthlyBudget) "Last transaction amount" else "Monthly Budget"
            val trendText = if (lastTransaction != null) {
                "${if (isExpense) "↘" else "↗"}\u00A0₹${"%,.0f".format(abs(lastTransaction.amount))}"
            } else if (isMonthlyBudget) {
                "₹0"
            } else {
                "₹${"%,.0f".format(budget)}"
            }

            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = trendLabel,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    modifier = GlanceModifier.padding(bottom = 2.dp),
                    maxLines = 1
                )

                Box(
                    modifier = GlanceModifier
                        .background(pillColor)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = trendText,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = pillTextColor
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class ExpenseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpenseWidget()
}

suspend fun updateExpenseWidget(context: Context) {
    try {
        ExpenseWidget().updateAll(context)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun enqueueWidgetUpdate(context: Context) {
    val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "widget_update",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}
