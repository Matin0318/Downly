package app.downly.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.downly.ui.LocalStrings
import app.downly.ui.downloads.DownloadsController
import app.downly.ui.formatBytes

/** Bar chart of the bytes downloaded on each of the last 7 days, plus totals. */
@Composable
fun UsageDialog(controller: DownloadsController, onClose: () -> Unit) {
    val strings = LocalStrings.current
    val more = strings.more
    val usage by controller.usage.collectAsState()
    val today = controller.todayEpochDay()

    val days = (6 downTo 0).map { today - it }
    val values = days.map { usage[it] ?: 0L }
    val sumLast = { n: Int -> (0 until n).sumOf { usage[today - it] ?: 0L } }
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(more.statsTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    more.statsHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The chart always runs left to right (oldest day on the left), also in Persian.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val maxValue = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
                        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                            val slot = size.width / values.size
                            val barWidth = slot * 0.6f
                            values.forEachIndexed { i, v ->
                                val left = i * slot + (slot - barWidth) / 2f
                                drawRoundRect(
                                    color = trackColor,
                                    topLeft = Offset(left, 0f),
                                    size = Size(barWidth, size.height),
                                    cornerRadius = CornerRadius(10f, 10f),
                                )
                                val h = (size.height * v / maxValue).coerceAtLeast(if (v > 0) 6f else 0f)
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(left, size.height - h),
                                    size = Size(barWidth, h),
                                    cornerRadius = CornerRadius(10f, 10f),
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            days.forEach { d ->
                                Text(
                                    text = strings.dayShort(((d + 4) % 7).toInt()),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                TotalLine(more.statsToday, formatBytes(usage[today] ?: 0L, strings))
                TotalLine(more.statsWeek, formatBytes(sumLast(7), strings))
                TotalLine(more.statsMonth, formatBytes(sumLast(30), strings))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(strings.close) } },
    )
}

@Composable
private fun TotalLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}
