package com.myapp.expensetracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs

object SplitShareImageRenderer {
    fun renderToCache(context: Context, summary: SplitShareSummary): android.net.Uri {
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawColor(Color.rgb(250, 250, 240))
        paint.color = Color.rgb(222, 241, 193)
        canvas.drawRoundRect(RectF(54f, 54f, 1026f, 1296f), 42f, 42f, paint)
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(86f, 86f, 994f, 1264f), 32f, 32f, paint)

        val left = 118f
        val right = 962f
        val contentWidth = right - left

        drawWrappedText(canvas, paint, summary.eventName, left, 168f, contentWidth, 42f, Color.rgb(34, 40, 33), true)
        drawWrappedText(canvas, paint, summary.memberName, left, 244f, contentWidth, 62f, Color.rgb(27, 34, 25), true)

        val headline = when {
            summary.netAmount < -0.005 -> "You owe ${rupee(abs(summary.netAmount))}"
            summary.netAmount > 0.005 -> "You get back ${rupee(summary.netAmount)}"
            else -> "Settled up"
        }
        val headlineColor = when {
            summary.netAmount < -0.005 -> Color.rgb(176, 43, 43)
            summary.netAmount > 0.005 -> Color.rgb(51, 126, 68)
            else -> Color.rgb(72, 84, 66)
        }
        var y = drawWrappedText(
            canvas = canvas,
            paint = paint,
            text = headline,
            x = left,
            y = 362f,
            maxWidth = contentWidth,
            size = 82f,
            color = headlineColor,
            bold = true
        ) + 56f

        drawWrappedText(canvas, paint, "Your settlement", left, y, contentWidth, 34f, Color.rgb(82, 96, 74), true)
        y += 56f
        if (summary.involvedSettlements.isEmpty()) {
            y = drawWrappedText(
                canvas,
                paint,
                "No payments needed.",
                left,
                y,
                contentWidth,
                36f,
                Color.rgb(42, 47, 39),
                false
            ) + 14f
        } else {
            summary.involvedSettlements.forEach { settlement ->
                val line = if (settlement.fromMemberId == summary.memberId) {
                    "Pay ${settlement.toMemberName} ${rupee(settlement.amount)}"
                } else {
                    "Collect ${rupee(settlement.amount)} from ${settlement.fromMemberName}"
                }
                y = drawWrappedText(canvas, paint, line, left, y, contentWidth, 36f, Color.rgb(42, 47, 39), false) + 14f
            }
        }

        y += 42f
        drawDivider(canvas, paint, y)
        y += 74f
        drawWrappedText(canvas, paint, "Everyone's balance", left, y, contentWidth, 34f, Color.rgb(82, 96, 74), true)
        y += 58f
        summary.allBalances.forEach { balance ->
            val label = when {
                balance.netAmount < -0.005 -> "owes ${rupee(abs(balance.netAmount))}"
                balance.netAmount > 0.005 -> "gets ${rupee(balance.netAmount)}"
                else -> "settled"
            }
            y = drawWrappedText(
                canvas,
                paint,
                "${balance.memberName}: $label",
                left,
                y,
                contentWidth,
                32f,
                Color.rgb(42, 47, 39),
                false
            ) + 12f
        }

        drawWrappedText(
            canvas,
            paint,
            "Shared via Expense Tracker",
            left,
            1218f,
            contentWidth,
            30f,
            Color.rgb(114, 124, 106),
            false
        )

        val imagesDir = File(context.cacheDir, "shared_images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        val file = File(imagesDir, "split_${summary.memberId}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun drawText(
        canvas: Canvas,
        paint: Paint,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean
    ) {
        paint.color = color
        paint.textSize = size
        paint.typeface =
            if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        canvas.drawText(text.take(34), x, y, paint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        paint: Paint,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        size: Float,
        color: Int,
        bold: Boolean
    ): Float {
        paint.color = color
        paint.textSize = size
        paint.typeface =
            if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT

        val lines = wrapText(text, paint, maxWidth)
        val lineHeight = size * 1.22f
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, x, y + (index * lineHeight), paint)
        }
        return y + (lines.size * lineHeight)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank() || paint.measureText(text) <= maxWidth) return listOf(text)

        val lines = mutableListOf<String>()
        var current = ""
        text.split(Regex("\\s+")).forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) lines += current
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        return lines.ifEmpty { listOf(text) }
    }

    private fun drawDivider(canvas: Canvas, paint: Paint, y: Float) {
        paint.color = Color.rgb(214, 219, 202)
        paint.strokeWidth = 3f
        canvas.drawLine(118f, y, 962f, y, paint)
    }

    private fun rupee(amount: Double): String =
        String.format(Locale.getDefault(), "\u20B9%,.2f", amount)
}
