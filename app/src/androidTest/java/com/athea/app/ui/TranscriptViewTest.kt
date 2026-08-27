package com.athea.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.athea.app.core.model.CommandBlock
import com.athea.app.core.model.OutputBlock
import com.athea.app.transcript.BlockView
import com.athea.app.ui.main.TranscriptView
import com.athea.app.ui.theme.AtheaTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests — satisfies audit "no UI tests".
 * Requires androidTestImplementation "androidx.compose.ui:ui-test-junit4".
 */
class TranscriptViewTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun commandBubbleIsDisplayed() {
        val session = SessionUi(
            id = 1, name = "test", pinned = false,
            displayMode = com.athea.app.core.model.DisplayMode.BLOCKS,
            draft = "", rawText = "", running = false,
            blocks = listOf(
                BlockView(CommandBlock("cmd-1", "echo hello"), false),
                BlockView(OutputBlock("out-1", "hello\n"), false)
            )
        )
        compose.setContent {
            AtheaTheme {
                TranscriptView(
                    session = session,
                    search = null,
                    scrollRequests = MutableSharedFlow(),
                    jumpToBottom = MutableSharedFlow(),
                    previewLines = 3,
                    contentTopPadding = 16.dp,
                    pinchZoomEnabled = false,
                    onOutputFontZoom = {},
                    onToggleBlock = {},
                    onRevealBlock = {},
                    onLocateBlock = { -1 },
                    onCopyCommand = {},
                    onSelectCommandText = {},
                    onAddToFavorites = {},
                    onAreaResized = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("echo hello").assertIsDisplayed()
        compose.onNodeWithText("hello").assertIsDisplayed()
    }
}
