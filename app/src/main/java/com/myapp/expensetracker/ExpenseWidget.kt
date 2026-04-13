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
import androidx.glance.LocalContext
import kotlin.math.abs

class ExpenseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val sharedPrefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        val totalBalance = db.transactionDao().getTotalBalance() ?: 0.0
        val budget = sharedPrefs.getFloat("budget", 0f).toDouble()
        val lastTransaction = db.transactionDao().getLastTransaction()

        provideContent {
            GlanceTheme {
                WidgetContent(totalBalance, budget, lastTransaction)
            }
        }
    }

    @Composable
    private fun WidgetContent(totalBalance: Double, budget: Double, lastTransaction: Transaction?) {
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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Total Expense",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.primary
                )
            )

            val formattedBalance =
                if (totalBalance < 0) "-₹${"%,.0f".format(abs(totalBalance))}" else "₹${
                    "%,.0f".format(totalBalance)
                }"
            Text(
                text = formattedBalance,
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.padding(bottom = 6.dp)
            )

            val isExpense = lastTransaction != null && lastTransaction.amount < 0
            val pillColor =
                if (isExpense) GlanceTheme.colors.errorContainer else GlanceTheme.colors.secondaryContainer
            val pillTextColor =
                if (isExpense) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.onSecondaryContainer

            val trendLabel = if (lastTransaction != null) "Last Transaction" else "Monthly Budget"
            val trendText = if (lastTransaction != null) {
                "${if (isExpense) "↘" else "↗"} ₹${"%,.0f".format(abs(lastTransaction.amount))}"
            } else {
                "₹${"%,.0f".format(budget)}"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.fillMaxWidth()
            ) {
                Text(
                    text = trendLabel,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    modifier = GlanceModifier.padding(end = 8.dp)
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
                        )
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
    ExpenseWidget().updateAll(context)
}
