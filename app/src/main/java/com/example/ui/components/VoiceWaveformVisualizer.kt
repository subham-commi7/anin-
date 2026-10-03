package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun VoiceWaveformVisualizer(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 28
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val step = width / (barCount + 1)

        for (i in 0 until barCount) {
            val x = step * (i + 1)
            val normalizedIdx = i.toFloat() / barCount
            val baseAmp = if (isActive) {
                val wave1 = sin(phase + normalizedIdx * 4 * Math.PI).toFloat()
                val wave2 = sin(phase * 1.5f + normalizedIdx * 2 * Math.PI).toFloat()
                (Math.abs(wave1 * 0.6f + wave2 * 0.4f)).coerceIn(0.15f, 0.95f)
            } else {
                0.08f
            }

            val barHeight = height * baseAmp * 0.85f
            val topY = centerY - barHeight / 2f
            val bottomY = centerY + barHeight / 2f

            drawLine(
                color = if (isActive) waveColor else waveColor.copy(alpha = 0.3f),
                start = Offset(x, topY),
                end = Offset(x, bottomY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
