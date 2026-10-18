package com.example.polygoneditor.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Asks for an edge length. Knows nothing about the editor –
 * the caller decides what confirming means.
 */
@Composable
fun LengthDialog(initial: Double, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    // Lives only while the dialog is shown, so every opening starts from `initial`
    var text by remember { mutableStateOf(initial.fmt()) }
    val value = text.replace(',', '.').toDoubleOrNull() // "12,5" works too
    val valid = value != null && value > 0

    AlertDialog(
        onDismissRequest = onDismiss, // Esc or a click outside
        title = { Text("Edge length") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it }, // the field shows `text` – without this nothing could be typed
                label = { Text("Length") },
                singleLine = true,
                isError = !valid,
                supportingText = { if (!valid) Text("Enter a positive number") },
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = valid) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
