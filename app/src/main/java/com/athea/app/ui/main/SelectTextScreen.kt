package com.athea.app.ui.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.athea.app.R
import com.athea.app.ui.common.AtheaScaffold
import com.athea.app.ui.theme.Ui
import com.athea.app.ui.theme.codeStyle

/**
 * Dedicated full-screen text selection surface (long-press to select),
 * mirroring the familiar chat-app pattern. Selection is bounded to the
 * given text by design.
 */
@Composable
fun SelectTextScreen(
    text: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtheaScaffold(
        title = stringResource(R.string.select_text_title),
        onBack = onClose,
        modifier = modifier,
    ) {
        SelectionContainer(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Text(
                text = text,
                style = codeStyle().copy(color = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(Ui.screenPadding),
            )
        }
    }
}
