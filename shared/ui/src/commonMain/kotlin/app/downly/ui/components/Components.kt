package app.downly.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Gradient tile with a drawn glyph for the file type and a tiny extension tag. */
@Composable
fun FileBadge(fileName: String, modifier: Modifier = Modifier) {
    val category = categoryOf(fileName)
    val ext = fileExtension(fileName).take(4).uppercase()
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(category.from, category.to))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            DownlyIcon(category.icon, tint = Color.White, iconSize = 24.dp)
            if (ext.isNotEmpty()) {
                Text(
                    text = ext,
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

class TabSpec(
    val icon: DIcon,
    val label: String,
    val count: Int,
    val countText: String,
    val accent: Color? = null,
)

/** Floating bottom bar with icon tabs and live count badges. */
@Composable
fun DownlyBottomBar(
    tabs: List<TabSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selected
                val accent = tab.accent ?: MaterialTheme.colorScheme.primary
                val iconTint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelect(index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .fillMaxWidth(0.62f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        DownlyIcon(tab.icon, tint = iconTint, iconSize = 24.dp)
                        if (tab.count > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(tab.accent ?: MaterialTheme.colorScheme.outline),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (tab.count > 99) "99" else tab.countText,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Text(
                        text = tab.label,
                        color = iconTint,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** One bar per connection, so you can literally see the parallel download. */
@Composable
fun SegmentBar(
    fractions: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        fractions.forEach { f ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(track),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(f.coerceIn(0f, 1f))
                        .background(color),
                )
            }
        }
    }
}

/** Live speed chart. */
@Composable
fun SpeedGraph(samples: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val maxValue = (samples.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val stepX = size.width / (samples.size - 1)
        val line = Path()
        val fill = Path()
        samples.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / maxValue) * size.height * 0.9f - 2f
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, color.copy(alpha = 0.25f))
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
