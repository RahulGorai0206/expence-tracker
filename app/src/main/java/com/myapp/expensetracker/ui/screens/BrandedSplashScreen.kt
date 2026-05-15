package com.myapp.expensetracker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrandedSplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val backgroundColors = listOf(
        Color(0xFF173861),
        Color(0xFF197160),
        Color(0xFF9A5B00)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(backgroundColors)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF173861)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Expense Tracker",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    fontSize = 34.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Track your expenses smartly",
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // Footer
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            Text(
                text = "OFFLINE • SECURE • PRIVATE",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
