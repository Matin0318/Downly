@file:OptIn(ExperimentalMaterial3Api::class)

package app.downly.ui.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import app.downly.ui.components.DownlyLogo
import app.downly.ui.components.SpeedLimitPicker
import androidx.compose.ui.draw.clip
import app.downly.ui.theme.WindowSize
import app.downly.ui.theme.LocalWindowSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.downly.core.model.DownloadStatus
import app.downly.ui.AppStrings
import app.downly.ui.HostSignals
import app.downly.ui.LocalStrings
import app.downly.ui.PlatformActions
import app.downly.ui.components.DIcon
import app.downly.ui.components.DownlyIcon
import app.downly.ui.components.FileBadge
import app.downly.ui.components.FileCategory
import app.downly.ui.components.DownlyPills
import app.downly.ui.components.DownlySegmentedTabs
import app.downly.ui.components.PillSpec
import app.downly.ui.components.SegmentSpec
import app.downly.ui.queues.displayName
import app.downly.ui.theme.AccentBrush
import app.downly.ui.components.categoryOf
import app.downly.ui.components.SegmentBar
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import app.downly.ui.components.DownlyProgressBar
import app.downly.ui.components.SpeedGraph
import app.downly.ui.formatBytes
import app.downly.ui.formatDuration
import app.downly.ui.formatPercent
import app.downly.ui.formatSpeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Filter { ACTIVE, DONE, FAILED }

private enum class SortMode { NEWEST, OLDEST, NAME, SIZE }

private fun DownloadItem.matches(filter: Filter): Boolean = when (filter) {
    Filter.ACTIVE -> status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED || status == DownloadStatus.PAUSED
    Filter.DONE -> status == DownloadStatus.COMPLETED
    Filter.FAILED -> status == DownloadStatus.FAILED
}

/** Lower case, one form of the Persian/Arabic look-alike letters, English digits, no joiners. */
private fun normalizeForSearch(text: String): String = buildString(text.length) {
    for (c in text.lowercase()) {
        when (c) {
            'ي', 'ى' -> append('ی')
            'ك' -> append('ک')
            in '۰'..'۹' -> append('0' + (c - '۰'))
            in '٠'..'٩' -> append('0' + (c - '٠'))
            '\u200c', '\u200d', '\u0640' -> {}
            else -> append(c)
        }
    }
}

/** Every typed word must appear in the file name or the link. */
private fun DownloadItem.matchesSearch(needles: List<String>): Boolean {
    if (needles.isEmpty()) return true
    val haystack = normalizeForSearch(fileName + " " + url)
    return needles.all { haystack.contains(it) }
}

private fun looksLikeUrl(text: String): Boolean =
    (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) || text.startsWith("ftp://", ignoreCase = true) || text.startsWith("magnet:", ignoreCase = true)) &&
        text.none { it.isWhitespace() }

@Composable
fun DownloadsScreen(
    controller: DownloadsController,
    platform: PlatformActions,
    signals: HostSignals,
    queueFilter: Long?,
    onQueueFilterChange: (Long?) -> Unit,
    onOpenAdd: () -> Unit,
    onOpenFiles: () -> Unit = {},
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    val strings = LocalStrings.current
    val items by controller.items.collectAsState()
    val settings by controller.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var filter by remember { mutableStateOf(Filter.ACTIVE) }
    var category by remember { mutableStateOf<FileCategory?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.NEWEST) }
    var sortOpen by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var detailsId by remember { mutableStateOf<Long?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var speedId by remember { mutableStateOf<Long?>(null) }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    var dismissedClip by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var bulkDeleteOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }

    val selecting = selected.isNotEmpty()
    backHandler(selecting) { selected = emptySet() }

    fun toast(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun toggleSelected(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    // Clipboard link detection each time the app comes to the foreground.
    LaunchedEffect(signals.resumeCount) {
        delay(400)
        val text = platform.readClipboardText()?.trim()
        clipboardUrl = if (text != null && looksLikeUrl(text) && text != dismissedClip && items.none { it.url == text }) {
            text
        } else {
            null
        }
    }

    val totalSpeed = items.sumOf { it.speedBytesPerSec }
    val activeCount = items.count { it.isActive }
    val pausedCount = items.count { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }

    val inQueue = items.filter { queueFilter == null || it.queueId == queueFilter }
    // While searching, look through every download: the status tab and the queue filter are ignored,
    // so a finished file is found even when the "In progress" tab is open.
    val searching = query.isNotBlank()
    val needles = remember(query) { normalizeForSearch(query).split(' ').filter { it.isNotEmpty() } }
    val filtered = (if (searching) items else inQueue).filter {
        (searching || it.matches(filter)) &&
            (category == null || categoryOf(it.fileName) == category) &&
            (!searching || it.matchesSearch(needles))
    }
    val visible = when (sortMode) {
        SortMode.NEWEST -> filtered
        SortMode.OLDEST -> filtered.reversed()
        SortMode.NAME -> filtered.sortedBy { it.fileName.lowercase() }
        SortMode.SIZE -> filtered.sortedByDescending { it.totalBytes }
    }
    val queueNames = settings.queues.associate { it.id to it.displayName(strings) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---------- top bar (normal or selection mode)
        if (selecting) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selected = emptySet() }) { DownlyIcon(DIcon.Close) }
                Text(
                    text = strings.selectedCount(selected.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { controller.startIds(selected) }) { DownlyIcon(DIcon.Play) }
                IconButton(onClick = { controller.pauseIds(selected) }) { DownlyIcon(DIcon.Pause) }
                IconButton(onClick = { moveOpen = true }) { DownlyIcon(DIcon.Queue) }
                IconButton(onClick = { selected = visible.map { it.id }.toSet() }) { DownlyIcon(DIcon.CheckCircle) }
                IconButton(onClick = { bulkDeleteOpen = true }) {
                    DownlyIcon(DIcon.Trash, tint = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (LocalWindowSize.current == WindowSize.Compact) {
                    DownlyLogo(modifier = Modifier.padding(end = 12.dp), size = 42.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (activeCount > 0) {
                        Text(
                            text = "${formatSpeed(totalSpeed, strings)} • ${strings.digits(activeCount.toString())} ${strings.activeNow}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (activeCount > 0) {
                    IconButton(onClick = controller::pauseAll) { DownlyIcon(DIcon.Pause) }
                } else if (pausedCount > 0) {
                    IconButton(onClick = controller::resumeAll) { DownlyIcon(DIcon.Play) }
                }
                IconButton(onClick = onOpenFiles) { DownlyIcon(DIcon.Folder) }
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) { query = ""; category = null } }) {
                    DownlyIcon(DIcon.Search)
                }
                Box {
                    IconButton(onClick = { sortOpen = true }) { DownlyIcon(DIcon.Sort) }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        SortMode.values().forEach { mode ->
                            val label = when (mode) {
                                SortMode.NEWEST -> strings.sortNewest
                                SortMode.OLDEST -> strings.sortOldest
                                SortMode.NAME -> strings.sortName
                                SortMode.SIZE -> strings.sortSize
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(label, fontWeight = if (mode == sortMode) FontWeight.Bold else FontWeight.Normal)
                                },
                                leadingIcon = {
                                    if (mode == sortMode) DownlyIcon(DIcon.Check, tint = MaterialTheme.colorScheme.primary, iconSize = 20.dp)
                                },
                                onClick = {
                                    sortMode = mode
                                    sortOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }

        val speedNow by rememberUpdatedState(totalSpeed)
        val speedSamples = remember { mutableStateListOf<Long>() }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                speedSamples.add(speedNow)
                if (speedSamples.size > 40) speedSamples.removeAt(0)
            }
        }

        // ---------- filters
        val accents = listOf(
            MaterialTheme.colorScheme.secondary,
            Color(0xFF4ADE80),
            MaterialTheme.colorScheme.error,
        )
        DownlySegmentedTabs(
            segments = Filter.values().map { f ->
                val label = when (f) {
                    Filter.ACTIVE -> strings.filterActive
                    Filter.DONE -> strings.filterDone
                    Filter.FAILED -> strings.filterFailed
                }
                SegmentSpec(label, strings.digits(inQueue.count { it.matches(f) }.toString()), accents[f.ordinal])
            },
            selected = filter.ordinal,
            onSelect = { filter = Filter.values()[it] },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        )
        if (settings.queues.size > 1) {
            val pills = listOf(PillSpec(strings.allQueues, strings.digits(items.size.toString()), DIcon.Queue)) +
                settings.queues.map { q ->
                    PillSpec(q.displayName(strings), strings.digits(items.count { it.queueId == q.id }.toString()))
                }
            DownlyPills(
                pills = pills,
                selected = if (queueFilter == null) 0 else (settings.queues.indexOfFirst { it.id == queueFilter } + 1).coerceAtLeast(0),
                onSelect = { i -> onQueueFilterChange(if (i == 0) null else settings.queues[i - 1].id) },
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (searching) {
                    item(key = "results", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = strings.more.searchResults(visible.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                if (filter == Filter.ACTIVE && activeCount > 0 && !selecting && !searching) {
                    item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                        SpeedSummaryCard(strings, totalSpeed, activeCount, speedSamples.toList())
                    }
                }
                val clip = clipboardUrl
                if (clip != null) {
                    item(key = "clipboard", span = { GridItemSpan(maxLineSpan) }) {
                        ClipboardBanner(
                            strings = strings,
                            url = clip,
                            onDownload = {
                                controller.add(clip)
                                clipboardUrl = null
                            },
                            onDismiss = {
                                dismissedClip = clip
                                clipboardUrl = null
                            },
                        )
                    }
                }

                if (searchOpen) {
                    item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text(strings.searchHint) },
                                leadingIcon = { DownlyIcon(DIcon.Search, iconSize = 20.dp) },
                                shape = RoundedCornerShape(16.dp),
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = category == null,
                                    onClick = { category = null },
                                    label = { Text(strings.allTypes) },
                                )
                                FileCategory.values().forEach { c ->
                                    FilterChip(
                                        selected = category == c,
                                        onClick = { category = if (category == c) null else c },
                                        leadingIcon = { DownlyIcon(c.icon, tint = c.to, iconSize = 18.dp) },
                                        label = { Text(strings.categoryName(c)) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (visible.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(strings = strings, searching = items.isNotEmpty(), onAdd = onOpenAdd)
                    }
                } else {
                    gridItems(visible, key = { it.id }) { item ->
                        DownloadCard(
                            strings = strings,
                            item = item,
                            selected = item.id in selected,
                            selecting = selecting,
                            queueLabel = if (settings.queues.size > 1) queueNames[item.queueId] else null,
                            otherQueues = settings.queues.filter { it.id != item.queueId }.map { it.id to it.displayName(strings) },
                            onMoveToQueue = { queueId ->
                                controller.moveToQueue(setOf(item.id), queueId)
                                toast(strings.moveToQueueNamed(queueNames[queueId] ?: ""))
                            },
                            onTap = { if (selecting) toggleSelected(item.id) else detailsId = item.id },
                            onLongPress = { toggleSelected(item.id) },
                            onToggle = { controller.toggle(item.id) },
                            onOpen = {
                                val path = item.destinationPath
                                if (path == null || !platform.openFile(path)) toast(strings.openFailed)
                            },
                            onShare = {
                                val path = item.destinationPath
                                if (path == null || !platform.shareFile(path)) toast(strings.openFailed)
                            },
                            onCopy = {
                                platform.copyText(item.url)
                                toast(strings.linkCopied)
                            },
                            onDetails = { detailsId = item.id },
                            onEdit = { editId = item.id },
                            onDelete = { deleteId = item.id },
                            onSpeedLimit = { speedId = item.id },
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    // ---------- dialogs
    val details = items.firstOrNull { it.id == detailsId }
    if (details != null) {
        DetailsDialog(
            strings = strings,
            item = details,
            onVerify = { hash -> controller.verifyChecksum(details.id, hash) },
            onClose = { detailsId = null },
        )
    }

    val speedItem = items.firstOrNull { it.id == speedId }
    if (speedItem != null) {
        SpeedLimitDialog(
            strings = strings,
            item = speedItem,
            onPick = { kb ->
                controller.setSpeedLimit(speedItem.id, kb)
                speedId = null
            },
            onClose = { speedId = null },
        )
    }

    val toEdit = items.firstOrNull { it.id == editId }
    if (toEdit != null) {
        EditLinkDialog(
            strings = strings,
            item = toEdit,
            onSave = { newUrl, mirrors ->
                val ok = controller.updateUrl(toEdit.id, newUrl)
                if (ok) {
                    controller.setMirrors(toEdit.id, mirrors)
                    editId = null
                    toast(strings.linkUpdated)
                }
                ok
            },
            onClose = { editId = null },
        )
    }

    val toDelete = items.firstOrNull { it.id == deleteId }
    if (toDelete != null) {
        DeleteDialog(
            strings = strings,
            item = toDelete,
            onConfirm = { alsoFile ->
                controller.remove(toDelete.id, deleteFile = alsoFile)
                deleteId = null
            },
            onCancel = { deleteId = null },
        )
    }

    if (bulkDeleteOpen) {
        var alsoFiles by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { bulkDeleteOpen = false },
            title = { Text(strings.deleteManyTitle(selected.size)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alsoFiles, onCheckedChange = { alsoFiles = it })
                    Text(strings.deleteAlsoFile)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.removeIds(selected, alsoFiles)
                    selected = emptySet()
                    bulkDeleteOpen = false
                }) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { bulkDeleteOpen = false }) { Text(strings.cancel) } },
        )
    }

    if (moveOpen) {
        AlertDialog(
            onDismissRequest = { moveOpen = false },
            title = { Text(strings.moveToQueue) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    settings.queues.forEach { q ->
                        FilledTonalButton(
                            onClick = {
                                controller.moveToQueue(selected, q.id)
                                selected = emptySet()
                                moveOpen = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(q.displayName(strings)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveOpen = false }) { Text(strings.cancel) } },
        )
    }
}

@Composable
private fun ClipboardBanner(strings: AppStrings, url: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                strings.clipboardTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDownload) { Text(strings.download) }
                TextButton(onClick = onDismiss) { Text(strings.dismiss) }
            }
        }
    }
}

/** Big live speed number with a chart of the last 40 seconds. */
@Composable
private fun SpeedSummaryCard(strings: AppStrings, speed: Long, activeCount: Int, samples: List<Long>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
            Text(
                text = formatSpeed(speed, strings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${strings.digits(activeCount.toString())} ${strings.activeNow}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpeedGraph(
                samples = samples,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(56.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(strings: AppStrings, searching: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(88.dp).background(AccentBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DownlyIcon(if (searching) DIcon.Search else DIcon.Download, tint = Color(0xFF00241D), iconSize = 40.dp)
        }
        Text(
            text = if (searching) strings.noMatches else strings.emptyTitle,
            style = MaterialTheme.typography.titleMedium,
        )
        if (!searching) {
            Text(
                text = strings.emptyBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onAdd) { Text(strings.navAdd) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadCard(
    strings: AppStrings,
    item: DownloadItem,
    selected: Boolean,
    selecting: Boolean,
    queueLabel: String?,
    otherQueues: List<Pair<Long, String>>,
    onMoveToQueue: (Long) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSpeedLimit: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onTap, onLongClick = onLongPress),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selecting) Checkbox(checked = selected, onCheckedChange = { onTap() })
                FileBadge(item.fileName)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = item.fileName,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle(strings, item),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(
                    onClick = if (item.status == DownloadStatus.COMPLETED) onOpen else onToggle,
                    modifier = Modifier.size(44.dp),
                ) {
                    val icon = when (item.status) {
                        DownloadStatus.COMPLETED -> DIcon.Open
                        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> DIcon.Pause
                        DownloadStatus.PAUSED -> DIcon.Play
                        DownloadStatus.FAILED -> DIcon.Retry
                    }
                    DownlyIcon(icon, iconSize = 22.dp)
                }
            }

            ProgressBlock(strings, item)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = queueLabel ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                SmallAction(DIcon.Copy, onCopy)
                if (item.status != DownloadStatus.COMPLETED) SmallAction(DIcon.Edit, onEdit)
                if (item.status != DownloadStatus.COMPLETED) {
                    Box {
                        SmallAction(DIcon.Queue, { menuOpen = true })
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(strings.itemSpeedLimit) },
                                onClick = { menuOpen = false; onSpeedLimit() },
                            )
                            otherQueues.forEach { (queueId, queueName) ->
                                DropdownMenuItem(
                                    text = { Text(strings.moveToQueueNamed(queueName)) },
                                    onClick = { menuOpen = false; onMoveToQueue(queueId) },
                                )
                            }
                        }
                    }
                }
                if (item.status == DownloadStatus.COMPLETED) SmallAction(DIcon.Share, onShare)
                SmallAction(DIcon.Info, onDetails)
                SmallAction(DIcon.Trash, onDelete, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SmallAction(icon: DIcon, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        DownlyIcon(icon, tint = tint, iconSize = 20.dp)
    }
}

/** "605.7 MB / 2.0 GB" (or just the downloaded part when the total is unknown). */
private fun sizeSummary(s: AppStrings, item: DownloadItem): String =
    if (item.totalBytes > 0) {
        "${formatBytes(item.downloadedBytes, s)} / ${formatBytes(item.totalBytes, s)}"
    } else {
        formatBytes(item.downloadedBytes, s)
    }

/** The short grey line under the file name. Speed and time left have their own row below the bar. */
private fun subtitle(s: AppStrings, item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> if (item.waiting) s.queued else s.preparing
    DownloadStatus.DOWNLOADING -> sizeSummary(s, item)
    DownloadStatus.PAUSED -> "${s.paused} • ${sizeSummary(s, item)}"
    DownloadStatus.COMPLETED -> "${s.completed} • ${formatBytes(item.totalBytes, s)}"
    DownloadStatus.FAILED -> s.failed
}

/**
 * Progress under the file name: percent on the start side, speed and time left on the end side, then the
 * bar. Every number is its own text, so Persian and English lines look the same and never mix up.
 */
@Composable
private fun ProgressBlock(strings: AppStrings, item: DownloadItem) {
    if (item.status == DownloadStatus.COMPLETED) return
    val progress = item.progress
    val failed = item.status == DownloadStatus.FAILED
    val paused = item.status == DownloadStatus.PAUSED
    val working = item.status == DownloadStatus.QUEUED || progress == null
    val accent = when {
        failed -> MaterialTheme.colorScheme.error
        paused -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (progress != null && !working) {
                Text(
                    text = formatPercent(progress, strings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.weight(1f))
            if (item.status == DownloadStatus.DOWNLOADING && item.speedBytesPerSec > 0) {
                StatChip(DIcon.Download, formatSpeed(item.speedBytesPerSec, strings))
                item.remainingSeconds?.let {
                    Spacer(Modifier.width(14.dp))
                    StatChip(DIcon.Clock, strings.remaining(formatDuration(it, strings)))
                }
            }
        }
        when {
            failed -> DownlyProgressBar(
                progress = progress ?: 0f,
                from = MaterialTheme.colorScheme.error,
                to = MaterialTheme.colorScheme.error,
            )
            paused -> DownlyProgressBar(
                progress = progress ?: 0f,
                from = MaterialTheme.colorScheme.outline,
                to = MaterialTheme.colorScheme.outline,
            )
            working -> DownlyProgressBar(progress = null)
            else -> DownlyProgressBar(progress = progress)
        }
        if (failed && !item.error.isNullOrBlank()) {
            Text(
                text = item.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatChip(icon: DIcon, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        DownlyIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant, iconSize = 16.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun statusLine(s: AppStrings, item: DownloadItem): String {
    val sizeText = if (item.totalBytes > 0) {
        "${formatBytes(item.downloadedBytes, s)} / ${formatBytes(item.totalBytes, s)}"
    } else {
        formatBytes(item.downloadedBytes, s)
    }
    return when (item.status) {
        DownloadStatus.QUEUED -> if (item.waiting) s.queued else s.preparing
        DownloadStatus.DOWNLOADING -> buildString {
            append(sizeText)
            if (item.speedBytesPerSec > 0) {
                append(" • ").append(formatSpeed(item.speedBytesPerSec, s))
                item.remainingSeconds?.let { append(" • ").append(s.remaining(formatDuration(it, s))) }
            }
        }
        DownloadStatus.PAUSED -> "${s.paused} • $sizeText"
        DownloadStatus.COMPLETED -> "${s.completed} • ${formatBytes(item.totalBytes, s)}"
        DownloadStatus.FAILED -> "${s.failed}: ${item.error ?: ""}"
    }
}

@Composable
private fun DetailsDialog(
    strings: AppStrings,
    item: DownloadItem,
    onVerify: suspend (String) -> DownloadsController.ChecksumResult,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var hash by remember(item.id) { mutableStateOf("") }
    var verdict by remember(item.id) { mutableStateOf<DownloadsController.ChecksumResult?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text(strings.close) } },
        title = { Text(item.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailRow(strings.detailsStatus, statusLine(strings, item))
                if (item.totalBytes > 0) DetailRow(strings.detailsSize, formatBytes(item.totalBytes, strings))
                if (item.segmentProgress.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${strings.detailsConnections}: ${strings.digits(item.segmentProgress.size.toString())}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SegmentBar(fractions = item.segmentProgress, modifier = Modifier.fillMaxWidth())
                    }
                }
                DetailRow(strings.detailsUrl, item.url, selectable = true)
                item.destinationPath?.let { DetailRow(strings.detailsSaved, it, selectable = true) }
                if (item.status == DownloadStatus.COMPLETED) {
                    OutlinedTextField(
                        value = hash,
                        onValueChange = {
                            hash = it
                            verdict = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(strings.checksumHint) },
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        shape = RoundedCornerShape(16.dp),
                    )
                    FilledTonalButton(
                        onClick = { scope.launch { verdict = onVerify(hash) } },
                        enabled = hash.isNotBlank(),
                    ) { Text(strings.verifyChecksum) }
                    verdict?.let { v ->
                        val message = when (v) {
                            DownloadsController.ChecksumResult.MATCH -> strings.checksumMatch
                            DownloadsController.ChecksumResult.MISMATCH -> strings.checksumMismatch
                            DownloadsController.ChecksumResult.INVALID_INPUT -> strings.checksumInvalid
                            DownloadsController.ChecksumResult.NO_FILE -> strings.checksumNoFile
                        }
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (v == DownloadsController.ChecksumResult.MATCH) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String, selectable: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val content: @Composable () -> Unit = {
            Text(
                text = value,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selectable) SelectionContainer { content() } else content()
    }
}

@Composable
private fun DeleteDialog(
    strings: AppStrings,
    item: DownloadItem,
    onConfirm: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var alsoFile by remember { mutableStateOf(false) }
    val canDeleteFile = item.status == DownloadStatus.COMPLETED
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings.deleteTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(strings.deleteBody)
                if (canDeleteFile) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoFile, onCheckedChange = { alsoFile = it })
                        Text(strings.deleteAlsoFile, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(alsoFile && canDeleteFile) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(strings.delete) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(strings.cancel) } },
    )
}

@Composable
private fun EditLinkDialog(
    strings: AppStrings,
    item: DownloadItem,
    onSave: (String, List<String>) -> Boolean,
    onClose: () -> Unit,
) {
    var text by remember(item.id) { mutableStateOf(item.url) }
    var mirrorsText by remember(item.id) { mutableStateOf(item.mirrors.joinToString("\n")) }
    var invalid by remember(item.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(strings.editLink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        invalid = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(strings.invalidUrl) }
                    } else {
                        null
                    },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp),
                )
                Text(strings.more.mirrorsLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = mirrorsText,
                    onValueChange = { mirrorsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    supportingText = { Text(strings.more.mirrorsHint) },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!onSave(text, mirrorsText.lines())) invalid = true }) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(strings.cancel) } },
    )
}

@Composable
private fun SpeedLimitDialog(strings: AppStrings, item: DownloadItem, onPick: (Int) -> Unit, onClose: () -> Unit) {
    var chosen by remember(item.id) { mutableStateOf(item.speedLimitKBps) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(strings.itemSpeedLimit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                SpeedLimitPicker(
                    valueKBps = chosen,
                    presets = listOf(0, 128, 256, 512, 1024, 2048),
                    onChange = { chosen = it },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(chosen) }) { Text(strings.save) } },
        dismissButton = { TextButton(onClick = onClose) { Text(strings.cancel) } },
    )
}

@Composable
internal fun SiteGrabberDialog(
    strings: AppStrings,
    onScan: suspend (String) -> List<String>?,
    onAdd: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("") }
    var links by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(strings.siteGrabber) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = page,
                    onValueChange = { page = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(strings.siteGrabberHint) },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp),
                )
                FilledTonalButton(
                    enabled = page.isNotBlank() && !scanning,
                    onClick = {
                        scanning = true
                        message = null
                        scope.launch {
                            val found = onScan(page)
                            scanning = false
                            if (found == null) {
                                message = strings.scanFailed
                                links = emptyList()
                            } else {
                                links = found.take(300)
                                selected = links.toSet()
                                message = if (found.isEmpty()) strings.noFilesFound else null
                            }
                        }
                    },
                ) { Text(strings.scan) }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (links.isNotEmpty()) {
                    TextButton(onClick = { selected = if (selected.size == links.size) emptySet() else links.toSet() }) {
                        Text(strings.selectAll)
                    }
                    Column(
                        modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    ) {
                        links.forEach { link ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = link in selected,
                                    onCheckedChange = { on -> selected = if (on) selected + link else selected - link },
                                )
                                Text(
                                    link.substringBefore('?').substringAfterLast('/').ifBlank { link },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = { onAdd(links.filter { it in selected }) }) {
                Text(strings.downloadSelected)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(strings.cancel) } },
    )
}
