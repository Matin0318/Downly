package app.downly.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.downly.ui.LocalStrings
import app.downly.ui.formatSpeed
import kotlin.math.roundToInt

/** The fastest limit the custom field accepts: 1 GB/s expressed in KB/s. */
private const val MAX_KBPS = 1_048_576

/**
 * Turns what the user typed into KB/s. Accepts Persian and Arabic digits and a comma or Persian
 * decimal mark. Returns null when the text is not a positive number.
 */
fun parseSpeedKBps(text: String, megabytes: Boolean): Int? {
    val normalized = buildString {
        for (c in text.trim()) {
            when (c) {
                in '۰'..'۹' -> append('0' + (c - '۰'))
                in '٠'..'٩' -> append('0' + (c - '٠'))
                ',', '٫', '،' -> append('.')
                else -> append(c)
            }
        }
    }
    val number = normalized.toDoubleOrNull() ?: return null
    if (number <= 0.0) return null
    val kb = (if (megabytes) number * 1024.0 else number).roundToInt()
    return kb.coerceIn(1, MAX_KBPS)
}

/**
 * Speed limit choice: quick presets (0 = unlimited) and a "Custom" option where any number can be typed in
 * KB/s or MB/s. [onChange] is called with the limit in KB/s every time a valid value is chosen.
 */
@Composable
fun SpeedLimitPicker(
    valueKBps: Int,
    presets: List<Int>,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val more = strings.more
    val isCustomValue = valueKBps > 0 && valueKBps !in presets
    var customOpen by remember { mutableStateOf(isCustomValue) }
    var megabytes by remember { mutableStateOf(isCustomValue && valueKBps >= 1024 && valueKBps % 1024 == 0) }
    var text by remember {
        mutableStateOf(
            when {
                !isCustomValue -> ""
                valueKBps >= 1024 && valueKBps % 1024 == 0 -> (valueKBps / 1024).toString()
                else -> valueKBps.toString()
            },
        )
    }

    fun commit() {
        val kb = parseSpeedKBps(text, megabytes)
        if (kb != null) onChange(kb)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { kb ->
                FilterChip(
                    selected = !customOpen && valueKBps == kb,
                    onClick = {
                        customOpen = false
                        onChange(kb)
                    },
                    label = { Text(if (kb == 0) strings.unlimited else formatSpeed(kb * 1024L, strings)) },
                )
            }
            FilterChip(
                selected = customOpen,
                onClick = {
                    customOpen = true
                    commit()
                },
                label = {
                    Text(if (isCustomValue) formatSpeed(valueKBps * 1024L, strings) else more.speedCustom)
                },
            )
        }
        if (customOpen) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        text = value.filter { it.isDigit() || it == '.' || it == ',' || it == '٫' }.take(8)
                        commit()
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(more.speedValue) },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp),
                )
                FilterChip(
                    selected = !megabytes,
                    onClick = {
                        megabytes = false
                        commit()
                    },
                    label = { Text("KB/s") },
                )
                FilterChip(
                    selected = megabytes,
                    onClick = {
                        megabytes = true
                        commit()
                    },
                    label = { Text("MB/s") },
                )
            }
            Text(
                more.speedCustomHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
