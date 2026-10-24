@file:OptIn(ExperimentalMaterial3Api::class)

package app.downly.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.downly.data.AppLanguage
import app.downly.data.ProxyMode
import app.downly.data.ThemeMode
import app.downly.ui.LocalStrings
import app.downly.ui.PlatformActions
import app.downly.ui.components.CountPicker
import app.downly.ui.formatBytes
import kotlinx.coroutines.launch
import app.downly.ui.components.DIcon
import app.downly.ui.components.SpeedLimitPicker
import app.downly.ui.components.DownlyIcon
import app.downly.ui.downloads.DownloadsController
import app.downly.ui.formatSpeed

@Composable
fun SettingsScreen(controller: DownloadsController, platform: PlatformActions, resumeTick: Int = 0) {
    val strings = LocalStrings.current
    val settings by controller.settings.collectAsState()
    var cleared by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var usageOpen by remember { mutableStateOf(false) }
    var storage by remember { mutableStateOf<DownloadsController.StorageReport?>(null) }
    var storageMessage by remember { mutableStateOf<String?>(null) }
    var cleaning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { storage = controller.storageReport() }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val more = strings.more

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings.settings,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Section(strings.language, DIcon.Globe) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.language == AppLanguage.EN,
                    onClick = { controller.updateSettings { it.copy(language = AppLanguage.EN) } },
                    label = { Text("English") },
                )
                FilterChip(
                    selected = settings.language == AppLanguage.FA,
                    onClick = { controller.updateSettings { it.copy(language = AppLanguage.FA) } },
                    label = { Text("فارسی") },
                )
            }
        }

        Section(strings.theme, DIcon.Disk) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(settings.theme == ThemeMode.SYSTEM, strings.themeSystem) {
                    controller.updateSettings { it.copy(theme = ThemeMode.SYSTEM) }
                }
                ThemeChip(settings.theme == ThemeMode.LIGHT, strings.themeLight) {
                    controller.updateSettings { it.copy(theme = ThemeMode.LIGHT) }
                }
                ThemeChip(settings.theme == ThemeMode.DARK, strings.themeDark) {
                    controller.updateSettings { it.copy(theme = ThemeMode.DARK) }
                }
            }
        }

        Section(strings.maxSimultaneous, DIcon.Queue) {
            Text(
                strings.maxSimultaneousHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CountPicker(
                value = settings.maxSimultaneous,
                presets = listOf(1, 2, 3, 4, 5, 0),
                zeroLabel = strings.unlimited,
                onChange = { n -> controller.updateSettings { it.copy(maxSimultaneous = n) } },
            )
            if (settings.maxSimultaneous == 0 || settings.maxSimultaneous > 6) {
                Text(
                    more.parallelManyWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        Section(strings.speedLimit, DIcon.Gauge) {
            Text(
                strings.speedLimitHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpeedLimitPicker(
                valueKBps = settings.speedLimitKBps,
                presets = listOf(0, 256, 512, 1024, 2048, 5120),
                onChange = { kb -> controller.updateSettings { it.copy(speedLimitKBps = kb) } },
            )
        }

        Section(strings.connections, DIcon.Link) {
            Text(
                strings.connectionsHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CountPicker(
                value = settings.maxConnections,
                presets = listOf(1, 2, 4, 8, 16, 32),
                zeroLabel = "",
                max = 64,
                onChange = { n -> controller.updateSettings { it.copy(maxConnections = n) } },
            )
            Text(
                more.connectionsAdvice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(strings.autoRetry, DIcon.Retry) {
            Text(
                strings.autoRetryHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 3, 5, 10).forEach { n ->
                    FilterChip(
                        selected = settings.autoRetryCount == n,
                        onClick = { controller.updateSettings { it.copy(autoRetryCount = n) } },
                        label = { Text(if (n == 0) strings.off else strings.digits(n.toString())) },
                    )
                }
            }
        }

        Section(null) {
            SwitchRow(
                title = strings.autoResume,
                subtitle = strings.autoResumeHint,
                checked = settings.autoResume,
                onChange = { v -> controller.updateSettings { it.copy(autoResume = v) } },
            )
            SwitchRow(
                title = strings.notifications,
                subtitle = strings.notificationsHint,
                checked = settings.notificationsEnabled,
                onChange = { v -> controller.updateSettings { it.copy(notificationsEnabled = v) } },
            )
            SwitchRow(
                title = strings.wifiOnly,
                subtitle = strings.wifiOnlyHint,
                checked = settings.wifiOnly,
                onChange = { v -> controller.updateSettings { it.copy(wifiOnly = v) } },
            )
            SwitchRow(
                title = strings.organizeByCategory,
                subtitle = strings.organizeByCategoryHint,
                checked = settings.organizeByCategory,
                onChange = { v -> controller.updateSettings { it.copy(organizeByCategory = v) } },
            )
        }

        Section(more.proxyTitle, DIcon.Globe) {
            Text(
                more.proxyHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.proxyMode == ProxyMode.NONE,
                    onClick = { controller.updateSettings { it.copy(proxyMode = ProxyMode.NONE) } },
                    label = { Text(more.proxyOff) },
                )
                FilterChip(
                    selected = settings.proxyMode == ProxyMode.HTTP,
                    onClick = { controller.updateSettings { it.copy(proxyMode = ProxyMode.HTTP) } },
                    label = { Text(more.proxyHttp) },
                )
                FilterChip(
                    selected = settings.proxyMode == ProxyMode.SOCKS,
                    onClick = { controller.updateSettings { it.copy(proxyMode = ProxyMode.SOCKS) } },
                    label = { Text(more.proxySocks) },
                )
            }
            if (settings.proxyMode != ProxyMode.NONE) {
                var host by remember { mutableStateOf(settings.proxyHost) }
                var port by remember { mutableStateOf(if (settings.proxyPort > 0) settings.proxyPort.toString() else "") }
                var user by remember { mutableStateOf(settings.proxyUser) }
                var pass by remember { mutableStateOf(settings.proxyPassword) }
                val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it.trim()
                        controller.updateSettings { s -> s.copy(proxyHost = host) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(more.proxyHost) },
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { value ->
                        port = value.filter { it.isDigit() }.take(5)
                        controller.updateSettings { s -> s.copy(proxyPort = port.toIntOrNull() ?: 0) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(more.proxyPort) },
                    textStyle = ltr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = {
                        user = it
                        controller.updateSettings { s -> s.copy(proxyUser = user) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(more.proxyUser) },
                    textStyle = ltr,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = {
                        pass = it
                        controller.updateSettings { s -> s.copy(proxyPassword = pass) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(more.proxyPass) },
                    textStyle = ltr,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                )
                Text(
                    more.proxyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section(more.statsTitle, DIcon.Gauge) {
            Text(
                more.statsHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { usageOpen = true }) { Text(more.statsOpen) }
        }

        Section(strings.batteryTitle, DIcon.Bolt) {
            Text(
                strings.batteryHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { platform.openBatterySettings() }) { Text(strings.batteryButton) }
        }

        Section(more.schedTitle, DIcon.Clock) {
            val exact = remember(resumeTick) { platform.canScheduleExactAlarms() }
            val battery = remember(resumeTick) { platform.isIgnoringBatteryOptimizations() }
            Text(
                more.schedHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusRow(exact, more.schedExactOk, more.schedExactNeeded, more.schedExactAllow) { platform.requestExactAlarms() }
            StatusRow(battery, more.schedBatteryOk, more.schedBatteryNeeded, more.schedBatteryAllow) {
                platform.requestIgnoreBatteryOptimizations()
            }
            Text(
                more.schedBrandNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section(strings.storage, DIcon.Folder) {
            SwitchRow(
                title = strings.savePublic,
                subtitle = strings.savePublicHint,
                checked = settings.savePublic,
                onChange = { v -> controller.updateSettings { it.copy(savePublic = v) } },
            )
            if (settings.savePublic) {
                Text(
                    text = "${strings.folderCurrent}: ${settings.customFolderName.ifBlank { strings.folderDefault }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        platform.pickFolder { uri, name ->
                            if (uri != null) {
                                controller.updateSettings { it.copy(customFolderUri = uri, customFolderName = name.orEmpty()) }
                            }
                        }
                    }) { Text(strings.folderChoose) }
                    if (settings.customFolderUri.isNotBlank()) {
                        TextButton(onClick = {
                            controller.updateSettings { it.copy(customFolderUri = "", customFolderName = "") }
                        }) { Text(strings.folderReset) }
                    }
                }
            }
            Text(
                text = controller.downloadDirPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = {
                controller.clearCompleted()
                cleared = true
            }) { Text(if (cleared) strings.clearedToast else strings.clearCompleted) }
        }

        Section(more.saveLocationTitle, DIcon.Folder) {
            // Re-checked every time the app comes back from the system screen where the user allows it.
            val direct = remember(resumeTick) { platform.hasFullStorageAccess() }
            Text(
                if (direct) more.saveDirectOn else more.saveDirectOff,
                style = MaterialTheme.typography.bodySmall,
                color = if (direct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
            if (!direct) {
                FilledTonalButton(onClick = { platform.requestFullStorageAccess() }) { Text(more.saveDirectAllow) }
            }
        }

        Section(more.storageTitle, DIcon.Disk) {
            Text(
                more.storageHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val report = storage
            if (report != null) {
                StorageLine(more.storageUnfinished, formatBytes(report.unfinishedBytes, strings))
                StorageLine(more.storageFinished, formatBytes(report.finishedBytes, strings))
                StorageLine(more.storageLeftover, formatBytes(report.leftoverBytes, strings))
            }
            FilledTonalButton(
                enabled = !cleaning,
                onClick = {
                    cleaning = true
                    scope.launch {
                        val result = controller.cleanLeftovers()
                        storage = controller.storageReport()
                        storageMessage = if (result.freedBytes == 0L && result.movedFiles == 0) {
                            more.storageNothing
                        } else {
                            more.storageCleaned(formatBytes(result.freedBytes, strings), result.movedFiles)
                        }
                        cleaning = false
                    }
                },
            ) { Text(more.storageClean) }
            storageMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        Section(more.backupTitle, DIcon.Disk) {
            Text(
                more.backupHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    platform.exportBackup("downly-backup.json", controller.exportBackup()) { ok ->
                        backupMessage = if (ok) more.backupSaved else more.backupFailed
                    }
                }) { Text(more.backupExport) }
                FilledTonalButton(onClick = {
                    platform.importBackup { text ->
                        if (text != null) {
                            val n = controller.importBackup(text)
                            backupMessage = if (n == null) more.backupInvalid else more.backupRestored(n)
                        }
                    }
                }) { Text(more.backupImport) }
            }
            backupMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        Section(strings.about, DIcon.Info) {
            Text("${strings.appName} ${platform.appVersion}", fontWeight = FontWeight.SemiBold)
            Text(
                strings.aboutBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (usageOpen) UsageDialog(controller = controller, onClose = { usageOpen = false })
}

/** A green line when everything is fine, otherwise the problem and a button that fixes it. */
@Composable
private fun StatusRow(ok: Boolean, okText: String, problemText: String, action: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (ok) okText else problemText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        )
        if (!ok) FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun StorageLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ThemeChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Section(title: String?, icon: DIcon? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon != null) {
                        DownlyIcon(icon, tint = MaterialTheme.colorScheme.primary, iconSize = 20.dp)
                    }
                    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            content()
        }
    }
}
