package app.downly.ui.downloads

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.downly.core.engine.CurlParser
import app.downly.data.MAIN_QUEUE_ID
import app.downly.ui.LocalStrings
import app.downly.ui.PlatformActions
import app.downly.ui.components.DIcon
import app.downly.ui.components.SpeedLimitPicker
import app.downly.ui.components.DownlyIcon
import app.downly.ui.components.FileCategory
import app.downly.ui.formatBytes
import app.downly.ui.formatClock
import app.downly.ui.formatSpeed
import app.downly.ui.queues.QueueEditorDialog
import app.downly.ui.queues.displayName
import kotlinx.coroutines.delay

private val speedChoices = listOf(0, 256, 512, 1024, 2048)

/**
 * The small "new download" window: paste a link (name and size are detected), choose where to save it,
 * pick a queue and more settings, then Add (only put it in the list) or Start (download now).
 */
@Composable
fun AddDownloadDialog(
    controller: DownloadsController,
    platform: PlatformActions,
    initialText: String?,
    onDismiss: () -> Unit,
    onAdded: (Int) -> Unit,
    onOpenGrabber: () -> Unit,
) {
    val strings = LocalStrings.current
    val settings by controller.settings.collectAsState()

    var linkText by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<DownloadsController.LinkInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    var queueId by remember { mutableStateOf(MAIN_QUEUE_ID) }
    var speedKb by remember { mutableStateOf(0) }
    var subFolder by remember { mutableStateOf("") }
    var moreOpen by remember { mutableStateOf(false) }
    var folderOpen by remember { mutableStateOf(false) }
    var newQueueOpen by remember { mutableStateOf(false) }
    var editQueueOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var qualities by remember { mutableStateOf<List<DownloadsController.QualityOption>>(emptyList()) }
    var qualityUrl by remember { mutableStateOf("") }
    var mirrorsText by remember { mutableStateOf("") }

    fun applyText(text: String) {
        val parsed = LinkInput.parse(text)
        if (parsed.urls.isEmpty()) {
            linkText = text.trim()
            return
        }
        linkText = parsed.urls.joinToString("\n")
        if (parsed.headers.isNotEmpty()) headers = parsed.headers
        error = null
    }

    LaunchedEffect(Unit) {
        initialText?.let { applyText(it) }
    }

    val parsed = remember(linkText) { LinkInput.parse(linkText) }
    val single = parsed.urls.singleOrNull()

    // Ask the server for the real name and size of the link.
    LaunchedEffect(single, headers) {
        info = null
        qualities = emptyList()
        qualityUrl = ""
        if (single == null) {
            if (!nameEdited) name = ""
            return@LaunchedEffect
        }
        checking = true
        try {
            delay(500)
            val found = controller.inspect(single, headers)
            info = found
            if (found != null && !nameEdited) name = found.fileName
            qualities = controller.hlsQualities(single, headers)
        } finally {
            checking = false
        }
    }

    val queue = settings.queues.firstOrNull { it.id == queueId } ?: settings.queues.firstOrNull()

    fun submit(start: Boolean) {
        if (parsed.urls.isEmpty()) {
            error = strings.invalidUrl
            return
        }
        val options = DownloadsController.AddOptions(
            headers = headers,
            queueId = queueId,
            customName = if (parsed.urls.size == 1 && nameEdited) name else "",
            subFolder = subFolder,
            speedLimitKBps = speedKb,
            startNow = start,
            mirrors = if (parsed.urls.size == 1) mirrorsText.lines() else emptyList(),
            hlsVariant = if (parsed.urls.size == 1) qualityUrl else "",
        )
        val added = parsed.urls.count { controller.add(it, options) }
        if (added == 0) error = strings.invalidUrl else onAdded(added)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings.addTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }) { DownlyIcon(DIcon.Settings, iconSize = 22.dp) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(strings.siteGrabber) },
                                onClick = {
                                    menuOpen = false
                                    onDismiss()
                                    onOpenGrabber()
                                },
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.linkLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { folderOpen = true }) {
                        DownlyIcon(DIcon.Folder, tint = MaterialTheme.colorScheme.primary, iconSize = 24.dp)
                    }
                    IconButton(onClick = {
                        val clip = platform.readClipboardText()?.trim()
                        if (clip.isNullOrEmpty()) {
                            error = strings.clipboardEmpty
                        } else {
                            nameEdited = false
                            applyText(clip)
                        }
                    }) {
                        DownlyIcon(DIcon.Paste, tint = MaterialTheme.colorScheme.primary, iconSize = 24.dp)
                    }
                }
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { value ->
                        error = null
                        if (CurlParser.looksLikeCurl(value)) applyText(value) else linkText = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text(strings.urlHint) },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp),
                    isError = error != null,
                )

                Text(strings.nameLabel, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = if (parsed.urls.size > 1) strings.linksFound(parsed.urls.size) else name,
                    onValueChange = {
                        name = it
                        nameEdited = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = parsed.urls.size <= 1,
                    placeholder = { Text(strings.nameAutoHint) },
                    shape = RoundedCornerShape(16.dp),
                )
                val detail = when {
                    checking -> strings.detecting
                    info != null && info!!.totalBytes > 0 -> formatBytes(info!!.totalBytes, strings)
                    else -> null
                }
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

                if (qualities.isNotEmpty()) {
                    Text(strings.more.qualityLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = qualityUrl.isEmpty(),
                            onClick = { qualityUrl = "" },
                            label = { Text(strings.more.qualityBest) },
                        )
                        qualities.forEach { q ->
                            FilterChip(
                                selected = qualityUrl == q.url,
                                onClick = { qualityUrl = q.url },
                                label = { Text(strings.digits(q.label)) },
                            )
                        }
                    }
                }

                TextButton(onClick = { moreOpen = !moreOpen }) {
                    Text(strings.moreSettings + if (moreOpen) "  ▲" else "  ▼")
                }
                if (moreOpen) {
                    Text(strings.queueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        settings.queues.forEach { q ->
                            FilterChip(
                                selected = q.id == queueId,
                                onClick = { queueId = q.id },
                                label = { Text(q.displayName(strings)) },
                            )
                        }
                        AssistChip(onClick = { newQueueOpen = true }, label = { Text("+ ${strings.newQueue}") })
                    }
                    if (queue != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val summary = if (queue.scheduleEnabled) {
                                "${formatClock(queue.startMinute, strings)} → ${formatClock(queue.stopMinute, strings)}"
                            } else {
                                strings.queueNoSchedule
                            }
                            Text(
                                text = "${strings.schedule}: $summary",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { editQueueOpen = true }) { Text(strings.queueEdit) }
                        }
                    }

                    Text(strings.itemSpeedLimit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    SpeedLimitPicker(valueKBps = speedKb, presets = speedChoices, onChange = { speedKb = it })

                    if (parsed.urls.size == 1) {
                        Text(strings.more.mirrorsLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = mirrorsText,
                            onValueChange = { mirrorsText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            supportingText = { Text(strings.more.mirrorsHint) },
                            shape = RoundedCornerShape(16.dp),
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${strings.folderAction}: ${subFolder.ifBlank { strings.folderAuto }}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { folderOpen = true }) { Text(strings.folderAction) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(strings.cancel) }
                    FilledTonalButton(onClick = { submit(start = false) }) { Text(strings.addOnly) }
                    Button(onClick = { submit(start = true) }) { Text(strings.startNow) }
                }
            }
        }
    }

    if (folderOpen) {
        FolderDialog(
            current = subFolder,
            onPick = {
                subFolder = it
                folderOpen = false
            },
            onDismiss = { folderOpen = false },
        )
    }
    if (newQueueOpen) {
        NewQueueDialog(
            defaultName = strings.queueDefaultName(settings.queues.size + 1),
            onCreate = { title ->
                queueId = controller.addQueue(title)
                newQueueOpen = false
            },
            onDismiss = { newQueueOpen = false },
        )
    }
    if (editQueueOpen && queue != null) {
        QueueEditorDialog(
            initial = queue,
            onSave = {
                controller.updateQueue(it)
                editQueueOpen = false
            },
            onDelete = null,
            onDismiss = { editQueueOpen = false },
        )
    }
}

@Composable
private fun FolderDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var custom by remember { mutableStateOf(if (FileCategory.values().any { it.name == current }) "" else current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.folderTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = current.isBlank(), onClick = { onPick("") }, label = { Text(strings.folderAuto) })
                    FileCategory.values().forEach { c ->
                        FilterChip(selected = current == c.name, onClick = { onPick(c.name) }, label = { Text(strings.categoryName(c)) })
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(strings.folderCustomHint) },
                    shape = RoundedCornerShape(16.dp),
                )
                Text(strings.folderNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(custom.trim()) }, enabled = custom.isNotBlank()) { Text(strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun NewQueueDialog(defaultName: String, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var title by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.newQueue) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(strings.queueNameHint) },
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(title.trim()) }, enabled = title.isNotBlank()) { Text(strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
