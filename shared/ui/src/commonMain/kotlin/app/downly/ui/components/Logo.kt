package app.downly.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Downly "D" monogram (download arrow cut out of the letter), in the 108 x 108 launcher grid. */
private fun monogramPath(): Path = Path().apply {
    fillType = PathFillType.EvenOdd
        moveTo(39.00f, 32.00f)
        lineTo(49.00f, 32.00f)
        lineTo(50.71f, 32.03f)
        lineTo(52.39f, 32.13f)
        lineTo(54.01f, 32.30f)
        lineTo(55.60f, 32.53f)
        lineTo(57.13f, 32.82f)
        lineTo(58.62f, 33.17f)
        lineTo(60.05f, 33.58f)
        lineTo(61.44f, 34.05f)
        lineTo(62.77f, 34.58f)
        lineTo(64.04f, 35.17f)
        lineTo(65.27f, 35.81f)
        lineTo(66.43f, 36.50f)
        lineTo(67.53f, 37.25f)
        lineTo(68.58f, 38.05f)
        lineTo(69.56f, 38.90f)
        lineTo(70.47f, 39.80f)
        lineTo(71.33f, 40.75f)
        lineTo(72.11f, 41.74f)
        lineTo(72.83f, 42.78f)
        lineTo(73.48f, 43.87f)
        lineTo(74.06f, 44.99f)
        lineTo(74.57f, 46.16f)
        lineTo(75.00f, 47.37f)
        lineTo(75.35f, 48.63f)
        lineTo(75.63f, 49.91f)
        lineTo(75.84f, 51.24f)
        lineTo(75.96f, 52.60f)
        lineTo(76.00f, 54.00f)
        lineTo(75.96f, 55.40f)
        lineTo(75.84f, 56.76f)
        lineTo(75.63f, 58.09f)
        lineTo(75.35f, 59.37f)
        lineTo(75.00f, 60.63f)
        lineTo(74.57f, 61.84f)
        lineTo(74.06f, 63.01f)
        lineTo(73.48f, 64.13f)
        lineTo(72.83f, 65.22f)
        lineTo(72.11f, 66.26f)
        lineTo(71.33f, 67.25f)
        lineTo(70.47f, 68.20f)
        lineTo(69.56f, 69.10f)
        lineTo(68.58f, 69.95f)
        lineTo(67.53f, 70.75f)
        lineTo(66.43f, 71.50f)
        lineTo(65.27f, 72.19f)
        lineTo(64.04f, 72.83f)
        lineTo(62.77f, 73.42f)
        lineTo(61.44f, 73.95f)
        lineTo(60.05f, 74.42f)
        lineTo(58.62f, 74.83f)
        lineTo(57.13f, 75.18f)
        lineTo(55.60f, 75.47f)
        lineTo(54.01f, 75.70f)
        lineTo(52.39f, 75.87f)
        lineTo(50.71f, 75.97f)
        lineTo(39.00f, 75.99f)
        cubicTo(34.33f, 76.00f, 32.00f, 73.67f, 32.00f, 69.00f)
        lineTo(32.00f, 39.00f)
        cubicTo(32.00f, 34.33f, 34.33f, 32.00f, 39.00f, 32.00f)
        close()
        moveTo(53.40f, 40.00f)
        lineTo(57.60f, 40.00f)
        cubicTo(59.20f, 40.00f, 60.00f, 40.80f, 60.00f, 42.40f)
        lineTo(60.00f, 51.00f)
        cubicTo(60.00f, 51.67f, 60.33f, 52.00f, 61.00f, 52.00f)
        lineTo(66.20f, 52.00f)
        cubicTo(67.40f, 52.00f, 67.59f, 52.44f, 66.78f, 53.32f)
        lineTo(57.27f, 63.59f)
        cubicTo(56.09f, 64.86f, 54.91f, 64.86f, 53.73f, 63.59f)
        lineTo(44.22f, 53.32f)
        cubicTo(43.41f, 52.44f, 43.60f, 52.00f, 44.80f, 52.00f)
        lineTo(50.00f, 52.00f)
        cubicTo(50.67f, 52.00f, 51.00f, 51.67f, 51.00f, 51.00f)
        lineTo(51.00f, 42.40f)
        cubicTo(51.00f, 40.80f, 51.80f, 40.00f, 53.40f, 40.00f)
        close()
}

/**
 * Downly's brand mark: the same artwork as the launcher icon, drawn natively so the
 * in-app logo always matches the app icon on Android and Desktop.
 */
@Composable
fun DownlyLogo(modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val mark = monogramPath()
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        // Show the central 68 x 68 window of the 108 grid so the letter fills the tile.
        val window = 68f
        val origin = 20f
        val u = s / window
        val radius = s * 0.33f
        val tile = Path().apply { addRoundRect(RoundRect(0f, 0f, s, s, CornerRadius(radius))) }
        clipPath(tile) {
            withTransform({
                translate(left = -origin * u, top = -origin * u)
                scale(u, u, pivot = Offset.Zero)
            }) {
                val full = Size(130f, 130f)
                val corner = Offset(-10f, -10f)
                // deep navy base
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF0B1626), Color(0xFF10304A)),
                        start = Offset(0f, 0f),
                        end = Offset(108f, 108f),
                    ),
                    topLeft = corner,
                    size = full,
                )
                // soft brand glow behind the letter
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color(0xFF2DD4BF).copy(alpha = 0.30f),
                        0.6f to Color(0xFF38BDF8).copy(alpha = 0.08f),
                        1f to Color.Transparent,
                        center = Offset(54f, 54f),
                        radius = 58f,
                    ),
                    topLeft = corner,
                    size = full,
                )
                // the letter with the arrow cut out
                drawPath(
                    mark,
                    Brush.linearGradient(
                        listOf(Color(0xFF5EEAD4), Color(0xFF38BDF8)),
                        start = Offset(36f, 30f),
                        end = Offset(76f, 78f),
                    ),
                )
                // top highlight
                drawPath(
                    mark,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.40f), Color.Transparent),
                        start = Offset(54f, 32f),
                        end = Offset(54f, 60f),
                    ),
                )
            }
        }
    }
}
