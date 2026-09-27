package com.athea.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.athea.app.R
import com.athea.app.ui.common.AtheaConfirmDialog
import com.athea.app.ui.common.AtheaTextFieldDialog

@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    AtheaTextFieldDialog(
        title = stringResource(R.string.rename_title),
        initialValue = initialName,
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
fun DeleteConfirmDialog(
    sessionName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AtheaConfirmDialog(
        title = stringResource(R.string.delete_confirm_title),
        text = stringResource(R.string.delete_confirm_text, sessionName),
        confirmLabel = stringResource(R.string.menu_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmTinted = true,
    )
}
