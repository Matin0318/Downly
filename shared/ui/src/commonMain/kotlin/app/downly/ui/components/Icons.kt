package app.downly.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class DIcon {
    Download, Pause, Play, Plus, Close, Check, Trash, Share, Copy, Search, Settings, Retry, Open, Back, Info, Paste,
    Stack, Bolt, CheckCircle, Alert, Video, Audio, Image, Doc, Archive, Package, Disk, Edit, Sort, Link, Gauge, Queue, Folder, Globe, Clock,
}

/**
 * Downly's own duotone icon set: a rounded outline plus a soft tinted fill.
 * Self-drawn (no extra dependency), identical on Android and Desktop.
 */
@Composable
fun DownlyIcon(
    icon: DIcon,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 24.dp,
) {
    val flip = icon == DIcon.Back && LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier = modifier
            .size(iconSize)
            .then(if (flip) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
    ) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun pt(x: Float, y: Float) = Offset(x * u, y * u)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, pt(x1, y1), pt(x2, y2), strokeWidth = 1.9f * u, cap = StrokeCap.Round)
        }

        fun buildPath(close: Boolean, v: FloatArray): Path {
            val path = Path()
            path.moveTo(v[0] * u, v[1] * u)
            var i = 2
            while (i < v.size) {
                path.lineTo(v[i] * u, v[i + 1] * u)
                i += 2
            }
            if (close) path.close()
            return path
        }

        /** Open polyline (stroke only). */
        fun poly(vararg v: Float) {
            drawPath(buildPath(false, v), tint, style = stroke)
        }

        /** Closed polygon with a soft fill. */
        fun shape(fill: Float, vararg v: Float) {
            val path = buildPath(true, v)
            if (fill > 0f) drawPath(path, tint.copy(alpha = fill))
            drawPath(path, tint, style = stroke)
        }

        /** Closed, solid polygon (rounded by a thin stroke). */
        fun solid(vararg v: Float) {
            val path = buildPath(true, v)
            drawPath(path, tint)
            drawPath(path, tint, style = Stroke(width = 1.4f * u, join = StrokeJoin.Round))
        }

        fun disc(cx: Float, cy: Float, r: Float, fill: Float) {
            if (fill > 0f) drawCircle(tint.copy(alpha = fill), radius = r * u, center = pt(cx, cy))
            drawCircle(tint, radius = r * u, center = pt(cx, cy), style = stroke)
        }

        fun dot(cx: Float, cy: Float, r: Float) {
            drawCircle(tint, radius = r * u, center = pt(cx, cy))
        }

        fun panel(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Float) {
            if (fill > 0f) {
                drawRoundRect(tint.copy(alpha = fill), pt(x, y), Size(w * u, h * u), CornerRadius(r * u))
            }
            drawRoundRect(tint, pt(x, y), Size(w * u, h * u), CornerRadius(r * u), style = stroke)
        }

        fun block(x: Float, y: Float, w: Float, h: Float, r: Float) {
            drawRoundRect(tint, pt(x, y), Size(w * u, h * u), CornerRadius(r * u))
        }

        when (icon) {
            DIcon.Download -> {
                panel(4f, 16f, 16f, 4.5f, 2.25f, 0.28f)
                line(12f, 3.5f, 12f, 14f)
                poly(7.5f, 10f, 12f, 14.5f, 16.5f, 10f)
            }
            DIcon.Pause -> {
                block(6.5f, 5f, 4f, 14f, 1.4f)
                block(13.5f, 5f, 4f, 14f, 1.4f)
            }
            DIcon.Play -> solid(8f, 5.2f, 19f, 12f, 8f, 18.8f)
            DIcon.Plus -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }
            DIcon.Close -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            DIcon.Check -> poly(5f, 12.5f, 10f, 17.5f, 19f, 7f)
            DIcon.Trash -> {
                shape(0.22f, 6.5f, 8f, 7.5f, 20f, 16.5f, 20f, 17.5f, 8f)
                line(4f, 7f, 20f, 7f)
                poly(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
                line(10f, 11f, 10f, 16f)
                line(14f, 11f, 14f, 16f)
            }
            DIcon.Share -> {
                line(8.3f, 10.8f, 15.7f, 6.7f)
                line(8.3f, 13.2f, 15.7f, 17.3f)
                disc(18f, 5.5f, 2.8f, 0.3f)
                disc(6f, 12f, 2.8f, 0.3f)
                disc(18f, 18.5f, 2.8f, 0.3f)
            }
            DIcon.Copy -> {
                panel(9f, 9f, 11f, 11f, 2.5f, 0.28f)
                panel(4f, 4f, 11f, 11f, 2.5f, 0f)
            }
            DIcon.Search -> {
                disc(11f, 11f, 6.5f, 0.2f)
                line(16f, 16f, 20.5f, 20.5f)
            }
            DIcon.Settings -> {
                line(4f, 7f, 20f, 7f)
                line(4f, 12f, 20f, 12f)
                line(4f, 17f, 20f, 17f)
                disc(9f, 7f, 2.4f, 0.9f)
                disc(15f, 12f, 2.4f, 0.9f)
                disc(8f, 17f, 2.4f, 0.9f)
            }
            DIcon.Retry -> {
                drawArc(
                    color = tint,
                    startAngle = -50f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = pt(5f, 5f),
                    size = Size(14f * u, 14f * u),
                    style = stroke,
                )
                poly(16.5f, 3.5f, 16.5f, 7.5f, 12.5f, 7.5f)
            }
            DIcon.Open -> {
                // Document page with a folded corner and an arrow entering it ("open file").
                shape(0.22f, 5f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 5f, 21f)
                poly(14f, 3f, 14f, 8f, 19f, 8f)
                line(8.8f, 15f, 15f, 15f)
                poly(12.5f, 12f, 15.5f, 15f, 12.5f, 18f)
            }
            DIcon.Back -> {
                line(20f, 12f, 5f, 12f)
                poly(11f, 6f, 5f, 12f, 11f, 18f)
            }
            DIcon.Info -> {
                disc(12f, 12f, 9f, 0.18f)
                line(12f, 11f, 12f, 16.5f)
                dot(12f, 7.7f, 1.2f)
            }
            DIcon.Paste -> {
                panel(5f, 5f, 14f, 16f, 2.5f, 0.2f)
                block(9f, 3f, 6f, 4f, 1.4f)
                line(9f, 12f, 15f, 12f)
                line(9f, 16f, 15f, 16f)
            }
            DIcon.Stack -> {
                shape(0.28f, 12f, 3.5f, 21f, 8.2f, 12f, 12.9f, 3f, 8.2f)
                poly(3f, 12.3f, 12f, 17f, 21f, 12.3f)
                poly(3f, 16.3f, 12f, 21f, 21f, 16.3f)
            }
            DIcon.Bolt -> shape(0.3f, 13.5f, 3f, 5f, 13.5f, 11f, 13.5f, 10f, 21f, 19f, 10f, 13f, 10f)
            DIcon.CheckCircle -> {
                disc(12f, 12f, 9f, 0.2f)
                poly(8f, 12.5f, 11f, 15.5f, 16.5f, 9f)
            }
            DIcon.Alert -> {
                shape(0.22f, 12f, 3.8f, 21.2f, 19.8f, 2.8f, 19.8f)
                line(12f, 9.5f, 12f, 14f)
                dot(12f, 16.8f, 1.2f)
            }
            DIcon.Video -> {
                panel(3f, 5.5f, 18f, 13f, 3.5f, 0.2f)
                solid(10f, 9.2f, 15.2f, 12f, 10f, 14.8f)
            }
            DIcon.Audio -> {
                shape(0.3f, 10.5f, 5f, 18f, 7f, 18f, 11f, 10.5f, 9f)
                line(10.5f, 5f, 10.5f, 17f)
                disc(7.7f, 17.3f, 2.8f, 0.55f)
            }
            DIcon.Image -> {
                panel(3.5f, 4.5f, 17f, 15f, 3.5f, 0.2f)
                dot(9f, 9.8f, 1.6f)
                poly(4.5f, 17.5f, 10f, 12.5f, 13.5f, 15.5f, 16.5f, 12.8f, 19.5f, 16.5f)
            }
            DIcon.Doc -> {
                shape(0.2f, 6f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 6f, 21f)
                poly(14f, 3f, 14f, 8f, 19f, 8f)
                line(9f, 13f, 16f, 13f)
                line(9f, 16.5f, 14f, 16.5f)
            }
            DIcon.Archive -> {
                panel(4f, 8.5f, 16f, 11.5f, 2.5f, 0.2f)
                panel(3f, 4f, 18f, 4.5f, 1.8f, 0.35f)
                block(10.3f, 12.5f, 3.4f, 4.5f, 1.2f)
            }
            DIcon.Package -> {
                shape(0.2f, 12f, 3f, 20f, 7.5f, 20f, 16.5f, 12f, 21f, 4f, 16.5f, 4f, 7.5f)
                poly(4f, 7.5f, 12f, 12f, 20f, 7.5f)
                line(12f, 12f, 12f, 21f)
            }
            DIcon.Disk -> {
                disc(12f, 12f, 9f, 0.18f)
                disc(12f, 12f, 3f, 0.5f)
            }
            DIcon.Edit -> {
                shape(0.25f, 5f, 19f, 5.6f, 14.2f, 15.8f, 4f, 20f, 8.2f, 9.6f, 18.6f)
                line(13.2f, 6.6f, 17.4f, 10.8f)
            }
            DIcon.Sort -> {
                line(4f, 7f, 14f, 7f)
                line(4f, 12f, 11f, 12f)
                line(4f, 17f, 8f, 17f)
                line(18f, 6f, 18f, 18f)
                poly(15f, 15f, 18f, 18f, 21f, 15f)
            }
            DIcon.Link -> rotate(-45f, pt(12f, 12f)) {
                panel(1.5f, 9f, 12f, 6f, 3f, 0.2f)
                panel(10.5f, 9f, 12f, 6f, 3f, 0.2f)
            }
            DIcon.Clock -> {
                disc(12f, 12f, 9f, 0.12f)
                poly(12f, 6.8f, 12f, 12f, 15.6f, 14.2f)
            }
            DIcon.Gauge -> {
                drawArc(
                    color = tint,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = pt(3f, 3.5f),
                    size = Size(18f * u, 18f * u),
                    style = stroke,
                )
                line(12f, 13f, 16.2f, 8.8f)
                disc(12f, 13f, 1.9f, 0.9f)
            }
            DIcon.Queue -> {
                dot(5f, 7f, 1.6f)
                line(9f, 7f, 20f, 7f)
                dot(5f, 12f, 1.6f)
                line(9f, 12f, 20f, 12f)
                dot(5f, 17f, 1.6f)
                line(9f, 17f, 16f, 17f)
            }
            DIcon.Globe -> {
                disc(12f, 12f, 9f, 0.15f)
                drawOval(tint, pt(8f, 3f), Size(8f * u, 18f * u), style = stroke)
                line(3f, 12f, 21f, 12f)
            }
            DIcon.Folder -> shape(0.22f, 3f, 6f, 9.5f, 6f, 11.8f, 8.8f, 21f, 8.8f, 21f, 19f, 3f, 19f)
        }
    }
}
