package app.downly.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Pick any hour and minute (24-hour clock). Returns minutes since midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayDialog(
    title: String,
    initialMinute: Int,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinute / 60).coerceIn(0, 23),
        initialMinute = (initialMinute % 60).coerceIn(0, 59),
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(12.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
    )
}
