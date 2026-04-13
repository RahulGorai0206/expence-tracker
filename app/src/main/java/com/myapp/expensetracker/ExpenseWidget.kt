package com.myapp.expensetracker

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ExpenseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val sharedPrefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        // Fetch data in the suspend function provideGlance
        val totalBalance = db.transactionDao().getTotalBalance() ?: 0.0
        val budget = sharedPrefs.getFloat("budget", 0f).toDouble()
        val lastTransaction = db.transactionDao().getLastTransaction()

        provideContent {
            WidgetContent(totalBalance, budget, lastTransaction)
        }
    }

    @Composable
    private fun WidgetContent(totalBalance: Double, budget: Double, lastTransaction: Transaction?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.White)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Balance",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorProvider(Color.Gray)
                )
            )
            Text(
                text = "₹ ${"%,.2f".format(totalBalance)}",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color.Black)
                )
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Monthly Budget",
                        style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color.Gray))
                    )
                    Text(
                        text = "₹ ${"%,.0f".format(budget)}",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color.Black)
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            if (lastTransaction != null) {
                Text(
                    text = "Last: ${lastTransaction.sender}",
                    style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color.DarkGray))
                )
                Text(
                    text = "${if (lastTransaction.amount < 0) "-" else "+"} ₹ ${
                        "%,.2f".format(
                            abs(
                                lastTransaction.amount
                            )
                        )
                    }",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(
                            if (lastTransaction.amount < 0) Color(0xFFD32F2F) else Color(
                                0xFF388E3C
                            )
                        )
                    )
                )
            } else {
                Text(
                    text = "No transactions",
                    style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color.Gray))
                )
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
