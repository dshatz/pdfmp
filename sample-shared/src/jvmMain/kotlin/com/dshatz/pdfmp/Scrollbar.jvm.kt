package com.dshatz.pdfmp

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dshatz.pdfmp.compose.platformModifier.rememberScrollbarAdapter
import com.dshatz.pdfmp.compose.state.PdfLayoutInfo

@Composable
actual fun Scrollbar(info: PdfLayoutInfo, modifier: Modifier) {
    val adapter = info.rememberScrollbarAdapter()
    VerticalScrollbar(adapter, modifier = modifier, style = LocalScrollbarStyle.current.copy(
        unhoverColor = Color.DarkGray,
        hoverColor = Color.Black
    ))
}