package com.athea.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.athea.app.R
import com.athea.app.ui.theme.Ui

/**
 * One AlertDialog skeleton for the whole app: shape, surface color and
 * the cancel button live here; dialogs only supply their title, content,
 * confirm action and save-label. No more per-dialog AlertDialog boilerplate.
 */
@Composable
fun AtheaDialogContainer(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirm: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Ui.dialogShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        // AlertDialog's own `text` slot has no ColumnScope receiver, so the
        // content lambda gets one from a Column instead of being called bare.
        text = {
            Column { content() }
        },
        confirmButton = confirm,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
        modifier = modifier,
    )
}

/**
 * One text-input dialog: a labeled [OutlinedTextField] backed by local state
 * so callers hand in an initial value and get the result. Covers rename,
 * favorite edit and the key editor's payload field.
 */
@Composable
fun AtheaTextFieldDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    label: String? = null,
    singleLine: Boolean = true,
) {
    var value by remember { mutableStateOf(initialValue) }

    AtheaDialogContainer(
        title = title,
        onDismiss = onDismiss,
        content = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { if (label != null) Text(label) },
                singleLine = singleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirm = {
            TextButton(onClick = { onSave(value) }) {
                Text(stringResource(R.string.dialog_save))
            }
        },
    )
}

/**
 * One confirm dialog for destructive/one-shot actions; [confirmTinted]
 * renders the confirm button in the error color (deletes).
 */
@Composable
fun AtheaConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String,
    confirmTinted: Boolean = false,
) {
    AtheaDialogContainer(
        title = title,
        onDismiss = onDismiss,
        content = {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirm = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (confirmTinted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}
