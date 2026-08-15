package com.myapp.expensetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private data class Confetto(
    val angleRad: Float,
    val reach: Float,
    val width: Float,
    val height: Float,
    val color: Color,
    val spins: Float,
    val startAt: Float
)

private val confettiPalette = listOf(
    Color(0xFF4CAF50),
    Color(0xFF00BCD4),
    Color(0xFFFFC107),
    Color(0xFFE91E63),
    Color(0xFF7C4DFF),
    Color(0xFFFF7043)
)

/**
 * Full-screen send-off shown when a restored backup already contains everything
 * setup would have asked for. Blocks input and calls [onFinished] once it ends.
 */
@Composable
fun SetupCelebration(
    title: String = "You're all set!",
    subtitle: String = "Your data is back. Welcome to Expense Tracker.",
    onFinished: () -> Unit
) {
    val density = LocalDensity.current
    val unit = remember(density) { with(density) { 1.dp.toPx() } }

    val confetti = remember {
        List(72) { index ->
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            Confetto(
                angleRad = angle,
                reach = 0.35f + Random.nextFloat() * 0.65f,
                width = (5f + Random.nextFloat() * 7f) * unit,
                height = (9f + Random.nextFloat() * 12f) * unit,
                color = confettiPalette[index % confettiPalette.size],
                spins = 1f + Random.nextFloat() * 3f,
                startAt = Random.nextFloat() * 0.18f
            )
        }
    }

    val burst = remember { Animatable(0f) }
    val checkScale = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val scrimAlpha = remember { Animatable(0f) }

    val glow = rememberInfiniteTransition(label = "CelebrationGlow")
    val glowScale by glow.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowScale"
    )

    LaunchedEffect(Unit) {
        launch { scrimAlpha.animateTo(1f, tween(280)) }
        launch { burst.animateTo(1f, tween(1700, easing = LinearOutSlowInEasing)) }
        launch {
            delay(90)
            checkScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            delay(420)
            textAlpha.animateTo(1f, tween(520))
        }
        delay(2700)
        onFinished()
    }

    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(scrimAlpha.value)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            // Swallow taps so nothing underneath reacts while this plays.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxReach = size.minDimension * 0.62f
            val t = burst.value

            // Shockwave rings
            repeat(3) { ring ->
                val ringT = ((t * 1.35f) - ring * 0.13f).coerceIn(0f, 1f)
                if (ringT > 0f && ringT < 1f) {
                    drawCircle(
                        color = primary,
                        radius = maxReach * ringT,
                        center = center,
                        alpha = (1f - ringT) * 0.45f,
                        style = Stroke(width = (1f - ringT) * 8f * unit + unit)
                    )
                }
            }

            // Confetti
            confetti.forEach { piece ->
                val local = ((t - piece.startAt) / (1f - piece.startAt)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach

                val eased = 1f - (1f - local).pow(3f)
                val radius = maxReach * piece.reach * eased
                val gravity = 260f * unit * local * local
                val position = Offset(
                    x = center.x + cos(piece.angleRad) * radius,
                    y = center.y + sin(piece.angleRad) * radius + gravity
                )
                val alpha = if (local < 0.62f) 1f else ((1f - local) / 0.38f).coerceIn(0f, 1f)

                withTransform({
                    rotate(degrees = piece.spins * 360f * local, pivot = position)
                }) {
                    drawRect(
                        color = piece.color,
                        topLeft = Offset(
                            position.x - piece.width / 2f,
                            position.y - piece.height / 2f
                        ),
                        size = Size(piece.width, piece.height),
                        alpha = alpha
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(checkScale.value * glowScale),
                    shape = CircleShape,
                    color = primary.copy(alpha = 0.14f)
                ) {}
                Surface(
                    modifier = Modifier
                        .size(112.dp)
                        .scale(checkScale.value),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = primary
                        )
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}
