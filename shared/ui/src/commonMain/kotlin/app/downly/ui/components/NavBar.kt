package app.downly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import app.downly.ui.theme.AccentBrush
import app.downly.ui.theme.OnAccent

class NavSpec(
    val icon: DIcon,
    val label: String,
    /** Small number on the icon (0 = hidden). */
    val badge: Int = 0,
    val badgeText: String = "",
)

/**
 * Floating bottom bar: two tabs, a big gradient "+" button with the label Add in the middle, two more tabs.
 * [items] must have exactly four entries; [selected] is an index into it (or -1).
 */
@Composable
fun DownlyNavBar(
    items: List<NavSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    addLabel: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, spec ->
                if (index == 2) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onAdd)
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(50.dp).clip(CircleShape).background(AccentBrush),
                            contentAlignment = Alignment.Center,
                        ) {
                            DownlyIcon(DIcon.Plus, tint = Color(0xFF00241D), iconSize = 28.dp)
                        }
                        Text(
                            text = addLabel,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
                NavSlot(spec, index == selected, { onSelect(index) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavSlot(spec: NavSpec, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            DownlyIcon(spec.icon, tint = tint, iconSize = 24.dp)
            if (spec.badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (spec.badge > 99) "99" else spec.badgeText,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = spec.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * Side navigation for tablets, foldables and landscape phones. With [extended] it is a wide panel with
 * labels next to the icons; otherwise a slim rail. It sits at the start edge (the right in Persian).
 */
@Composable
fun DownlyNavRail(
    items: List<NavSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    addLabel: String,
    onAdd: () -> Unit,
    appName: String,
    extended: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxHeight()) {
        Surface(
            modifier = Modifier.width(if (extended) 236.dp else 92.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (extended) 14.dp else 10.dp, vertical = 18.dp),
                horizontalAlignment = if (extended) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = if (extended) 6.dp else 0.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DownlyLogo(size = 42.dp)
                    if (extended) {
                        Text(appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = (if (extended) Modifier.fillMaxWidth().height(52.dp) else Modifier.size(56.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(AccentBrush)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DownlyIcon(DIcon.Plus, tint = OnAccent, iconSize = 26.dp)
                        if (extended) {
                            Text(addLabel, color = OnAccent, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                items.forEachIndexed { index, spec ->
                    RailItem(spec, index == selected, extended) { onSelect(index) }
                }
            }
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
private fun RailItem(spec: NavSpec, selected: Boolean, extended: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (selected) FontWeight.Bold else FontWeight.Medium
    if (extended) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DownlyIcon(spec.icon, tint = tint, iconSize = 24.dp)
            Text(spec.label, color = tint, fontWeight = weight, maxLines = 1, modifier = Modifier.weight(1f))
            if (spec.badge > 0) {
                Box(
                    modifier = Modifier.clip(CircleShape).background(accent).padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (spec.badge > 99) "99" else spec.badgeText,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .width(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                DownlyIcon(spec.icon, tint = tint, iconSize = 24.dp)
                if (spec.badge > 0) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (spec.badge > 99) "99" else spec.badgeText,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(spec.label, color = tint, fontSize = 11.sp, fontWeight = weight, maxLines = 1)
        }
    }
}
