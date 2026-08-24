package com.athea.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for shared UI dimensions and shapes.
 * Every "similar" element references the same constant — no duplicates
 * that can drift apart.
 */
object Ui {
    // ---- Shapes -----------------------------------------------------------
    val menuShape = RoundedCornerShape(24.dp)
    val bubbleShape = RoundedCornerShape(22.dp)
    val panelShape = RoundedCornerShape(28.dp)
    val chipShape = RoundedCornerShape(10.dp)
    val attachmentShape = RoundedCornerShape(14.dp)
    val dialogShape = RoundedCornerShape(20.dp)

    // ---- Menus ------------------------------------------------------------
    val menuMinWidth = 190.dp
    val menuItemMinHeight = 56.dp
    val menuIconSize = 24.dp

    // ---- Top bar ----------------------------------------------------------
    val topButtonSize = 48.dp
    val topBarHorizontalPadding = 10.dp
    val topBarTopPadding = 32.dp
    val topBarBottomPadding = 6.dp

    // ---- Composer ---------------------------------------------------------
    val composerButtonSize = 38.dp
    val composerButtonSpace = 52.dp
    val composerButtonRowHeight = 44.dp
    val composerFieldStart = 10.dp
    val composerFieldEndGrown = 8.dp
    val composerFieldTopGrown = 10.dp

    // ---- Key row ----------------------------------------------------------
    val keyMinWidth = 44.dp
    val keyMinHeight = 40.dp
    val keySpacing = 4.dp
    val keyPaddingH = 8.dp

    // ---- Bubbles ----------------------------------------------------------
    val bubblePaddingH = 16.dp
    val bubblePaddingTop = 12.dp
    val bubblePaddingBottom = 14.dp
    val bubbleMaxWidthFraction = 0.85f
    val outputPaddingH = 12.dp

    // ---- Drawer -----------------------------------------------------------
    val drawerWidthFraction = 0.72f
    val drawerItemPaddingH = 20.dp
    val drawerItemPaddingV = 12.dp

    // ---- Misc -------------------------------------------------------------
    val scrollbarButtonSize = 36.dp
}
