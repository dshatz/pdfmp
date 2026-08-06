package com.dshatz.pdfmp

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.ui.graphics.Color

@androidx.compose.runtime.Composable
actual fun Scrollbar(
    info: com.dshatz.pdfmp.compose.state.PdfLayoutInfo,
    modifier: androidx.compose.ui.Modifier
) {
    /*val adapter = info.rememberScrollbarAdapter()
    VerticalScrollbar(adapter, modifier = modifier, style = LocalScrollbarStyle.current.copy(
        unhoverColor = Color.DarkGray,
        hoverColor = Color.Black
    ))*/
}