package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun VoiceWaveformVisualizer(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 24
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val width = size.width
        val height = size.height
        val spacing = width / barCount
        val barWidth = spacing * 0.5f

        for (i in 0 until barCount) {
            val x = i * spacing + spacing * 0.25f
            val normX = i.toFloat() / barCount
            val envelope = sin(normX * Math.PI).toFloat().coerceIn(0f, 1f)

            val barHeight = if (isActive) {
                val wave = (sin(phase + i * 0.5) * 0.6 + sin(phase * 1.5 + i * 0.3) * 0.4).toFloat()
                (height * 0.85f * envelope * Math.abs(wave)).coerceAtLeast(6f)
            } else {
                6f
            }

            val y = (height - barHeight) / 2f
            drawRoundRect(
                color = waveColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
