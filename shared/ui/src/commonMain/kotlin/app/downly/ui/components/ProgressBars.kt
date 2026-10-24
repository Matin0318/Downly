package app.downly.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.downly.ui.theme.AccentFrom
import app.downly.ui.theme.AccentTo

/**
 * Standard rounded progress bar: a soft track, a gradient fill with a light gloss, a smooth animation
 * when the value changes and a small minimum width so even 1% is visible. It fills from the start edge,
 * so it grows from the right in Persian. Pass `null` for an endless "working" animation.
 */
@Composable
fun DownlyProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    from: Color = AccentFrom,
    to: Color = AccentTo,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val animated by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "progress",
    )
    val phase = if (progress == null) {
        val transition = rememberInfiniteTransition(label = "working")
        val value by transition.animateFloat(
            initialValue = -0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
            label = "phase",
        )
        value
    } else {
        0f
    }

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)

        val rtl = layoutDirection == LayoutDirection.Rtl
        val width: Float
        val left: Float
        if (progress == null) {
            width = size.width * 0.4f
            left = size.width * phase
        } else {
            if (animated <= 0f) return@Canvas
            width = maxOf(size.height, size.width * animated)
            left = if (rtl) size.width - width else 0f
        }
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(from, to), startX = left, endX = left + width),
            topLeft = Offset(left, 0f),
            size = Size(width, size.height),
            cornerRadius = radius,
        )
        // Light gloss on the upper half of the fill.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset(left + size.height * 0.3f, size.height * 0.14f),
            size = Size((width - size.height * 0.6f).coerceAtLeast(0f), size.height * 0.34f),
            cornerRadius = CornerRadius(size.height * 0.17f, size.height * 0.17f),
        )
    }
}
