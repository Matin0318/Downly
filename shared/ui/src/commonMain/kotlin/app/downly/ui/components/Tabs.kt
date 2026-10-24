package app.downly.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SegmentSpec(val label: String, val countText: String, val accent: Color)

/**
 * Status tabs as one rounded control: every segment shows a big number and a small label.
 * The selected segment glows in its own colour.
 */
@Composable
fun DownlySegmentedTabs(
    segments: List<SegmentSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            segments.forEachIndexed { index, seg ->
                val isSelected = index == selected
                val shape = RoundedCornerShape(18.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(if (isSelected) seg.accent.copy(alpha = 0.16f) else Color.Transparent)
                        .then(if (isSelected) Modifier.border(1.dp, seg.accent.copy(alpha = 0.55f), shape) else Modifier)
                        .clickable { onSelect(index) }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = seg.countText,
                        color = if (isSelected) seg.accent else MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = seg.label,
                        color = if (isSelected) seg.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

class PillSpec(val label: String, val countText: String, val icon: DIcon? = null)

/** Small scrollable pills, used for the queue filter. */
@Composable
fun DownlyPills(
    pills: List<PillSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pills.forEachIndexed { index, pill ->
            val isSelected = index == selected
            val shape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(if (isSelected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, if (isSelected) accent else Color.Transparent, shape)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pill.icon?.let { DownlyIcon(it, tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant, iconSize = 16.dp) }
                Text(
                    text = pill.label,
                    color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) accent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = pill.countText,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
