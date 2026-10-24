package app.downly.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.downly.core.model.DownloadStatus
import app.downly.ui.AppStrings
import app.downly.ui.LocalStrings
import app.downly.ui.PlatformActions
import app.downly.ui.components.DIcon
import app.downly.ui.components.DownlyIcon
import app.downly.ui.components.FileCategory
import app.downly.ui.components.categoryOf
import app.downly.ui.downloads.DownloadItem
import app.downly.ui.downloads.DownloadsController
import app.downly.ui.formatBytes
import kotlinx.coroutines.launch

/**
 * File manager: every finished file, filtered by type, with open / play / share / rename / delete.
 * Videos and music play in the built-in player.
 */
@Composable
fun FilesScreen(
    controller: DownloadsController,
    platform: PlatformActions,
    onClose: () -> Unit,
) {
    val strings = LocalStrings.current
    val more = strings.more
    val allItems by controller.items.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var category by remember { mutableStateOf<FileCategory?>(null) }
    var query by remember { mutableStateOf("") }
    var renameId by remember { mutableStateOf<Long?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }

    val done = allItems.filter { it.status == DownloadStatus.COMPLETED }
    val present = FileCategory.values().filter { c -> done.any { categoryOf(it.fileName) == c } }
    val visible = done.filter {
        (category == null || categoryOf(it.fileName) == category) &&
            (query.isBlank() || it.fileName.contains(query, ignoreCase = true))
    }
    val totalSize = visible.sumOf { it.totalBytes.coerceAtLeast(0L) }

    fun toast(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    fun pathOf(item: DownloadItem): String? = item.destinationPath

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(more.filesTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        more.filesSummary(visible.size, formatBytes(totalSize, strings)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { DownlyIcon(DIcon.Close) }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text(strings.searchHint) },
                shape = RoundedCornerShape(16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text(strings.allTypes) })
                present.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(strings.categoryName(c)) })
                }
            }

            if (visible.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(more.filesEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { item ->
                        FileRow(
                            strings = strings,
                            item = item,
                            onOpen = {
                                val path = pathOf(item)
                                if (path == null || !platform.openFile(path)) toast(more.filesOpenFailed)
                            },
                            onPlay = {
                                val path = pathOf(item)
                                val ok = path != null && (platform.playMedia(path, item.fileName) || platform.openFile(path))
                                if (!ok) toast(more.filesOpenFailed)
                            },
                            onShare = {
                                val path = pathOf(item)
                                if (path == null || !platform.shareFile(path)) toast(more.filesOpenFailed)
                            },
                            onRename = { renameId = item.id },
                            onDelete = { deleteId = item.id },
                        )
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    val renaming = allItems.firstOrNull { it.id == renameId }
    if (renaming != null) {
        var name by remember(renaming.id) { mutableStateOf(renaming.fileName) }
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text(more.filesRename) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && name.trim() != renaming.fileName,
                    onClick = {
                        val id = renaming.id
                        val newName = name
                        renameId = null
                        scope.launch { if (!controller.rename(id, newName)) snackbar.showSnackbar(more.filesRenameFailed) }
                    },
                ) { Text(strings.save) }
            },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text(strings.cancel) } },
        )
    }

    val deleting = allItems.firstOrNull { it.id == deleteId }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(strings.delete) },
            text = { Text(deleting.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    controller.remove(deleting.id, deleteFile = true)
                    deleteId = null
                }) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(strings.cancel) } },
        )
    }
}

@Composable
private fun FileRow(
    strings: AppStrings,
    item: DownloadItem,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val category = categoryOf(item.fileName)
    val playable = category == FileCategory.Video || category == FileCategory.Audio
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(category.from, category.to))),
                    contentAlignment = Alignment.Center,
                ) {
                    DownlyIcon(category.icon, tint = Color.White, iconSize = 22.dp)
                }
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        item.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${strings.categoryName(category)} • ${formatBytes(item.totalBytes.coerceAtLeast(0L), strings)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (playable) {
                    IconButton(onClick = onPlay) { DownlyIcon(DIcon.Play, tint = MaterialTheme.colorScheme.primary) }
                }
                IconButton(onClick = onOpen) { DownlyIcon(DIcon.Open) }
                IconButton(onClick = onShare) { DownlyIcon(DIcon.Share) }
                IconButton(onClick = onRename) { DownlyIcon(DIcon.Edit) }
                IconButton(onClick = onDelete) { DownlyIcon(DIcon.Trash, tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
