package com.google.mytaskmanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * Yes/No dialog. Destructive confirmations use [danger] so the confirm button turns red and is unmistakable.
 */
@Composable
fun ConfirmDialog(
    title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    danger: Boolean = false, dismissLabel: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
        shape = MaterialTheme.shapes.extraLarge
    )
}

/**
 * Single-field input dialog with live validation. The confirm button stays disabled while [validate] returns an
 * error; the error text appears once the user has typed.
 *
 * @param validate returns an error message, or null when the text is acceptable.
 */
@Composable
fun TextInputDialog(
    title: String, label: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit,
    initial: String = "", confirmLabel: String = "Save", singleLine: Boolean = true, help: String? = null,
    validate: (String) -> String? = { null }
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val error = validate(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AppTextField(
                value = text, onValueChange = { text = it; touched = true }, label = label,
                modifier = Modifier.focusRequester(focus), error = if (touched) error else null, help = help,
                singleLine = singleLine, minLines = if (singleLine) 1 else 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        },
        confirmButton = { TextButton(enabled = error == null, onClick = { onConfirm(text.trim()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.extraLarge
    )
}

/**
 * Calendar dialog used by "Replan". Returns the chosen day as UTC-midnight millis (what the server stores for due
 * dates), or null when the date was cleared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initialMillis: Long?, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit, allowClear: Boolean = true) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.selectedDateMillis) }) { Text("Set date") } },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onConfirm(null) }) { Text("Clear") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    ) { DatePicker(state = state) }
}

/** A titled list of choices in a dialog (used for roles, priorities, column pickers). */
@Composable
fun <T> ChoiceDialog(title: String, options: List<T>, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit, selected: T? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { opt ->
                    TextButton(onClick = { onPick(opt) }, modifier = Modifier.padding(0.dp)) {
                        Text(label(opt), color = if (opt == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.extraLarge
    )
}
