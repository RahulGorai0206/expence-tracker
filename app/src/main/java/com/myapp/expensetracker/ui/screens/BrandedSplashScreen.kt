package com.myapp.expensetracker.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

import androidx.compose.ui.res.painterResource
import com.myapp.expensetracker.R

@Composable
fun BrandedSplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    // Pulse animation for the central icon
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Floating animation offset (sin wave)
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "float"
    )

    // Colors matching the screenshot
    val bgColor = Color(0xFF08060F) // Deep dark purple/black
    val gridColor = Color.White.copy(alpha = 0.03f)
    val glowColor = Color(0xFF4A258D).copy(alpha = 0.35f)
    val iconBgColor = Color(0xFFEADDFF)
    val iconColor = Color(0xFF21005D)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Draw Grid Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 36.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += gridSpacing
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += gridSpacing
            }
        }

        // Central glowing blur
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp)
                .size(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent)
                    )
                )
        )

        // Floating Pills (Positions tuned for general phone aspect ratios)
        FloatingPill(
            category = "Bills",
            amount = "₹19,460",
            dotColor = Color(0xFF4ADE80), // Green
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    x = (-90).dp,
                    y = (-230).dp + (sin(floatAnim) * 8).dp
                )
        )

        FloatingPill(
            category = "Transport",
            amount = "₹1,591",
            dotColor = Color(0xFF60A5FA), // Blue
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    x = 90.dp,
                    y = (-130).dp + (sin(floatAnim + 2f) * 8).dp
                )
        )

        FloatingPill(
            category = "Groceries",
            amount = "₹1,952",
            dotColor = Color(0xFFFBBF24), // Orange/Yellow
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    x = (-80).dp,
                    y = 150.dp + (sin(floatAnim + 1f) * 8).dp
                )
        )

        FloatingPill(
            category = "Shopping",
            amount = "₹912",
            dotColor = Color(0xFFF87171), // Red/Pink
            modifier = Modifier
                .align(Alignment.Center)
                .offset(
                    x = 80.dp,
                    y = 240.dp + (sin(floatAnim + 3f) * 8).dp
                )
        )

        // Center Content
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .size(118.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
                shape = RoundedCornerShape(38.dp),
                color = iconBgColor,
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "App Icon",
                        modifier = Modifier.size(72.dp),
                        tint = iconColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Ledger",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    fontSize = 38.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tracking your financial journey",
                style = MaterialTheme.typography.bodyLarge.copy(
                    letterSpacing = 0.2.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FloatingPill(
    category: String,
    amount: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = category,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = amount,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
