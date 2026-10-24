package app.downly.ui.queues

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.downly.core.model.DownloadStatus
import app.downly.ui.downloads.DownloadItem
import app.downly.data.DownloadQueue
import app.downly.data.MAIN_QUEUE_ID
import app.downly.data.QueueFinishAction
import app.downly.ui.AppStrings
import app.downly.ui.LocalStrings
import app.downly.ui.components.CountPicker
import app.downly.ui.components.DIcon
import app.downly.ui.components.SpeedLimitPicker
import app.downly.ui.components.DownlyIcon
import app.downly.ui.components.TimeOfDayDialog
import app.downly.ui.downloads.DownloadsController
import app.downly.ui.formatBytes
import app.downly.ui.formatClock
import app.downly.ui.formatPercent
import app.downly.ui.formatSpeed

fun DownloadQueue.displayName(strings: AppStrings): String = name.ifBlank { strings.queueMain }

fun DownloadQueue.scheduleSummary(strings: AppStrings): String =
    if (scheduleEnabled) "${formatClock(startMinute, strings)} → ${formatClock(stopMinute, strings)}" else strings.queueNoSchedule

@Composable
fun QueuesScreen(
    controller: DownloadsController,
) {
    val strings = LocalStrings.current
    val settings by controller.settings.collectAsState()
    val items by controller.items.collectAsState()
    var editing by remember { mutableStateOf<DownloadQueue?>(null) }
    var showingQueueId by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<DownloadQueue?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.queuesTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { editing = DownloadQueue(id = 0L, name = strings.queueDefaultName(settings.queues.size + 1)) }) {
                DownlyIcon(DIcon.Plus, iconSize = 18.dp)
                Text("  ${strings.newQueue}")
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 330.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridItems(settings.queues, key = { it.id }) { queue ->
                val mine = items.filter { it.queueId == queue.id }
                QueueCard(
                    strings = strings,
                    queue = queue,
                    total = mine.size,
                    active = mine.count { it.isActive },
                    onStart = { controller.startQueue(queue.id) },
                    onPause = { controller.pauseQueue(queue.id) },
                    onShow = { showingQueueId = queue.id },
                    onEdit = { editing = queue },
                    onToggle = { on -> controller.setQueueEnabled(queue.id, on) },
                    onDelete = if (queue.id != MAIN_QUEUE_ID) {
                        { deleting = queue }
                    } else {
                        null
                    },
                )
            }
        }
    }

    val shown = settings.queues.firstOrNull { it.id == showingQueueId }
    if (shown != null) {
        QueueDownloadsDialog(
            strings = strings,
            title = shown.displayName(strings),
            downloads = items.filter { it.queueId == shown.id },
            onDismiss = { showingQueueId = null },
        )
    }

    val toDelete = deleting
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(strings.more.queueDelete) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(toDelete.displayName(strings), fontWeight = FontWeight.SemiBold)
                    Text(strings.more.queueDeleteBody)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.deleteQueue(toDelete.id)
                    deleting = null
                }) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(strings.cancel) } },
        )
    }

    val current = editing
    if (current != null) {
        QueueEditorDialog(
            initial = current,
            onSave = { saved ->
                if (saved.id == 0L) {
                    val id = controller.addQueue(saved.name)
                    controller.updateQueue(saved.copy(id = id))
                } else {
                    controller.updateQueue(saved)
                }
                editing = null
            },
            onDelete = if (current.id != 0L && current.id != MAIN_QUEUE_ID) {
                {
                    controller.deleteQueue(current.id)
                    editing = null
                }
            } else {
                null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun QueueCard(
    strings: AppStrings,
    queue: DownloadQueue,
    total: Int,
    active: Int,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onShow: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val more = strings.more
    val dim = if (queue.enabled) 1f else 0.5f
    val compact = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    DownlyIcon(
                        DIcon.Queue,
                        tint = if (active > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        iconSize = 22.dp,
                    )
                }
                Column(modifier = Modifier.weight(1f).alpha(dim)) {
                    Text(
                        queue.displayName(strings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(strings.queueCount(total))
                            if (!queue.enabled) append(" • ${more.queueDisabled}")
                            else if (active > 0) append(" • ${strings.digits(active.toString())} ${strings.activeNow}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = queue.enabled, onCheckedChange = onToggle)
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        DownlyIcon(DIcon.Trash, tint = MaterialTheme.colorScheme.error, iconSize = 22.dp)
                    }
                }
            }

            val hasChips = queue.scheduleEnabled || queue.maxSimultaneous > 0 || queue.speedLimitKBps > 0 ||
                queue.wifiOnly || queue.onFinish != QueueFinishAction.NOTHING
            if (hasChips) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).alpha(dim),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (queue.scheduleEnabled) AssistChip(onClick = onEdit, label = { Text(queue.scheduleSummary(strings)) })
                    if (queue.maxSimultaneous > 0) {
                        AssistChip(onClick = onEdit, label = { Text("${strings.parallelLabel}: ${strings.digits(queue.maxSimultaneous.toString())}") })
                    }
                    if (queue.speedLimitKBps > 0) {
                        AssistChip(onClick = onEdit, label = { Text(formatSpeed(queue.speedLimitKBps * 1024L, strings)) })
                    }
                    if (queue.wifiOnly) AssistChip(onClick = onEdit, label = { Text(strings.wifiOnly) })
                    if (queue.onFinish != QueueFinishAction.NOTHING) {
                        AssistChip(onClick = onEdit, label = { Text(strings.finishInternetPanel) })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (active > 0) {
                    FilledTonalButton(onClick = onPause, modifier = Modifier.height(40.dp), contentPadding = compact) {
                        DownlyIcon(DIcon.Pause, iconSize = 18.dp)
                        Text("  ${strings.queuePause}", maxLines = 1, softWrap = false)
                    }
                } else {
                    Button(
                        onClick = onStart,
                        enabled = total > 0 && queue.enabled,
                        modifier = Modifier.height(40.dp),
                        contentPadding = compact,
                    ) {
                        DownlyIcon(DIcon.Play, iconSize = 18.dp)
                        Text("  ${strings.queueStart}", maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.weight(1f))
                // Icons instead of words: in Persian the words did not fit next to the start button.
                IconButton(onClick = onShow, modifier = Modifier.size(44.dp)) {
                    DownlyIcon(DIcon.Stack, tint = MaterialTheme.colorScheme.primary, iconSize = 24.dp)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
                    DownlyIcon(DIcon.Edit, tint = MaterialTheme.colorScheme.primary, iconSize = 24.dp)
                }
            }
        }
    }
}

@Composable
private fun QueueDownloadsDialog(
    strings: AppStrings,
    title: String,
    downloads: List<DownloadItem>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    strings.queueCount(downloads.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            if (downloads.isEmpty()) {
                Text(strings.queueEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(downloads, key = { it.id }) { item ->
                        QueueDownloadRow(strings, item)
                        if (item != downloads.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

@Composable
private fun QueueDownloadRow(strings: AppStrings, item: DownloadItem) {
    val progress = item.progress
    val status = when (item.status) {
        DownloadStatus.QUEUED -> if (item.waiting) strings.queued else strings.preparing
        DownloadStatus.DOWNLOADING -> if (progress != null) formatPercent(progress, strings) else formatBytes(item.downloadedBytes, strings)
        DownloadStatus.PAUSED -> strings.paused + if (progress != null) " • " + formatPercent(progress, strings) else ""
        DownloadStatus.COMPLETED -> strings.completed
        DownloadStatus.FAILED -> strings.failed
    }
    val statusColor = when (item.status) {
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.DOWNLOADING, DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor, modifier = Modifier.weight(1f))
            if (item.totalBytes > 0) {
                Text(
                    formatBytes(item.totalBytes, strings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (progress != null && item.status != DownloadStatus.COMPLETED) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
    }
}

private val parallelChoices = listOf(0, 1, 2, 3, 4, 5)
private val speedChoices = listOf(0, 256, 512, 1024, 2048)

/** Edit or create a queue. A queue with id 0 is new. */
@Composable
fun QueueEditorDialog(
    initial: DownloadQueue,
    onSave: (DownloadQueue) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var queue by remember { mutableStateOf(initial) }
    var pickStart by remember { mutableStateOf(false) }
    var pickStop by remember { mutableStateOf(false) }
    val isMain = initial.id == MAIN_QUEUE_ID

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(8.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(if (isMain) strings.queueMain else strings.queueEdit) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isMain) {
                    OutlinedTextField(
                        value = queue.name,
                        onValueChange = { queue = queue.copy(name = it.take(40)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(strings.queueNameHint) },
                        shape = RoundedCornerShape(16.dp),
                    )
                }

                SectionLabel(strings.schedule)
                SwitchLine(strings.scheduleEnable, queue.scheduleEnabled) { queue = queue.copy(scheduleEnabled = it) }
                if (queue.scheduleEnabled) {
                    TimeLine(strings.scheduleStart, formatClock(queue.startMinute, strings)) { pickStart = true }
                    TimeLine(strings.scheduleStop, formatClock(queue.stopMinute, strings)) { pickStop = true }
                    Text(strings.daysLabel, style = MaterialTheme.typography.bodyMedium)
                    ChipRow {
                        strings.dayOrder.forEach { day ->
                            val on = ((queue.daysMask shr day) and 1) == 1
                            FilterChip(
                                selected = on,
                                onClick = { queue = queue.copy(daysMask = queue.daysMask xor (1 shl day)) },
                                label = { Text(strings.dayShort(day)) },
                            )
                        }
                    }
                    Text(
                        strings.scheduleHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionLabel(strings.parallelLabel)
                CountPicker(
                    value = queue.maxSimultaneous,
                    presets = parallelChoices,
                    zeroLabel = strings.parallelAuto,
                    onChange = { n -> queue = queue.copy(maxSimultaneous = n) },
                )

                SectionLabel(strings.queueSpeed)
                SpeedLimitPicker(
                    valueKBps = queue.speedLimitKBps,
                    presets = speedChoices,
                    onChange = { kb -> queue = queue.copy(speedLimitKBps = kb) },
                )

                SwitchLine(strings.wifiOnly, queue.wifiOnly) { queue = queue.copy(wifiOnly = it) }

                SectionLabel(strings.queueFinish)
                ChipRow {
                    FilterChip(
                        selected = queue.onFinish == QueueFinishAction.NOTHING,
                        onClick = { queue = queue.copy(onFinish = QueueFinishAction.NOTHING) },
                        label = { Text(strings.finishNothing) },
                    )
                    FilterChip(
                        selected = queue.onFinish == QueueFinishAction.INTERNET_PANEL,
                        onClick = { queue = queue.copy(onFinish = QueueFinishAction.INTERNET_PANEL) },
                        label = { Text(strings.finishInternetPanel) },
                    )
                }
                if (queue.onFinish == QueueFinishAction.INTERNET_PANEL) {
                    Text(
                        strings.finishNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(queue.copy(name = queue.name.trim())) }, enabled = isMain || queue.name.isNotBlank()) {
                Text(strings.save)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(strings.queueDelete, color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
            }
        },
    )

    if (pickStart) {
        TimeOfDayDialog(
            title = strings.scheduleStart,
            initialMinute = queue.startMinute,
            confirmLabel = strings.save,
            cancelLabel = strings.cancel,
            onConfirm = {
                queue = queue.copy(startMinute = it)
                pickStart = false
            },
            onDismiss = { pickStart = false },
        )
    }
    if (pickStop) {
        TimeOfDayDialog(
            title = strings.scheduleStop,
            initialMinute = queue.stopMinute,
            confirmLabel = strings.save,
            cancelLabel = strings.cancel,
            onConfirm = {
                queue = queue.copy(stopMinute = it)
                pickStop = false
            },
            onDismiss = { pickStop = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimeLine(label: String, value: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = onClick) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
