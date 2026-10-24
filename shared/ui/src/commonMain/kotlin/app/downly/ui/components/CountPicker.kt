package app.downly.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.downly.ui.LocalStrings

/** Turns typed text (Persian or Arabic digits too) into a whole number from 1 to [max], or null. */
fun parseCount(text: String, max: Int = 999): Int? {
    val digits = buildString {
        for (c in text.trim()) {
            when (c) {
                in '۰'..'۹' -> append('0' + (c - '۰'))
                in '٠'..'٩' -> append('0' + (c - '٠'))
                else -> if (c in '0'..'9') append(c)
            }
        }
    }
    val n = digits.toIntOrNull() ?: return null
    return if (n >= 1) n.coerceAtMost(max) else null
}

/**
 * A number choice: quick presets plus a "Custom" option where any number can be typed.
 * The preset with value 0 is shown with [zeroLabel] (for example "Unlimited" or "Auto").
 * [onChange] is called as soon as a valid number is chosen.
 */
@Composable
fun CountPicker(
    value: Int,
    presets: List<Int>,
    zeroLabel: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    max: Int = 999,
) {
    val strings = LocalStrings.current
    val more = strings.more
    val isCustomValue = value > 0 && value !in presets
    var customOpen by remember { mutableStateOf(isCustomValue) }
    var text by remember { mutableStateOf(if (isCustomValue) value.toString() else "") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { n ->
                FilterChip(
                    selected = !customOpen && value == n,
                    onClick = {
                        customOpen = false
                        onChange(n)
                    },
                    label = { Text(if (n == 0) zeroLabel else strings.digits(n.toString())) },
                )
            }
            FilterChip(
                selected = customOpen,
                onClick = {
                    customOpen = true
                    parseCount(text, max)?.let(onChange)
                },
                label = { Text(if (isCustomValue) strings.digits(value.toString()) else more.speedCustom) },
            )
        }
        if (customOpen) {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input.take(3)
                    parseCount(text, max)?.let(onChange)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(more.parallelValue) },
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
            )
            Text(
                more.parallelCustomHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
